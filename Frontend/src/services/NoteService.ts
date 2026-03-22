import axios from "axios";
import { createApiError } from "../utils/errorUtils";
import type { Note, NotePayload, UserResponse } from "../types";

export const API_URL =
  import.meta.env.VITE_API_URL || "http://localhost:5000/api";

export const api = axios.create({
  baseURL: API_URL,
});

const _handleError = (label: string, error: unknown): never => {
  console.error(label, error);
  throw createApiError(error);
};

const NoteService = {
  // Note CRUD operations
  createNote: async (noteData: NotePayload): Promise<Note> => {
    try {
      const response = await api.post<Note>("/notes", noteData);
      return response.data;
    } catch (error) {
      return _handleError("Error creating note:", error);
    }
  },

  getNoteById: async (noteId: string): Promise<Note> => {
    try {
      const response = await api.get<Note>(`/notes/${noteId}`);
      return response.data;
    } catch (error) {
      return _handleError(`Error fetching note ${noteId}:`, error);
    }
  },

  getAllNotes: async (): Promise<Note[]> => {
    try {
      const response = await api.get<Note[]>("/notes");
      return response.data;
    } catch (error) {
      return _handleError("Error fetching notes:", error);
    }
  },

  updateNote: async (noteId: string, noteData: Partial<Note>): Promise<Note> => {
    try {
      const response = await api.put<Note>(`/notes/${noteId}`, noteData);
      return response.data;
    } catch (error) {
      return _handleError(`Error updating note ${noteId}:`, error);
    }
  },

  deleteNote: async (noteId: string): Promise<void> => {
    try {
      await api.delete(`/notes/${noteId}`);
    } catch (error) {
      _handleError(`Error deleting note ${noteId}:`, error);
    }
  },

  checkApiConnection: async (): Promise<boolean> => {
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
  inviteCollaborator: async (noteId: string, email: string): Promise<void> => {
    try {
      await api.post(`/notes/${noteId}/invite`, { email });
    } catch (error) {
      _handleError(`Error inviting collaborator to note ${noteId}:`, error);
    }
  },

  addCollaborator: async (noteId: string, collaboratorId: string): Promise<Note> => {
    try {
      const response = await api.post<Note>(
        `/notes/${noteId}/collaborators/${collaboratorId}`,
        {},
      );
      return response.data;
    } catch (error) {
      return _handleError(`Error adding collaborator to note ${noteId}:`, error);
    }
  },

  removeCollaborator: async (noteId: string, collaboratorId: string): Promise<void> => {
    try {
      await api.delete(`/notes/${noteId}/collaborators/${collaboratorId}`);
    } catch (error) {
      _handleError(`Error removing collaborator from note ${noteId}:`, error);
    }
  },

  // User operations
  lookupUserByEmail: async (email: string): Promise<UserResponse> => {
    try {
      const response = await api.post<UserResponse>("/users/lookup", { email });
      return response.data;
    } catch (error) {
      return _handleError("Error looking up user by email:", error);
    }
  },

  lookupUserById: async (userId: string): Promise<UserResponse> => {
    try {
      const response = await api.get<UserResponse>(`/users/lookup/${userId}`);
      return response.data;
    } catch (error) {
      return _handleError("Error looking up user by ID:", error);
    }
  },

  getCurrentUser: async (): Promise<UserResponse> => {
    try {
      const response = await api.get<UserResponse>("/users/me");
      return response.data;
    } catch (error) {
      return _handleError("Error fetching current user:", error);
    }
  },
};

export default NoteService;
