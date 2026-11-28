import type { VNode } from "vue";

export type Metadata = Readonly<Record<string, unknown>>;
export type MetadataEditHandler = (key: string, oldValue: unknown, editedValue: unknown) => boolean;

export interface MetadataCardOptions {
  title?: string;
  emptyTitle?: string;
  pathTree?: boolean;
  unsupportedValuePolicy?: "SKIP" | "SHOW_AS_STRING";
  /** Return null to use the unsupported-value policy. */
  complexValueRenderer?: (key: string, value: unknown) => VNode | null;
  editHandler?: MetadataEditHandler;
  inlineStringLimit?: number;
  /** Preferred width and height in CSS pixels. */
  stringPopupSize?: readonly [width: number, height: number];
  /** JSON encodes enum constants as strings, so callers supply their choices when known. */
  enumValues?: Readonly<Record<string, readonly string[]>>;
}
