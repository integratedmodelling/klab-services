# PR #99 — read-only deployment investigation, 29 October 2026

## Decision

PR #99 remains **Needs contract/coordination evidence**, draft. Its Python CI is
green; real numerical/model association is not demonstrated. No new computation
or administration was authorized or performed during this investigation.

## Recovered state and CI

Recovered clean branch `feature/python-resource-inspector` at
`80a60edc58221b0c8d80b9786327cc5f5c85f71c`; remote PR head matches.
Both [push Python CI](https://github.com/integratedmodelling/klab-services/actions/runs/40726548623)
and [PR Python CI](https://github.com/integratedmodelling/klab-services/actions/runs/40726562390)
passed Linux 3.11/3.12/3.13, coverage/lint/builds and core-only checks for that head.
This resolves the earlier handoff's pending Python CI item, not scientific acceptance.

This continuation changes documentation/evidence only. `git diff --check` and
JSON parsing/profile-endpoint validation passed. No Python test suite was rerun;
the CI results above apply to the recovered executable content, which is unchanged.

[Maven CI](https://github.com/integratedmodelling/klab-services/actions/runs/40726548650)
still fails at `LanguageAdapter.java:1234`: non-exhaustive switch over
`SemanticSyntax.Type`. Java source is unchanged by this PR. The exact dependency
enum/build mismatch has not been established; do not guess a missing semantic
case or patch Java under this Python milestone. Jenkins still reports an error;
its underlying cause was not inspected here.

## Fresh hosted observations

The [timestamped public metadata snapshot](results/resource-inspector-reboot-check-20261029.json)
uses the exact URLs in `examples/hosted-deployment.json`. An initial authenticated
inventory attempt failed during Runtime observation. Public endpoints subsequently
responded; Reasoner initially reported not operational, null worldview and revision
0, then initialized during the investigation. The saved later status reports
operational/accepted generation 1. These observations are not an atomic snapshot
and do not establish the cause of the initial transport failure.

Fresh authenticated inventory with `connect(..., initialize=False)` then succeeded:

| Selected service | Current observed ID | Terrain advertised | Component summary |
| --- | --- | --- | --- |
| Resources | `d07d43fe-2a4a-46d0-a46b-c0bbe1b6e6d6` | Yes | Internal, generators, geospatial, authority components |
| Resolver | `ba9757a1-6fe1-4b5d-be3d-3fda7e279431` | No | Internal only |
| Runtime | `f57bb519-19c9-4dbe-a0f5-671b665096ae` | No | Internal only |

These IDs differ from the failed 28 October pilot. Do not associate yesterday's
Jobs or diagnostic absence with today's service identities or silently reuse old
readiness evidence. An ID change does not independently identify a binary version
or prove a particular restart/deployment event.

Earlier ad-hoc public probes accidentally used `/main` for Reasoner/Resolver;
their 403 responses were not from the selected profile endpoints and must not be
interpreted as an authentication regression. Exact-profile endpoint checks pass.

## Source contract: do not infer the wrong fix

At the pinned Java source, `Setting.java:275–279` defines
`LOAD_REMOTE_RUNTIME_COMPONENTS` with default **true**. That is the source default,
not a read of the deployed service configuration.

`RuntimeService.java:2171–2264` first checks the Runtime function catalog, otherwise
resolves `SERVICE_IMPLEMENTATION` via the selected Resources service, ingests the
resolved contribution according to the setting, then verifies the implementation
was installed. Later ingestion also calls component loading under that setting.
Therefore missing preloaded Runtime advertisement **alone** is not proof that a
model cannot execute and is not a sufficient reason to toggle settings or install
plugins. This PR's prerequisite guard is explicitly conservative and does not
claim to validate dynamic/remote loading.

`KlabAdminController.java:176–187` exposes settings only for callers authorized to
ADMINISTER and returns the entire settings map. We did not call it, dump settings,
attempt privilege escalation or request any setting change. There is no verified
narrow, safe public interface here for obtaining the deployed loader setting.
No remote host/log access or deployment-operator identity has been established.

## Smallest operator investigation (not performed automatically)

1. **Correlate the original failed pilot**, using the committed
   [28 October evidence](results/resource-inspector-hosted-pilot-20261028.json):
   session `a8b010433fc240bd9a2014e901b0b4e5`, context suffix `1coobkgbe53`, region
   Job 1 and elevation Job 2. The old Runtime ID was
   `5641e5b2-944d-4ed4-8fb0-fc464a718d0f`. Retrieve archived Runtime/Resolver logs
   for that instance; current-instance logs may not contain the failure. Share
   only relevant sanitized errors, not credentials, whole configuration or other
   users' data. Cleanup was confirmed and original scoped Job lookup no longer
   returned it.
2. **Verify the actual unchanged build tuple:** service build/commit identities,
   JDK, adopted worldview revision, and existing generator archive provenance/hash.
   Advertised SNAPSHOT versions or `fileHash` strings do not establish source SHA.
3. **Check the selected Runtime's supported resolution/loading path:** its actual
   loader setting, which Resources service its scope uses, resolution of
   `klab.generators.geospatial.terrain` as `SERVICE_IMPLEMENTATION`, relevant
   archive export/download permissions and whether installation/implementation
   lookup succeeded. Do not infer the setting from the source default or assume
   a missing component is the only possible cause.
4. **Report the observed failure and a proposed minimal correction.** Any shared
   configuration/import/component/restart action needs separate explicit
   authorization. Do not make Java/component/worldview source changes to satisfy
   this milestone. An already compatible owned deployment is an alternative.
5. **Only then seek authorization for a new bounded F2 pilot.** No such pilot was
   run on 29 October. It must validate the actual field and recorded model URN,
   confirm owned cleanup and refresh the contribution-guide recommendation.

This is a prepared handoff, not a message sent to an operator or an issued admin
instruction. It does not claim the deployment blocker is resolved.
