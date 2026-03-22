import type { Note } from "../types";

interface NoteCardProps {
  note: Note;
  onClick: () => void;
  onDelete: (noteId: string) => void;
}

const NoteCard = ({ note, onClick, onDelete }: NoteCardProps) => {
  const getContentPreview = (value: string) => {
    if (!value) return "No content";

    // Render HTML note bodies as readable snippets in the dashboard card.
    return value
      .replace(/<[^>]*>/g, " ")
      .replace(/\s+/g, " ")
      .trim();
  };

  return (
    <article
      className="card glass-panel shadow-sm hover:shadow-xl hover:-translate-y-1 transition-all duration-300 cursor-pointer border border-base-300/60 overflow-hidden"
      onClick={onClick}
    >
      <div className="h-1 w-full bg-gradient-to-r from-primary via-info to-secondary" />
      <div className="card-body">
        <h3 className="card-title text-base-content/90 text-lg min-w-0">
          <span className="truncate block">{note.title || "Untitled Note"}</span>
        </h3>
        <p className="text-sm text-base-content/70 line-clamp-3 min-h-[3.75rem]">
          {getContentPreview(note.content)}
        </p>
      </div>
      <div className="card-actions px-4 pb-3 flex items-center justify-between text-xs text-base-content/60 font-mono-ui">
        <span>
          {note.updatedAt ? new Date(note.updatedAt).toLocaleDateString() : ""}
        </span>
        {note.collaboratorIds?.length > 0 && (
          <span className="badge badge-outline badge-xs">Shared</span>
        )}
        <button
          className="btn btn-ghost btn-xs text-error hover:bg-error/10"
          onClick={(e) => {
            e.stopPropagation(); // prevent card click
            onDelete(note.id);
          }}
        >
          Delete
        </button>
      </div>
    </article>
  );
};

export default NoteCard;
