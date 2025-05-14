// src/pages/NoteEditor.jsx
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import NoteService from '../services/NoteService'; 
import ActiveUsersList from '../components/ActiveUsersList'; 
import CollaboratorsList from '../components/CollaboratorsList';
import { useWebSocket } from "../services/WebSocketProvider.jsx"; 

import {
    Box,
    TextField,
    CircularProgress,
    Alert,
    Chip,
    AppBar,
    Toolbar,
    Typography,
    IconButton,
    Snackbar,
    Paper
} from '@mui/material';
import { ArrowBack as ArrowBackIcon, Save as SaveIcon } from '@mui/icons-material';
import { styled } from '@mui/material/styles';

const ContentArea = styled(Box)(({ theme }) => ({
    display: 'flex',
    height: 'calc(100vh - 64px)',
    [theme.breakpoints.down('md')]: {
        flexDirection: 'column',
    },
}));

const EditorSection = styled(Box)(({ theme }) => ({
    flexGrow: 1,
    padding: theme.spacing(3),
    display: 'flex',
    flexDirection: 'column',
    backgroundColor: theme.palette.background.default,
}));

const SidebarSection = styled(Paper)(({ theme }) => ({
    width: 300,
    padding: theme.spacing(2),
    display: 'flex',
    flexDirection: 'column',
    borderLeft: `1px solid ${theme.palette.divider}`,
    [theme.breakpoints.down('md')]: {
        width: '100%',
        height: 'auto',
        marginTop: theme.spacing(2),
        borderLeft: 'none',
        borderTop: `1px solid ${theme.palette.divider}`,
    },
}));

const TitleField = styled(TextField)(({ theme }) => ({
    marginBottom: theme.spacing(2),
    '& .MuiOutlinedInput-root': {
        borderRadius: theme.shape.borderRadius,
        fontSize: '1.2rem',
        fontWeight: 500,
    },
}));

const ContentTextField = styled(TextField)(({ theme }) => ({
    flexGrow: 1,
    '& .MuiOutlinedInput-root': {
        height: '100%',
        alignItems: 'flex-start',
        padding: theme.spacing(2),
    },
    '& .MuiOutlinedInput-input': {
        height: '100%',
        overflow: 'auto',
        lineHeight: 1.5,
        fontSize: '1rem',
        fontFamily: "'Roboto', 'Helvetica', 'Arial', sans-serif",
    },
}));

const StatusChip = styled(Chip)(({ theme }) => ({
    marginLeft: theme.spacing(1),
    color: 'white',
    '&.connected': {
        backgroundColor: theme.palette.success.main,
    },
    '&.disconnected': {
        backgroundColor: theme.palette.warning.main,
    },
    '&.saving': {
        backgroundColor: theme.palette.info.main,
    },
}));

function NoteEditor() {
    const { noteId } = useParams();
    const { currentUser } = useAuth();
    const navigate = useNavigate();

    const [note, setNote] = useState({ title: '', content: '' });
    const [localContent, setLocalContent] = useState('');
    const [localTitle, setLocalTitle] = useState('');
    const [collaborators, setCollaborators] = useState([]);
    // State to store active users as a Map for efficient updates by userId
    const [activeUsersState, setActiveUsersState] = useState(new Map());
    // Store fetched user details separately, keyed by ID
    const [fetchedUserDetails, setFetchedUserDetails] = useState({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [notification, setNotification] = useState('');
    const [showNotification, setShowNotification] = useState(false);
    const [isOwner, setIsOwner] = useState(false);
    const [owner, setOwner] = useState(null);
    const [isNoteModified, setIsNoteModified] = useState(false);

    const {
        connected,
        subscribeToNote,
        unsubscribeFromNote,
        sendNoteUpdate,
        sendTypingIndicator
    } = useWebSocket();

    const saveTimeoutRef = useRef(null);
    const typingTimeoutRef = useRef(null);
    const lastSavedContentRef = useRef({ title: '', content: '' });
    const pendingUpdatesRef = useRef(0);

    // Use useCallback for the lookup function to prevent unnecessary re-creation
    const lookupUserById = useCallback(async (userId) => {
        if (!userId || fetchedUserDetails[userId]) {
            return;
        }
        console.log(`Looking up user details for ID: ${userId}`);
        try {
            const userDetails = await NoteService.lookupUserById(userId);
            console.log(`Fetched details for ${userId}:`, userDetails);
            setFetchedUserDetails(prevDetails => ({
                ...prevDetails,
                [userId]: userDetails
            }));
        } catch (error) {
            console.error(`Error looking up user ${userId}:`, error);
            setFetchedUserDetails(prevDetails => ({
                ...prevDetails,
                [userId]: { userId: userId, email: `User-${userId.substring(0, 6)}...`, displayName: `User-${userId.substring(0, 6)}...` }
            }));
        }
    }, [fetchedUserDetails]);

    // Initial note load
    useEffect(() => {
        const fetchNote = async () => {
            try {
                const noteData = await NoteService.getNoteById(noteId);

                setNote(noteData);
                setLocalTitle(noteData.title || '');
                setLocalContent(noteData.content || '');
                lastSavedContentRef.current = {
                    title: noteData.title || '',
                    content: noteData.content || ''
                };
                setCollaborators(noteData.collaboratorIds || []);
                setIsOwner(noteData.ownerId === currentUser?.uid);
                setOwner(noteData.ownerId);
                setLoading(false);

                // Initial lookup for owner and collaborators
                if (noteData.ownerId) lookupUserById(noteData.ownerId);
                if (noteData.collaboratorIds) {
                    noteData.collaboratorIds.forEach(id => lookupUserById(id));
                }

            } catch (error) {
                console.error("Error fetching note:", error);
                setError('Failed to load note: ' + (error.response?.data?.message || error.message));
                setLoading(false);
            }
        };

        if (noteId && currentUser) {
            fetchNote();
        }
    }, [noteId, currentUser, lookupUserById]);

    // WebSocket connection and event handling
    useEffect(() => {
        if (loading || !currentUser?.uid || !noteId) return;

        console.log("Setting up WebSocket connection and event handlers");

        // Subscribe to the note if we have a connection
        if (connected) {
            console.log("WebSocket connected, subscribing to note:", noteId);
            subscribeToNote(noteId);
        }

        const handleNoteUpdate = (e) => {
            const data = e.detail;
            console.log("Received note update:", data);

            if (data.userId !== currentUser.uid) {
                setNote(prev => {
                    const updatedNote = {
                        ...prev,
                        title: data.title,
                        content: data.content
                    };
                    setLocalTitle(data.title || '');
                    setLocalContent(data.content || '');
                    lastSavedContentRef.current = { title: data.title || '', content: data.content || '' };
                    return updatedNote;
                });

                setNotification('Document updated by another user');
                setShowNotification(true);
            }
        };

        // Handler for initial note state and subsequent full state updates
        const handleNoteState = (e) => {
            console.log("NOTE_EDITOR: Received 'note-state' event. Detail:", e.detail);

            const data = e.detail;
            if (!data) {
                console.error("NOTE_EDITOR: Note state data is empty or undefined");
                return;
            }

            console.log("NOTE_EDITOR: Processing note state with title:", data.title);

            // Update note content and title
            setNote(prev => ({ ...prev, title: data.title || prev.title, content: data.content || prev.content }));

            // Update local state 
            if (data.title !== undefined) setLocalTitle(data.title || '');
            if (data.content !== undefined) setLocalContent(data.content || '');

            // Update last saved content reference
            if (data.title !== undefined || data.content !== undefined) {
                lastSavedContentRef.current = {
                    title: data.title || '',
                    content: data.content || ''
                };
            }

            // Process active users from the state message
            if (data.activeUsers) {
                console.log("NOTE_EDITOR: Processing active users from note-state:", data.activeUsers);
                try {
                    const incomingActiveUsers = data.activeUsers;
                    setActiveUsersState(prevActiveUsers => {
                        const updatedUsers = new Map(prevActiveUsers);

                        if (typeof incomingActiveUsers === 'object' && incomingActiveUsers !== null) {
                            Object.entries(incomingActiveUsers).forEach(([userId, userData]) => {
                                if (userId) lookupUserById(userId);

                                updatedUsers.set(userId, {
                                    userId: userId,
                                    isTyping: prevActiveUsers.has(userId) ? prevActiveUsers.get(userId).isTyping : false,
                                });
                            });
                        } else if (Array.isArray(incomingActiveUsers)) {
                            incomingActiveUsers.forEach(user => {
                                const userId = user.userId || user.id;
                                if (userId) {
                                    lookupUserById(userId);
                                    updatedUsers.set(userId, {
                                        userId: userId,
                                        isTyping: prevActiveUsers.has(userId) ? prevActiveUsers.get(userId).isTyping : false,
                                    });
                                }
                            });
                        } else {
                            console.warn("NOTE_EDITOR: data.activeUsers from note-state is not a processable object or array:", incomingActiveUsers);
                        }

                        // Remove users who are no longer in the active list
                        const incomingUserIds = new Set(
                            Array.isArray(incomingActiveUsers) ?
                                incomingActiveUsers.map(user => user.userId || user.id).filter(Boolean) :
                                (typeof incomingActiveUsers === 'object' && incomingActiveUsers !== null ? Object.keys(incomingActiveUsers) : [])
                        );

                        prevActiveUsers.forEach((_, userId) => {
                            if (!incomingUserIds.has(userId)) {
                                updatedUsers.delete(userId);
                            }
                        });

                        console.log("NOTE_EDITOR: Updated active users state:", updatedUsers);
                        return updatedUsers;
                    });


                } catch (error) {
                    console.error("NOTE_EDITOR: Error processing active users from state:", error);
                }
            }

            // Process collaborators from the state message - store IDs and trigger lookup
            if (data.collaborators) {
                console.log("NOTE_EDITOR: Processing collaborators from note-state:", data.collaborators);
                try {
                    const collabArray = typeof data.collaborators === 'object' && data.collaborators !== null ?
                        Object.values(data.collaborators) : (Array.isArray(data.collaborators) ? data.collaborators : []);

                    const collaboratorIds = collabArray
                        .map(c => {
                            const userId = c.userId || c.id;
                            if (userId) lookupUserById(userId);
                            return userId;
                        })
                        .filter(Boolean);

                    console.log("NOTE_EDITOR: Setting collaborators:", collaboratorIds);
                    setCollaborators(collaboratorIds);
                } catch (error) {
                    console.error("NOTE_EDITOR: Error processing collaborators from state:", error);
                }
            }
        };


        // Handler for individual user presence updates (joining/leaving)
        const handleUserPresence = (e) => {
            const data = e.detail;
            console.log("Received user presence update:", data);

            if (data.userId) {
                lookupUserById(data.userId);
            }

            setActiveUsersState(prevActiveUsers => {
                const updatedUsers = new Map(prevActiveUsers);

                if (data.joining) {
                    updatedUsers.set(data.userId, {
                        userId: data.userId,
                        isTyping: prevActiveUsers.has(data.userId) ? prevActiveUsers.get(data.userId).isTyping : false,
                    });
                } else {
                    updatedUsers.delete(data.userId);
                }
                console.log("NOTE_EDITOR: Updated active users state from presence:", updatedUsers);
                return updatedUsers;
            });

            const userDetails = fetchedUserDetails[data.userId];
            const userDisplay = userDetails?.email || userDetails?.displayName || data.userName || 'A user';

            if (data.joining) {
                setNotification(`${userDisplay} joined the document`);
            } else {
                setNotification(`${userDisplay} left the document`);
            }
            setShowNotification(true);
        };

        const handleTypingIndicator = (e) => {
            const data = e.detail;
            console.log("Received typing indicator:", data);

            setActiveUsersState(prevActiveUsers => {
                const updatedUsers = new Map(prevActiveUsers);
                const user = updatedUsers.get(data.userId);

                if (user) {
                    updatedUsers.set(data.userId, { ...user, isTyping: data.isTyping });
                    console.log(`NOTE_EDITOR: Updated typing status for ${data.userId} to ${data.isTyping}`);
                } else {
                    console.warn(`NOTE_EDITOR: Typing indicator received for unknown user ${data.userId}. Adding with typing status.`);
                    updatedUsers.set(data.userId, {
                        userId: data.userId,
                        isTyping: data.isTyping,
                    });
                    lookupUserById(data.userId);
                }
                console.log("NOTE_EDITOR: Updated active users state from typing:", updatedUsers);
                return updatedUsers;
            });
        };


        const handleError = (e) => {
            const errorData = e.detail;
            console.error("Received WebSocket error:", errorData);
            setError(errorData.message || 'An error occurred');
        };

        // Event Listener Subscriptions
        window.addEventListener('note-update', handleNoteUpdate);
        window.addEventListener('note-state', handleNoteState);
        window.addEventListener('user-presence', handleUserPresence);
        window.addEventListener('typing-indicator', handleTypingIndicator);
        window.addEventListener('websocket-error', handleError);

        // Cleanup Function
        return () => {
            console.log("Cleaning up WebSocket event listeners and subscriptions");

            window.removeEventListener('note-update', handleNoteUpdate);
            window.removeEventListener('note-state', handleNoteState);
            window.removeEventListener('user-presence', handleUserPresence);
            window.removeEventListener('typing-indicator', handleTypingIndicator);
            window.removeEventListener('websocket-error', handleError);

            if (connected && noteId) {
                unsubscribeFromNote(noteId);
            }

            if (saveTimeoutRef.current) {
                clearTimeout(saveTimeoutRef.current);
            }
            if (typingTimeoutRef.current) {
                clearTimeout(typingTimeoutRef.current);
            }
        };

    }, [noteId, currentUser, loading, connected, subscribeToNote, unsubscribeFromNote, lookupUserById, fetchedUserDetails]);


    // Save note function
    const saveNote = async () => {
        if (saving) return;

        if (localTitle === lastSavedContentRef.current.title &&
            localContent === lastSavedContentRef.current.content) {
            setIsNoteModified(false);
            return;
        }

        try {
            setSaving(true);
            pendingUpdatesRef.current += 1;

            const titleToSave = localTitle;
            const contentToSave = localContent;

            const updatedNote = { ...note, title: titleToSave, content: contentToSave };
            setNote(updatedNote);

            console.log("Saving note to server via HTTP:", updatedNote);
            await NoteService.updateNote(noteId, updatedNote);

            console.log("Sending note update through WebSocket:", titleToSave, contentToSave);
            sendNoteUpdate(noteId, titleToSave, contentToSave);

            lastSavedContentRef.current = { title: titleToSave, content: contentToSave };

            pendingUpdatesRef.current -= 1;
            setSaving(false);
            setIsNoteModified(false);

            console.log("Note saved successfully");
        } catch (error) {
            console.error("Error saving note:", error);
            setError('Failed to save: ' + (error.response?.data?.message || error.message));
            pendingUpdatesRef.current -= 1;
            setSaving(false);
        }
    };

    // Force save on demand
    const handleForceSave = () => {
        if (saveTimeoutRef.current) {
            clearTimeout(saveTimeoutRef.current);
            saveTimeoutRef.current = null;
        }
        saveNote();
    };

    const handleChange = (field, value) => {
        if (field === 'title') {
            setLocalTitle(value);
        } else if (field === 'content') {
            setLocalContent(value);
        }

        setIsNoteModified(true);

        if (typingTimeoutRef.current) {
            clearTimeout(typingTimeoutRef.current);
        } else {
            if (pendingUpdatesRef.current === 0) {
                sendTypingIndicator(noteId, true);
            }
        }

        typingTimeoutRef.current = setTimeout(() => {
            sendTypingIndicator(noteId, false);
            typingTimeoutRef.current = null;
        }, 2000);

        if (saveTimeoutRef.current) {
            clearTimeout(saveTimeoutRef.current);
        }

        saveTimeoutRef.current = setTimeout(() => {
            saveNote();
            saveTimeoutRef.current = null;
        }, 1500);
    };

    const handleCollaboratorChange = (updatedCollaborators) => {
        setCollaborators(updatedCollaborators);
    };

    const handleCloseNotification = () => {
        setShowNotification(false);
    };

    const activeUsersForList = Array.from(activeUsersState.values()).map(user => {
        const details = fetchedUserDetails[user.userId] || { userId: user.userId, email: `Loading user ${user.userId.substring(0, 6)}...` };
        return {
            ...user,
            ...details, 
            display: details.email || `User ${user.userId.substring(0, 6)}...`
        };
    }).filter(user => user && user.userId); 


    // Status indicator text
    const getStatusText = () => {
        if (saving) return 'Saving...';
        if (isNoteModified) return 'Modified';
        if (connected) return 'Connected';
        return 'Connecting...';
    };

    // Status indicator class
    const getStatusClass = () => {
        if (saving) return 'saving';
        if (connected) return 'connected';
        return 'disconnected';
    };

    return (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
            <AppBar position="static">
                <Toolbar>
                    <IconButton
                        color="inherit"
                        edge="start"
                        onClick={() => navigate('/dashboard')}
                        sx={{ mr: 2 }}
                        title="Back to Dashboard"
                    >
                        <ArrowBackIcon />
                    </IconButton>
                    <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
                        {isOwner ? 'Edit Note' : 'View Note'}
                    </Typography>
                    {isNoteModified && (
                        <IconButton
                            color="inherit"
                            onClick={handleForceSave}
                            sx={{ mr: 1 }}
                            title="Save Note"
                        >
                            <SaveIcon />
                        </IconButton>
                    )}
                    <StatusChip
                        label={getStatusText()}
                        className={getStatusClass()}
                        size="small"
                        variant="outlined"
                    />
                </Toolbar>
            </AppBar>

            {error && (
                <Alert
                    severity="error"
                    sx={{ m: 2 }}
                    onClose={() => setError('')}
                >
                    {error}
                </Alert>
            )}

            <Snackbar
                open={showNotification}
                autoHideDuration={4000}
                onClose={handleCloseNotification}
                message={notification}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
            />

            {loading ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', flexGrow: 1 }}>
                    <CircularProgress />
                </Box>
            ) : (
                <ContentArea>
                    <EditorSection>
                        <TitleField
                            fullWidth
                            variant="outlined"
                            label="Title"
                            value={localTitle}
                            onChange={(e) => handleChange('title', e.target.value)}
                            placeholder="Note Title"
                            InputProps={{
                                style: {
                                    fontWeight: 500,
                                    fontSize: '1.2rem'
                                }
                            }}
                        />
                        <ContentTextField
                            fullWidth
                            multiline
                            variant="outlined"
                            label="Content"
                            value={localContent}
                            onChange={(e) => handleChange('content', e.target.value)}
                            placeholder="Start typing your note content here..."
                            InputProps={{
                                sx: {
                                    alignItems: 'flex-start',
                                    '& .MuiInputBase-input': {
                                        verticalAlign: 'top'
                                    }
                                }
                            }}
                        />
                    </EditorSection>

                    <SidebarSection elevation={0}>
                        {/* Pass the prepared active users list to ActiveUsersList */}
                        <ActiveUsersList
                            activeUsers={activeUsersForList}
                            currentUser={currentUser}
                        />

                        <CollaboratorsList
                            noteId={noteId}
                            collaborators={collaborators}
                            owner={owner}
                            onCollaboratorChange={handleCollaboratorChange}
                            isOwner={isOwner}
                            fetchedUserDetails={fetchedUserDetails}
                        />
                    </SidebarSection>
                </ContentArea>
            )}
        </Box>
    );
}

export default NoteEditor;
