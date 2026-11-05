# TA-3 N review — before real execution

Contributor self-review under KLAB_CLI_Contribution_Review.md, not maintainer sign-off.
Base937c758b62743a0ab68e48792870a9261cf7cc87, independent branch
feature/python-scientific-scenario-n; no cascading contract PR dependency.

Recommendation: **Needs actual acceptance evidence**. Existing N verifier and fixture
are reused unchanged in their scientific expectations. Added explicit report checks,
successful baseline-model/observation association, controlled metadata rejection
copies, unknown-submission no-replay cleanup handling and a hashed manifest.
18focused oracle/plan tests PASS; Ruff clean. No Java/product/model/input edits.

Independent oracle: pre-existing north-first matrix, not extracted from writer/TIFF.
Exact binary fractions, asymmetric cells/axes, float64 sentinel, zero/missing mask,
native sparse offsets and geographic centres/transform. Model source delegates to
unchanged single-use Java TEST producer; actual test source/class and model hashes
must be bound. Not terrain/provider accuracy, multi-shard, temporal or L5 replication.
Corrected unpublished exporter9844147666bbb53071dfcd54db284aef48be9c79315b73415e74af8c1d70126f
is explicit, not adopted component#11.

Negative metadata controls copy exported bytes, change only temporary local copies,
and require rejection of missing/wrong units, CRS and nodata. These are L2 negative
controls, not Java mutation/negative applicability or repair of original exporter.
Original artifact hash must remain unchanged. No actual computation yet.

Thin TA-2 adapter may be invoked from its separate worktree with explicit
workflow-package pointing to this accepted-base checkout; exact adapter/source/wheel
identities must be recorded independently. No combined merged candidate implied.
Retain failure diagnostic and NOT_RUN checks. Normal hosted delivery blocked by
recorded ref/rules failure; no new push/rules workaround. Need actual pinned Java
values/plan/artifact/cleanup evidence, then exact review/update and normal eligible
merge once repository evaluation is restored or remedy authorized.
