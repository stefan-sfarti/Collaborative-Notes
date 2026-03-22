import { LogOut, Menu, Moon, RefreshCw, Sun, User as UserIcon } from "lucide-react";
import { Link } from "react-router-dom";
import { useTheme } from "../hooks/useTheme";
import type { User } from "../types";

interface DashboardNavbarProps {
  loading: boolean;
  onRefresh: () => void;
  currentUser: User | null;
  onLogout: () => void;
}

const DashboardNavbar = ({ loading, onRefresh, currentUser, onLogout }: DashboardNavbarProps) => {
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="navbar glass-panel shadow-sm border-b border-base-300/70 px-3 sm:px-4 gap-2 flex-wrap sticky top-0 z-20">
      <div className="flex-1 min-w-0">
        <Link to="/dashboard" className="text-lg sm:text-2xl font-bold bg-gradient-to-r from-primary via-info to-secondary bg-clip-text text-transparent truncate block hover:opacity-80 transition-opacity">
          CollabNotes
        </Link>
      </div>
      <div className="flex-none flex items-center gap-1 sm:gap-3">
        <button
          className="btn btn-ghost btn-circle btn-sm sm:btn-md hover:rotate-12 transition-transform"
          onClick={toggleTheme}
          title="Toggle Theme"
        >
          {theme === "dark" ? <Sun size={20} /> : <Moon size={20} />}
        </button>

        <button
          className="btn btn-ghost btn-circle btn-sm sm:btn-md sm:w-auto sm:px-3 group"
          onClick={onRefresh}
          disabled={loading}
          title="Refresh Notes"
        >
          {loading ? (
            <span className="loading loading-spinner loading-sm" />
          ) : (
            <RefreshCw
              size={20}
              className="transition-transform group-hover:rotate-90"
            />
          )}
          <span className="hidden sm:inline ml-2">Refresh</span>
        </button>

        <div className="dropdown dropdown-end ml-1">
          <label tabIndex={0} className="btn btn-ghost btn-circle avatar placeholder hover:bg-base-200">
            <div className="bg-primary text-primary-content rounded-full w-9 sm:w-10">
              {currentUser?.email ? (
                <span className="text-sm sm:text-base font-medium">{currentUser.email.substring(0, 1).toUpperCase()}</span>
              ) : (
                <Menu size={20} />
              )}
            </div>
          </label>
          <ul tabIndex={0} className="mt-3 z-[1] p-2 shadow-lg menu menu-sm dropdown-content bg-base-100 rounded-box w-56 border border-base-200">
            {currentUser?.email && (
              <li className="menu-title px-4 py-2 border-b border-base-200 mb-2">
                <span className="text-sm font-semibold text-base-content truncate block w-full">{currentUser.displayName || currentUser.email}</span>
                {currentUser.displayName && (
                  <span className="text-xs text-base-content/60 truncate block w-full font-normal">{currentUser.email}</span>
                )}
              </li>
            )}
            <li>
              <Link to="/profile" className="py-3">
                <UserIcon size={16} />
                Profile Settings
              </Link>
            </li>
            <li>
              <button onClick={onLogout} className="text-error hover:bg-error/10 py-3 mt-1">
                <LogOut size={16} />
                Logout
              </button>
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default DashboardNavbar;
