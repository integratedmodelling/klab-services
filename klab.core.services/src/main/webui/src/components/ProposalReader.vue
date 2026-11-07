<script setup lang="ts">
import { computed } from "vue";
import { parseDocument } from "yaml";
import ProposalValue from "./ProposalValue.vue";
const props = defineProps<{ source: string }>();
const fallbackLimited = computed(() => props.source.length > 20000);
const fallback = computed(() => props.source.slice(0, 20000));
const document = computed(() => {
  try {
    const parsed = parseDocument(props.source, { schema: "json", strict: true });
    if (parsed.errors.length) return undefined;
    const value: unknown = parsed.toJS({ maxAliasCount: 20 });
    if (!value || typeof value !== "object") return undefined;
    let remaining = 800, limited = false;
    function bound(item: unknown, depth = 0): unknown {
      if (--remaining < 0 || depth > 8) { limited = true; return 'Further detail is available in the exact source.'; }
      if (typeof item === 'string' && item.length > 20000) { limited = true; return item.slice(0, 20000) + '\n[Continued in the exact source]'; }
      if (Array.isArray(item)) {
        const result: unknown[] = [];
        for (const child of item) { if (remaining <= 0 || result.length >= 100) { limited = true; break; } result.push(bound(child, depth + 1)); }
        return result;
      }
      if (item && typeof item === 'object') {
        const result: [string, unknown][] = [];
        for (const [key, child] of Object.entries(item)) { if (remaining <= 0 || result.length >= 100) { limited = true; break; } result.push([key, bound(child, depth + 1)]); }
        return Object.fromEntries(result);
      }
      return item;
    }
    const reading = bound(value);
    return { value: reading, limited };
  } catch { return undefined; }
});
const reading = computed(() => {
  const root = document.value?.value;
  if (!root || typeof root !== 'object' || Array.isArray(root)) return { content: root, metadata: {}, title: '' };
  const envelope = root as Record<string, unknown>;
  const isEnvelope = envelope.proposal && typeof envelope.proposal === 'object' && !Array.isArray(envelope.proposal)
    && Object.keys(envelope).every(key => ['proposal', 'proposal_schema', 'context_pack_version'].includes(key));
  const proposal = (isEnvelope ? envelope.proposal : envelope) as Record<string, unknown>;
  const metadataKeys = new Set(['id', 'revision_id', 'status', 'iteration', 'supersedes_revision', 'generated_at']);
  const metadata = Object.fromEntries(Object.entries(proposal).filter(([key]) => metadataKeys.has(key)));
  if (isEnvelope) for (const key of ['proposal_schema', 'context_pack_version']) if (key in envelope) metadata[key] = envelope[key];
  const content = Object.fromEntries(Object.entries(proposal).filter(([key]) => !metadataKeys.has(key) && key !== 'title'));
  // Unknown title shapes remain visible rather than being silently omitted.
  if (proposal.title != null && typeof proposal.title !== 'string') content.title = proposal.title;
  return { content, metadata, title: typeof proposal.title === 'string' ? proposal.title : '' };
});
</script>
<template>
  <article class="proposal-reader" aria-label="Readable candidate">
    <template v-if="document">
      <p v-if="document.limited" class="reader-note" role="status">This large document is shortened in the reading view. The exact source and download contain the complete document.</p>
      <h3 v-if="reading.title" class="proposal-title">{{ reading.title }}</h3>
      <ProposalValue :value="reading.content" />
      <details v-if="Object.keys(reading.metadata).length" class="document-metadata" :open="!reading.content || Object.keys(reading.content).length === 0">
        <summary>Document metadata (as supplied)</summary>
        <ProposalValue :value="reading.metadata" />
      </details>
    </template>
    <template v-else>
      <p class="reader-note">A structured reading view is unavailable for this document. Its supplied text is shown below; the exact source remains available.</p>
      <p v-if="fallbackLimited" class="reader-note" role="status">This large document is shortened in the reading view. The exact source and download contain the complete document.</p>
      <pre class="reader-fallback">{{ fallback }}</pre>
    </template>
  </article>
</template>
<style scoped>
.proposal-reader { font-size: 1rem; line-height: 1.65; max-width: 72ch; min-width: 0; overflow-wrap: anywhere; }
.reader-note { font-size: .875rem; color: #53667b; text-wrap: pretty; }
.reader-fallback { white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; }
.proposal-title { font-size: 1.5rem; line-height: 1.35; margin: 0 0 24px; font-weight: 600; overflow-wrap: anywhere; text-wrap: balance; }
.document-metadata { margin-block-start: 32px; border-block-start: 1px solid #cad5e2; padding-block-start: 16px; }
.document-metadata summary { cursor: pointer; font-size: .875rem; color: #53667b; margin-block-end: 16px; min-height: 44px; display: list-item; list-style: disclosure-closed inside; }
.document-metadata[open] > summary { list-style-type: disclosure-open; }
.document-metadata summary::marker { color: #53667b; }
.document-metadata summary:focus-visible { outline: 3px solid #2459a8; outline-offset: 3px; }
</style>
