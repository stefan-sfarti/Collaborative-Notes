import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import toast from "react-hot-toast";
import { api, API_URL } from "../services/NoteService";

const AuthContext = createContext();

export function useAuth() {
  return useContext(AuthContext);
}

export function AuthProvider({ children }) {
  const [currentUser, setCurrentUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const logout = useCallback(() => {
    localStorage.removeItem("authToken");
    localStorage.removeItem("authUser");
    setCurrentUser(null);
    setToken(null);
  }, []);

  useEffect(() => {
    // Attach auth token to axios instance for all API calls
    if (token) {
      api.defaults.headers.common.Authorization = `Bearer ${token}`;
    } else {
      delete api.defaults.headers.common.Authorization;
    }

    // Add response interceptor for global 401 handling
    const responseInterceptor = api.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          toast.error("Your session has expired. Please log in again.");
          logout();
        }
        return Promise.reject(error);
      },
    );

    return () => {
      api.interceptors.response.eject(responseInterceptor);
    };
  }, [token, logout]);

  useEffect(() => {
    // Restore local-auth session from storage if present
    const storedToken = localStorage.getItem("authToken");
    const storedUserJson = localStorage.getItem("authUser");

    if (storedToken && storedUserJson) {
      try {
        const storedUser = JSON.parse(storedUserJson);
        setToken(storedToken);
        setCurrentUser(storedUser);
        setLoading(false);
        return;
      } catch {
        localStorage.removeItem("authToken");
        localStorage.removeItem("authUser");
      }
    }

    setLoading(false);
  }, []);

  const login = async (email, password) => {
    try {
      setError(null);
      const response = await fetch(`${API_URL}/users/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        const errorBody = await response.json().catch(() => ({}));
        const message = errorBody.message || "Login failed";
        throw new Error(message);
      }

      const data = await response.json();
      const authToken = data.token || data.accessToken;
      const user = data.user || {
        email: data.email || email,
        id: data.userId || data.id,
      };

      if (!authToken) {
        throw new Error("Login response did not contain a token");
      }

      setToken(authToken);
      setCurrentUser(user);
      localStorage.setItem("authToken", authToken);
      localStorage.setItem("authUser", JSON.stringify(user));
    } catch (err) {
      console.error("Local login failed:", err);
      setError(err.message);
      throw err;
    }
  };

  const register = async (email, password) => {
    try {
      setError(null);
      const response = await fetch(`${API_URL}/users/register`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        const errorBody = await response.json().catch(() => ({}));
        const message = errorBody.message || "Registration failed";
        throw new Error(message);
      }

      const data = await response.json();

      // If backend returns a token on register, treat it like login
      const authToken = data.token || data.accessToken;
      const user = data.user || {
        email: data.email || email,
        id: data.userId || data.id,
      };

      if (authToken) {
        setToken(authToken);
        setCurrentUser(user);
        localStorage.setItem("authToken", authToken);
        localStorage.setItem("authUser", JSON.stringify(user));
      }

      return data;
    } catch (err) {
      console.error("Local registration failed:", err);
      setError(err.message);
      throw err;
    }
  };

  const getFreshToken = async () => {
    try {
      // For local auth, just return the stored token
      return token;
    } catch (err) {
      console.error("Failed to get fresh token:", err);
      await logout();
      return null;
    }
  };

  const value = {
    currentUser,
    token,
    login,
    register,
    logout,
    getFreshToken,
    error,
    isAuthenticated: !!token,
  };

  return (
    <AuthContext.Provider value={value}>
      {!loading && children}
    </AuthContext.Provider>
  );
}
