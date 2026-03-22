import type { ActiveUserDisplay, User } from "../types";

interface ActiveUsersListProps {
  activeUsers?: ActiveUserDisplay[];
  currentUser: User | null;
}

/**
 * Component to display a list of active users in the note editor.
 * Expects activeUsers to be an array of objects with at least { userId, display, isTyping }.
 */
function ActiveUsersList({ activeUsers = [], currentUser }: ActiveUsersListProps) {
  // Filter out any invalid user entries
  const validUsers = activeUsers.filter(
    (user) => user && user.userId && user.display,
  );

  return (
    <div className="card bg-base-100 shadow-sm border border-base-200">
      <div className="card-body py-3">
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-sm font-semibold">
            Active Users ({validUsers.length})
          </h2>
        </div>

        <div className="max-h-48 overflow-auto text-sm space-y-1">
          {validUsers.length > 0 ? (
            validUsers.map((user) => {
              const isCurrentUser = user.userId === currentUser?.id;
              const hasUnknownDisplay =
                user.display === "Loading..." || user.display === "Unknown User";
              const resolvedDisplay =
                isCurrentUser && hasUnknownDisplay && currentUser?.email
                  ? currentUser.email
                  : user.display;

              return user.display === "Loading..." && !isCurrentUser ? (
                <div
                  key={user.userId}
                  className="flex items-center gap-2 p-2 rounded-lg"
                >
                  <div className="avatar placeholder">
                    <div className="rounded-full w-8 h-8 bg-base-300 animate-pulse" />
                  </div>
                  <div className="w-24 h-3 bg-base-300 animate-pulse rounded" />
                </div>
              ) : (
                <div
                  key={user.userId}
                  className={`flex items-center gap-2 p-2 rounded-lg min-w-0 ${
                    isCurrentUser
                      ? "bg-base-200/80"
                      : "hover:bg-base-200/60"
                  }`}
                >
                  <div className="avatar placeholder">
                    <div
                      className={`rounded-full w-8 ${
                        user.userId === currentUser?.id
                          ? "bg-primary text-primary-content"
                          : "bg-secondary text-secondary-content"
                      }`}
                    >
                      <span>
                        {String(user.display || "U")
                          .substring(0, 1)
                          .toUpperCase()}
                      </span>
                    </div>
                  </div>
                  <div className="flex flex-col flex-1 min-w-0">
                    <span className="truncate">
                      {isCurrentUser
                        ? `${resolvedDisplay} (You)`
                        : resolvedDisplay}
                    </span>
                    <span className="text-xs text-base-content/60 flex items-center gap-1">
                      <span className="w-2 h-2 rounded-full bg-success" />
                      Online
                      {user.isTyping && (
                        <span className="italic ml-1">typing...</span>
                      )}
                    </span>
                  </div>
                </div>
              );
            })
          ) : (
            <div className="p-2 text-xs text-base-content/60">
              <p>No other active users</p>
              <p>You&apos;re the only one here.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default ActiveUsersList;
