import axios from 'axios';

export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:5000/api';

export const api = axios.create({
    baseURL: API_URL
});

const NoteService = {
    // Note CRUD operations
    createNote: async (noteData) => {
        try {
            const response = await api.post('/notes', noteData);
            return response.data;
        } catch (error) {
            console.error('Error creating note:', error);
            throw error;
        }
    },

    getNoteById: async (noteId) => {
        try {
            const response = await api.get(`/notes/${noteId}`);
            return response.data;
        } catch (error) {
            console.error(`Error fetching note ${noteId}:`, error);
            throw error;
        }
    },

    getAllNotes: async () => {
        try {
            const response = await api.get('/notes');
            return response.data;
        } catch (error) {
            console.error('Error fetching notes:', error);
            throw error;
        }
    },

    updateNote: async (noteId, noteData) => {
        try {
            const response = await api.put(`/notes/${noteId}`, noteData);
            return response.data;
        } catch (error) {
            console.error(`Error updating note ${noteId}:`, error);
            throw error;
        }
    },

    deleteNote: async (noteId) => {
        try {
            const response = await api.delete(`/notes/${noteId}`);
            return response.data;
        } catch (error) {
            console.error(`Error deleting note ${noteId}:`, error);
            throw error;
        }
    },

    // Collaborator operations
    addCollaborator: async (noteId, collaboratorId) => {
        try {
            const response = await api.post(
                `/notes/${noteId}/collaborators/${collaboratorId}`,
                {}
            );
            return response.data;
        } catch (error) {
            console.error(`Error adding collaborator to note ${noteId}:`, error);
            throw error;
        }
    },

    removeCollaborator: async (noteId, collaboratorId) => {
        try {
            const response = await api.delete(
                `/notes/${noteId}/collaborators/${collaboratorId}`
            );
            return response.data;
        } catch (error) {
            console.error(`Error removing collaborator from note ${noteId}:`, error);
            throw error;
        }
    },

    // User operations
    lookupUserByEmail: async (email) => {
        try {
            const response = await api.post('/users/lookup', { email });
            return response.data;
        } catch (error) {
            console.error('Error looking up user by email:', error);
            throw error;
        }
    },

    lookupUserById: async (userId) => {
        try {
            const response = await api.get(`/users/lookup/${userId}`);
            return response.data;
        } catch (error) {
            console.error('Error looking up user by ID:', error);
            throw error;
        }
    },

    getCurrentUser: async () => {
        try {
            const response = await api.get('/users/me');
            return response.data;
        } catch (error) {
            console.error('Error fetching current user:', error);
            throw error;
        }
    }
};

export default NoteService;
