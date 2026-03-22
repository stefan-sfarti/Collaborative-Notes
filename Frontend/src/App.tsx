import { Toaster } from "react-hot-toast";
import {
    Navigate,
    Route,
    BrowserRouter as Router,
    Routes,
} from "react-router-dom";
import ErrorBoundary from "./components/ErrorBoundary";
import OfflineIndicator from "./components/OfflineIndicator";
import ProtectedRoute from "./components/ProtectedRoute";
import { AuthProvider } from "./contexts/AuthContext";
import Dashboard from "./pages/Dashboard";
import Login from "./pages/Login";
import NoteEditor from "./pages/NoteEditor";
import Profile from "./pages/Profile";
import Register from "./pages/Register";
import { WebSocketProvider } from "./services/WebSocketProvider";

function App() {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <WebSocketProvider>
          <Router>
            <div className="min-h-[100dvh] w-full">
              <Toaster position="top-right" />
              <OfflineIndicator />
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
                  path="/profile"
                  element={
                    <ProtectedRoute>
                      <Profile />
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
    </ErrorBoundary>
  );
}

export default App;
