# Engine-like scientific connections

`connect(profile, credentials=provider)` orchestrates authentication, bounded
service observation, explicit selection and scoped peer initialization around
the existing synchronous `Client`. Direct `Client(...)` remains supported.

```python
from klab_client import DeploymentProfile, HubCertificateProvider, connect

profile = DeploymentProfile.load("deployment.json")
provider = HubCertificateProvider(certificate_path="/private/klab.cert")
with connect(profile, credentials=provider) as connection:
    connection.wait_ready(timeout=30)
    print(connection.catalog.summary())  # secret-free status, not asset permissions
    client = connection.client
    # Existing typed observe, Job, handle restoration and native export APIs.
```

The certificate is a native k.LAB **ENGINE properties/license file**, not a TLS
client certificate. The provider follows Java-properties Latin-1 encoding,
escaping, continuation and last-duplicate semantics. It POSTs the inspected
native DTO to `/api/v2/engines/auth-cert` without an existing Authorization
header. It consumes `userData.identity/token/expiry/groups`, `hub.id` and
scientist `services`; legacy `nodes` are not substituted. Authentication must
return a valid matching identity and nonexpired package; there is no anonymous
fallback. See [contract inventory and evidence](connection-verification.md).

## Profiles and issued tokens

Build and atomically save a version-1 profile from explicitly known endpoints:

```python
from klab_client import ConnectionIdentity, DeploymentProfile, IssuedTokenProvider

profile = DeploymentProfile.from_urls(
    "research-deployment",
    {"runtime": "https://runtime.example/runtime",
     "reasoner": "https://reasoner.example/reasoner",
     "resources": "https://resources.example/resources",
     "resolver": "https://resolver.example/resolver"},
    hub_url="https://hub.example",
    credential_refs={"runtime": "env:KLAB_RUNTIME_TOKEN",
                     "reasoner": "env:KLAB_REASONER_TOKEN",
                     "resources": "env:KLAB_RESOURCES_TOKEN",
                     "resolver": "env:KLAB_RESOLVER_TOKEN"},
)
profile.save("deployment.json")  # existing parent; no overwrite by default
provider = IssuedTokenProvider.from_env(
    profile, ConnectionIdentity("my-known-principal", "my-known-issuer")
)
```

Use the same `connect`/scientific code with this provider. Tokens are read privately
from the caller's environment; profiles contain references, never credential
values. A mapping or callback is also supported. A callback may return an
immutable `CredentialGrant` with known aware expiry and typed service references.
Identity/expiry metadata comes from the trusted credential source, not parsing an
unverified JWT into purported permission grants. Custom provider callbacks must
honor the supplied deadline; synchronous Python cannot interrupt arbitrary caller
code without adding an execution mechanism.

Profiles validate known service types for required dependencies, URL bases,
finite budgets, bounded catalogs and environment-reference syntax. Unknown
schemas/fields fail, including nested credential fields. `load` and `save` use
explicit paths; `save(overwrite=True)` is an explicit atomic replacement.
Profile names/IDs are public metadata and must not be used to store credentials.

## Trust and service selection

- Certificate Hub base must exactly match `profile.hub_url`, including its base
  path. Normal TLS verification is required. Certificate material is never sent
  to a discovered Hub, redirected login destination or unapproved service.
- Only exact `trusted_urls` receive probes/credentials. Configured service URLs
  populate this allowlist when it is omitted. Advertised untrusted references
  remain visible but unusable; trust is not inferred from the advertisement.
- HTTP certificate exchange/credential forwarding is limited to **TEST**
  certificates in an explicit all-loopback `test_loopback=True` profile.
  `verify=False` is limited to those loopback test profiles, and cannot be used
  for a non-TEST certificate exchange.
- Explicit `choices={"runtime": "https://.../runtime"}` or observed/reference
  ID wins if it uniquely names an eligible service. Otherwise exactly one
  verified available candidate of each required type is needed. Several
  candidates fail with `ServiceSelectionError`; choose an endpoint explicitly.
- The default required set is Runtime/Reasoner/Resources/Resolver. A smaller
  `required_services` supports metadata-only use; accessing `connection.client`
  still requires a selected Runtime. The catalog itself remains inspectable.
- Hub reference IDs are distinct from observed service UUIDs. Configured
  `ServiceReference.expected_service_id` pins are enforced for both providers.
  The selected endpoint/UUID cannot silently fail over or change under handles.
  Selected mappings, connection profile and bootstrap identity are read-only.
- Optional `worldview` is a strict requirement for a matching, supplied
  `worldviewId` capability on required candidates. Missing/incompatible evidence
  is labelled unknown/incompatible; omit that assertion when the deployment does
  not expose meaningful worldview compatibility for those services.

An injectable policy implements `select(service_type, candidates, preference)`.
Its return must be one of those verified candidates. Replacing this policy or
`CredentialProvider.acquire(profile, deadline=...)` does not change observe,
export or handle code, and cannot expand credential trust.

## Readiness, renewal and lifecycle

`refresh()` explicitly re-observes capability/status metadata. Advertised,
trusted, reachable, available, operational and supported are separate states;
selection is exposed separately. Failed/incomplete probes invalidate current
readiness while preserving identity pins. No cached login/profile is permanent
permission authority. Catalogs and profiles can be inspected offline; this does
not provide offline reasoning or queued mutations.

Peer setup POSTs the supported `/notifyUserScope` notification. It retains native
status metadata (including the required Jackson `@CLASS` discriminator), avoids
advertising loopback peers to a remote host, and retains the latest pending
snapshot per target. A boolean acknowledgement is distinct from operational
readiness and grants no asset permissions. Startup, refresh and peer requests
are bounded; explicit `wait_ready(timeout=...)` polls required readiness/pending
acknowledgements within its budget. Completely missing/unavailable required
candidates fail selection with a configuration error rather than being launched.

Known expiry permits synchronous provider reacquisition **before** a request.
`renew()` explicitly reacquires through the supported provider (the certificate
provider repeats its native exchange). Unknown expiry remains unknown. A 401/403
does not invent expiry or trigger a scientific retry. Certificate/package expiry
raises `CredentialExpiredError`; server authentication/authorization errors retain
their distinct types. Principal/issuer change or a pinned service UUID change
makes the connection stale and requires explicit reconnection. Concurrent callers
receive whole origin-bound credential snapshots. Previous/current known secrets
remain redacted in diagnostics after renewal.

No submitted scientific mutation is replayed on authentication or transport
failure. Existing `SubmissionOutcomeUnknown`, Job/handle and explicit cancellation
semantics remain authoritative. `Connection.close()` closes local HTTP resources;
it never releases sessions/contexts or cancels jobs. Caller-owned cleanup remains
explicit and independently attempted.

## Example and acceptance

`python examples/connect.py deployment.json --certificate /private/klab.cert`
prints a safe catalog. The same example supports `--issued-tokens --principal
my-principal --issuer my-issuer`, and optional restoration/native export of an
existing observation handle. Missing requested configuration fails clearly.

The named `connection-onboarding`, `connection-recovery`, `connection-access`
and `connection-readiness` suites use existing functional runners/shared workflows.
Their owned TEST exchange is **controlled contract evidence**, followed by real
services, an independently known 123.25 m / 123250 mm field, published native
GeoJSON identity/geometry checks and fresh-process recovery after local close.
It is not genuine Hub onboarding. A real ENGINE certificate accepted by a real
supported Hub and services remains a separate explicitly reported acceptance gate.
