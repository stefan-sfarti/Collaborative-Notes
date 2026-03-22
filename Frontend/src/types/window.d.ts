import type {
  NoteUpdateEventDetail,
  NoteStateEventDetail,
  UserPresenceEventDetail,
  TypingIndicatorEventDetail,
  WebSocketErrorEventDetail,
} from "./index";

declare global {
  interface WindowEventMap {
    "note-update": CustomEvent<NoteUpdateEventDetail>;
    "note-state": CustomEvent<NoteStateEventDetail>;
    "user-presence": CustomEvent<UserPresenceEventDetail>;
    "typing-indicator": CustomEvent<TypingIndicatorEventDetail>;
    "websocket-error": CustomEvent<WebSocketErrorEventDetail>;
  }
}
