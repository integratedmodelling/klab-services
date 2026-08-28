import Keycloak from "keycloak-js";
import { reactive, readonly } from "vue";
import type { AuthenticationSettings } from "../types";

export interface AuthState {
  enabled: boolean;
  ready: boolean;
  authenticated: boolean;
  username: string;
  error: string;
}

const mutableState = reactive<AuthState>({
  enabled: false,
  ready: false,
  authenticated: false,
  username: "",
  error: "",
});

let keycloak: Keycloak | null = null;

export const authState = readonly(mutableState) as AuthState;

export async function initializeAuthentication(settings: AuthenticationSettings): Promise<void> {
  mutableState.enabled = settings.enabled;
  if (!settings.enabled || !settings.url) {
    mutableState.ready = true;
    return;
  }

  keycloak = new Keycloak({
    url: settings.url,
    realm: settings.realm,
    clientId: settings.clientId,
  });

  try {
    mutableState.authenticated = await keycloak.init({
      onLoad: "check-sso",
      checkLoginIframe: false,
      pkceMethod: "S256",
    });
    mutableState.username =
      String(keycloak.tokenParsed?.preferred_username ?? keycloak.tokenParsed?.name ?? "");
    keycloak.onAuthLogout = clearIdentity;
    keycloak.onTokenExpired = () => void refreshToken();
  } catch (error) {
    mutableState.error = error instanceof Error ? error.message : "Authentication is unavailable";
  } finally {
    mutableState.ready = true;
  }
}

export function login(): Promise<void> {
  if (!keycloak) return Promise.resolve();
  return keycloak.login({ redirectUri: window.location.href });
}

export function logout(): Promise<void> {
  if (!keycloak) return Promise.resolve();
  return keycloak.logout({ redirectUri: window.location.href });
}

export async function accessToken(): Promise<string | undefined> {
  if (!keycloak?.authenticated) return undefined;
  await refreshToken();
  return keycloak.token;
}

async function refreshToken(): Promise<void> {
  if (!keycloak?.authenticated) return;
  try {
    await keycloak.updateToken(30);
  } catch {
    keycloak.clearToken();
    clearIdentity();
  }
}

function clearIdentity(): void {
  mutableState.authenticated = false;
  mutableState.username = "";
}
