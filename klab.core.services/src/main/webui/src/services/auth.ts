import Keycloak from "keycloak-js";
import { reactive, readonly } from "vue";
import type { AuthenticationSettings } from "../types";

export interface AuthState {
  /**
   * True when Keycloak authentication has been configured by the service.
   */
  enabled: boolean;

  /**
   * True once the initial Keycloak session check has completed.
   *
   * Until this becomes true, components requiring authentication may want
   * to show a "checking session" state rather than immediately showing
   * the login button.
   */
  ready: boolean;

  /**
   * True when the browser currently has an authenticated Keycloak session.
   */
  authenticated: boolean;

  /**
   * Username extracted from the Keycloak access token.
   */
  username: string;

  /**
   * Authentication initialization/runtime error.
   *
   * Authentication errors must not prevent the public dashboard from
   * functioning.
   */
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

/**
 * Reactive, read-only authentication state exposed to Vue components.
 */
export const authState = readonly(mutableState) as AuthState;

/**
 * Initialize optional Keycloak authentication.
 *
 * Authentication is deliberately NOT required to load the dashboard.
 *
 * The application uses check-sso so that:
 *
 * - an existing Keycloak session is detected automatically;
 * - users without a Keycloak session remain anonymous;
 * - the public dashboard/status/capabilities remain accessible;
 * - protected panels/pages can explicitly invoke login().
 */
export async function initializeAuthentication(
  settings: AuthenticationSettings,
): Promise<void> {
  resetState(settings.enabled);

  if (!settings.enabled || !settings.url) {
    mutableState.ready = true;
    return;
  }

  keycloak = new Keycloak({
    url: settings.url,
    realm: settings.realm,
    clientId: settings.clientId,
  });

  /*
   * Register callbacks before initialization.
   *
   * The identity is also explicitly updated after init(), because the
   * initial authentication may complete during init before these callbacks
   * would otherwise be useful to the application.
   */
  keycloak.onAuthSuccess = updateIdentity;

  keycloak.onAuthLogout = clearIdentity;

  keycloak.onAuthRefreshSuccess = updateIdentity;

  keycloak.onAuthRefreshError = () => {
    keycloak?.clearToken();
    clearIdentity();
  };

  keycloak.onTokenExpired = () => {
    void refreshToken();
  };

  try {
    /*
     * document.baseURI is important here.
     *
     * The dashboard may be served from:
     *
     *   http://localhost:8092/resources/
     *
     * or:
     *
     *   https://some-host.example.org/resources/
     *
     * or potentially another context path.
     *
     * index.html already establishes the correct application base path,
     * so this produces the correct callback URL without hard-coding the
     * server hostname, port or context path.
     */
    const silentCheckSsoRedirectUri = new URL(
      "silent-check-sso.html",
      document.baseURI,
    ).href;

    const authenticated = await keycloak.init({
      /*
       * IMPORTANT:
       *
       * Do not use "login-required" here.
       *
       * The dashboard itself is public. check-sso only detects whether
       * the browser already has a Keycloak session.
       */
      onLoad: "check-sso",

      /*
       * Check an existing Keycloak SSO session without redirecting the
       * entire public dashboard through Keycloak.
       */
      silentCheckSsoRedirectUri,

      /*
       * If the browser cannot perform silent SSO (for example because
       * third-party cookie restrictions interfere with it), do NOT fall
       * back to a visible Keycloak redirect.
       *
       * The dashboard must remain usable anonymously.
       */
      silentCheckSsoFallback: false,

      /*
       * Disable the Keycloak login-status iframe.
       *
       * Authentication state is instead handled through the token and
       * explicit login/logout operations.
       */
      checkLoginIframe: false,

      /*
       * Use Authorization Code Flow with PKCE S256.
       *
       * This does not require the Keycloak administration console to expose
       * a separate PKCE configuration option. The JavaScript client sends
       * the PKCE parameters as part of the authorization request.
       */
      pkceMethod: "S256",
    });

    mutableState.authenticated = authenticated;

    if (authenticated) {
      updateIdentity();
    } else {
      clearIdentity();
    }
  } catch (error) {
    /*
     * Keycloak being unavailable must not make the public dashboard
     * unavailable.
     */
    clearIdentity();

    mutableState.error =
      error instanceof Error
        ? error.message
        : "Authentication is unavailable";
  } finally {
    mutableState.ready = true;
  }
}

/**
 * Explicitly authenticate the user.
 *
 * This should be called when the user attempts to use a panel/page/operation
 * whose WebUiConfiguration has requiresAuthentication=true.
 *
 * window.location.href ensures Keycloak returns the user to the page they
 * were actually using, independently of hostname, port and deployment path.
 */
export async function login(): Promise<void> {
  if (!keycloak || !mutableState.enabled) {
    return;
  }

  mutableState.error = "";

  try {
    await keycloak.login({
      redirectUri: window.location.href,
    });
  } catch (error) {
    mutableState.error =
      error instanceof Error
        ? error.message
        : "Unable to start authentication";
  }
}

/**
 * Log out from Keycloak and return to the current dashboard location.
 */
export async function logout(): Promise<void> {
  if (!keycloak) {
    clearIdentity();
    return;
  }

  mutableState.error = "";

  try {
    await keycloak.logout({
      redirectUri: window.location.href,
    });
  } catch (error) {
    mutableState.error =
      error instanceof Error
        ? error.message
        : "Unable to log out";
  }
}

/**
 * Obtain a valid access token for an authenticated API request.
 *
 * Returns undefined when the user is anonymous.
 *
 * API code can therefore distinguish between:
 *
 *   public request:
 *     no token required
 *
 *   authenticated request:
 *     accessToken() must return a token
 */
export async function accessToken(): Promise<string | undefined> {
  if (!keycloak?.authenticated) {
    return undefined;
  }

  const valid = await refreshToken();

  if (!valid) {
    return undefined;
  }

  return keycloak.token;
}

/**
 * Refresh the access token if it will expire within 30 seconds.
 *
 * Returns true when the user still has a usable authenticated session.
 */
async function refreshToken(): Promise<boolean> {
  if (!keycloak?.authenticated) {
    return false;
  }

  try {
    await keycloak.updateToken(30);

    updateIdentity();

    return Boolean(keycloak.authenticated && keycloak.token);
  } catch {
    keycloak.clearToken();
    clearIdentity();

    return false;
  }
}

/**
 * Synchronize the Vue authentication state with the Keycloak instance.
 */
function updateIdentity(): void {
  if (!keycloak?.authenticated) {
    clearIdentity();
    return;
  }

  mutableState.authenticated = true;
  mutableState.error = "";

  mutableState.username = String(
    keycloak.tokenParsed?.preferred_username ??
      keycloak.tokenParsed?.name ??
      keycloak.tokenParsed?.email ??
      "",
  );
}

/**
 * Return the application to anonymous mode.
 *
 * This does not make the dashboard unavailable; it only prevents protected
 * components/operations from being used until login() succeeds.
 */
function clearIdentity(): void {
  mutableState.authenticated = false;
  mutableState.username = "";
}

/**
 * Reset authentication state before initialization.
 */
function resetState(enabled: boolean): void {
  mutableState.enabled = enabled;
  mutableState.ready = false;
  mutableState.authenticated = false;
  mutableState.username = "";
  mutableState.error = "";

  keycloak = null;
}