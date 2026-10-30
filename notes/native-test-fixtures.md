# Native deterministic test fixtures

`klab.tests.fixtures` is an internal contextualizer library used only by canonical native
testcases. Its discovery metadata is registered normally with the Runtime libraries. Calling
one of its functions does not grant permission to execute it.

`inspector.fixtures(context)` first requires an actual active `TestCaseScope`, then verifies
that it owns a registered context with the same native DigitalTwin object and scope ID and
that the context exposes the local `RuntimeService.Testing` capability. A private object with
no public constructor and no serialization contract is installed in the context's local data.
It is tied to that testcase agent, context and twin. Strings, booleans, metadata maps and remote
scope transport cannot construct this permit. Derived local scopes retain the same permit.

The permit is AutoCloseable so normal context closure revokes it. Testcase scope disposal and
`inspector.clearfixtures(context)` also revoke it. A closed permit remains invalid in any
already-created scope copy. Every invocation checks current ownership and lifetime again.
There is no HTTP endpoint, general operation API or change to ContextScope.submit.

The functions use normal execution, builder and transaction machinery:

- `empty()` receives the normal `Data.Builder` and generates no members. The ordinary
  INSTANTIATION execution must succeed and record discovery completion; the function itself
  writes no graph data, provenance or completion receipt.
- `failclassifier(after=<predicate family>)` is an ordinary Concept-returning classifier.
  It requires concrete attribution from the supplied earlier family on its actual current
  member before recording `prior`/`classifier` counters and deliberately throwing. This makes
  the two-family rollback assertion non-vacuous, without hard-coding a fixture ontology.
- `failconsumer()` consumes the actual model-local `selected` Observation input, validates it
  through Runtime member enumeration, then records its counter and deliberately throws.

Invocation counters are private in-memory test evidence, independent of the model transaction.
They intentionally survive rollback. The canonical test separately asserts that native graph
assets, member semantics, attribution and contextualization history are unchanged. These
counters must never be described as committed provenance or as a successful model result.

For example a native testcase grants the permit, snapshots native state, submits a deliberately
failing model, checks positive invocation evidence plus unchanged native state, then retries a
normal model in the same context and clears the permit. A successful empty-discovery case
asserts real typed completion and no extra invocation on satisfied reuse.
