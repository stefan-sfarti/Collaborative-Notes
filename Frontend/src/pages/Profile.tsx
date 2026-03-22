import { ArrowLeft } from "lucide-react";
import React, { useEffect, useState } from "react";
import toast from "react-hot-toast";
import { useNavigate } from "react-router-dom";
import DashboardNavbar from "../components/DashboardNavbar";
import { useAuth } from "../contexts/AuthContext";
import { UserService } from "../services/UserService";

const Profile = () => {
  const { currentUser, logout, updateCurrentUser } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        setLoading(true);
        const data = await UserService.getCurrentUser();
        // Use user.email, user.userId
        setEmail(data.email || "");
        setDisplayName(data.displayName || "");
      } catch (err: any) {
        console.error(err);
        toast.error("Failed to load profile.");
      } finally {
        setLoading(false);
      }
    };
    fetchProfile();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !displayName) {
      setError("Email and Display Name are required");
      return;
    }

    try {
      setSaving(true);
      setError(null);
      const updatedUserResponse = await UserService.updateProfile(email, displayName); // returns User
      toast.success("Profile updated successfully");
      
      // Keep currentUser up to date in context
      if (updateCurrentUser && currentUser) {
        const userId = (updatedUserResponse as any).userId || currentUser.id;
        updateCurrentUser({
          ...currentUser,
          id: userId,
          email: updatedUserResponse.email,
          displayName: updatedUserResponse.displayName,
        });
      }
    } catch (err: any) {
      const msg = err.response?.data || "Failed to update profile.";
      setError(msg);
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col h-full bg-base-100 text-base-content">
      <DashboardNavbar
        loading={false}
        onRefresh={() => {}}
        currentUser={currentUser}
        onLogout={logout}
      />

      <div className="flex-1 p-4 sm:p-6 lg:p-8 flex justify-center">
        <div className="w-full max-w-lg">
          <button
            onClick={() => navigate("/dashboard")}
            className="btn btn-ghost btn-sm mb-6 pl-0"
          >
            <ArrowLeft size={16} />
            Back to Dashboard
          </button>
          
          <div className="card bg-base-100 shadow-xl border border-base-200">
            <div className="card-body">
              <h2 className="card-title text-2xl font-bold mb-4">Edit Profile</h2>

              {loading ? (
                <div className="flex justify-center p-8">
                  <span className="loading loading-spinner loading-lg"></span>
                </div>
              ) : (
                <form onSubmit={handleSubmit} className="space-y-4">
                  {error && (
                    <div className="alert alert-error">
                      <span>{error}</span>
                    </div>
                  )}

                  <div className="form-control">
                    <label className="label">
                      <span className="label-text">Email Address</span>
                    </label>
                    <input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="input input-bordered w-full"
                      required
                      autoComplete="email"
                    />
                  </div>

                  <div className="form-control">
                    <label className="label">
                      <span className="label-text">Display Name</span>
                    </label>
                    <input
                      type="text"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      className="input input-bordered w-full"
                      required
                    />
                  </div>

                  <div className="card-actions justify-end mt-8">
                    <button
                      type="submit"
                      className="btn btn-primary"
                      disabled={saving}
                    >
                      {saving ? (
                        <>
                          <span className="loading loading-spinner loading-sm"></span>
                          Saving...
                        </>
                      ) : (
                        "Save Changes"
                      )}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Profile;
