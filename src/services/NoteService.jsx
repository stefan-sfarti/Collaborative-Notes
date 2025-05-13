// src/services/NoteService.jsx
import axios from 'axios';

const API_URL = 'http://localhost:5000/api';

const api = axios.create({
    baseURL: API_URL
});

// Configure request interceptor to add auth token
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('authToken');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// Configure response interceptor to handle auth errors
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;

        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;
            window.location.href = '/login';
            return Promise.reject(error);
        }

        return Promise.reject(error);
    }
);

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

    getUserDetails: async (userId) => {
        try {
            const response = await api.get(`/users/${userId}`);
            return response.data;
        } catch (error) {
            console.error(`Error fetching user details for ${userId}:`, error);
            throw error;
        }
    }
};

export default NoteService;