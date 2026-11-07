# Native-shaped commit-cache inspection review

Parent PR297 verified merged53ee5bed9/exacta2c54ca9a/all29checksPASS; original runner
timeout retained and sameartifact full390case233.376sPASS separately recorded.

This slice reads an existing native GET/api/v1/commit?id endpoint, not a proposed
scientific commit write. Exact currentroot/Runtime/client/transport/credential fences
and before/after Runtimeidentity checks bracket bounded reads. Review caught a
post-read capability lookup could occur before rebinding check; added pre-capability
fence so changed endpoint/credential never gets an implicit rebound read.

Reports preserve exact requested IDs/public timestamp/owner, native change groups/
link triples and immutable bounded credential-filtered raw auxiliary records. Missing
groupsNone versus suppliedempty groups explicit; duplicate group members refuse,
reported added/modified/deleted groups need not be disjoint. Native link endpoints
can name earlier assets: no invented fullgraph/endpoint membership or contiguous
revision assurance. Nullcacheentry explicitMissingAssetError; no synthesized empty
delta, next-ID scanning, JobID-to-commit mapping or permission authority.

Native sourcecache200entries/10minuteafteraccess is inspected configuration, not
promised deployed/durable retention. Fixture128entry cache/explicitexpiry is separate
finite policy. It materializes only after synthetic observation completion and reports
im:commit-id, current-principal denial and ownedrootcleanup. No native scientific
execution, mutation, source repair or k.Actors implementation inferred.

Source verification41SDK100%unexcluded newmodule/allchangedSDK;2actualpublic
sync/nativeasync HTTP workflows and13supplementaryfixturecases100%;bodymypy/fatal
lint/49governance+queue+workfloworderingPASS. Initial39controlrun37PASS/2FAIL caught
Runtimeidentity drift raising HandleIdentityError (safe refusal) rather than expected
ProtocolError; sanitized facade error classification corrected. Initial39coverage
98.53%expandedto41/100%with explicit badroot/client checks, no coverage exemptions.
Native facade now owns its rootfencing rather than deriving from proposed shared-read
management, so native reads require no misleading experimental grant semantics.
Afterartifactfreeze explicit publiccachecleanup assertion initially looked for the
counter at top-level rather than counts.commit_reports; both workflows raisedKeyError.
Counter was already included in the allcounts-zero assertion. Corrected testonly to
the actual published shape, not a production cleanup change; final2publicHTTPPASS.

Prepared approved read-only native testNOT_RUN; whole fullreference checkpoint/delta/
gap-resync reconciliation remains independently implementable Pythonwork and active
RC-P3.1/CAP-10/CAP-11 after any primitive delivery. Existing GraphProjection/old
ID-only reference checkpoints keep their separate meanings. JAVA-GAP-0016 not closed.
Final freshinstalled4903SDK/405simulator/72worker/allreference/39governancePASS,
zero skips/errors/failures;65SDK/32simulator/5worker rawmoduleparity, no simulatorin
production.10focusedCIgates+actualorderedselectionPASS;17linkedPASSvs17nativeNOT_RUN.
PR298finalreceipt/exactheadCI/ordinarymerge pending, no whole-row acceptance claimed.
