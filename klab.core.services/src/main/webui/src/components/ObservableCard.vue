<script setup lang="ts">
import "../styles/theme.css";
import "../knowledge/knowledge.css";
import { computed } from "vue";
import { conceptLabel, conceptDocumentation, conceptMetadata, semanticColor, type Concept } from "../knowledge/model";
import { documentationMarkdown } from "../knowledge/markdown";
const props = withDefaults(defineProps<{
  concept: Concept;
  relationships?: { label: string; concept: Concept; incoming?: boolean }[];
}>(), { relationships: () => [] });
const emit = defineEmits<{ select: [string] }>();
const documentation = computed(() => documentationMarkdown(conceptDocumentation(props.concept)));
const qualifiers = computed(() => {
  const concept = props.concept;
  const type = ["CONFIGURATION", "QUALITY", "SUBJECT", "AGENT", "RELATIONSHIP", "EVENT", "PROCESS", "EXTENT", "IDENTITY", "ATTRIBUTE", "REALM", "ROLE", "DOMAIN"].find(t => concept.type?.includes(t));
  return [(type || 'observable').toLowerCase(),
    concept.collective || concept.type?.includes('COLLECTIVE') ? 'collective' : '',
    concept.abstract || concept.type?.includes('ABSTRACT') ? 'abstract' : ''].filter(Boolean).join(' / ');
});
const metadata = computed(() => Object.entries(conceptMetadata(props.concept)).filter(([key, value]) =>
  !['@CLASS', 'delegate', 'dc:comment', 'rdfs:comment', 'dc:description', 'klab:displayLabel', 'dc:label', 'rdfs:label'].includes(key) &&
  ['string', 'number', 'boolean'].includes(typeof value) && String(value).trim()));
</script>
<template>
  <article class="observable-card knowledge-ui" aria-label="Observable details">
    <header><h2 :style="{ color: `var(--semantic-${semanticColor(concept.type)})` }">{{ conceptLabel(concept) }}</h2><span>{{ qualifiers }}</span><code>{{ concept.urn }}</code></header>
    <div v-if="documentation" class="concept-documentation" v-html="documentation" />
    <p v-for="(notification, index) in concept.notifications || []" :key="index" class="observable-notification">{{ notification.message }}</p>
    <dl v-if="metadata.length" class="observable-metadata"><template v-for="[key, value] in metadata" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template></dl>
    <section v-if="relationships.length" class="observable-relationships" aria-label="Loaded relationships">
      <h3>Relationships</h3>
      <dl><template v-for="(relationship, index) in relationships" :key="index">
        <dt>{{ relationship.label }}<span v-if="relationship.incoming"> · incoming</span></dt>
        <dd><button type="button" :title="relationship.concept.urn" @click="emit('select', relationship.concept.urn)"
          :style="{ color: `var(--semantic-${semanticColor(relationship.concept.type)})` }">{{ conceptLabel(relationship.concept) }}</button></dd>
      </template></dl>
    </section>
  </article>
</template>
<style>
.observable-card { padding: 14px; background: var(--klab-surface); overflow-wrap: anywhere; }
.observable-card header { display: grid; gap: 6px; }
.observable-card h2 { margin: 0; font: 600 16px/1.35 var(--klab-font); }
.observable-card header > span { color: var(--klab-muted); }
.observable-card code { font-family: var(--klab-mono); font-size: 12px; white-space: pre-wrap; }
.concept-documentation { line-height: 1.55; max-width: 75ch; }
.concept-documentation :is(p,ul,ol,blockquote,pre) { margin: 10px 0; }
.concept-documentation :is(h3, h4) { font: 600 14px/1.4 var(--klab-font); margin: 14px 0 6px; }
.concept-documentation :is(ul,ol) { padding-left: 22px; }
.concept-documentation a { color: var(--klab-action); text-decoration: underline; }
.concept-documentation pre { white-space: pre-wrap; padding: 8px; background: var(--klab-surface-alt); }
.concept-documentation blockquote { margin-left: 0; padding-left: 12px; border-left: 1px solid var(--klab-field-border); }
.observable-card dl { display: grid; grid-template-columns: minmax(110px, auto) minmax(0, 1fr); gap: 6px 14px; margin: 12px 0 0; }
.observable-card dt { font-weight: 600; }
.observable-card dd { margin: 0; }
.observable-card dt span { font-weight: 400; color: var(--klab-muted); }
.observable-relationships h3 { font: 600 14px var(--klab-font); margin: 16px 0 0; }
.observable-relationships button { border: 0; padding: 0; min-height: 24px; text-align: left; background: transparent; }
@media (max-width: 520px) { .observable-card dl { grid-template-columns: 1fr; gap: 4px; } .observable-card dd { margin-bottom: 6px; } }
</style>
