Worldview validation profiling

WorldviewValidationProfile.java is a Java 21 source-launch harness. Run from the
repository root with the Reasoner runtime classpath (updated target/classes before
the distribution jars). Arguments:
  <Resources list/WORLDVIEW JSON snapshot> <private asset directory> <knowledge directory> [mode]

Modes:
  semantic-only: isolate semantic validation; remove authority bindings and bridge
    configuration, replace CHEM:CID_962 and TAXA:[6MB3T] with their local identity
    roots. Authored predicate applicability clauses remain intact.
  live-owner: authenticate through the normal service-owner certificate flow,
    obtain a current worldview from the local Resources client, and resolve the
    hosted authority components using that owner. Saves the captured worldview
    beside the private asset directory. Never changes the live service workspace.

Use -Xmx1g consistently when comparing runs. Enable phase tracing with
-Dklab.reasoner.traceCandidate=true and JFR with
-XX:StartFlightRecording=filename=<recording.jfr>,settings=profile,dumponexit=true.
Report wall time AND main-thread CPU time, diagnostic equality and reasoning
query counts. Wall time varies substantially with machine load. Semantic-only
runs exclude network authority work and are not full startup benchmarks.

Deployment cache retention:
Preserve <klab data directory>/semantic-candidates/authority-bootstrap/persistent,
including catalogs and identities. Default data directory: service account's
~/.klab; klab.data.directory may override it. Preserve service ownership and write
access. Candidate capture/staging directories are temporary. Preserving this
cache avoids repeated authority capture when provider/configuration/owner proofs
remain current; it does not bypass fresh authorization or semantic validation.

Resolution profiling

ResolutionProfile.java submits staging.vxii.basic.cotechino through the existing
authenticated stack in a fresh ONE_OFF twin per run. Run from the repository root:
  java -Xmx768m -cp "<runtime distribution directory>/*" tools/validation/ResolutionProfile.java 2
The optional argument is the number of runs. The harness closes its own twins;
it does not boot or shut down the stack. Wait for Resources indexing to settle
before comparing warm runs. PROFILE elapsedMs includes submission and follow-on
execution; use Resolver's "Resolution attempt timing" for the main resolution.

Reasoner status metadata exposes semantic.validation.sourceProofs and
semantic.caches (hits, misses, loadNanos). Client status is polled and may lag;
use /reasoner/public/status for a fresh final snapshot. Capture JFR on the Reasoner
to separate inference CPU, actor-lock waiting and HTTP waits. Avoid simultaneous
builds/indexing when measuring steady-state timings.

Source proofs and inference results are bounded, in-memory, accepted-actor caches.
They do not add IDE startup validation or need deployment persistence. Static
source reuse rechecks dependency authorization; an actor-local captured proof
never upgrades a portable unversioned witness or grants executable admission.
Executable context checks remain active. Closure changes invalidate inference
answers and local authority proofs; expired captures cannot authorize reuse.
Clause/hierarchy profiling:
semantic.caches also includes hierarchy, equivalence and specialization counters.
Hierarchy answers preserve equivalence groups and return owned copies. Only
complete clause-specialization searches are memoized; recursive visited-state
answers are not reusable. Ontology edits, backend changes and manager ontology
replacement retire affected answers. Private compilation indexes named parents
on demand; identity requirement discovery uses OWLAPI's property index.
For startup phase timings, launch the Reasoner with
  -Dklab.reasoner.traceCandidate=true
and record JFR from JVM startup. Measure readiness separately from Resources'
initial namespace indexing and from resolution. Do not compile concurrently
with measured startup or resolution runs.
The specializationParents counter covers immutable asserted-edge snapshots, not
subsumption answers. Each traversal retains its visited set and DL checks.
Private recursive searches verify manager ontology identity on entry and exit;
axiom-change listeners and cancellation remain active inside the search.
