import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from "@headlessui/react";
import { useEffect, useState } from "react";
import DocumentBar from "../components/view/DocumentBar";
import CreateDoc from "../components/view/CreateDoc";
import { useDispatch, useSelector } from "react-redux";
import { getAllDocs } from "../Redux/Document/Action";
import { toast } from "sonner";

const DocThumbnail = () => (
  <svg viewBox="0 0 108 136" fill="none" xmlns="http://www.w3.org/2000/svg" className="w-full h-full">
    <rect width="108" height="136" fill="#fff"/>
    <rect x="16" y="22" width="76" height="6" rx="2" fill="#e8eaed"/>
    <rect x="16" y="34" width="60" height="5" rx="2" fill="#e8eaed"/>
    <rect x="16" y="46" width="76" height="4" rx="2" fill="#f1f3f4"/>
    <rect x="16" y="56" width="64" height="4" rx="2" fill="#f1f3f4"/>
    <rect x="16" y="66" width="70" height="4" rx="2" fill="#f1f3f4"/>
    <rect x="16" y="76" width="50" height="4" rx="2" fill="#f1f3f4"/>
    <rect x="16" y="90" width="76" height="4" rx="2" fill="#f1f3f4"/>
    <rect x="16" y="100" width="58" height="4" rx="2" fill="#f1f3f4"/>
    <rect x="16" y="110" width="72" height="4" rx="2" fill="#f1f3f4"/>
  </svg>
);

const View = () => {
  const options = ["All documents", "Owned by me", "Shared with me"];
  const [selectedOption, setSelectedOption] = useState(options[0]);
  const displayName = localStorage.getItem("displayName");
  const [showCreateDoc, setShowCreateDoc] = useState(false);
  const dispatch = useDispatch();
  const docStore = useSelector((store) => store.docStore);

  useEffect(() => {
    dispatch(getAllDocs()).then(() => {
      toast.success("Documents loaded");
    });
  }, [dispatch]);

  const filteredDocs = docStore?.allDocs?.filter((doc) => {
    if (selectedOption === options[1]) return doc.owner === displayName;
    if (selectedOption === options[2]) return doc.sharedWith.some((s) => s.username === displayName);
    return true;
  }) ?? [];

  return (
    <div className="min-h-screen bg-[#f8f9fa]">

      {/* ── Recent docs header ── */}
      <div className="bg-[#f8f9fa] border-b border-[#e0e0e0] py-6 px-8">
        <div className="max-w-5xl mx-auto flex items-center justify-between">
          <h2 className="text-[#202124] text-xl font-medium">Recent documents</h2>

          <Listbox value={selectedOption} onChange={setSelectedOption}>
            <div className="relative">
              <ListboxButton className="flex items-center gap-2 bg-white border border-[#dadce0] rounded-lg px-4 py-2 text-sm text-[#3c4043] hover:bg-[#f8f9fa] transition-colors shadow-sm">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="#5f6368">
                  <path d="M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z"/>
                </svg>
                {selectedOption}
                <svg width="14" height="14" viewBox="0 0 24 24" fill="#5f6368">
                  <path d="M7 10l5 5 5-5z"/>
                </svg>
              </ListboxButton>
              <ListboxOptions
                anchor="bottom"
                className="absolute right-0 mt-1 w-48 bg-white rounded-lg shadow-lg border border-[#dadce0] py-1 z-20 text-sm text-[#3c4043]"
              >
                {options.map((opt) => (
                  <ListboxOption
                    key={opt}
                    value={opt}
                    className={({ active }) =>
                      `px-4 py-2 cursor-pointer ${active ? "bg-[#f1f3f4]" : ""} ${opt === selectedOption ? "text-[#1a73e8] font-medium" : ""}`
                    }
                  >
                    {opt}
                  </ListboxOption>
                ))}
              </ListboxOptions>
            </div>
          </Listbox>
        </div>
      </div>

      <div className="max-w-5xl mx-auto px-8 py-6">

        {/* ── Empty state ── */}
        {filteredDocs.length === 0 && (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <svg width="80" height="80" viewBox="0 0 24 24" fill="#dadce0" className="mb-4">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14,2 14,8 20,8" stroke="#dadce0" strokeWidth="2" fill="none"/>
            </svg>
            <p className="text-[#3c4043] text-lg font-medium mb-1">No documents yet</p>
            <p className="text-[#5f6368] text-sm">Click the + button below to create your first document</p>
          </div>
        )}

        {/* ── Document grid ── */}
        {filteredDocs.length > 0 && (
          <>
            {/* Column headers */}
            <div className="flex items-center px-3 mb-2 text-xs text-[#5f6368] font-medium uppercase tracking-wide">
              <span className="flex-1 min-w-0">Name</span>
              <span className="w-36 hidden sm:block">Owner</span>
              <span className="w-24 hidden md:block text-center">Shared</span>
              <span className="w-10"/>
            </div>

            {/* Document rows */}
            <div className="flex flex-col gap-1">
              {filteredDocs.map((doc, index) => (
                <DocumentBar key={doc.id ?? index} index={index} doc={doc} />
              ))}
            </div>
          </>
        )}
      </div>

      {/* ── Create doc FAB ── */}
      <CreateDoc isOpen={showCreateDoc} setIsOpen={setShowCreateDoc} />
      <button
        className="fixed right-8 bottom-8 flex items-center gap-3 bg-[#c2e7ff] text-[#001d35] rounded-2xl px-5 py-4 shadow-md hover:shadow-lg transition-shadow font-medium text-sm"
        onClick={() => setShowCreateDoc(true)}
        title="Create new document"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
          <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-2 10h-4v4h-2v-4H7v-2h4V7h2v4h4v2z"/>
        </svg>
        New document
      </button>
    </div>
  );
};

export default View;
