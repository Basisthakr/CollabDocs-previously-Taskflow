import React, { useState, useEffect, useLayoutEffect, useRef, useCallback, useMemo } from "react";
import { createPortal } from "react-dom";
import ReactQuill from "react-quill";
import "react-quill/dist/quill.snow.css";
import "./editor.css";
import { useParams, useLocation, useNavigate } from "react-router-dom";
import { Client } from "@stomp/stompjs";
import { BASE_URL, WS_URL } from "../Redux/config.js";
import { useDispatch } from "react-redux";
import { LOGOUT } from "../Redux/Auth/ActionType.js";
import docsIcon from "../assets/doc_image.png";

// ---------------------------------------------------------------------------
// Edit — real-time collaborative document editor
//
// Sync strategy:
//   1. On mount: load the full CRDT state via REST (GET /api/docs/changes/:id)
//   2. While editing: send each keystroke via STOMP and also receive remote
//      changes via STOMP for instant collaboration.
//   3. Polling every 2 s: fetch the live CRDT from the server and merge any
//      items that the local replica doesn't yet have. This acts as a reliable
//      catch-up mechanism — even if a STOMP message was missed, the next poll
//      will apply it.
//   4. Save: the server auto-saves when the last subscriber disconnects. There
//      is also an explicit "Save" button + auto-save on page unload.
//
// CRDT state lives in React refs (not useState) so that WebSocket/interval
// callbacks always see current values without stale-closure issues.
// ---------------------------------------------------------------------------

const AVATAR_COLORS  = ["#1a73e8", "#e8710a", "#1e8e3e", "#d93025", "#9334e6", "#00796b"];
const CURSOR_COLORS  = ["#e8710a", "#1e8e3e", "#d93025", "#9334e6", "#00796b", "#c2185b", "#1a73e8"];
const POLL_INTERVAL_MS = 2000;

const FORMATS = [
  "bold", "italic", "underline", "strike",
  "header", "align", "list", "indent",
  "color", "background", "link",
];

function cursorColor(name) {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = ((h << 5) - h) + name.charCodeAt(i);
  return CURSOR_COLORS[Math.abs(h) % CURSOR_COLORS.length];
}

// Renders remote cursor carets + name badges inside the Quill container via a portal.
function CursorLayer({ editor, cursors }) {
  const [positions, setPositions] = useState({});

  useLayoutEffect(() => {
    if (!editor) return;
    const next = {};
    for (const [user, { index }] of Object.entries(cursors)) {
      if (index < 0) continue;
      try {
        const b = editor.getBounds(index, 0);
        if (b) next[user] = b;
      } catch (_) {}
    }
    setPositions(next);
  }, [editor, cursors]);

  return (
    <>
      {Object.entries(positions).map(([user, b]) => {
        const color = cursorColor(user);
        return (
          <div
            key={user}
            style={{
              position: "absolute",
              top:      b.top,
              left:     b.left,
              height:   b.height,
              pointerEvents: "none",
              zIndex:   20,
            }}
          >
            <div style={{ position: "absolute", width: 2, height: "100%", background: color }} />
            <div style={{
              position:    "absolute",
              bottom:      "100%",
              left:        0,
              background:  color,
              color:       "#fff",
              fontSize:    11,
              fontWeight:  700,
              padding:     "1px 6px",
              borderRadius:"3px 3px 3px 0",
              whiteSpace:  "nowrap",
              userSelect:  "none",
              lineHeight:  "18px",
            }}>
              {user}
            </div>
          </div>
        );
      })}
    </>
  );
}

// Static toolbar rendered into a custom container referenced by #editor-toolbar.
// Quill wires up all the button/select event handlers automatically.
function EditorToolbar() {
  return (
    <div id="editor-toolbar">
      {/* Undo / Redo */}
      <span className="ql-formats">
        <button className="ql-undo" title="Undo (Ctrl+Z)">
          <svg viewBox="0 0 18 18">
            <polygon className="ql-fill ql-stroke" points="6 10 4 12 2 10 6 10" />
            <path className="ql-stroke" d="M8.09,13.91A4.6,4.6,0,0,0,9,14,5,5,0,1,0,4,9" />
          </svg>
        </button>
        <button className="ql-redo" title="Redo (Ctrl+Y)">
          <svg viewBox="0 0 18 18">
            <polygon className="ql-fill ql-stroke" points="12 10 14 12 16 10 12 10" />
            <path className="ql-stroke" d="M9.91,13.91A4.6,4.6,0,0,1,9,14,5,5,0,1,1,14,9" />
          </svg>
        </button>
      </span>

      {/* Heading */}
      <span className="ql-formats">
        <select className="ql-header" defaultValue="">
          <option value="1">Heading 1</option>
          <option value="2">Heading 2</option>
          <option value="3">Heading 3</option>
          <option value="">Normal text</option>
        </select>
      </span>

      {/* Text style */}
      <span className="ql-formats">
        <button className="ql-bold"      title="Bold (Ctrl+B)" />
        <button className="ql-italic"    title="Italic (Ctrl+I)" />
        <button className="ql-underline" title="Underline (Ctrl+U)" />
        <button className="ql-strike"    title="Strikethrough" />
      </span>

      {/* Color */}
      <span className="ql-formats">
        <select className="ql-color"      title="Text color" />
        <select className="ql-background" title="Highlight color" />
      </span>

      {/* Alignment */}
      <span className="ql-formats">
        <select className="ql-align" title="Alignment" />
      </span>

      {/* Lists + indent */}
      <span className="ql-formats">
        <button className="ql-list"   value="ordered" title="Numbered list" />
        <button className="ql-list"   value="bullet"  title="Bulleted list" />
        <button className="ql-indent" value="-1"      title="Decrease indent" />
        <button className="ql-indent" value="+1"      title="Increase indent" />
      </span>

      {/* Link */}
      <span className="ql-formats">
        <button className="ql-link" title="Insert link" />
      </span>

      {/* Clear formatting */}
      <span className="ql-formats">
        <button className="ql-clean" title="Clear formatting" />
      </span>
    </div>
  );
}

export default function Edit() {
  const quillRef   = useRef(null);
  const { docId }  = useParams();
  const location   = useLocation();
  const navigate   = useNavigate();
  const dispatch   = useDispatch();
  const username   = localStorage.getItem("displayName");
  const token      = localStorage.getItem("accessToken");
  const docTitle   = location.state ?? "Untitled document";

  // ── CRDT mutable state (never triggers re-renders) ──────────────────────
  const crdtMapRef    = useRef({});
  const firstItemRef  = useRef(null);
  const counterRef    = useRef(0);
  const stompRef      = useRef(null);

  // ── UI state ─────────────────────────────────────────────────────────────
  const [connected,      setConnected]      = useState(false);
  const [activeUsers,    setActiveUsers]    = useState([]);
  const [docStatus,      setDocStatus]      = useState("connecting");
  const [saveError,      setSaveError]      = useState("");
  const [remoteCursors,  setRemoteCursors]  = useState({});
  const [editorReady,    setEditorReady]    = useState(false);
  const statusTimerRef = useRef(null);

  // ── Quill modules (stable reference so Quill doesn't re-init) ────────────
  const modules = useMemo(() => ({
    toolbar: {
      container: "#editor-toolbar",
      handlers: {
        undo: () => quillRef.current?.getEditor()?.history.undo(),
        redo: () => quillRef.current?.getEditor()?.history.redo(),
      },
    },
    history: { delay: 1000, maxStack: 100, userOnly: true },
  }), []);

  // ── CRDT item factory ────────────────────────────────────────────────────
  // fmtAttrs keys match Quill attribute names (bold, italic, underline, strike,
  // header, align, list, indent, color, background).
  function makeItem(id, leftId, rightId, content, isDeleted, fmtAttrs = {}) {
    return {
      id,
      left:        leftId,
      right:       rightId,
      content,
      isDeleted,
      isBold:      !!fmtAttrs.bold,
      isItalic:    !!fmtAttrs.italic,
      isUnderline: !!fmtAttrs.underline,
      isStrike:    !!fmtAttrs.strike,
      header:      fmtAttrs.header     ?? null,
      align:       fmtAttrs.align      ?? null,
      list:        fmtAttrs.list       ?? null,
      indent:      fmtAttrs.indent     ?? 0,
      color:       fmtAttrs.color      ?? null,
      background:  fmtAttrs.background ?? null,
    };
  }

  // ── YATA conflict-resolution helpers ────────────────────────────────────
  function clientIdOf(itemId) {
    const at = itemId.indexOf("@");
    return at >= 0 ? itemId.substring(at + 1) : itemId;
  }

  function compareItems(incomingId, candidateId) {
    return clientIdOf(candidateId).localeCompare(clientIdOf(incomingId));
  }

  // ── CRDT mutators ────────────────────────────────────────────────────────

  function applyInsert(newItem) {
    const map = crdtMapRef.current;
    if (map[newItem.id]) return;

    map[newItem.id] = newItem;

    if (!newItem.left) {
      newItem.right = firstItemRef.current ? firstItemRef.current.id : null;
      if (firstItemRef.current) firstItemRef.current.left = newItem.id;
      firstItemRef.current = newItem;
    } else {
      const left = map[newItem.left];
      if (!left) {
        delete map[newItem.id];
        console.warn("applyInsert: missing left neighbour for", newItem.id);
        return;
      }

      let cur   = left;
      let right = left.right ? map[left.right] : null;

      while (right && compareItems(newItem.id, right.id) > 0) {
        cur   = right;
        right = right.right ? map[right.right] : null;
      }

      newItem.left  = cur.id;
      newItem.right = right ? right.id : null;
      cur.right     = newItem.id;
      if (right) right.left = newItem.id;
    }
  }

  function applyDelete(itemId) {
    const item = crdtMapRef.current[itemId];
    if (item) item.isDeleted = true;
  }

  // Fully overwrites all format fields on an item.
  function applyFormat(itemId, attrs) {
    const item = crdtMapRef.current[itemId];
    if (!item) return;
    item.isBold      = !!attrs.bold;
    item.isItalic    = !!attrs.italic;
    item.isUnderline = !!attrs.underline;
    item.isStrike    = !!attrs.strike;
    item.header      = attrs.header     ?? null;
    item.align       = attrs.align      ?? null;
    item.list        = attrs.list       ?? null;
    item.indent      = attrs.indent     ?? 0;
    item.color       = attrs.color      ?? null;
    item.background  = attrs.background ?? null;
  }

  // ── Quill renderer ───────────────────────────────────────────────────────
  function renderQuill() {
    const editor = quillRef.current?.getEditor();
    if (!editor) return;

    const saved = editor.getSelection();
    const ops   = [];
    let current = firstItemRef.current;

    while (current) {
      if (!current.isDeleted) {
        const attrs = {};
        if (current.isBold)      attrs.bold       = true;
        if (current.isItalic)    attrs.italic      = true;
        if (current.isUnderline) attrs.underline   = true;
        if (current.isStrike)    attrs.strike      = true;
        if (current.header)      attrs.header      = current.header;
        if (current.align)       attrs.align       = current.align;
        if (current.list)        attrs.list        = current.list;
        if (current.indent)      attrs.indent      = current.indent;
        if (current.color)       attrs.color       = current.color;
        if (current.background)  attrs.background  = current.background;
        ops.push({
          insert: current.content,
          ...(Object.keys(attrs).length ? { attributes: attrs } : {}),
        });
      }
      current = current.right ? crdtMapRef.current[current.right] : null;
    }

    // Quill requires the document to end with exactly one \n. If the CRDT
    // doesn't include a trailing newline (the user never pressed Enter at the
    // very end), Quill's sentinel \n is virtual — it never gets an ID in the
    // CRDT. That means block-level attributes on it (heading, alignment, list)
    // can't be stored as CRDT items. To prevent renderQuill from wiping those
    // attributes on every rebuild, we read the current Quill format at that
    // position and carry it forward into the new ops.
    const endsWithNewline = ops.length > 0 && ops[ops.length - 1].insert === '\n';
    if (!endsWithNewline) {
      const trailingFormat = editor.getFormat(ops.length);
      ops.push({
        insert: '\n',
        ...(Object.keys(trailingFormat).length ? { attributes: trailingFormat } : {}),
      });
    }

    editor.setContents({ ops }, "api");
    if (saved) editor.setSelection(saved.index, saved.length, "api");
  }

  // ── Position helper ──────────────────────────────────────────────────────
  function getVisibleItemId(pos) {
    if (pos < 0) return null;
    let current = firstItemRef.current;
    let count   = -1;
    while (current) {
      if (!current.isDeleted) {
        count++;
        if (count === pos) return current.id;
      }
      current = current.right ? crdtMapRef.current[current.right] : null;
    }
    return null;
  }

  // ── Build a format attrs object from a CRDT item ─────────────────────────
  function itemAttrs(item) {
    return {
      bold:       item.isBold,
      italic:     item.isItalic,
      underline:  item.isUnderline,
      strike:     item.isStrike,
      header:     item.header,
      align:      item.align,
      list:       item.list,
      indent:     item.indent,
      color:      item.color,
      background: item.background,
    };
  }

  // ── Build a format attrs object from a DTO (server/incoming message) ─────
  function dtoAttrs(dto) {
    return {
      bold:       dto.isBold,
      italic:     dto.isItalic,
      underline:  dto.isUnderline,
      strike:     dto.isStrike,
      header:     dto.header,
      align:      dto.align,
      list:       dto.list,
      indent:     dto.indent,
      color:      dto.color,
      background: dto.background,
    };
  }

  // ── Build a complete WebSocket message body for a format operation ────────
  function formatMsg(itemId, attrs) {
    return JSON.stringify({
      id:          itemId,
      operation:   "format",
      content:     "",
      left:        null,
      right:       null,
      isDeleted:   false,
      isBold:      !!attrs.bold,
      isItalic:    !!attrs.italic,
      isUnderline: !!attrs.underline,
      isStrike:    !!attrs.strike,
      header:      attrs.header     ?? null,
      align:       attrs.align      ?? null,
      list:        attrs.list       ?? null,
      indent:      attrs.indent     ?? 0,
      color:       attrs.color      ?? null,
      background:  attrs.background ?? null,
    });
  }

  // ── Merge items from server response into local CRDT ────────────────────
  function mergeCrdtItems(items) {
    let changed = false;
    for (const dto of items) {
      if (!crdtMapRef.current[dto.id]) {
        applyInsert(makeItem(
          dto.id, dto.left, dto.right, dto.content, dto.isDeleted, dtoAttrs(dto)
        ));
        changed = true;
      } else {
        const local = crdtMapRef.current[dto.id];
        if (dto.isDeleted && !local.isDeleted) {
          local.isDeleted = true;
          changed = true;
        }
        // Do NOT sync any format fields from the poll. Format state is
        // authoritative in the local CRDT and is propagated via STOMP only.
        // Syncing isBold/isItalic from the server here caused a race condition:
        // undo → server still has old value → poll fires → overrides local state.
      }
    }
    return changed;
  }

  // ── Mark editor ready after first render ────────────────────────────────
  useEffect(() => { setEditorReady(true); }, []);

  // ── Keep undo/redo buttons visually disabled when stacks are empty ───────
  useEffect(() => {
    const editor = quillRef.current?.getEditor?.();
    if (!editor) return;
    const syncBtns = () => {
      const undoBtn = document.querySelector('#editor-toolbar .ql-undo');
      const redoBtn = document.querySelector('#editor-toolbar .ql-redo');
      if (undoBtn) undoBtn.disabled = editor.history.stack.undo.length === 0;
      if (redoBtn) redoBtn.disabled = editor.history.stack.redo.length === 0;
    };
    editor.on('editor-change', syncBtns);
    syncBtns();
    return () => editor.off('editor-change', syncBtns);
  }, [editorReady]);

  // ── Load initial document from REST API ─────────────────────────────────
  useEffect(() => {
    if (!token || !docId) return;

    async function loadDocument() {
      try {
        const res = await fetch(`${BASE_URL}/api/docs/changes/${docId}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!res.ok) {
          console.error("Failed to load document — status:", res.status);
          return;
        }
        const items = await res.json();

        crdtMapRef.current   = {};
        firstItemRef.current = null;

        let maxCounter = 0;
        for (const dto of items) {
          const at = dto.id ? dto.id.indexOf("@") : -1;
          if (at >= 0 && dto.id.substring(at + 1) === username) {
            const n = parseInt(dto.id.substring(0, at), 10);
            if (!isNaN(n) && n >= maxCounter) maxCounter = n + 1;
          }
        }
        counterRef.current = maxCounter;

        mergeCrdtItems(items);
        renderQuill();
      } catch (err) {
        console.error("Error loading document:", err);
      }
    }

    loadDocument();
  }, [docId, token]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Polling: merge remote changes every POLL_INTERVAL_MS ─────────────────
  useEffect(() => {
    if (!token || !docId) return;

    const poll = async () => {
      try {
        const res = await fetch(`${BASE_URL}/api/docs/changes/${docId}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!res.ok) return;
        const items = await res.json();

        const changed = mergeCrdtItems(items);
        if (changed) {
          setDocStatus("updating");
          renderQuill();
          if (statusTimerRef.current) clearTimeout(statusTimerRef.current);
          statusTimerRef.current = setTimeout(
            () => setDocStatus(prev => prev === "updating" ? "connected" : prev),
            1500
          );
        }
      } catch (err) {
        console.error("Poll error:", err);
      }
    };

    const interval = setInterval(poll, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [docId, token]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── WebSocket connection ─────────────────────────────────────────────────
  useEffect(() => {
    if (!token || !docId) return;

    const client = new Client({
      brokerURL:      WS_URL,
      reconnectDelay: 5000,
      connectHeaders: { Authorization: `Bearer ${token}` },

      onConnect: () => {
        setConnected(true);
        setDocStatus("connected");

        client.subscribe(`/docs/broadcast/usernames/${docId}`, (msg) => {
          const data = JSON.parse(msg.body);
          const names = data.displayNames ?? [];
          setActiveUsers(names);
          setRemoteCursors(prev => {
            const next = {};
            for (const k of Object.keys(prev)) if (names.includes(k)) next[k] = prev[k];
            return next;
          });
        });

        client.subscribe(`/docs/broadcast/cursors/${docId}`, (msg) => {
          const c = JSON.parse(msg.body);
          if (c.username !== username) {
            setRemoteCursors(prev => ({ ...prev, [c.username]: { index: c.index, length: c.length } }));
          }
        });

        client.subscribe(`/docs/broadcast/changes/${docId}`, (msg) => {
          handleIncomingChange(JSON.parse(msg.body));
        });
      },

      onDisconnect: () => { setConnected(false); setDocStatus("connecting"); },
      onStompError: (frame) => console.error("STOMP error:", frame),
    });

    client.activate();
    stompRef.current = client;

    return () => {
      client.deactivate();
      stompRef.current = null;
    };
  }, [docId, token]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Auto-save on page unload ─────────────────────────────────────────────
  useEffect(() => {
    const handleUnload = () => {
      if (!token || !docId) return;
      navigator.sendBeacon(`${BASE_URL}/api/docs/save/${docId}`, JSON.stringify({ token }));
      try {
        const xhr = new XMLHttpRequest();
        xhr.open("POST", `${BASE_URL}/api/docs/save/${docId}`, false);
        xhr.setRequestHeader("Authorization", `Bearer ${token}`);
        xhr.setRequestHeader("Content-Type", "application/json");
        xhr.send();
      } catch (_) {}
    };
    window.addEventListener("beforeunload", handleUnload);
    return () => window.removeEventListener("beforeunload", handleUnload);
  }, [docId, token]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Manual save ─────────────────────────────────────────────────────────
  const handleSave = useCallback(async () => {
    if (!token || !docId) return;
    setDocStatus("saving");
    try {
      const res = await fetch(`${BASE_URL}/api/docs/save/${docId}`, {
        method:  "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) {
        const body = await res.text().catch(() => "");
        throw new Error(`HTTP ${res.status}: ${body || res.statusText}`);
      }
      setDocStatus("saved");
      if (statusTimerRef.current) clearTimeout(statusTimerRef.current);
      statusTimerRef.current = setTimeout(() => setDocStatus("connected"), 2000);
    } catch (err) {
      console.error("Save error:", err);
      setSaveError(err.message);
      setDocStatus("savefailed");
      if (statusTimerRef.current) clearTimeout(statusTimerRef.current);
      statusTimerRef.current = setTimeout(() => { setDocStatus("connected"); setSaveError(""); }, 5000);
    }
  }, [docId, token]);

  // ── Keyboard shortcut Ctrl+S / Cmd+S ────────────────────────────────────
  useEffect(() => {
    const onKeyDown = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === "s") {
        e.preventDefault();
        handleSave();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [handleSave]);

  // ── Incoming STOMP change handler ────────────────────────────────────────
  function handleIncomingChange(incoming) {
    if (!incoming) return;
    if (clientIdOf(incoming.id) === username) return;

    let changed = false;
    switch (incoming.operation) {
      case "insert":
        if (!crdtMapRef.current[incoming.id]) {
          applyInsert(makeItem(
            incoming.id, incoming.left, incoming.right, incoming.content,
            incoming.isDeleted, dtoAttrs(incoming)
          ));
          changed = true;
        }
        break;
      case "delete":
        if (crdtMapRef.current[incoming.id] && !crdtMapRef.current[incoming.id].isDeleted) {
          applyDelete(incoming.id);
          changed = true;
        }
        break;
      case "format": {
        if (crdtMapRef.current[incoming.id]) {
          applyFormat(incoming.id, dtoAttrs(incoming));
          changed = true;
        }
        break;
      }
      default:
        console.warn("Unknown CRDT operation:", incoming.operation);
    }

    if (!changed) return;

    setTimeout(renderQuill, 0);

    setDocStatus("updating");
    if (statusTimerRef.current) clearTimeout(statusTimerRef.current);
    statusTimerRef.current = setTimeout(
      () => setDocStatus(prev => prev === "updating" ? "connected" : prev),
      1500
    );
  }

  // ── Local change handler ─────────────────────────────────────────────────
  const handleLocalChange = useCallback(
    (_, delta, source) => {
      if (source !== "user") return;
      const client = stompRef.current;
      if (!client?.connected) return;

      let visualPos = 0;

      for (const op of delta.ops) {
        if (op.retain !== undefined) {
          if (op.attributes) {
            // ── Format existing selected text ──────────────────────────────
            // This is the critical path: selecting text and clicking Bold/Italic/
            // Underline etc. sends a retain+attributes delta. We must translate
            // each character in the selection into a CRDT format operation.
            for (let i = 0; i < op.retain; i++) {
              const itemId = getVisibleItemId(visualPos + i);
              if (!itemId) continue;
              const item = crdtMapRef.current[itemId];
              if (!item) continue;

              // Merge: only override fields present in op.attributes; keep
              // existing values for everything else.
              const newAttrs = {
                bold:       op.attributes.bold       !== undefined ? !!op.attributes.bold       : item.isBold,
                italic:     op.attributes.italic     !== undefined ? !!op.attributes.italic     : item.isItalic,
                underline:  op.attributes.underline  !== undefined ? !!op.attributes.underline  : item.isUnderline,
                strike:     op.attributes.strike     !== undefined ? !!op.attributes.strike     : item.isStrike,
                header:     op.attributes.header     !== undefined ? (op.attributes.header  || null) : item.header,
                align:      op.attributes.align      !== undefined ? (op.attributes.align   || null) : item.align,
                list:       op.attributes.list       !== undefined ? (op.attributes.list    || null) : item.list,
                indent:     op.attributes.indent     !== undefined ? (op.attributes.indent  ?? 0)    : item.indent,
                color:      op.attributes.color      !== undefined ? (op.attributes.color   || null) : item.color,
                background: op.attributes.background !== undefined ? (op.attributes.background || null) : item.background,
              };

              applyFormat(itemId, newAttrs);
              client.publish({
                destination: `/docs/change/${docId}`,
                body: formatMsg(itemId, newAttrs),
              });
            }
          }
          visualPos += op.retain;

        } else if (op.insert !== undefined) {
          const text  = typeof op.insert === "string" ? op.insert : "\n";
          const attrs = op.attributes ?? {};

          const fmtAttrs = {
            bold:       !!attrs.bold,
            italic:     !!attrs.italic,
            underline:  !!attrs.underline,
            strike:     !!attrs.strike,
            header:     attrs.header     || null,
            align:      attrs.align      || null,
            list:       attrs.list       || null,
            indent:     attrs.indent     ?? 0,
            color:      attrs.color      || null,
            background: attrs.background || null,
          };

          for (let i = 0; i < text.length; i++) {
            const char   = text[i];
            const id     = `${counterRef.current++}@${username}`;
            const leftId = getVisibleItemId(visualPos - 1 + i);

            const item = makeItem(id, leftId, null, char, false, fmtAttrs);
            applyInsert(item);

            client.publish({
              destination: `/docs/change/${docId}`,
              body: JSON.stringify({
                id,
                left:        leftId,
                right:       null,
                content:     char,
                operation:   "insert",
                isDeleted:   false,
                isBold:      fmtAttrs.bold,
                isItalic:    fmtAttrs.italic,
                isUnderline: fmtAttrs.underline,
                isStrike:    fmtAttrs.strike,
                header:      fmtAttrs.header,
                align:       fmtAttrs.align,
                list:        fmtAttrs.list,
                indent:      fmtAttrs.indent,
                color:       fmtAttrs.color,
                background:  fmtAttrs.background,
              }),
            });
          }

          visualPos += text.length;

        } else if (op.delete !== undefined) {
          const toDelete = [];
          for (let i = 0; i < op.delete; i++) {
            const itemId = getVisibleItemId(visualPos + i);
            if (itemId) toDelete.push(itemId);
          }

          for (const itemId of toDelete) {
            applyDelete(itemId);
            client.publish({
              destination: `/docs/change/${docId}`,
              body: JSON.stringify({
                id:          itemId,
                operation:   "delete",
                content:     "",
                left:        null,
                right:       null,
                isDeleted:   true,
                isBold:      false,
                isItalic:    false,
                isUnderline: false,
                isStrike:    false,
                header:      null,
                align:       null,
                list:        null,
                indent:      0,
                color:       null,
                background:  null,
              }),
            });
          }
        }
      }

      // Broadcast cursor position so collaborators see it move while typing.
      const sel = quillRef.current?.getEditor()?.getSelection();
      if (sel && client?.connected) {
        client.publish({
          destination: `/docs/cursor/${docId}`,
          body: JSON.stringify({ username, index: sel.index, length: sel.length }),
        });
      }
    },
    [docId, username] // eslint-disable-line react-hooks/exhaustive-deps
  );

  // ── Cursor broadcast ─────────────────────────────────────────────────────
  const handleSelectionChange = useCallback((range, source) => {
    if (source !== "user") return;
    const client = stompRef.current;
    if (!client?.connected) return;
    client.publish({
      destination: `/docs/cursor/${docId}`,
      body: JSON.stringify({
        username,
        index:  range ? range.index  : -1,
        length: range ? range.length : 0,
      }),
    });
  }, [docId, username]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Status label ─────────────────────────────────────────────────────────
  const statusLabel = {
    connecting:  { text: "● Connecting…",  cls: "" },
    connected:   { text: "✓ Connected",    cls: "docs-status--saved" },
    updating:    { text: "↻ Updating…",    cls: "docs-status--updating" },
    saving:      { text: "● Saving…",      cls: "docs-status--updating" },
    saved:       { text: "✓ Saved",        cls: "docs-status--saved" },
    savefailed:  { text: `✗ Save failed${saveError ? ": " + saveError : ""}`, cls: "docs-status--error" },
  }[docStatus] ?? { text: "…", cls: "" };

  // ── Render ───────────────────────────────────────────────────────────────
  return (
    <div className="docs-app">

      {/* ── Top bar ── */}
      <header className="docs-topbar">
        <div className="docs-topbar-left">
          <img
            src={docsIcon}
            alt="Docs home"
            className="docs-logo"
            onClick={() => navigate("/")}
            title="Go to Docs home"
          />
          <div className="docs-title-area">
            <span className="docs-doc-title">{docTitle}</span>
            <nav className="docs-menu-bar">
              {["File", "Edit", "View", "Insert", "Format", "Tools"].map(item => (
                <button key={item} className="docs-menu-item">{item}</button>
              ))}
            </nav>
          </div>
        </div>

        <div className="docs-topbar-right">
          <span className={`docs-status ${statusLabel.cls}`}>{statusLabel.text}</span>

          {activeUsers.length > 0 && (
            <div
              className="docs-active-users"
              title={`Active: ${activeUsers.join(", ")}`}
            >
              {activeUsers.slice(0, 5).map((user, i) => (
                <div
                  key={user}
                  className="docs-user-avatar"
                  style={{ backgroundColor: AVATAR_COLORS[i % AVATAR_COLORS.length], zIndex: 5 - i }}
                  title={user}
                >
                  {user[0]?.toUpperCase()}
                </div>
              ))}
              {activeUsers.length > 5 && (
                <div className="docs-user-avatar docs-user-avatar--more">
                  +{activeUsers.length - 5}
                </div>
              )}
            </div>
          )}

          <button className="docs-save-btn" onClick={handleSave} title="Save (Ctrl+S)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M17 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V7l-4-4zm-5 16a3 3 0 1 1 0-6 3 3 0 0 1 0 6zm3-10H5V5h10v4z"/>
            </svg>
            Save
          </button>

          <button className="docs-share-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z"/>
            </svg>
            Share
          </button>

          <div
            className="docs-user-avatar docs-current-user-avatar"
            style={{ backgroundColor: AVATAR_COLORS[5] }}
            title={`${username} — click to sign out`}
            onClick={() => dispatch({ type: LOGOUT })}
          >
            {username?.[0]?.toUpperCase()}
          </div>
        </div>
      </header>

      {/* ── Toolbar strip (outside the page so it doesn't scroll away) ── */}
      <div className="docs-toolbar-strip">
        <EditorToolbar />
      </div>

      {/* ── Document page ── */}
      <div className="docs-editor-area">
        <div className="docs-page">
          <ReactQuill
            ref={quillRef}
            onChange={handleLocalChange}
            onChangeSelection={handleSelectionChange}
            modules={modules}
            formats={FORMATS}
            theme="snow"
          />
          {editorReady && (() => {
            const editor = quillRef.current?.getEditor();
            if (!editor?.container) return null;
            return createPortal(
              <CursorLayer editor={editor} cursors={remoteCursors} />,
              editor.container
            );
          })()}
        </div>
      </div>

    </div>
  );
}
