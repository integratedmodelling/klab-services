# Contribution review: native indexed-read slices

## Recommendation

Ready for maintainer review, bounded to native slice DTO/read validation.
Author/CLI review under KLAB_CLI_Contribution_Review.md,
not independent human approval. Python-only support for the actual native slice
DTO addresses a demonstrated input-validation/type gap; it does not certify
temporal scientific execution or a remote revision lease.

## Reviewed state

- Integration feature/python-experiment-runner@c10555ebf (PR222).
- Contract12cbd9516 preceded executable ba7773abd; Markdown-only local review updates.
- Reused clean owned core worktree; peer8e785aa7f and categorical2c09eed preserved.
- Existing Java StorageScan.Slice and Scheduler.Event.Type are the authority.

## Capability and boundaries

Scientists can pass immutable ScanSlice metadata or existing dicts to sync/async
indexed reads. INPUT_CHANGE is no longer excluded; invalid native fields/intervals
fail before network access. No event key reconstruction, timestamp conversion,
model selection, scheduler execution, scientific mediation, bulk/window/revision
protocol, dependency or Java production changes. Default initialization unchanged.

## Findings, ordered by consequence

Demonstrated Python contract gap in client.py Context._read_spec: the earlier type
allowlist omitted Java INPUT_CHANGE and did not check native interval/long bounds.
Corrected through shared scan_slices.py, inherited by AsyncContext. No additional
important/blocking source defect demonstrated at this checkpoint. Exact native
fields are copied; nested legacy extension metadata is not deep-snapshotted and
callers must not mutate it in flight. This is documented, not a cross-fetch lease.
Python blank-key checks are conservative; no supplied key is trimmed or normalized.

## Validation actually performed

| Check | Revision/environment | Status | Establishes / limits |
|---|---|---|---|
| focused slice/client/async pytest | ba7773abd sources, Windows Python3.12.7 | Passed:135 | Native types/intervals/long limits, typed/dict parity, exact fields, immutable typed values, zero-HTTP failures and shared sync/async paths; mocked transport not science |
| Ruff E9,F63,F7,F82 and git diff --check | same source | Passed | Syntax/undefined-name/whitespace checks only |
| NativeSliceContractProbe.java | owned accepted Java classes/Temurin21 | Passed | Actual record constructor/JSON four types and positive/negative interval rules, not temporal model execution |
| Fresh wheel/full installed gates | ba7773abd, Windows Python3.12.7 | Passed:3,229/all17gates | Wheel/sdist/install/parity/lint; new module32statements/12branches100%, no gate changes |
| Actual Java initialization read | ba7773abd, fresh owned fixture | Passed |20cells234.5m+3dictreads, exact initialization metadata,23 read requests, zero HTTP invalid controls, cleanup; never temporal transition proof |

First focused collection used the previous installed wheel and could not import the
new symbol; source path made explicit. One test wrongly required a peer-service
header for a direct home-Runtime read; corrected to established route/scope behavior,
not a routing code change. Both failed checks retained separately from later PASS.

## Scientific and lifecycle evidence

Planned explicit initialization read uses independently declared existing Java
scenario234.5m,20cells and ordinary caller; no new scientific fixture or oracle.
Temporal-transition/input-change execution and committed-revision leases NOT_RUN /
NOT_CLAIMED. Owned context/session/service/listener cleanup confirmed; unrelated
Java unchanged. Exact source/installed/raw harness/live bindings, private report
hash and credential absence checked in native-scan-slices-final-validation.json.

## Contributor coordination

No Java or component changes. No component PR9 reopening or publication, no peer
scientific rerun. Broader #123/time/provider/category and other roadmap gates stay open.

## Smallest next actions

Final focused map/public documentation checks and normal PR/final-head CI/eligible
merge, ancestry/source/postmerge confirmation. No further Java or scientific rerun
needed for this unchanged executable. No demonstrated important/blocking defect
remains in this bounded slice; this recommendation is not maintainer acceptance.

## Suggested PR description

Expose a faithful native ScanSlice and shared input validation for sync/async
indexed reads; preserve explicit fields and refuse invalid native intervals before
HTTP. Report source, Java contract and installed/live initialization evidence with
temporal execution/revision limits explicit.
