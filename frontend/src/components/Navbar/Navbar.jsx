import docsIcon from "../../assets/doc_image.png";
import { useNavigate } from "react-router-dom";
import { useState } from "react";
import Popover from "@mui/material/Popover";
import { useDispatch } from "react-redux";
import { LOGOUT } from "../../Redux/Auth/ActionType";

export default function NavBar({ usernames }) {
  const displayName = localStorage.getItem("displayName");
  const dispatch    = useDispatch();
  const navigate    = useNavigate();

  const [anchorEl, setAnchorEl] = useState(null);
  const open = Boolean(anchorEl);

  return (
    <div className="sticky top-0 z-50 bg-white border-b border-[#e0e0e0] flex items-center justify-between px-6 h-16 shadow-sm">

      {/* Left — logo + brand */}
      <div
        className="flex items-center gap-3 cursor-pointer select-none"
        onClick={() => navigate("/")}
      >
        <img src={docsIcon} alt="Docs" width={36} height={36} />
        <span className="text-[#202124] text-xl font-normal tracking-tight">Docs</span>
      </div>

      {/* Right — active users + user info */}
      <div className="flex items-center gap-4">
        {usernames && usernames.length > 0 && (
          <>
            <button
              onClick={(e) => setAnchorEl(e.currentTarget)}
              className="flex items-center gap-2 text-sm text-[#1a73e8] bg-[#e8f0fe] hover:bg-[#d2e3fc] px-3 py-1.5 rounded-full transition-colors font-medium"
            >
              <span className="w-2 h-2 rounded-full bg-[#1a73e8] inline-block" />
              {usernames.length} active
            </button>
            <Popover
              open={open}
              anchorEl={anchorEl}
              onClose={() => setAnchorEl(null)}
              anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
              transformOrigin={{ vertical: "top", horizontal: "right" }}
              PaperProps={{
                style: {
                  borderRadius: 12,
                  boxShadow: "0 4px 12px rgba(0,0,0,.15)",
                  minWidth: 160,
                  padding: "8px 0",
                },
              }}
            >
              <div className="px-4 py-2">
                <p className="text-xs text-[#5f6368] font-medium uppercase tracking-wide mb-2">
                  Active users
                </p>
                {usernames.map((user, i) => (
                  <div key={i} className="flex items-center gap-2 py-1">
                    <div className="w-6 h-6 rounded-full bg-[#1a73e8] text-white text-xs flex items-center justify-center font-semibold">
                      {user[0]?.toUpperCase()}
                    </div>
                    <p className="text-[#3c4043] text-sm">{user}</p>
                  </div>
                ))}
              </div>
            </Popover>
          </>
        )}

        <button
          onClick={() => dispatch({ type: LOGOUT })}
          className="text-sm text-[#3c4043] hover:bg-[#f1f3f4] px-3 py-1.5 rounded-full transition-colors"
        >
          Sign out
        </button>

        <div
          className="w-8 h-8 rounded-full bg-[#1a73e8] text-white text-sm font-semibold flex items-center justify-center uppercase select-none"
          title={displayName}
        >
          {displayName?.[0]}
        </div>
      </div>
    </div>
  );
}
