# Saved scientific handles — version 1

The maintained saved-handle API provides local scientific references and explicit
restoration for Context, ObservationImpl and Job. It works with the original #84
objects and imports no typed-request module. It changes no Java contract or
federation-session policy; the broader promotion/federation gate remains
unresolved and is not certified here.

Historical candidate hashes, dated validation evidence and the independent merge
handoff are preserved in [Saved handles validation history](saved-handles-validation.md).

## Issue and PR boundary

This checkout implements [issue #88](https://github.com/integratedmodelling/klab-services/issues/88)
for [PR #86](https://github.com/integratedmodelling/klab-services/pull/86).
Its scope is strict portable references, offline file operations, safe binding
and decoder identity, and explicit current-server restoration. Shared submission
and URL helpers are included because normal helper jobs must preserve the same
identity/decoder contracts as capture. The test matrix below maps implementation
criteria to maintained regressions and live acceptance. Publication and exact
published-candidate checks remain required before closing the issue.

Typed-request work belongs to #87/PR #85; federation integration and remaining
rollout checks belong to #89 in a separate checkout. Handles provide groundwork
for later persistent-session and job-recovery work, but do not deliver durable
jobs across server restart, simulation checkpoints or event replay. Reference
this commit from later issues without treating those guarantees as completed.

The client PR retains integration base `ad2bc949c` on `feature/python-client`.
The develop/federation merge is published separately under #89 to that same
integration branch. Neither client feature is stacked on the other; live reports
identify the separate server candidate. Broader federation promotion remains gated.

## Local references and explicit restoration

```python
from klab_client import load_handle, save_handle

# Saving is local, including when the original context was creator-owned.
save_handle(context, "context.json")
save_handle(job, "job.json")
save_handle(observation, "observation.json")

# A different process supplies its Client/endpoints/credentials independently.
reference = load_handle("observation.json")
print(reference.inspect())  # remote_verified is always False for an offline reference
restored = client.restore_handle(reference)
values = restored.fetch_data([0, 1], curve="D2_YX")
```

Context, ObservationImpl and Job expose `to_handle()`. The immutable `Handle`
provides `dumps/loads`, `save/load`, `inspect` and `restore`; equivalent top-level
functions are exported. Construction, capture, JSON parsing and file save/load
perform no networking. Restore never creates replacement work, submits, cancels,
waits or releases. The existing attach POST can reconstruct managed state and
retains its ambiguous-outcome behavior. Client closure remains local-only.

## Flat strict JSON schema

Common required fields: `version` (exact integer 1), `kind` (`context`,
`observation`, `job`), `runtime_id`, `runtime_url`, `context_id`, `scope`.
Root context identity is the server's existing `session.context` ID, not an
invented twin ID. Scope retains every focus ID and the optional observer suffix.

Observation adds `observation_id`. Job adds `job_id`, `service`, `service_id`,
`service_url`, `result_kind`. Only Runtime/resolver services are supported.
Allowed decoders: Runtime `observation`/`mapping`; resolver `dataflow`/`mapping`.
Supported service helpers supply their result interpretation. Arbitrary custom
callable decoders are refused unless an existing allowed interpretation is
explicitly supplied; functions/import paths are never serialized or executed.

The current refactor gives the Resolver helper explicit `result_kind="dataflow"`
decoder metadata, so capture preserves its allowed interpretation without
inferring it from a callable.

No cached DTO/configuration, principal/owner flags, status, cancellation flags,
profile or optional snapshot is stored. Unknown fields and all duplicate keys
(including nested duplicates) are rejected. No pickle or executable imports are
used. Input is bounded to 64 KiB and individual identifiers to 4 KiB of UTF-8;
IDs are positive signed-64-bit integers, never booleans. Unknown versions fail
explicitly. URL userinfo, queries/fragments, control characters, obvious
authentication material and configured credential contents are refused without
echoing their values in errors.

Capture/restoration inspect raw string values and up to four percent-decoded
representations for credentials configured on the supplied client. Inspection
never rewrites identities/base paths, does not form-decode literal `+`, and never
follows an inspected URL. Excessive encoding and invalid percent-encoded UTF-8
fail locally without echoing data. This is bounded by both schema size and a fixed
decoding count; a manually constructed reference cannot infer credentials that
were never supplied to it.

Capture needs an already configured or previously inspected stable home Runtime
identity. Resolver capture additionally needs prior peer capabilities. Saving
never fetches missing identities. Capability inspection caches only service IDs;
it does not follow advertised origins or confer authority.
Job capture also compares the bound scope client's available configured/cached
Runtime ID with the job client's stable home ID, including legacy configuration
that omits serviceId. Contradictory caches cannot be downgraded to URL-only checks.
Same-Runtime second clients remain supported; a legacy source without a known ID
still needs a compatible URL/configuration and the job client's stable identity.

Submission and handle capture share Runtime binding validation, rejecting known
identity conflicts before rebinding or HTTP. Both use the same URL normalization
as restoration, including uppercase schemes and explicit default ports.

The same checks apply before focusing on a bound observation or reading its
cells, including both contexts' reported identities. A known foreign asset cannot
be turned into a destination-bound focus and then saved under that destination's
Runtime ID. Bare integer IDs carry no origin information; missing legacy IDs
retain canonical URL/root validation and current server authorization.

## Restoration contract and source map

1. Match canonical configured origin **and service base path** before HTTP.
   Scheme/host/default-port spelling and trailing service slash are normalized;
   distinct base paths remain distinct. File URLs are never contacted or added
   as endpoints. Explicit `allow_relocation=True` uses only caller-configured
   endpoints and still requires verified stable Runtime/peer identities.
2. Read configured service capabilities and compare IDs; malformed/unreachable
   capabilities are protocol/transport failures, not missing work.
3. Attach the exact root context, check returned root/Runtime identity, and keep
   disposal ownership false. A different current principal is checked by the
   server; saved flags cannot bypass authorization.
4. Query each selected focus/observer and the requested observation using the
   existing context-scoped `/api/v1/query` route with `resultType=OBSERVATION`,
   exact ID and bounded results. Neo4jQueryCompiler's context visibility is the
   membership authority; canonical URN/class/ID checks add defensive verification.
   The unscoped direct asset getter is not used. Unknown selections fail explicitly.
   An observer must still be a persisted agent.
5. Return freshly decoded, bound observation data or the selected context view.
   Cached file data are not a substitute. Restore jobs to their actual executing
   service and decoder, then validate status once without waiting/retrieving.
   EMPTY raises JobUnavailableError; other valid states remain inspectable.
   Runtime observation-job results additionally verify current context identity
   before being bound. Resolver Dataflow remains a plan, not executed work.

Sources: RuntimeServerController.queryKnowledgeGraph, RuntimeService's context
query dispatch, KnowledgeGraphQuery defaults, Neo4jQueryCompiler visible/predicate
logic, checked Python DTOs and existing Job routes. No new endpoint is invented.

## File policy and failures

Save requires an existing destination parent and does not create directories.
Default overwrite is refused atomically. A same-directory temporary file (0600
permissions on POSIX; filesystem ACLs govern Windows access) is
fully written (including partial-write handling), synced and closed before
publication. Explicit overwrite uses atomic replace; no-clobber publication uses
hard-link creation and fails explicitly on unsupported filesystems. Failed write
or replace leaves an existing valid file unchanged. Temporary resources are
cleaned on failure/interruption. A cleanup failure after publication is reported
honestly: inspect the destination, which may contain the complete new reference.
Concurrent no-clobber writers cannot produce a partial or mixed document.

Local failures are HandleFormatError (InvalidRequestError), HandleIdentityError
(InvalidRequestError) and HandleIOError (KlabError). Remote 401/403/404, protocol,
transport/server errors, expired jobs and attach ambiguity retain their existing
distinct classes. Diagnostics never echo file-provided data or credentials.

## Requirement/failure test matrix

| Requirement | Named tests |
|---|---|
| All kinds/decoders, immutable deterministic round-trip | all_reference_kinds_and_decoder_variants_round_trip |
| Root/focus/observer, no ownership/disposal | local_context_handle_preserves_selection_and_contains_no_authority, restored_selection_revalidates_assets_and_never_takes_ownership |
| Invalid version/kind/fields/types/IDs/URLs | invalid_schema_is_local_and_diagnostics_do_not_echo_input, unknown_version_is_explicit, observation_and_job_ids_are_exact_integers, job_cross_field_invariants |
| JSON duplicates/size/UTF-8/nonfinite/snapshot secrets | malformed_duplicate_oversized_or_nonfinite_json_is_rejected, nested_duplicate_keys_and_missing_fields, unknown_or_secret_fields_are_rejected |
| Offline capture, stable service IDs/custom decoder | offline_capture_identity_requirements_and_custom_jobs, configured_credential_cannot_be_smuggled_as_reference_metadata |
| Known source identity versus legacy configuration | job_capture_rejects_differing_known_source_runtime_before_network, same_runtime_second_client_scope_and_legacy_unknown_capture, conflicting_source_cache_is_not_downgraded_to_configuration_only |
| Submission binding and explicit decoder metadata | submission_rejects_known_foreign_scope_before_rebinding_or_http, submission_retains_same_runtime_and_legacy_scope_support, custom_submission_decoder_is_not_inferred_to_be_a_dataflow, runtime_job_capture_rejects_contradictory_home_capabilities |
| Shared canonical URL equivalence | direct_job_capture_uses_same_url_normalization_as_restoration |
| Focus/read binding before capture | test_runtime_binding.py, foreign_focus_cannot_be_relabelled_into_a_saved_handle |
| Encoded credential and bounded inspection | percent_encoded_configured_credential_cannot_enter_a_saved_hint, loaded_reference_credential_is_rejected_before_capabilities_or_attach, safe_percent_encoding_is_inspected_but_never_rewrites_identity, literal_plus_and_percent_are_not_form_decoded_as_credentials, inspection_budget_and_invalid_utf8_are_explicit_before_network |
| Origin/base path, relocation, capability identity | mismatch_never_contacts_a_file_provided_origin, url_canonicalization_base_paths_and_explicit_verified_relocation, observer_requires_agent_and_service_identity_checks_are_explicit |
| Fresh authorized graph/foreign result refusal | restore_observation_queries_current_authorized_graph_and_not_cached_dto, lookup_must_establish_fresh_context_identity, restored_observation_job_cannot_bind_a_foreign_result |
| Runtime/resolver routing/decoder/state | runtime_and_resolver_job_references_do_not_cross_route, restored_job_decoder_allowlist_and_actual_result_routes, restore_does_not_wait_cancel_or_invent_job_state |
| Missing/expired/failed/interrupted/unknown work | unavailable_or_unknown_job_state, restored_terminal_results_keep_existing_semantics |
| 401/403/404/5xx/transport/ambiguity, no substitutes | capability_failure_is_not_proof_of_absence, attach_failure_never_creates_replacement_work, transport_failure_and_attach_ambiguity |
| Atomic overwrite, permission/partial/interrupted writes | atomic_save_refuses_overwrite_and_preserves_existing_file, failed_and_partial_writes_preserve_existing_valid_file, interrupted_write_cleans_temporary_and_does_not_publish, local_io_policy_limits_and_cleanup_errors_are_explicit |
| Concurrency/subprocess/principal changes | atomic_no_clobber_publication_under_concurrent_saves, actual_fresh_subprocess_round_trip_and_authorized_principal_change |

The integration-marked subprocess test uses a deterministic local HTTP server;
it is not real scientific-execution evidence. Existing #84 close/release, manual
attach/job, transport and raw submission tests remain in the full suite.

## Gates and real-stack demonstration

Run these commands from `python-client` with the dev extras installed:

```powershell
python -m pytest -q --cov=klab_client --cov-branch --cov-report=json
python tools/check_feature_coverage.py coverage.json --base ad2bc949c167e1c2e370973ce4427fe67cb1cc30 --new-module src/klab_client/handles.py
python -m ruff check src/klab_client/handles.py tests/test_saved_handles.py tools/check_feature_coverage.py --select E9,F63,F7,F82
python -m build
```

New production module requires unexcluded 100% executable line and branch coverage;
changed existing production lines/decisions are checked separately from inherited
gaps. CI applies these gates on Python 3.11/3.12/3.13. Coverage/lint are dev extras.

The Runtime-binding correction passed **330 maintained tests** on Windows Python
3.12, including the shared focus/read checks and rejection before a foreign focus
can be saved. New-module and changed-code gates cover the shared helpers too;
targeted lint passed. Historical cross-version, package and live results retain
their own source identities in the linked validation record. A changed candidate
requires fresh checks before publication.

The fixed coverage comparison base measures this contribution against its
integration baseline. When integrating, retain the new-module gate and select
the repository's maintained comparison base for future changes.

`examples/saved_handles.py` has explicit create, restore, collaborator and release
modes. Caller setup reads credentials separately and explicitly initializes peers;
it is not a hidden restoration action. On the prepared real fixture it checks
all three handle kinds, a Resolver plan, full focus/observer selection, a
nonuniform 20-cell field with valid zero/missingness, and genuine in-flight timeout
then restoration/completion in another Python process. A collaborator reads
shared work and is denied private work; only the owner's final mode disposes
test-owned state.

```powershell
python tools/run_local_acceptance.py --java C:\jdk\bin\java.exe --worldview C:\pinned-imod --state-dir C:\existing-parent\new-state --build --saved-handles --samples 1 --duration 1
```

A Python-process restart does not establish persistent job retention across Runtime restart or
resumable simulation. No production onboarding, provider accuracy, automatic
migration, bulk export or federation-policy redesign is certified.
