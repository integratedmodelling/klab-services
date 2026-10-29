# Contribution review follow-up: PR #99 live model association

## Recommendation

**29 October evidence update:** recovered head `80a60edc5` now has passing Linux
Python 3.11/3.12/3.13 and core-only CI. Maven still fails on unchanged Java and
Jenkins reports an error. Fresh hosted IDs differ; Reasoner initialized during
read-only checks, and authenticated component inventory subsequently succeeded
with the same advertisement concern. No new computation or administration was
performed. The recommendation below is unchanged; see the
[operator handoff](../../PR99_DEPLOYMENT_HANDOFF.md) for exact scope and next actions.

**Needs contract/coordination evidence — not ready.** The authorized real pilot
failed at the elevation Job; model/result association was not demonstrated. A
concrete cross-service component-advertisement mismatch was observed, but the
exact Job failure cause is not established. Python safeguards/tests pass. This
assessment follows `KLAB_CLI_Contribution_Review.md` and does not replace a failed
live check with offline success or claim maintainer acceptance.

## Reviewed state

- PR: #99, head `feature/python-resource-inspector`, base
  `feature/python-semantic-explorer` at `69c424a3b`.
- Reviewed follow-up code: `a7d57fc7b85732b60d65ac2452664029dcd6cbd4`.
- Previous F2 checkpoint: `e4580f17fc70622aa36b9c8dd7e6a93cf8737cd2`.
- Milestone/final integration base: `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`.
- Isolated F2 worktree and separate Python 3.12.7 environment. No F3/F4/delivery
  code was imported into this feature branch and no existing branch was reset.
- Included new F2-only pilot/runner/tests, named suite selection, CI coverage,
  documentation and sanitized evidence. SDK production source is unchanged from
  the previous F2 checkpoint.
- User explicitly authorized **one** hosted pilot: one owned session/context, at
  most two observation Jobs for the 5×4 region, readback and owned cleanup; no
  imports, component changes, restarts or other users' objects. That pilot was
  consumed; later operations were read-only capability/diagnostic requests.
- The failed pilot used the initial helper before the subsequent preflight and
  diagnostic guards. Its SDK identity is recorded, but it is not successful live
  acceptance of the reviewed follow-up helper.

## Capability and boundaries

The F2 pilot now tests the missing association without depending on F3 rendering:
inspect an explicit candidate, request a bounded region/field, verify actual
geometry/units/finite data, require an actual successful numerical effect and
read this observation's accepted recorded resolution for the model URN.
Namespace is ranking context, never forced selection. Recorded plan membership
is not relabeled as execution of every branch or durable-root-commit proof.

The helper does no provisioning/retry. It requires explicit authorization, fresh
report output, independently owned objects, and confirmed release. New guards
snapshot bounded component/function advertisements, retain safe Job-status
diagnostics before cleanup, and conservatively require Runtime advertisement of
the model's direct computation service before spending Jobs. This is a pilot
policy, not a claim that dynamic/remote loading is universally unsupported.

## Findings, ordered by consequence

1. **Failed required live acceptance; deployed cause unestablished.** The model
   `staging.vxii.basic.elevation-quantifier` was retrieved and the region Job
   completed, but elevation Job 2 raised `JobFailedError`. No successful field,
   selected-model association or numerical validation is claimed. Both owned
   context and session releases were confirmed. After cleanup, querying the
   original Job returned `MissingAssetError`; its detailed failure reason was
   not recovered. The original helper retained only the exception type. Safe
   pre-cleanup enum/trace-availability diagnostics were added for future pilots.
2. **Observed deployment compatibility concern, not proven root cause.** Resources
   advertises `klab.component.generators`, `klab.component.geospatial` and
   `klab.generators.geospatial.terrain`; Runtime and Resolver advertise only
   `internal.local.service.component` and no terrain function. The model's
   contextualization explicitly references that terrain function. Exact deployed
   binary provenance is still unknown. Advertised `fileHash` values are preserved
   without inventing a hash algorithm/source commit.
3. **Contract uncertainty — native dynamic loading.** Source inspection at the
   pinned base shows `RuntimeService` and `Setting.LOAD_REMOTE_RUNTIME_COMPONENTS`
   support conditional remote component loading/resolution. Therefore absence
   from a capability snapshot alone cannot prove an implementation is impossible
   or identify the Job's failure. An operator must inspect the actual selected
   Runtime/Resolver diagnostics and supported loading/routing configuration.
   Python must not install plugins or broaden settings silently. The current
   pilot's conservative preflight does not certify other loading modes.
4. **External CI findings retained.** The previous PR head's Linux Python
   3.11/3.12/3.13 and core-only jobs passed. Its separate Maven build failed and
   Jenkins reported an error. No Java change was made here; these findings need
   triage/coordination, not an unauthorized Java fix. New-head CI must rerun.

No demonstrated F2 SDK decoding/routing defect was identified from this failed
computation. This does not establish the SDK or the deployment is otherwise correct.

## Validation actually performed

Commands below ran in `python-client` using the isolated `pr99-inspector-312`
Python 3.12.7 environment. `<external>` denotes the approved OpenCode temp root;
certificate arguments are paths only, never credential contents.

| Check / exact command | Revision/environment | Status | Establishes / limits |
| --- | --- | --- | --- |
| `python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov=workflows.resource_inspection --cov=run_resource_inspector_acceptance --cov-branch --cov-report=term --cov-report=json:<external>/pr99-final-coverage.json` | Exact executable content of `a7d57fc7b` | Passed, exit 0; 1,246 tests; existing Rasterio warning | New pilot/CLI and F2 module have 100% unexcluded line/branch coverage |
| `python tools/check_feature_coverage.py <external>/pr99-final-coverage.json --base ad2bc949c167e1c2e370973ce4427fe67cb1cc30` with complete module arguments in `.github/workflows/python-client.yml` | Same code | Passed, exit 0 | Existing inherited/new-module/changed-code gates; no exclusions/threshold reductions |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check` | Same code before commit | Passed, exit 0 each | Static/whitespace gates |
| `python -m pytest tests/functional/test_resource_inspection.py --collect-only -q -o addopts=` | Candidate `a7d57fc7b` | Passed, exit 0; one case collected | Named actual-Java case exists; collection is not acceptance |
| `python tools/run_resource_inspector_acceptance.py --profile examples/hosted-deployment.json --certificate <private-ENGINE-certificate-path> --model staging.vxii.basic.elevation-quantifier --report <external>/pr99-hosted-pilot-20261028.json --allow-owned-work` | F2 SDK `e4580f17f`; initial helper before follow-up guards | **Failed, exit 1**, stage `observe_elevation`, `JobFailedError` | Model retrieval/region completion/owned cleanup observed; no successful numerical/model association |
| Authenticated GET capabilities for Runtime/Resolver/Resources, `connect(..., initialize=False)` | Same F2 SDK; follow-up helper inventory | Passed read-only probes | Actual component/function advertisement mismatch; no new observations or admin changes |
| Original Job status GET after cleanup | Same F2 SDK | Failed with `MissingAssetError` | Detailed original failure no longer available through this scoped path |
| Successful second computation, installed-wheel follow-up, new-head CI | Not performed at review | Not run | No authorization inferred for another pilot; previous checkpoints are not current acceptance |

The first local workflow test run exposed an incomplete test double (missing
activity outcome), corrected before the full passing run. It was not a backend
failure. An initial standalone inventory command lacked the tools import path;
it failed before connecting and was rerun with the explicit repository tools path.

## Scientific and lifecycle evidence

The [sanitized record](../../results/resource-inspector-hosted-pilot-20261028.json)
preserves actual model URN/origin, advertised empty resource/dependency lists,
original Job references, SDK package digest, service IDs, failed outcome, confirmed
cleanup and later capability snapshots. Empty advertisement is not a complete
dependency closure; metadata availability is not executability. It contains no
credential material or arbitrary stack text and makes no independent accuracy claim.

Deterministic tests cover no implicit mutation, two submissions maximum, no retry,
scoped Runtime binding, malformed/missing/oversized chart or component metadata,
label spoofing versus canonical model URNs, wrong activity/observation identity,
missing numerical effect, missing required Runtime service, failure and cleanup
paths, secret-message omission and fresh report publication. Existing SDK tests
cover authorization/malformed-response distinctions. Lower-layer success cannot
replace the failed hosted acceptance.

## Contributor coordination

No Java, component, worldview or shared-service configuration was changed. No
maintainer was assigned or contacted outside the existing PR. Deployment operators
must resolve the execution prerequisite/diagnostic gap or supply an already
compatible owned deployment. Updating the existing component/runtime configuration
requires a separate explicit authorization; the pilot permission did not grant it.
Downstream stacked PRs retain their historical checkpoints and will need deliberate
additive integration of this follow-up; none was silently rebased or merged.

## Smallest next actions

1. Have the selected deployment operator correlate the recorded pilot context/Jobs
   with Runtime/Resolver logs and verify supported generator component loading/
   routing, permissions, archive provenance and exact service builds.
2. Use an already compatible unchanged execution deployment, or obtain separate
   explicit administration authorization if changes are required. Do not infer it
   from the request to finish a Python PR.
3. Obtain authorization for a new bounded pilot after prerequisites are verified;
   pass actual field and recorded model association, confirm cleanup, then update
   this recommendation and current CI evidence. No full four-cell milestone matrix
   is imposed as an unnecessary prerequisite for the F2-only pilot.

## Suggested PR description

F2 adds bounded typed model/resource inspection plus a narrowly scoped live pilot
that connects inspected identifiers to an actual recorded numerical result without
forcing models. 1,246 offline tests and existing coverage/lint gates pass. One real
authorized hosted pilot **failed** at elevation; cleanup was confirmed. Resources
advertises the required generator while Runtime/Resolver do not; exact cause remains
unproven. Safe prerequisite/diagnostic guards and sanitized evidence are included.
Keep this PR draft until compatible execution and model/result association pass.
