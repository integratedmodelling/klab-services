# Saved scientific handles — version 1

Base: upstream integration **ad2bc949c167e1c2e370973ce4427fe67cb1cc30**.
This feature works with the original #84 objects and imports no typed-request
module. It changes no Java contract or federation-session policy. Baseline Python
97 and strict Java 56-case checks passed on the recorded base; the broader
promotion/federation gate remains unresolved and is not certified here.

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

No cached DTO/configuration, principal/owner flags, status, cancellation flags,
profile or optional snapshot is stored. Unknown fields and all duplicate keys
(including nested duplicates) are rejected. No pickle or executable imports are
used. Input is bounded to 64 KiB and individual identifiers to 4 KiB of UTF-8;
IDs are positive signed-64-bit integers, never booleans. Unknown versions fail
explicitly. URL userinfo, queries/fragments, control characters, obvious
authentication material and configured credential contents are refused without
echoing their values in errors.

Capture needs an already configured or previously inspected stable home Runtime
identity. Resolver capture additionally needs prior peer capabilities. Saving
never fetches missing identities. Capability inspection caches only service IDs;
it does not follow advertised origins or confer authority.

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
Default overwrite is refused atomically. A same-directory 0600 temporary file is
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

```powershell
python -m pytest -q --cov=klab_client --cov-branch --cov-report=json
python tools/check_feature_coverage.py coverage.json --base ad2bc949c167e1c2e370973ce4427fe67cb1cc30 --new-module src/klab_client/handles.py
python -m ruff check src/klab_client/handles.py tests/test_saved_handles.py tools/check_feature_coverage.py --select E9,F63,F7,F82
python -m build
```

New production module requires unexcluded 100% executable line and branch coverage;
changed existing production lines/decisions are checked separately from inherited
gaps. CI applies these gates on Python 3.11/3.12/3.13. Coverage/lint are dev extras.

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

Exact candidate, coverage, matrix, wheel and real-stack results will be recorded
after execution; these instructions are not passing evidence. A Python-process
restart does not establish persistent job retention across Runtime restart or
resumable simulation. No production onboarding, provider accuracy, automatic
migration, bulk export or federation-policy redesign is certified.

## Independent merge handoff

Both branches start at the same SHA. Expected overlaps: additive package exports,
Client facade additions, README/API inventory, dev extras, coverage CI and the
local runner's feature flags. Coverage-check tooling is identical and independently
included; preserve both new-module arguments when combining. Neither feature
depends on the other or changes Java session authority.
