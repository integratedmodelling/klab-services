# Explicit annotated-input provenance development review

Parent PR296 verified merged a17f8c3fe; final ffd3fb185/all28checksPASS.
This is local review, not native/scientific/maintainer acceptance.

- Existing upload and ADD-only source acknowledgment remain separate. Input join
  never implements generic resource semantic annotation/update, native deletion,
  source publication, parser/compiler or scientific engine. Source is explicit.
- The facade reads completed import/Resolver jobs and fresh observation membership,
  not cached input metadata; failed/uncertain job retrieval propagates without upload
  replay. Explicit root/client/service/job decoder/result-kind anti-rebinding checks
  bracket reads. Replacing credentials during current query discards the join.
- Root graph must bind the exact selected MODEL directly to the admitted namespace
  and uploaded RESOURCE. Namespace source/hash exact. Manifest binds exact consumed
  binary bytes/hash/schema/URN; manifest sourceSHA is distinct from binary SHA.
- Plan requirements alone do not prove selection: finite join requires an explicit
  single OBSERVE actuator selecting the exact model, no children, and source/version/
  provider-pinned model/resource requirements. Other shapes refuse, not guessed.
- Fresh recorded input matches binary/manifest/annotation hashes, byte count and
  concrete model/resource versions. Source/graph/access receipts remain server claims,
  not atomic history, native annotation, scientific lineage or permission authority.
- SDK implementation imports no simulator; fixture derives manifest/graph/plan/input
  from owned completed upload/source state. Literal crop/source finite admission and
  recorded400cells are retained, never called newly executed native science.
- Public sync/nativeasync workflows cover actual upload/source ADD/resolve/observe/
  join/data read, altered source/binary refusal, server Bob denial even with recreated
  borrowed job IDs, separate-root refusal and cleanup. Persistent ADD assets are
  separately reset by authenticated test control, not native SDK RESOURCE deletion.
- Per-job timeout explicitly(0,60]seconds; bounded graph/query responses and exact
  manifests. Native ResourceImporter retains fail-closed ambiguous submission behavior.

Source gates:55SDK controls,100%unexcluded newmodule/allchangedSDKcode;2public
sync/nativeasync workflows (multiple positive/denial/oracle/cleanup assertions);
18supplementaryfixturecases cover new derived manifests plus complete graph module,
100%unexcluded. Bodymypy/fatal lint/49governance/queue/workfloworderingPASS.
All9installed focusedCIgatesPASS, plus actualordered693SDK/173append/6HTTP/21fixture
thenunchanged100%selection/changedcode gatePASS. Full installed suites/PR pending.

Retained development history:
- Initial exact Context object check rejected legitimate focused observation wrappers;
  corrected to sameclient/root/handle, with actual owner denial at server unchanged.
- Import job returns rawdict, not ResourceSet DTO; corrected join's public boundary.
- Initial base attribute/job narrowing/type errors corrected with explicit union and
  isinstance Job narrowing, no type ignores or disabled body checking.
- New custom-decoder restriction incorrectly rejected canonical Resolver dataflow
  lambda; actualpublic workflow exposed it. Require rawmapping import and canonical
  dataflow result-kind/callable plan decoder; retain binding checks. Unit mock now
  reflects actual public kind, not a fake mapping-only Resolver acceptance path.
- Initial42SDKcontrols97.66%expanded; redundant raw result-object check removed only
  because public Job.result enforces object payload, not by weakening that boundary.
  Foreign matching receipt provider covered and rejected. Final55controls100%.
- Combined focused+ordered shell command exceeded its120second harness budget after
  all9focusedgates passed and ordered693SDK/173append passed. This was a harness
  scheduling timeout, not a product testfailure; partial logs retained. No surviving
  ordered-helper Pythonprocess found. A separate unique attempt2 directory rerun
  completed693/173/6/21andunchanged100%gatePASS without altering producttimeouts.

Native source boundary: inspected ResourcesService interface at parent source;
raw SHA25668b47f16b7ddff2f19276259abf27ce007118d7030a60f3adc878810293631ee.
No established generic safe resource semantic annotation API found; no native adapter
fabricated for provisional source/selection/manifest graph stamps. JAVA-GAP-0013
lineage/compatibility and JAVA-GAP-0002ownedcleanup remain genuine external obligations.
