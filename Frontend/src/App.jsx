// src/App.js
import React from 'react';
import { BrowserRouter as Router, Route, Routes, Navigate } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import NoteEditor from './pages/NoteEditor';
import ProtectedRoute from './components/ProtectedRoute';
import {WebSocketProvider} from "./services/WebSocketProvider.jsx";


function App() {
    return (
        <AuthProvider>
            <WebSocketProvider>
                <Router>
                    <div className="app-container">
                        <Routes>
                            <Route path="/login" element={<Login />} />
                            <Route path="/register" element={<Register />} />
                            <Route
                                path="/dashboard"
                                element={
                                    <ProtectedRoute>
                                        <Dashboard />
                                    </ProtectedRoute>
                                }
                            />
                            <Route
                                path="/notes/:noteId"
                                element={
                                    <ProtectedRoute>
                                        <NoteEditor />
                                    </ProtectedRoute>
                                }
                            />
                            <Route path="*" element={<Navigate to="/dashboard" replace />} />
                        </Routes>
                    </div>
                </Router>
            </WebSocketProvider>
        </AuthProvider>
    );
}

export default App;