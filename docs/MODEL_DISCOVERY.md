# Model discovery and contextualizer preparation

Model discovery has two explicit request-stage modes. They are not model metadata and confer no runtime permission.

* `EXECUTABLE` is the default for existing callers. It requires installed-method input inspection, exact source-backed typed-input coverage, and full observable semantic matching.
* `PREPARABLE` is requested by the Resolver using `modelDiscoveryMode: "PREPARABLE"`. It lists source-valid coarse candidates before normal contextualizer prerequisites are prepared. It preserves type enumeration, visibility/access, spatial/time and coverage constraints, source validation, native-bearer obligations, and exact saved dependency occurrence proof. It does not inspect unavailable installed methods and does not calculate semantic distance.

Both document and legacy SQL catalogs honor the mode. An absent parameter means `EXECUTABLE`; an unrecognized value is rejected. The parameter travels as the enum name, not a Java-only enum object. Existing direct matching consumers remain strict. The production on-demand semantic MODEL query is the Resolver's query path.

The Resolver must obtain a source preparation witness before controlled prerequisite preparation. `ModelValidationGate.PreparationReceipt` is a distinct local type with a private snapshot; it cannot be used as an executable admission. It binds the original named model, saved namespace source hash/version, executable Model fingerprint, Reasoner identity/revision, and source pending obligations. It requires known nonblank saved source. Preparation retains normal source reconstruction and native validation; it skips only installed input inspection.

After the existing component prerequisite mechanism runs under its existing load policy, full installed inspection and semantic admission must succeed against the same source/Model/Reasoner snapshot. An unavailable inspection remains unavailable, never known empty. Source, computation, alias, pending obligations or semantic generation changes during preparation invalidate the witness. A later validation cannot relabel a changed snapshot as the old plan.

Only the resulting full admission may enter semantic distance and ranking. The existing contextualizer prerequisite ResourceSet is retained per compiler/submission and reused for the selected model's provenance and execution prerequisites; there is no global installation or source cache introduced here. Preparing candidates before ranking can inspect/prepare more source-valid candidates than the formerly selected-only path. The existing runtime preparation mechanism also handles resource/adapter prerequisites and must retain its load-policy checks and provenance. Pure catalog listing and installed inspection do not invoke or install contextualizers.

This ordering does not convert ordinary observations into `MemberQualityCollection`, manufacture a pending source obligation, waive native-bearing checks, or make incomplete source proposals executable. A typed collection consumer still requires exact source-backed pending ports and the existing bounded native-collection result contract.
