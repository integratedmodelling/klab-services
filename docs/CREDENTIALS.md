# External credentials

Each service owns a credential vault under its configuration directory (`services/<service-type>`
within its data root). Credentials for external systems are separate from k.LAB login tokens.
Administrators manage named sets in **Credentials** on the service Web dashboard and in the IDE's
ServiceDashboard. The service enforces `ADMINISTER` permission for administration even for direct
Java API calls. Hiding an editor is not the authorization boundary.

## Sets and schemas

A set has an opaque, service-generated ID, a unique display name, an endpoint URL, a schema ID,
named parameter values and `ResourcePrivileges` usage grants. Multiple sets can target the same
endpoint. An omitted ID creates a new set; an existing ID updates that set. Unknown IDs are rejected.
Omitted parameters and omitted privileges preserve their current values on update. An explicit empty string clears an
optional parameter; required fields cannot be cleared. Changing schema requires a new set.

`CredentialInfo` contains the stable ID (`key`), display name (`description`), endpoint (`host`),
schema, usage grants, non-secret parameters and names of configured fields. It never contains
secret values, masked secret placeholders or the old positional credential array. Blank secret
inputs in either editor preserve stored values. Optional secrets have an explicit clearing control.
The existing positional Java write API remains accepted for compatibility, but new integrations
should use named parameters and schema discovery.

Built-in schemas are `basic`, `token` (Git HTTPS username and access token in the `password` field),
`key` (bearer/API key), `oidc` (OAuth client configuration), `s3`, and `ssh` (private key and optional
passphrase). A schema describes storage and validation. Its mechanism identifies the protocol
adapter implemented by the consuming component; defining a schema does not implement a protocol,
perform an OAuth token exchange, or refresh an expired token.

Services can add or override schemas in `credential-schemas.json` in their configuration directory.
The configuration is read when the vault is first accessed; restart the service after editing it.
All fields must have unique names and declare whether they are secret and required:

```json
[
  {
    "id": "registry-token",
    "label": "Private registry",
    "mechanism": "bearer",
    "fields": [
      {"name": "token", "label": "Access token", "secret": true, "required": true}
    ]
  }
]
```

Administrators always have usage access. Other users must be authenticated and granted usage
through usernames, groups or the public flag. Explicit user/group exclusions override usage grants.
Empty grants permit administrators only. Null, anonymous and unrecognized scopes never receive
secret material. A service's own exact service scope can perform trusted background work; other
service scopes are not implicitly trusted.

## API

The existing endpoint paths are retained:

- `GET /credentials`: administrator metadata listing.
- `POST /credentials`: create/update; body remains `{"host": "...", "credentials": {...}}`.
- `DELETE /credentials?id=<id>`: delete a set.
- `GET /checkCredentials?scheme=<schema>&host=<endpoint>`: exact metadata existence check.
- `GET /credentials/schemata`: administrator schema discovery (the only additional route).

Example write (send through HTTPS and do not store request bodies in logs):

```json
{
  "host": "https://gitlab.com/my-organization",
  "credentials": {
    "name": "Organization GitLab access",
    "scheme": "token",
    "parameters": {"username": "oauth2", "password": "<access-token>"},
    "privileges": {"allowedUsers": ["alice"], "allowedGroups": ["MODELLERS"]}
  }
}
```

`KlabService.addCredentials`, `getCredentialInfo`, `getCredentialSchemas` and `removeCredentials`
provide the same administrative contract to Java clients. There is deliberately no HTTP endpoint
that retrieves stored secrets. Local components resolve a set through the owning service's
`credentialVault().resolve(id, destination, callerScope)` and must retain the requesting user's
scope rather than substitute the service scope. The returned object is detached from storage.
Components must recheck the destination with `CredentialVault.matchesEndpoint` if their transport
redirects or selects a different endpoint.

## Git repositories

HTTPS username/password and access-token authentication are implemented by the Git adapter.
Use the `basic` or `token` schema (or a custom schema with mechanism `basic` and fields `username`
and `password`). Other mechanisms, including stored SSH private keys, require a transport adapter;
they fail explicitly instead of silently falling back to unauthenticated Git.

Repository-management requests accept optional `credentialId` in the existing `ProjectRequest`
body. The scoped `ResourcesService.manageRepository` overload passes the real requesting principal
and optional ID. Project import through `ResourcesProvider.importProject(..., scope, credentialId)`
uses that ID for cloning and stores only the ID in local `.git/config` as `klab.credentialId` for
subsequent operations. No token or password is embedded in a repository URL or configuration.
For component code, `Utils.Git.withCredentials(service, callerScope, id, operation)` binds those
values for one synchronous operation and restores the previous binding in `finally`.

An explicit ID must be authorized for the actual destination. Without an ID, automatic selection
works only when exactly one authorized set matches; ambiguous matches fail and require selection.
The destination must match scheme, hostname, effective port and a path-segment boundary. Hostname
prefixes, unrelated paths, protocol downgrades and arbitrary push URLs cannot reuse the credential.
Git rechecks the target when the transport requests the username/password and when creating every
HTTP connection, covering redirects even after authentication has been cached, and separate push URLs. Endpoints must be absolute `https`, `http`, `ssh` or `s3` URLs without embedded
user information, query strings, fragments or percent-encoded paths. Prefer HTTPS for network Git.

## Storage and operation

The vault uses AES-256-GCM with a random 96-bit nonce per write, authenticated format metadata and
a versioned binary format. Files are created with owner-only POSIX permissions or Windows ACLs.
Writes take an interprocess lock, flush an encrypted temporary file, and atomically replace the
vault. Corruption, an incorrect key or a missing key fails closed without replacing existing data.
These choices follow the authenticated encryption and key-separation guidance in the
[OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html).

By default the service creates a random 32-byte binary `credentials.key` beside `credentials.vault`.
For deployments, configure `-Dklab.credentials.keyFile=/path/to/private/key` to keep the key outside
the service data/backup directory, on a restricted secret mount. The key file must contain exactly
32 random bytes. The default protects against disclosure of the vault file alone; anyone able to
read both the key and vault, or compromise the running service, can recover the credentials.
Protect the service account and backups accordingly. Back up the key separately: losing it makes
the encrypted credentials unrecoverable. Offline key rotation currently requires recreating the
sets under a new key; there is no automated key-rotation endpoint.

Legacy shared `.klab/credentials.json` is no longer loaded, written or automatically migrated.
Its host-only keys cannot establish reliable service ownership or authorization. Administrators
must recreate those sets in the appropriate service, verify the usage grants and consumers, update
references to the new IDs, then remove the old plaintext file from active storage and handle old
backups according to local retention policy. Rotate tokens that were exposed through the old
metadata API. Local client credentials use a separate encrypted vault per authenticated principal
under `credentials-client/<principal-id>`; they do not access a service vault.

The CLI accepts `credentials set --name ... --id <existing-id>` for updates (omit `--id` to create)
and `credentials remove --id ...`; `--resources` targets the resources service with the user's scope.
