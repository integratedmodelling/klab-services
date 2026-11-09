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
