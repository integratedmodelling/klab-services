# Puerto / PaL G0 - first compatibility gate, 1 November 2026

## Decision: PAUSE feature expansion; milestone INCOMPLETE/BLOCKED

The unchanged archive is not syntactically accepted by either tested modern Java
parser dependency set. Do not implement F1-F7 to conceal that result or rewrite
Puerto in Python. **A (unchanged executable) is disproven for these exact parser
tuples.** A migration investigation is needed before deciding whether this is B
(small approved adaptation) or C (significant migration). C is a planning risk,
not a measured migration cost or evidence that every algorithm must change.

G0 is **not accepted complete**: live worldview/resource registration, resolution,
computation, a pinned R environment and a fully specified common scenario remain
unestablished. Offline parser failure justifies stopping before service startup.
No PaL Job, context or remote project was created. No Java scientific, ontology,
component, archived-model or SDK production source was changed.

## Reviewed state and authority

- Local branch: `research/pal-artifact-feasibility`, base
  `6bcee78ee268fe321d2b417ee4403b8abfefa0b9` (accepted Python tooling integration).
- User authorized this milestone to start on 1 November, after branch cleanup.
  Remote pushes, PRs, merges, releases and shared deployments are not authorized.
- Original specification remains `ARIES_Pasture_Milestone_Revised.md`, SHA-256
  `9c64ceab7b15f605eda1008ff2dc65c2dc075ec738b614d6397c1d8094ab69a9`.
- #90/#98/#100/#102 are merged; #102's scientific gates were deferred, not passed.
  Historical planning-guide draft/failure snapshots do not override that disposition.
- Prior unique history is preserved by tags; none of it is silently merged here.

## Artifacts and licensing

| Artifact | Immutable record | Bytes | SHA-256 |
| --- | --- | --- | --- |
| Puerto v1.0 | 6786419 (concept DOI 6786418) | 612570 | `4b1aff4c5b8ba3be9b2568ed79098c176850dc1be5570816e00d27848adccad9` |
| PaL 0.1.1 | 7533051 | 4704395 | `6503feeb0dc743601fe3e3205c879354801fc21353913fd06b9ae4ff1443d872` |

**Use `artifacts.json` and the download tool for exact machine pins.** Both Zenodo
MD5 declarations and independently calculated SHA-256 values were checked. Puerto
contains 40 files / 13 R scripts. PaL contains 48 files / 12 source namespaces
(ten thematic namespaces plus data and tables), and one test namespace.
PaL declares `klab.version = 0.10.0`.

Both archives contain GNU Affero GPL version 3 licence text. Zenodo's Puerto
metadata says `apgl-v3`; PaL says `other-open`. Those labels are recorded, not used
to override the actual licence. Input datasets can have separate provenance and
terms; archive licence text alone is not a completed dataset redistribution review.
No third-party archive, raster, shapefile or scientific source is committed here.

## What the archive actually asks Radiation to calculate

`src/ecology/grassland/lf/radiation.kim` declares four models, not three total:

| Local name | Declared output / dependencies |
| --- | --- |
| `rgc_max` | Maximum photosynthetically active radiation, `MJ/m^2`; pasture-plant classification `com`, table `T_RC_MAX` |
| `rg2` | `earth:SolarRadiation for ecology:Vegetation in MJ/m^2`; radiation `rg`, plant `com`, overstory plant `com2` |
| `fr` | `percentage of ecology:Vegetation biology:Growth caused by earth:SolarRadiation`; `rg2`, `rgc_max` |
| `ftrh` | Global limiting factor; temperature `ft`, radiation `fr`, moisture `fh` |

The archived `rg2` expression uses radiation unchanged when `com == com2`,
otherwise multiplies by 0.2. `fr` saturates at 1 or uses `rg2 / rgc_max`.
`T_RC_MAX` lists 25 for its ten vegetation classes. These are **source-defined
checks**, not independent validation of the same implementation. An independent
oracle must reconcile the paper/R/supplementary equation, scale and actual inputs.
The archive calls `fr` a percentage but describes a 0-1 range: do not silently
multiply/divide by 100. The data namespace exposes radiation as `MJ`, whereas the
dependent model requests `MJ/m^2`; supported mediation cannot be assumed.

First source-derived request candidate (not successfully resolved or submitted):
`earth:SolarRadiation for ecology:Vegetation in MJ/m^2`.
Actual model URNs/selection must come from Java; local names are not invented
service-returned URNs. `radiation-dependencies.json` records the source inventory.

The data namespace actively references old local URNs for radiation, main plant
classification and overstory classification. Those exact URNs are absent from the
eight bundled resource descriptors. This proves absence from this ZIP, **not**
that every possible provider/network copy is unavailable. Seven climate descriptors
have coordinates around longitude -1.8 to -0.85, latitude 48.1 to 48.76 (not the
Cantabrian study area). A vegetation TIFF has no matching bundled resource.json.

## Independent reference: a useful positive finding

The publisher's S1 DOCX supplement (`pone.0281348.s015`) contains **S12 Table: 140
numerical Puerto/PaL vegetation-growth pairs in g/m^2**. It is machine-readable
table data, not digitized plot pixels. Its SHA-256 is
`3089114b43a942123cc156438470b4338920573ab8078834e355e3b3b119b066`.
The paper is CC BY 4.0; attribution and dataset-specific terms still need recording
before public data redistribution. Compact source metadata are in `artifacts.json`;
the locally extracted table remains outside Git pending publication authorization.

This is a possible independently published R-result reference, not a fresh Puerto
run and not P4 acceptance. The table's plot numbers 1-140 have not been mapped to
the archive's 233 management polygons (DBF fields `up`, `cattle`, `mares`).
The supplement states PaL was averaged by plot and explains bare/non-palatable
areas as a cause of differences. Never join rows by file order or assume identical
masks. Supplementary S14 is **growth by plot**, not an independently established
temporal series. Daily time-series reference availability remains UNKNOWN.

The paper's showcased results use mean climate for May 2018 at 50 m. The archive's
active observer instead specifies 100 m and time 2010-2050. These are distinct
scenarios, not interchangeable defaults. A common exact subset and time support
must be established before F3/F6. No arbitrary small bbox is labelled the study fixture.

Candidate growth variables are archive `crecpot` (potential above-ground growth)
and `creca` (actual above-ground growth), both declared g/m^2. The R source uses
`crecpot` and `crec` within its timestep tables. Mapping S12 to the exact output,
aggregation and time support remains a research requirement, not inferred from names.

## Puerto R execution assessment

No `Rscript.exe` was found on PATH or under Program Files/R, local Programs or
the existing Anaconda installation. PowerShell's `r` is an alias, not evidence of R.
No runtime was globally installed or third-party R code executed.

The unchanged runner hardcodes `H:/C/PROYECTOS/Puerto2018`, site `MUPs378_376`,
2010-2016 and a 2555-day simulation. It expects `Inputs/sites/<site>`, while the
ZIP contains a flat `input/` directory. `Plant1_Comun.R` loads `clima.Rdata` and
`suelo.Rdata`; neither is bundled. `fechas.Rdata` is present but is not a replacement.
Required packages mentioned in the scripts: RSAGA, data.table, ggplot2, grid,
raster, rgdal and rgeos. No R version, package lockfile or SAGA binary is supplied.
R execution is **BLOCKED / NOT RUN**, not a failed scientific result. Installing
R alone would not supply the missing site inputs or establish the paper scenario.

## Reproduced Java compatibility result

The external read-only probe uses the same `KimStandaloneSetup`/`IParser` entry
point used by current Resources parser tests. It reads original ZIP bytes directly,
never adapts syntax, registers a project or launches a service. JDK
Temurin 21.0.12.1+1, `-Xmx768m`, 90-second parent deadline; both commands exited 0
because the diagnostic harness completed, **not** because PaL passed.

| Parser set | k.IM JAR SHA-256 | Positive control | Archived source namespaces | Archived test |
| --- | --- | --- | --- | --- |
| Prior selected Java acceptance classpath, timestamped k.IM 20261016.113257-58 | `77bdeccc45db8faa1e83ab9a5c6fc6a300ea71c3eeac9d9fa4d31af9830e015d` | PASS | 12/12 FAIL | PASS |
| Separate comparison with current local language snapshots, k.IM 20261029.085338-67 | `01f8ae62dcb520cadf64931eff1cfc7cdb5db087b9f5daedae2d80bd0a7d8d38` | PASS | 12/12 FAIL | PASS |

Radiation has eight syntax diagnostics in each run, including line 9 (`2` in
`MJ/m^2`), line 34 (`of`), and line 35 (description placement expecting `;`).
The tables namespace has 95 diagnostics; growth has 21. Parser recovery can
cascade: these counts are not counts of independent defects or migration tasks.
`parser-results.json` preserves the results and `parser-dependencies.json` the
actual dependency identities. Current local snapshots are **not proof of the
latest advertised upstream snapshot** or a complete supported deployed stack.

No live worldview acceptance, resource registration, discovery, Job submission,
export, permissions or fresh-process restoration was attempted after syntax failed.
Those are BLOCKED/NOT RUN, never implied by a successful standalone parser control.

## Reproduce the bounded work

From `python-client/`, with existing test dependencies:

```powershell
python tools/pal_g0_artifacts.py --archive puerto --download --output C:/fresh-parent/puerto
python tools/pal_g0_artifacts.py --archive pal --download --output C:/fresh-parent/pal
python tools/pal_g0_artifacts.py --archive pal --input C:/existing/pal.zip --output C:/fresh-parent/pal-offline
python -m pytest tests/unit/test_pal_g0_artifacts.py -q
```

The tool has pinned size/SHA-256/Zenodo-MD5 and member/expansion budgets. It does
not extract or execute source. Existing output is rejected before network/file reads.
Raw ZIPs, selected text, supplement, complete parser dependency manifests, probe
source, launch argfiles and logs are retained externally under
`C:/Users/lumsd/AppData/Local/Temp/opencode/pal-g0-evidence-20261101/`.
The probe is retained at `probes/PalParserProbe.java`; run with an explicit existing
Resources dependency classpath, original PaL ZIP and fresh JSON destination:

```text
<pinned-java> -Xmx768m -Djava.awt.headless=true -cp <pinned-classpath> PalParserProbe.java <pal.zip> <fresh-result.json>
```

The parser test is offline: no service deployment tuple is attested by it.

### Validation of this research increment

Windows Python 3.12, explicit `PYTHONPATH` bound to this worktree's `src` and
`tools` (module import path checked before regression). Focused artifact tests:
**11 passed**. Existing unit/integration suite plus those tests: **1689 passed,
1 Rasterio georeferencing warning in 56.36 seconds**. Fatal-error lint passed.
These are research-tool/SDK regression results, not PaL scientific acceptance.

Two initial focused runs retained one failing backslash-member test (8 then 10
other tests passed). Windows ZIP writing and reading normalize separators. The
fixture now explicitly retains the malformed header name, and validation checks
`ZipInfo.orig_filename` before accepting the normalized name. Original failure
logs are retained; no assertion was removed or scientific gate weakened.

Both original downloaded archives were also checked with the research tool, and
the checked-in Java probe separately reproduced both parser diagnostic reports.
No package matrix, R execution, live services or full scientific gate is claimed.

## Reuse map and next decision

Existing semantic explorer/model inspector, recorded evidence, typed requests,
runner, native exports and analysis remain the starting point for F2-F5.
No new SDK capability is justified by a legacy parser failure. This does not yet
approve NO_NEW_CODE_REQUIRED for PaL acceptance: existing behavior needs genuine
PaL tests once a compatible fixture is available. F1/F6/F7 are NOT STARTED.

Options before proceeding:

1. **Separate migration assessment (recommended):** authorize a read-only mapping
   of legacy syntax/ontology/resource contracts to modern equivalents, focused first
   on Radiation. Deliver proposed changes and independent equivalence tests for
   maintainer/scientific review **before** changing Java, ontologies or PaL source.
   Relative effort: moderate for triage; full migration unknown/high risk due to
   resource bindings, temporal semantics and worldview changes.
2. **Historical environment:** recover the original 0.10.x stack/worldview and
   resources. Relative effort: uncertain; obsolete dependencies and data access are
   risks. A pass there would not certify modern Java compatibility.
3. **Switch case:** choose an actually supported modern model with available
   independent reference data. Likely less legacy work, but changes the milestone
   and requires explicit user approval; no substitute result counts as PaL/P4.

In parallel, obtaining the original site climate/soil inputs, exact historical
environment metadata and the S12 plot-to-polygon mapping would unblock independent
reference work. No paid/large compute, maintainer contact, imports or public data
publication is authorized by this report. No calendar completion estimate is
defensible before those dependencies are resolved.
