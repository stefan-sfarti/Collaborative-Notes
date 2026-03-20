// src/components/CollaboratorsList.jsx
import React, { useEffect, useState } from "react";
import toast from "react-hot-toast";
import NoteService from "../services/NoteService";
import { useAuth } from "../contexts/AuthContext";

/**
 * Component to display and manage note collaborators.
 * Expects 'collaborators' prop to be an array of user ID strings.
 */
function CollaboratorsList({
  noteId,
  collaborators = [],
  onCollaboratorChange,
  isOwner = false,
  owner,
  fetchedUserDetails,
}) {
  const { currentUser } = useAuth();
  const [email, setEmail] = useState("");
  const [ownerEmail, setOwnerEmail] = useState("");
  const [ownerLoading, setOwnerLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    async function fetchOwnerEmail() {
      setOwnerLoading(true);
      try {
        const response = await NoteService.lookupUserById(owner);
        setOwnerEmail(response.email);
      } catch (err) {
        console.error("Error fetching owner email:", err);
        setOwnerEmail(`Owner-${String(owner || "").substring(0, 6)}...`);
      } finally {
        setOwnerLoading(false);
      }
    }

    if (owner) {
      fetchOwnerEmail();
    } else {
      setOwnerEmail("");
    }
  }, [owner]);

  useEffect(() => {
    if (collaborators && collaborators.length > 0) {
      collaborators.forEach((collabId) => {
        if (typeof collabId !== "string") {
          console.warn("Invalid collaborator ID found:", collabId);
        }
      });
    }
  }, [collaborators, fetchedUserDetails]);

  const handleAddCollaborator = async (e) => {
    e.preventDefault();
    setError("");

    if (!email.trim()) return;

    try {
      setLoading(true);

      const userData = await NoteService.lookupUserByEmail(email.trim());

      if (!userData || !userData.userId) {
        setError("User not found with this email address");
        return;
      }

      if (userData.userId === currentUser.id) {
        setError("You can't add yourself as a collaborator");
        return;
      }

      if (collaborators.includes(userData.userId)) {
        setError("This user is already a collaborator");
        return;
      }

      await NoteService.inviteCollaborator(noteId, email.trim());

      toast.success(`${email} added as collaborator`);
      setEmail("");

      if (onCollaboratorChange) {
        onCollaboratorChange([...collaborators, userData.userId]);
      }
    } catch (err) {
      const errorData = err.response?.data;
      const errorMessage =
        typeof errorData === "string"
          ? errorData
          : err.message || "Failed to add collaborator";
      setError(errorMessage);
      console.error("Error adding collaborator:", err);
    } finally {
      setLoading(false);
    }
  };

  const handleRemoveCollaborator = async (collaboratorId) => {
    if (!isOwner) return;

    try {
      setLoading(true);
      await NoteService.removeCollaborator(noteId, collaboratorId);

      const updatedCollaborators = collaborators.filter(
        (id) => id !== collaboratorId,
      );

      if (onCollaboratorChange) {
        onCollaboratorChange(updatedCollaborators);
      }

      toast.success("Collaborator removed successfully");
    } catch (err) {
      setError(err.message || "Failed to remove collaborator");
      console.error("Error removing collaborator:", err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card bg-base-100 shadow-sm border border-base-200">
      <div className="card-body">
        <h2 className="card-title text-sm font-semibold mb-2">Collaborators</h2>

        <div className="space-y-2 max-h-52 overflow-auto text-sm">
          {owner && (
            <>
              <div className="flex items-center gap-2 p-2 rounded-lg border border-primary/40 bg-primary/5">
                <div className="avatar placeholder">
                  <div className="bg-primary text-primary-content rounded-full w-8">
                    {ownerLoading ? (
                      <span className="loading loading-spinner loading-xs" />
                    ) : (
                      <span>
                        {String(ownerEmail || "O")
                          .substring(0, 1)
                          .toUpperCase()}
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex flex-col">
                  {ownerLoading ? (
                    <div className="w-32 h-3 bg-base-300 animate-pulse rounded" />
                  ) : (
                    <span className="font-semibold flex items-center gap-1">
                      <span className="text-warning">★</span>
                      {ownerEmail}
                      <span className="badge badge-xs badge-outline ml-1">
                        Owner
                      </span>
                    </span>
                  )}
                </div>
              </div>

              {collaborators && collaborators.length > 0 && (
                <div className="divider my-2" />
              )}
            </>
          )}

          {collaborators && collaborators.length > 0
            ? collaborators.map((collabId) => {
                if (typeof collabId !== "string") {
                  console.warn(
                    "Skipping invalid collaborator ID in render:",
                    collabId,
                  );
                  return null;
                }

                const userDetails = fetchedUserDetails?.[collabId];
                const displayEmail =
                  userDetails?.pending === true
                    ? "Loading..."
                    : userDetails?.email || "Unknown User";

                return (
                  <div
                    key={collabId}
                    className="flex items-center justify-between gap-2 p-2 rounded-lg hover:bg-base-200/60"
                  >
                    <div className="flex items-center gap-2">
                      <div className="avatar placeholder">
                        <div className="bg-secondary text-secondary-content rounded-full w-8">
                          <span>
                            {displayEmail === "Loading..." ||
                            displayEmail === "Unknown User"
                              ? "?"
                              : String(displayEmail).substring(0, 1).toUpperCase()}
                          </span>
                        </div>
                      </div>
                      {displayEmail === "Loading..." ? (
                        <div className="w-28 h-3 bg-base-300 animate-pulse rounded" />
                      ) : (
                        <span className="truncate flex-1 min-w-0">
                          {displayEmail}
                        </span>
                      )}
                    </div>
                    {isOwner && collabId !== owner && (
                      <button
                        type="button"
                        className="btn btn-ghost btn-xs text-error"
                        onClick={() => handleRemoveCollaborator(collabId)}
                        disabled={loading}
                      >
                        Remove
                      </button>
                    )}
                  </div>
                );
              })
            : ownerEmail && (
                <p className="text-xs text-base-content/60">
                  No collaborators yet
                </p>
              )}
        </div>

        {isOwner && (
          <div className="mt-3 pt-3 border-t border-base-200">
            <p className="text-xs font-semibold mb-2">Add collaborator</p>
            <form
              onSubmit={handleAddCollaborator}
              className="form-control gap-2"
            >
              <div className="join w-full">
                <input
                  type="email"
                  className="input input-bordered input-sm join-item w-full min-w-0"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="Enter email address"
                  disabled={loading}
                />
                <button
                  type="submit"
                  className="btn btn-primary btn-sm join-item"
                  disabled={!email.trim() || loading}
                >
                  {loading ? (
                    <span className="loading loading-spinner loading-xs" />
                  ) : (
                    "Add"
                  )}
                </button>
              </div>
              {error && (
                <span className="text-xs text-error">{error}</span>
              )}
            </form>
          </div>
        )}
      </div>
    </div>
  );
}

export default CollaboratorsList;
