import { useWebSocket } from "../services/WebSocketProvider";

function OfflineIndicator() {
  const { connectionStatus } = useWebSocket();

  if (connectionStatus === "connected") return null;

  return (
    <div className="alert alert-warning rounded-none sticky top-0 z-50 flex items-center gap-2 py-2">
      <span className="loading loading-spinner loading-xs" />
      <span>
        {connectionStatus === "reconnecting"
          ? "Reconnecting to server..."
          : "Disconnected from server"}
      </span>
    </div>
  );
}

export default OfflineIndicator;
