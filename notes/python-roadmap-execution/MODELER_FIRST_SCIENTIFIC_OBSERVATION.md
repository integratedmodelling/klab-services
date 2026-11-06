# Modeler — first scientific observation attempt

Historical reference copy for the6November A+B audit. Home-directory paths are
projected as `$HOME`; original report remains unchanged in the remediation checkout.

**Final status: `MODEL_NOT_AVAILABLE`**

Inspection: November 5, 2026 local; **2026-11-06 01:12:29–01:15:26 UTC**. The completed `MODELER_SERVICE_REPAIR_REPORT.md` was read first. No worldview repair, service restart or configuration change was repeated.

## 1. Executive result

The repaired semantic baseline remains healthy, but **this registered local service stack cannot yet demonstrate the requested scientific computation**. `geography:Elevation` is recognized; however, authenticated Resources discovery returns:

- **0 model namespaces**.
- **0 models**, including an independent supported full model-catalog query returning HTTP 200 / `[]`.
- **0 registered resources**.
- **0 registered resource adapters**.

The only registered project is **`imod`**: 26 ontologies and two observation-strategy documents, but no model namespaces. There is consequently **no registered alternative scientific model to select either**. Generic native Runtime functions and a semantic worldview are not a substitute for an admitted model and its scientific data.

No temporary context or scientific Job was created: submitting an elevation observation with no discovered model/data would not establish authentic computation. No synthetic terrain generator, literal fixture, unregistered project or guessed resource was substituted. Resolver/Runtime execution is **NOT_RUN**, not failed. There are no numerical results or scientific-correctness claims.

This conclusion covers the inspected registered catalogs of the four requested local services. It is **not** a claim that no elevation model exists anywhere in the k.LAB ecosystem or in an uninspected remote federation.

## 2. Exact service, distribution and worldview identities

All four services retain their repaired process creation identities, parent **Modeler PID 54460**, Windows owner context and selected **develop / `1.0.0-SNAPSHOT` / build `202610301844`**. Working directories remain beneath:

`$HOME/.klab/distribution/klab/1.0.0-SNAPSHOT/develop/202610301844/klab.server.<service>`

| Service | PID | Service ID | Endpoint | Baseline/final state |
|---|---:|---|---|---|
| Reasoner | 27844 | `570b4341-cd02-4e27-a77f-dc35352ffc0d` | `http://127.0.0.1:8091/reasoner` | Operational; not busy |
| Resources | 48596 | `4fa96242-5fa1-4647-ba24-cdc8b29e7549` | `http://127.0.0.1:8092/resources` | Operational; not busy |
| Resolver | 53144 | `85406374-4413-4503-a651-054b88c3f7d0` | `http://127.0.0.1:8093/resolver` | Operational; not busy |
| Runtime | 14188 | `100bf613-81b4-4347-8194-aec59a615959` | `http://127.0.0.1:8094/runtime` | Operational; not busy |

All four initial status/capabilities/health checks returned HTTP 200; health UP, available/connected true. Final status checks again returned HTTP 200 and operational true.

Reasoner semantic baseline:

```text
worldviewUrn=imod
worldviewId=_uu_wv:721872336085100
acceptedGeneration=2
inputGeneration=4
semantic.validation=accepted:2:ready
beanStatus=VALID
logicalStatus=VALID
semantic.validation.blocked=false
consistent=true
knowledgeRevision=2
semanticSessionId=7b5853d5-0f2d-4a67-8a05-f9f3b61e8ae3
semanticContentFingerprint=semantic-content-v2:65c8bb7ff248c915de0d2e6ef1db2b81df8514352a4720cea305979a255d2b02
```

Resources remains `worldviewProvider=true`, `adoptedWorldview=imod`, `workspaceNames=[local]`, `semanticSearchCapable=true`. The six previously documented semantic warnings remain; there is no evidence here that they caused the empty model catalog. The immediate prerequisite gap is absent registered models/data, not a relapse of Reasoner readiness.

## 3. Observable versus executable model

Requested observable: **`geography:Elevation`**.

A fresh documented semantic query at **01:15:26 UTC** returned HTTP 200, exact URN `geography:Elevation`, namespace `geography`, name `Elevation`, types `QUALITY`, `EXTENSIVE`, `QUANTIFIABLE`, `OBSERVABLE`, `LENGTH`, and empty returned notifications.

That result identifies a concept, **not a measured elevation or a scientific model**.

| Required stage | Evidence/result |
|---|---|
| Recognized scientific concept | **PASS:** fresh Reasoner lookup of `geography:Elevation` |
| Available calculation/model definition | **NOT_AVAILABLE:** zero registered MODEL/NAMESPACE entries |
| Model applicable to a selected context | **NOT_RUN:** no model exists in the inspected catalog to qualify |
| Resolvable model/dependencies | **NOT_RUN:** no selected model or registered data resource |
| Executable model/Runtime component binding | **NOT_ESTABLISHED:** no selected binding; no advertised data adapters |
| Numerical observation | **NOT_PRODUCED:** no scientific submission |

No fallback observable was selected because the **entire registered model inventory**, not merely an elevation-name search, is empty.

## 4. Model/resource discovery evidence and permissions

Requests used the corresponding loopback service's supported native local-owner authentication (`server-key` plus its documented anonymous-token transport convention). Secret values were read only in memory, never printed, copied or changed. Resources advertised **READ, CONNECT, CREATE, DELETE, UPDATE, UPDATE_METADATA, ADMINISTER** for this principal. Empty results were therefore not produced by an anonymous/no-scope request; registered project metadata was successfully returned to the same principal.

### Authenticated Resources registry results

| GET path beneath `/resources` | HTTP | Entries | Meaning |
|---|---:|---:|---|
| `/api/v1/list/WORKSPACE` | 200 | 1 | `local` |
| `/api/v1/list/PROJECT` | 200 | 1 | `imod`; 26 ontologies, 2 strategy documents, **0 namespaces**, 0 behaviors |
| `/api/v1/list/NAMESPACE` | 200 | **0** | No registered model namespace |
| `/api/v1/list/MODEL` | 200 | **0** | No registered model, elevation or otherwise |
| `/api/v1/list/RESOURCE` | 200 | **0** | No registered scientific resource |
| `/api/v1/list/RESOURCE_ADAPTER` | 200 | **0** | No registered resource adapter |

The only registered project's existing `src` tree also contains **zero `.kim` files**, corroborating the live inventory. Project identity remains clean Git revision `1328000fff63a1951465b1f7b130c0af15264c95`.

### Independent supported model-catalog query

The installed live OpenAPI advertises the generic asset-query route. This exact read-only query returned **HTTP 200 / `[]`**:

```http
POST http://127.0.0.1:8092/resources/api/v1/query/MODEL?infoClass=org.integratedmodelling.klab.api.lang.kim.KimModel
Content-Type: application/json

{
  "@CLASS": "org.integratedmodelling.klab.api.collections.impl.ParametersImpl",
  "delegate": {},
  "unnamedKeys": []
}
```

This enumerates the authorized complete local model catalog; it is not represented as a context-specific semantic-selection result. Zero models leaves no candidate for any observable. No geometry or scientific context is required to enumerate this empty catalog.

The newer repository source also describes `/api/v1/models/candidates`, but **that route is not advertised by this installed build's live OpenAPI**. It was not invoked or treated as a reason to upgrade. Actual installed `WorkspaceManager.list(Class)` bytecode was checked with read-only `javap`: MODEL listing enumerates namespace statements of `KimModel` type; NAMESPACE listing calls `getNamespaces()`. The empty MODEL result is not being mistaken for an intentionally unsupported class-list stub.

### Adapters and execution components

Resources, Resolver and Runtime each advertise only `internal.local.service.component` (`BUILT_IN`, version `1.0.0-alpha1`) with **zero adapters**. Their built-in service-function counts are respectively 13, 9 and 10. Runtime libraries include `core`, `component`, `klab.data` and `klab.tests.fixtures`; these are generic execution/test facilities, **not advertised elevation models or real DEM datasets**. No test-fixture function was executed as a substitute.

Reasoner additionally advertises its normal authority dependencies (`klab.authority.land`, `pubchem`, `taxa`, `un`, version `1.0.0-SNAPSHOT`). These support semantic authorities, not an elevation data/model binding. No component was installed or activated by this experiment.

These are **advertised/registered capability observations**, not a claim that no adapter implementation class can exist anywhere in a JAR. Without a registered model and usable data binding, compiled generic functions alone do not establish scientific executability.

### Existing files that were not silently promoted to registered models

The sibling disk project `klab.staging.vxii` is **not returned by the registered project/namespace/model lists**. Two source examples were inspected read-only to distinguish possible leads from current execution capability:

- `src/staging/vxii/basic.kim:133–138` explicitly describes its elevation model as **“Fake elevation … using fractals”**, invoking `klab.generators.geospatial.terrain()`. It is both unregistered and synthetic; it was excluded.
- `src/staging/vxii/dependencies.kim:21–29` contains a pre-contextualized WCS example referencing `im-data-global-geography__elevation-global-90m`. It is **not a currently registered resource/model**, a verified accessible dataset or an independent reference. Its remote availability, rights and Runtime adapter compatibility were not assumed or tested by fabricating a resource submission.

Registering that or another project would change Resources configuration, and installing adapters would change the current installation. Neither is within this test's safety boundaries. No such operation was performed.

## 5. Geographic and temporal context

**Not selected / not created.** There is no discovered executable model from which to establish supported coverage, CRS, grid size, time requirements or dependency permissions.

| Parameter | Actual value |
|---|---|
| Requested observable | `geography:Elevation` |
| Selected model/resource IDs | None |
| Geographic extent / CRS | Not selected |
| Grid resolution / dimensions / orientation | Not selected |
| Temporal context | Not selected |
| Actual measurement units | None produced; the concept's LENGTH type is not a measured quantity |
| Model/data-specific permissions | Not established because no model/data binding exists |

No large request was issued, and no arbitrary bounding box or reference value was invented merely to obtain a Job ID.

## 6. Resolver and Runtime outcomes

| Requested evidence | Actual outcome |
|---|---|
| Temporary context creation | **NOT_RUN** |
| Model selection | **No candidate available** |
| Resolver request/outcome/dataflow | **NOT_RUN / none** |
| Runtime Job identity/terminal state | **NOT_CREATED / not applicable** |
| Produced observation identity | **None** |
| Execution activities/provenance | **None produced** |
| Numerical result retrieval | **NOT_RUN**, no scientific output exists |

There is no failed or ambiguous submission to retry. No Resolver or Runtime defect can be inferred from a model that was never found or submitted. Their healthy lifecycle flags do not supply the missing model/data catalog.

## 7. Scientific values and independent verification

**No numerical values were produced.** Precision, measured units, missing-value flags, valid zero, coordinates, grid dimensions/orientation, numerical provenance and agreement with an independent reference are therefore **not applicable / unverified**.

The zero inventory counts above are **not elevation values**, zero-filled grids or no-data samples. No Python numerical computation, Java-output correction, DEM synthesis or invented expected elevation was used.

`COMPUTATION_EXECUTED_UNVERIFIED` would require an actual successful computation; that did not occur. `SCIENTIFIC_OBSERVATION_VERIFIED` is not warranted.

## 8. Responsible prerequisite gap and verification limits

**Confirmed gap:** the local Resources catalog contains semantic worldview knowledge but no registered scientific model namespace/model or data resource. No genuine registered fallback exists. Runtime also advertises no data adapters, so no applicable execution chain is demonstrated.

This is a model/data provisioning gap, **not a failed semantic lookup, authorization rejection, Resolver failure or Runtime computation failure**. The requested local pipeline cannot advance from meaning to calculation under the current inventories and no-change boundaries.

**Not established:** remote federation model availability, real WCS dataset access/rights, compatibility of unregistered staging models, model-specific Runtime bindings, independent scientific accuracy. A remote service name advertised during authentication is not a model-discovery or execution receipt. This report makes no global absence claim about remote catalogs.

## 9. Cleanup and preservation

**No test context, session, Job or observation was created**, so no cancellation/release endpoint was called. This avoids releasing unrelated user state. Native owner-visible Runtime `/api/v1/contexts` returned HTTP 200 / `[]` before and after; that is scoped cleanup evidence, not a global all-users inventory.

All four service PIDs, parents and creation identities remain unchanged. Final states are operational and not busy. Reasoner retains accepted generation/knowledge revision 2 and the same `imod` identity/fingerprint. No service, Modeler or auxiliary process was stopped, restarted or launched.

Configuration SHA256 values are identical before/after:

| File | SHA256 |
|---|---|
| Resources YAML | `e68d82190696a42e75caa7215c04f23ae4c1f6823bfe86f7e972748a108e3f64` |
| Reasoner YAML | `913d1f23b271a22bb0f72a5d73b16fd51553af2201144bfed1f08ab416ed06b5` |
| Resolver YAML | `6f50523ad20ee2a46ac6e02b5106c76d57e74000deada71e691392a8d58b4cab` |
| Runtime YAML | `62757eee8f634031be7ecb9404ec02591495eafb2faab3585d8685736c2a6151` |
| `engine.properties` | `890d9933dadd6e54d86c82e026f94f3dbaf2e2d569f0c134d08ca7a0e2cfbbfb` |

`imod` remains Git-clean. Only this requested report was authored. Worldview/model source, data, credentials, selected build, components, Maven dependencies, independent Python environments and pre-existing unrelated files were preserved. No GitHub PR or power action occurred.

## 10. Commands, contracts and evidence references

- Live native-authenticated GETs: each service's `/public/status`, `/public/capabilities`, `/public/health`; Resources `/v3/api-docs` and list routes above; Runtime `/api/v1/contexts`.
- Read-only semantic query: POST `/reasoner/api/v1/resolve/concept`, `text/plain`, body `geography:Elevation`.
- Read-only complete model query: exact method/path/body in section 4.
- Process identity: `psutil.Process(pid).ppid()/create_time()/cwd()`; configuration `hashlib.sha256(path.read_bytes()).hexdigest()`; project `git --no-optional-locks -C <imod> rev-parse HEAD` and `status --short`.
- Installed list contract: Temurin `javap -p -c -classpath <active Resources directory>/klab.services.resources-1.0.0-SNAPSHOT.jar org.integratedmodelling.klab.services.resources.storage.WorkspaceManager`, exit 0. No Java service was launched by this inspection.

Matching repository source is explanatory where not independently bytecode-matched:

- `klab.core.api/src/main/java/org/integratedmodelling/klab/api/knowledge/KlabAsset.java:85–111`: MODEL maps to KimModel, NAMESPACE/RESOURCE/RESOURCE_ADAPTER contracts.
- `klab.services.resources.server/src/main/java/org/integratedmodelling/resources/server/controllers/ResourceCRUDController.java:98–111`: authenticated generic asset listing.
- `klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/storage/WorkspaceManager.java:220–244`: namespace/model enumeration, corroborated in the installed JAR.
- `klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/ResourcesProvider.java:1391–1430`: Resources/model/adapter list dispatch; `:1914–1935`: generic empty-query listing contract.
- `klab.core.services/src/main/java/org/integratedmodelling/klab/services/application/controllers/KlabServiceController.java:200–213`: supported query method and information-class parameter.
- `klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/runtime/extension/Extensions.java:126–143`: advertised component adapter/library/function fields.
- Completed semantic repair, exact immutable worldview bytes and prior accepted-concept relationships: `MODELER_SERVICE_REPAIR_REPORT.md`.

## 11. Recommended next experiment

Under **separate provisioning authorization**, identify and register one vetted **real DEM model and resource** compatible with this exact `imod` revision/build, and verify the required adapter and Runtime implementation are actually available. Supply the model/resource URNs, data rights, spatial coverage, CRS/units/no-data rules and a genuine independently checkable reference. Do not use the staged fractal model as real terrain.

Then repeat this test on a small tile/grid supported by that admitted source, create one temporary context, and retain the Resolver dataflow, Runtime terminal Job/result, values and provenance before releasing only the test state. Until such a model/data chain is admitted, the repaired Modeler is **semantically ready but scientific observation execution remains unverified**.
