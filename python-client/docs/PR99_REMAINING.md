# PR #99: completed feature and remaining integration gates

**PR #98 does not block PR #99.** The stack runs F1 #101 → F2 #99 → F3 #98.
F3's second-identity/nonowner-denial fixture belongs to #98, not #99 acceptance.

## Completed for this bounded feature

- Typed resource/model inspection and bounded pilot implemented.
- Pilot fixture corrected to use existing `earth:Terrestrial earth:Region`, as
  required by the unchanged elevation contextual-admission contract. No public
  SDK focus, Java, scientific model, component, ontology or worldview source change.
- Actual local synthetic terrain computation returned 20/20 finite cells in m;
  accepted recorded resolution identified the inspected model
  `staging.vxii.basic.elevation-quantifier`.
- Both the native pilot and shipped workflow passed; shipped workflow used native
  local-owner bootstrap instead of the hosted Hub connection bootstrap.
- Owned scopes released; original deployment/configuration/plugins restored.
- Windows: 1,246 offline tests, 45 focused tests, lint and existing coverage gates
  passed. At `19a38b570336cba11666838c9f11582c2629701b`, Python 3.11/3.12/3.13
  and core-only GitHub checks all passed.

Feature correction: `36b84293c`. Live evidence/reassessment: `19a38b570`.
See [actual values, identities and limits](results/resource-inspector-terrestrial-acceptance-20261029.json)
and [contribution assessment](reviews/discover-compare-explain/resource-inspector-followup.md).

## What remains for PR #99

1. **Java/dependency maintainer: resolve the existing Java build gate separately.**
   At the tested head, [Build and test run 40961757674](https://github.com/integratedmodelling/klab-services/actions/runs/40961757674)
   fails at `klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/lang/LanguageAdapter.java:1234`:
   `the switch expression does not cover all possible input values`.
   Establish the exact language dependency/enum contract and provide an approved
   compatible dependency or upstream Java correction. Do not guess the missing
   case or add Java changes to this Python PR under the current authorization.
   Evidence required: the approved upstream revision/dependency identities and a
   successful required Java build after deliberate integration.
2. **Python maintainer: review the feature and latest fixture correction.**
   Review the partial-evidence/inspection boundaries and existing checks against
   the contribution guide. Verify the latest published head's Python checks; the
   results above apply to the named tested revision, not automatically to later
   commits. This handoff adds documentation only; its CI may still be running.
3. **Maintainer: confirm the stacked base/integration plan.**
   #99 is based on `feature/python-semantic-explorer` (#101), not directly on the
   final integration target. Arrange upstream/base integration in the intended
   order, preserving published history and feature-separated PRs. Recheck required
   checks after any authorized integration. Only then decide when to leave draft
   and accept/merge; no merge, rebase or force-push is authorized by this handoff.

**No further numerical retry is needed for #99's bounded acceptance.** Keep its
draft status while the Java build and maintainer/integration decisions remain.

## Not PR #99 completion requirements

- PR #98's valid second-user/private-context denial test or diagram-viewer review.
- Full milestone scientific matrix, independent accuracy oracle, throughput,
  notebook delivery or final combined integration acceptance.

These remain separate work, not prerequisites that flow backward from #98 to #99.

## Evidence limits and operating state

The existing generator is synthetic terrain, not measured elevation or an accuracy
benchmark. Model association is verified; provenance remains partial, with no
exhaustive lineage, durable-root-commit or exact binary/source-attestation claim.
Hosted Hub bootstrap was not rerun. Those limits must stay explicit in review.

The deployment was restored again after the subsequent #98 positive-control run;
do not assume the ready test setup is still installed. See #98's
`execution-evidence-local-acceptance-20261029.json` on its branch for that later
restoration checkpoint. Sanitized logs, backups and caches are retained externally.
Four of the latest five authorized attempts have been used across both PRs; one
remains. This documentation commit neither consumes nor renews that allowance.
