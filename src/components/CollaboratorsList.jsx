// src/components/CollaboratorsList.jsx
import React, {useEffect, useState} from 'react';
import NoteService from '../services/NoteService';
import {
    Box,
    Typography,
    TextField,
    List,
    ListItem,
    ListItemText,
    ListItemSecondaryAction,
    IconButton,
    Paper,
    Divider,
    InputAdornment,
    CircularProgress,
    Avatar,
    ListItemAvatar,
    Tooltip
} from '@mui/material';
import {
    PersonAdd as PersonAddIcon,
    Delete as DeleteIcon,
    Person as PersonIcon,
    CheckCircle as CheckCircleIcon,
    Cake as CrownIcon // Using Cake icon as crown
} from '@mui/icons-material';
import { useAuth } from '../contexts/AuthContext';

/**
 * Component to display and manage note collaborators.
 * Expects 'collaborators' prop to be an array of user ID strings.
 */
function CollaboratorsList({ noteId, collaborators = [], onCollaboratorChange, isOwner = false, owner, fetchedUserDetails }) {
    const { currentUser } = useAuth();
    const [email, setEmail] = useState('');
    const [ownerEmail, setOwnerEmail] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    // Use fetchedUserDetails from NoteEditor instead of internal state for consistency
    // const [collaboratorEmails, setCollaboratorEmails] = useState({});

    // Effect to fetch owner email when owner ID changes
    useEffect(() => {
        async function fetchOwnerEmail() {
            try {
                const response = await NoteService.lookupUserById(owner);
                setOwnerEmail(response.email);
            } catch (error) {
                console.error('Error fetching owner email:', error);
                // Optionally set ownerEmail to a fallback or handle error display
                setOwnerEmail(`Owner-${owner?.substring(0, 6)}...`);
            }
        }

        if (owner) {
            fetchOwnerEmail();
        } else {
            setOwnerEmail(''); // Clear owner email if owner is null/undefined
        }
    }, [owner]); // Dependency: re-run if owner ID changes

    // Effect to trigger lookup for collaborators when the list of collaborator IDs changes
    // This relies on NoteEditor passing down fetchedUserDetails
    useEffect(() => {
        if (collaborators && collaborators.length > 0) {
            collaborators.forEach(collabId => {
                // Trigger lookup in NoteEditor via the passed lookupUserById (indirectly via fetchedUserDetails)
                // Or directly call lookupUserById if passed as a prop, but using fetchedUserDetails is better
                // Ensure collabId is a string before attempting lookup
                if (typeof collabId === 'string') {
                    // Note: The actual lookup happens in NoteEditor's useEffect when fetchedUserDetails changes
                    // This effect primarily ensures that NoteEditor's lookup is triggered for these IDs.
                    // If fetchedUserDetails is not passed down, you would call NoteService.lookupUserById(collabId) here.
                    // Assuming fetchedUserDetails is passed and updated by NoteEditor.
                } else {
                    console.warn("Invalid collaborator ID found:", collabId);
                }
            });
        }
    }, [collaborators, fetchedUserDetails]); // Dependencies: collaborators list and fetched details map

    const handleAddCollaborator = async (e) => {
        e.preventDefault();
        setError('');
        setSuccess('');

        if (!email.trim()) return;

        try {
            setLoading(true);

            // Look up the user by email
            const userData = await NoteService.lookupUserByEmail(email.trim());

            if (!userData || !userData.userId) {
                setError('User not found with this email address');
                return;
            }

            // Check if it's the current user
            if (userData.userId === currentUser.uid) {
                setError("You can't add yourself as a collaborator");
                return;
            }

            // Check if user is already a collaborator by checking the array of IDs
            if (collaborators.includes(userData.userId)) {
                setError("This user is already a collaborator");
                return;
            }

            // Add collaborator via backend service
            await NoteService.addCollaborator(noteId, userData.userId);

            // Update UI and notify parent component
            setSuccess(`${email} added as collaborator`);
            setEmail('');

            // Notify parent component with the updated array of *IDs*
            if (onCollaboratorChange) {
                onCollaboratorChange([
                    ...collaborators, // Keep existing IDs
                    userData.userId // Add the new user ID string
                ]);
            }
        } catch (error) {
            setError(error.response?.data?.message || 'Failed to add collaborator');
            console.error('Error adding collaborator:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleRemoveCollaborator = async (collaboratorId) => {
        if (!isOwner) return; // Only owner can remove

        try {
            setLoading(true);
            // Remove collaborator via backend service
            await NoteService.removeCollaborator(noteId, collaboratorId);

            // Update UI and notify parent component
            const updatedCollaborators = collaborators.filter(id => id !== collaboratorId); // Filter by ID string

            // Notify parent component with the updated array of *IDs*
            if (onCollaboratorChange) {
                onCollaboratorChange(updatedCollaborators);
            }

            setSuccess('Collaborator removed successfully');
        } catch (error) {
            setError(error.response?.data?.message || 'Failed to remove collaborator');
            console.error('Error removing collaborator:', error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Paper elevation={2} sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
                Collaborators
            </Typography>

            <Divider/>

            <List dense sx={{  maxHeight: 200, overflow: 'auto' }}>
                {/* Owner display with crown icon */}
                {owner && ownerEmail && ( // Render owner if ID and email are available
                    <>
                        <ListItem
                            sx={{
                                borderRadius: 1,
                                border: '1px',
                                borderColor: 'primary.main'
                            }}
                        >
                            <ListItemAvatar>
                                <Avatar sx={{ bgcolor: 'primary.dark' }}>
                                    {ownerEmail.substring(0, 1).toUpperCase()}
                                </Avatar>
                            </ListItemAvatar>
                            <ListItemText
                                primary={
                                    <Box sx={{ display: 'flex', alignItems: 'center' }}>
                                        <CrownIcon sx={{ color: 'gold', mr: 1 }} />
                                        <Typography component="span" fontWeight="bold">
                                            {ownerEmail} (Owner)
                                        </Typography>
                                    </Box>
                                }
                            />
                        </ListItem>

                        {(collaborators && collaborators.length > 0) && (
                            <Divider sx={{ my: 1 }} />
                        )}
                    </>
                )}

                {/* Collaborators list */}
                {collaborators && collaborators.length > 0 ? (
                    // Map over the array of collaborator IDs (strings)
                    collaborators.map(collabId => {
                        // Ensure collabId is a string before using it
                        if (typeof collabId !== 'string') {
                            console.warn("Skipping invalid collaborator ID in render:", collabId);
                            return null; // Skip rendering invalid entries
                        }

                        // Get user details (including email) from the fetchedUserDetails prop
                        const userDetails = fetchedUserDetails?.[collabId];
                        // Prioritize email from fetched details, fallback to UID snippet
                        const displayEmail = userDetails?.email || `User ${collabId.substring(0, 6)}...`;


                        return (
                            <ListItem key={collabId}>
                                <ListItemAvatar>
                                    <Avatar sx={{ bgcolor: 'secondary.main' }}>
                                        {/* Use the first letter of the display email for the avatar */}
                                        {displayEmail.substring(0, 1).toUpperCase()}
                                    </Avatar>
                                </ListItemAvatar>
                                <ListItemText
                                    primary={displayEmail} // Display the email or fallback
                                />
                                {/* Only show remove button if current user is the owner AND the collaborator is not the owner */}
                                {isOwner && collabId !== owner && (
                                    <ListItemSecondaryAction>
                                        <Tooltip title="Remove collaborator">
                                            <IconButton
                                                edge="end"
                                                aria-label="delete"
                                                onClick={() => handleRemoveCollaborator(collabId)}
                                                disabled={loading}
                                                color="error"
                                                size="small"
                                            >
                                                <DeleteIcon />
                                            </IconButton>
                                        </Tooltip>
                                    </ListItemSecondaryAction>
                                )}
                            </ListItem>
                        );
                    })
                ) : (
                    // Show "No collaborators yet" if the list is empty and owner email is loaded
                    // This prevents showing the message briefly before owner email is fetched
                    ownerEmail && (
                        <Typography variant="body2" color="textSecondary">
                            No collaborators yet
                        </Typography>
                    )
                )}
            </List>

            {/* Add Collaborator section - only visible to the owner */}
            {isOwner && (
                <>
                    <Typography variant="h6" gutterBottom sx={{ mt: 2 }}> {/* Added margin top */}
                        Add Collaborator
                    </Typography>

                    <Box component="form" onSubmit={handleAddCollaborator}>
                        <TextField
                            fullWidth
                            size="small"
                            type="email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            placeholder="Enter collaborator's email"
                            disabled={loading}
                            error={!!error}
                            helperText={error || success}
                            InputProps={{
                                endAdornment: (
                                    <InputAdornment position="end">
                                        <IconButton
                                            type="submit"
                                            edge="end"
                                            color="primary"
                                            disabled={!email.trim() || loading}
                                        >
                                            {loading ?
                                                <CircularProgress size={20} /> :
                                                success ?
                                                    <CheckCircleIcon color="success" /> :
                                                    <PersonAddIcon />
                                            }
                                        </IconButton>
                                    </InputAdornment>
                                ),
                            }}
                            FormHelperTextProps={{
                                sx: {
                                    color: error ? 'error.main' : success ? 'success.main' : 'inherit'
                                }
                            }}
                        />
                    </Box>
                </>
            )}
        </Paper>
    );
}

export default CollaboratorsList;
