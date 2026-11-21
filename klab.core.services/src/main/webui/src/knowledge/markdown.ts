/** Documentation-only Markdown. Raw HTML is always text and links use an explicit protocol allowlist. */
export function documentationMarkdown(source: string): string {
  const escape = (text: string) => text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  function inline(text: string): string {
    const protectedParts: string[] = [];
    const protect = (html: string) => `\u0000${protectedParts.push(html) - 1}\u0000`;
    // Reserve inline code before parsing links/emphasis; only generated HTML can be restored.
    let result = escape(text.replace(/\u0000/g, ""))
      .replace(/`([^`]+)`/g, (_, code: string) => protect(`<code>${code}</code>`))
      .replace(/\[([^\]]+)\]\(([^\s)]+)\)/g, (_, label: string, url: string) => {
        if (!/^(https?:\/\/|mailto:)/i.test(url) || /[\u0000-\u0020]/.test(url)) return label;
        return protect(`<a href="${url}" target="_blank" rel="noopener noreferrer">${label}</a>`);
      })
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>").replace(/__([^_]+)__/g, "<strong>$1</strong>")
      .replace(/\*([^*]+)\*/g, "<em>$1</em>").replace(/_([^_]+)_/g, "<em>$1</em>");
    return result.replace(/\u0000(\d+)\u0000/g, (_, index: string) => protectedParts[Number(index)]);
  }
  const lines = source.replace(/\r\n?/g, "\n").split("\n"), blocks: string[] = [];
  for (let index = 0; index < lines.length;) {
    const line = lines[index];
    if (!line.trim()) { index++; continue; }
    if (/^\s*```/.test(line)) {
      const code: string[] = []; index++;
      while (index < lines.length && !/^\s*```/.test(lines[index])) code.push(lines[index++]);
      index++; blocks.push(`<pre><code>${escape(code.join("\n"))}</code></pre>`); continue;
    }
    const heading = /^#{1,6}\s+(.+)$/.exec(line);
    if (heading) { blocks.push(`<h3>${inline(heading[1])}</h3>`); index++; continue; }
    if (/^\s*>/.test(line)) {
      const quote: string[] = [];
      while (index < lines.length && /^\s*>/.test(lines[index])) quote.push(lines[index++].replace(/^\s*>\s?/, ""));
      blocks.push(`<blockquote>${inline(quote.join(" "))}</blockquote>`); continue;
    }
    const list = /^\s*(?:([-+*])|\d+\.)\s+.+$/.exec(line);
    if (list) {
      const tag = list[1] ? "ul" : "ol", items: string[] = [];
      const pattern = list[1] ? /^\s*[-+*]\s+(.+)$/ : /^\s*\d+\.\s+(.+)$/;
      while (index < lines.length) {
        const item = pattern.exec(lines[index]); if (!item) break;
        items.push(`<li>${inline(item[1])}</li>`); index++;
      }
      blocks.push(`<${tag}>${items.join("")}</${tag}>`); continue;
    }
    const paragraph = [line]; index++;
    while (index < lines.length && lines[index].trim() && !/^\s*(?:#{1,6}\s|>|```|[-+*]\s|\d+\.\s)/.test(lines[index])) paragraph.push(lines[index++]);
    blocks.push(`<p>${inline(paragraph.join(" "))}</p>`);
  }
  return blocks.join("\n");
}
