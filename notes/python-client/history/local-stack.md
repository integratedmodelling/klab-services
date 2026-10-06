# Historical local stack investigation and startup results

**Historical investigation stage.** Subsequent work repaired the blockers below
and completed the deterministic signed-JWT scientific workflow, lifecycle tests
and fresh-state throughput runs. See [work-log.md](work-log.md) and
[throughput.md](throughput.md) for current evidence and explicit production/provider
limits. This file preserves how the initial setup and defects were discovered.

Date: **5 October 2026**. Checkout: `feature/python-client`, starting from
`490af42d4` (Java server sources remain at baseline
`75bf1f7d29c96ec86789d8e1a0135b0b44d0c8ef`).

**The local stack can be built and started.** All four k.LAB service applications
and the repository's embedded Neo4j support application answered actual HTTP
requests. The Python package retrieved live capabilities, status and health.
This is infrastructure/transport validation, **not completed scientific
acceptance or a computation-throughput benchmark**.

## Environment and concrete setup failures

| Item | Finding / action / outcome |
|---|---|
| Java on PATH | Absent. The existing temporary JDK contained only java.exe, javac.exe and a server directory; Java exited `3221225781` (`0xC0000135`) without output. |
| Complete JDK | Downloaded official Eclipse Temurin 21.0.12.1+1 Windows x64 ZIP into the approved temporary directory and verified its published SHA-256. Extracted separately; no global Java/PATH changes. Java and Maven then ran successfully. |
| Maven | Repository wrapper resolved Maven 3.9.5 from the existing wrapper cache. |
| Reactor dependencies | Targeted compilation of all four servers, their dependencies and graphdb succeeded: 13 reactor entries, 86 seconds. Internal language SNAPSHOT artifacts were available in the local Maven cache. This is not a hermetic dependency lock. |
| Docker | Not on PATH; unnecessary for the attempted stack because support/klab.support.graphdb supplies embedded Neo4j. |
| First startup failure | All four service applications failed bean construction because `static/index.html` was missing. Java compilation does not build the shared Web UI. Spring caught the failure but some background JVM threads remained alive; process liveness was not HTTP readiness. |
| Web UI prerequisite | Built `klab.core.services` with the supported `webui` profile through process-resources. Its pinned Node 24.13.1/npm 11.8.0 toolchain, npm ci, Vue typecheck and Vite production build succeeded in about 28 seconds. No fake index.html or disabled controller was substituted. |
| Second startup | Graphdb and all four service applications exposed HTTP 200 endpoints. Runtime health confirmed Neo4j `2025.10.1`, community edition, database neo4j. |
| Shared local configuration | A shared **isolated** user.home/data tree lets services find each other's generated local client.properties/keys, matching the repository's local peer discovery convention. Personal ~/.klab files were not changed. |

Official ZIP:
`https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip`

Verified SHA-256:
`f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e`.

The portable installation and generated logs/data live under
`C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack`.

## Reproduce compilation and startup

From the repository root, select the portable JDK for the current shell only:

```powershell
$env:JAVA_HOME = 'C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack\jdk\jdk-21.0.12.1+1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -version

.\mvnw.cmd -B -ntp -pl klab.services.resources.server,klab.services.reasoner.server,klab.services.resolver.server,klab.services.runtime.server,support/klab.support.graphdb -am -DskipTests compile
.\mvnw.cmd -B -ntp -pl klab.core.services -Pwebui -DskipTests process-resources
.\mvnw.cmd -B -ntp -pl klab.services.resources.server,klab.services.reasoner.server,klab.services.resolver.server,klab.services.runtime.server,support/klab.support.graphdb dependency:build-classpath "-DincludeScope=runtime" "-Dmdep.outputFile=target/local-stack-classpath.txt"
```

Quote the dotted `-Dmdep.outputFile=...` argument in PowerShell. An initial
unquoted invocation was split into an invalid lifecycle phase; quoting fixed
it. `-DskipTests` here means these were build/setup commands, not Java test passes.

The development launcher in `tools/local_stack.py` builds Java argument files
from those classpaths, preferring the checkout's compiled reactor classes over
installed SNAPSHOT jars. The initial attempts used an equivalent temporary
launcher; the checked-in launcher/probe was subsequently exercised as well.
It requires explicit Java and state paths, binds HTTP to loopback, uses a shared
isolated home, refuses occupied ports, records owned PIDs/logs, and reports startup
failure instead of pretending that a live JVM is a ready service.

From `python-client/`, using the existing verified Python test environment:

```powershell
$python = 'C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311\Scripts\python.exe'
$state = 'C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack'
$java = "$env:JAVA_HOME\bin\java.exe"
& $python .\tools\local_stack.py start --java $java --state-dir $state
& $python .\tools\local_stack.py probe --state-dir $state
& $python .\tools\local_stack.py stop --state-dir $state
```

The state directory's parent must exist. Defaults verified:

| Application | Main class | HTTP base / connector |
|---|---|---|
| Graphdb | org.integratedmodelling.klab.support.graphdb.KlabNeo4jStarter | http://127.0.0.1:8382, Bolt 127.0.0.1:7687 |
| Resources | org.integratedmodelling.resources.server.ResourcesServer | http://127.0.0.1:8092/resources |
| Reasoner | org.integratedmodelling.klab.services.reasoner.ReasonerServer | http://127.0.0.1:8091/reasoner |
| Resolver | org.integratedmodelling.klab.services.resolver.server.ResolverServer | http://127.0.0.1:8093/resolver |
| Runtime | org.integratedmodelling.klab.services.runtime.server.RuntimeServer | http://127.0.0.1:8094/runtime |

The tool's HTTP probe is read-only. It does not register resources, issue
credentials or claim scientific readiness from UP/operational flags. Stop
verifies each recorded JVM's argument-file identity before using taskkill, and
affects only those owned processes. Generated data, logs and keys are retained
outside the checkout for investigation; the validation JVMs are stopped after
the investigation. The process lifecycle utility is currently Windows-only.

## Maintained worldview: configured and loaded, not guessed

Located and cloned the public maintained project
`https://github.com/integratedmodelling/imod.git` at
**`608bef150ced0a109db98a5aad64ba4461beaa54`** into the temporary assets directory.
Its manifest defines the imod worldview and public privileges. It supplies .kwv
ontologies and .obs strategies; its resources/resources.json is empty and it
contains no .kim numerical models or elevation rasters.

Initially Resources had empty workspaces and no worldview; Reasoner answered
HTTP but reported operational=false and worldviewId=null. After configuring the
local project through the existing Resources FILE-project startup configuration
and restarting with the shared isolated home, Resources reported:

* worldviewProvider=true, adoptedWorldview=imod, workspaceNames=[worldview].
* Local administration catalog requests returned **1 PROJECT, 0 MODEL, 0 RESOURCE**.
* Reasoner reported operational=true and a non-null worldviewId.

Add these fields to the generated
`<state>/home/.klab/services/resources/resources.yaml`, preserving its generated
serviceId and other configuration:

```yaml
workspaces:
  worldview:
    - imod
projectConfiguration:
  imod:
    sourceUrl: "file:///C:/Users/lumsd/AppData/Local/Temp/opencode/klab-local-stack/assets/imod"
    served: true
    worldview: true
    privileges:
      public: true
    locallyManaged: true
    authoritative: true
    syncIntervalMinutes: 0
    localPath: "C:/Users/lumsd/AppData/Local/Temp/opencode/klab-local-stack/assets/imod"
    workspaceName: "worldview"
    storageType: "FILE"
```

This is the configuration read by WorkspaceManager.readConfiguration, not an
invented ingestion endpoint. No personal workspaces were modified. The public
project revision was kept pinned while inspecting its contents.

The boot log still contains fundamental-type mapping warnings for this worldview,
despite the service's consistent=true status. Other nonfatal startup diagnostics
include CoreActorLibrary.Agent no-argument instantiation and a Spring MongoDB
monitor trying localhost:27017. Those did **not** prevent the observed service
startup/Neo4j connection. They are not evidence that MongoDB is an essential
dependency for this selected workflow; broader scientific behavior is unverified.

## Supported local authentication: what exists and what was actually tested

* ServiceStartupOptions supports `-cert`, `-certResource`, `-authPackage` and the
  existing `KLAB_LOCAL_AUTHENTICATION_RESPONSE` environment variable.
* An authentication package must originate from the supported authenticated Java
  Engine flow; ServiceInstance reconstructs its non-anonymous user identity.
  The launcher accepts an **explicit** `--auth-package-file` containing that
  existing encoded response. It does not discover, generate or impersonate one.
* Without a valid certificate/package, services start in anonymous offline mode.
  That is precisely what the startup logs showed. The personal ~/.klab directory
  contained properties files, not an available certificate; no certificate or
  authentication response was provisioned for this test.
* The existing browser exchange requires a configured trusted HTTPS hub and the
  authenticated local owner. An anonymously started local service cannot grant
  an arbitrary signed-in browser the missing owner identity.
* Generated `server-key` secrets support the repository's privileged localhost
  administration path. That path was used only for explicit infrastructure
  catalog/semantic diagnostics on these isolated services. Keys were not printed,
  committed or added to the ordinary Python Client authentication contract.

Actual SDK calls using no user credentials fetched all services' public
capabilities/status/health successfully. Actual ordinary session creation,
observable resolution and resource listing returned **HTTP 403**.

An explicit live-suite attempt used the documented public anonymous token and
agent_name=anonymous with the now-running endpoint URLs. The workflow test
reached Runtime and failed at `/createSession` with **AuthorizationError / HTTP
403**. The reference test failed for missing KLAB_ELEVATION_REFERENCE. There were
**2 failures, zero skips**, no created scientific session and no numeric result.
This is a measured authorization boundary, not a missing-URL preflight failure.

Local administration is not a workaround for the missing authenticated identity:
an attempted property-based project import returned job 1, but workspace creation
refused the anonymous user (`ResourcesProvider.createWorkspace`:891–892). The
importer returned null and JobManager.status returned **status:null** rather than
a terminal failure. This is an observed job reporting defect for null outcomes;
the Python Job decoder already rejects such a status as ProtocolError. Startup
FILE-project configuration supplied the public worldview without faking a user.

## Live semantic probes revealed real client/example and server gaps

Privileged **diagnostic** requests to the actual Reasoner gave:

| Input | Actual response | Consequence |
|---|---|---|
| geography:Region | HTTP 200, owl:Nothing, VOID, no error notification | Older example namespace is wrong for this worldview. Corrected the example to earth:Region and added a live-captured regression fixture; Python now raises MissingAssetError for unresolved observables. |
| earth:Region | HTTP 200, earth:Region, OBJECT | Region definition confirmed against this loaded worldview. |
| geography:Elevation in m | HTTP 200, geography:Elevation, NUMBER, **no unit** | String-unit resolution is broken at this server baseline. |
| geography:Elevation in mm | Same semantic result, **no unit** | Cannot honestly demonstrate unit conversion from these resolved DTOs. |

The unit issue is traced to
`klab.services.reasoner/.../internal/SemanticsBuilder.java:291–293`:
`withUnit(String unit)` simply returns `this`. ReasonerService.declare calls that
overload with the parsed unit string. It is a concrete missing server behavior,
not a dependency to install and not something Python may silently reconstruct.
Any server fix should be separate, preserve portable unit definitions/URNs and
be checked with reasoner/serialization tests before repeating the live probes.

## Results, remaining requirements and throughput boundary

Recorded successful checks:

* Targeted server compilation, Web UI build, classpath generation.
* Five real JVM applications answering HTTP on the intended ports.
* Runtime connected to real Neo4j; public Python SDK capabilities/status/health.
* Maintained public worldview loaded through supported startup configuration.
* Actual semantic DTO decoding and captured unresolved-observable response.
* Updated offline Python suite: **53 passed, 2 live tests deselected**.

The next complete scientific run requires:

1. A legitimate authenticated owner/issued credentials and peer user-scope
   advertisement through the supported Engine/deployment flow.
2. A compatible executable elevation model plus a real covering dataset (the
   loaded worldview is not that model/dataset). Alternatively, an explicitly
   designed deterministic local scientific fixture through the normal workflow.
3. Repair and verify the server string-unit builder behavior for the declared
   m/mm invariant, and reconcile worldview warnings with the intended revision.
4. An independently sourced reference to certify numerical correctness.

No successful observation submission/completion/value retrieval was measured.
No scientific throughput, concurrency, large-result bandwidth or sustained-load
numbers are claimed. Infrastructure startup and public HTTP response throughput
would be different measurements; they cannot replace computation throughput.

Evidence outside Git:
`build-servers.log`, `build-webui.log`, `build-classpaths.log`, each service's
console/log files, `sdk-probe-result.json`, `admin-probe-result.json`,
`semantic-probe-result.json`, `unresolved-observable.json`, and
`anonymous-live-acceptance.xml`, under the temporary state directory.
Initial failed-attempt logs were followed by fresh startup logs; their specific
errors and corrections are preserved here. No bulky logs, generated keys, JDK,
worldview clone or databases belong in the contribution.
