<script setup lang="ts">
import type { AuthState } from "../services/auth";
import { login, logout } from "../services/auth";

defineProps<{ auth: AuthState }>();
</script>

<template>
  <div class="login-control" aria-live="polite">
    <q-skeleton v-if="auth.enabled && !auth.ready" type="QBtn" width="110px" />
    <q-btn
      v-else-if="auth.enabled && !auth.authenticated"
      outline
      no-caps
      rounded
      color="white"
      icon="login"
      label="Sign in"
      @click="login"
    />
    <q-btn-dropdown
      v-else-if="auth.authenticated"
      flat
      no-caps
      rounded
      color="white"
      icon="account_circle"
      :label="auth.username || 'Account'"
    >
      <q-list class="account-menu">
        <q-item clickable v-close-popup @click="logout">
          <q-item-section avatar><q-icon name="logout" /></q-item-section>
          <q-item-section>Sign out</q-item-section>
        </q-item>
      </q-list>
    </q-btn-dropdown>
    <q-chip v-else dense outline color="grey-4" text-color="grey-4" icon="public">
      Public access
    </q-chip>
  </div>
</template>
