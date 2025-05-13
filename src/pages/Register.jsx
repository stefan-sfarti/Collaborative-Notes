// src/pages/Register.js
import React, { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import {
    Box,
    Container,
    Typography,
    TextField,
    Button,
    Paper,
    Grid,
    Link,
    Alert,
    CircularProgress,
    Divider,
    Avatar,
    InputAdornment
} from '@mui/material';
import {
    PersonAdd as PersonAddIcon,
    Google as GoogleIcon,
    Email as EmailIcon,
    LockOutlined as LockOutlinedIcon
} from '@mui/icons-material';
import { styled } from '@mui/material/styles';

const StyledContainer = styled(Container)(() => ({
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    minHeight: '100vh',
    backgroundImage: 'linear-gradient(to bottom right, rgba(245,245,250,0.9), rgba(245,245,250,0.6))',
    backgroundSize: 'cover',
    backgroundPosition: 'center',
}));

const StyledPaper = styled(Paper)(({ theme }) => ({
    padding: theme.spacing(5),
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    width: '100%',
    maxWidth: '420px',
    margin: theme.spacing(3),
    borderRadius: theme.shape.borderRadius * 2,
    boxShadow: '0 8px 32px rgba(0, 0, 0, 0.1)',
    backdropFilter: 'blur(8px)',
    border: '1px solid rgba(255, 255, 255, 0.2)',
    position: 'relative',
    overflow: 'hidden',
    '&::before': {
        content: '""',
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        height: '4px',
        background: 'linear-gradient(90deg, #3f51b5, #5a66c4, #3f51b5)',
        backgroundSize: '200% 100%',
        animation: 'gradientMove 4s ease infinite',
    },
    '@keyframes gradientMove': {
        '0%': {
            backgroundPosition: '0% 50%'
        },
        '50%': {
            backgroundPosition: '100% 50%'
        },
        '100%': {
            backgroundPosition: '0% 50%'
        }
    }
}));

const RegisterIconWrapper = styled(Avatar)(({ theme }) => ({
    margin: theme.spacing(1),
    width: theme.spacing(7),
    height: theme.spacing(7),
    backgroundColor: theme.palette.primary.main,
    boxShadow: '0 4px 12px rgba(63, 81, 181, 0.4)',
}));

const GoogleButton = styled(Button)(({ theme }) => ({
    backgroundColor: '#fff',
    color: '#757575',
    border: `1px solid ${theme.palette.divider}`,
    boxShadow: theme.shadows[1],
    '&:hover': {
        backgroundColor: theme.palette.grey[50],
        boxShadow: theme.shadows[2],
    },
    position: 'relative',
    overflow: 'hidden',
    transition: 'all 0.3s ease',
}));

const StyledDivider = styled(Divider)(({ theme }) => ({
    margin: theme.spacing(3, 0),
    width: '100%',
    '&::before, &::after': {
        borderColor: 'rgba(0, 0, 0, 0.1)',
    },
}));

const LogoText = styled(Typography)(({ theme }) => ({
    fontWeight: 700,
    fontSize: '1.8rem',
    background: 'linear-gradient(45deg, #3f51b5, #5a66c4)',
    WebkitBackgroundClip: 'text',
    WebkitTextFillColor: 'transparent',
    letterSpacing: '0.5px',
}));

function Register() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [googleLoading, setGoogleLoading] = useState(false);
    const { signup, loginWithGoogle } = useAuth();
    const navigate = useNavigate();

    async function handleSubmit(e) {
        e.preventDefault();

        if (password !== confirmPassword) {
            return setError('Passwords do not match');
        }

        try {
            setError('');
            setLoading(true);
            await signup(email, password);
            navigate('/dashboard');
        } catch (error) {
            setError('Failed to create account: ' + error.message);
        }

        setLoading(false);
    }

    async function handleGoogleSignUp() {
        try {
            setError('');
            setGoogleLoading(true);
            await loginWithGoogle();
            navigate('/dashboard');
        } catch (error) {
            setError('Failed to sign up with Google: ' + error.message);
        }

        setGoogleLoading(false);
    }

    return (
        <StyledContainer>
            <StyledPaper elevation={6}>
                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', mb: 3 }}>
                    <RegisterIconWrapper>
                        <PersonAddIcon sx={{ fontSize: 32 }} />
                    </RegisterIconWrapper>
                    <LogoText>
                        CollabNotes
                    </LogoText>
                    <Typography variant="subtitle1" color="text.secondary" sx={{ mt: 1 }}>
                        Sign up to start collaborating
                    </Typography>
                </Box>

                {error && <Alert
                    severity="error"
                    sx={{
                        width: '100%',
                        mb: 3,
                        borderRadius: 2,
                        boxShadow: '0 2px 8px rgba(244, 67, 54, 0.2)'
                    }}
                >
                    {error}
                </Alert>}

                <Box component="form" onSubmit={handleSubmit} sx={{ width: '100%' }}>
                    <TextField
                        margin="normal"
                        required
                        fullWidth
                        id="email"
                        label="Email Address"
                        name="email"
                        autoComplete="email"
                        autoFocus
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        InputProps={{
                            startAdornment: (
                                <InputAdornment position="start">
                                    <EmailIcon color="action" />
                                </InputAdornment>
                            ),
                        }}
                        sx={{
                            '& .MuiOutlinedInput-root': {
                                '&.Mui-focused fieldset': {
                                    borderColor: 'primary.main',
                                    borderWidth: 2
                                },
                            }
                        }}
                    />
                    <TextField
                        margin="normal"
                        required
                        fullWidth
                        name="password"
                        label="Password"
                        type="password"
                        id="password"
                        autoComplete="new-password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        InputProps={{
                            startAdornment: (
                                <InputAdornment position="start">
                                    <LockOutlinedIcon color="action" />
                                </InputAdornment>
                            ),
                        }}
                        sx={{
                            '& .MuiOutlinedInput-root': {
                                '&.Mui-focused fieldset': {
                                    borderColor: 'primary.main',
                                    borderWidth: 2
                                },
                            }
                        }}
                    />
                    <TextField
                        margin="normal"
                        required
                        fullWidth
                        name="confirmPassword"
                        label="Confirm Password"
                        type="password"
                        id="confirmPassword"
                        autoComplete="new-password"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        InputProps={{
                            startAdornment: (
                                <InputAdornment position="start">
                                    <LockOutlinedIcon color="action" />
                                </InputAdornment>
                            ),
                        }}
                        sx={{
                            '& .MuiOutlinedInput-root': {
                                '&.Mui-focused fieldset': {
                                    borderColor: 'primary.main',
                                    borderWidth: 2
                                },
                            }
                        }}
                    />
                    <Button
                        type="submit"
                        fullWidth
                        variant="contained"
                        color="primary"
                        disabled={loading}
                        sx={{
                            mt: 3,
                            mb: 2,
                            py: 1.2,
                            boxShadow: '0 4px 12px rgba(63, 81, 181, 0.4)',
                            '&:hover': {
                                boxShadow: '0 6px 16px rgba(63, 81, 181, 0.6)',
                            }
                        }}
                    >
                        {loading ?
                            <CircularProgress size={24} color="inherit" /> :
                            'Sign Up'
                        }
                    </Button>

                    <StyledDivider>
                        <Typography variant="body2" color="text.secondary" component="span" sx={{ px: 1 }}>
                            or continue with
                        </Typography>
                    </StyledDivider>

                    <GoogleButton
                        fullWidth
                        variant="outlined"
                        startIcon={<GoogleIcon />}
                        onClick={handleGoogleSignUp}
                        disabled={googleLoading}
                        sx={{ mb: 3, py: 1.2 }}
                    >
                        {googleLoading ?
                            <CircularProgress size={24} color="inherit" /> :
                            'Sign up with Google'
                        }
                    </GoogleButton>

                    <Box sx={{ textAlign: 'center' }}>
                        <Typography variant="body2" color="text.secondary">
                            Already have an account?{' '}
                            <Link component={RouterLink} to="/login" variant="body2" fontWeight="bold">
                                Log In
                            </Link>
                        </Typography>
                    </Box>
                </Box>
            </StyledPaper>
        </StyledContainer>
    );
}

export default Register;