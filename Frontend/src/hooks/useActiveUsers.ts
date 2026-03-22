import { useState, useCallback, useEffect, useRef } from "react";
import NoteService from "../services/NoteService";
import type {
  ActiveUserEntry,
  ActiveUserDisplay,
  FetchedUserDetails,
  FetchedUserEntry,
  UserResponse,
} from "../types";

interface UseActiveUsersReturn {
  activeUsersState: Map<string, ActiveUserEntry>;
  setActiveUsersState: React.Dispatch<React.SetStateAction<Map<string, ActiveUserEntry>>>;
  fetchedUserDetails: FetchedUserDetails;
  lookupUserById: (userId: string) => Promise<void>;
  activeUsersForList: ActiveUserDisplay[];
}

export function useActiveUsers(): UseActiveUsersReturn {
  // State to store active users as a Map for efficient updates by userId
  const [activeUsersState, setActiveUsersState] = useState<Map<string, ActiveUserEntry>>(new Map());
  // Store fetched user details separately, keyed by ID
  const [fetchedUserDetails, setFetchedUserDetails] = useState<FetchedUserDetails>({});
  const fetchedUserDetailsRef = useRef<FetchedUserDetails>({});

  useEffect(() => {
    fetchedUserDetailsRef.current = fetchedUserDetails;
  }, [fetchedUserDetails]);

  // Use useCallback for the lookup function to prevent unnecessary re-creation
  const lookupUserById = useCallback(async (userId: string): Promise<void> => {
    if (!userId || fetchedUserDetailsRef.current[userId]) {
      return;
    }

    // Mark lookup as in-flight to avoid duplicate requests during rapid events.
    fetchedUserDetailsRef.current[userId] = { pending: true };

    console.log(`Looking up user details for ID: ${userId}`);
    try {
      const userDetails = await NoteService.lookupUserById(userId);
      console.log(`Fetched details for ${userId}:`, userDetails);
      fetchedUserDetailsRef.current[userId] = userDetails;
      setFetchedUserDetails((prevDetails) => ({
        ...prevDetails,
        [userId]: userDetails,
      }));
    } catch (error) {
      console.error(`Error looking up user ${userId}:`, error);
      const fallback: FetchedUserEntry = {
        userId: userId,
        email: "Unknown User",
        displayName: "Unknown User",
      };
      fetchedUserDetailsRef.current[userId] = fallback;
      setFetchedUserDetails((prevDetails) => ({
        ...prevDetails,
        [userId]: fallback,
      }));
    }
  }, []);

  const activeUsersForList: ActiveUserDisplay[] = Array.from(activeUsersState.values())
    .map((user) => {
      const details = fetchedUserDetails[user.userId] as (UserResponse & { pending?: boolean }) | undefined || {
        userId: user.userId,
        pending: true,
        email: "Loading...",
      };
      return {
        ...user,
        ...details,
        display: details.pending ? "Loading..." : (details as UserResponse).email || "Unknown User",
      };
    })
    .filter((user) => user && user.userId);

  return {
    activeUsersState,
    setActiveUsersState,
    fetchedUserDetails,
    lookupUserById,
    activeUsersForList,
  };
}
