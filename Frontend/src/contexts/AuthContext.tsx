import type { ReactNode } from "react";
import {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useRef,
    useState,
} from "react";
import toast from "react-hot-toast";
import { api } from "../services/NoteService";
import type { AuthContextValue, LoginApiResponse, User } from "../types";
import { createApiError } from "../utils/errorUtils";

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);

  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }

  return context;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const logout = useCallback(() => {
    localStorage.removeItem("authToken");
    localStorage.removeItem("authUser");
    setCurrentUser(null);
    setToken(null);
  }, []);

  const logoutRef = useRef<() => void>(logout);
  const last401HandledAtRef = useRef(0);
  useEffect(() => {
    logoutRef.current = logout;
  }, [logout]);

  // Sync auth header with current token
  useEffect(() => {
    if (token) {
      api.defaults.headers.common.Authorization = `Bearer ${token}`;
    } else {
      delete api.defaults.headers.common.Authorization;
    }
  }, [token]);

  // Register 401 interceptor once; use logoutRef to avoid stale closure
  useEffect(() => {
    const responseInterceptor = api.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          const now = Date.now();
          // Coalesce bursts of concurrent 401s from note + lookup requests.
          if (now - last401HandledAtRef.current > 1500) {
            last401HandledAtRef.current = now;
            toast.error("Your session has expired. Please log in again.");
            logoutRef.current();
          }
        }
        return Promise.reject(error);
      },
    );

    return () => {
      api.interceptors.response.eject(responseInterceptor);
    };
  }, []);

  useEffect(() => {
    // Restore local-auth session from storage if present
    const storedToken = localStorage.getItem("authToken");
    const storedUserJson = localStorage.getItem("authUser");

    if (storedToken && storedUserJson) {
      try {
        const storedUser = JSON.parse(storedUserJson) as User;
        // Ensure auth header exists before protected views mount on refresh.
        api.defaults.headers.common.Authorization = `Bearer ${storedToken}`;
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

  const login = async (email: string, password: string): Promise<void> => {
    try {
      setError(null);
      const response = await api.post<LoginApiResponse>("/users/login", { email, password });
      const data = response.data;
      const authToken = data.token || data.accessToken;
      if (!authToken) {
        throw new Error("Login response did not contain a token");
      }

      const userId = data.userId || data.id;
      if (!userId) {
        throw new Error("Login response did not contain a user ID");
      }
      const user: User = data.user || {
        email: data.email || email,
        id: userId,
      };

      api.defaults.headers.common.Authorization = `Bearer ${authToken}`;
      setToken(authToken);
      setCurrentUser(user);
      localStorage.setItem("authToken", authToken);
      localStorage.setItem("authUser", JSON.stringify(user));
    } catch (err) {
      console.error("Local login failed:", err);
      const message = createApiError(err).message;
      setError(message);
      throw new Error(message);
    }
  };

  const register = async (email: string, password: string): Promise<LoginApiResponse> => {
    try {
      setError(null);
      const response = await api.post<LoginApiResponse>("/users/register", { email, password });
      const data = response.data;

      // If backend returns a token on register, treat it like login
      const authToken = data.token || data.accessToken;
      if (authToken) {
        const userId = data.userId || data.id;
        if (!userId) {
          throw new Error("Register response did not contain a user ID");
        }
        const user: User = data.user || {
          email: data.email || email,
          id: userId,
        };
        api.defaults.headers.common.Authorization = `Bearer ${authToken}`;
        setToken(authToken);
        setCurrentUser(user);
        localStorage.setItem("authToken", authToken);
        localStorage.setItem("authUser", JSON.stringify(user));
      }

      return data;
    } catch (err) {
      console.error("Local registration failed:", err);
      const message = createApiError(err).message;
      setError(message);
      throw new Error(message);
    }
  };

  const getFreshToken = async (): Promise<string | null> => {
    try {
      // For local auth, just return the stored token
      return token;
    } catch (err) {
      console.error("Failed to get fresh token:", err);
      logout();
      return null;
    }
  };

  const updateCurrentUser = (user: User) => {
    setCurrentUser(user);
    localStorage.setItem("authUser", JSON.stringify(user));
  };

  const value: AuthContextValue = {
    currentUser,
    token,
    login,
    register,
    logout,
    getFreshToken,
    error,
    isAuthenticated: !!token,
    updateCurrentUser,
  };

  return (
    <AuthContext.Provider value={value}>
      {!loading && children}
    </AuthContext.Provider>
  );
}
