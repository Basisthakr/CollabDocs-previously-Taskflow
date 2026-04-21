package com.basisttha.engine;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Operation-based CRDT (inspired by YATA) implemented as a doubly-linked list.
 *
 * Every character in the document is an {@link Item} with a globally-unique ID
 * in the format {@code counter@clientId} (e.g. {@code 3@alice}).
 *
 * <p>Concurrent-insert conflict resolution: when two inserts both declare the
 * same left neighbour, we slide right past any item whose clientId sorts after
 * the incoming item's clientId.  This gives a deterministic, total order that
 * converges on every replica without a central coordinator.
 *
 * <p>Deletes are tombstone-based: the item is marked {@code isDeleted=true} but
 * stays in the list so that concurrent inserts that reference it as a neighbour
 * still resolve correctly.
 *
 * <p>All mutating methods are {@code synchronized} on the {@code Crdt} instance
 * because the Spring WebSocket thread-pool can deliver multiple STOMP frames for
 * the same document concurrently.
 */
@Slf4j
public class Crdt {

    /** O(1) lookup by item id. */
    private final Map<String, Item> itemMap = new HashMap<>();

    /** First non-sentinel item in the list (null when document is empty). */
    private Item firstItem;

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Populate the CRDT from a flat snapshot list produced by {@link #toSnapshots()}.
     * The list must be in left-to-right document order.
     */
    public synchronized void initFromSnapshots(List<ItemSnapshot> snapshots) {
        itemMap.clear();
        firstItem = null;

        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        // First pass: create Item objects and register in map.
        List<Item> items = new ArrayList<>(snapshots.size());
        for (ItemSnapshot s : snapshots) {
            Item item = Item.builder()
                    .id(s.id())
                    .content(s.content())
                    .operation(s.operation())
                    .isDeleted(s.deleted())
                    .isBold(s.bold())
                    .isItalic(s.italic())
                    .isUnderline(s.underline())
                    .isStrike(s.strike())
                    .header(s.header())
                    .align(s.align())
                    .list(s.list())
                    .indent(s.indent())
                    .color(s.color())
                    .background(s.background())
                    .build();
            items.add(item);
            itemMap.put(item.getId(), item);
        }

        // Second pass: wire up left/right pointers in order.
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setLeft(i > 0 ? items.get(i - 1) : null);
            items.get(i).setRight(i < items.size() - 1 ? items.get(i + 1) : null);
        }

        firstItem = items.get(0);
        log.debug("Crdt initialised with {} items", items.size());
    }

    // -------------------------------------------------------------------------
    // Mutating operations (all synchronized)
    // -------------------------------------------------------------------------

    /**
     * Insert {@code item} at the position indicated by {@code item.getLeft()}.
     *
     * <p>When two concurrent inserts target the same left neighbour we slide
     * rightward past any item whose clientId sorts lexicographically after the
     * incoming item's clientId.  This is the YATA tie-breaking rule.
     */
    public synchronized void insert(Item item) {
        if (item == null || item.getId() == null) {
            log.warn("Attempted to insert null or id-less item — ignored");
            return;
        }
        if (itemMap.containsKey(item.getId())) {
            log.debug("Duplicate insert for id {} — ignored (already applied)", item.getId());
            return;
        }

        if (item.getLeft() == null) {
            // Insert at the very beginning.
            item.setRight(firstItem);
            if (firstItem != null) {
                firstItem.setLeft(item);
            }
            firstItem = item;
        } else {
            Item left  = item.getLeft();
            Item right = left.getRight();

            // Slide right while the candidate right item should come BEFORE us
            // (i.e. its clientId sorts after ours — it was inserted "earlier" in
            // wall-clock time and wins priority in our ordering scheme).
            while (right != null && compareItems(item, right) > 0) {
                left  = right;
                right = right.getRight();
            }

            item.setLeft(left);
            item.setRight(right);
            left.setRight(item);
            if (right != null) {
                right.setLeft(item);
            }
        }

        itemMap.put(item.getId(), item);
        log.debug("Inserted item {} (content='{}')", item.getId(), item.getContent());
    }

    /** Tombstone-delete: marks the item as deleted without unlinking it. */
    public synchronized void delete(String id) {
        Item item = itemMap.get(id);
        if (item == null) {
            log.warn("delete called for unknown id {} — ignored", id);
            return;
        }
        item.setDeleted(true);
        item.setOperation("delete");
        log.debug("Deleted item {}", id);
    }

    /** Convenience overload for tests and callers that only need bold/italic. */
    public synchronized void format(String id, boolean isBold, boolean isItalic) {
        format(id, isBold, isItalic, false, false, null, null, null, 0, null, null);
    }

    /** Update all formatting attributes on an existing item. */
    public synchronized void format(String id, boolean isBold, boolean isItalic,
            boolean isUnderline, boolean isStrike,
            Integer header, String align, String list, int indent,
            String color, String background) {
        Item item = itemMap.get(id);
        if (item == null) {
            log.warn("format called for unknown id {} — ignored", id);
            return;
        }
        item.setBold(isBold);
        item.setItalic(isItalic);
        item.setUnderline(isUnderline);
        item.setStrike(isStrike);
        item.setHeader(header);
        item.setAlign(align);
        item.setList(list);
        item.setIndent(indent);
        item.setColor(color);
        item.setBackground(background);
        log.debug("Formatted item {} bold={} italic={} underline={} strike={} header={}",
                id, isBold, isItalic, isUnderline, isStrike, header);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public synchronized Item getItem(String id) {
        return itemMap.get(id);
    }

    /** Returns ALL items (including tombstones) in document order. */
    public synchronized List<Item> getItems() {
        List<Item> result = new ArrayList<>();
        Item current = firstItem;
        while (current != null) {
            result.add(current);
            current = current.getRight();
        }
        return result;
    }

    /** Renders the visible (non-deleted) content as a plain string. */
    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        Item current = firstItem;
        while (current != null) {
            if (!current.isDeleted()) {
                sb.append(current.getContent());
            }
            current = current.getRight();
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Persistence helpers
    // -------------------------------------------------------------------------

    /**
     * Produce a compact, flat snapshot of the document suitable for JSON
     * serialisation.  Tombstoned items are stripped and IDs are rewritten to
     * {@code index@_} so the stored document is always small and clean.
     *
     * <p>This compaction is safe because we only call it when the LAST user
     * disconnects from a document — at that point no remote replica holds a
     * reference to the old IDs.
     */
    public synchronized List<ItemSnapshot> toSnapshots() {
        List<ItemSnapshot> result = new ArrayList<>();
        int index = 0;
        Item current = firstItem;

        while (current != null) {
            if (!current.isDeleted()) {
                String leftId  = index > 0 ? (index - 1) + "@_" : null;
                String rightId = null; // filled in below once we know if there is a next
                result.add(new ItemSnapshot(
                        index + "@_",
                        current.getContent(),
                        leftId,
                        rightId,          // placeholder
                        current.getOperation(),
                        false,
                        current.isBold(),
                        current.isItalic(),
                        current.isUnderline(),
                        current.isStrike(),
                        current.getHeader(),
                        current.getAlign(),
                        current.getList(),
                        current.getIndent(),
                        current.getColor(),
                        current.getBackground()
                ));
                index++;
            }
            current = current.getRight();
        }

        // Back-fill rightId pointers now that all snapshots are built.
        for (int i = 0; i < result.size() - 1; i++) {
            ItemSnapshot snap = result.get(i);
            result.set(i, new ItemSnapshot(
                    snap.id(), snap.content(), snap.leftId(),
                    result.get(i + 1).id(),
                    snap.operation(), snap.deleted(), snap.bold(), snap.italic(),
                    snap.underline(), snap.strike(), snap.header(), snap.align(),
                    snap.list(), snap.indent(), snap.color(), snap.background()
            ));
        }

        log.debug("Produced {} snapshots for persistence", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Compares two items for ordering during a concurrent-insert conflict.
     * Returns > 0 if {@code incoming} should appear AFTER {@code candidate},
     * i.e. the candidate wins the position.
     */
    private int compareItems(Item incoming, Item candidate) {
        String incomingClient  = clientIdOf(incoming.getId());
        String candidateClient = clientIdOf(candidate.getId());
        // Higher clientId (lexicographically) gets priority (comes first).
        return candidateClient.compareTo(incomingClient);
    }

    private String clientIdOf(String itemId) {
        int at = itemId.indexOf('@');
        return at >= 0 ? itemId.substring(at + 1) : itemId;
    }
}
