import { useNavigate } from "react-router-dom";
import type { ConnectionStatus } from "../types";

interface EditorHeaderProps {
  isOwner: boolean;
  saving: boolean;
  isNoteModified: boolean;
  connectionStatus: ConnectionStatus;
  onForceSave: () => void;
  saveError: string | null;
}

const EditorHeader = ({
  isOwner,
  saving,
  isNoteModified,
  connectionStatus,
  onForceSave,
  saveError,
}: EditorHeaderProps) => {
  const navigate = useNavigate();

  const getStatusText = () => {
    if (saving) return "Saving...";
    if (isNoteModified) return "Modified";
    if (connectionStatus === "connected") return "Connected";
    if (connectionStatus === "reconnecting") return "Reconnecting...";
    return "Disconnected";
  };

  const getBadgeClass = () => {
    if (saving) return "badge-info";
    if (isNoteModified) return "badge-warning";
    if (connectionStatus === "connected") return "badge-success";
    return "badge-ghost";
  };

  return (
    <>
      <div className="navbar glass-panel shadow-sm border-b border-base-300/70 px-2 sm:px-3 gap-2 flex-wrap sticky top-0 z-20">
        <div className="flex-none">
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            onClick={() => navigate("/dashboard")}
            title="Back to Dashboard"
          >
            ←
          </button>
        </div>
        <div className="flex-1 min-w-0">
          <span className="font-semibold text-sm sm:text-base truncate block tracking-wide">
            {isOwner ? "Edit Note" : "View Note"}
          </span>
        </div>
        <div className="flex-none flex items-center gap-2 pr-1 sm:pr-2">
          <span className={`badge badge-sm font-mono-ui ${getBadgeClass()}`}>
            {getStatusText()}
          </span>
          {isNoteModified && (
            <button
              type="button"
              className="btn btn-outline btn-sm px-2 sm:px-3"
              onClick={onForceSave}
              title="Save Note"
            >
              Save
            </button>
          )}
        </div>
      </div>

      {saveError && (
        <div className="alert alert-error mx-3 mt-2 flex items-center justify-between">
          <span>{saveError}</span>
          <button
            type="button"
            className="btn btn-sm btn-ghost"
            onClick={onForceSave}
          >
            Retry
          </button>
        </div>
      )}
    </>
  );
};

export default EditorHeader;
