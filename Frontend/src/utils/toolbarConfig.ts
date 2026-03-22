import {
  Bold,
  Italic,
  Strikethrough,
  Heading1,
  Heading2,
  List,
  ListOrdered,
  Quote,
} from "lucide-react";
import type { ToolbarGroup } from "../types";

// Each group is an array of button descriptors.
// canCheck: true means the button can be disabled via editor.can()
export const TOOLBAR_BUTTONS: ToolbarGroup[] = [
  [
    { command: "toggleBold", commandArgs: null, isActiveCheck: "bold", isActiveArgs: null, Icon: Bold, title: "Bold", canCheck: true },
    { command: "toggleItalic", commandArgs: null, isActiveCheck: "italic", isActiveArgs: null, Icon: Italic, title: "Italic", canCheck: true },
    { command: "toggleStrike", commandArgs: null, isActiveCheck: "strike", isActiveArgs: null, Icon: Strikethrough, title: "Strikethrough", canCheck: true },
  ],
  [
    { command: "toggleHeading", commandArgs: { level: 1 }, isActiveCheck: "heading", isActiveArgs: { level: 1 }, Icon: Heading1, title: "Heading 1", canCheck: false },
    { command: "toggleHeading", commandArgs: { level: 2 }, isActiveCheck: "heading", isActiveArgs: { level: 2 }, Icon: Heading2, title: "Heading 2", canCheck: false },
  ],
  [
    { command: "toggleBulletList", commandArgs: null, isActiveCheck: "bulletList", isActiveArgs: null, Icon: List, title: "Bullet List", canCheck: false },
    { command: "toggleOrderedList", commandArgs: null, isActiveCheck: "orderedList", isActiveArgs: null, Icon: ListOrdered, title: "Numbered List", canCheck: false },
  ],
  [
    { command: "toggleBlockquote", commandArgs: null, isActiveCheck: "blockquote", isActiveArgs: null, Icon: Quote, title: "Quote", canCheck: false },
  ],
];
