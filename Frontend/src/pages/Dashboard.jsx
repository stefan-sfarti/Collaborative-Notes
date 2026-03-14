// src/pages/Dashboard.js
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import NoteService from '../services/NoteService';

function Dashboard() {
    const [notes, setNotes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [createLoading, setCreateLoading] = useState(false);
    const [error, setError] = useState('');
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
                setError('');
            } catch (error) {
                console.error('Error fetching notes:', error);
                setError(`Failed to fetch notes: ${error.response?.data?.message || error.message || 'Network Error'}`);

                // Check if token is invalid
                if (error.response?.status === 401) {
                    showSnackbar('Your session has expired. Please log in again.');
                    setTimeout(() => {
                        handleLogout();
                    }, 2000);
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
            setError('Cannot create note: API server is not available');
            return;
        }

        try {
            setCreateLoading(true);
            const newNote = {
                title: 'New Note',
                content: '',
            };

            // Ensure fresh token before creating note
            await getFreshToken();
            const createdNote = await NoteService.createNote(newNote);
            navigate(`/notes/${createdNote.id}`);
        } catch (error) {
            console.error('Error creating note:', error);
            setError(`Failed to create note: ${error.response?.data?.message || error.message || 'Unknown error'}`);

            if (error.response?.status === 401) {
                showSnackbar('Your session has expired. Please log in again.');
                setTimeout(() => {
                    handleLogout();
                }, 2000);
            }
        } finally {
            setCreateLoading(false);
        }
    };

    const handleDeleteNote = async (noteId, e) => {
        e.preventDefault();
        e.stopPropagation();

        if (!apiStatus) {
            setError('Cannot delete note: API server is not available');
            return;
        }

        try {
            // Ensure fresh token before deleting
            await getFreshToken();
            await NoteService.deleteNote(noteId);
            setNotes(notes.filter(note => note.id !== noteId));
            showSnackbar('Note deleted successfully');
        } catch (error) {
            console.error('Error deleting note:', error);
            setError(`Failed to delete note: ${error.response?.data?.message || error.message || 'Unknown error'}`);

            if (error.response?.status === 401) {
                showSnackbar('Your session has expired. Please log in again.');
                setTimeout(() => {
                    handleLogout();
                }, 2000);
            }
        }
    };

    const handleRefresh = async () => {
        setLoading(true);
        setError('');

        // First check API connectivity
        const isConnected = await NoteService.checkApiConnection();
        setApiStatus(isConnected);

        if (!isConnected) {
            setError('Cannot connect to API server. Please check if the backend is running.');
            setLoading(false);
            return;
        }

        try {
            // Ensure fresh token before fetching
            await getFreshToken();
            const notesData = await NoteService.getAllNotes();
            setNotes(notesData);
            showSnackbar('Notes refreshed successfully');
        } catch (error) {
            setError(`Failed to fetch notes: ${error.response?.data?.message || error.message || 'Network Error'}`);

            if (error.response?.status === 401) {
                showSnackbar('Your session has expired. Please log in again.');
                setTimeout(() => {
                    handleLogout();
                }, 2000);
            }
        } finally {
            setLoading(false);
        }
    };

    const handleLogout = async () => {
        try {
            await logout();
            navigate('/login');
        } catch (error) {
            setError('Failed to log out: ' + error.message);
        }
    };

    const handleCardClick = (noteId) => {
        navigate(`/notes/${noteId}`);
    };

    // Check if user is authenticated
    useEffect(() => {
        if (!currentUser) {
            navigate('/login');
        }
    }, [currentUser, navigate]);

    return (
        <div className="min-h-screen flex flex-col bg-base-200">
            <div className="navbar bg-base-100 shadow-sm">
                <div className="flex-1">
                    <span className="btn btn-ghost normal-case text-xl font-semibold">
                        CollabNotes
                    </span>
                </div>
                <div className="flex-none gap-3 pr-4">
                    <button
                        className="btn btn-ghost btn-sm"
                        onClick={handleRefresh}
                        disabled={loading}
                    >
                        {loading ? (
                            <span className="loading loading-spinner loading-xs" />
                        ) : (
                            <span className="material-symbols-outlined text-base">refresh</span>
                        )}
                        <span className="hidden sm:inline">Refresh</span>
                    </button>
                    {currentUser?.email && (
                        <div className="hidden sm:flex flex-col items-end text-right">
                            <span className="text-sm font-medium">{currentUser.email}</span>
                        </div>
                    )}
                    <button
                        className="btn btn-outline btn-sm"
                        onClick={handleLogout}
                    >
                        Logout
                    </button>
                </div>
            </div>

            <main className="flex-1 px-4 py-6 max-w-6xl mx-auto w-full">
                {error && (
                    <div className="alert alert-error mb-3">
                        <span>{error}</span>
                    </div>
                )}
                {!apiStatus && (
                    <div className="alert alert-warning mb-3">
                        <span>
                            Cannot connect to the API server. Please check if the backend is running.
                        </span>
                    </div>
                )}

                <div className="flex justify-between items-center mb-4 gap-3">
                    <h2 className="text-2xl font-semibold text-base-content">
                        Your Notes
                    </h2>
                    <button
                        className="btn btn-primary"
                        onClick={handleCreateNote}
                        disabled={createLoading || !apiStatus}
                    >
                        {createLoading && (
                            <span className="loading loading-spinner loading-xs mr-2" />
                        )}
                        Create New Note
                    </button>
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
                    <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
                        {notes.map(note => (
                            <article
                                key={note.id}
                                className="card bg-base-100 shadow-sm hover:shadow-lg transition cursor-pointer border border-base-200"
                                onClick={() => handleCardClick(note.id)}
                            >
                                <div className="card-body">
                                    <h3 className="card-title text-base-content/90 truncate">
                                        {note.title || 'Untitled Note'}
                                    </h3>
                                    <p className="text-sm text-base-content/70 line-clamp-3">
                                        {note.content || 'No content'}
                                    </p>
                                </div>
                                <div className="card-actions px-4 pb-3 flex items-center justify-between text-xs text-base-content/60">
                                    <span>
                                        {note.updatedAt
                                            ? new Date(note.updatedAt).toLocaleDateString()
                                            : ''}
                                    </span>
                                    <button
                                        className="btn btn-ghost btn-xs text-error"
                                        onClick={(e) => handleDeleteNote(note.id, e)}
                                    >
                                        Delete
                                    </button>
                                </div>
                            </article>
                        ))}
                    </div>
                )}
            </main>
        </div>
    );
}

export default Dashboard;