import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import { useAuth } from '../contexts/AuthContext.jsx';

// Create the context
const WebSocketContext = createContext(null);

// Provider component
export function WebSocketProvider({ children }) {
    const [connected, setConnected] = useState(false);
    const { currentUser, getFreshToken } = useAuth();
    const stompClientRef = useRef(null);
    const subscriptionsMapRef = useRef(new Map());

    // Effect hook to manage WebSocket connection lifecycle
    useEffect(() => {
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
                    stompClientRef.current = null; 
                }

                // Create a new STOMP client instance
                const stompClient = new Client({
                    brokerURL: 'ws://localhost:5000/ws-notes',
                    connectHeaders: {
                        Authorization: `Bearer ${token}`
                    },
                    debug: function(str) {
                        console.log('STOMP Debug: ' + str);
                    },
                    reconnectDelay: 5000,
                    heartbeatIncoming: 4000,
                    heartbeatOutgoing: 4000
                });

                // STOMP Event Handlers

                stompClient.onConnect = (frame) => {
                    console.log('STOMP connected successfully:', frame);
                    setConnected(true);

                    if (subscriptionsMapRef.current.size > 0) {
                        console.log(`Re-subscribing to ${subscriptionsMapRef.current.size} notes...`);
                        const noteIdsToResubscribe = Array.from(subscriptionsMapRef.current.keys());
                        subscriptionsMapRef.current.clear();
                        noteIdsToResubscribe.forEach(noteId => {
                            subscribeToNote(noteId);
                        });
                    }
                };

                stompClient.onStompError = (frame) => {
                    console.error('STOMP error:', frame);
                    setConnected(false);
                };

                stompClient.onWebSocketClose = () => {
                    console.log('WebSocket connection closed');
                    setConnected(false);
                };

                stompClient.onWebSocketError = (event) => {
                    console.error('WebSocket error:', event);
                    setConnected(false);
                };

                stompClient.activate();
                stompClientRef.current = stompClient;

            } catch (error) {
                console.error('Failed to connect WebSocket:', error);
                setConnected(false); 
            }
        };

        connectWebSocket();

        return () => {
            if (stompClientRef.current && stompClientRef.current.connected) {
                console.log('Component unmounting or dependencies changed, deactivating STOMP client');
                stompClientRef.current.deactivate();
            }
            stompClientRef.current = null;
            subscriptionsMapRef.current.clear();
            setConnected(false);
        };
    }, [currentUser, getFreshToken]);

    // Function to subscribe to updates for a specific note
    const subscribeToNote = async (noteId) => {
        if (!stompClientRef.current || !connected || !currentUser) {
            console.log(`Cannot subscribe to note ${noteId}: client not ready or not connected.`);
            return null;
        }

        if (subscriptionsMapRef.current.has(noteId)) {
            console.log(`Already subscribed to note: ${noteId}. Skipping duplicate subscription.`);
            return noteId; 
        }


        try {
            console.log(`Attempting to subscribe to note: ${noteId}`);
            const token = await getFreshToken();
            const subscriptions = []; 

            // Subscribe to different topics for the note

            const noteUpdateSub = stompClientRef.current.subscribe(
                `/topic/notes/${noteId}`,
                (message) => {
                    try {
                        const data = JSON.parse(message.body);
                        console.log(`Received note update for ${noteId}:`, data);
                        window.dispatchEvent(new CustomEvent('note-update', { detail: data }));
                    } catch (error) {
                        console.error(`Error processing note update message for ${noteId}:`, error);
                    }
                },
                { 'Authorization': `Bearer ${token}` }
            );
            subscriptions.push(noteUpdateSub);

            // Subscribe to user presence updates
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

            const noteStateSub = stompClientRef.current.subscribe(
                `/user/queue/notes/${noteId}/state`, 
                (message) => {
                    try {
                        console.log(`Received note state for ${noteId} on user queue:`, message.body);
                        const data = JSON.parse(message.body);
                        console.log(`Parsed note state data for ${noteId}:`, data);
                        window.dispatchEvent(new CustomEvent('note-state', { detail: data }));
                    } catch (error) {
                        console.error(`Error processing note state message for ${noteId}:`, error);
                    }
                },
                { 'Authorization': `Bearer ${token}` }
            );
            subscriptions.push(noteStateSub);

            const noteStateTopicSub = stompClientRef.current.subscribe(
                `/topic/notes/${noteId}/state`,
                (message) => {
                    try {
                        console.log(`Received note state for ${noteId} on topic:`, message.body);
                        const data = JSON.parse(message.body);
                        console.log(`Parsed note state data for ${noteId}:`, data);
                        window.dispatchEvent(new CustomEvent('note-state', { detail: data }));
                    } catch (error) {
                        console.error(`Error processing note state message for ${noteId}:`, error);
                    }
                },
                { 'Authorization': `Bearer ${token}` }
            );
            subscriptions.push(noteStateTopicSub);


            subscriptionsMapRef.current.set(noteId, subscriptions);

            // Send initial messages after subscribing

            console.log(`Requesting initial state for note: ${noteId} to /app/notes/${noteId}/state`);
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/state`,
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' },
                body: JSON.stringify({ requestType: 'initial-state' }) 
            });
            console.log(`Initial state request sent for ${noteId}`);

            // Announce presence in the note
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/presence`,
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' },
                body: JSON.stringify({
                    joining: true,
                    userId: currentUser.uid
                })
            });
            console.log(`Presence announcement sent for ${noteId}`);


            console.log(`Successfully subscribed to note: ${noteId}`);
            return noteId;
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
            return null;
        }
    };

    // Function to unsubscribe from updates for a specific note
    const unsubscribeFromNote = async (noteId) => {
        if (!stompClientRef.current || !connected || !subscriptionsMapRef.current.has(noteId)) {
            console.log(`Cannot unsubscribe from note ${noteId}: client not ready, not connected, or not subscribed.`);
            return;
        }

        try {
            console.log(`Attempting to unsubscribe from note: ${noteId}`);
            const token = await getFreshToken();

            // Announce leaving the note before unsubscribing from presence
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/presence`,
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' },
                body: JSON.stringify({
                    joining: false,
                    userId: currentUser.uid
                })
            });
            console.log(`Presence leaving announcement sent for ${noteId}`);


            // Get and clean up all subscriptions for this noteId
            const subscriptions = subscriptionsMapRef.current.get(noteId);
            if (subscriptions) {
                subscriptions.forEach(subscription => {
                    if (subscription && typeof subscription.unsubscribe === 'function') {
                        subscription.unsubscribe();
                        console.log(`Unsubscribed from destination: ${subscription.destination}`);
                    } else {
                        console.warn('Attempted to unsubscribe from an invalid subscription object:', subscription);
                    }
                });
                subscriptionsMapRef.current.delete(noteId);
            }

            console.log(`Successfully unsubscribed from note: ${noteId}`);
        } catch (error) {
            console.error(`Failed to unsubscribe from note ${noteId}:`, error);
        }
    };

    // Function to send a note content update message
    const sendNoteUpdate = async (noteId, title, content) => {
        if (!stompClientRef.current || !connected || !currentUser) {
            console.log(`Cannot send note update for ${noteId}: client not ready or not connected.`);
            return;
        }

        try {
            const token = await getFreshToken();
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/update`,
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' },
                body: JSON.stringify({
                    title: title,
                    content: content,
                    userId: currentUser.uid 
                })
            });
            console.log(`Note update message sent for ${noteId}`);
        } catch (error) {
            console.error(`Failed to send note update for ${noteId}:`, error);
        }
    };

    const sendNoteStateUpdate = async (noteId, title, content) => {
        if (!stompClientRef.current || !connected || !currentUser) {
            console.log(`Cannot send note state update for ${noteId}: client not ready or not connected.`);
            return;
        }

        try {
            const token = await getFreshToken();
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/state`,
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' },
                body: JSON.stringify({
                    title: title,
                    content: content,
                    userId: currentUser.uid 
                })
            });
            console.log(`Note state update message sent for ${noteId}`);
        } catch (error) {
            console.error(`Failed to send note state update for ${noteId}:`, error);
        }
    };


    // Function to send a typing indicator message
    const sendTypingIndicator = async (noteId, isTyping) => {
        if (!stompClientRef.current || !connected || !currentUser) {
            console.log(`Cannot send typing indicator for ${noteId}: client not ready or not connected.`);
            return;
        }

        try {
            const token = await getFreshToken();
            stompClientRef.current.publish({
                destination: `/app/notes/${noteId}/typing`, 
                headers: { 'Authorization': `Bearer ${token}`, 'content-type': 'application/json' },
                body: JSON.stringify({
                    isTyping: isTyping,
                    userId: currentUser.uid 
                })
            });
            console.log(`Typing indicator sent for ${noteId}: ${isTyping}`);
        } catch (error) {
            console.error(`Failed to send typing indicator for ${noteId}:`, error);
        }
    };

    const value = {
        connected, 
        subscribeToNote,
        unsubscribeFromNote,
        sendNoteUpdate, 
        sendTypingIndicator, 
        sendNoteStateUpdate 
    };

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
        throw new Error('useWebSocket must be used within a WebSocketProvider');
    }
    return context;
};
