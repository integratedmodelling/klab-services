# Hub authentication follow-up

## Current bridge

The service dashboard signs in with Keycloak, then sends its access token to
`POST public/ui/authentication` on the service. The service calls the trusted hub's
`GET /api/v2/users/me?remote=true` with that token. The existing hub endpoint returns
an `EngineProfileResource` containing `name`, `email`, `roles`, `groupEntries`, and
`jwtToken` (normally HTTP 202). HTTP 204 means the Keycloak identity has no hub profile.

The hub JWT uses the hub name as issuer, username as subject, `engine` as audience,
and top-level `roles` and `perms`. Despite its name, the hub's `getGroupsIds()` supplies
group **names**, filtered by membership validity. The bridge uses these JWT permissions
rather than granting every group appearing in `groupEntries`.

The current contract does not publish a signing-key discovery endpoint. Consequently,
the bridge trusts the response received directly over HTTPS from the configured hub;
it does **not** claim to verify the JWT signature independently. It checks issuer,
subject/profile agreement, audience, issuance time, expiry, and the user role, and
issues a random service-specific browser credential valid for at most five minutes.
The upstream JWT stays on the service for the user identity and downstream calls.
Client-supplied hub JWTs are never enrolled in this browser credential cache.

This works without a service certificate. The existing certificate-based JWT validator
and native-client `server-key` path remain separate. Local browser sessions must match
the authenticated service owner's username and startup hub issuer. They use isolated,
non-local user scopes and cannot select an existing IDE context with a scope header.

Trust configuration is taken from service startup authentication data: the authenticated
hub URL/name for a certified service, or the hub's HTTPS URL/id in the local authentication
package. Explicit server-side overrides are available when these fields are unavailable:

```properties
klab.webui.hub.url=https://hub.example.org/hub
klab.webui.hub.issuer=im
```

The URL includes the hub's deployment context, but excludes `/api/v2/users/me`.
Both overrides must describe the same trusted hub. They are not browser-controlled.
Plain HTTP, redirects, URL credentials, query strings, and fragments are rejected.
An offline hub prevents new browser sign-ins and renewals; already-issued credentials
remain valid until their bounded expiry. Restarting a service invalidates its credentials.

## Service-issued permissions

`ServicePermissionPolicy` resolves the grant after hub identity verification. For a local
service, matching the authenticated startup owner's username **and startup issuer** grants
`ROLE_ADMINISTRATOR`, `ROLE_DATA_MANAGER`, and all service CRUD permissions, including
`ADMINISTER`. A username match alone, an anonymous/unauthenticated startup owner, or a hub
issuer supplied only through a web configuration override cannot establish ownership.
Other users remain inadmissible on the local browser route. Network services retain their
verified hub-role policy; an ordinary hub user receives READ.

The grant is service-specific: it updates both `EngineAuthorization` authorities and the
browser's `ServiceUserScope` permissions. It does not rewrite the upstream JWT, invent hub
group memberships, set the local-secret flag, or grant administration on downstream services.
The authenticated scope is the authority for service capabilities. The dashboard requests
capabilities with its browser credential and discards the previous grants on logout.

`ServiceSideScope.isAuthorized(...)` exposes the server-side permission contract to API-layer
consumers. `WorkflowParticipant` maps a service grant of `ADMINISTER` to workflow `ADMIN`,
so service administrators can inspect/manage workflows without workflow-specific group
properties. Ordinary participants continue through the existing group/asset policy.

This is the initial owner policy, not a general-purpose permission editor. Grants are recomputed
on exchange/renewal; logout, expiry, or restart invalidates the corresponding session. Per-user
delegation, configurable grants/denies, live revocation, and auditing of every service operation
remain future work. Service configuration and asset-specific checks still apply where enforced.

## Proposed hub changes (not implemented here)

1. **Publish signing-key discovery and rotation.** Provide authenticated issuer metadata
   and HTTPS JWKS with stable key identifiers (`kid`), overlap during rotation, caching
   policy, and an explicit issuer contract. This lets local services validate hub tokens
   independently without requiring institutional service credentials or a live exchange
   for each browser session renewal.

2. **Introduce a dedicated exchange endpoint.** Preserve `users/me?remote=true` during
   migration, but use POST for token issuance and return a versioned response with
   `access_token`, `token_type`, `expires_in`, and intended service audience. Add
   `Cache-Control: no-store` to all token-bearing responses. The current GET response
   also exposes a broader profile than the dashboard needs.

3. **Reduce and scope token lifetime.** The current factory creates ten-day engine JWTs.
   Exchange credentials should be short-lived, bounded by the source authentication,
   and restricted to the receiving service or explicitly authorized service set.
   Define refresh, logout/revocation, and disabled-user/membership-change behavior.

4. **Formalize identity and authorization claims.** Specify stable subject identifiers,
   human-readable usernames, tenant/issuer boundaries, role names, and group identifiers.
   Distinguish authentication from hub registration, signed agreements, membership validity,
   and authorization. Retain compatibility with `ROLE_USER` and group-name `perms` during
   migration. Do not derive k.LAB administrator privileges from unrelated Keycloak roles.

5. **Handle incomplete profiles explicitly.** The current factory and
   `EngineProfileResource` access the first agreement directly. Handle absent/empty
   agreements and invalid/expired membership without an indexing error. Return stable
   error codes for missing registration, agreement required, disabled user, and forbidden
   audience; the current 204 is only an indirect registration signal.

6. **Constrain exchange callers and add integration tests.** Verify the Keycloak token's
   intended audience and authorized client, rate-limit exchange, and test that one user
   cannot obtain another user's token. Cover issuer/key rotation, expired credentials,
   profile/subject mismatch, malformed claims, membership changes, and redirects. Never
   log access tokens, issued JWTs, service secrets, or complete authentication packages.

## Related service follow-up

The legacy certificate-based verifier still skips audience enforcement and discards
parsed `perms`; the new browser bridge checks audience and preserves permission names.
Migrate the legacy path deliberately with compatibility tests for engine/service callers.
Its scope cache is keyed by username rather than issuer and authorization level. Browser
scopes are isolated to avoid that collision, but the legacy cache deserves a separate audit.

Browser credentials currently support user-scoped operations, including workflow listing,
graphs, and PNG downloads. Context/session selection and distributed browser runtime work
need an explicit registration and ownership contract before scope headers can be enabled.
The bridge is in-memory and bounded to 256 active credentials per service process; a
distributed deployment would need shared session storage or service affinity.

## Validation

Focused service tests cover owner restrictions, hub response claims, role/group projection,
scope isolation, opaque credential expiry/revocation, exchange routing, and HTTP errors.
Frontend tests cover concurrent exchange, renewal, logout races, rejection/retry, and
malformed responses. These use controlled hub responses; production Keycloak-to-hub login
must also be checked after deploying the rebuilt service and dashboard.
