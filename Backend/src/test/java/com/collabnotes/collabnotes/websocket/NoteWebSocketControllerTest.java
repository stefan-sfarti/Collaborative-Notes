package com.collabnotes.collabnotes.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.collabnotes.collabnotes.dto.NoteDTO;
import com.collabnotes.collabnotes.dto.UserResponse;
import com.collabnotes.collabnotes.exception.ConflictException;
import com.collabnotes.collabnotes.metrics.MetricsService;
import com.collabnotes.collabnotes.service.NoteService;
import com.collabnotes.collabnotes.service.NoteSessionService;
import com.collabnotes.collabnotes.service.UserService;
import com.collabnotes.collabnotes.util.JwtUtil;
import com.collabnotes.collabnotes.websocket.message.CommentMessage;
import com.collabnotes.collabnotes.websocket.message.CursorPositionMessage;
import com.collabnotes.collabnotes.websocket.message.ErrorMessage;
import com.collabnotes.collabnotes.websocket.message.NoteContentUpdateMessage;
import com.collabnotes.collabnotes.websocket.message.NotePartialUpdateMessage;
import com.collabnotes.collabnotes.websocket.message.TypingIndicatorMessage;
import com.collabnotes.collabnotes.websocket.message.UserPresenceMessage;

@ExtendWith(MockitoExtension.class)
class NoteWebSocketControllerTest {

    @Mock
    private NoteService noteService;

    @Mock
    private UserService userService;

    @Mock
    private NoteSessionService sessionService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MetricsService metricsService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private SimpMessageHeaderAccessor headerAccessor;

    private NoteWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new NoteWebSocketController(noteService, userService,
                sessionService, messagingTemplate, metricsService, jwtUtil);
    }

    @Nested
    class UpdateNote {

        @Test
        void whenValidTokenAndAccess_updatesAndReturnsBroadcastMessage() throws Exception {
            when(jwtUtil.extractUserId("Bearer token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);

            NoteDTO updatedNote = new NoteDTO();
            updatedNote.setVersion(5L);
            when(noteService.updateNote(eq("note-1"), any(NoteDTO.class), eq("user-1")))
                    .thenReturn(updatedNote);

            NoteContentUpdateMessage message = new NoteContentUpdateMessage();
            message.setTitle("Updated");
            message.setContent("New content");
            message.setVersionNumber(4);

            NoteContentUpdateMessage result = controller.updateNote("note-1", message,
                    "Bearer token", headerAccessor);

            assertNotNull(result);
            assertEquals("user-1", result.getUserId());
            assertEquals("note-1", result.getNoteId());
            assertEquals(5L, result.getVersionNumber());
        }

        @Test
        void whenNoToken_throwsIllegalArgument() {
            when(jwtUtil.extractUserId(null)).thenReturn(null);

            NoteContentUpdateMessage message = new NoteContentUpdateMessage();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.updateNote("note-1", message, null, headerAccessor));
        }

        @Test
        void whenNoAccess_throwsIllegalArgument() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(false);

            NoteContentUpdateMessage message = new NoteContentUpdateMessage();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.updateNote("note-1", message, "token", headerAccessor));
        }

        @Test
        void whenConflictException_sendsErrorToUserAndReturnsNull() throws Exception {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);
            when(noteService.updateNote(eq("note-1"), any(NoteDTO.class), eq("user-1")))
                    .thenThrow(new ConflictException("Version conflict"));

            NoteContentUpdateMessage message = new NoteContentUpdateMessage();
            message.setTitle("T");
            message.setContent("C");

            NoteContentUpdateMessage result = controller.updateNote("note-1", message,
                    "token", headerAccessor);

            assertNull(result);
            verify(messagingTemplate).convertAndSendToUser(eq("user-1"),
                    eq("/queue/errors"), any(ErrorMessage.class));
        }

        @Test
        void whenUpdateReturnsNull_throwsIllegalState() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);
            when(noteService.updateNote(eq("note-1"), any(NoteDTO.class), eq("user-1")))
                    .thenReturn(null);

            NoteContentUpdateMessage message = new NoteContentUpdateMessage();
            message.setTitle("T");
            message.setContent("C");

            assertThrows(IllegalStateException.class,
                    () -> controller.updateNote("note-1", message, "token", headerAccessor));
        }

        @Test
        void whenVersionNumberIsZero_sendsNullVersionToService() throws Exception {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);

            NoteDTO updatedNote = new NoteDTO();
            updatedNote.setVersion(1L);

            ArgumentCaptor<NoteDTO> dtoCaptor = ArgumentCaptor.forClass(NoteDTO.class);
            when(noteService.updateNote(eq("note-1"), dtoCaptor.capture(), eq("user-1")))
                    .thenReturn(updatedNote);

            NoteContentUpdateMessage message = new NoteContentUpdateMessage();
            message.setTitle("T");
            message.setContent("C");
            message.setVersionNumber(0);

            controller.updateNote("note-1", message, "token", headerAccessor);

            assertNull(dtoCaptor.getValue().getVersion());
        }

        @Test
        void recordsMetricsRegardlessOfOutcome() throws Exception {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);

            NoteDTO updatedNote = new NoteDTO();
            updatedNote.setVersion(1L);
            when(noteService.updateNote(eq("note-1"), any(NoteDTO.class), eq("user-1")))
                    .thenReturn(updatedNote);

            NoteContentUpdateMessage message = new NoteContentUpdateMessage();
            message.setTitle("T");
            message.setContent("C");

            controller.updateNote("note-1", message, "token", headerAccessor);

            verify(metricsService).recordOperation(eq("websocket.updateNote.time"), any(Long.class));
        }
    }

    @Nested
    class UpdateNotePartial {

        @Test
        void whenValid_setsUserIdAndReturnsMessage() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);

            NotePartialUpdateMessage message = new NotePartialUpdateMessage();
            message.setInsertText("hello");
            message.setPosition(10);

            NotePartialUpdateMessage result = controller.updateNotePartial("note-1", message,
                    "token", headerAccessor);

            assertNotNull(result);
            assertEquals("user-1", result.getUserId());
            assertEquals("note-1", result.getNoteId());
        }

        @Test
        void whenNoToken_throwsIllegalArgument() {
            when(jwtUtil.extractUserId(null)).thenReturn(null);

            NotePartialUpdateMessage message = new NotePartialUpdateMessage();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.updateNotePartial("note-1", message, null, headerAccessor));
        }

        @Test
        void whenNoAccess_throwsIllegalArgument() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(false);

            NotePartialUpdateMessage message = new NotePartialUpdateMessage();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.updateNotePartial("note-1", message, "token", headerAccessor));
        }
    }

    @Nested
    class UpdateCursorPosition {

        @Test
        void whenValid_setsUserIdAndReturnsMessage() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);

            CursorPositionMessage message = new CursorPositionMessage();
            message.setCursorPosition(42);

            CursorPositionMessage result = controller.updateCursorPosition("note-1", message,
                    "token", headerAccessor);

            assertNotNull(result);
            assertEquals("user-1", result.getUserId());
            assertEquals("note-1", result.getNoteId());
        }

        @Test
        void whenNoToken_throwsIllegalArgument() {
            when(jwtUtil.extractUserId(null)).thenReturn(null);

            CursorPositionMessage message = new CursorPositionMessage();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.updateCursorPosition("note-1", message, null, headerAccessor));
        }
    }

    @Nested
    class UpdatePresence {

        @Test
        void whenJoining_addsUserToSessionAndSetsUserName() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);
            when(userService.getUserInfo("user-1"))
                    .thenReturn(new UserResponse("user-1", "user@example.com", "User", null));

            UserPresenceMessage message = new UserPresenceMessage();
            message.setJoining(true);

            UserPresenceMessage result = controller.updatePresence("note-1", message,
                    "token", headerAccessor);

            assertNotNull(result);
            assertEquals("user-1", result.getUserId());
            assertEquals("user@example.com", result.getUserName());
            verify(sessionService).addUserToNote("note-1", "user-1");
        }

        @Test
        void whenLeaving_removesUserFromSession() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);

            UserPresenceMessage message = new UserPresenceMessage();
            message.setJoining(false);

            UserPresenceMessage result = controller.updatePresence("note-1", message,
                    "token", headerAccessor);

            assertNotNull(result);
            verify(sessionService).removeUserFromNote("note-1", "user-1");
            verify(sessionService, never()).addUserToNote(anyString(), anyString());
        }

        @Test
        void whenJoining_andUserInfoNull_setsUnknownUserName() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);
            when(userService.getUserInfo("user-1")).thenReturn(null);

            UserPresenceMessage message = new UserPresenceMessage();
            message.setJoining(true);

            UserPresenceMessage result = controller.updatePresence("note-1", message,
                    "token", headerAccessor);

            assertEquals("Unknown", result.getUserName());
        }

        @Test
        void whenNoToken_throwsIllegalArgument() {
            when(jwtUtil.extractUserId(null)).thenReturn(null);

            UserPresenceMessage message = new UserPresenceMessage();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.updatePresence("note-1", message, null, headerAccessor));
        }
    }

    @Nested
    class UpdateTypingStatus {

        @Test
        void whenValid_setsUserIdAndReturnsMessage() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);

            TypingIndicatorMessage message = new TypingIndicatorMessage();
            message.setTyping(true);

            TypingIndicatorMessage result = controller.updateTypingStatus("note-1", message,
                    "token", headerAccessor);

            assertNotNull(result);
            assertEquals("user-1", result.getUserId());
            assertEquals("note-1", result.getNoteId());
        }

        @Test
        void whenNoToken_throwsIllegalArgument() {
            when(jwtUtil.extractUserId(null)).thenReturn(null);

            TypingIndicatorMessage message = new TypingIndicatorMessage();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.updateTypingStatus("note-1", message, null, headerAccessor));
        }
    }

    @Nested
    class HandleComment {

        @Test
        void whenValid_andNoteAccessible_returnsCommentMessage() throws Exception {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);

            NoteDTO note = new NoteDTO();
            note.setId("note-1");
            when(noteService.getNoteById("note-1", "user-1")).thenReturn(note);

            CommentMessage message = new CommentMessage();
            message.setCommentText("Nice work!");
            message.setAction("add");

            CommentMessage result = controller.handleComment("note-1", message,
                    "token", headerAccessor);

            assertNotNull(result);
            assertEquals("user-1", result.getUserId());
            assertEquals("note-1", result.getNoteId());
        }

        @Test
        void whenNoteNotFound_sendsErrorAndReturnsNull() throws Exception {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);
            when(noteService.getNoteById("note-1", "user-1")).thenReturn(null);

            CommentMessage message = new CommentMessage();
            message.setCommentText("Comment");

            CommentMessage result = controller.handleComment("note-1", message,
                    "token", headerAccessor);

            assertNull(result);
            verify(messagingTemplate).convertAndSendToUser(eq("user-1"),
                    eq("/queue/errors"), any(ErrorMessage.class));
        }

        @Test
        void whenNoToken_throwsIllegalArgument() {
            when(jwtUtil.extractUserId(null)).thenReturn(null);

            CommentMessage message = new CommentMessage();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.handleComment("note-1", message, null, headerAccessor));
        }
    }

    @Nested
    class RequestNoteState {

        @Test
        void whenValid_sendsStateToUserQueue() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");

            NoteDTO note = new NoteDTO();
            note.setId("note-1");
            note.setTitle("Title");
            note.setContent("Content");
            note.setOwnerId("owner-1");
            note.setVersion(3L);
            when(noteService.getNoteById("note-1", "user-1")).thenReturn(note);
            when(sessionService.isUserViewingNote("note-1", "user-1")).thenReturn(false);
            when(sessionService.getUsersViewingNote("note-1")).thenReturn(Set.of("user-1"));
            when(userService.getUserInfo("user-1"))
                    .thenReturn(new UserResponse("user-1", "u@e.com", "User", null));
            when(noteService.getNoteCollaborators("note-1", "user-1")).thenReturn(List.of());

            controller.requestNoteState("note-1", "token", headerAccessor);

            verify(sessionService).addUserToNote("note-1", "user-1");
            verify(messagingTemplate).convertAndSendToUser(eq("user-1"),
                    eq("/queue/notes/note-1/state"), any());
            verify(messagingTemplate).convertAndSend(eq("/topic/notes/note-1/presence"),
                    any(UserPresenceMessage.class));
        }

        @Test
        void whenNoToken_doesNotSendState() {
            when(jwtUtil.extractUserId(null)).thenReturn(null);

            controller.requestNoteState("note-1", null, headerAccessor);

            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        }

        @Test
        void whenNoteNotAccessible_sendsErrorToUser() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");
            when(noteService.getNoteById("note-1", "user-1")).thenReturn(null);

            controller.requestNoteState("note-1", "token", headerAccessor);

            verify(messagingTemplate).convertAndSendToUser(eq("user-1"),
                    eq("/queue/errors"), any(ErrorMessage.class));
        }

        @Test
        void whenUserAlreadyViewing_doesNotAddAgain() {
            when(jwtUtil.extractUserId("token")).thenReturn("user-1");

            NoteDTO note = new NoteDTO();
            note.setId("note-1");
            note.setTitle("T");
            note.setContent("C");
            note.setOwnerId("owner-1");
            note.setVersion(1L);
            when(noteService.getNoteById("note-1", "user-1")).thenReturn(note);
            when(sessionService.isUserViewingNote("note-1", "user-1")).thenReturn(true);
            when(sessionService.getUsersViewingNote("note-1")).thenReturn(Set.of("user-1"));
            when(userService.getUserInfo("user-1"))
                    .thenReturn(new UserResponse("user-1", "u@e.com", "User", null));
            when(noteService.getNoteCollaborators("note-1", "user-1")).thenReturn(List.of());

            controller.requestNoteState("note-1", "token", headerAccessor);

            verify(sessionService, never()).addUserToNote(anyString(), anyString());
        }
    }

    @Nested
    class ResolveUserId {

        @Test
        void whenTokenValid_returnsUserIdFromToken() throws Exception {
            when(jwtUtil.extractUserId("Bearer tok")).thenReturn("user-1");
            when(noteService.hasNoteAccess("note-1", "user-1")).thenReturn(true);

            NoteDTO updatedNote = new NoteDTO();
            updatedNote.setVersion(1L);
            when(noteService.updateNote(eq("note-1"), any(NoteDTO.class), eq("user-1")))
                    .thenReturn(updatedNote);

            NoteContentUpdateMessage message = new NoteContentUpdateMessage();
            message.setTitle("T");
            message.setContent("C");

            NoteContentUpdateMessage result = controller.updateNote("note-1", message,
                    "Bearer tok", headerAccessor);

            assertEquals("user-1", result.getUserId());
        }

        @Test
        void whenTokenInvalid_fallsBackToSessionAttribute() {
            when(jwtUtil.extractUserId("bad-token")).thenReturn(null);

            Map<String, Object> sessionAttrs = new HashMap<>();
            sessionAttrs.put("userId", "session-user");
            when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttrs);
            when(noteService.hasNoteAccess("note-1", "session-user")).thenReturn(true);

            TypingIndicatorMessage message = new TypingIndicatorMessage();
            message.setTyping(true);

            TypingIndicatorMessage result = controller.updateTypingStatus("note-1", message,
                    "bad-token", headerAccessor);

            assertEquals("session-user", result.getUserId());
        }

        @Test
        void whenTokenInvalid_andNoSessionAttribute_throwsIllegalArgument() {
            when(jwtUtil.extractUserId("bad-token")).thenReturn(null);
            when(headerAccessor.getSessionAttributes()).thenReturn(null);

            TypingIndicatorMessage message = new TypingIndicatorMessage();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.updateTypingStatus("note-1", message, "bad-token", headerAccessor));
        }

        @Test
        void whenTokenInvalid_andSessionUserIdBlank_throwsIllegalArgument() {
            when(jwtUtil.extractUserId("bad-token")).thenReturn(null);

            Map<String, Object> sessionAttrs = new HashMap<>();
            sessionAttrs.put("userId", "   ");
            when(headerAccessor.getSessionAttributes()).thenReturn(sessionAttrs);

            TypingIndicatorMessage message = new TypingIndicatorMessage();

            assertThrows(IllegalArgumentException.class,
                    () -> controller.updateTypingStatus("note-1", message, "bad-token", headerAccessor));
        }
    }
}
