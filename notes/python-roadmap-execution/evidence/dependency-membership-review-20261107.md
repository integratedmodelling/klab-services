# Native-shaped dependency membership development review

Parent PR294 verified mergedcc71b0991. This is local review of a useful native
membership slice, not full source-bound graph acceptance or maintainer approval.

- Existing Resources.resolve is used. All supplied ResourceSet groups are retained
  with native array ordering, exact root identity, provider, reported version,
  nullable timestamp/optional/project and credential-filtered immutable metadata.
  SourcePreviews identity/credential/transport fences are composed, not duplicated
  or downgraded; async methods await native AsyncClient directly.
- Native source documents closure but inspected addDependencies leaves several
  categories unfinished, remote namespace lookupTODO, no edges or complete witness.
  Snapshot deliberately says closureUNKNOWN/edgesUNAVAILABLE. Missing groupsNone,
  absent version/access remains unknown, no inferred graph edges or source hashes.
- Native result/group copies can have different timestamps/project metadata. Source
  review caught overstrict whole-descriptor comparison; only contradictory known
  versions refuse across groups. Within-group duplicate identities refuse.
- ProviderURLs are inert validatedHTTP(S), no credentials/query/fragments, and are
  never followed. Configured credential reflection refuses; secret-named metadata
  and native accessKey aren't exported as public permission authority. Native
  availability claims are not access leases or proof of scientific execution.
- Total memberbudget spans every array, including rootduplicates; no truncation,
  invented native pagination or implicit fetching. Scope/Resources identity drift
  discards results with no automatic retry or fallback.
- Independent fixture contains declared ordered group arrays and authenticated
  test-only version advance. Ephemeral only, currentprincipal/root denial and owned
  cleanup; it creates no dependency resources. Prepared native readonlytestNOT_RUN,
  not source mutation/graphdb loading/ontology repair.

Source gates:34SDK controls with100%unexcluded wholemodule/allchangedSDKcoverage,
2public sync/nativeasync HTTP workflows;1supplementaryfixture test100%covers finite
routes/principals/durable refusal/version preservation. Bodymypy/fatal lint and
48governance/queue controlsPASS. Initial19SDKcoverage94.80%, then27controls99.40%
expanded to34/100%without exclusions; reflectedcredential error-class mismatch and
one mypy empty-map annotation corrected, no secrecy guard weakened. Native DTO
already checks rootresponse object/diagnostic array, so duplicate unreachable checks
were removed, not the public validation boundary.

Fresh installed artifact verification is running. This slice does NOT discharge
original dependency requirement: source-bound complete finite reference graph,
pagination/cycle/gap/access receipts and independent acceptance remain Python work.
Keep RC-P4.1/CAP-13 active after any membership PR; never waive it as native blocker.
