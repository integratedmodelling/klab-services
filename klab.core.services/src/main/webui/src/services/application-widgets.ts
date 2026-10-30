import type {InjectionKey} from "vue";
export const extendedTypes = new Set([
  "iconbutton", "link", "switch", "radio", "multiselect", "textarea", "slider", "range", "date", "time", "color",
  "submit", "reset", "list", "table", "tree", "tabs", "stepper", "pagination", "breadcrumbs",
  "markdown", "image", "icon", "separator", "badge", "spinner", "upload", "download", "audio", "video"
]);
export const applicationFilesKey: InjectionKey<{
  upload(nodeId: string, file: File): Promise<unknown>;
  download(handle: string): Promise<void>;
}> = Symbol("application-files");
export function safeSource(value: unknown): string | undefined {
  if (typeof value !== "string" || value !== value.trim() || !value.trim() || /[\u0000-\u001f\u007f\\]/.test(value) || value.startsWith("//")) return;
  try {
    const url = new URL(value, document.baseURI);
    if (url.username || url.password || !["http:", "https:"].includes(url.protocol)) return;
    if (/^[a-z][a-z0-9+.-]*:/i.test(value) && url.protocol !== "https:") return;
    return value;
  } catch { return; }
}
/** Deliberately small Markdown dialect. Escape all source HTML before adding fixed markup. */
export function markdown(source: string): string {
  const escaped = source.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  return escaped.split("\n").map(line => {
    let text = line.replace(/`([^`]+)`/g, "<code>$1</code>")
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>").replace(/\*([^*]+)\*/g, "<em>$1</em>")
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_all, label: string, url: string) =>
        safeSource(url) ? `<a href="${url}" target="_blank" rel="noopener noreferrer">${label}</a>` : label);
    const heading = /^(#{1,6}) (.*)$/.exec(text);
    if (heading) return `<h${heading[1].length}>${heading[2]}</h${heading[1].length}>`;
    if (text.startsWith("- ")) return `<div>• ${text.slice(2)}</div>`;
    return `<div>${text || "<br>"}</div>`;
  }).join("");
}
