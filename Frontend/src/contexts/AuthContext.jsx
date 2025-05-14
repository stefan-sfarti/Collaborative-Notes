// src/contexts/AuthContext.js
import React, { createContext, useContext, useState, useEffect } from 'react';
import { initializeApp } from 'firebase/app';
import {
    getAuth,
    createUserWithEmailAndPassword,
    signInWithEmailAndPassword,
    signOut,
    onAuthStateChanged,
    GoogleAuthProvider,
    signInWithPopup,
    setPersistence,
    browserLocalPersistence
} from 'firebase/auth';
import { getPerformance } from "firebase/performance";



const firebaseConfig = {
    apiKey: "AIzaSyAqollAFdsQanUOl6Ov5IEd49ikm8mKLas",
    authDomain: "focus-poet-457511-n7.firebaseapp.com",
    projectId: "focus-poet-457511-n7",
    storageBucket: "focus-poet-457511-n7.firebasestorage.app",
    messagingSenderId: "98546797256",
    appId: "1:98546797256:web:bfb0402e25171cdb2933fd",
    measurementId: "G-2BMVQZCGSH"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const googleProvider = new GoogleAuthProvider();
const perf = getPerformance(app);

// Set persistence to LOCAL to keep the user signed in
setPersistence(auth, browserLocalPersistence);

const AuthContext = createContext();

// Token storage keys
const TOKEN_STORAGE_KEY = 'authToken';
const TOKEN_EXPIRY_KEY = 'authTokenExpiry';
const TOKEN_REFRESH_MARGIN = 10 * 60 * 1000; // 10 minutes in milliseconds

export function useAuth() {
    return useContext(AuthContext);
}

export function AuthProvider({ children }) {
    const [currentUser, setCurrentUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [token, setToken] = useState(() => {
        // Initialize token from localStorage if available and not expired
        const storedToken = localStorage.getItem(TOKEN_STORAGE_KEY);
        const expiryTime = localStorage.getItem(TOKEN_EXPIRY_KEY);

        if (storedToken && expiryTime && new Date().getTime() < parseInt(expiryTime)) {
            return storedToken;
        }

        return null;
    });

    // Save token to localStorage with expiry time
    const saveToken = (newToken) => {
        setToken(newToken);

        if (newToken) {
            // Set expiry to 1 hour from now
            const expiryTime = new Date().getTime() + 60 * 60 * 1000;
            localStorage.setItem(TOKEN_STORAGE_KEY, newToken);
            localStorage.setItem(TOKEN_EXPIRY_KEY, expiryTime.toString());
        } else {
            localStorage.removeItem(TOKEN_STORAGE_KEY);
            localStorage.removeItem(TOKEN_EXPIRY_KEY);
        }
    };

    // Check if token needs refreshing
    const isTokenExpiringSoon = () => {
        const expiryTime = localStorage.getItem(TOKEN_EXPIRY_KEY);
        if (!expiryTime) return true;

        const currentTime = new Date().getTime();
        const timeUntilExpiry = parseInt(expiryTime) - currentTime;

        return timeUntilExpiry < TOKEN_REFRESH_MARGIN;
    };

    // Get a fresh token (useful for making API calls)
    const getFreshToken = async () => {
        if (!currentUser) return null;

        // Only refresh if token is expiring soon or doesn't exist
        if (isTokenExpiringSoon()) {
            try {
                console.log('Refreshing token...');
                const freshToken = await currentUser.getIdToken(true);
                saveToken(freshToken);
                return freshToken;
            } catch (error) {
                console.error('Error refreshing token:', error);
                if (error.code === 'auth/user-token-expired') {
                    // Force user to re-login
                    await logout();
                    window.location.href = '/login';
                }
                return token; // Return existing token as fallback
            }
        }

        return token;
    };

    function signup(email, password) {
        return createUserWithEmailAndPassword(auth, email, password);
    }

    function login(email, password) {
        return signInWithEmailAndPassword(auth, email, password);
    }

    function loginWithGoogle() {
        return signInWithPopup(auth, googleProvider);
    }

    function logout() {
        saveToken(null);
        return signOut(auth);
    }

    useEffect(() => {
        const unsubscribe = onAuthStateChanged(auth, async (user) => {
            setCurrentUser(user);
            if (user) {

                try {
                    const newToken = await user.getIdToken();
                    saveToken(newToken);
                } catch (error) {
                    console.error('Error getting token:', error);
                    saveToken(null);
                }
            } else {
                saveToken(null);
            }
            setLoading(false);
        });

        return unsubscribe;
    }, []);

    // Create a token refresh mechanism
    useEffect(() => {
        if (!currentUser) return;

        // Function to check and refresh token
        const checkAndRefreshToken = async () => {
            if (isTokenExpiringSoon()) {
                await getFreshToken();
            }
        };

        // Run initially and set up interval
        checkAndRefreshToken();

        // Refresh token every 10 minutes
        const refreshInterval = setInterval(checkAndRefreshToken, 10 * 60 * 1000);

        return () => clearInterval(refreshInterval);
    }, [currentUser]);

    const value = {
        currentUser,
        token,
        signup,
        login,
        loginWithGoogle,
        logout,
        getFreshToken
    };

    return (
        <AuthContext.Provider value={value}>
            {!loading && children}
        </AuthContext.Provider>
    );
}