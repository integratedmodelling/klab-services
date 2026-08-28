<script setup lang="ts">
import { computed } from "vue";
import extensionComponents from "virtual:klab-dashboard-extensions";
import type { DashboardContext, FullPageComponent } from "../types";
import AuthenticationDialog from "./AuthenticationDialog.vue";

const props = defineProps<{ page: FullPageComponent; context: DashboardContext }>();
const implementation = computed(() => extensionComponents[props.page.component]);
const authenticationRequired = computed(
  () => props.page.requiresAuthentication && !props.context.auth.authenticated,
);
</script>

<template>
  <main class="full-page-main">
    <header class="full-page-heading">
      <div>
        <div class="service-type">Service interface</div>
        <h1>{{ page.title }}</h1>
        <p>{{ page.description }}</p>
      </div>
      <q-btn flat no-caps icon="arrow_back" label="Dashboard" href="./" />
    </header>

    <section v-if="page.requiresAuthentication && !context.auth.ready" class="full-page-loading">
      <q-spinner-orbit color="primary" size="48px" />
      <span>Checking your k.LAB network session…</span>
    </section>
    <section v-else-if="authenticationRequired" class="full-page-locked" aria-hidden="true">
      <q-icon name="lock" size="48px" />
    </section>
    <component
      :is="implementation"
      v-else-if="implementation"
      :context="context"
      class="full-page-component"
    />
    <q-banner v-else rounded class="error-banner">
      Component <code>{{ page.component }}</code> was configured but was not included in this UI
      build.
    </q-banner>

    <AuthenticationDialog
      :visible="authenticationRequired && context.auth.ready"
      :auth="context.auth"
      :page="page"
    />
  </main>
</template>
