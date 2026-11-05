# Native indexed-read source identity bounds

Base `13b723aa3c361e011031e76e5a7730ae0caf28c1` (#228). Independent wire-domain
correction while CODELIST #229 CI runs; same owned worktree/separate branch from
accepted integration. Preserve reviewed #229 candidate/evidence and all unrelated work.

Demonstrated Python defect: both facades send observation ID `2**63` as sourceId
to mocked point-read HTTP even though Java `StorageScan.Point` declares positive
`long sourceId` (StorageScan.java338–359). No real network or Java defect claimed.
Reproducer: `indexed-source-id-long-bound-reproducer.json`. This is distinct from
the already corrected caller-supplied cell offset domain; sourceId is supplied by
the observation DTO and invalid server/local DTO data should raise ProtocolError.

Smallest correction: shared `_read_spec` rejects positive IDs above `2**63-1`
before any read HTTP. Preserve existing source0/detached-consumer unsupported
behavior, exact integer/type/negative checks, identity/binding selection, existing
valid values and order. Do not coerce/clamp IDs or broaden this into a repository-wide
ID/handle/job migration, incarnation or Java repair effort. No scientific algorithm,
API signature, dependency, principal or provider policy change.

Tests: both facades oversized sourceId cases fail before correction; after correction
ProtocolError and zero HTTP. Positive1/maxlong retain exact wire identity with mocked
responses, without claiming actual committed source existence. Existing negative/bool/
source0/detached-consumer controls remain, existing sync/async scientific reads pass.
Actual native constructor/JSON contract probe can verify sourceId1/maxlong/negative0;
it does not authorize a huge source or replace scientific computation. Installed
17 gates, exact candidate/source/install/probe binding, guide review/normal PR/CI/
eligible merge/ancestry/postmerge required. No unchanged model rerun is needed for
local impossible-wire-integer rejection. Full A+B/study/provenance gates remain open.

## Before-fix and native-domain evidence

Four oversized-ID regressions failed on unchanged SDK (both facades, 2**63 and
2**100), with 12 existing/positive controls passing. The initial harness run also
had two detached-control setup failures; corrected the test to mutate decoded
observation metadata as the existing foundation tests do, then reran before the
production correction. Those setup errors are not additional SDK defects.

Actual unchanged Java `StorageScan.Point` constructor/JSON probe passed sourceId1
and maxlong exactly; zero/negative constructor IDs rejected, out-of-long Java string
parse rejected. This checks domain/serialization only, not source existence or
computation. Shared Python preflight correction is under full installed validation.

Candidate `6f782b4a4e23410bfc3d40bb4b62c9c66d25b704` passed 176 focused tests,
3,362 installed tests, all 17 gates, build/lint/source parity. Exact current source,
installed/raw-harness/artifact identity and native probe binding verified. This
baseline excludes pending CODELIST #229 additions; combined tree must be checked
before delivery. No scientific live run or source-existence claim.

- Wheel: `0b29affe26ffa22c3e67720588418ee81020949a45fb66e804bac40c844a0186`
- Sdist: `aa01bd035095e34326aa4e7fff9e9ca9dce1a3dbbb681d6d7928d3eddee4bb1a`
