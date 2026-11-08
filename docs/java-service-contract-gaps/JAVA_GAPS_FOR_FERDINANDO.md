# Java engineering handoff for the Python client

## Executive summary

**Question:** what Java changes—or documented existing API usage—let the completed
Python client work against a maintained k.LAB 1.0 stack?

The scientific SDK is `klab-python-client` **0.1.0**, verified at
`161f06f7a01f9dd4b69df130b0044051794e8db5`; its source is unchanged in this handoff.
The historical authentic-DEM investigation used local Modeler **202610301844**,
JDK 21 and **imod generation 2**. This is not Ferdinando's staging environment.
Python implementation/reference acceptance does not establish native acceptance.

The observed blocker is `Context.observe(...).result()` on the original DEM;
downstream `fetch_data` receives no usable field. Unit-safe `export_to_file` needs
native verification. `resolver.encode_dataflow` is intentionally unsupported;
experimental block/replay/worker protocols are not maintained Java commitments.

**First decisions/actions, in order:**

1. [Recover and rerun the null-scanner failure](#numerical-execution), or identify
   the maintained build/usage sequence that already avoids it.
2. [Provide the producing native GeoTIFF fixture](#scientific-data), verify units
   and demonstrate supported export/slice retrieval before designing new APIs.
3. [Specify the Engine/scope/principal recipe](#ownership-and-lifecycle), then
   qualify reconnect, cancellation and owner-only cleanup on that exact tuple.
4. [Choose supported source/revision/event usage](#inputs-and-provenance): distinguish
   existing ADD/commit reads from proposed conditional publication and durable replay.
5. [Decide optional platform contracts](#platform-contracts), not a prerequisite
   to every scientific client workflow.

The authentic elevation path has a historically traced Runtime execution failure.
The TIFF failure has a public producing sequence and retained bytes; its current
official tuple still needs retesting. Other records are source-scoped gaps,
proposals, unexercised native workflows or fixture setup—not 18 Java bugs.
Ferdinando's reported-working `klab.staging.vxii` harness is the best starting point for
finding supported equivalents. Maintainer-reported success is important evidence;
we have inspected its source, not executed it or established sequence equivalence.

## Environment assumptions and reproducible entry point

### Choose the actual maintained tuple first

Pin the four service builds/IDs (Reasoner, Resources, Resolver, Runtime), geospatial
component, worldview/authority versions and principal roles. Use an operator-approved
Engine/Hub certificate profile **or** issued tokens; neither a new login nor refresh
endpoint is presumed. `Client.from_env()` reads `KLAB_RUNTIME_URL/TOKEN` and the
corresponding `KLAB_REASONER_*`, `KLAB_RESOURCES_*`, `KLAB_RESOLVER_*` variables.
Set `KLAB_AGENT_NAME`, service identity pins and trusted CA configuration as needed.
Never commit credentials. The SDK sends configured `Authorization`, scoped
`klab-scope`, and verified originating service information where required. Do not
forward credentials just because another URL is advertised.

Public sequence: initialize/attach the authorized user scope → create an owned
session/context or attach an existing one → install the correct region/focus and
lexical namespace/project → resolve/observe → inspect Job result → read/export.
`initialize_user_scope()` advertises configured peers; it does not grant permission.
Local `close()` is not remote release; attached readers do not acquire disposal rights.
The implemented initialization reads each service's `/public/capabilities` and
`/public/status`, then POSTs `/notifyUserScope` with `services`, `emailAddress`,
`localFederation`; Runtime `/createSession` and scoped `/createContext` follow.
An already initialized Engine deployment may use its existing advertisement instead;
Ferdinando should confirm which sequence is intended for external clients.

From a clean checkout with Python 3.11+ and an isolated environment:

```sh
python -m pip install 'jsonschema>=4.23,<5' './python-client[dev,analysis,tabular,local,events,notebook]' ./python-worker './developer-tools/klab-service-simulator[test]'
python developer-tools/java-gap-governance/govern.py validate
python developer-tools/java-gap-governance/govern.py generate --check
python developer-tools/java-gap-governance/govern.py contracts --mode simulated --gap JAVA-GAP-0001 --output sim-0001.json
python developer-tools/java-gap-governance/native_probe.py --gap JAVA-GAP-0001 --output preflight-0001.json
python developer-tools/java-gap-governance/govern.py contracts --mode real --gap JAVA-GAP-0001 --output real-0001.json
```

Use **unused output names**. Simulator mode runs the packaged SDK against isolated
test fixtures; expect pass, with full output in `sim-0001.pytest.log`. The two last
commands exit **3 / REAL_NOT_RUN**, state what executed and exactly what to supply.
Preflight checks repository custody; real mode is a missing-native-adapter
disposition, **not a Java request or a passing compatibility test**. Neither falls
back to simulation. Substitute any stable ID from the inventory below.

The optional native probe `--connect` accepts a private JSON config with
`approved_read_only:true`, credential-free `base_url`, `expected_service_id`,
`native_build`, and optional `token_env` naming a bearer-token environment variable.
It makes one bounded GET to `/public/capabilities`, refuses redirects/identity drift,
and still does not execute the selected gap. Connection gates fail with exit 4.

Prepared live pytest commands below require explicit `KLAB_FIXTURE_STATE_DIR`,
`scientist.json` and their named extra manifests. Missing inputs fail, never skip.
They are **NOT_RUN here**. Do not start, repair, restart or mutate a retained stack
merely to green this document; obtain disposable-fixture/cleanup approval first.

## Numerical execution

1. **User-visible goal:** observe authentic elevation and retrieve scientifically
   correct cells, rather than a finished Job containing an error.
2. **Python interaction:** after approved client/context and native geometry setup:

   ```python
   from klab_client import ObservationRequest, ObservationOptions
   region = context.observe(ObservationRequest(
       "earth:Terrestrial earth:Region", geometry=native_geometry)).result(120)
   focused = context.within(region)
   job = focused.observe("geography:Elevation in m", options=ObservationOptions(
       namespace="scientific.examples.usgs_virginia", project="scientific.usgs.virginia"))
   elevation = job.result(120)
   first = elevation.fetch_data(range(256), curve=approved_curve)
   rest = elevation.fetch_data(range(256, 400), curve=approved_curve)
   ```

   This is the **target SDK sequence**, not a claim the historical Modeler request
   was made through Python. `native_geometry` and traversal must come from the
   recovered fixture, not an invented EPSG:3857 rectangle.
   Supporting discovery is `inspect_model_candidates(client, ModelCandidateQuery(...))`;
   source checks use `SourceValidation(client).parse(...)` then `validate(...,
   document_version=..., expected_revision=...)`. Neither catalog inspection nor
   successful validation proves executable model admission or numerical computation.
3. **Expected Java behavior:** existing observation/Resolver/point-read APIs should
   produce the selected field. Candidate inspection is not model selection;
   do not use the SDK's deliberately unsupported forced-model option.
   Submission is POST `/api/v1/submit` under the selected Runtime base; sparse
   values use POST `/api/v1/observation/value`, not observation metadata as data.
4. **Actual Java evidence:** **HISTORICAL**, 2026-11-06 02:58–03:11 UTC, build
   202610301844/imod generation 2, Temurin 21.0.12.1+1: Resources registered the DEM; Resolver selected
   `scientific.examples.usgs_virginia.elevation-quantifier` and resource
   `resources.eric.lumsden:staging:usgs:virginia20211229`. Runtime Job 9 returned
   `empty:true`, id −1 and contextualization errors. Reported stack:

   ```text
   NullPointerException: Cannot invoke Storage$DoubleScanner.add(double) because filler is null
     at RasterEncoder.java:183
     via RasterAdapter.encode:102
   ```

   The installed Java reader matched all **400** source cells in isolation; Runtime
   returned none. These are different results. Current-build execution is **NOT_RUN**.
5. **Reproduction:** run the 0001 simulator/preflight commands above. The SDK test
   `developer-tools/klab-service-simulator/tests/test_vertical.py::test_native_async400_and_authentic_recorded_delivery_not_java`
   delivers recorded USGS cells; it does not execute Java. The native 20×20 EPSG:4269 crop,
   oracle, exact request/raw receipts and `NullScannerReproducer.java` remain outside
   Git; the Java reproducer is absent from this checkout. **Portable recovery task:**
   evidence custodian must package those files, hashes, bounds/noData/unit/datum and
   cleanup manifest into a reviewed fixture; Ferdinando supplies its producing build.
   Until then there is no honest clean-checkout end-to-end native reproducer.
6. **Root cause / uncertainty:** installed `ResourcesProvider.contextualizeSynchronous()` bytecode passes null to the
   raster adapter (`aconst_null` offset 180; invocation 207). The remote numeric
   scanner bridge is implicated, not lack of Java numerical capabilities generally.
   Staging `resolve_quality` instead establishes terrestrial region, focus and
   explicit namespace/project; its [staging.vxii.basic elevation model](https://github.com/integratedmodelling/klab.staging.vxii/blob/0de15fbafab988455de87edd7d4581a7c22000ac/src/staging/vxii/basic.kim#L119)
   uses
   `klab.generators.geospatial.terrain()`, not the remote USGS raster resource.
   A passing generator/storage test need not exercise the failing scanner bridge.
   Determine which supported setup and resource path is equivalent before proposing a fix.
7. **Recommended Java-side action:** Resources/geospatial maintainer, owner not yet
   assigned: **fix or identify an existing correction** for the null scanner at this
   boundary, or document the supported remote-resource invocation that uses a valid
   scanner. The historical smallest correction is a real numeric scanner paired
   with the existing serializing data builder, not a Python-computed replacement.
   First compare the exact failing request with staging; do not port terrain
   computation into Python or rewrite the ontology.
8. **Acceptance test:** recovered native fixture must select the same model/resource
   and return all 400 oracle cells with verified axes, coverage/noData, precision and
   metre provenance through Python. Source NAVD88 attribution is not an output GeoKey.
   The simulator transport check must also remain passing; that alone cannot close it.
9. **Traceability:** 0001, 0003, 0011, 0013; RC-P2.1/P2.3/P2.5, RC-P4.1 and CAP-09.
   [Historical requests, hashes and full scanner account](../../notes/python-roadmap-execution/MODELER_REAL_DEM_REMEDIATION_REPORT.md).
   Original input: `USGS_1_n38w080_20211229.tif`; [public retrieval URL](https://prd-tnm.s3.amazonaws.com/StagedProducts/Elevation/1/TIFF/historical/n38w080/USGS_1_n38w080_20211229.tif).
   Original SHA256 `026ec5429ddb1197b46247dd97832e2abe246be112e7133f4fce5029fea50de5`;
   crop `2c9fcd0748b9ef3d8c036a59af5c1419a826ba14262a3a80d089b1d59023fb26`;
   oracle `22ed905702f9e8bed9dc6a98f07ff7762877459c55cb50e581ec1a6fa7519b76`.
   Re-downloading the DEM does not recover the missing producing request/harness.

## Scientific data

1. **User-visible goal:** export/read results whose units, coordinates, masks and
   selected time states remain meaningful in Rasterio/xarray or another consumer.
2. **Python interaction:** `observation.fetch_data(offsets, curve=..., slice=...)`
   for sparse checks; `observation.export_to_file(destination,
   media_type="image/tiff;application=geotiff", max_bytes=budget)` for an advertised
   native GeoTIFF representation. The exact public call sequence is in the
   [native stream workflow](../../python-client/tools/workflows/native_stream_workload.py).
   [Typed slice workflow](../../python-client/tools/workflows/native_scan_slices.py)
   shows `ObservationOptions`, `ScanSlice.initialization()` and `fetch_data` end to end.
3. **Expected Java behavior:** use advertised `/export/{class}/{urn}` representations
   and published `StorageScan.Point` first. Internal Java plans/storage views exist;
   they are not automatically a public block/lease API. New remote reads are needed
   only if existing exports cannot satisfy the agreed window/time workload.
   Resources contextualization/Avro is another existing contract, not proof of
   revision-pinned block access to a committed Runtime observation.
4. **Actual Java evidence:** retained native 5×4 float32 TIFF has values **123.25**,
   EPSG:3857 and transform `(100,0,200000,0,-100,6000400)`, but `units==(None,)` and
   band tags `{}` instead of `m`. Producing services: `a0a013a188fb752907f2d143a61b86f4a9cb0a0b`,
   JDK 21.0.12.1/Windows; geospatial `1.0-20261025.094856-223`.
   Issue #10 also records the original two-observation/export sequence, Python
   candidate `ced45f17691ce6a78b6a489ecca4a319564c69be` and worldview
   `d6c37c0b82afadd004d94bc5311983b66677532c`. This supports a **historically
   reproduced native export failure**, not a current-build verdict. Exact original
   execution timestamp/raw transaction IDs are not retained in the artifact manifest.
5. **Reproduction:** `python -m pytest -q developer-tools/java-gap-governance/tests/test_native_export_capture.py`
   passes by detecting the preserved missing units without rewriting bytes.
   [Fixture](../../developer-tools/java-gap-governance/fixtures/native-geotiff-missing-unit.json),
   TIFF SHA256 `940b21aee3d6eaa7e70c7baef52d29d48d577398b0352ccb0e43347ca37ba2ce`.
   The original producer uses the full public
   [python.fixture project](https://github.com/integratedmodelling/klab-services/blob/ced45f17691ce6a78b6a489ecca4a319564c69be/python-client/tools/fixture_hub.py),
   not an unpublished DEM: constant elevation `100 + 23.25`, the 5×4 geometry
   above, focused `geography:Elevation in m` in namespace `python.fixture`, then
   `export_to_file`. Port that sequence to the current SDK's `job.result()` and
   `context.observe(..., options=ObservationOptions(namespace="python.fixture"))`;
   the old issue snippet's `wait()`/request-options syntax is version-specific.
   Run simulated/preflight modes for 0010/0006/0014. Native candidate:
   `python -m pytest -q -o addopts= -m live python-client/tests/functional/test_native_scan_slices.py`;
   requires approved `scientist.json` plus registered `consolidation:*` fixtures.
   It tests a **synthetic Java constant 234.5 m**, not USGS or general temporal blocks.
6. **Root cause / uncertainty:** unit handling in geospatial `GeodataIO` /
   `Geotools.stateToCoverage` needs transaction verification. Runtime's published
   READ_VALUE accepts a Point; remote block/snapshot authority remains unestablished.
7. **Recommended Java-side action:** geospatial maintainer **reproduce the recovered
   public producing sequence** and identify the official corrected component, if any.
   Storage/Runtime maintainer **document the existing export/slice/mediation recipe**;
   specify a new bounded block contract only for demonstrated uncovered needs.
8. **Acceptance test:** unchanged native numerical/spatial/mask precision plus
   embedded measurement unit, and documented metre→millimetre mediation where
   supported. For blocks, compare the same native fixture's exact cells, descriptors,
   revision/expiry and cancellation against sparse/export oracles; never stamp units
   or fabricate axis/dictionary meaning in Python. Staging `storage_views` and
   `monthly_process` are promising native test sources, not executions here.
9. **Traceability:** 0010/0006/0014, RC-P2.1/P2.2/P2.4/P2.5, CAP-09/CAP-15;
   [geospatial #10](https://github.com/integratedmodelling/klab.component.geospatial/issues/10)
   and [#123](https://github.com/integratedmodelling/klab-services/issues/123) stay open.

## Ownership and lifecycle

1. **User-visible goal:** reconnect or share authorized work without leaking another
   principal's data, replaying Jobs or deleting somebody else's context.
2. **Python interaction:** `Client.from_env()` / supported `connect(...)` credential
   profile → `initialize_user_scope()` → `create_session().create_context()`;
   save `job.to_handle()` / observation handle → a fresh client's `restore_handle(...)`;
   `job.cancel()` requests cancellation, while `job.result(timeout=...)` is only a wait.
   Agent registration uses `AgentExecutions(context, principal_key=...,
   runtime_incarnation=...).create(source, allow_execute=True)` then explicit AMQP control.
3. **Expected Java behavior:** existing Engine/Hub, scope, attach, Job and native
   agent APIs—not new login, stop or retry routes. Broker credentials are separate
   from HTTP credentials. Closing Python detaches locally; only explicit owned
   release disposes remote scopes; a stop acknowledgement is not terminal cleanup.
4. **Actual Java evidence:** historical privacy/restoration/native Java–Qpid slices
   exist, but the full production issuer/role/two-Runtime/restart/disposal matrix is
   **NOT_RUN**. Currentness had an original Java exception; source correction
   [#148](https://github.com/integratedmodelling/klab-services/pull/148) is merged,
   not missing. [#128](https://github.com/integratedmodelling/klab-services/pull/128)
   is also a **merged PR**, not an unresolved issue: it rejects cached Runtime-origin mismatch.
5. **Reproduction:** use per-ID simulator/preflight modes for 0015/0004/0012/0008.
   Native read-only candidate:
   `python -m pytest -q -o addopts= -m live python-client/tests/functional/test_shared_context_read.py`.
   Needs `scientist.json`, `shared-context-read.json`, an existing observation handle,
   expected offsets/unit/text, a separate reader credential environment variable and
   operator-attested native build/principal. It does not test restart or disposal.
6. **Root cause / uncertainty:** no global missing-authentication finding. `ClientKnowledgeGraph`
   currentness/deployed compatibility and Runtime retention/incarnation are separate
   from local handle validation. Existing Java agent lifecycle must not be relabeled absent.
7. **Recommended Java-side action:** Engine/Runtime/operator owners **provide one
   supported initialization/identity recipe and disposable two-principal fixture**;
   **qualify deployed #148**, document Job retention and independently observe native
   agent terminal disposal. Change Java only for failures reproduced by that matrix.
8. **Acceptance test:** authorized current-credential reads succeed; wrong/expired
   credentials and foreign origin/private-parent/disposal operations fail. Reconnect
   creates no replacement Job/session; expiry stays distinct from cancellation;
   owned release/agent disposal is independently observed. Include both Runtime orders
   and cached/noncached paths without weakening ACLs.
9. **Traceability:** 0015/0004/0012/0008; RC-P1.1–P1.4, RC-P3.2/P3.3/P3.5,
   RC-P4.5, CAP-01/02/07/14; [#89](https://github.com/integratedmodelling/klab-services/issues/89),
   [#144](https://github.com/integratedmodelling/klab-services/issues/144),
   [#125](https://github.com/integratedmodelling/klab-services/issues/125).

## Inputs and provenance

1. **User-visible goal:** use an imported input, edit supported source without lost
   updates, and explain which committed inputs produced an observation after reconnect.
2. **Python interaction:** `ResourceImporter(client, context).upload(...)` →
   `receipt.job.result(...)` → `Authoring(client).parse(...)` → explicit
   `create_workspace`, `create_project`, `create_document(..., allow_write=True)`.
   This existing ADD/import flow is exercised by
   [public input workflow](../../developer-tools/klab-service-simulator/tests/test_input_workflow.py).
   `CommitInspector(context).inspect(commit_id)` reads native reports; `fetch_provenance(observation)`
   reads partial lineage. `AcceptedDocuments(..., allow_experimental=True)` conditional
   publication and `read_notifications(..., allow_experimental=True)` durable cursors
   are explicitly **proposed** APIs, not native fallbacks.
3. **Expected Java behavior:** Resources import/schema/ADD/project-lock and Runtime
   GET `/api/v1/commit?id=...` already exist. Native principal-shared project locks
   are not editor revision fences. Temporary commit reports/transient hints do not
   imply durable full history, contiguous revisions or gap-free replay.
4. **Actual Java evidence:** Resources `delete(..., RESOURCE, ...)` lacks a supported
   branch in audited source; `ResourcesKBox.deleteResource` is internal, not an
   authorized remote cleanup API. No destructive test was attempted. Generic
   conditional accepted-source publication and durable lineage/replay remain proposals;
   complete native workflow **NOT_RUN**. Local SHA256 manifests are not Java commits.
5. **Reproduction:** per-ID modes for 0002/0009/0005/0016. Native existing-API candidate:
   `python -m pytest -q -o addopts= -m live python-client/tests/functional/test_commits.py`.
   Needs `scientist.json`, approved `commit-inspection.json` with native build,
   Runtime/context/commit IDs and exact expected asset/link groups. This is read-only
   cache compatibility, not a full-history or publication test. Imported resources
   additionally need explicit disposable/retained-resource custody before upload.
6. **Root cause / uncertainty:** `ResourcesProvider.delete` TODO RESOURCE;
   source admission/lock semantics in `WorkspaceManager`; `DigitalTwinImpl.getCommit`
   uses a source-configured 200-entry/10-minute-after-access cache—not a deployed promise.
7. **Recommended Java-side action:** Resources owner **document supported cleanup or
   retention and accepted-source update/publication usage**. Runtime owner **document
   authoritative commit/snapshot/retention and resync semantics**. Identify an existing
   equivalent before agreeing any simulator-derived CAS or cursor protocol.
8. **Acceptance test:** imported identity/hash matches selected input and recorded
   native lineage; foreign cleanup is denied. Two editors cannot silently overwrite
   an agreed revision. Two readers recover committed state or explicitly resync on
   expiry/gaps; unavailable groups are not converted into empty successful history.
9. **Traceability:** 0002/0009/0005/0016; RC-P4.3/P4.4, RC-P3.1/P3.4,
   CAP-10/11/13/14; [#126](https://github.com/integratedmodelling/klab-services/issues/126),
   [#129](https://github.com/integratedmodelling/klab-services/issues/129).

## Platform contracts

1. **User-visible goal:** optionally compose live twins, contribute a Python worker
   or serialize a plan—without disguising proposed platform behavior as a Java bug.
2. **Python interaction:** [copied composition SDK workflow](../../developer-tools/klab-service-simulator/tests/test_public_copied_composition.py)
   is not live federation. [Worker SDK workflow](../../developer-tools/klab-service-simulator/tests/test_worker_vertical.py)
   uses a separate proposed backend. `client.resolver.encode_dataflow(dataflow)`
   currently raises `UnsupportedOperationError` before HTTP, deliberately.
3. **Expected Java behavior:** only agreed supported operations. `attach_context`
   is available; native Resources contextualization is not automatically a remote
   computation-worker ABI; Resolver service encoding is not portable plan replay.
4. **Actual Java evidence:** `ClientContextScope.connect` and `ServiceContextScope.connect`
   return null. `ResolverClient.encodeDataflow` returns null while `ResolverService.encodeDataflow`
   implements encoding. These are **source-scoped client boundaries**, not claims
   that all federation or encoding is absent. Native worker registration/lease ABI
   and all three complete native workflows are **NOT_RUN** here.
5. **Reproduction:** per-ID modes for 0007/0017/0018. For 0018:
   `python -m pytest -q developer-tools/java-gap-governance/tests/test_encoder_source.py python-client/tests/unit/test_client.py::test_explicit_unsupported_and_unconfigured_methods`.
   This checks source and honest Python refusal, not native encoding. No maintained
   live-composition/worker/encoder producer fixture is currently packaged.
6. **Root cause / uncertainty:** established stubs versus unspecified external
   operation/identity/ABI contracts. Existing attachments and copied snapshots are
   narrower alternatives, not evidence of migration/failover or live subscriptions.
7. **Recommended Java-side action:** Ferdinando/platform owner **choose and document
   the product contract**, supported alternative or intentional non-support. Only
   then request a connect implementation, external encoder transport or worker ABI;
   do not implement the simulator's experimental routes by default.
8. **Acceptance test:** approved two-twin fixture preserves independent ownership,
   revocation and C-only disposal; worker stale leases/cancellation cannot publish
   partial output; encoder matches a maintained exact dataflow oracle. Tests must
   distinguish native results from synthetic/reference behavior and capture cleanup.
9. **Traceability:** 0007/0017/0018, CAP-03/17, RC-P0/P5;
   [#124](https://github.com/integratedmodelling/klab-services/issues/124),
   [#127](https://github.com/integratedmodelling/klab-services/issues/127).

## Questions for Ferdinando

* **Startup/observation:** what exact Engine/Modeler startup, service advertisement,
  authority, model-registration and root/focus sequence does staging use? Please
  provide producing service/component commits and the supported external SDK/DTO sequence.
* **Runnable staging:** can you provide a command invoking `resolve_quality`,
  `storage_views` and `monthly_process`, approved dependencies/resources/rights and
  expected assertions? Source is accessible at
  [0de15fbafab988455de87edd7d4581a7c22000ac](https://github.com/integratedmodelling/klab.staging.vxii/tree/0de15fbafab988455de87edd7d4581a7c22000ac).
  It initializes terrestrial regions, explicit namespace/project, storage curves and
  mm factor 1000, and checks 12 monthly transitions/relative −10 m. Which of these
  are supported through external HTTP clients rather than local Inspector calls?
* **Coverage:** does that maintained scenario also verify snapshots/revisions,
  cancellation, cross-principal ownership and worker execution, or only a subset?
* **Proposals:** for each proposal below, is there an equivalent Java API? Please
  give method/path, DTO, auth/scope and an executable fixture; otherwise approve,
  narrow or reject the proposed capability before implementation.
* **Stability:** which native contracts/build combinations are intended to be stable
  for external Python clients? Which behaviors should remain explicitly unsupported?

## Remaining inventory and reproduction crosswalk

Each stable record occurs once below. Full reproductions retain every original
roadmap/scenario/test link and historical receipt. **Current native execution is
REAL_NOT_RUN for all 18.** Simulator status is SIMULATOR_PASS for 0001–0017 and
SIMULATOR_NOT_IMPLEMENTED for 0018 (a source-only guard passes). A simulator pass
does not prove a Java defect or Java parity.
Priorities express workflow impact: P0 observed execution blocker, P1 native
compatibility/usage decision, P2 broader reference assumptions, P3 optional platform extension.

Engineering classifications are recomputed from today's evidence: **2 historically
reproduced native failures, 4 source-scoped gaps, 6 proposals, 5 native-test cases,
1 environment/fixture case**. The index preserves the older native-status/evidence
axes separately. In particular, 0010's historical unit-defect bytes are not erased;
its producing transaction still needs verification. 0011 is a legacy-fixture/setup
decision, not a request to change current Java reasoning or rewrite ontologies.

|ID|Short name|Classification|Priority|Native evidence|Simulator/public SDK test|Next owner/action|Full reproduction|
|---|---|---|---|---|---|---|---|
|<a id="java-gap-0001"></a>JAVA-GAP-0001|Authentic elevation hits null scanner|REPRODUCED_JAVA_FAILURE|P0|Historical Job 9/stack; current unknown|[400-cell delivery](../../developer-tools/klab-service-simulator/tests/test_vertical.py)|Resources/geospatial: recover fixture; fix or identify correction|[0001](reproductions/JAVA-GAP-0001.md)|
|<a id="java-gap-0010"></a>JAVA-GAP-0010|Verify native GeoTIFF unit loss|REPRODUCED_JAVA_FAILURE|P1|Pinned export report, public producer and native bytes; current unknown|[Artifact check](../../developer-tools/java-gap-governance/tests/test_native_export_capture.py)|Geospatial/operator: port producer, provide official tuple and retest|[0010](reproductions/JAVA-GAP-0010.md)|
|<a id="java-gap-0015"></a>JAVA-GAP-0015|Production identity and permissions|NATIVE_TEST_REQUIRED|P1|Historical slices, not production matrix|[Shared reads](../../developer-tools/klab-service-simulator/tests/test_shared_access.py)|Operator/Engine: provide supported principal/scope fixture|[0015](reproductions/JAVA-GAP-0015.md)|
|<a id="java-gap-0004"></a>JAVA-GAP-0004|Retained Job and restart semantics|NATIVE_TEST_REQUIRED|P1|Completed restoration narrower than continuation|[Recovery](../../developer-tools/klab-service-simulator/tests/test_durable_recovery.py)|Runtime: document retention; approve owned restart fixture|[0004](reproductions/JAVA-GAP-0004.md)|
|<a id="java-gap-0012"></a>JAVA-GAP-0012|Deployed currentness after source fix|NATIVE_TEST_REQUIRED|P1|Merged #148; paired deployment unverified|[Refresh](../../developer-tools/klab-service-simulator/tests/test_partial_refresh.py)|Runtime: exercise corrected paired-build matrix|[0012](reproductions/JAVA-GAP-0012.md)|
|<a id="java-gap-0003"></a>JAVA-GAP-0003|Candidate discovery deployment|NATIVE_TEST_REQUIRED|P1|Old installation lacks advertisement; source API exists|[Candidates](../../developer-tools/klab-service-simulator/tests/test_model_candidates.py)|Resources: provide protocol-1 deployment/visibility fixture|[0003](reproductions/JAVA-GAP-0003.md)|
|<a id="java-gap-0011"></a>JAVA-GAP-0011|Legacy worldview fixture setup|ENVIRONMENT_SETUP|P1|ODO/source negatives and absent authority descriptors|[Validation](../../developer-tools/klab-service-simulator/tests/test_source_validation.py)|Fixture owner: version fixture/providers, preserve old oracle|[0011](reproductions/JAVA-GAP-0011.md)|
|<a id="java-gap-0002"></a>JAVA-GAP-0002|Owned resource cleanup|SOURCE_CONFIRMED_CONTRACT_GAP|P1|Remote delete branch lacks RESOURCE; no destructive probe|[Import](../../developer-tools/klab-service-simulator/tests/test_resource_imports.py)|Resources: document delete/retention or implement scoped cleanup|[0002](reproductions/JAVA-GAP-0002.md)|
|<a id="java-gap-0006"></a>JAVA-GAP-0006|Remote bounded blocks and leases|SOURCE_CONFIRMED_CONTRACT_GAP|P1|Point controller, internal plans; remote lease not established|[Read plans](../../developer-tools/klab-service-simulator/tests/test_read_plans.py)|Storage: show export equivalent before agreeing new transport|[0006](reproductions/JAVA-GAP-0006.md)|
|<a id="java-gap-0014"></a>JAVA-GAP-0014|Scientific mapping and snapshots|CLIENT_CONTRACT_PROPOSAL|P1|Native staging source, unmatched external tuple|[Scientific adapters](../../developer-tools/klab-service-simulator/tests/test_scientific_adapters.py)|Storage/geospatial: provide matching descriptors and oracle|[0014](reproductions/JAVA-GAP-0014.md)|
|<a id="java-gap-0008"></a>JAVA-GAP-0008|Native managed agent acceptance|NATIVE_TEST_REQUIRED|P1|Java lifecycle/wire exists; complete disposal unverified|[Managed behaviors](../../developer-tools/klab-service-simulator/tests/test_managed_behavior_workflow.py)|Runtime/operator: broker/incarnation and disposal fixture|[0008](reproductions/JAVA-GAP-0008.md)|
|<a id="java-gap-0009"></a>JAVA-GAP-0009|Accepted-source revision safety|CLIENT_CONTRACT_PROPOSAL|P1|Native ADD/locks differ from reference CAS publication|[Accepted documents](../../developer-tools/klab-service-simulator/tests/test_accepted_documents.py)|Resources: document supported update/admission semantics|[0009](reproductions/JAVA-GAP-0009.md)|
|<a id="java-gap-0005"></a>JAVA-GAP-0005|Durable event recovery|CLIENT_CONTRACT_PROPOSAL|P1|Transient native hints, no agreed durable cursor|[Notifications](../../developer-tools/klab-service-simulator/tests/test_durable_notifications.py)|Runtime: existing resync usage or agreed replay contract|[0005](reproductions/JAVA-GAP-0005.md)|
|<a id="java-gap-0016"></a>JAVA-GAP-0016|Committed lineage and revisions|CLIENT_CONTRACT_PROPOSAL|P1|Native commit cache exists, not full durable history|[Commit reads](../../developer-tools/klab-service-simulator/tests/test_commits.py)|Runtime: identify snapshot/history/retention authority|[0016](reproductions/JAVA-GAP-0016.md)|
|<a id="java-gap-0013"></a>JAVA-GAP-0013|Finite semantic/admission assumptions|CLIENT_CONTRACT_PROPOSAL|P2|Simulator finite answers are not Java requirements|[SDK vertical workflow](../../developer-tools/klab-service-simulator/tests/test_vertical.py)|Ferdinando: map supported staging admission/selection sequence|[0013](reproductions/JAVA-GAP-0013.md)|
|<a id="java-gap-0007"></a>JAVA-GAP-0007|Live twin composition|SOURCE_CONFIRMED_CONTRACT_GAP|P3|Connect stubs; attach/copy are narrower alternatives|[Copied composition](../../developer-tools/klab-service-simulator/tests/test_public_copied_composition.py)|Platform: choose operation; document equivalent or implement|[0007](reproductions/JAVA-GAP-0007.md)|
|<a id="java-gap-0017"></a>JAVA-GAP-0017|Computation-worker backend|CLIENT_CONTRACT_PROPOSAL|P3|No maintained native computation-worker ABI established|[Worker](../../developer-tools/klab-service-simulator/tests/test_worker_vertical.py)|Platform: select role/ABI and deployment fixture; optional extension|[0017](reproductions/JAVA-GAP-0017.md)|
|<a id="java-gap-0018"></a>JAVA-GAP-0018|Resolver client encoder boundary|SOURCE_CONFIRMED_CONTRACT_GAP|P3|Client null stub; service implementation exists|[Source guard](../../developer-tools/java-gap-governance/tests/test_encoder_source.py)|Resolver: supported transport/fixture or intentional non-support|[0018](reproductions/JAVA-GAP-0018.md)|

## Issue reconciliation

Authenticated read-only review refreshed **2026-11-08 UTC**; owners/labels unchanged.
No unresolved implementation issue is closed by Python/simulator delivery.

|Tracker|Current state|Preserved obligation / disposition|
|---|---|---|
|[#89](https://github.com/integratedmodelling/klab-services/issues/89)|OPEN|Full deployed identity/shared-session matrix; keep open (0015/0004)|
|[#123](https://github.com/integratedmodelling/klab-services/issues/123)|OPEN|Remote scan/block/slice authority; keep open (0006/0014)|
|[#124](https://github.com/integratedmodelling/klab-services/issues/124)|OPEN|Live composition/migration product contract; keep open (0007)|
|[#125](https://github.com/integratedmodelling/klab-services/issues/125)|OPEN|Native managed lifecycle/disposal acceptance; preview-only Python claim is obsolete (0008)|
|[#126](https://github.com/integratedmodelling/klab-services/issues/126)|OPEN|Native durable replay/resync; keep open (0005/0016)|
|[#127](https://github.com/integratedmodelling/klab-services/issues/127)|OPEN|Native worker backend/ABI/deployment; separate experimental worker is delivered (0017)|
|[#129](https://github.com/integratedmodelling/klab-services/issues/129)|OPEN|Supported tuples/release decisions, not blanket Java defects; keep open|
|[#130](https://github.com/integratedmodelling/klab-services/issues/130)|OPEN|PaL operational inputs/oracles/rights; no research replication or closure authorized|
|[#144](https://github.com/integratedmodelling/klab-services/issues/144)|OPEN|Paired-runtime acceptance after merged #148; keep open (0012)|
|[#193](https://github.com/integratedmodelling/klab-services/issues/193)|OPEN|Legacy fixture/authority compatibility, not failed numerical science (0011)|
|[geospatial #10](https://github.com/integratedmodelling/klab.component.geospatial/issues/10)|OPEN|Official unit-preserving native export, unchanged scientific bytes (0010)|
|[#87](https://github.com/integratedmodelling/klab-services/issues/87)|CLOSED|Typed requests delivered; preserve closure|
|[#88](https://github.com/integratedmodelling/klab-services/issues/88)|CLOSED|Saved handles delivered; preserve closure|
|[#91](https://github.com/integratedmodelling/klab-services/issues/91)|CLOSED|Streaming client delivered; component correction remains separate|
|[#142](https://github.com/integratedmodelling/klab-services/issues/142)|CLOSED|Historical HTTP expectation correction, not current missing API|
|[#145](https://github.com/integratedmodelling/klab-services/issues/145)|CLOSED|Historical Java-only test setup correction|
|[#147](https://github.com/integratedmodelling/klab-services/issues/147)|CLOSED|Historical reactor inventory, not proof every failure is a product defect|

PR #128's merged origin-affinity correction is already discussed above; it is not
a missing issue between #127 and #129. Issue authors are not automatically Java owners.

## Local setup incidents and evidence appendix

**RESOLVED_LOCALLY, not open Java API requests:** graceful Modeler shutdown/startup;
accepted imod generation 2 with **26 ontologies**; installed geospatial KAR;
registered authentic DEM resource/model; provisioned Neo4j Spatial; successful
context-specific Resolver selection. Scanner execution failed **after** those gates.
Retain this useful installation for diagnosis; no repair/removal is requested here.
Legacy ODO fixture drift (0011) remains separate and must preserve its old hash and negatives.
That fixture expects ODO hash prefix `496bc663…`, versus `5c020dac…` after the
additive `impliesPredicate` change; its 243-file source and TAXA/CHEM authority
requirements need a versioned fixture decision, not an unsolicited ontology rewrite.

Sources reviewed: complete A+B+C planning guide/acceptance map, final execution
ledger/package reconciliation, all 18 registry/reproduction records, current issues,
Java controller/client/storage/Engine contracts and public packaged SDK call paths.
Java source audit baseline: `9db975b21146093a7962d29e28345a762519befd`.
No live installed OpenAPI/service DTO refresh or Java execution was performed for
this rewrite; maintained DTO/build equivalence is a requested fixture, not assumed.
Source locations: [Runtime controller](../../klab.services.runtime.server/src/main/java/org/integratedmodelling/klab/services/runtime/server/controllers/RuntimeServerController.java),
[ResourcesProvider](../../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/ResourcesProvider.java),
[DigitalTwinImpl](../../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/DigitalTwinImpl.java),
[Resolver client](../../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/ResolverClient.java)
and [service](../../klab.services.resolver/src/main/java/org/integratedmodelling/klab/services/resolver/ResolverService.java).

[Full sanitized prior pass/failure receipts](evidence/consolidation-20261108/receipt-index.json)
preserve simulator/native separation; [live issue metadata](live-issue-review.json)
preserves exact titles/states/owners/labels and body/comment hashes. No old gap ID,
alias, failed receipt or original acceptance obligation has been dropped. Earlier
versions remain in Git history. `gaps.yaml` is the synchronized evidence index, not
a substitute for this engineering handoff. Historical source fixes, including
selected-query [#191](https://github.com/integratedmodelling/klab-services/pull/191),
do not establish all native workflows passed.

## Exit criteria and handoff checklist

- [ ] [Numerical execution](#numerical-execution): identify supported staging/build
  sequence and package the original 400-cell reproducer; fix scanner only if still failing.
- [ ] [Scientific data](#scientific-data): reproduce unit export on an official tuple;
  document existing external slice/export coverage before approving a new block API.
- [ ] [Ownership/lifecycle](#ownership-and-lifecycle): supply maintained Engine/principal
  recipe and approved native reconnect/cancellation/disposal fixture.
- [ ] [Inputs/provenance](#inputs-and-provenance): give supported cleanup, revision,
  commit-history and resync contracts, or explicitly mark narrower supported scope.
- [ ] [Platform contracts](#platform-contracts): accept, narrow or reject optional
  composition/worker/encoding requirements with API and fixture references.

For each decision, record owner/build/fixture and the exact after-change assertion;
retain Python→simulator and Python→native results separately. Only an exact native
retest of the original failure warrants JAVA_FIXED_VERIFIED. Review/delivery remains
in [PR #320](https://github.com/integratedmodelling/klab-services/pull/320); pending
maintainer review is pending, not completion or permission for further Java development.
