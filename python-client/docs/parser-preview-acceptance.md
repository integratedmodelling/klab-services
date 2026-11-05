# Actual Resources source-preview acceptance

The `parser-preview` named functional suite exercises existing SourcePreviews
against the maintained Resources service, rather than a DTO-only wire fixture.
It sends separately labelled synthetic namespace, ontology, strategy-document
and behavior source; checks family, URN, service identity and both UTF-8 hashes;
permits only explicit edge-whitespace trimming; and requires invalid input to
fail without becoming a successful receipt. Blank-source policy must reject
before HTTP. All families are attempted independently and failed records retained.

No remote authoring, persistence, import, publication, compilation, agent start,
scientific execution or knowledge revision is asserted. The source is new test
material, not an automatic historical-language/ontology migration. A valid parser
receipt does not imply semantic closure, an authoritative knowledge revision,
permissions for authoring, or correctness of a scientific model.

```powershell
<installed-python> python-client/tools/run_reliability_acceptance.py `
  --server-repo <explicit-compiled-server-checkout> --java <jdk21-java.exe> `
  --components <two-pinned-component-manifest.json> `
  --state-dir <fresh-private-external-directory> --scenario parser-preview
```

Use the existing owned TEST fixture only. The runner pins compiled source,
components and minimal synthetic worldview, generates private TEST credentials,
fails on occupied reserved loopback ports, and closes only captured owned
processes. This is not production Hub onboarding or historical scientific admission.
Q1's unaccepted recovery harness is not a dependency of this suite.

Verified2026-11-05 on implementation96f6b0b24: `q5-live-v2` passes all four
families and their malformed-source refusals. Namespace/ontology/strategy retain
edge whitespace; behavior trims it explicitly. For invalid behavior, a null/
incompatible response causes ProtocolError rather than a successful receipt;
rich syntax diagnostics are not claimed. Knowledge revision remains unestablished.
The installed2862-test/16coverage/lint/package checks also pass; hosted merge
acceptance is separate. The initial failed fixture run remains preserved.
