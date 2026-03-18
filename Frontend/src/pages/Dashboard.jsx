// src/pages/Dashboard.js
import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { useAuth } from "../contexts/AuthContext";
import NoteService from "../services/NoteService";
import DashboardNavbar from "../components/DashboardNavbar";
import NoteCard from "../components/NoteCard";
function Dashboard() {
  const [notes, setNotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [createLoading, setCreateLoading] = useState(false);
  const [error, setError] = useState("");
  const [apiStatus, setApiStatus] = useState(true); // Track API connectivity
  const { currentUser, logout, token, getFreshToken } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    const fetchNotes = async () => {
      if (!apiStatus) return;

      try {
        // Ensure we have a fresh token before fetching
        await getFreshToken();
        const notesData = await NoteService.getAllNotes();
        setNotes(notesData);
        setError("");
      } catch (error) {
        console.error("Error fetching notes:", error);
        const errorMsg = `Failed to fetch notes: ${error.response?.data?.message || error.message || "Network Error"}`;
        setError(errorMsg);
        if (error.response?.status !== 401) {
          toast.error(errorMsg);
        }
      } finally {
        setLoading(false);
      }
    };

    if (currentUser && token) {
      fetchNotes();
    }
  }, [currentUser, token, apiStatus]);

  const handleCreateNote = async () => {
    if (!apiStatus) {
      setError("Cannot create note: API server is not available");
      return;
    }

    try {
      setCreateLoading(true);
      const newNote = {
        title: "New Note",
        content: "",
        ownerId: currentUser?.id,
      };

      // Ensure fresh token before creating note
      await getFreshToken();
      const createdNote = await NoteService.createNote(newNote);
      navigate(`/notes/${createdNote.id}`);
    } catch (error) {
      console.error("Error creating note:", error);
      const errorMsg = `Failed to create note: ${error.response?.data?.message || error.message || "Unknown error"}`;
      setError(errorMsg);
      if (error.response?.status !== 401) {
        toast.error(errorMsg);
      }
    } finally {
      setCreateLoading(false);
    }
  };

  const handleDeleteNote = async (noteId, e) => {
    e.preventDefault();
    e.stopPropagation();

    if (!apiStatus) {
      setError("Cannot delete note: API server is not available");
      return;
    }

    try {
      // Ensure fresh token before deleting
      await getFreshToken();
      await NoteService.deleteNote(noteId);
      setNotes(notes.filter((note) => note.id !== noteId));
      toast.success("Note deleted successfully");
    } catch (error) {
      console.error("Error deleting note:", error);
      const errorMsg = `Failed to delete note: ${error.response?.data?.message || error.message || "Unknown error"}`;
      setError(errorMsg);
      if (error.response?.status !== 401) {
        toast.error(errorMsg);
      }
    }
  };

  const handleRefresh = async () => {
    setLoading(true);
    setError("");

    // First check API connectivity
    const isConnected = await NoteService.checkApiConnection();
    setApiStatus(isConnected);

    if (!isConnected) {
      setError(
        "Cannot connect to API server. Please check if the backend is running.",
      );
      setLoading(false);
      return;
    }

    try {
      // Ensure fresh token before fetching
      await getFreshToken();
      const notesData = await NoteService.getAllNotes();
      setNotes(notesData);
      toast.success("Notes refreshed successfully");
    } catch (error) {
      const errorMsg = `Failed to fetch notes: ${error.response?.data?.message || error.message || "Network Error"}`;
      setError(errorMsg);
      if (error.response?.status !== 401) {
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
    } catch (error) {
      setError("Failed to log out: " + error.message);
    }
  };

  const handleCardClick = (noteId) => {
    navigate(`/notes/${noteId}`);
  };

  // Check if user is authenticated
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

      <main className="flex-1 px-3 sm:px-4 py-5 sm:py-6 max-w-6xl mx-auto w-full fade-up">
        {error && (
          <div className="alert alert-error mb-3">
            <span>{error}</span>
          </div>
        )}
        {!apiStatus && (
          <div className="alert alert-warning mb-3">
            <span>
              Cannot connect to the API server. Please check if the backend is
              running.
            </span>
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
              disabled={createLoading || !apiStatus}
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
                className={`text-lg font-semibold ${apiStatus ? "text-success" : "text-error"}`}
              >
                {apiStatus ? "Online" : "Offline"}
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
          <div className="flex justify-center items-center py-16">
            <span className="loading loading-spinner loading-lg" />
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
              disabled={createLoading || !apiStatus}
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
    </div>
  );
}

export default Dashboard;
