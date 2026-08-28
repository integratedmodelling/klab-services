<script setup lang="ts">
import type { AuthState } from "../services/auth";
import { login } from "../services/auth";
import type { FullPageComponent } from "../types";

defineProps<{ visible: boolean; auth: AuthState; page: FullPageComponent }>();
</script>

<template>
  <q-dialog
    :model-value="visible"
    persistent
    transition-show="scale"
    transition-hide="scale"
    aria-labelledby="authentication-title"
  >
    <q-card class="auth-dialog">
      <q-card-section class="auth-dialog__heading">
        <q-avatar color="primary" text-color="white" icon="lock" />
        <div>
          <div class="eyebrow">k.LAB network</div>
          <h2 id="authentication-title">Sign in to open {{ page.title }}</h2>
        </div>
      </q-card-section>
      <q-card-section class="auth-dialog__body">
        <p>{{ page.description || "This service interface requires an authenticated identity." }}</p>
        <q-banner v-if="auth.error" rounded class="error-banner">
          {{ auth.error }}
        </q-banner>
        <q-banner v-else-if="!auth.enabled" rounded class="configuration-banner">
          Authentication has not been configured for this service instance.
        </q-banner>
        <p v-else class="auth-dialog__note">
          Authentication is handled by the configured k.LAB identity provider. After sign-in you
          will return directly to this page.
        </p>
      </q-card-section>
      <q-card-actions align="right" class="auth-dialog__actions">
        <q-btn flat no-caps label="Back to dashboard" href="./" />
        <q-btn
          unelevated
          no-caps
          color="primary"
          icon-right="login"
          label="Sign in"
          :disable="!auth.enabled || !!auth.error"
          @click="login"
        />
      </q-card-actions>
    </q-card>
  </q-dialog>
</template>
