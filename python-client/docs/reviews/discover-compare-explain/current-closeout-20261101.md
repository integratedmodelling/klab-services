# Current milestone pass — 2026-11-01

## Disposition

PR98 merged normally at `0ea89ded267fe047224366570edd231c6567ff5a` after
both original two-user evidence tests passed. PR100 merged normally at
`4af05e33a52cb666c4f73443707144f355ed523f` after its original sequential,
parallel and sibling-processor-failure cases passed together. Merge trees and both
parents match the accepted candidates; no bypass, retarget, force-push or branch
deletion. Earlier failed/UNKNOWN records remain preserved. An earlier PR100
untraced processing ProtocolError remains unexplained, not claimed fixed by later
passing controls. These are bounded acceptance results, not scientific accuracy
or arbitrary-provider certification.

PR102 remains **draft, unmerged, and not scientifically accepted**. Normal merge
`510b77050` incorporates accepted PR100 head `b130b33ab` without disturbing the
separate closeout worktree's unpublished planning/evidence documents.
The integrated baseline passed1,640 offline unit/integration tests; after the
permission-test correction,1,643 pass, with one existing Rasterio warning. Fatal
lint and wheel/source-distribution builds also pass. This is not live milestone
acceptance; the full workflow coverage gate is checked separately.

## Permission-test correction

The milestone's private-access test previously checked evidence/export denial but
never required private handle restoration itself to fail. An offline regression
demonstrated the false pass when restoration exposed metadata and later queries
denied access. The test now requires restoration denial before those independent
checks. Invalid authentication is not permission denial, and the positive-control
session is released even if the assertion fails. Synthetic regressions are not
live permission evidence; the complete milestone suite must still run.

## Missing evidence and concrete unblock path

1. **Independent scientific inputs:** supply a real four-case fixture conforming
   to `tools/workflows/milestone.py::load_fixture`: two genuine request variations
   across two resolutions, every independently expected cell/mask, units, CRS,
   transform, dtype and justified tolerances; reference source/revision/hash;
   authority code and independently expected concept/parent; recorded model/call
   node identities and deployment identities. The bundled unit-test dictionaries
   are synthetic and cannot be promoted to scientific references. The published
   terrain model is a fractal generator, not an independently known raster oracle.
   No suitable live reference fixture was identified in the available fixture files.
2. **Larger-grid reference:** supply a compatible independently referenced larger
   fixture for the required correctness-first throughput procedure. Do not produce
   expected values by copying the SDK's observed outputs, rename identical cases,
   weaken assertions, or change ontology/scientific algorithms to obtain a pass.
3. **Verified deployment integration:** the acceptance adapter currently emits
   `deployment_verified=false` unconditionally. The delivery builder correctly
   refuses readiness without verified evidence. Implement and regression-test a
   provenance-validation path that checks actual service/binary/component/worldview
   identities against retained launch/asset evidence; declarations or a manually
   flipped boolean are insufficient. Existing private isolated artifact attestations
   are supporting inputs, not a reason to bypass this contract.
4. Once those prerequisites are verified, run the unchanged scientific/export/
   permission/restoration/notebook cases within an explicit finite workload, inspect
   each failure before any fresh attempt, then measure throughput only after actual
   correctness passes. Final delivery and merge remain conditional on all gates.

Ongoing live-test authority is already granted; the blocker is missing scientific
evidence and the attestation contract, **not another request for test permission**.
No PR102 observation Jobs were launched during this pass. Original services remain
untouched. The final Technical-page deployment and requested computer sleep remain
pending; do not sleep while work is blocked or a test/service is active.
