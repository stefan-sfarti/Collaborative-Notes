// src/pages/Dashboard.js
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import NoteService from '../services/NoteService';
import {
    Box,
    Typography,
    Container,
    Grid,
    Card,
    CardContent,
    CardActions,
    Button,
    AppBar,
    Toolbar,
    IconButton,
    CircularProgress,
    Alert,
    Menu,
    MenuItem,
    Snackbar
} from '@mui/material';
import { Add as AddIcon, Delete as DeleteIcon, MoreVert as MoreVertIcon, Refresh as RefreshIcon } from '@mui/icons-material';
import { styled } from '@mui/material/styles';

const NoteCard = styled(Card)(({ theme }) => ({
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    transition: 'transform 0.2s ease-in-out',
    '&:hover': {
        transform: 'translateY(-4px)',
        boxShadow: theme.shadows[4],
    },
    cursor: 'pointer',
}));

const NoteCardContent = styled(CardContent)({
    flexGrow: 1,
});

function Dashboard() {
    const [notes, setNotes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [createLoading, setCreateLoading] = useState(false);
    const [error, setError] = useState('');
    const [apiStatus, setApiStatus] = useState(true); // Track API connectivity
    const { currentUser, logout, token, getFreshToken } = useAuth();
    const navigate = useNavigate();
    const [anchorEl, setAnchorEl] = useState(null);
    const open = Boolean(anchorEl);
    const [snackbarOpen, setSnackbarOpen] = useState(false);
    const [snackbarMessage, setSnackbarMessage] = useState('');

    useEffect(() => {
        const fetchNotes = async () => {
            if (!apiStatus) return;

            try {
                // Ensure we have a fresh token before fetching
                await getFreshToken();
                const notesData = await NoteService.getAllNotes();
                setNotes(notesData);
                setError('');
            } catch (error) {
                console.error('Error fetching notes:', error);
                setError(`Failed to fetch notes: ${error.response?.data?.message || error.message || 'Network Error'}`);

                // Check if token is invalid
                if (error.response?.status === 401) {
                    showSnackbar('Your session has expired. Please log in again.');
                    setTimeout(() => {
                        handleLogout();
                    }, 2000);
                }
            } finally {
                setLoading(false);
            }
        };

        if (currentUser && token) {
            fetchNotes();
        }
    }, [currentUser, token, apiStatus]);

    const handleCreateNote = async () => {
        if (!apiStatus) {
            setError('Cannot create note: API server is not available');
            return;
        }

        try {
            setCreateLoading(true);
            const newNote = {
                title: 'New Note',
                content: '',
            };

            // Ensure fresh token before creating note
            await getFreshToken();
            const createdNote = await NoteService.createNote(newNote);
            navigate(`/notes/${createdNote.id}`);
        } catch (error) {
            console.error('Error creating note:', error);
            setError(`Failed to create note: ${error.response?.data?.message || error.message || 'Unknown error'}`);

            if (error.response?.status === 401) {
                showSnackbar('Your session has expired. Please log in again.');
                setTimeout(() => {
                    handleLogout();
                }, 2000);
            }
        } finally {
            setCreateLoading(false);
        }
    };

    const handleDeleteNote = async (noteId, e) => {
        e.preventDefault();
        e.stopPropagation();

        if (!apiStatus) {
            setError('Cannot delete note: API server is not available');
            return;
        }

        try {
            // Ensure fresh token before deleting
            await getFreshToken();
            await NoteService.deleteNote(noteId);
            setNotes(notes.filter(note => note.id !== noteId));
            showSnackbar('Note deleted successfully');
        } catch (error) {
            console.error('Error deleting note:', error);
            setError(`Failed to delete note: ${error.response?.data?.message || error.message || 'Unknown error'}`);

            if (error.response?.status === 401) {
                showSnackbar('Your session has expired. Please log in again.');
                setTimeout(() => {
                    handleLogout();
                }, 2000);
            }
        }
    };

    const handleRefresh = async () => {
        setLoading(true);
        setError('');

        // First check API connectivity
        const isConnected = await NoteService.checkApiConnection();
        setApiStatus(isConnected);

        if (!isConnected) {
            setError('Cannot connect to API server. Please check if the backend is running.');
            setLoading(false);
            return;
        }

        try {
            // Ensure fresh token before fetching
            await getFreshToken();
            const notesData = await NoteService.getAllNotes();
            setNotes(notesData);
            showSnackbar('Notes refreshed successfully');
        } catch (error) {
            setError(`Failed to fetch notes: ${error.response?.data?.message || error.message || 'Network Error'}`);

            if (error.response?.status === 401) {
                showSnackbar('Your session has expired. Please log in again.');
                setTimeout(() => {
                    handleLogout();
                }, 2000);
            }
        } finally {
            setLoading(false);
        }
    };

    const handleLogout = async () => {
        try {
            await logout();
            navigate('/login');
        } catch (error) {
            setError('Failed to log out: ' + error.message);
        }
    };

    const handleMenuClick = (event) => {
        setAnchorEl(event.currentTarget);
    };

    const handleClose = () => {
        setAnchorEl(null);
    };

    const handleCardClick = (noteId) => {
        navigate(`/notes/${noteId}`);
    };

    const showSnackbar = (message) => {
        setSnackbarMessage(message);
        setSnackbarOpen(true);
    };

    const handleSnackbarClose = () => {
        setSnackbarOpen(false);
    };

    // Check if user is authenticated
    useEffect(() => {
        if (!currentUser) {
            navigate('/login');
        }
    }, [currentUser, navigate]);

    return (
        <Box sx={{ flexGrow: 1 }}>
            <AppBar position="static">
                <Toolbar>
                    <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
                        CollabNotes
                    </Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center' }}>
                        <IconButton
                            color="inherit"
                            onClick={handleRefresh}
                            disabled={loading}
                            sx={{ mr: 1 }}
                        >
                            <RefreshIcon />
                        </IconButton>
                        <Typography variant="body1" sx={{ mr: 2 }}>
                            {currentUser?.email}
                        </Typography>
                        <IconButton
                            color="inherit"
                            aria-controls="menu-appbar"
                            aria-haspopup="true"
                            onClick={handleMenuClick}
                        >
                            <MoreVertIcon />
                        </IconButton>
                        <Menu
                            id="menu-appbar"
                            anchorEl={anchorEl}
                            anchorOrigin={{
                                vertical: 'bottom',
                                horizontal: 'right',
                            }}
                            keepMounted
                            transformOrigin={{
                                vertical: 'top',
                                horizontal: 'right',
                            }}
                            open={open}
                            onClose={handleClose}
                        >
                            <MenuItem onClick={handleLogout}>Logout</MenuItem>
                        </Menu>
                    </Box>
                </Toolbar>
            </AppBar>

            <Container sx={{ mt: 4, mb: 4 }}>
                {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
                {!apiStatus && (
                    <Alert severity="warning" sx={{ mb: 2 }}>
                        Cannot connect to the API server. Please check if the backend is running
                    </Alert>
                )}

                <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 3 }}>
                    <Button
                        variant="contained"
                        color="primary"
                        startIcon={createLoading ? <CircularProgress size={20} color="inherit" /> : <AddIcon />}
                        onClick={handleCreateNote}
                        disabled={createLoading || !apiStatus}
                    >
                        Create New Note
                    </Button>
                </Box>

                {loading ? (
                    <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
                        <CircularProgress />
                    </Box>
                ) : notes.length === 0 ? (
                    <Box sx={{ p: 4, textAlign: 'center' }}>
                        <Typography variant="h6" color="textSecondary">
                            No notes yet. Create your first note to get started!
                        </Typography>
                    </Box>
                ) : (
                    <Grid container spacing={3}>
                        {notes.map(note => (
                            <Grid item xs={12} sm={6} md={4} key={note.id}>
                                <NoteCard onClick={() => handleCardClick(note.id)}>
                                    <NoteCardContent>
                                        <Typography variant="h6" component="h3" gutterBottom noWrap>
                                            {note.title || 'Untitled Note'}
                                        </Typography>
                                        <Typography variant="body2" color="textSecondary" sx={{
                                            display: '-webkit-box',
                                            WebkitLineClamp: 3,
                                            WebkitBoxOrient: 'vertical',
                                            overflow: 'hidden',
                                            textOverflow: 'ellipsis'
                                        }}>
                                            {note.content || 'No content'}
                                        </Typography>
                                    </NoteCardContent>
                                    <CardActions sx={{ justifyContent: 'space-between' }}>
                                        <Typography variant="caption" color="textSecondary">
                                            {new Date(note.updatedAt).toLocaleDateString()}
                                        </Typography>
                                        <IconButton
                                            size="small"
                                            color="error"
                                            onClick={(e) => handleDeleteNote(note.id, e)}
                                        >
                                            <DeleteIcon />
                                        </IconButton>
                                    </CardActions>
                                </NoteCard>
                            </Grid>
                        ))}
                    </Grid>
                )}
            </Container>

            <Snackbar
                open={snackbarOpen}
                autoHideDuration={6000}
                onClose={handleSnackbarClose}
                message={snackbarMessage}
            />
        </Box>
    );
}

export default Dashboard;