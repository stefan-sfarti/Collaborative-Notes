// src/pages/Dashboard.js
import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useNavigate } from "react-router-dom";
import ConfirmModal from "../components/ConfirmModal";
import DashboardNavbar from "../components/DashboardNavbar";
import NoteCard from "../components/NoteCard";
import NoteCardSkeleton from "../components/NoteCardSkeleton";
import OfflineIndicator from "../components/OfflineIndicator";
import { useAuth } from "../contexts/AuthContext";
import NoteService from "../services/NoteService";
import { useWebSocket } from "../services/WebSocketProvider.jsx";
import { createApiError } from "../utils/errorUtils";

function Dashboard() {
  const [notes, setNotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [createLoading, setCreateLoading] = useState(false);
  const [error, setError] = useState("");
  const [noteToDelete, setNoteToDelete] = useState(null);
  const { currentUser, logout, token } = useAuth();
  const { connectionStatus } = useWebSocket();
  const navigate = useNavigate();

  useEffect(() => {
    const fetchNotes = async () => {
      try {
        const notesData = await NoteService.getAllNotes();
        setNotes(notesData);
        setError("");
      } catch (err) {
        const errorMsg = `Failed to fetch notes: ${createApiError(err).message}`;
        setError(errorMsg);
        if (err.status !== 401) {
          toast.error(errorMsg);
        }
      } finally {
        setLoading(false);
      }
    };

    if (currentUser && token) {
      fetchNotes();
    }
  }, [currentUser, token]);

  const handleCreateNote = async () => {
    try {
      setCreateLoading(true);
      const newNote = {
        title: "New Note",
        content: "",
        ownerId: currentUser?.id,
      };
      const createdNote = await NoteService.createNote(newNote);
      navigate(`/notes/${createdNote.id}`);
    } catch (err) {
      const errorMsg = `Failed to create note: ${createApiError(err).message}`;
      setError(errorMsg);
      if (err.status !== 401) {
        toast.error(errorMsg);
      }
    } finally {
      setCreateLoading(false);
    }
  };

  const handleDeleteNote = (noteId) => {
    const note = notes.find((n) => n.id === noteId);
    setNoteToDelete({ id: noteId, title: note?.title || "this note" });
  };

  const confirmDelete = async () => {
    try {
      await NoteService.deleteNote(noteToDelete.id);
      setNotes(notes.filter((note) => note.id !== noteToDelete.id));
      toast.success("Note deleted successfully");
    } catch (err) {
      const errorMsg = `Failed to delete note: ${createApiError(err).message}`;
      setError(errorMsg);
      toast.error(errorMsg);
    } finally {
      setNoteToDelete(null);
    }
  };

  const handleRefresh = async () => {
    setLoading(true);
    setError("");

    try {
      const notesData = await NoteService.getAllNotes();
      setNotes(notesData);
      toast.success("Notes refreshed successfully");
    } catch (err) {
      const errorMsg = `Failed to fetch notes: ${createApiError(err).message}`;
      setError(errorMsg);
      if (err.status !== 401) {
        toast.error(errorMsg);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    try {
      await logout();
      navigate("/login");
    } catch (err) {
      setError("Failed to log out: " + err.message);
    }
  };

  const handleCardClick = (noteId) => {
    navigate(`/notes/${noteId}`);
  };

  useEffect(() => {
    if (!currentUser) {
      navigate("/login");
    }
  }, [currentUser, navigate]);

  return (
    <div className="min-h-screen flex flex-col bg-transparent">
      <DashboardNavbar
        loading={loading}
        onRefresh={handleRefresh}
        currentUser={currentUser}
        onLogout={handleLogout}
      />

      <OfflineIndicator />

      <main className="flex-1 px-3 sm:px-4 py-5 sm:py-6 max-w-6xl mx-auto w-full fade-up">
        {error && (
          <div className="alert alert-error mb-3">
            <span>{error}</span>
          </div>
        )}

        <div className="glass-panel border border-base-300/70 rounded-2xl p-4 sm:p-5 mb-5 sm:mb-6 shadow-sm">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div>
              <p className="text-xs uppercase tracking-[0.18em] text-base-content/60 mb-1">
                Workspace
              </p>
              <h2 className="text-2xl sm:text-3xl font-bold text-base-content leading-tight">
                Your Notes
              </h2>
              <p className="text-sm text-base-content/70 mt-1">
                {notes.length} note{notes.length === 1 ? "" : "s"} synced for{" "}
                {currentUser?.email || "you"}
              </p>
            </div>
            <button
              className="btn btn-primary btn-sm sm:btn-md"
              onClick={handleCreateNote}
              disabled={createLoading}
            >
              {createLoading && (
                <span className="loading loading-spinner loading-xs mr-2" />
              )}
              Create New Note
            </button>
          </div>

          <div className="mt-4 grid grid-cols-2 sm:grid-cols-3 gap-2">
            <div className="rounded-xl border border-base-300/60 bg-base-100/70 px-3 py-2">
              <p className="text-[11px] uppercase tracking-wide text-base-content/60">
                Total Notes
              </p>
              <p className="text-lg font-semibold">{notes.length}</p>
            </div>
            <div className="rounded-xl border border-base-300/60 bg-base-100/70 px-3 py-2">
              <p className="text-[11px] uppercase tracking-wide text-base-content/60">
                Sync Status
              </p>
              <p
                className={`text-lg font-semibold ${connectionStatus === "connected" ? "text-success" : "text-warning"}`}
              >
                {connectionStatus === "connected" ? "Online" : "Reconnecting"}
              </p>
            </div>
            <div className="rounded-xl border border-base-300/60 bg-base-100/70 px-3 py-2 col-span-2 sm:col-span-1">
              <p className="text-[11px] uppercase tracking-wide text-base-content/60">
                View
              </p>
              <p className="text-lg font-semibold">Collaborative</p>
            </div>
          </div>
        </div>

        {loading ? (
          <div className="grid gap-4 sm:gap-5 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <NoteCardSkeleton key={i} />
            ))}
          </div>
        ) : notes.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <p className="text-lg font-medium text-base-content/80 mb-2">
              No notes yet
            </p>
            <p className="text-sm text-base-content/60 mb-4">
              Create your first note to get started.
            </p>
            <button
              className="btn btn-outline btn-primary"
              onClick={handleCreateNote}
              disabled={createLoading}
            >
              Create a note
            </button>
          </div>
        ) : (
          <div className="grid gap-4 sm:gap-5 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
            {notes.map((note) => (
              <NoteCard
                key={note.id}
                note={note}
                onClick={() => handleCardClick(note.id)}
                onDelete={handleDeleteNote}
              />
            ))}
          </div>
        )}
      </main>

      <ConfirmModal
        isOpen={!!noteToDelete}
        title="Delete Note"
        message={`Are you sure you want to delete "${noteToDelete?.title}"? This cannot be undone.`}
        confirmLabel="Delete"
        confirmClass="btn-error"
        onConfirm={confirmDelete}
        onCancel={() => setNoteToDelete(null)}
      />
    </div>
  );
}

export default Dashboard;
