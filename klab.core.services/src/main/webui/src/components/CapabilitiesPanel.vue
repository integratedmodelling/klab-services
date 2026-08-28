<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{ capabilities: Record<string, unknown> | null; loading: boolean }>();

const identity = computed(() => [
  ["Service name", props.capabilities?.serviceName],
  ["Service ID", props.capabilities?.serviceId],
  ["Server ID", props.capabilities?.serverId],
  ["Endpoint", props.capabilities?.url],
]);

const components = computed(() =>
  Array.isArray(props.capabilities?.components) ? props.capabilities.components : [],
);
const permissions = computed(() =>
  Array.isArray(props.capabilities?.permissions) ? props.capabilities.permissions : [],
);
const importCount = computed(() => countSchemata(props.capabilities?.importSchemata));
const exportCount = computed(() => countSchemata(props.capabilities?.exportSchemata));

function countSchemata(value: unknown): number {
  if (!value || typeof value !== "object") return 0;
  return Object.values(value).reduce<number>(
    (total, entry) => total + (Array.isArray(entry) ? entry.length : 0),
    0,
  );
}
</script>

<template>
  <q-card flat class="surface-card capability-card">
    <q-card-section class="section-heading">
      <div>
        <div class="eyebrow">Discovery</div>
        <h2>Capabilities</h2>
      </div>
      <q-icon name="hub" size="28px" color="secondary" />
    </q-card-section>
    <q-separator dark />
    <q-card-section v-if="loading"><q-skeleton v-for="i in 5" :key="i" type="text" /></q-card-section>
    <q-card-section v-else-if="capabilities" class="capability-content">
      <dl class="identity-list">
        <template v-for="([label, value], index) in identity" :key="index">
          <dt>{{ label }}</dt><dd>{{ value || "Not advertised" }}</dd>
        </template>
      </dl>
      <div class="capability-counts">
        <div><strong>{{ components.length }}</strong><span>components</span></div>
        <div><strong>{{ importCount }}</strong><span>import formats</span></div>
        <div><strong>{{ exportCount }}</strong><span>export formats</span></div>
      </div>
      <div v-if="permissions.length" class="permission-row">
        <span class="metric-label">Your permissions</span>
        <q-chip v-for="permission in permissions" :key="String(permission)" dense color="teal-9" text-color="teal-1">
          {{ permission }}
        </q-chip>
      </div>
      <q-expansion-item v-if="components.length" dense icon="extension" label="Installed components">
        <q-list separator class="component-list">
          <q-item v-for="(component, index) in components" :key="index">
            <q-item-section>{{ (component as Record<string, unknown>).name || (component as Record<string, unknown>).id || `Component ${index + 1}` }}</q-item-section>
          </q-item>
        </q-list>
      </q-expansion-item>
    </q-card-section>
    <q-card-section v-else class="empty-state">Capabilities are not available yet.</q-card-section>
  </q-card>
</template>
