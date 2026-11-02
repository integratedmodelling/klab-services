# PaL Radiation retry: production coverage recovered

**Next review package:** [Scientific compatibility proposal](RADIATION_SCIENTIFIC_REVIEW.md)
and [unsent producer questions](radiation-producer-questions.md). These record
taxonomy conflicts and concrete input/mask coverage gates; no mappings are applied.

## Outcome

**Radiation data access improved; scientific bindings still block Java execution.**
The user selected shortlist #2, the narrow reviewed three-model Radiation subset,
not Java migration or full PaL reproduction. The original project/archive, prior
failed development-endpoint requests, reviewed derivative and equations remain
unchanged. No project registration, scientific Job, service change, Java/ontology
edit, push or PR occurred.

This retry does NOT demonstrate the SEEA-style Java report blocker for Radiation:
the three-model chain does not need the legacy account-table compiler. Its numeric
lookup table is a different construct from SEEA `define table` reporting.

## New input evidence

The original descriptor requests coverage
`im-alice-spain__spain_solar_radiation_may_2018` from
`https://integratedmodelling.org/dev-geoserver/ows`. Earlier requests to that
development URL returned an HTML homepage. Those failures remain valid evidence.

Both public **production** endpoints now return actual WCS CoverageDescriptions
XML for that identifier:

- `https://integratedmodelling.org/geoserver/ows`
- `https://integratedmodelling.org/geoserver/im-alice-spain/wcs`

Both responses have SHA-256
`d5adff58e682c842a976243ac9b7b2375a4a07a16d43c556f75ba79543343cf9`.
Coverage identifier and geographic bounds match the archived descriptor. This is
much stronger evidence than a similarly named unrelated replacement dataset, but
not an immutable original-paper payload identity attestation.

A bounded WCS 2.0.1 GetCoverage request for longitude -3.76 to -3.75, latitude
43.35 to 43.36 returned an actual native-grid GeoTIFF:

| Property | Observed value |
| --- | --- |
| Status/type | HTTP 200, image/tiff |
| Bytes | 16820 |
| SHA-256 | `6c5ebdbdf584ae743f71de7da77fa3d1174d8ad9ce5249004ac82ab439b0c2f3` |
| Shape/type/CRS | 36 x 36, float32, EPSG:4326 |
| Valid/missing | 1296 finite valid cells, zero missing cells |
| Raw range | 28.336299896240234 to 44.52827453613281 |
| TIFF units | Absent; scale 1, offset 0 |

This is raw-input integrity inspection, not a computed Radiation result or an
accuracy comparison. No scaling, class substitution or raster resampling was
requested. The returned native bounds differ slightly from the requested box;
their actual transform/bounds are recorded. The two previously recovered main
vegetation/overstory TIFFs remain external and unchanged.

### Identity, units and time remain unresolved

- WCS range metadata advertises `W.m-2.Sr-1`, not the required energy-per-area
  interpretation. We retain the inconsistency and do not write invented units.
- Scoped WMS capabilities confirms the layer name/title but has an empty abstract,
  no MetadataURL and no time dimensions. The title “May 2018” alone does not
  establish daily mean, monthly sum or a single-day value.
- Original PaL data binding declares `earth:SolarRadiation in MJ`, whereas the
  Radiation dependency requests `MJ/m^2`. PuertoClima.R divides its radiation
  arrays by 1000 and explicitly comments `MJ/m2/d`. That original R conversion
  does not authorize applying it to this recovered PaL raster.
- Native WCS grid high indices are 2782,1700; the archived descriptor says
  `S2(2782,1700)`. Possible count/index convention differences must be resolved
  rather than assuming checksum-equivalent historical data.
- The alternate endpoint and a new crop hash do not prove that the original study
  used these exact bytes. Source/licence and paper-run provenance are still needed
  before redistribution or a reproduction claim.

Thus **public named coverage retrieval is now verified**, while authoritative
scientific interpretation and original-run identity remain unverified. Do not
continue describing radiation simply as unavailable everywhere.

## Current-worldview check, not guessed semantic migration

Checked exact reviewed Radiation/table references against upstream worldview
`1328000fff63a1951465b1f7b130c0af15264c95`, without loading or editing it.
The narrow derivative has **19 distinct fully qualified concept references**;
only `earth:SolarRadiation` directly matches a token in its original namespace.
The other 18 lack that direct lexical match. This is source inventory, not a live
Reasoner verdict, concept entailment test or proof about all other deployments.
It is distinct from the earlier full-Radiation/ftrh inventory of 22 references.

New biology/ecology processes and the `life:TaxonomicIdentity` TAXA binding do not
automatically supply `biology:Plant`, `biology:Growth`, `ecology:Vegetation`,
photosynthetically active radiation, overstory or pastureland. TAXA can be useful
for reviewed identities; it does not establish all biological model meanings.

`data:Maximum` exists, but its declaration as a data aggregation qualifier is not
an approved replacement for the original `im:Maximum` meaning. Replacing every
`im` prefix or collapsing taxonomic ranks would be scientific/ontology migration.
No such mapping was applied. Legacy class identities include species, genera,
family-level Gramineae and class-level Polypodiopsida; verify individual authority
identities/ranks and equality behavior rather than treating them all as species.

The active original main-vegetation classifier has ten codes, while overstory has
nine. The nine common codes map to identical original concept names; code 21
(Polypodiopsida) is main-only, consistent with the earlier actual raster category
inventory. This is source-classification consistency, not modern authority approval.
An initial inspection incorrectly expected ten overstory mappings and stopped;
the corrected inspection preserves the genuine ten/nine distinction. No scientific
source was edited to make the inspection pass.

## Rechecks actually run

Reused the unchanged reviewed derivative ZIP, SHA-256
`8d5072273766edbe05503e53f38c55b964a5a630d024be93218803072a1e1ef0`:

- Both pinned/current-local actual Java parser tuples again report zero syntax
  errors for the Radiation subset and lookup table; positive controls pass.
- Existing original-versus-adapted Groovy probe again passes eight arithmetic
  cases for each and rejects attenuation/saturation mutations. This remains offline
  equation-equivalence evidence, not k.LAB execution or real-field acceptance.
- No model equations, independent arithmetic oracle or scientific validation
  thresholds were changed to fit the recovered raw raster.
- No Python scientific algorithm was ported, no package installed, and no full SDK
  regression suite is claimed for this research/documentation-only increment.

Geospatial source advertises an embeddable WCS adapter, which offers a possible
alternative to legacy ZIP import. Source advertisement alone is not an actual
current-runtime WCS encoding/import/permission acceptance test. The recovered
local vegetation payload path still requires a supported, verified binding.

## Decision / exact next gate

This option is **not yet a reproduction**, but has progressed beyond its previous
input-availability blocker. No new unavoidable production-Java change has been
demonstrated for the three-model Radiation chain.

Before registration/execution, obtain:

1. authoritative unit and temporal meaning for this named radiation product and
   an approved relationship to the original scenario;
2. reviewed compatibility definitions/mappings for the missing biological and
   taxonomic concepts, preserving original class equality and the 0–1 limiting
   factor versus percentage representation;
3. verified resource binding, target grid/classification-safe alignment, masks and
   permissions on an isolated unchanged-Java stack.

Those are explicit scientific/ontology/import gates, not permission to invent
default values, rescale by a guessed factor, or rename the models to compatible
but unrelated concepts. Any actual ontology or Java migration remains separately
scoped. The existing adapted equations need no new Python SDK implementation.

## Evidence

External root: `C:/Users/lumsd/AppData/Local/Temp/opencode/pal-radiation-retry-20261101/`.

- `production-coverage-probes.json` and both original XML responses.
- `radiation-crop-receipt.json`, `radiation-may2018-native-crop.tif`.
- `radiation-wms-metadata.json` plus saved capabilities response.
- `latest-worldview-check.json` and external unchanged upstream source snapshots.
- `archived-radiation-bindings.json`, `puerto-radiation-unit-context.json`,
  `original-classification-comparison.json`.
- `reviewed-checks.json`, both parser results/logs, `equivalence-recheck.log`.

All third-party scientific payloads remain external. Original failed results are
retained alongside new evidence, not overwritten by the successful alternate URL.
