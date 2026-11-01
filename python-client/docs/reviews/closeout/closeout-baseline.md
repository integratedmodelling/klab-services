# Closeout baseline — 31 October 2026

**Conditional handoff, not accepted closeout.** Follow the newly supplied
[roadmap](../../../../docs/contributor-guides/01_RESEARCH_DRIVEN_PYTHON_CLIENT_ROADMAP.md)
and [closeout spec](../../../../docs/contributor-guides/02_OUTSTANDING_PR_CLOSEOUT_SPEC.md).
The implementing agent's assessment below is not an independent approval.

## Frozen source and remote inventory

- Repository: `https://github.com/integratedmodelling/klab-services.git`.
- Starting delivery HEAD: `e9c1230a62b1d159bb94613f8d6b105124030685`, clean before this pass.
- Local planning worktree: `work/python-client-closeout-20261031`, based on that HEAD.
  Original feature worktrees and published histories are untouched.
- Current `develop`: `d95776f7c6490ba1f2c7e700e7810db06ab80a7b`.
- Current `feature/python-client`: `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`.
- Milestone integration target: `0d17032ca5083cb4a354a1509af12d5d36a52dd9`.
- All open PRs were queried without an author filter: **90, 98, 100, 102 only**.
  All four are open drafts. Their reported CI checks pass; none has a submitted
  approving review. Only #102 has comments (two author evidence/status comments).
- [Machine ledger](pr-ledger.json) records exact heads, bases and dispositions.

Important drift: #90's API-recorded base is `ad2bc949c167e1c2e370973ce4427fe67cb1cc30`,
but its named target now points to `1e20fc478f`. Their merge base is `ad2bc949c1`;
target/head have 38/6 distinct commits. Do not mistake the recorded base for the
current target tip. Its PR diff is 88 files, including Java/security changes.

The Python stack is linear: integration -> #98 (11 commits) -> #100 (10) -> #102
(15). Their feature deltas are 13, 14 and 37 files respectively. `develop` is four
commits beyond the previously integrated `f9b8879dd`; those new commits concern
component audits, observer-scope identity, semantic building and process writers.

The federation fix `16e1be908` is an ancestor of both current develop and delivery.
`git log HEAD..244883d7b` returns only #90's merge commit: its constituent commits
are already inherited by the later delivery history. This suggests overlap, not
maintainer-approved supersession. #90 cannot be closed as superseded by inference.

Read-only merge-tree calculations succeeded:

| Hypothetical combination | Result tree (not an accepted/tested commit) |
| --- | --- |
| Current Python-client target + #90 | `49fc9cf1857568930daaf99eacfcef2e4ee79fd6` |
| Delivery HEAD + current develop | `459955ed424e179aaf07157ef9bfe6aa89868f15` |

No merge commit was created, no branch retargeted and no source integration made.
Textual compatibility does not establish security, compilation or science.

## Current environment: GET-only observations

Public status/capability GET requests returned 200 for all four hosted and all
four existing local scientific services; all report available/operational.

| Service | Hosted ID | Local ID / existing listener PID |
| --- | --- | --- |
| Reasoner | `c929ee54-15e1-4e67-aa66-fc47b9e580e2` | `570b4341-cd02-4e27-a77f-dc35352ffc0d` / 40900 |
| Resources | `d07d43fe-2a4a-46d0-a46b-c0bbe1b6e6d6` | `4fa96242-5fa1-4647-ba24-cdc8b29e7549` / 37600 |
| Resolver | `ba9757a1-6fe1-4b5d-be3d-3fda7e279431` | `85406374-4413-4503-a651-054b88c3f7d0` / 42988 |
| Runtime | `f57bb519-19c9-4dbe-a0f5-671b665096ae` | `100bf613-81b4-4347-8194-aec59a615959` / 43696 |

GraphDB's existing PID 46944 still listens on 8382/7687. Other Java processes
were observed but not touched. No services were started, stopped or reconfigured.
Both Reasoners advertise worldview ID `_uu_wv:5017406921366017`; this is **not**
an IMOD source-commit or ODO-version attestation. Both Resources advertise semantic
search; this does not attest a newly configured two-user fixture. Runtime advertises
only `component.jar` export schemata on both deployments. Hosted Resources advertises
`geospatial.io`, not proof of Runtime observation export support.

The existing local JVM executable is Eclipse Adoptium 21.0.12.1+1-LTS, SHA256
`82051fdab26319d77d20cc0065045d05ec00b3e3d05f44935d7c06b96b621d55`.
**Running service source/binary commits, current worldview source revision, Hub
binary, component archive checksums and a compatible two-principal deployment:
UNATTESTED.** Public Maven/version descriptors and old build records cannot fill
these gaps. The previously restored 202610301844 build is historical provenance,
not newly verified binary attribution.

## Authority and work limits

This pass permits local planning/docs, isolated offline tests and read-only
inspection. Historical computation allowances have been consumed; no new one was
inferred from the closeout spec. No submissions, remote scope deletion, user/token
administration, project import, server restart, Java/ontology/component changes,
PR publication/approval/merge/closure or supersession decision occurred.

The user's later chat separately authorizes a plain-language portfolio update
after this wrap-up. That is not authorization to publish/change the PR stack or
start PaL experiments. No credentials were read into or copied into these reports.

## Commands, evidence and checks

Read all supplied 01/02 documents (172/101 lines) and the full referenced 03 PaL
spec (172 lines), repository instructions, contributor review, both planning
guides and historical roadmap, milestone, contracts/public API/functional/analysis
contracts and applicable current/historical review reports before source edits.

Metadata commands: `git remote -v`, `git branch -vv`, `git worktree list --porcelain`,
`git ls-remote`, explicit `git fetch` of selected refs, `gh pr list --state open
--limit 100`, `gh pr view --json ...`, REST branch-ref/pull checks, `git merge-base`,
`git rev-list`, `git log`, feature-specific `git diff`, and `git merge-tree --write-tree`.
No failure-ignore option or remote write was used.

Fresh Windows/Python 3.12.7 checks on unchanged executable content:

- Existing workflow's full unit/integration + branch coverage invocation:
  **1,599 passed**, one existing Rasterio warning, 56.61s; no functional tests ran.
- Exact existing feature-coverage invocation from `.github/workflows/python-client.yml`:
  **passed**, including all new-module and changed-code gates, without exclusions.
- `python -m ruff check src tests examples tools --select E9,F63,F7,F82`: passed.
- `python -m build --outdir <external>/build`: wheel and sdist built successfully.
- Fresh core-only venv installed that wheel; 27 SDK modules match the checkout,
  scientific dependencies absent, handle/local-client smoke passed without HTTP.
  SDK digest `4f4172f267f656425a15094c5695624ea7b8f9528c44683d5cda4b1b11f33045`;
  wheel SHA256 `06c390227601a403c26cf63c1ff4a56c9ad7e6d3d598bca76d89a14125cb768a`.
- Supplied 01/02 files copied byte-exactly; all 49 unique catalog entries retained
  (19 research, 19 capability, 11 project). Original historical Part II unchanged.

Raw JSON, JUnit, coverage, build and install logs remain in the external private
`closeout-audit-20261031-1718` evidence folder. Sanitized summaries and hashes are
kept here; arbitrary server payloads, tokens and machine-state folders are not.
Historical live runs remain attributed to their original candidates. This pass
does not rerun the Java strict/native/live gates or certify either merge tree.
