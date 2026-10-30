# Contribution review: PR101 after upstream integration

## Recommendation

**Ready for maintainer review of bounded F1.** One demonstrated credential-view
defect was fixed. 1,172 unit/integration tests and both actual shipped Java
functional tests pass, along with existing coverage/lint gates. This is an
implementing-agent assessment, not independent approval. Verify current-head CI
after publication; no PR merge or approving self-review is implied.

## Reviewed state

- Repository/worktree: `integratedmodelling/klab-services`, `pr101-semantic-explorer`.
- Actual target/base: `feature/python-discover-compare-explain-integration`,
  `324a67af761a421a26da8dc7326dd594059ce699`.
- Initial clean candidate: `e4dc73aa433e62eabc833fe87cc3e32d99360351`.
- Fixed executable candidate: `ba2b73145a16dc0fbd738ac50b1ffb9fd7eecfa8`.
- Reviewed the F1 diff, inherited upstream baseline, public exports, bounded DTOs,
  documentation descriptors, semantic state checks and shipped functional suite
  using `KLAB_CLI_Contribution_Review.md`. Read-only reproduction preceded the fix.
- User requires simple code and actual unit/functional/existing-Java evidence.
  This pass creates no sessions, contexts or numerical Jobs and edits no Java,
  dependency, component, ontology, worldview or scientific source.

## Capability and boundaries

Search an explicitly configured local authority, explicitly select a canonical
code, inspect documentation descriptors, resolve its identity, navigate direct
hierarchy and ask directional specialization/satisfiability predicates. Java stays
authoritative; no Python inference, automatic first-match choice, permanent cache,
atomic snapshot, exhaustive traversal or provider-content fetching.

## Findings, ordered by consequence

1. **Demonstrated credential-view defect, fixed.** Knowledge service/worldview IDs,
   resolved URNs and authority candidate codes were retained verbatim although
   raw metadata was redacted. A fake configured credential echoed by a service
   leaked through typed values/repr. Five new regression cases failed against the
   old source. The small `_identity` helper rejects such identities with a generic
   ProtocolError before exposing/reusing them. Unlike display-only metadata,
   canonical identities must not silently become different redacted request IDs.
   Labels, descriptions and raw metadata remain redacted. Normal identities and
   original knowledge-stamp equality are unchanged.
2. **Previously missing live evidence is now supplied.** The running October30
   Reasoner advertises configured authorities. CHEM's Aspirin identity was checked
   against PubChem's independent CID2244/Title response; the canonical locator
   follows the Java encoding contract. Direct hierarchy expectations come from
   unmodified pinned worldview declarations, not Python results under test.
   Both shipped tests actually executed and passed; collection/mocks are not
   presented as live acceptance.
3. **Current-head publication/CI and maintainer decision remain procedural gates.**
   Java, Python3.11–3.13, core-only and Jenkins all passed at initial e4dc73aa4.
   Check the newly published fix/report head separately. Do not bypass failures,
   impersonate a reviewer, silently retarget the PR or merge into another branch.

No further demonstrated blocking/important F1 defect was found in this bounded
review. This is not exhaustive correctness, independent approval or full milestone
scientific acceptance.

## Validation actually performed

Windows Python3.12.7 in `pr99-inspector-312`; explicit PYTHONPATH selects this PR101
worktree, not the interpreter's PR99 editable source. Coverage/JUnit/private state
and the native bootstrap helper are outside Git.

| Check / command | Revision/content | Status / limits |
| --- | --- | --- |
| `python -m pytest tests/unit/test_semantic_explorer.py -k credential_bearing -q --tb=short` | Initial source plus new tests | Failed exit1: all five reproduce missing rejection |
| `python -m pytest tests/unit tests/integration -q --tb=short --cov=klab_client --cov-branch --cov-report=json:<external>/pr101-current-review-coverage.json` | Exact ba2b73145 executable content | Passed exit0:1,172 tests; one existing Rasterio warning |
| Exact branch `check_feature_coverage.py` command/all new-module arguments using that external JSON | Same content | Passed exit0: existing100% new-module line/branch and changed-code gates |
| `python -m ruff check src tests examples tools --select E9,F63,F7,F82`; `git diff --check` | Same content | Passed exit0 each |
| `python <external>/pr101-semantic-live-20261030.py` | Same content, actual October30 Java | Passed exit0:2 actual functional tests in1.31s |
| Functional invocation: `pytest tests/functional/test_semantic_explorer.py -q --tb=short -o addopts= -m 'live and functional' --junitxml=<external>/functional.xml -p no:cacheprovider` | Same content | Passed, neither skipped nor mocked Java responses |
| Independent PubChem CID2244 Title endpoint | Public PubChem, not the SDK | HTTP200:CID2244,TitleAspirin |
| Post-test knowledge/owned-work checks | Running native stack | Consistent revision1; zero contexts/projects; no numerical submission/session/context creation |

An initial lint command used the repository root instead of `python-client/` and
failed to locate src/tests/examples. The correct-directory command above passed;
the mistaken invocation is not counted as a source defect or successful lint run.

An exploratory LAND check found legitimately empty documentation descriptors and
confirmed that wire URNs use canonical authority locators rather than internal OWL
names. Its initial internal-name expectation was wrong, not a Python/Java defect.
The final fixture uses CHEM with independently advertised documentation and the
source-defined encoding contract; no Java was altered to satisfy an expectation.

## Scientific, identity and lifecycle evidence

[Sanitized live result](../../results/semantic-explorer-oct30-stack-20261030.json).
Selected exactly `CID:2244` from a successful CHEM search; checked advertised
text/html documentation and resolved `CHEM:[CID:2244]`. Did not choose by label or
fetch documentation content. PubChem's separate public response confirms Aspirin.

Pinned IMOD commit1328000fff63a1951465b1f7b130c0af15264c95,
`earth.kwv:9–17`, explicitly declares Geolocated under Georeferenced. Both direct
neighbor directions, child-is-parent=true, reverse=false and satisfiability for
both were checked against real Java. The source file SHA256 is recorded.

The functional harness substitutes only the client bootstrap for the normal native
loopback owner connection. The shipped test bodies, Python SDK, Java HTTP payloads
and predicate results are real. Client hooks enforce no numerical submissions,
owned session/context creation or external content fetches. Semantic resolution
can materialize knowledge/caches; it is not universally side-effect-free. Hosted
Hub login, exact binary/source attestation, private/two-user access and numerical
scientific accuracy are not claimed by this F1 check.

## Contributor coordination

Small Python-only validation fix; no new abstraction beyond one focused identity
helper or any semantic algorithm change. Existing Java controls semantic meaning.
PR99 stays ready and unmerged per the user's decision while this upstream F1 PR
finishes. No other PR or service setup was changed by this pass.

## Smallest next actions

Publish this tested fix/report as one batch, verify exact-head CI, and obtain the
maintainer's merge decision for the configured milestone integration target.
Handle PR99's subsequent integration deliberately; no silent retarget or merge.

## Suggested PR description

Bounded revision-aware semantic authority exploration over existing Java. Reject
credential-bearing canonical identities without rewriting scientific/request IDs.
1,172 offline tests, coverage/lint and actual authority/hierarchy functional checks
on October30 Java pass. Native bootstrap and explicit scientific limits documented.
