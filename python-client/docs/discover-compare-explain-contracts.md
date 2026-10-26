# Discover, compare, explain: reconnaissance

Milestone base: `1e20fc478f645b5fb5c2fd00ffd223a040ca2878`, fetched from
`origin/feature/python-client` on 26 October 2026. The user's clean local Python
branch remains at `188caeb47b86f2eb114cd6f557cf839df0eb10ee`; it was not reset.
Remote: `https://github.com/integratedmodelling/klab-services.git`.
Implementation worktree/branch: `klab-python-discover-compare-explain` /
`feature/python-discover-compare-explain`. Use additive feature checkpoints on
this isolated branch; no unrelated develop integration, publication or Java edits.

## Source contract inventory (not deployed acceptance)

All source locations below refer to the exact milestone base. Existing transport
adds the configured origin's credential, scope header and home Runtime service ID.
No newly advertised URL acquires credentials.

| Slice | Java implementation / DTO | HTTP and scope | Bounds / outcomes | Python boundary |
| --- | --- | --- | --- | --- |
| Authority discovery | ReasonerCapabilitiesImpl; Worldview.AuthorityBinding | GET `/public/capabilities`, selected Reasoner | Local name differs from provider URN; revision 0 is not a reliable invalidation guarantee | Explicit configured binding; no global federation |
| Authority search | AuthoritySearchController:18–34; AuthorityBindings:85–148; AuthoritySearchRequest/Response | POST `/api/v1/authority/search`, authorized scope/component | Query 1..256 Java characters, offset 0..10000, limit 1..100; OK/UNSUPPORTED/UNAVAILABLE/FAILED; HTTP 400/403 distinct | Typed page; explicit canonical-code selection; total is provider-returned count |
| Documentation | AuthorityDocumentationController:38–125 | GET `/api/v1/authority/documentation?authority=...&identity=...`, authorized scope | MIME-to-URL map; 400/403/404/502; content may redirect | Inspect links only, never fetch/execute provider content or forward credentials |
| Semantic resolution | ReasonerController; ReasonerClient; ServicesAPI.REASONER | POST `/api/v1/resolve/concept`, `/observable`, plain text | Concept/Observable DTO; Nothing/error is not success; resolution can materialize knowledge | Reuse existing DTO codecs; retain supplied semantics, never invent units |
| Direct hierarchy | ReasonerController:294–314; ReasonerClient | POST `/api/v1/children`, `/parents`, Concept body | Asserted direct neighbors, no wire pagination | Byte/item bounded; never implicit transitive expansion |
| Semantic checks | ReasonerController:239–244,906–908 | POST `/api/v1/subsumes` with Concept[2], `/satisfiable` with Concept | `/subsumes` calls `is(first,second)`: first specializes second, despite misleading Swagger wording | Bounded explicit boolean checks; no local inference |
| Models/resources | ResourceCRUDController:59–111,156–171; KimModelImpl, ResourceSet | GET `/api/v1/list/{class}`, `/retrieve/{class}/{urn}`, `/resolve/{class}/{urn}`, authorized UserScope | Whole list, no server paging; null/malformed/errors distinct; ResourceSet has originating service descriptors | Explicit selected Resources; listed applicability is not Resolver choice; bounded views, no recursive closure |
| Direct resolution | ResolverController:45–76; ResolutionRequest; DataflowImpl/ActuatorImpl | POST `/api/v1/resolve`, authorized context, home Runtime header; existing Job retrieval | RESOLVED/NO_MODEL/FAILED; may contain references; does not execute | Decode a separately requested plan, never claim it was used for past execution |
| Recorded evidence | RuntimeServerController:543–573; KnowledgeGraphQuery; LinkImpl; ActivityImpl | POST `/api/v1/query`, current context/member authorization | Bounded one-hop links and activity queries; 400/403/422/500/503 distinct | Reuse fetch_provenance membership gate, retain failed activities; no arbitrary numeric unscoped lookup |
| Diagrams | FlowChart; ResolutionFlowChartAdapter; DataflowFlowChartAdapter; scope commit | Activity metadata `im:resolution-graph`, `im:dataflow-graph` | ELK root/children/ports/edges, string identities and parallel/reference relationships | Read-only decoding, explicit missing/partial evidence; no replay or arbitrary metadata execution |
| Experiments | Context.observe; Job.result/cancel; saved Handle; Connection | Existing scope creation, submission, polling and restore interfaces | Ambiguous submission never retried; wait timeout retains Job; local close is not release | Independently owned connection/session/context per experiment, default sequential |

## Selected safety defaults

No semantic cache. Results carry service/scope/worldview/revision observations;
known revision drift rejects stale selection, and absent/zero revisions explicitly
report an unknown freshness guarantee. Harmless extensions stay inspectable.
Capabilities before/after operations detect observed drift, not an atomic server
snapshot/read lease. Unknown correctness-affecting states fail explicitly.

## Live and scientific feasibility gate

**Not yet established at this candidate.** Historical missing units and empty
dataflow failures in `develop-worldview-compatibility-issue.md` remain historical
failures, not repaired by this reconnaissance. No deployed binary SHA has yet
been verified for this milestone. The old separately compiled federation stack
and corrected geospatial archive are candidates only; compatibility with new
authority/FlowChart contracts must be checked independently.

The four-cell matrix must use two actual supported scientific variations and two
honored grid resolutions with an independent oracle. The existing nonuniform
5x4 fixture is not proof of a second resolution or variation. Matrix selection,
real recorded-diagram retrieval, notebook execution and throughput are **not run**.
Missing prerequisites must block completion rather than weaken acceptance.
