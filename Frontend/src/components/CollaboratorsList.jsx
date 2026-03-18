// src/components/CollaboratorsList.jsx
import React, { useEffect, useState } from "react";
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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  useEffect(() => {
    async function fetchOwnerEmail() {
      try {
        const response = await NoteService.lookupUserById(owner);
        setOwnerEmail(response.email);
      } catch (error) {
        console.error("Error fetching owner email:", error);
        setOwnerEmail(`Owner-${String(owner || "").substring(0, 6)}...`);
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
    setSuccess("");

    if (!email.trim()) return;

    try {
      setLoading(true);

      // Look up the user by email
      const userData = await NoteService.lookupUserByEmail(email.trim());

      if (!userData || !userData.userId) {
        setError("User not found with this email address");
        return;
      }

      // Check if it's the current user
      if (userData.userId === currentUser.id) {
        setError("You can't add yourself as a collaborator");
        return;
      }

      // Check if user is already a collaborator by checking the array of IDs
      if (collaborators.includes(userData.userId)) {
        setError("This user is already a collaborator");
        return;
      }

      // Add collaborator via the new backend invite endpoint
      await NoteService.inviteCollaborator(noteId, email.trim());

      setSuccess(`${email} added as collaborator`);
      setEmail("");

      if (onCollaboratorChange) {
        onCollaboratorChange([...collaborators, userData.userId]);
      }
    } catch (error) {
      const errorData = error.response?.data;
      const errorMessage =
        typeof errorData === "string"
          ? errorData
          : errorData?.message || "Failed to add collaborator";
      setError(errorMessage);
      console.error("Error adding collaborator:", error);
    } finally {
      setLoading(false);
    }
  };

  const handleRemoveCollaborator = async (collaboratorId) => {
    if (!isOwner) return; // Only owner can remove

    try {
      setLoading(true);
      await NoteService.removeCollaborator(noteId, collaboratorId);

      const updatedCollaborators = collaborators.filter(
        (id) => id !== collaboratorId,
      );

      if (onCollaboratorChange) {
        onCollaboratorChange(updatedCollaborators);
      }

      setSuccess("Collaborator removed successfully");
    } catch (error) {
      setError(
        error.response?.data?.message || "Failed to remove collaborator",
      );
      console.error("Error removing collaborator:", error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card bg-base-100 shadow-sm border border-base-200">
      <div className="card-body">
        <h2 className="card-title text-sm font-semibold mb-2">Collaborators</h2>

        <div className="space-y-2 max-h-52 overflow-auto text-sm">
          {owner && ownerEmail && (
            <>
              <div className="flex items-center gap-2 p-2 rounded-lg border border-primary/40 bg-primary/5">
                <div className="avatar placeholder">
                  <div className="bg-primary text-primary-content rounded-full w-8">
                    <span>
                      {String(ownerEmail || "O")
                        .substring(0, 1)
                        .toUpperCase()}
                    </span>
                  </div>
                </div>
                <div className="flex flex-col">
                  <span className="font-semibold flex items-center gap-1">
                    <span className="text-warning">★</span>
                    {ownerEmail}
                    <span className="badge badge-xs badge-outline ml-1">
                      Owner
                    </span>
                  </span>
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
                  userDetails?.email ||
                  `User ${String(collabId).substring(0, 6)}...`;

                return (
                  <div
                    key={collabId}
                    className="flex items-center justify-between gap-2 p-2 rounded-lg hover:bg-base-200/60"
                  >
                    <div className="flex items-center gap-2">
                      <div className="avatar placeholder">
                        <div className="bg-secondary text-secondary-content rounded-full w-8">
                          <span>
                            {String(displayEmail || "U")
                              .substring(0, 1)
                              .toUpperCase()}
                          </span>
                        </div>
                      </div>
                      <span className="truncate max-w-[12rem] sm:max-w-[14rem]">
                        {displayEmail}
                      </span>
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
              {(error || success) && (
                <span
                  className={`text-xs ${error ? "text-error" : "text-success"}`}
                >
                  {error || success}
                </span>
              )}
            </form>
          </div>
        )}
      </div>
    </div>
  );
}

export default CollaboratorsList;
