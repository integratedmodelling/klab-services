# Native async semantic exploration

Base `e92b8be52e21f81acafcd9436a962d77d5fa7dbf` (#229). Separate read-only
supported-client notebook slice while source-ID #230 CI runs; same owned worktree,
preserved reviewed candidate/evidence. Existing `SemanticExplorer` explicitly refuses
native AsyncClient, although native Reasoner transport/routes already exist.

Add `AsyncSemanticExplorer` with awaited concept/observable resolution, parents/
children, is_a/satisfiable predicates, authority search/candidate resolution and
documentation descriptors. Share existing pure checks/immutable DTO conversion:
no local semantic inference, cached authority choice or Resolver/model forcing.
Every network operation awaits native async transport; no threads/nested event loop.

Keep bounded item/response budgets, notification/type/classification validation,
scope/service/worldview/observed-revision checks and stale-candidate ownership.
Null revisions remain unavailable, not authoritative leases. Scope and endpoint
identity must be rechecked around results as in sync exploration. Authority UNSUPPORTED/
UNAVAILABLE/FAILED stay explicit statuses; never normalize them to empty OK or
invent a production provider. Documentation URLs are inert descriptors only, never
followed or sent credentials. Candidate selection stays explicit and explorer-bound.
Cancellation stops local requests, not remote work; all routes are read-only even
where the current Reasoner API uses POST bodies. No Java, authority registration,
grammar/compiler, publication or scientific algorithm changes.

Before code: reproduce existing local AsyncClient refusal. Tests must compare sync/
native async outputs on same DTOs, distinguish all authority statuses, stale/foreign
candidate refusal, hierarchy/predicate order and exact supported routes, malformed/
missing/unknowntype/budget/error/redaction/cancellation/type controls. Keep existing
sync tests green. Installed 17 gates and one actual Java known semantic positive/
missing/hierarchy/predicate workflow required; authority behavior reported exactly
as served, not forced to nonempty. No scientific submission or original study rerun.
Exact source/install/harness/live/privacy/cleanup, guide review, normal PR/CI/eligible
merge/postmerge required. Full semantic/revision/production-authority/A+B/study gates
stay open. Inspect enabled Python criteria and demonstrated defect before any Java
work; none is currently requested or justified.

## Initial package validation

`1c4a6bad6`:160 focused /3,404 installed/all17gates/build/lint/source parity PASS.
Initial expanded test command had159PASS/1FAIL because mock json= rejected deliberate
NaN before SDK decoding; raw response bytes corrected that test setup, not production.
This package excludes pending #230 source-ID tests/fix. Parent now merged
`1af1e4935ee4c3c3cd9a260af3151a7837ab6bcb`; exact209postmerge/integration and
combined validation required before one actual semantic read-only Java run.

- Wheel: `8862dc29e2c568a6599ecabfa045afd2ce9cb5457b04ae839e51f53131e5c166`
- Sdist: `9b978fd0ce4d7a68d5bee23fb9dba5ced4d4e721945ff41514bd65b9e098b624`
