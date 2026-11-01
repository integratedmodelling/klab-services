# Adapted PaL Radiation pilot - implementation checkpoint

## Status: syntax adapter implemented; authentic scientific execution BLOCKED

User authorized a narrowly scoped adapted Radiation pilot on 1 November 2026,
including recovery of real inputs. Unresolved scientific mappings remain held
for approval. No Java changes, shared-service changes, remote publication or full
PaL/P4 claim is authorized. Local branch `feature/pal-radiation-adaptation` starts
at reviewed G0 checkpoint `9a69fae6d634c6c1cfe08a5c0a8fc996b5c0a7ff`; underlying
accepted Python integration is `6bcee78ee`. No PR exists for this increment.

## What is implemented

`tools/pal_radiation_adaptation.py` consumes only the exact hash-pinned original
PaL ZIP. It generates a fresh external derivative ZIP and provenance manifest.
It is a research tool, not a public SDK API or a ready-to-register project.

The derivative includes `rgc_max`, `rg2`, `fr` and `T_RC_MAX` in their original
namespaces. `ftrh` and all unrelated tables/models are explicitly excluded from
this pilot, not claimed migrated. Changes are limited to:

- Five live `MJ/m^2` spellings become `MJ/(m*m)`; comments retain original text.
- One grammar-only `of` after `percentage` is removed. The percentage/fraction
  representation question remains unresolved; no values are scaled.
- Three descriptions are preserved verbatim as comments because modern model
  grammar does not admit the old description position. Rendered-documentation
  parity is not claimed.
- Lookup-table delimiters change from `{{ ... }}` to `=== ... ===`.
- Line endings are normalized. The two expressions remain exactly unchanged.

Original licence text and an explicit modification/subset/limitations notice are
included in generated outputs. No copied third-party model, ontology or raster
is committed. Original archives remain unchanged. A generated project manifest
is deliberately absent: outputs always say `SYNTAX_ONLY_NOT_REGISTERABLE` with
mapping/resource/time approvals blocked and scientific acceptance false.

## Verified results and limits

| Check | Result | Meaning |
| --- | --- | --- |
| Both existing actual Java parser sets: timestamped 20261016-58 and local 20261029-67 | PASS: Radiation subset and table source have zero syntax errors; positive control also passes | Syntax only; no semantic adaptation or model resolution |
| Groovy expression probe of original and derivative expressions | PASS: eight independently specified arithmetic cases for each | Algorithm-equivalence evidence only, not k.LAB execution or field accuracy |
| Deliberately wrong attenuation and saturation expressions | Both rejected by known answers | Probe detects these two numerical faults; not exhaustive oracle certification |
| Focused Python adaptation tests | 9 passed | Traceability, declared transformations, original pin rejection, no-clobber preflight and repeatable bundle |
| Complete Python unit/integration suite | 1698 passed, one expected Rasterio warning, 45.03 seconds | Existing SDK regression plus research checks; not PaL scientific acceptance |
| Fatal-error lint and diff whitespace | PASS at implementation checkpoint | Narrow static checks |
| Real recovered vegetation files | Both downloaded, hashed, metadata/category values inspected | Actual referenced inputs; no import, classification, resampling or scientific run |
| Original radiation WCS | FAIL availability/contract check: redirects to HTML site despite HTTP 200 | No coverage obtained; do not count HTTP delivery as scientific availability |
| Modern semantic bindings, actual service compute, nodata/permissions/export | BLOCKED / NOT RUN | Required before P2 acceptance |
| Puerto runtime and P4 | BLOCKED / NOT RUN | Not waived by this pilot |

Initial generation stopped on an incorrect unit-token count: two archived comments
also contain `MJ/m^2`. The inventory was corrected to count/modify only live source
before `//`, preserving comments. No output was created by that failed attempt.
This was a research transformation preflight error, not a failed PaL calculation.

## Independent numerical expectations

`radiation-known-answers.json` specifies eight arithmetic expectations from the
independently authored Puerto R attenuation/saturation logic and its `Rgmax`
input column, with tolerance fixed before execution. For example, radiation 20
with equal plant classes remains 20 and yields factor 0.8 at threshold 25; with
different classes it becomes 4 and factor 0.16. Zero, threshold equality and
saturation are covered. The probe evaluates original archived expressions in
Groovy, not a new Python implementation of Puerto.

This does not run Puerto R, does not validate actual semantic concept equality,
and does not supply missing observations. The archived R climate script treats
its radiation as daily MJ/m^2; adapting a monthly resource to that support is an
unresolved scientific decision. Nodata/classification/space/time correctness must
be checked in the eventual actual Java run, not inferred from these arithmetic cases.

## Real data recovery - new evidence since G0

The exact referenced project remains publicly readable on Bitbucket:
`integratedmodelling/im.alice.spain`, pinned inspected commit
`054d1a79286d708ce2a04e94d0530d5eebcec404`. Its resource directory was fully listed
with two bounded metadata pages (187 entries); the entire approximately 373 MB
repository was **not** downloaded.

Two original matching-URN raster payloads were recovered:

| Resource | Bytes | SHA-256 |
| --- | --- | --- |
| `local:amarquez:im.alice.spain:com_50m_wgs84` | 4736687 | `468bab1d7690047a8d390616384623ac9a994a6dd4419e4869fb9816aad4ddf7` |
| `local:amarquez:im.alice.spain:com2_50m_wgs84` | 505099 | `e40797bc0f59ba04fc4111fbab653c5077c70e42adbbc6a8049c03b36f0cf9c5` |

Both are float32 categorical rasters, shape 850 x 1391, EPSG:4326, Cantabrian
coverage and explicit negative nodata. Both contain 369850 valid cells and 812500
missing cells (these counts alone are not proof that masks coincide). Main-plant
values are the ten original class codes; overstory has nine; all valid values are
integral. No bilinear interpolation or code-to-taxonomy mapping was applied.
Their degree-based transforms differ slightly. Filenames containing `50m` are not
proof of a shared projected 50 m grid. Preserve actual metadata and validate any
chosen target grid/reprojection with classification-safe resampling.

The exact matching radiation descriptor was also found. It specifies adapter
`wcs`, identifier `im-alice-spain__spain_solar_radiation_may_2018`, WCS 2.0.1 and
`https://integratedmodelling.org/dev-geoserver/ows`. Both GetCapabilities and
DescribeCoverage requests returned HTML, redirecting to
`https://klab.integratedmodelling.org/`. No coverage XML or radiation raster was
returned. This proves failure of this URL/request now, not universal loss of the data.

The recovered repository snapshot is not attested as identical to the paper's
execution snapshot. Its descriptors have empty metadata; redistribution terms
remain UNKNOWN. Raster bytes remain external and must not be published implicitly.

## Semantic source recovery - not an approved mapping

The legacy worldview source is publicly readable at
`integratedmodelling/im` on Bitbucket. Inspected revision
`b15c8fe7c9c2aaedc1ea3c83e67f7e18716f91a3` is dated 2026-09-22, so it must not be
mislabelled the original 2023 execution worldview. Seven small source files were
recovered externally, with hashes and definition contexts recorded.

They recover useful meanings: biological growth/plant declarations, the vegetation
identity, overstory as a vegetation-stratum attribute, photosynthetically active
radiation and GBIF-backed genus/species/family identities. This is better evidence
than choosing modern concepts by similar labels. It does not establish equivalent
modern domains, authority bindings, logical entailments or units.

In particular, do not rename `im` to `imod` globally, turn biological growth into
an unrelated physical growth process, replace missing classifications with numeric
constants, or collapse genus/species/family distinctions. Concept equality in the
attenuation expression must mean the same thing after any approved mapping.

The selected modern worldview `d6c37c0b82afadd004d94bc5311983b66677532c` still has
only two direct name matches among the 22 source references examined during G0
(full Radiation namespace plus its table). That is lexical/source evidence, not
a live reasoner result or a proof about every newer worldview. All new scientific
ontology mappings are **NOT APPLIED / AWAITING APPROVAL**.

## Reproduction

From `python-client/`, using the exact original archive stored externally:

```powershell
python tools/pal_radiation_adaptation.py --archive C:/evidence/pal.zip --output C:/fresh-parent/radiation
python -m pytest tests/unit/test_pal_radiation_adaptation.py -q
```

Run the existing `probes/PalParserProbe.java` with each G0-pinned dependency set,
pointing it at the generated `adapted-source.zip` and a fresh JSON destination.
No Java source was changed for these runs. For offline arithmetic equivalence:

```text
<pinned-java> -Xmx768m -Djava.awt.headless=true -cp <G0-pinned-classpath> groovy.ui.GroovyMain docs/research/pal/probes/radiation_expression_check.groovy <original-pal.zip> <adapted-source.zip> docs/research/pal/radiation-known-answers.json
```

Temurin 21.0.12.1+1; Groovy 4.0.25 is present in the recorded dependency set.
The existing classpath also carries a Groovy 3 jar: this probe is not a certification
of clean runtime dependency convergence or actual k.LAB compilation/execution.
Original and generated manifest hashes, parser results, expression output, metadata
recovery and test logs are retained under
`C:/Users/lumsd/AppData/Local/Temp/opencode/pal-radiation-evidence-20261101/`.
Compact generated summaries accompany this report; source/raster payloads remain external.

## Concrete remaining prerequisites

1. Recover the original radiation coverage, or obtain an authoritative supported
   endpoint and exact unit/time interpretation. A replacement dataset is a separately
   approved scientific scenario, not recovery of the original run.
2. Review the recovered legacy definitions against a selected modern worldview and
   approve explicit concept/authority mappings or a separately scoped compatibility
   ontology. No proposed mapping becomes authoritative merely because syntax parses.
3. Establish a supported local payload import path and permissions. The baseline
   legacy importer rejects multi-file ZIP ingestion and JSON metadata alone does
   not install raster bytes. Do not assume a new SDK wrapper can fix that.
4. Then run an isolated, bounded three-model pilot with actual source identities,
   daily support, class equality, masks, coordinates and independent expected values.
   A new Java/component requirement stops this no-Java scope for a separate decision.

The adapted syntax slice is reviewable now. P1 semantics, P2 accuracy, P3 growth
and P4 comparison remain unaccepted. Full growth also has separate nonsemantic
`number`-model and temporal-state migration risks; they are not part of this pilot.
