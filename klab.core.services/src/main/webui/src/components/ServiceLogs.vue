<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { serviceApi } from "../services/api";
import { postJson } from "../services/administration";

interface LogRecord {
  eventId: string;
  timestamp: string;
  level: string;
  logger: string;
  message: string;
  stackTrace?: string;
  diagnostics?: Record<string, string>;
  partial: boolean;
  truncated: boolean;
}
interface LogPage {
  records: LogRecord[];
  nextCursor: string;
  caughtUp: boolean;
  sourceZone: string;
  omittedEvents: number;
}
interface LogCapabilities {
  streamIds: string[];
  defaultLimit: number;
  sourceZone: string;
  redactionPolicy: string;
}

const props = defineProps<{ visible: boolean }>();
const controller = new AbortController();
const recordLimit = 1000;
const records = ref<LogRecord[]>([]);
const logView = ref<HTMLElement>();
const capabilities = ref<LogCapabilities>();
const busy = ref(false);
const error = ref("");
const progress = ref("");
const expired = ref(false);
const live = ref(false);
const caughtUp = ref(false);
const omitted = ref(0);
const trimmed = ref(false);
const cursor = ref<string>();
let timer: number | undefined;
let nextQueryAt = 0;

function stopFollowing() {
  live.value = false;
  window.clearTimeout(timer);
}

function scheduleFollow() {
  window.clearTimeout(timer);
  if (live.value) timer = window.setTimeout(() => void readPage(), caughtUp.value ? 2000 : 500);
}

async function readPage() {
  if (busy.value || controller.signal.aborted) return;
  const view = logView.value;
  const pinToEnd = live.value && (!view || view.scrollHeight - view.scrollTop - view.clientHeight < 48);
  busy.value = true;
  error.value = "";
  progress.value = "Reading service log…";
  try {
    capabilities.value ??= await serviceApi.request<LogCapabilities>("admin/logs/capabilities", {
      signal: controller.signal, cache: "no-store",
    });
    if (!capabilities.value.streamIds.includes("service")
        || capabilities.value.redactionPolicy !== "vetted-diagnostics-v1") {
      stopFollowing();
      capabilities.value = undefined;
      progress.value = "";
      error.value = "This service does not expose a supported diagnostic log stream.";
      return;
    }
    const query = {
      streamId: "service",
      cursor: cursor.value ?? null,
      limit: capabilities.value.defaultLimit,
      follow: !!cursor.value && caughtUp.value,
      includeMetadata: true,
    };
    // A refresh or restart can follow immediately after the previous cursor response.
    const now = Date.now();
    if (nextQueryAt > now) await new Promise(resolve => window.setTimeout(resolve, nextQueryAt - now));
    controller.signal.throwIfAborted();
    const page = await postJson<LogPage>("admin/logs/query", query, controller.signal);
    if (controller.signal.aborted) return;
    const entries = new Map(records.value.map(record => [record.eventId, record]));
    let added = 0;
    let updated = 0;
    for (const record of page.records) {
      const previous = entries.get(record.eventId);
      if (!previous) added++;
      else if (previous.partial !== record.partial || previous.stackTrace !== record.stackTrace
          || previous.message !== record.message || previous.truncated !== record.truncated) updated++;
      entries.set(record.eventId, record);
    }
    trimmed.value ||= entries.size > recordLimit;
    records.value = [...entries.values()].slice(-recordLimit);
    omitted.value += page.omittedEvents;
    cursor.value = page.nextCursor;
    caughtUp.value = page.caughtUp;
    expired.value = false;
    const activity = added ? `${added} ${added === 1 ? "entry" : "entries"} loaded`
      : updated ? `${updated} ${updated === 1 ? "entry" : "entries"} updated`
      : "No new entries";
    progress.value = `${activity} · ${new Date().toLocaleTimeString()}${page.caughtUp ? " · End of active log" : " · More entries may be available"}`;
    if (pinToEnd) {
      await nextTick();
      if (logView.value) logView.value.scrollTop = logView.value.scrollHeight;
    }
  } catch (reason) {
    if (controller.signal.aborted) return;
    stopFollowing();
    progress.value = "";
    const status = reason instanceof Error ? reason.message.split(" ")[0] : "";
    expired.value = status === "410";
    error.value = expired.value
      ? "The log changed or this reading session expired. Start a new query to continue."
      : status === "429" ? "The service is limiting log reads. Try again in a moment."
      : status === "503" ? "The service's configured log file is unavailable."
      : status === "403" ? "The current session could not read this log. Start a new query or check administrator access."
      : "Could not read service logs. Try again.";
  } finally {
    nextQueryAt = Date.now() + 250;
    busy.value = false;
    scheduleFollow();
  }
}

function restart() {
  stopFollowing();
  capabilities.value = undefined;
  cursor.value = undefined;
  caughtUp.value = false;
  omitted.value = 0;
  trimmed.value = false;
  expired.value = false;
  records.value = [];
  void readPage();
}

function refresh() {
  if (expired.value) restart();
  else void readPage();
}

function toggleFollow() {
  if (live.value) stopFollowing();
  else {
    live.value = true;
    if (logView.value) logView.value.scrollTop = logView.value.scrollHeight;
    void readPage();
  }
}

onMounted(readPage);
watch(() => props.visible, visible => { if (!visible) stopFollowing(); });
onBeforeUnmount(() => { stopFollowing(); controller.abort(); });
</script>

<template>
  <section aria-labelledby="service-logs-title">
    <header class="logs-heading">
      <div>
        <h2 id="service-logs-title">Service log</h2>
        <p v-if="capabilities">Source time zone {{ capabilities.sourceZone }} · Unvetted entries show timestamp and level</p>
      </div>
      <div class="log-actions">
        <q-btn outline color="primary" :label="cursor && !caughtUp && !expired ? 'Load more entries' : 'Refresh log'"
          :loading="busy && !live" :disable="live" @click="refresh" />
        <q-btn flat :label="live ? 'Pause live updates' : 'Follow log'" :disable="!live && (busy || !!error)"
          @click="toggleFollow" />
        <q-btn flat label="Start new query" :disable="busy" @click="restart" />
      </div>
    </header>
    <q-banner v-if="error" class="error-banner" role="alert">
      {{ error }}
    </q-banner>
    <p v-if="progress" class="log-note" :role="live ? undefined : 'status'">{{ progress }}</p>
    <p v-if="!busy && !error && !records.length" class="empty-state">
      {{ caughtUp ? 'No entries found in the active service log.' : 'This part of the log has no entries to display. Load more to continue.' }}
    </p>
    <p v-if="omitted || trimmed" class="log-note">
      <span v-if="omitted">The service omitted {{ omitted }} log entries. </span>
      <span v-if="trimmed">Showing the last {{ recordLimit }} entries loaded.</span>
    </p>
    <div v-if="records.length" ref="logView" class="log-view" tabindex="0" role="region" aria-label="Log entries">
      <ol>
        <li v-for="record in records" :key="record.eventId" class="log-record">
          <div class="log-meta">
            <span>{{ record.timestamp }}</span>
            <strong :class="{ 'log-error': record.level === 'ERROR', 'log-warning': record.level === 'WARN' }">{{ record.level }}</strong>
            <span>{{ record.logger }}</span>
            <span v-if="record.partial">Partial entry</span>
            <span v-if="record.truncated">Truncated</span>
          </div>
          <pre>{{ record.message }}</pre>
          <details v-if="record.stackTrace || Object.keys(record.diagnostics ?? {}).length">
            <summary>Entry details</summary>
            <pre v-if="record.stackTrace">{{ record.stackTrace }}</pre>
            <dl v-if="Object.keys(record.diagnostics ?? {}).length">
              <template v-for="(value, key) in record.diagnostics" :key="key"><dt>{{ key }}</dt><dd>{{ value }}</dd></template>
            </dl>
          </details>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.logs-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: start; gap: 12px; margin-bottom: 16px; }
h2 { font: 600 21px/1.35 var(--klab-font); letter-spacing: -.025em; margin: 0; }
.logs-heading p { margin: 4px 0 0; }
.logs-heading p, .log-note, .log-meta { color: var(--klab-muted); }
.log-actions, .log-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 12px; }
.log-view { max-height: 65vh; overflow: auto; background: var(--klab-surface); border: 1px solid var(--klab-border); border-radius: var(--klab-radius); scrollbar-color: var(--klab-field-border) var(--klab-surface); }
ol { list-style: none; margin: 0; padding: 0; }
.log-record { padding: 12px 16px; overflow-wrap: anywhere; }
.log-record + .log-record { border-top: 1px solid var(--klab-border); }
.log-meta, pre, dl { font: 13px/1.55 var(--klab-mono); }
.log-error { color: var(--klab-danger); }
.log-warning { color: var(--klab-warning); }
pre { margin: 6px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; }
summary { padding-block: 8px; cursor: pointer; color: var(--klab-action); }
dt { font-weight: 600; }
dd { margin: 0 0 6px; }
</style>
