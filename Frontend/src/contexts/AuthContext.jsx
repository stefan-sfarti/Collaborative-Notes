import { createContext, useContext, useEffect, useRef, useState } from 'react';
import keycloak from '../keycloak';
import { api, API_URL } from '../services/NoteService';

const AuthContext = createContext();

export function useAuth() {
    return useContext(AuthContext);
}

export function AuthProvider({ children }) {
    const [currentUser, setCurrentUser] = useState(null);
    const [token, setToken] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const initialized = useRef(false);

    useEffect(() => {
        // Attach auth token to axios instance for all API calls
        if (token) {
            api.defaults.headers.common.Authorization = `Bearer ${token}`;
        } else {
            delete api.defaults.headers.common.Authorization;
        }
    }, [token]);

    useEffect(() => {
        if (initialized.current) {
            return;
        }
        initialized.current = true;

        const initKeycloak = async () => {
            try {
                const authenticated = await keycloak.init({
                    onLoad: 'check-sso',
                    silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
                    pkceMethod: 'S256',
                    flow: 'standard'
                });

                if (authenticated) {
                    setCurrentUser(keycloak.tokenParsed);
                    setToken(keycloak.token);
                }
            } catch (err) {
                console.error('Keycloak initialization failed:', err);
                setError(err.message);
            } finally {
                setLoading(false);
            }
        };

        // First, try to restore a local-auth session
        const storedToken = localStorage.getItem('authToken');
        const storedUserJson = localStorage.getItem('authUser');

        if (storedToken && storedUserJson) {
            try {
                const storedUser = JSON.parse(storedUserJson);
                setToken(storedToken);
                setCurrentUser(storedUser);
                setLoading(false);
                return;
            } catch {
                localStorage.removeItem('authToken');
                localStorage.removeItem('authUser');
            }
        }

        // Fallback to Keycloak SSO flow
        initKeycloak();

        const interval = setInterval(() => {
            if (keycloak.isTokenExpired()) {
                keycloak.updateToken(30)
                    .then((refreshed) => {
                        if (refreshed) {
                            setToken(keycloak.token);
                            setCurrentUser(keycloak.tokenParsed);
                        }
                    })
                    .catch((err) => {
                        console.error('Token refresh failed:', err);
                        logout();
                    });
            }
        }, 60000);

        return () => clearInterval(interval);
    }, []);

    const login = async () => {
        try {
            await keycloak.login({
                redirectUri: window.location.origin
            });
        } catch (err) {
            console.error('Login failed:', err);
            setError(err.message);
            throw err;
        }
    };

    const localLogin = async (email, password) => {
        try {
            setError(null);
            const response = await fetch(`${API_URL}/users/login`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ email, password })
            });

            if (!response.ok) {
                const errorBody = await response.json().catch(() => ({}));
                const message = errorBody.message || 'Login failed';
                throw new Error(message);
            }

            const data = await response.json();
            const authToken = data.token || data.accessToken;
            const user =
                data.user ||
                {
                    email: data.email || email,
                    id: data.userId || data.id
                };

            if (!authToken) {
                throw new Error('Login response did not contain a token');
            }

            setToken(authToken);
            setCurrentUser(user);
            localStorage.setItem('authToken', authToken);
            localStorage.setItem('authUser', JSON.stringify(user));
        } catch (err) {
            console.error('Local login failed:', err);
            setError(err.message);
            throw err;
        }
    };

    const localRegister = async (email, password) => {
        try {
            setError(null);
            const response = await fetch(`${API_URL}/users/register`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ email, password })
            });

            if (!response.ok) {
                const errorBody = await response.json().catch(() => ({}));
                const message = errorBody.message || 'Registration failed';
                throw new Error(message);
            }

            const data = await response.json();

            // If backend returns a token on register, treat it like login
            const authToken = data.token || data.accessToken;
            const user =
                data.user ||
                {
                    email: data.email || email,
                    id: data.userId || data.id
                };

            if (authToken) {
                setToken(authToken);
                setCurrentUser(user);
                localStorage.setItem('authToken', authToken);
                localStorage.setItem('authUser', JSON.stringify(user));
            }

            return data;
        } catch (err) {
            console.error('Local registration failed:', err);
            setError(err.message);
            throw err;
        }
    };

    const register = async () => {
        try {
            await keycloak.register({
                redirectUri: window.location.origin
            });
        } catch (err) {
            console.error('Registration failed:', err);
            setError(err.message);
            throw err;
        }
    };

    const logout = async () => {
        try {
            localStorage.removeItem('authToken');
            localStorage.removeItem('authUser');
            await keycloak.logout({
                redirectUri: window.location.origin
            });
            setCurrentUser(null);
            setToken(null);
        } catch (err) {
            console.error('Logout failed:', err);
        }
    };

    const getFreshToken = async () => {
        try {
            // For local auth, just return the stored token
            if (token && (!currentUser || !currentUser.preferred_username)) {
                return token;
            }

            if (keycloak.isTokenExpired()) {
                const refreshed = await keycloak.updateToken(30);
                if (refreshed) {
                    setToken(keycloak.token);
                    return keycloak.token;
                }
            }
            return keycloak.token;
        } catch (err) {
            console.error('Failed to get fresh token:', err);
            await logout();
            return null;
        }
    };

    const value = {
        currentUser,
        token,
        login,
        localLogin,
        register,
        localRegister,
        logout,
        getFreshToken,
        error,
        isAuthenticated: !!token
    };

    return (
        <AuthContext.Provider value={value}>
            {!loading && children}
        </AuthContext.Provider>
    );
}
