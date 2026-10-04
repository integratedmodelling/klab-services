# Production proposal schema validation

NetworkNT now validates uploaded proposal JSON/YAML against the bundled context-pack 1.3 Draft 2020-12 schema in the default Resources workflow validator. No endpoint or transport DTO changes are required by the IDE or future Vue editor. DOCUMENT_SCHEMA returns real PASS/FAIL diagnostics; missing/broken validator infrastructure stays BLOCKED. Instance paths and schema keywords identify structural errors, and successful checks include the exact proposal byte digest.

Review can retain a structurally invalid submission for correction. Acceptance reruns the real check and requires PASS. This removes the missing Java schema-provider gate; it does not discharge authoritative import context, ontology adaptation with imports, Reasoner or scientific review. Self-reported validation fields in a proposal are only data.

## Dependency choice

The reactor uses Spring Boot 3.4.4, Java 21 and explicitly managed Jackson 2.19.0. NetworkNT **2.0.4** is pinned only in the Resources module. Its [tagged POM](https://github.com/networknt/json-schema-validator/blob/2.0.4/pom.xml) targets Jackson 2.18.4 and SLF4J 2.0.17. The resolved reactor tree confirms Jackson databind/core/annotations/YAML all remain **2.19.0**, with SLF4J **2.0.17** and new transitive `com.ethlo.time:itu:1.14.0`. No Spring or Jackson upgrade is introduced.

This is a compatibility pin, not a claim that 2.0.4 is the newest release. NetworkNT 2.0.8 targets Jackson 2.22.1; 3.x requires Jackson 3. Selecting either would require a broader dependency upgrade/compatibility assessment. The [2.0.4 API](https://github.com/networknt/json-schema-validator/blob/2.0.4/doc/quickstart.md) supports Draft 2020-12 directly. Regression tests exercise its `prefixItems` and `unevaluatedProperties` behavior, not just schema loading.

The schema is compiled once from a server-selected bundled resource with local `$defs` references; clients cannot select or supply schemas. Network and file-URI retrieval is denied. This is not a strict classpath allowlist: NetworkNT's [SchemaLoader](https://github.com/networknt/json-schema-validator/blob/2.0.4/src/main/java/com/networknt/schema/resource/SchemaLoader.java) resolves classpath resources (including mapped metaschemas) before consulting the custom rejecting loader. Independent review reproduced classpath `$ref` resolution through the package-private schema-construction test seam; this does not demonstrate a client-request bypass of the fixed production schema. Input parsing preserves strict duplicate detection and a single-document boundary, rejects invalid UTF-8, and enforces the existing 4 MiB limit. Returned schema diagnostics are capped at 50 messages of 1,000 characters each (plus truncation notice). This caps response size, not total validator work; HTTP body/concurrency limits remain deployment configuration.

## Verification

The selected seven-module offline reactor runs the protocol, authorization, schema, isolated parser/adaptation, shared identity inspector and existing Worldview suites. New tests cover valid real hydrology YAML/JSON; missing nested fields; invalid types/enums; extra properties; client schema replacement; duplicate keys/trailing documents/malformed JSON; invalid UTF-8/size; Draft 2020-12 keywords; missing/broken schemas and denied HTTP/file references; and independent semantic gates.

The lifecycle test uses the real schema provider and explicitly synthetic non-schema providers: invalid submission -> blocked acceptance without persistence -> request changes -> corrected superseding revision -> stale candidate rejected -> exact revised candidate accepted with terminal artifacts. It proves the schema/protocol boundary, not scientific validity. The default real hydrology integration separately proves DOCUMENT_SCHEMA PASS while final acceptance stays blocked. A bundled workflow 1.1 regression asserts that all three bootstrap-comments declarations and final-comments use `application/vnd.klab.comments+json`, matching the established uploads and the review/final-stage convention at the inspected base. Persisted workflow 1.0 definitions remain pinned and may retain the historical editing-stage `proposal+yaml` mismatch; this change does not migrate them or introduce `comments+yaml` support.

Final offline reactor: **BUILD SUCCESS; 69 Java tests, zero failures/errors/skips**. Counts: shared ProposalDocument 2; WorkflowSchema 6; authorization 9; protocol 24; isolated candidate validator 5; NetworkNT schema validator 11; Worldview validation 12. Offline Python package suite: **14 passed**, zero failures/errors. Total: **83 passing tests**. Suite reports, hydrology check output and resolved dependency evidence are in `review-evidence/schema-increment/`. Full local reactor log: `C:/Users/Ferd/Documents/Codex/2026-10-03/task-7/backend-final-tests.log`. `git diff --check` passed.

Dependencies were resolved into a task-local writable Maven repository with Maven 3.9.6's chained read-only tail pointing at the existing cache. No shared Maven cache writes were needed. Reproduce after resolving dependencies with:

```powershell
./run-review-tests.ps1 -LocalRepository '<task-local-repository>' -ReadOnlyRepository '<existing-cache>'
```

Both repository arguments are optional for ordinary developer environments. The script runs offline and uses a task-local test home. `review-evidence/schema-increment/dependency-tree.txt` records the effective versions.

## Decisions and work still needed for completion

1. **Authoritative context contract.** Choose the provider and immutable revision/hash identifiers for current imported ontologies, including mandatory upper-domain/root context. Define invalidation when an import changes during review. IMPORT_CONTEXT remains BLOCKED until current snapshots can actually be verified.
2. **Read-only semantic validation.** Define an isolated Reasoner session over those snapshots and candidate bytes, with no saved-source synchronization or authoritative knowledge updates. Specify per-expression resolution/type/ancestry diagnostics and which semantic failures block review completion. REASONER and imported ADAPTATION remain BLOCKED; implication/detection is syntax-only, and consequence execution is outside this task.
3. **Candidate/action correspondence and package transport.** Agree attachment roles and a versioned manifest DTO, bind its digest to Candidate, and verify that generated ontology bytes implement the exact approved action set against its base revisions/preconditions. The offline four-artifact pilot and current ID/checksum binding do not establish this relationship.
4. **Scientific decision policy.** Define required expert qualifications, assignees/quorum, how action-level disagreements or deferred issues are resolved, and which bootstrap coverage shortfalls require an explicit waiver. Counts are coverage targets, never fabricated evidence or consensus. Parser/schema PASS cannot make these judgments.
5. **Application and handoff semantics.** Decide whether acceptance means review completion only or also authoritative application. Application needs an idempotent transaction/outbox boundary and a defined PR destination/ownership/permissions policy before callbacks can claim success. APPLICATION and PR_HANDOFF explicitly remain BLOCKED.
6. **Production access and persistence proof.** Verify Role.USER/owner/assignee/public-read through actual authenticated HTTP tests; decide multi-process CAS/catalog transaction strategy and the migration/archival policy for pinned old workflows. Aggregate terminal artifact persistence is protected locally, but catalog projection and external application are not one atomic transaction.

These decisions do not block structural proposal validation or editor integration. They do block a claim of complete production acceptance/application support. No merge, deployment, authoritative ontology change or automatic PR publication by the service is included.
