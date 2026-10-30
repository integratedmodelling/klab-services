# Administrative diagnostic logs

This initial API exposes vetted diagnostics from the active service log. It is an administration API, not public log access or arbitrary file retrieval. The IDE `LogViewer(LogSource)` can consume pages through `RemoteLogSource` and the selected service's existing authenticated client; its Path constructors retain local-file behaviour.

## Source and access

The service uses Spring's resolved `logging.file.name`. Standard startup constructs the path from the prepared service data root, normally `~/.klab/services/<type>/logs/<type>.log`, and sets the logging property before Spring starts. The API does not expand a literal tilde or accept caller paths. It uses the active configured file only; archived segments are not exposed in this first slice.

The existing administrator/system controller policy is retained. Every handler additionally checks an authenticated EngineAuthorization with a resolved ServiceUserScope granting ADMINISTER. Ordinary authenticated READ access does not permit log retrieval. Query cursors are bound to an authenticated identity fingerprint and require authorization on every request; no raw identity/credential is stored in a cursor or returned.

`GET /admin/logs/capabilities` reports stream `service`, limits, source zone, redaction policy, and unsupported archive/correlation features. `POST /admin/logs/query` accepts:

```json
{"streamId":"service","cursor":null,"limit":200,"follow":false,
 "from":null,"to":null,"minLevel":"ERROR","loggerPrefix":null}
```

`from` and `to` are optional UTC ISO instants; logger prefix applies to the displayed full or abbreviated logger. The standard file timestamp lacks an offset, so conversion uses the service JVM's source zone, returned explicitly in the page. A custom incompatible file pattern may not be recognized; this reader supports the standard startup and fallback patterns.

Results contain hosting service ID, per-reader/process instance ID, records, next cursor, snapshot byte boundary, caughtUp, omission count and policy version. No filesystem path is returned. Cursors expire after ten minutes or bounded cache eviction. Defaults are 200 records, maximum 1,000, at most 256KiB scanned per query plus bounded boundary witnesses, 64KiB retained stack per event, at most 256 cursor states, and a conservative 1MiB response budget. Queries are rate limited per actor and globally serialized within this bounded reader. Reading these logs is audited to a separate bounded rolling sink (`<configured-log>.admin-audit`) to avoid polling its own audit output.

## Paging, polling and changes

Continue with nextCursor and the same filters. Snapshot pages preserve the original high-water boundary. Once caughtUp, set follow=true to extend the snapshot to current file size; following an unread snapshot is rejected. The last event at EOF is partial because more exception lines may arrive. Later pages may return the same eventId with updated stack/partial status; consumers must replace that event rather than duplicate it.

UTF-8 fragments and unfinished lines are retained within bounds. Oversized lines/stacks are marked truncated, not silently treated as complete. Recognized multiline cause/suppressed/frame structure is preserved with messages redacted; unsupported continuation text becomes a placeholder.

Restart/expired cursor, rotation or truncation returns 410 with an explicit history-gap/reset diagnostic. File identity, directory create/delete events and bounded prefix/tail fingerprints detect replacement and retained-boundary changes, including ordinary copy-truncate/regrow. This is not a tamper-proof integrity audit of the entire file; no full historical rehash occurs per page. The initial API deliberately does not continue silently across rotated-away history. Start a new query explicitly after a gap.

Bad stream/filter/limit/cursor input returns 400, insufficient administration rights 403, unavailable configured source 503, and a budget limit 429 with Retry-After. Existing service authentication governs unauthenticated requests. Responses use private, no-store cache controls. Malformed JSON returns a constant diagnostic without echoing payloads.

## Disclosure boundary

Managed-file origin alone does not make raw text safe for remote disclosure. Vetted codes are currently:

- AUTHORITY_BOOTSTRAP_COMPONENT_FAILURE from OwnedBootstrapDiagnostics;
- AUTHORITY_BOOTSTRAP_FAILURE from AuthorityBootstrap;
- Unhandled service request failure from Logging.

Producer matching accepts full names and standard package abbreviations while requiring the exact producer class. Unapproved emitters and unknown message families are omitted. Fields are individually validated: phase/reason enums, canonical public component coordinates, numeric HTTP status, binding counts and approved revision hashes. Unknown fields, credentials, request/response bodies and raw messages are not exported. No unredacted admin bypass is provided.

Original exception/cause types, safe source frames, suppressed chains and explicit truncation are retained. Existing bootstrap Throwable redaction remains in force. OwnedBootstrapDiagnostics additionally captures a numeric RequestFailure status from a bounded cause search before the snapshot loses that field; no provider response text is restored. Historical statuses that were never logged cannot be reconstructed.

## IDE

Construct `new LogViewer(new RemoteLogSource(selectedClient))`. Calls run on a daemon background poller; FX updates retain at most 5,000 remote rows and update partial events by ID. Closing does not wait for network completion or let a late reply advance a reset source. Authorization/expiry/gap/transport failure stops polling and displays a safe status; use `setSource` for an explicit reset or service selection. No remote payloads or credentials are persisted. `setFile` switches back to the existing local watcher.

This API cannot serve diagnostics when HTTP fails before startup, recover never-written async logs, or retrieve expired archives. Infrastructure/container logs remain the fallback. Broader redacted text, archive browsing and streaming tail are separate future work. Deployment must coordinate matching API/common client and IDE artifacts; no Languages, ODO, authority or worldview change is required.
