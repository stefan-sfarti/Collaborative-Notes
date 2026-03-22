import type { IMessage, StompSubscription } from "@stomp/stompjs";
import { Client } from "@stomp/stompjs";
import type { MutableRefObject, ReactNode } from "react";
import {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useRef,
    useState,
} from "react";
import { useAuth } from "../contexts/AuthContext";
import type { ConnectionStatus, WebSocketContextValue } from "../types";

const WebSocketContext = createContext<WebSocketContextValue | null>(null);

const buildWebSocketUrl = (): string => {
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
const createMessageHandler = (eventName: string) => (message: IMessage) => {
  try {
    const data = JSON.parse(message.body) as unknown;
    window.dispatchEvent(new CustomEvent(eventName, { detail: data }));
  } catch (error) {
    console.error(`Error processing ${eventName}:`, error);
  }
};

interface SubscriptionChannel {
  destination: (noteId: string) => string;
  eventName: string;
}

// Channels to subscribe when joining a note.
const SUBSCRIPTION_CHANNELS: SubscriptionChannel[] = [
  { destination: (noteId) => `/topic/notes/${noteId}/presence`, eventName: "user-presence" },
  { destination: (noteId) => `/topic/notes/${noteId}/typing`, eventName: "typing-indicator" },
  { destination: (noteId) => `/user/queue/notes/${noteId}/state`, eventName: "note-state" },
  // OT channels (prosemirror-collab)
  { destination: (noteId) => `/topic/notes/${noteId}/ot`, eventName: "ot-steps" },
  { destination: (noteId) => `/user/queue/notes/${noteId}/ot-catchup`, eventName: "ot-catchup" },
];

export function WebSocketProvider({ children }: { children: ReactNode }) {
  // "connected" | "reconnecting" | "disconnected"
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("disconnected");
  const { currentUser, token, getFreshToken } = useAuth();

  const stompClientRef = useRef<Client | null>(null);
  const subscriptionsMapRef: MutableRefObject<Map<string, StompSubscription[]>> = useRef(new Map());
  const desiredSubscriptionsRef: MutableRefObject<Set<string>> = useRef(new Set());
  const getFreshTokenRef: MutableRefObject<() => Promise<string | null>> = useRef(getFreshToken);

  useEffect(() => {
    getFreshTokenRef.current = getFreshToken;
  }, [getFreshToken]);

  const subscribeToNote = useCallback(
    async (noteId: string): Promise<string | null> => {
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
        const token = await getFreshTokenRef.current();
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

        // Delay the state + presence requests briefly so React effects in
        // consuming components (useCollaborationEvents, useOTCollab) have time
        // to mount and register their DOM event listeners before the server
        // responses arrive and get dispatched as CustomEvents on `window`.
        setTimeout(() => {
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
        }, 150);

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

  const unsubscribeFromNote = useCallback(async (noteId: string): Promise<void> => {
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
      const token = await getFreshTokenRef.current();
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
        const freshToken = await getFreshTokenRef.current();
        if (!freshToken || disposed) return;

        const existingClient = stompClientRef.current;
        if (existingClient) {
          existingClient.deactivate();
          stompClientRef.current = null;
        }

        const stompClient = new Client({
          brokerURL: buildWebSocketUrl(),
          connectHeaders: {
            Authorization: `Bearer ${freshToken}`,
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

  const sendTypingIndicator = useCallback(
    async (noteId: string, isTyping: boolean): Promise<void> => {
      const client = stompClientRef.current;
      if (!client || !client.connected || !currentUser?.id || !noteId) return;

      try {
        const freshToken = await getFreshTokenRef.current();
        if (!freshToken) return;

        client.publish({
          destination: `/app/notes/${noteId}/typing`,
          headers: {
            Authorization: `Bearer ${freshToken}`,
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

  const sendOTSteps = useCallback(
    async (noteId: string, version: number, steps: object[]): Promise<boolean> => {
      const client = stompClientRef.current;
      if (!client || !client.connected || !noteId || steps.length === 0) return false;

      try {
        const freshToken = await getFreshTokenRef.current();
        if (!freshToken) return false;

        client.publish({
          destination: `/app/notes/${noteId}/ot-submit`,
          headers: {
            Authorization: `Bearer ${freshToken}`,
            "content-type": "application/json",
          },
          body: JSON.stringify({ version, steps }),
        });
        return true;
      } catch (error) {
        console.error(`Failed to send OT steps for ${noteId}:`, error);
        return false;
      }
    },
    [],
  );

  const getStompClient = useCallback(() => stompClientRef.current, []);

  const value: WebSocketContextValue = {
    connectionStatus,
    subscribeToNote,
    unsubscribeFromNote,
    sendTypingIndicator,
    sendOTSteps,
    getStompClient,
  };

  return (
    <WebSocketContext.Provider value={value}>
      {children}
    </WebSocketContext.Provider>
  );
}

export const useWebSocket = (): WebSocketContextValue => {
  const context = useContext(WebSocketContext);
  if (context === null) {
    throw new Error("useWebSocket must be used within a WebSocketProvider");
  }
  return context;
};
