import React from 'react';
import {
    Box,
    Typography,
    List,
    ListItem,
    ListItemText,
    ListItemAvatar,
    Avatar,
    Paper,
    Divider
} from '@mui/material';
import { FiberManualRecord as FiberManualRecordIcon } from '@mui/icons-material';

/**
 * Component to display a list of active users in the note editor.
 * Expects activeUsers to be an array of objects with at least { userId, display, isTyping }.
 */
function ActiveUsersList({ activeUsers = [], currentUser }) {
    const users = Array.isArray(activeUsers) ? activeUsers :
        (activeUsers && typeof activeUsers === 'object') ? Object.values(activeUsers) : [];

    // Filter out any invalid user entries
    const validUsers = users.filter(user => user && user.userId && user.display);


    return (
        <Paper elevation={2} sx={{ p: 2, mb: 2 }}>
            <Typography variant="h6" gutterBottom>
                Active Users ({validUsers.length})
            </Typography>

            <Divider sx={{ mb: 2 }} />

            <List dense sx={{ maxHeight: 200, overflow: 'auto' }}>
                {validUsers.length > 0 ? (
                    validUsers.map(user => (
                        <ListItem
                            key={user.userId}
                            sx={{
                                // current user
                                bgcolor: user.userId === currentUser?.uid ? 'rgba(0, 0, 0, 0.04)' : 'transparent',
                                borderRadius: 1,
                                mb: 0.5
                            }}
                        >
                            <ListItemAvatar>
                                <Avatar sx={{
                                    bgcolor: user.userId === currentUser?.uid ? 'primary.main' : 'secondary.main'
                                }}>
                                    {/* Use the first letter of the display name for the avatar */}
                                    {user.display?.substring(0, 1).toUpperCase() || 'U'}
                                </Avatar>
                            </ListItemAvatar>
                            <ListItemText
                                primary={
                                    <Typography noWrap>
                                        {user.userId === currentUser?.uid
                                            ? `${user.display} (You)`
                                            : user.display}
                                    </Typography>
                                }
                                secondary={
                                    <Box component="span" sx={{ display: 'flex', alignItems: 'center' }}>
                                        <FiberManualRecordIcon
                                            sx={{ fontSize: 12, color: 'success.main', mr: 0.5 }}
                                        />
                                        Online
                                        {/* Show typing indicator if user is typing */}
                                        {user.isTyping && (
                                            <Typography
                                                component="span"
                                                variant="body2"
                                                sx={{ ml: 1, fontStyle: 'italic' }}
                                            >
                                                typing...
                                            </Typography>
                                        )}
                                    </Box>
                                }
                            />
                        </ListItem>
                    ))
                ) : (
                    // if there are no other active users
                    <ListItem>
                        <ListItemText
                            primary="No other active users"
                            secondary="You're the only one here"
                        />
                    </ListItem>
                )}
            </List>
        </Paper>
    );
}

export default ActiveUsersList;
