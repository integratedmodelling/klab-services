# Contribution review: PaL Radiation syntax adaptation

## Recommendation

**Ready for maintainer review as a syntax-only research contribution.** Separate
read-only reviewer found no demonstrated blocking or important implementation
defect in the authorized scope. Scientific acceptance remains **BLOCKED**.
This recommendation does not approve ontology/time mappings, registration,
publication, P2 accuracy or P4 reproduction.

## Reviewed state

- Base: `9a69fae6d634c6c1cfe08a5c0a8fc996b5c0a7ff`.
- Reviewed candidate: `11877f7b81ddb6d14cf10aa337a5039ef9205424`.
- Branch/worktree: `feature/pal-radiation-adaptation`, `pal-radiation-adaptation-20261101`.
- Separate reviewer: `ses_e67822912ffeuCeLIZ28LiUz46`.
- Correct base ancestry; clean working tree before/after both reviewer passes.
- Seven added research files; no SDK, packaging or production Java changes.
- Repository instructions, contributor-review guide and original milestone read.
- No G0/pilot PR according to candidate documentation; reviewer did not query remote state.

## Capability and boundaries

Pinned original PaL bytes produce a reproducible external derivative with only
`rgc_max`, `rg2`, `fr`, `T_RC_MAX`, original licence and modification notice.
Mechanical syntax changes preserve equations and concept names. Output remains
`SYNTAX_ONLY_NOT_REGISTERABLE`, with no project manifest or implied semantic
authority. No new Python implementation of Puerto or existing SDK duplication.

## Findings, ordered by consequence

**Mandatory contribution defects: none identified.** This is not exhaustive proof.

1. **External data blocker:** original WCS responses are HTML rather than coverage.
   Recover the authoritative radiation input or supported endpoint and exact
   unit/time meaning. A substitute dataset needs separate scientific approval.
2. **Scientific coordination:** concept mappings, equality of plant classifications,
   fraction/percentage mediation and monthly-to-daily support remain unapproved.
   Parsing and expression arithmetic cannot approve them. Keep execution paused.
3. **Reviewer environment issue, resolved:** initial invocation used a different,
   incomplete copied JDK and failed to find `java.dll`; a direct-classpath attempt
   also exceeded Windows command-length limits. Correct installed executable plus
   argument files independently passes all probes. No restore/install was needed.
4. **Optional improvement:** exact portable setup and command/revision linkage in
   the reproduction recipe would reduce reconstruction. Current external manifests
   and hashes support review; this is not a correctness defect.

## Validation actually performed by reviewer

Windows Python from `pr102-upstream-312`; source imports explicitly bound to this
candidate. Initial tests disabled bytecode/cache writes and used external temp
paths. No repository edits, services, registration or remote mutations.

| Check | Result and limits |
| --- | --- |
| Clean status, ancestry and complete diff | PASS |
| Focused adaptation pytest, no cache, external basetemp | PASS: 9 tests, exit 0 |
| Fatal-error Ruff lint, no cache | PASS, exit 0 |
| Base-to-candidate diff whitespace | PASS |
| In-memory actual pinned archive adaptation / derivative comparison | PASS: exact sources, manifest, licence/notice, membership and bundle hashes |
| Original Puerto source and semicolon-delimited parameter table | PASS: attenuation/saturation independently inspected; all 41 Rgmax rows equal 25 |
| Independent recovered-raster inspection | PASS: hashes, shapes, CRS/transforms, category sets/integrality, valid/missing counts |
| Saved WCS bodies and seven legacy sources | PASS integrity; HTML verified; no fresh WCS availability claim |
| Initial Java invocation through copied JDK | BLOCKED: exit 2, missing java.dll; preserved, not candidate breakage |
| Installed Temurin version | PASS, exit 0: 21.0.12.1+1 |
| Fresh independent parser with pinned `parser.args` | PASS, exit 0: positive control and both derivative sources have zero errors |
| Fresh independent parser with `parser-current.args` | PASS, exit 0: same result |
| Fresh independent Groovy expression probe | PASS, exit 0: 8 original + 8 adapted cases, two mutants rejected, Groovy 4.0.25 |
| Full Python offline suite | Producer log inspected: 1698 passed, one warning; not rerun by reviewer |

Correct installed Java executable:
`C:/Users/lumsd/AppData/Local/Programs/Temurin21/jdk-21.0.12.1+1/bin/java.exe`,
SHA-256 `82051fdab26319d77d20cc0065045d05ec00b3e3d05f44935d7c06b96b621d55`.
DLL presence/hashes were recorded. Independent rechecks used argument lists,
`CREATE_NO_WINDOW`, 15-second version and 90-second probe deadlines.

The failed executable was instead under
`C:/Users/lumsd/AppData/Local/Temp/opencode/klab-local-stack/jdk/`.
Do not reinterpret it as a failure of the intact installed JDK or erase that attempt.
An initial reviewer table-inspection delimiter error was also corrected to the
actual semicolon format before the independent inspection passed.

Exact fresh reviewer commands, outputs, selected paths and executable/DLL hashes:
`C:/Users/lumsd/AppData/Local/Temp/opencode/pal-review-java-recheck-8wj3o7ls/recheck.json`.

## Scientific and lifecycle evidence

Derivative ZIP has exactly two sources, LICENSE and NOTICE, no project manifest.
Source-defined arithmetic equivalence is explicitly not k.LAB execution. Rasters
remain external; redistribution terms and paper-run identity are unknown. Legacy
worldview source is dated 2026, not relabelled as the 2023 environment. Grid,
missingness, classification, units/time questions remain visible. No-clobber and
repeatable bundling checks passed. Remote identity/permissions, execution recovery
and export lifecycle are N/A to this generator and unverified for a live pilot.

## Contributor coordination

No production-source overlap. Scientific/worldview maintainers should assess
proposed mappings and resource maintainers the import path; these are suggested
consultation areas, not assignments. No messages or PRs were sent.

## Smallest next actions

Preserve this bounded candidate/review. Keep execution paused until authoritative
radiation input, approved mappings, import support and permissions are established.
Seek separate authorization before push, PR, publication, registration or Java migration.
The earlier suggestion to restore the JDK is superseded by the successful independent
recheck; no environment repair is now required for these parser/arithmetic probes.

## Suggested PR description (prepared only)

Add a pinned, syntax-only PaL Radiation research adapter. Generate three models
and one table externally with unchanged equations/concepts, licence/provenance and
non-registerable status. Nine focused tests, lint, exact archive/bundle/data checks,
both real parser sets and arithmetic probes pass independently; producer offline
suite reports 1698 passes. No SDK/production Java changes, approved ontology
bindings, authentic k.LAB execution or P2/P4 acceptance. Radiation availability
and scientific bindings remain blockers. No public data payloads included.
