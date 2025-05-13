// src/components/WebSocketProvider.jsx
import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import { useAuth } from '../contexts/AuthContext.jsx'; // Assuming AuthContext is correctly implemented

// Create the context
const WebSocketContext = createContext(null);

// Provider component
export function WebSocketProvider({ children }) {
    // State to track connection status
    const [connected, setConnected] = useState(false);
    // Get authentication context hook
    const { currentUser, getFreshToken } = useAuth();
    // Ref to hold the STOMP client instance
    const stompClientRef = useRef(null);
    // Ref to store subscriptions, mapping noteId to an array of subscription objects
    const subscriptionsMapRef = useRef(new Map());

    // Effect hook to manage WebSocket connection lifecycle
    useEffect(() => {
        // Only connect if a user is logged in
        if (!currentUser) {
            console.log('No user logged in, skipping WebSocket connection.');
            // Ensure client is deactivated if user logs out while connected
            if (stompClientRef.current && stompClientRef.current.connected) {
                console.log('User logged out, deactivating STOMP client.');
                stompClientRef.current.deactivate();
                stompClientRef.current = null;
                setConnected(false);
            }
            return;
        }

        const connectWebSocket = async () => {
            try {
                console.log('Attempting to connect WebSocket...');
                // Get a fresh authentication token
                const token = await getFreshToken();

                // Clean up any existing connection before creating a new one
                if (stompClientRef.current) {
                    console.log('Existing STOMP client found, deactivating...');
                    stompClientRef.current.deactivate();
                    stompClientRef.current = null; // Clear the ref after deactivation
                }

                // Create a new STOMP client instance
                const stompClient = new Client({
                    // WebSocket broker URL - make sure this is correct
                    brokerURL: 'ws://localhost:5000/ws-notes',
                    // Headers for the CONNECT frame, including authorization
                    connectHeaders: {
                        Authorization: `Bearer ${token}`
                    },
                    // Debug function to log STOMP activity
                    debug: function(str) {
                        console.log('STOMP Debug: ' + str);
                    },
                    // Reconnect delay in milliseconds
                    reconnectDelay: 5000,
                    // Heartbeat settings (client sends/expects heartbeats)
                    heartbeatIncoming: 4000,
                    heartbeatOutgoing: 4000
                });

                // --- STOMP Event Handlers ---

                // Called upon successful STOMP connection
                stompClient.onConnect = (frame) => {
                    console.log('STOMP connected successfully:', frame);
                    setConnected(true);

                    // Re-subscribe to previously subscribed notes after a successful reconnect
                    // This handles cases where the WebSocket disconnects and then reconnects
                    if (subscriptionsMapRef.current.size > 0) {
                        console.log(`Re-subscribing to ${subscriptionsMapRef.current.size} notes...`);
                        const noteIdsToResubscribe = Array.from(subscriptionsMapRef.current.keys());
                        // Clear the map before re-subscribing to avoid duplicates
                        subscriptionsMapRef.current.clear();
                        noteIdsToResubscribe.forEach(noteId => {
                            // Use the public subscribe function to ensure proper handling
                            subscribeToNote(noteId);
                        });
                    }
                };

                // Called when a STOMP error occurs
                stompClient.onStompError = (frame) => {
                    console.error('STOMP error:', frame);
                    setConnected(false); // Connection is likely broken on STOMP error
                };

                // Called when the underlying WebSocket closes
                stompClient.onWebSocketClose = () => {
                    console.log('WebSocket connection closed');
                    setConnected(false); // Update connection status
                };

                // Called when an error occurs on the underlying WebSocket
                stompClient.onWebSocketError = (event) => {
                    console.error('WebSocket error:', event);
                    setConnected(false); // Update connection status
                };

                // Activate the STOMP client to initiate the connection process
                stompClient.activate();
                // Store the active client instance in the ref
                stompClientRef.current = stompClient;

            } catch (error) {
                console.error('Failed to connect WebSocket:', error);
                setConnected(false); // Ensure connected state is false on error
            }
        };

        // Initiate the connection when the component mounts or currentUser/token changes
        connectWebSocket();

        // Clean up function: runs when the component unmounts or dependencies change
        return () => {
            // Deactivate the STOMP client if it exists and is connected
            if (stompClientRef.current && stompClientRef.current.connected) {
                console.log('Component unmounting or dependencies changed, deactivating STOMP client');
                stompClientRef.current.deactivate();
            }
            // Clear the ref on cleanup
            stompClientRef.current = null;
            // Clear all subscriptions on cleanup
            subscriptionsMapRef.current.clear();
            setConnected(false);
        };
    }, [currentUser, getFreshToken]); // Dependencies: Re-run effect if currentUser or getFreshToken changes

    // Function to subscribe to updates for a specific note
    const subscribeToNote = async (noteId) => {
        // Prevent subscribing if client is not ready or not connected
        if (!stompClientRef.current || !connected || !currentUser) {
            console.log(`Cannot subscribe to note ${noteId}: client not ready or not connected.`);
            return null;
        }

        // Prevent duplicate subscriptions for the same noteId
        if (subscriptionsMapRef.current.has(noteId)) {
            console.log(`Already subscribed to note: ${noteId}. Skipping duplicate subscription.`);
            return noteId; // Return the noteId indicating it's already subscribed
        }


        try {
            console.log(`Attempting to subscribe to note: ${noteId}`);
            // Get a fresh token for the subscription headers
            const token = await getFreshToken();
            const subscriptions = []; // Array to hold subscription objects for this note

            // --- Subscribe to different topics for the note ---

            // Subscribe to note content updates (server pushes changes here)
            const noteUpdateSub = stompClientRef.current.subscribe(
                `/topic/notes/${noteId}`,
                (message) => {
                    try {
                        const data = JSON.parse(message.body);
                        console.log(`Received note update for ${noteId}:`, data);
                        // Dispatch a custom event for other components to listen to
                        window.dispatchEvent(new CustomEvent('note-update', { detail: data }));
                    } catch (error) {
                        console.error(`Error processing note update message for ${noteId}:`, error);
                    }
                },
                // Optional: Add headers for the SUBSCRIBE frame if needed (e.g., receipt)
                { 'Authorization': `Bearer ${token}` }
            );
            subscriptions.push(noteUpdateSub);

            // Subscribe to user presence updates (who is viewing/editing the note)
            const presenceSub = stompClientRef.current.subscribe(
                `/topic/notes/${noteId}/presence`,
                (message) => {
                    try {
                        const data = JSON.parse(message.body);
                        console.log(`Received presence update for ${noteId}:`, data);
                        window.dispatchEvent(new CustomEvent('user-presence', { detail: data }));
                    } catch (error) {
                        console.error(`Error processing presence message for ${noteId}:`, error);
                    }
                },
                { 'Authorization': `Bearer ${token}` }
            );
            subscriptions.push(presenceSub);

            // Subscribe to typing indicators from other users
            const typingSub = stompClientRef.current.subscribe(
                `/topic/notes/${noteId}/typing`,
                (message) => {
                    try {
                        const data = JSON.parse(message.body);
                        console.log(`Received typing indicator for ${noteId}:`, data);
                        window.dispatchEvent(new CustomEvent('typing-indicator', { detail: data }));
                    } catch (error) {
                        console.error(`Error processing typing indicator message for ${noteId}:`, error);
                    }
                },
                { 'Authorization': `Bearer ${token}` }
            );
            subscriptions.push(typingSub);

            // Subscribe to note state updates (This is likely where the initial state and subsequent full state updates are sent)
            // FIX: Subscribe to the user-specific queue for state updates as sent by the server.
            const noteStateSub = stompClientRef.current.subscribe(
                `/user/queue/notes/${noteId}/state`, // Subscribing to the user queue
                (message) => {
                    try {
                        console.log(`Received note state for ${noteId} on user queue:`, message.body);
                        const data = JSON.parse(message.body);
                        console.log(`Parsed note state data for ${noteId}:`, data);
                        // Dispatch the state data
                        window.dispatchEvent(new CustomEvent('note-state', { detail: data }));
                    } catch (error) {
                        console.error(`Error processing note state message for ${noteId}:`, error);
                    }
                },
                { 'Authorization': `Bearer ${token}` }
            );
            subscriptions.push(noteStateSub);

            // Optional: Keep the topic subscription for state as well, if server might also broadcast here
            const noteStateTopicSub = stompClientRef.current.subscribe(
                `/topic/notes/${noteId}/state`, // Also subscribing to the topic (optional)
                (message) => {
                    try {
                        console.log(`Received note state for ${noteId} on topic:`, message.body);
                        const data = JSON.parse(message.body);
                        console.log(`Parsed note state data for ${noteId}:`, data);
                        // Dispatch the state data
                        window.dispatchEvent(new CustomEvent('note-state', { detail: data }));
                    } catch (error) {
                        console.error(`Error processing note state message for ${noteId}:`, error);
                    }
                },
                { 'Authorization': `Bearer ${token}` }
            );
            subscriptions.push(noteStateTopicSub);


            // Store the array of subscription objects in the map, keyed by noteId
            subscriptionsMapRef.current.set(noteId, subscriptions);

            // --- Send initial messages after subscribing ---

            // Request the initial state for the note
            // This message is sent TO the server using an /app destination
            console.log(`Requesting initial state for note: ${noteId} to /app/notes/${noteId}/state`);
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/state`,
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' }, // Specify content type
                body: JSON.stringify({ requestType: 'initial-state' }) // Include a body to potentially help server routing
            });
            console.log(`Initial state request sent for ${noteId}`);

            // Announce presence in the note
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/presence`,
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' }, // Specify content type
                body: JSON.stringify({
                    joining: true,
                    userId: currentUser.uid // Include user ID for clarity on server side
                })
            });
            console.log(`Presence announcement sent for ${noteId}`);


            console.log(`Successfully subscribed to note: ${noteId}`);
            return noteId; // Return the noteId on successful subscription
        } catch (error) {
            console.error(`Failed to subscribe to note ${noteId}:`, error);
            // Clean up any partial subscriptions if an error occurred during the process
            const partialSubscriptions = subscriptionsMapRef.current.get(noteId);
            if(partialSubscriptions) {
                partialSubscriptions.forEach(sub => {
                    if (sub && typeof sub.unsubscribe === 'function') {
                        sub.unsubscribe();
                    }
                });
                subscriptionsMapRef.current.delete(noteId);
            }
            return null; // Return null on failure
        }
    };

    // Function to unsubscribe from updates for a specific note
    const unsubscribeFromNote = async (noteId) => {
        // Prevent unsubscribing if client is not ready or not connected, or if not subscribed
        if (!stompClientRef.current || !connected || !subscriptionsMapRef.current.has(noteId)) {
            console.log(`Cannot unsubscribe from note ${noteId}: client not ready, not connected, or not subscribed.`);
            return;
        }

        try {
            console.log(`Attempting to unsubscribe from note: ${noteId}`);
            const token = await getFreshToken();

            // Announce leaving the note *before* unsubscribing from presence
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/presence`,
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' },
                body: JSON.stringify({
                    joining: false,
                    userId: currentUser.uid // Include user ID
                })
            });
            console.log(`Presence leaving announcement sent for ${noteId}`);


            // Get and clean up all subscriptions for this noteId
            const subscriptions = subscriptionsMapRef.current.get(noteId);
            if (subscriptions) {
                subscriptions.forEach(subscription => {
                    // Ensure the subscription object and its unsubscribe method exist
                    if (subscription && typeof subscription.unsubscribe === 'function') {
                        subscription.unsubscribe();
                        console.log(`Unsubscribed from destination: ${subscription.destination}`);
                    } else {
                        console.warn('Attempted to unsubscribe from an invalid subscription object:', subscription);
                    }
                });
                // Remove the noteId from the subscriptions map after unsubscribing
                subscriptionsMapRef.current.delete(noteId);
            }

            console.log(`Successfully unsubscribed from note: ${noteId}`);
        } catch (error) {
            console.error(`Failed to unsubscribe from note ${noteId}:`, error);
        }
    };

    // Function to send a note content update message
    const sendNoteUpdate = async (noteId, title, content) => {
        // Prevent sending if client is not ready or not connected
        if (!stompClientRef.current || !connected || !currentUser) {
            console.log(`Cannot send note update for ${noteId}: client not ready or not connected.`);
            return;
        }

        try {
            const token = await getFreshToken();
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/update`, // Destination to send update to the server
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' },
                body: JSON.stringify({
                    title: title,
                    content: content,
                    userId: currentUser.uid // Include user ID
                })
            });
            console.log(`Note update message sent for ${noteId}`);
        } catch (error) {
            console.error(`Failed to send note update for ${noteId}:`, error);
        }
    };

    // Function to request or send a full note state update (depending on server implementation)
    // Renamed from getNoteStateUpdate to clarify its purpose might be sending state FROM the client
    // If this is intended *only* to request state, the body might differ.
    // If this is intended to push client-side state to the server, the name is fine.
    // Let's assume for now it's for sending state FROM the client, similar to sendNoteUpdate.
    // If you need to *request* state, use the publish call in subscribeToNote.
    const sendNoteStateUpdate = async (noteId, title, content) => {
        if (!stompClientRef.current || !connected || !currentUser) {
            console.log(`Cannot send note state update for ${noteId}: client not ready or not connected.`);
            return;
        }

        try {
            const token = await getFreshToken();
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/state`, // Destination to send state to the server
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' },
                body: JSON.stringify({
                    title: title,
                    content: content,
                    userId: currentUser.uid // Include user ID
                })
            });
            console.log(`Note state update message sent for ${noteId}`);
        } catch (error) {
            console.error(`Failed to send note state update for ${noteId}:`, error);
        }
    };


    // Function to send a typing indicator message
    const sendTypingIndicator = async (noteId, isTyping) => {
        // Prevent sending if client is not ready or not connected
        if (!stompClientRef.current || !connected || !currentUser) {
            console.log(`Cannot send typing indicator for ${noteId}: client not ready or not connected.`);
            return;
        }

        try {
            const token = await getFreshToken();
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/typing`, // Destination to send typing status to the server
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' },
                body: JSON.stringify({
                    isTyping: isTyping,
                    userId: currentUser.uid // Include user ID
                })
            });
            console.log(`Typing indicator sent for ${noteId}: ${isTyping}`);
        } catch (error) {
            console.error(`Failed to send typing indicator for ${noteId}:`, error);
        }
    };

    // Context value provided to consumers
    const value = {
        connected, // Connection status
        subscribeToNote, // Function to subscribe
        unsubscribeFromNote, // Function to unsubscribe
        sendNoteUpdate, // Function to send content updates
        sendTypingIndicator, // Function to send typing indicators
        sendNoteStateUpdate // Function to send state updates (renamed)
    };

    // Provide the context value to the children components
    return (
        <WebSocketContext.Provider value={value}>
            {children}
        </WebSocketContext.Provider>
    );
}

// Custom hook to consume the WebSocket context
export const useWebSocket = () => {
    const context = useContext(WebSocketContext);
    if (context === null) {
        // Throw an error if hook is used outside of the provider
        throw new Error('useWebSocket must be used within a WebSocketProvider');
    }
    return context;
};
