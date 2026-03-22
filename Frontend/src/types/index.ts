// ─── Domain / API shapes ──────────────────────────────────────────────────────

export interface User {
  id: string;
  email: string;
  displayName?: string;
}

export interface Note {
  id: string;
  title: string;
  content: string;
  ownerId: string;
  collaboratorIds: string[];
  createdAt: string;
  updatedAt: string;
  version?: number;
}

export type NotePayload = Pick<Note, "title" | "content"> & { ownerId?: string };

export interface UserResponse {
  userId: string;
  email: string;
  displayName: string | null;
  photoUrl?: string | null;
}

/** Shape returned by /users/login and /users/register — fields vary by impl. */
export interface LoginApiResponse {
  token?: string;
  accessToken?: string;
  user?: User;
  email?: string;
  userId?: string;
  id?: string;
}

// ─── Auth context ─────────────────────────────────────────────────────────────

export interface AuthContextValue {
  currentUser: User | null;
  token: string | null;
  isAuthenticated: boolean;
  error: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<LoginApiResponse>;
  logout: () => void;
  getFreshToken: () => Promise<string | null>;
  updateCurrentUser: (user: User) => void;
}

// ─── WebSocket context ────────────────────────────────────────────────────────

export type ConnectionStatus = "connected" | "reconnecting" | "disconnected";

export interface WebSocketContextValue {
  connectionStatus: ConnectionStatus;
  subscribeToNote: (noteId: string) => Promise<string | null>;
  unsubscribeFromNote: (noteId: string) => Promise<void>;
  sendTypingIndicator: (noteId: string, isTyping: boolean) => Promise<void>;
  sendOTSteps: (noteId: string, version: number, steps: object[]) => Promise<boolean>;
  getStompClient: () => import("@stomp/stompjs").Client | null;
}

// ─── Presence / active users ──────────────────────────────────────────────────

export interface ActiveUserEntry {
  userId: string;
  isTyping: boolean;
}

export interface ActiveUserDisplay extends ActiveUserEntry {
  email?: string;
  displayName?: string | null;
  display: string;
  pending?: boolean;
}

export type FetchedUserEntry =
  | UserResponse
  | { pending: true }
  | { userId: string; email: string; displayName: string };

export type FetchedUserDetails = Record<string, FetchedUserEntry>;

// ─── Toolbar config ───────────────────────────────────────────────────────────

import type { LucideIcon } from "lucide-react";

export interface ToolbarButton {
  command: string;
  commandArgs: Record<string, unknown> | null;
  isActiveCheck: string;
  isActiveArgs: Record<string, unknown> | null;
  Icon: LucideIcon;
  title: string;
  canCheck: boolean;
}

export type ToolbarGroup = ToolbarButton[];

// ─── Error utils ──────────────────────────────────────────────────────────────

export interface ApiError {
  message: string;
  status: number | null;
  isConflict: boolean;
}

// ─── Custom DOM event payloads ────────────────────────────────────────────────

export interface NoteStateEventDetail {
  title?: string;
  content?: string;
  otVersion?: number;
  activeUsers?: Record<string, unknown> | ActiveUserPayload[];
  collaborators?: Record<string, unknown> | CollaboratorPayload[];
}

export interface ActiveUserPayload {
  userId?: string;
  id?: string;
}

export interface CollaboratorPayload {
  userId?: string;
  id?: string;
}

export interface UserPresenceEventDetail {
  userId?: string;
  id?: string;
  joining?: boolean;
  isJoining?: boolean;
  userName?: string;
}

export interface TypingIndicatorEventDetail {
  userId: string;
  isTyping: boolean;
}

export interface WebSocketErrorEventDetail {
  message: string;
  frame?: unknown;
}

// ─── OT (prosemirror-collab) WebSocket payloads ───────────────────────────────

/** Server → all clients: accepted step broadcast on /topic/notes/{noteId}/ot */
export interface OTStepsBroadcastDetail {
  version: number;
  steps: object[];
  clientId: string;
}

/** Server → single client: catch-up on /user/queue/notes/{noteId}/ot-catchup */
export interface OTCatchUpDetail {
  version: number;
  steps: Array<{ step: object; clientId: string }>;
}

