# Quantitative containment implementation boundaries

`<identity> within <identity> <extensive-physical-property>` retains the original
measurement type and units. The identity slots are ordered and independent of
ordinary trait qualification, native inherency and observational containment.
The grammar, semantic syntax, service beans and semantic builder preserve them.
The reasoner carries the ordered identities as semantic annotations on a generated
measurement subclass, without introducing physical parthood or existential fillers.

## Deliberate limits

- Matching currently requires identical ordered identity URNs. Subsumption,
  authority equivalence or conversions between identity pairs need an agreed rule.
- No runtime numerical extraction, fraction calculation or allocation is implemented.
  A compatible model must produce the partial measurement explicitly.
- Applying a second quantitative `within` to an already partial quantity is rejected.
  Its composition contract has not been agreed; the implementation does not replace
  the inner identity pair or accumulate conflicting annotations.
- Eligibility follows authoritative membership in ODO's `ExtensivePhysicalProperty`
  hierarchy. There is no per-property or substance catalogue. Length, Area, Duration
  and valid new or inherited specializations follow that same criterion.
- Syntax acceptance alone does not establish eligibility: generic quality descriptors
  are admitted syntactically and the reasoner checks ODO ancestry. Intensive and
  nonphysical qualities fail semantic validation even if they carry extensive flags.
- Observational containment declaration validation remains its separate contract.
- Assemblage semantics are outside this change.

## Core ontology alignment

Energy requires a coordinated ODO core that classifies it under
ExtensivePhysicalProperty and retains joule units. The quantitative patch corrects
the language table but does not replace the bundled ontology. The separate ODO
release/consumer change must accompany integration; a private paired API overlay
alone is insufficient. No historical ontology release is changed by this patch.

## Eligibility decision

The agreed checkpoint is ODO `ExtensivePhysicalProperty`, without an additional
substance whitelist. The provisional catalogue has been removed. The active ODO
closure controls semantic acceptance; missing ancestry proof fails closed. Nested
quantitative composition still needs a separate contract before it can be admitted.

## Validation environment

See the task's validation report for executed tests and remaining build limitations.
Generated parser sources must be rebuilt for every language inheriting Observable.
Do not combine the updated syntax adapter with older inherited parser artifacts.
