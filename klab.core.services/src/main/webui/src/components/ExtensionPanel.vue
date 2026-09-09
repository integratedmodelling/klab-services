<script setup lang="ts">
import {computed} from "vue";
import {login} from "../services/auth";
import {resolveExtension} from "../services/extensions";
import type {DashboardContext, DashboardPanel} from "../types";

const props = defineProps<{ panel: DashboardPanel; context: DashboardContext }>();
const implementation = computed(() =>
    resolveExtension(props.panel.component, props.context.config),
);
</script>

<template>
  <q-card flat class="surface-card extension-card">
    <q-card-section class="section-heading">
      <div>
        <div class="eyebrow">Service extension</div>
        <h2>{{ panel.title }}</h2>
        <p>{{ panel.description }}</p>
      </div>
    </q-card-section>

    <q-separator dark/>

    <!-- Keycloak session check still running -->
    <q-card-section
        v-if="
        panel.requiresAuthentication &&
        context.auth.enabled &&
        !context.auth.ready
      "
        class="locked-panel"
    >
      <q-spinner-orbit size="30px"/>

      <div>
        <strong>Checking your session</strong>
        <span>Checking your k.LAB network identity…</span>
      </div>
    </q-card-section>

    <!-- Authentication required -->
    <q-card-section
        v-else-if="
        panel.requiresAuthentication &&
        !context.auth.authenticated
      "
        class="locked-panel"
    >
      <q-icon name="lock" size="30px"/>

      <div>
        <strong>Sign in to use this panel</strong>

        <span v-if="!context.auth.enabled">
          Authentication is not configured for this service.
        </span>

        <span v-else-if="context.auth.error">
          {{ context.auth.error }}
        </span>

        <span v-else>
          Authentication adds your k.LAB network identity
          to service requests.
        </span>

        <q-btn
            v-if="
            context.auth.enabled &&
            context.auth.ready &&
            !context.auth.error
          "
            unelevated
            no-caps
            icon="login"
            label="Sign in"
            @click="login"
        />
      </div>
    </q-card-section>

    <!-- Public panel, or authenticated protected panel -->
    <q-card-section v-else-if="implementation">
      <component
          :is="implementation"
          :context="context"
      />
    </q-card-section>

    <q-card-section v-else class="missing-panel">
      Component
      <code>{{ panel.component }}</code>
      was configured but was not included in this UI build.
    </q-card-section>
  </q-card>
</template>