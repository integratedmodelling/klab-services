# Contributor review: routing CI pinned-plugin bootstrap

2026-11-05 UTC; author/CLI review, not independent human approval.
Base554b5e30b9433300610ff66c4f79f6d564018827; branchfix/ci-routing-plugin-bootstrap.
One demonstrated CI prerequisite; product/SDK/Java/source fixtures unchanged.

## Defect and bounded correction

PR207's routing run41970836627/job127058446595 passed53selected source-matched
tests, then failed resolving org.integratedmodelling:klab.product:1.0.1-SNAPSHOT
when reaching runtime-server. The duplicate run41970841994 passed with a populated
cache; neither result authorizes ignoring the failed check. Normal PR207 packaging
and its9focused project-denial tests passed independently.

Reuse the **same** official immutable source pin accepted by PR202:
0c80acb1f16a1d405f888cf99286d6e365293559, samepluginversion1.0.1-SNAPSHOT.
Build current reactor API first, then the plugin. No artifact version change,
publication, byte-equivalence assertion, test removal or coverage relaxation.
The routing verify/JaCoCo command and canonical method coverage check remain exact.

## Review and validation contract

CI-only; Pythonwheel/scientificdemo N/A. The earlier PR202 private cold-M2/API/
plugin/full18module packaging evidence establishes this bootstrap mechanism,
not a predeclared pass of this workflow. Validate current YAML, exact source pin,
nonpersistent secondary credentials, unchanged existing verify/coverage steps and
new final-head hosted routing result. Normal packaging/Python/Jenkins checks remain
required where selected; no bypass. No local service/build process started here.

Source/diff inspected for usefulness, architecture, reproducibility, maintainability
and contributor fit. Scientific meaning/asset permissions/state/concurrency N/A:
no product behavior or remote data operations change. Caching remains only an
optimization, not an artifact source. No reviewers assigned or overlap inferred.
Ready for bounded maintainer review after YAML checks; hosted final-head checks
and normal eligible merge/ancestry/postmerge/owned branch retirement remain required.

PR207 stays OPEN/UNSTABLE until this independent prerequisite is accepted and
incorporated, then its own final-head checks rerun normally. Its source/review and
the contract-only Q6 branch remain preserved; no unaccepted stack is created.
