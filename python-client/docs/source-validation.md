# Revision-aware namespace diagnostics

`SourceValidation` / `AsyncSourceValidation` use the existing Java Reasoner
`POST /api/v1/validate/document` contract, not an experimental replacement.
They accept an explicit `SourcePreviews` parser receipt and an editor-owned
`document_version`. Parser success is not semantic validation.

```python
preview = SourcePreviews(client).parse(namespace_source)
validator = SourceValidation(client)
report = validator.validate(preview, document_version="edit-3", expected_revision=7)
```

The async counterpart awaits `.parse` and `.validate` directly. An omitted
`expected_revision` captures the current **known** Reasoner revision for this one
request; it never guesses a revision or accepts missing consistency/session/content
identity. The receipt pins Resources URL/service, original and parsed source hashes,
document URN/version, and Reasoner URL/service/worldview/revision/session/content.
Capability and binding checks surround validation. Revision drift rejects the
response without silently retrying. Call `validate` explicitly against a newly
selected revision/source version to revalidate.

`status="COMPLETE"` means diagnostic evaluation completed, **not that source is
valid**: Error diagnostics can still be present. Immutable `raw["notifications"]`
preserves those errors; noncomplete server statuses are not replaced by parser
success. Reports grant no publication, authoring revision, executable-model,
runtime-context, scientific or hidden-evaluator authority. Already returned reports
are historical snapshots, not live knowledge leases.

## Deliberate limits

- Namespace snapshots only. The inspected native ontology validation path can
  synchronize saved ontologies or repair/load worldview knowledge. This facade
  refuses ontology/behavior/strategy semantic validation rather than authorize
  that side effect. Ordinary read-only parser previews remain separate.
- Always source diagnostics: `executableModels=false`, no execution context,
  classifier-preparable admission or pending dependency proofs.
- Exact parsed source hash/version confirmations required. Optional parser edge
  trimming retains both original and parsed hashes, rather than equating them.
- No automatic publish/save/compile/observe, local semantic checker or fallback.
- No native calls were performed here. Native compatibility remains **NOT_RUN**;
  source custody is in `evidence/source-validation-authority-20261107.json`.

The opt-in native test `tests/functional/test_source_validation.py` requires
`source-validation.json` under the prepared fixture-state directory. It must set
`allow_namespace_source_diagnostics=true`, `namespace_only_no_ontology_repair=true`,
pin `native_build`, and supply approved namespace `source`, `namespace_urn`,
`document_version`, `knowledge_revision`, `expected_status` and
`expected_notification_count`. It never imports or publishes source, creates a
Runtime observation, or asks the ontology validation path to synchronize knowledge.

The independent `source-validation` simulator admits two literal namespace snapshots
and returns a declared error for one of them. An authenticated test-only knowledge
advance verifies stale refusal and explicit revalidation; it is not a compiler,
ontology engine, saved-source store or native acceptance test.
