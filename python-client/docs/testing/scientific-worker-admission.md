# Isolated N/S worker admission (W1)

This is **not scheduled scientific acceptance**. The existing Windows TEST harness
and unchanged N/S oracles are reused. The new manual-only workflow has no schedule,
downloads, builds, pip installs, deployment edits or secret provisioning. A missing
resource manifest/artifact is BLOCKED_EXTERNAL with nonzero exit, not a green skip.
Bad source/byte/authorization declarations are FAILED. Preflight PASS is only
IMPLEMENTED_OFFLINE; actual local/manual N+S PASS is LOCALLY_ACCEPTED_BOUNDED.
Scheduled_L4 remains NOT_RUN: independent GitHub scheduled-run provenance/authority
and artifacts must be verified before any SCHEDULED_L4_ACCEPTED claim.

## Operator-supplied resource contract

Supply a private JSON resource manifest, schema_version1, with:

- authorization: exact worker label/name, isolated:true, and reference to explicit
  operator permission. This is an attestation, not a permission grant by Python.
- source_sha: approved exact clean checkout commit; python_version: exact installed
  interpreter version; java/java_version: absolute Java21 executable and full
  `java -version` stderr. No Java/component upgrades are performed.
- wheel/components: absolute paths to the reviewed noneditable installed Python
  wheel and existing two-archive generators/geospatial manifest.
- files: map of absolute local file paths to SHA256. Include executable/wheel/
  componentmanifest, componentarchives, coreontology, every loaded classpath JAR/
  compiled class, Runtime testclasses, N/S manifests/oracles and scientific fixture
  worldview/models. Build/source provenance must be supplied by the operator;
  matching a hash does not prove how a binary was built. Keep manifest outsideGit.

Preflight rejects tracked source drift, wrong worker, mismatched bytes, incomplete
classpath/fixture pinning, wrongJava/Python, wheel/install/source mismatch and
inconsistent N/S metadata. No arbitrary service URL or credential is accepted.
Harness creates new signed TEST principals in private per-scenario state.

```console
python python-client/tools/run_scientific_worker.py --resources PRIVATE_RESOURCE_MANIFEST --worker AUTHORIZED_WORKER --output FRESH_EXISTING_PARENT/RUN_ID
python python-client/tools/run_scientific_worker.py --resources PRIVATE_RESOURCE_MANIFEST --worker AUTHORIZED_WORKER --output FRESH_EXISTING_PARENT/ANOTHER_RUN_ID --execute
```

N/S execute serially in separate fresh states. An exclusive local reservation
prevents concurrent harness reuse; no foreign lock is removed. Original harness
port checks/finally cleanup and no-ambiguous-replay behavior remain. Child console
is hidden; output is restricted to the Windows owner/admin/system. Raw logs stay
private; worker-receipt.json contains sanitized identities/statuses/hash references.
Actual scenario receipts retain request/job/plan/result identities. Independent
PID+creation-time absence and listener availability checks follow successful runs.
Failed/unknown cleanup never becomes PASS; no second scenario after a failedfirst.
An operator must quarantine an interrupted worker until independently checked;
forced CI cancellation/worker loss cannot be certified by a later green job.

## One focused provisioning request — not executed by this contribution

2026-11-05 observed: repositorypush=true/admin=false; runner/policy APIs403,
Jenkinsworker metadata403. Defaultbranchdevelop; Pythonintegration is separate.
Existing Actions artifacts are DTO/wire receipts, not the required Java fixture
bundle. Do not reuse these as scientific artifacts or inherit Jenkins deployment
credentials/services (its pipeline can update real containers).

Operator must provide: an authorized isolated WindowsJava21 runner label and
scientific-acceptance environment approval policy; a source/build-attested artifact
bundle at the declared paths; Python noneditable wheel/environment; owner-only temp
directory and permission for the harness's loopback ports; quarantine/cleanup policy
on interruption; explicit default-branch workflow adoption and approved cadence.
`clean:false` preserves provisioned ignored build artifacts, while tracked source
must still be clean and every loaded byte pinned. No guessed runner label/schedule
is supplied. Dispatch only after those decisions. A queued/unavailable job or manual
run is never a scheduled pass. One genuine authorized scheduled N+S run and cleanup
receipts are still required; W1 delivery and periodic acceptance remain separate.
