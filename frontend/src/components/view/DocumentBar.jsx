import { Menu, MenuButton, MenuItem, MenuItems } from "@headlessui/react";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import DeleteIcon from "@mui/icons-material/Delete";
import DriveFileRenameOutlineIcon from "@mui/icons-material/DriveFileRenameOutline";
import DoNotTouchIcon from "@mui/icons-material/DoNotTouch";
import VisibilityIcon from "@mui/icons-material/Visibility";
import ShareIcon from "@mui/icons-material/Share";
import { useNavigate } from "react-router-dom";
import { useState } from "react";
import Delete from "./Delete";
import Rename from "./Rename";
import Share from "./Share";

const DocumentBar = ({ index, doc }) => {
  const navigate = useNavigate();
  const [showDelete, setShowDelete] = useState(false);
  const [showRename, setShowRename] = useState(false);
  const [showShare,  setShowShare]  = useState(false);
  const displayName = localStorage.getItem("displayName");

  return (
    <div
      key={index}
      className="flex items-center px-3 py-3 rounded-xl hover:bg-[#f1f3f4] cursor-pointer group transition-colors relative"
      onClick={(e) => {
        if (e.target.closest("button") || e.target.closest("[role='menu']")) return;
        navigate(`/edit/${doc.id}`, { state: doc.title });
      }}
    >
      {/* Doc icon */}
      <div className="w-10 h-10 flex-shrink-0 mr-4 flex items-center justify-center rounded bg-[#e8f0fe]">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="#1a73e8">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zm-1 7H7V7h2v2h4V7h1v2zM7 14h10v2H7v-2zm0 3h7v2H7v-2z"/>
        </svg>
      </div>

      {/* Title */}
      <span className="flex-1 min-w-0 text-[#202124] text-sm font-medium truncate pr-4">
        {doc.title}
      </span>

      {/* Owner */}
      <span className="w-36 hidden sm:block text-[#5f6368] text-sm truncate pr-4">
        {doc.owner}
      </span>

      {/* Shared count */}
      <span className="w-24 hidden md:flex items-center justify-center gap-1 text-[#5f6368] text-sm">
        {doc.sharedWith.length > 0 && (
          <>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" className="opacity-60">
              <path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"/>
            </svg>
            {doc.sharedWith.length}
          </>
        )}
      </span>

      {/* Actions */}
      <div className="w-10 flex justify-end">
        <Delete isOpen={showDelete} setIsOpen={setShowDelete} doc={doc} />
        <Rename isOpen={showRename} setIsOpen={setShowRename} doc={doc} />
        <Share  isOpen={showShare}  setIsOpen={setShowShare}  doc={doc} />

        <Menu as="div" className="relative" onClick={(e) => e.stopPropagation()}>
          <MenuButton className="p-1.5 rounded-full text-[#5f6368] hover:bg-[#e0e0e0] opacity-0 group-hover:opacity-100 transition-opacity">
            <MoreVertIcon fontSize="small" />
          </MenuButton>

          <MenuItems
            anchor="bottom end"
            className="absolute right-0 mt-1 w-52 bg-white rounded-xl border border-[#e0e0e0] shadow-lg py-1 z-50 text-sm text-[#202124]"
          >
            {/* Owner actions */}
            {doc.owner === displayName && (
              <MenuItem>
                {({ active }) => (
                  <button
                    className={`w-full flex items-center gap-3 px-4 py-2 ${active ? "bg-[#f1f3f4]" : ""}`}
                    onClick={() => setShowDelete(true)}
                  >
                    <DeleteIcon fontSize="small" className="text-[#5f6368]" />
                    Delete
                  </button>
                )}
              </MenuItem>
            )}

            {/* Owner or Editor */}
            {(doc.owner === displayName ||
              doc.sharedWith?.some((p) => p.username === displayName && p.permission === "EDIT")) && (
              <>
                <MenuItem>
                  {({ active }) => (
                    <button
                      className={`w-full flex items-center gap-3 px-4 py-2 ${active ? "bg-[#f1f3f4]" : ""}`}
                      onClick={() => setShowRename(true)}
                    >
                      <DriveFileRenameOutlineIcon fontSize="small" className="text-[#5f6368]" />
                      Rename
                    </button>
                  )}
                </MenuItem>
                <MenuItem>
                  {({ active }) => (
                    <button
                      className={`w-full flex items-center gap-3 px-4 py-2 ${active ? "bg-[#f1f3f4]" : ""}`}
                      onClick={() => setShowShare(true)}
                    >
                      <ShareIcon fontSize="small" className="text-[#5f6368]" />
                      Share
                    </button>
                  )}
                </MenuItem>
              </>
            )}

            {/* View-only */}
            {doc.sharedWith?.some((p) => p.username === displayName && p.permission === "VIEW") &&
              doc.owner !== displayName && (
              <MenuItem disabled>
                <div className="flex items-center gap-3 px-4 py-2 text-[#5f6368]">
                  <VisibilityIcon fontSize="small" />
                  View only
                </div>
              </MenuItem>
            )}

            {/* No access */}
            {doc.owner !== displayName &&
              !doc.sharedWith?.some((p) => p.username === displayName) && (
              <MenuItem disabled>
                <div className="flex items-center gap-3 px-4 py-2 text-[#d93025]">
                  <DoNotTouchIcon fontSize="small" />
                  No access
                </div>
              </MenuItem>
            )}
          </MenuItems>
        </Menu>
      </div>
    </div>
  );
};

export default DocumentBar;
