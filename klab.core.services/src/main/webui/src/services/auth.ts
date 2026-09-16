import Keycloak from "keycloak-js";
import { reactive, readonly } from "vue";
import type { AuthenticationSettings } from "../types";
import { HubSession } from "./hub-session";

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
const serviceSession = new HubSession();
let identityGeneration = 0;

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
    const signedIn = await keycloak.init({
      onLoad: "check-sso",
      checkLoginIframe: false,
      pkceMethod: "S256",
    });
    keycloak.onAuthLogout = clearIdentity;
    keycloak.onTokenExpired = () => void accessToken().catch(() => {});
    if (signedIn) await accessToken();
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
  clearIdentity();
  if (!keycloak) return Promise.resolve();
  return keycloak.logout({ redirectUri: window.location.href });
}

export async function accessToken(): Promise<string | undefined> {
  if (!keycloak?.authenticated) {
    if (mutableState.authenticated) clearIdentity();
    return undefined;
  }
  const generation = identityGeneration;
  try {
    await keycloak.updateToken(30);
    if (generation !== identityGeneration) throw new Error("Sign-in was cancelled.");
    if (!keycloak.authenticated || !keycloak.token) {
      clearIdentity();
      return undefined;
    }
    const session = await serviceSession.get(keycloak.token);
    if (generation !== identityGeneration) throw new Error("Sign-in was cancelled.");
    mutableState.username = session.username;
    mutableState.authenticated = true;
    mutableState.error = "";
    return session.token;
  } catch (error) {
    if (generation === identityGeneration) {
      clearIdentity();
      mutableState.error = error instanceof Error ? error.message : "Service sign-in failed.";
    }
    throw error;
  }
}

function clearIdentity(): void {
  identityGeneration++;
  serviceSession.clear();
  mutableState.authenticated = false;
  mutableState.username = "";
}

export function invalidateServiceAuthentication(): void {
  clearIdentity();
  mutableState.error = "Service access expired or was denied. Sign in again.";
}
