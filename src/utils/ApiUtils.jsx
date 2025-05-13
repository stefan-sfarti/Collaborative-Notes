// src/utils/apiUtils.js
import { useAuth } from '../contexts/AuthContext';

/**
 * Custom hook for making authenticated API requests
 * @returns {Object} API utility functions
 */
export function useApi() {
    const { token, getFreshToken } = useAuth();

    /**
     * Make an authenticated fetch request
     * @param {string} url - The URL to fetch
     * @param {Object} options - Fetch options
     * @returns {Promise} - Fetch response
     */
    const authFetch = async (url, options = {}) => {
        // Get a fresh token if needed
        const currentToken = token || await getFreshToken();

        if (!currentToken) {
            throw new Error('No authentication token available');
        }

        // Set up headers with authentication
        const headers = {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${currentToken}`,
            ...(options.headers || {})
        };

        try {
            const response = await fetch(url, {
                ...options,
                headers
            });

            // Handle unauthorized errors (token expired)
            if (response.status === 401) {
                // Try with a fresh token
                const newToken = await getFreshToken();

                if (!newToken) {
                    throw new Error('Authentication failed');
                }

                // Retry the request with new token
                headers.Authorization = `Bearer ${newToken}`;
                return fetch(url, {
                    ...options,
                    headers
                });
            }

            return response;
        } catch (error) {
            console.error('API request failed:', error);
            throw error;
        }
    };

    /**
     * GET request with authentication
     * @param {string} url - The URL to fetch
     * @param {Object} options - Additional fetch options
     * @returns {Promise} - JSON response
     */
    const get = async (url, options = {}) => {
        const response = await authFetch(url, {
            method: 'GET',
            ...options
        });

        return response.json();
    };

    /**
     * POST request with authentication
     * @param {string} url - The URL to fetch
     * @param {Object} data - Data to send
     * @param {Object} options - Additional fetch options
     * @returns {Promise} - JSON response
     */
    const post = async (url, data, options = {}) => {
        const response = await authFetch(url, {
            method: 'POST',
            body: JSON.stringify(data),
            ...options
        });

        return response.json();
    };

    /**
     * PUT request with authentication
     * @param {string} url - The URL to fetch
     * @param {Object} data - Data to send
     * @param {Object} options - Additional fetch options
     * @returns {Promise} - JSON response
     */
    const put = async (url, data, options = {}) => {
        const response = await authFetch(url, {
            method: 'PUT',
            body: JSON.stringify(data),
            ...options
        });

        return response.json();
    };

    /**
     * DELETE request with authentication
     * @param {string} url - The URL to fetch
     * @param {Object} options - Additional fetch options
     * @returns {Promise} - JSON response
     */
    const del = async (url, options = {}) => {
        const response = await authFetch(url, {
            method: 'DELETE',
            ...options
        });

        return response.json();
    };

    return {
        get,
        post,
        put,
        delete: del,
        authFetch
    };
}