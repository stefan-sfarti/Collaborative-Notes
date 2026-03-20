import { Client } from "@stomp/stompjs";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { useAuth } from "../contexts/AuthContext.jsx";

const WebSocketContext = createContext(null);

const buildWebSocketUrl = () => {
  try {
    const apiUrl = import.meta.env.VITE_API_URL || "http://localhost:5000/api";
    const url = new URL(apiUrl);
    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    url.pathname = "/ws-notes";
    url.search = "";
    url.hash = "";
    return url.toString().replace(/\/$/, "");
  } catch (error) {
    console.warn(
      "Failed to parse VITE_API_URL for websocket, using fallback",
      error,
    );
    return "ws://localhost:5000/ws-notes";
  }
};

// Factory: returns a STOMP message handler that dispatches a custom DOM event.
const createMessageHandler = (eventName) => (message) => {
  try {
    const data = JSON.parse(message.body);
    window.dispatchEvent(new CustomEvent(eventName, { detail: data }));
  } catch (error) {
    console.error(`Error processing ${eventName}:`, error);
  }
};

// Channels to subscribe when joining a note.
const SUBSCRIPTION_CHANNELS = [
  { destination: (noteId) => `/topic/notes/${noteId}`, eventName: "note-update" },
  { destination: (noteId) => `/topic/notes/${noteId}/presence`, eventName: "user-presence" },
  { destination: (noteId) => `/topic/notes/${noteId}/typing`, eventName: "typing-indicator" },
  { destination: (noteId) => `/user/queue/notes/${noteId}/state`, eventName: "note-state" },
];

export function WebSocketProvider({ children }) {
  // "connected" | "reconnecting" | "disconnected"
  const [connectionStatus, setConnectionStatus] = useState("disconnected");
  const { currentUser, token, getFreshToken } = useAuth();

  const stompClientRef = useRef(null);
  const subscriptionsMapRef = useRef(new Map());
  const desiredSubscriptionsRef = useRef(new Set());
  const getFreshTokenRef = useRef(getFreshToken);

  useEffect(() => {
    getFreshTokenRef.current = getFreshToken;
  }, [getFreshToken]);

  const subscribeToNote = useCallback(
    async (noteId) => {
      if (!noteId) return null;

      desiredSubscriptionsRef.current.add(noteId);

      const client = stompClientRef.current;
      if (!client || !client.connected || !currentUser?.id) {
        return noteId;
      }

      if (subscriptionsMapRef.current.has(noteId)) {
        return noteId;
      }

      try {
        const token = await getFreshTokenRef.current?.();
        if (!token) {
          console.warn("Unable to subscribe without auth token");
          return null;
        }

        const authHeaders = { Authorization: `Bearer ${token}` };

        const subscriptions = SUBSCRIPTION_CHANNELS.map((channel) =>
          client.subscribe(
            channel.destination(noteId),
            createMessageHandler(channel.eventName),
            authHeaders,
          ),
        );

        subscriptionsMapRef.current.set(noteId, subscriptions);

        client.publish({
          destination: `/app/notes/${noteId}/state`,
          headers: { ...authHeaders, "content-type": "application/json" },
          body: JSON.stringify({ requestType: "initial-state" }),
        });

        client.publish({
          destination: `/app/notes/${noteId}/presence`,
          headers: { ...authHeaders, "content-type": "application/json" },
          body: JSON.stringify({ joining: true }),
        });

        return noteId;
      } catch (error) {
        console.error(`Failed to subscribe to note ${noteId}:`, error);

        const partialSubscriptions = subscriptionsMapRef.current.get(noteId);
        if (partialSubscriptions) {
          partialSubscriptions.forEach((sub) => {
            if (sub && typeof sub.unsubscribe === "function") {
              sub.unsubscribe();
            }
          });
          subscriptionsMapRef.current.delete(noteId);
        }

        return null;
      }
    },
    [currentUser?.id],
  );

  const unsubscribeFromNote = useCallback(async (noteId) => {
    if (!noteId) return;

    desiredSubscriptionsRef.current.delete(noteId);

    const subscriptions = subscriptionsMapRef.current.get(noteId);
    if (subscriptions) {
      subscriptions.forEach((sub) => {
        if (sub && typeof sub.unsubscribe === "function") {
          sub.unsubscribe();
        }
      });
      subscriptionsMapRef.current.delete(noteId);
    }

    const client = stompClientRef.current;
    if (!client || !client.connected) {
      return;
    }

    try {
      const token = await getFreshTokenRef.current?.();
      if (!token) return;

      client.publish({
        destination: `/app/notes/${noteId}/presence`,
        headers: {
          Authorization: `Bearer ${token}`,
          "content-type": "application/json",
        },
        body: JSON.stringify({ joining: false }),
      });
    } catch (error) {
      console.error(`Failed to publish leaving presence for ${noteId}:`, error);
    }
  }, []);

  useEffect(() => {
    if (!currentUser?.id || !token) {
      const client = stompClientRef.current;
      if (client) {
        client.deactivate();
      }
      stompClientRef.current = null;
      subscriptionsMapRef.current.clear();
      desiredSubscriptionsRef.current.clear();
      setConnectionStatus("disconnected");
      return;
    }

    let disposed = false;

    const connectWebSocket = async () => {
      try {
        const token = await getFreshTokenRef.current?.();
        if (!token || disposed) return;

        const existingClient = stompClientRef.current;
        if (existingClient) {
          existingClient.deactivate();
          stompClientRef.current = null;
        }

        const stompClient = new Client({
          brokerURL: buildWebSocketUrl(),
          connectHeaders: {
            Authorization: `Bearer ${token}`,
          },
          reconnectDelay: 4000,
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000,
          debug: () => {},
        });

        stompClient.onConnect = () => {
          if (disposed) return;
          setConnectionStatus("connected");

          desiredSubscriptionsRef.current.forEach((noteId) => {
            subscribeToNote(noteId);
          });
        };

        stompClient.onStompError = (frame) => {
          console.error("STOMP error:", frame);
          setConnectionStatus("reconnecting");
          window.dispatchEvent(
            new CustomEvent("websocket-error", {
              detail: {
                message: "WebSocket error while syncing the note.",
                frame,
              },
            }),
          );
        };

        stompClient.onWebSocketClose = () => {
          setConnectionStatus("reconnecting");
        };

        stompClient.onWebSocketError = (event) => {
          console.error("WebSocket error:", event);
          setConnectionStatus("reconnecting");
        };

        stompClient.activate();
        stompClientRef.current = stompClient;
      } catch (error) {
        console.error("Failed to connect websocket:", error);
        setConnectionStatus("disconnected");
      }
    };

    connectWebSocket();

    return () => {
      disposed = true;
      const client = stompClientRef.current;
      if (client) {
        client.deactivate();
      }
      stompClientRef.current = null;
      subscriptionsMapRef.current.clear();
      setConnectionStatus("disconnected");
    };
  }, [currentUser?.id, token, subscribeToNote]);

  const sendNoteUpdate = useCallback(
    async (noteId, title, content) => {
      const client = stompClientRef.current;
      if (!client || !client.connected || !currentUser?.id || !noteId) return;

      try {
        const token = await getFreshTokenRef.current?.();
        if (!token) return;

        client.publish({
          destination: `/app/notes/${noteId}/update`,
          headers: {
            Authorization: `Bearer ${token}`,
            "content-type": "application/json",
          },
          body: JSON.stringify({
            title,
            content,
            userId: currentUser.id,
          }),
        });
      } catch (error) {
        console.error(`Failed to send note update for ${noteId}:`, error);
      }
    },
    [currentUser?.id],
  );

  const sendTypingIndicator = useCallback(
    async (noteId, isTyping) => {
      const client = stompClientRef.current;
      if (!client || !client.connected || !currentUser?.id || !noteId) return;

      try {
        const token = await getFreshTokenRef.current?.();
        if (!token) return;

        client.publish({
          destination: `/app/notes/${noteId}/typing`,
          headers: {
            Authorization: `Bearer ${token}`,
            "content-type": "application/json",
          },
          body: JSON.stringify({
            isTyping,
            userId: currentUser.id,
          }),
        });
      } catch (error) {
        console.error(`Failed to send typing indicator for ${noteId}:`, error);
      }
    },
    [currentUser?.id],
  );

  const value = {
    connectionStatus,
    // Keep legacy `connected` boolean so consumers don't break during transition
    connected: connectionStatus === "connected",
    subscribeToNote,
    unsubscribeFromNote,
    sendNoteUpdate,
    sendTypingIndicator,
  };

  return (
    <WebSocketContext.Provider value={value}>
      {children}
    </WebSocketContext.Provider>
  );
}

export const useWebSocket = () => {
  const context = useContext(WebSocketContext);
  if (context === null) {
    throw new Error("useWebSocket must be used within a WebSocketProvider");
  }
  return context;
};
