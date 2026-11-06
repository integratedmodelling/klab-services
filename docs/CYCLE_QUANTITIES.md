# Cycle quantity consumer contract

ODO 0.2.2 supplies abstract intensive Frequency in Hz. IMOD gives physical
Frequency and Period the same CyclicProcess bearer; Period retains Duration
ancestry and seconds. Irregular Events and occurrence rates are separate.
The ontology does not compute reciprocal values.

SemanticsBuilder validates a quality modifier against its established parent's
inherited bearer before constructing the qualified concept. Looking up the new
subclass could miss that restriction before classification and accept an
incompatible Event, Subject or unspecialized Process. The existing construction
path for non-quality concepts is preserved.

CycleQuantityIntegrationTest exercises real source parsing, semantic construction,
inheritance, a valid cyclic subtype, incompatible bearers and unit dimensions.
The exact ODO candidate bytes are bundled, and import/admission tests use its
immutable source pin and digest. Live URL verification stays opt-in until
publication. The same numbered ODO source history must be published before
activating the IMOD import. This change starts no service and publishes nothing.

Duration and Period are intensive in both the paired Languages categories and
ODO ancestry. Ordinary event duration remains a measurable quantity in seconds.
Quantitative `within` requires extensive physical-property ancestry and therefore
rejects Duration, Period and their inherited specializations, even with stale
EXTENSIVE consumer flags. Source parsing alone does not establish that eligibility.

This alignment adds no numerical aggregation implementation, automatic summation,
reciprocal calculation or temporal decomposition rule. Existing measurement and
unit-conversion paths remain in use.
