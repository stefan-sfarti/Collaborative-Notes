import axios from "axios";
import { User } from "../types";

const API_URL = import.meta.env.VITE_API_URL || "http://localhost:5000/api";

const apiClient = axios.create({
  baseURL: `${API_URL}/users`,
  headers: {
    "Content-Type": "application/json",
  },
});

apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("authToken");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

export const UserService = {
  getCurrentUser: async (): Promise<User> => {
    const response = await apiClient.get<User>("/me");
    return response.data;
  },

  updateProfile: async (email: string, displayName: string): Promise<User> => {
    const response = await apiClient.put<User>("/me", { email, displayName });
    return response.data;
  },
};
