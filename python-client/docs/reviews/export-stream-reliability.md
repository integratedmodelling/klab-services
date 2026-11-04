# Contribution review: actual-socket export reliability

## Recommendation and scope

**Ready for maintainer review for the explicitly synthetic HTTP workload.**
Author-performed read-only review, not independent human approval. This adds
missing acceptance evidence to the existing atomic exporter, not a duplicate
transport, new SDK abstraction, retry policy, chunk protocol or Java exporter.
No remaining demonstrated blocking defect was found within that scope. Required
hosted checks and normal merge still apply.

Base: `4a54145de`, integration `feature/python-experiment-runner`, containing the
verified PR197 merge. Candidate/source/artifact identities and exact commands are
recorded in the queue evidence root's `f2-gates/identity.json` and `commands.json`.
The complete diff consists of a standalone shared workflow, isolated subprocess
integration test, usage and the existing acceptance map's F2 contract. There are
no production SDK/Java/dependency changes; all43 SDK source modules must match the
installed wheel. All original exporter coverage gates remain enforced.

Final workflow/demo source: `4dc36476a2e797dd5918068bfdb25c6c74211b14`.
Installed offline gates pass **2839 tests**, one retained Rasterio warning,
all16 coverage gates, lint and wheel/sdist builds. Wheel SHA256
`4a05d02fc7e62b180474a0719f0f97c4c2fbeb40f46c589d9d9781480a1f4c96`;
sdist `52a1b3f8997edd2c4790e530fa792e850f5c6f431ffaab36c5c75337321afe93`.
The final report-only field clarification does not alter SDK bytes or transfer
behavior; its complete64MiB run is separately recorded in `f2-final-64mib`.

## Scientist-facing outcome and evidence

Scientists/maintainers can reproduce actual network/failure and memory behavior
without trusting only MockTransport tests. The installed SDK downloads64MiB of
known binary bytes over real sockets, verifies exact file bytes, and contrasts a
deliberately eager HTTPX positive control.64MiB is a useful modest transfer volume
(roughly4096×2048 float64 cells), but the generated body is **not a scientific
format, Java model output or provider accuracy test**. CI uses8MiB through the same
paths. Metadata warmup/client creation/import allocations are outside the stated
incremental measurement. RSS is sampled every2ms and remains an observation, not
a universal bound or SLA; the selected streamed path must stay below4MiB traced
Python peak, while the buffered control must expose a body-sized allocation.

Actual final Windows Python3.12.7 measurement: **67,108,864 bytes published**;
streamed Python peak **664,481 bytes**, RSS growth **819,200 bytes**. Eager positive
control Python peak **134,510,729 bytes**, RSS growth **73,834,496 bytes**. All six
fault outcomes match the table below with zero new published bytes, previous-file
preservation, no temporary files and one export attempt. These numbers describe
this execution only, not a general capacity/performance promise.

All six negative cases check one export GET only, no false completed artifact,
preservation of the previous valid file, temporary-file cleanup, response-handler
completion, local server/thread closure and no credential echo:

| Injected condition | Required supported outcome |
|---|---|
| Peer closes after one partial chunk | TransportError |
| Read stalls after a partial chunk | TransportError, existing transport diagnostic policy |
| User interrupts after a partial disk write | Original KeyboardInterrupt, not server cancellation |
| Incorrect media response | ProtocolError |
| Declared body exceeds requested budget | ProtocolError |
| HTTP403 | AuthorizationError from actual HTTP status, not Python role inference |

Declared body size and successfully published bytes are reported separately; a
failed64MiB declaration is not claimed as64MiB transferred. The cancellation case
uses a bounded synchronization with the actual temporary-file write, not a guessed
sleep before a request. It deliberately interrupts only its standalone child
process's main thread and must not be imported into an active notebook kernel.

## Validation and identities

Raw receipts and results are under
`C:/Users/lumsd/AppData/Local/Temp/opencode/scientific-reliability-queue-20261104/`.
`f2-gates` records installed-wheel source identity, source/artifact SHA256, commands,
offline regression/coverage/lint and wheel/sdist builds. `f2-demo-command.json` and
the selected64MiB run's `result.json` record actual measured outcomes and platform.
The integration test executes the installed client in a fresh `python -I` process.

Reproduce:

```console
python -I python-client/tools/workflows/stream_reliability.py --work-dir <fresh-owned-directory>
python -m pytest -q python-client/tests/integration/test_export_stream_reliability.py python-client/tests/unit/test_exports.py
```

Java/component/worldview dependencies are **not used by this contract fixture**;
the previously pinned production tuple is unchanged, not silently revalidated or
upgraded. No large real-Java/provider workload or scale/soak acceptance is claimed.
RC-P2.4 remains IMPLEMENTED_UNVERIFIED for that broader deployment gate; CAP-09/16
and RC-R1 gain a separately identified actual-socket safety/memory evidence layer.

## Findings and integration risks

No exporter production defect was demonstrated by these scenarios; existing
atomic-publication/error behavior passes. Reporting was clarified during review
to avoid conflating declared length with delivered/published bytes. The buffered
control intentionally buffers, is local-only and is not a proposed SDK path.
Partial bodies are never promoted, scientific bytes are never transformed, and
there is no retry after an ambiguous operation. No service scope or scientific
Job is created, restored, cancelled or released. Controlled loopback auth is not
genuine Java authorization proof. Existing identity/origin and TLS policies are
untouched; optional measurement dependencies remain development extras.

No overlapping PR/contributor assignment or external communication was introduced.
After final hosted checks, merge normally, verify the remote merge and rerun the
focused subprocess check; retire the branch, record MERGED_BOUNDED in the single
queue ledger and automatically continue the partial-refresh feature.
