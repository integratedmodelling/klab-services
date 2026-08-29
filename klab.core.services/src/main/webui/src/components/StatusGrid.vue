<script setup lang="ts">
import {computed} from "vue";

const props = defineProps<{
  status: Record<string, unknown> | null;
  loading: boolean;
}>();

const health = computed(() => Number(props.status?.healthPercentage ?? 0));
const load = computed(() => Number(props.status?.loadMilliPercentage ?? 0) / 10);
const memory = computed(() => {
  const used = Number(props.status?.memoryUsedBytes ?? 0);
  const available = Number(props.status?.memoryAvailableBytes ?? 0);
  if (!used && !available) return "Unknown";
  return `${formatBytes(used)} / ${formatBytes(used + available)}`;
});

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** index).toFixed(index > 2 ? 1 : 0)} ${units[index]}`;
}

function formatUptime(value: unknown): string {
  const milliseconds = Number(value ?? 0);
  if (!milliseconds) return "Just started";
  const days = Math.floor(milliseconds / 86_400_000);
  const hours = Math.floor((milliseconds % 86_400_000) / 3_600_000);
  const minutes = Math.floor((milliseconds % 3_600_000) / 60_000);
  return [days && `${days}d`, hours && `${hours}h`, `${minutes}m`].filter(Boolean).join(" ");
}
</script>

<template>
  <section class="status-grid" aria-label="Service status">
    <q-card flat class="metric-card metric-card--primary">
      <q-card-section>
        <div class="metric-label">Operational state</div>
        <div v-if="loading" class="metric-value">
          <q-skeleton type="text" width="65%"/>
        </div>
        <div v-else class="metric-value state-value">
          <span
              class="state-dot"
              :class="status?.operational ? 'state-dot--ok' : status?.available ? 'state-dot--warn' : 'state-dot--down'"
          />
          {{ status?.operational ? "Operational" : status?.available ? "Available" : "Unavailable" }}
        </div>
        <div class="metric-note">{{ status?.busy ? "Processing work" : "Ready for requests" }}</div>
      </q-card-section>
    </q-card>

    <q-card flat class="metric-card">
      <q-card-section>
        <div class="metric-label">Health</div>
        <div class="metric-value">{{ loading ? "—" : `${health}%` }}</div>
        <q-linear-progress rounded size="6px" :value="health / 100" color="secondary" track-color="grey-8"/>
      </q-card-section>
    </q-card>

    <q-card flat class="metric-card">
      <q-card-section>
        <div class="metric-label">Current load</div>
        <div class="metric-value">{{ loading ? "—" : `${load.toFixed(1)}%` }}</div>
        <div class="metric-note">{{ Number(status?.connectedSessionCount ?? 0) }} connected sessions</div>
      </q-card-section>
    </q-card>

    <q-card flat class="metric-card">
      <q-card-section>
        <div class="metric-label">Memory</div>
        <div class="metric-value metric-value--small">{{ loading ? "—" : memory }}</div>
        <div class="metric-note">Uptime {{ formatUptime(status?.uptimeMs) }}</div>
      </q-card-section>
    </q-card>
  </section>
</template>
