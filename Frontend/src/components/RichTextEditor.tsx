import { useEffect, useRef } from "react";
import React from "react";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Placeholder from "@tiptap/extension-placeholder";
import { Extension } from "@tiptap/core";
import { sendableSteps } from "prosemirror-collab";
import type { EditorView } from "prosemirror-view";
import type { Plugin } from "prosemirror-state";
import { TOOLBAR_BUTTONS } from "../utils/toolbarConfig";

interface RichTextEditorProps {
  title: string;
  content: string;
  onChange: (field: "title" | "content", value: string) => void;
  /** prosemirror-collab plugin from useOTCollab; omit to disable OT. */
  collabPlugin?: Plugin | null;
  /** Called after every editor transaction so the OT hook can flush steps. */
  onEditorUpdate?: (view: EditorView) => void;
  /** Receives the EditorView after mount so the OT hook can apply remote steps. */
  onEditorViewReady?: (view: EditorView | null) => void;
}

const RichTextEditor = ({
  title,
  content,
  onChange,
  collabPlugin,
  onEditorUpdate,
  onEditorViewReady,
}: RichTextEditorProps) => {
  const isApplyingRemoteUpdateRef = useRef(false);

  // Build a TipTap extension that wraps the prosemirror-collab plugin.
  const collabExtension = collabPlugin
    ? Extension.create({
        name: "prosemirrorCollab",
        addProseMirrorPlugins() {
          return [collabPlugin];
        },
      })
    : null;

  const editor = useEditor({
    extensions: [
      // Disable history when OT is active — prosemirror-collab manages undo.
      StarterKit.configure({ history: collabPlugin ? false : undefined }),
      Placeholder.configure({
        placeholder: "Start typing your note content here...",
      }),
      ...(collabExtension ? [collabExtension] : []),
    ],
    content: content || "",
    onUpdate: ({ editor: e, transaction }) => {
      if (isApplyingRemoteUpdateRef.current) return;
      // When OT is active, receiveTransaction marks the tx with "collab$" metadata.
      // Skip onChange for remote steps — they must not trigger a REST save.
      const isRemoteOTStep = !!collabPlugin && !!transaction.getMeta("collab$");
      if (!isRemoteOTStep) {
        onChange("content", e.getHTML());
      }
      if (onEditorUpdate && e.view) {
        onEditorUpdate(e.view);
      }
    },
    editorProps: {
      attributes: {
        className:
          "prose prose-sm sm:prose-base max-w-none focus:outline-none p-4 sm:p-5 flex-1 outline-none",
      },
    },
  });

  // Expose the EditorView to the OT hook after mount / on unmount.
  useEffect(() => {
    if (!onEditorViewReady) return;
    if (editor?.view) {
      onEditorViewReady(editor.view);
    }
    return () => {
      onEditorViewReady(null);
    };
  }, [editor, onEditorViewReady]);

  // Sync external content changes into the editor, but only when OT is NOT
  // active. With OT, the editor state is driven purely by received steps.
  useEffect(() => {
    if (!editor || collabPlugin) return;

    const incomingContent = content || "";
    const currentContent = editor.getHTML();

    if (incomingContent !== currentContent) {
      isApplyingRemoteUpdateRef.current = true;
      editor.commands.setContent(incomingContent, false);
      isApplyingRemoteUpdateRef.current = false;
    }
  }, [content, editor, collabPlugin]);

  // When OT is active, remote steps flow in via useOTCollab; however if a
  // full-reset is needed (e.g. reconnect bootstrap) the parent can push a new
  // content prop with collabPlugin set to null temporarily.
  useEffect(() => {
    if (!editor || !collabPlugin) return;
    // Verify no pending steps are stuck after a content reset.
    const sendable = sendableSteps(editor.view.state);
    if (!sendable) return; // nothing to do
  }, [editor, collabPlugin]);

  return (
    <section className="flex-1 min-h-0 flex flex-col px-3 sm:px-4 py-3 sm:py-4 gap-3 overflow-hidden">
      <input
        type="text"
        className="input input-lg input-ghost w-full text-xl sm:text-2xl font-bold border-b border-base-300 rounded-none focus:outline-none focus:border-primary transition-colors duration-200"
        placeholder="Note title"
        value={title || ""}
        onChange={(e) => onChange("title", e.target.value)}
      />

      <div className="flex-1 min-h-0 bg-base-100 rounded-lg shadow-sm border border-base-300 flex flex-col overflow-hidden">
        {/* Editor Toolbar */}
        {editor && (
          <div className="bg-base-200 border-b border-base-300 p-2 flex flex-wrap gap-1 items-center z-10 sticky top-0">
            {TOOLBAR_BUTTONS.map((group, groupIdx) => (
              <React.Fragment key={groupIdx}>
                {groupIdx > 0 && (
                  <div className="divider divider-horizontal mx-0 h-4 self-center" />
                )}
                {group.map((btn) => (
                  <button
                    key={btn.title}
                    type="button"
                    onClick={() => {
                      const chain = editor.chain().focus();
                      if (btn.commandArgs) {
                        (chain as unknown as Record<string, (args: Record<string, unknown>) => { run: () => void }>)[btn.command](btn.commandArgs).run();
                      } else {
                        (chain as unknown as Record<string, () => { run: () => void }>)[btn.command]().run();
                      }
                    }}
                    disabled={
                      btn.canCheck
                        ? !(editor.can().chain().focus() as unknown as Record<string, (...args: unknown[]) => boolean>)[btn.command](
                            ...(btn.commandArgs ? [btn.commandArgs] : []),
                          )
                        : undefined
                    }
                    className={`btn btn-sm btn-ghost border border-transparent ${
                      editor.isActive(
                        btn.isActiveCheck,
                        ...(btn.isActiveArgs ? [btn.isActiveArgs] : []),
                      )
                        ? "btn-active border-base-300"
                        : "hover:border-base-300"
                    }`}
                    title={btn.title}
                  >
                    <btn.Icon size={14} />
                  </button>
                ))}
              </React.Fragment>
            ))}
          </div>
        )}

        {/* Editor Content Area */}
        <div className="flex-1 min-h-0 overflow-y-auto">
          <EditorContent editor={editor} className="h-full" />
        </div>
      </div>
    </section>
  );
};

export default RichTextEditor;
