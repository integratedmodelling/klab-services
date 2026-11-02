# Native model transport: nested flush ownership

## Bounded defect and correction

The reflective domain serializer used `JsonGenerator.writeObjectField` for each
field, including `@CLASS`. For nontrivial values that path invokes the generator's
`ObjectCodec`, re-entering `ObjectMapper.writeValue`. Each nested mapper call can
flush the transport, regardless of the **outer writer's** `FLUSH_AFTER_WRITE_VALUE`
setting. This is a transport/lifecycle bug, not a scientific model change.

A synthetic namespace containing20 empty native `KimModelImpl` declarations
produces380 flushes on the integration base. A caller-controlled writer configured
not to flush still produces380. A bounded-output fixture which rejects unexpected
nested flushes fails with `JsonMappingException` wrapping its controlled
`IOException`. Baseline failures are retained.

The correction keeps field values with the active `SerializerProvider` using
`defaultSerializeField`, and writes the class identity as a string primitive using
`writeStringField`. The outer writer/generator retains flush/close ownership.
Null omission, reflective field selection, class tags, proxy/local-view rejection,
exception propagation and domain/model values are not intentionally changed.
No dependency, scientific algorithm, worldview, resource mapping or deployment is
changed. The tests do not evaluate model expressions.

The first partial correction still flushed42 times because `@CLASS` continued to
re-enter the mapper. Those failed tests are retained; both field paths are now
corrected. This is not a claim that swallowing transport exceptions is acceptable:
actual output failures must still fail rather than return truncated success.

## Original live failure is not proven fixed

The earlier genuine-Job attempt5 passed service/semantic/provider readiness,
created two contexts and obtained two submission receipts. Resolver logged region
`NO_MODEL` and elevation `FAILED`. No numerical readback was verified. A final
publisher-socket cleanup assertion masked the original Job-path exception. These
failures and receipts must remain preserved; no automatic mutation replay.

Resources logged `HttpMessageNotWritableException`/`JsonMappingException` with a
`ClientAbortException`/I/O cause. That is **not proof of a bad model field**.
Offline native parsing/adaptation/semantic visiting serializes all fixture models,
all three namespaces and the ontology collection successfully. Baseline and fixed
class-provenance-verified runs produce byte-identical hashes for all11 native
model/document/collection records. A fresh real-service read-only probe against
the unchanged **pre-fix** backend also passed all8 selected reads (model, three namespaces, ontology,
and model/namespace/ontology lists), with0 sessions,0 contexts and0 scientific Jobs.
Its14 recorded JVM identities are independently absent, with no corresponding
visible windows; unrelated services/terminals were untouched.

Consequently this PR's acceptance concerns the reproduced flush-ownership defect
and unchanged transport content, not the still-unproven primary cause of attempt5.
Drafts118/117/108 still require real successful Job/data/notification and live
queue/socket cleanup evidence. CI or this bounded transport correction cannot
substitute for that acceptance. The publisher lifecycle defect remains in117's
separately authorized scope.

## Regression checks

`ModelTransportSerializationTest` covers nested flush bounds, the outer writer's
no-flush policy, controlled output failure, model roundtrip, and one actual
loopback HTTP response with a bounded-flush output stream. The loopback fixture
uses an ephemeral loopback-only port, a request deadline, no proxy and explicit
server/client cleanup. It starts no scientific services and submits no Jobs.

Run with the existing domain serialization guard tests:

```text
./mvnw -B -pl klab.core.common -am -Dtest=org.integratedmodelling.common.data.jackson.*Test,SerializationTests,DataflowSerializationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The dedicated workflow runs this same set. General build, final-head CI and local
review are additional gates; author-conducted local review is not independent
maintainer approval.

Current local evidence:70 selected tests pass (including all5 new cases), and both
changed statements have zero missed instructions. The broader candidate run has
322 tests/1 failure versus clean base317 tests/the same1 failure:
`QuietHttpPostTest.requiredCommandsStillReportHttpStatusAndStructuredErrorDetails`,
same assertion type and message. This is retained, not repaired or waived. The
18-module package build passes with tests skipped in that separate packaging pass;
the focused tests are run independently. No complete-repository green test claim.
