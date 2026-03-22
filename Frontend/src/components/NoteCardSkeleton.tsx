function NoteCardSkeleton() {
  return (
    <article className="card glass-panel border border-base-300/60 overflow-hidden animate-pulse">
      <div className="h-1 w-full bg-base-300" />
      <div className="card-body gap-3">
        <div className="h-5 w-3/4 bg-base-300 rounded" />
        <div className="space-y-2">
          <div className="h-3 w-full bg-base-300 rounded" />
          <div className="h-3 w-5/6 bg-base-300 rounded" />
          <div className="h-3 w-4/6 bg-base-300 rounded" />
        </div>
      </div>
      <div className="card-actions px-4 pb-3">
        <div className="h-3 w-20 bg-base-300 rounded" />
      </div>
    </article>
  );
}

export default NoteCardSkeleton;
