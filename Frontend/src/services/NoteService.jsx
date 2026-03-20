import axios from "axios";
import { createApiError } from "../utils/errorUtils";

export const API_URL =
  import.meta.env.VITE_API_URL || "http://localhost:5000/api";

export const api = axios.create({
  baseURL: API_URL,
});

const _handleError = (label, error) => {
  console.error(label, error);
  throw createApiError(error);
};

const NoteService = {
  // Note CRUD operations
  createNote: async (noteData) => {
    try {
      const response = await api.post("/notes", noteData);
      return response.data;
    } catch (error) {
      _handleError("Error creating note:", error);
    }
  },

  getNoteById: async (noteId) => {
    try {
      const response = await api.get(`/notes/${noteId}`);
      return response.data;
    } catch (error) {
      _handleError(`Error fetching note ${noteId}:`, error);
    }
  },

  getAllNotes: async () => {
    try {
      const response = await api.get("/notes");
      return response.data;
    } catch (error) {
      _handleError("Error fetching notes:", error);
    }
  },

  updateNote: async (noteId, noteData) => {
    try {
      const response = await api.put(`/notes/${noteId}`, noteData);
      return response.data;
    } catch (error) {
      _handleError(`Error updating note ${noteId}:`, error);
    }
  },

  deleteNote: async (noteId) => {
    try {
      const response = await api.delete(`/notes/${noteId}`);
      return response.data;
    } catch (error) {
      _handleError(`Error deleting note ${noteId}:`, error);
    }
  },

  checkApiConnection: async () => {
    try {
      const response = await api.get("/notes", {
        timeout: 5000,
        validateStatus: () => true,
      });

      // 2xx/3xx => reachable and successful.
      // 401/403 => reachable but unauthorized (still indicates API is up).
      return response.status < 500;
    } catch (error) {
      console.error("API connectivity check failed:", error);
      return false;
    }
  },

  // Collaborator operations
  inviteCollaborator: async (noteId, email) => {
    try {
      const response = await api.post(`/notes/${noteId}/invite`, { email });
      return response.data;
    } catch (error) {
      _handleError(`Error inviting collaborator to note ${noteId}:`, error);
    }
  },

  addCollaborator: async (noteId, collaboratorId) => {
    try {
      const response = await api.post(
        `/notes/${noteId}/collaborators/${collaboratorId}`,
        {},
      );
      return response.data;
    } catch (error) {
      _handleError(`Error adding collaborator to note ${noteId}:`, error);
    }
  },

  removeCollaborator: async (noteId, collaboratorId) => {
    try {
      const response = await api.delete(
        `/notes/${noteId}/collaborators/${collaboratorId}`,
      );
      return response.data;
    } catch (error) {
      _handleError(`Error removing collaborator from note ${noteId}:`, error);
    }
  },

  // User operations
  lookupUserByEmail: async (email) => {
    try {
      const response = await api.post("/users/lookup", { email });
      return response.data;
    } catch (error) {
      _handleError("Error looking up user by email:", error);
    }
  },

  lookupUserById: async (userId) => {
    try {
      const response = await api.get(`/users/lookup/${userId}`);
      return response.data;
    } catch (error) {
      _handleError("Error looking up user by ID:", error);
    }
  },

  getCurrentUser: async () => {
    try {
      const response = await api.get("/users/me");
      return response.data;
    } catch (error) {
      _handleError("Error fetching current user:", error);
    }
  },
};

export default NoteService;
