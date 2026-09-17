# Default observers

An observer is an observation of an agent representing a contributor to a digital twin.
It is distinct from the authenticated identity, the provenance agent, and the executable
k.Actors agent. This contract covers default selection, connection preparation, interactive
observer choice, and maintenance of perceived geometry.

## Project and worldview contract

Project-owned settings are stored in `META-INF/manifest.json`, alongside dependency, version and worldview declarations:

```json
{
  "definedWorldview": "earth",
  "metadata": {
    "klab.user.observer": "people:Contributor",
    "dc:description": "Shared project metadata"
  }
}
```

The observer value is a semantic expression resolving to a concrete, singular agent. It is
optional. Blank text contributes no observer. A non-string value is ignored with a warning;
semantic validation occurs when the observer is prepared with the reasoner.

`ProjectSettings` carries the metadata map. `Project.getSettings()` transports the stored
settings separately from `Project.getMetadata()`, which also contains service-generated values.
`ProjectSettingsIO` reads absent files as empty settings and replaces saved files atomically.
Malformed files fail explicitly and are not silently overwritten. New projects include only the manifest.

The manifest metadata map supports structured JSON values. A legacy `META-INF/project.json` is
read for compatibility, retaining its previous override precedence until the next successful save
migrates the metadata into the manifest and removes that file. All metadata from projects whose
manifest declares a nonblank `definedWorldview` is merged into the worldview. Merely adopting a
worldview does not make a project a contributor. External project metadata is included as well.

The IDE shows default observer settings only for these contributors. The worldview declaration
is visible to other users but only administrators may change it, including via the service API.
Null `ProjectSettings.definedWorldview` leaves the declaration unchanged; blank removes it.
Changes to observer metadata are rejected for noncontributors; existing dormant values are
preserved during unrelated edits and remain excluded from worldview harvesting.

Projects are merged in ascending project-name order. A later project replaces an earlier value.
Different nonblank observer expressions emit a warning identifying the replacing project and
both expressions. Equal expressions (after trimming) do not conflict. This ordering is a
deterministic recovery policy for anomalous configuration, not a semantic precedence hierarchy.
Other metadata keys use the same replacement order without observer-specific warnings.

Worldview warnings travel in the worldview resource set. `WorldviewImpl.update` retains them
and forwards them to the user scope. It replaces the metadata snapshot, so a deleted declaration
does not survive a later refresh. Direct preparation also reports the worldview notifications.
Settings saved through the service invalidate its cached worldview; edits made outside the
service require workspace reload before the new worldview is served.

## Group override contract

`Group.getObserverSemantics()` and the JSON field `observerSemantics` in `GroupImpl` carry
an optional override. Existing group implementations default to no override. The authentication
provider must actually populate this field; this change does not modify an external hub's group
administration interface.

`DefaultObserver.select` applies these rules:

1. Start with `Worldview.USER_OBSERVER_SEMANTICS` (`klab.user.observer`).
2. Ignore groups without nonblank observer semantics.
3. Sort groups by ID, then name, then observer expression; the last declaring group wins.
4. Warn when different groups declare different expressions. A group overriding the worldview
   is expected and does not itself produce a conflict warning.

Group membership and group objects are never changed. Substitution applies to the selected
observer semantics. Group iteration order cannot change the result. No extra federation policy
is inferred from the older `klab.federation.observer` key.

## Querying or creating the agent

`DefaultObserverPreparation.prepare(worldview, contextScope, geometry)` is an explicit,
asynchronous preparation boundary. The supplied context must represent the requesting user
and have neither a focused observation nor an observer. The caller must already have authorized
access to the twin.

The operation:

1. Selects semantics and sends conflict warnings through the requesting scope.
2. Returns a completed future containing `null` if no default is configured.
3. Validates the expression through the reasoner as a concrete, singular agent.
4. Derives the logical identity `klab.user:<name-based UUID>` from the user's server URL and
   username. The length-prefixed authority and username determine the UUID; session IDs, twin
   IDs and semantics do not. Anonymous or unnamed identities are rejected.
5. Looks up the identity using the twin's existing individual-observation catalog URN,
   `<context-id>:individuals:<logical-identity>`.
6. Returns an existing matching observation. A conflicting stored semantic identity fails with
   a warning rather than silently rewriting graph data or creating another user identity.
7. If absent, requires a nonempty geometry and calls the normal observation builder's
   `identity(...).geometry(...).submit()`. The returned future is the normal resolution future;
   registering an unresolved placeholder is not considered success.

The server URL is currently the available authority discriminator. Deployments must keep that
value and username stable; changing either requires an explicit identity migration policy.
The preparation helper does not invent a physical extent for a user agent. The caller supplies
the geometry appropriate to the worldview and connection policy.

## Project settings editor and save protocol

The IDE's `WorkspaceEditor` project context menu opens one settings tab per project, with the
default observer, a permission editor, and editable project-owned metadata. Metadata supports
typed inline edits and adding, replacing or removing keys; JSON values preserve structured data.
The observer key has its own text field and is excluded from the generic metadata rows.
Saving is asynchronous and a rejected save retains the draft. Edit access and a lock owned by
the requester are required. Manifest/dependency editing remains separate.

Saving uses the existing generic resources API:

```java
var request = new ProjectImpl();
request.setUrn(workspaceUrn + "/" + projectUrn);
request.setSettings(settings);
// A null manifest explicitly selects settings-only replacement.
service.submit(request, ResourcesService.SubmissionMode.REPLACE, userScope);
```

The service requires an existing local project in that workspace and a lock owned by the
requesting identity, and rechecks service UPDATE plus project access (or service administration).
`ProjectSettings.permissions` transports an optional encoded access-rights update: null leaves
rights unchanged; an empty string means owner-only. Rights are stored only in `ResourcesKBox`,
never in the manifest. Existing service grants and project ownership are preserved.
The ordinary project `setRights` endpoint uses the same guarded path. Metadata is stored in
the manifest; a catalog-write failure restores the original manifest and legacy settings file and attempts to restore rights.
This is compensating error recovery across two stores, not a crash-atomic distributed transaction.
This settings-only replacement does not implement general project
manifest editing beyond `definedWorldview`. Errors are returned as resource-set notifications.
The IDE loads stored metadata and catalog rights when opening the tab and saves that editable
snapshot, excluding generated project metadata. Lock changes refresh editability; save-time server
checks remain authoritative. Project `ResourceInfo.permissions` reports the caller's UPDATE access.

## Connection and interactive selection

Runtime creation and connection call preparation with the requesting identity, its groups and
the first available worldview advertised by its resource services. Unavailable resource services
warn and do not prevent group-only defaults. Preparing across multiple distinct worldview
providers is not a new federation/merge policy; services must still supply a coherent worldview.
The named default agent is initially submitted with universal occupied geometry, allowing its
normal model resolution without inventing a physical location. This does **not** initialize its
perceived extent to a worldwide footprint.

The registered runtime twin scope remains neutral. The returned connection scope and its copied
configuration contain the user's observer, so connecting another contributor cannot replace the
first user's selection. The configuration transports the observer to the client, including on
reconstruction after restart. Preparation failures produce warnings and a connection without a
default observer. An already active client connection retains its interactive selection.

`withObserver` carries the selected persistent agent ID as `ResolutionConstraint.Type.Observer`,
as well as the existing `#observerId` scope-token suffix. The backend resolves that ID through the
knowledge graph and rejects missing or non-agent observers. Context selection and observer
selection are independent: replacing or clearing one retains the other. The IDE replaces its
current context without nesting it under previous interactive context selections.

The IDE observer tab loads the twin's cohort members independently of the observation tree's
current focus and depth. Every agent member is eligible, including agents instantiated by models.
Click an outline observer icon to make that agent current; the current observer uses a filled, accented icon.
Clicking the current icon leaves the choice intact. If there is no current observer and the catalog has
exactly one agent, it is selected automatically. Right-click opens the available observer actions without
changing the choice. On refresh or entering the tab, the selected agent's cohort is expanded
and the agent is scrolled into view. A selection remains visible while catalog links are loading.
Asynchronous catalog and observer refreshes cannot overwrite a newer selection or another twin.

Connection-created agents carry `klab.observer.automatic`. Root, explicit agent submissions carry
`klab.observer.explicit`; model-generated agents do not acquire that flag from their parent.
Only explicitly submitted agents appear in the observation tree. Explicitly submitting an
existing agent promotes its visibility without creating a duplicate. All agents remain in the
observer catalog. Older agents without explicit-submission metadata remain observer-only until
explicitly submitted again.

## Perceived geometry and notifications

`getGeometry()` continues to mean occupied geometry, persisted through `HAS_GEOMETRY`.
`geometry(PERCEIVES)` returns a separate nullable snapshot transported as `perceivedGeometry`.
Builders can supply it for agents; non-agent perceived geometries are rejected. `OVERSEES` and
`AFFECTS` are still future geometry relationships.

A successful root observation transaction unions its target geometry into the active observer's
perceived geometry in the same database transaction. Failures roll back both. Reused observations
and successful graph queries, which do not create observation transactions, update perception in
a separate short transaction. Failed/empty results do not grow perception. Empty or universal
geometry adds no spatial or temporal extent.

`KnowledgeGraph.Transaction.perceive` locks the observer before reading the persisted baseline,
unions spatial and temporal extents, and replaces only its `PERCEIVES_GEOMETRY` relationship.
Occupied geometry and shared geometry nodes are not edited or deleted. Reading the stored
baseline under the lock prevents stale connection snapshots from losing concurrent additions,
including writers holding different context locks. Observation caches are invalidated only after
commit. Temporal extents are retained, unlike continuant cohort geometry. Spatial union preserves
disjoint areas rather than replacing them with a bounding rectangle.

Root commits advertise the observer among modified assets. Client graph ingestion invalidates
its cached snapshot before IDE notifications. Query/reuse paths emit `ObserverGeometryChanged`
with the refreshed observation; `ObserverResolved` announces prepared or promoted agents.
The IDE refreshes the selected observer only when the notified identity still matches the current
selection, and updates views without stealing the active panel tab.

Resolution geometry precedence is an explicit geometry constraint, then the context observation's
geometry, then the observer's perceived geometry. Root/substantial submissions clear interactive
context focus before applying this fallback. An explicitly supplied observation geometry wins.
There is no fallback from missing perception to occupation. Consequently a new default observer
needs an initial observation with explicit geometry before it can supply a useful implicit extent.
The IDE drop preview uses the same distinction: dependents show context geometry and independent
observations show perceived geometry.

## Perception policy

`PerceivedGeometryPolicy.DEFAULT` is `UNION`: exact spatial union with temporal extent union.
Union remains the fallback as policy articulation grows. The future policy source is a worldview
`define` instruction selected according to agent semantics, not an IDE preference or a group
override of geometry behavior. No new `define` syntax or semantic dispatch is implemented yet.

Planned policies include bounding-box union, convex hulls, and shape simplification. Collective
and relationship policies will also need the visible members and all relationship endpoints;
today maintenance uses the completed target geometry and does not infer endpoint visibility.
An explicit user edit does not change the automatic policy: later observations can expand it again.

## Auditing and editing perception

The DigitalTwinEditor knowledge-tree menu opens **Audit / edit observer geometry** for an agent.
The same action is available on observer rows in its owned DigitalTwinControlPanel. There is one
auxiliary Observer tab per agent, independent of the currently selected observer. Closing the tab
disposes its maps; reopening an existing tab preserves its draft.

Separate labeled maps show saved perceived space and read-only occupied space as actual WGS84
GeoJSON shapes. Missing space is explicitly reported, never substituted with the other geometry.
Geometry details expose both snapshots, including their temporal dimensions, for read-only auditing.
Pan/zoom the perceived map, choose **Use map view**, inspect the amber draft rectangle, then choose
**Save perceived space**. **Discard draft and reload** removes the draft. Invalid, nonfinite,
empty, or antimeridian-crossing/out-of-world rectangles are rejected rather than silently wrapped.

The service contract is:

- `GET /api/v1/observer/{id}/geometry` (`getObserverGeometry`) returns an `ObserverGeometryView`:
  one transported agent snapshot and separate nullable occupied/perceived GeoJSON strings.
- `POST /api/v1/observer/geometry` (`updateObserverGeometry`) accepts `ObserverGeometryUpdate`:
  `observerId`, nullable `expectedGeometry`, and WGS84 `west`, `south`, `east`, `north` coordinates.
  `expectedGeometry` is the last-read perceived geometry encoding; null asserts no perception.
  The response is the refreshed observation snapshot.

Both routes require authenticated context access. Audit requires an agent in a cohort of that twin;
editing verifies that membership again in the write transaction. `replacePerceivedSpace` obtains
the observer lock, compares canonical persisted and expected encodings, and replaces only the
spatial dimension. Perceived time and any other dimensions are preserved. A stale baseline returns
HTTP 409, invalid requests return HTTP 400, and a rejected transaction leaves the graph unchanged.
This prevents a manual replacement from silently erasing concurrent automatic additions. Occupied
geometry and shared geometry nodes remain untouched. Cache revision and `ObserverGeometryChanged`
notification occur after commit; the client ingests the returned snapshot as well as pushed events.

Open tabs refresh after graph notifications when clean. Unsaved drafts are retained and saves
still check their original baseline. Late loads/saves cannot update closed tabs. The first editing
surface uses the existing Leaflet map; `java_leaflet` needs no changes for viewport selection.

## Remaining work

Freehand/polygon editing, trimming, clearing, antimeridian-spanning edits, temporal editing, and
worldview-defined perception policies remain future extensions.
Automatic `user.kactor` association remains unfinished: `USER` behaviors live in the root user
scope, while observation binding currently recognizes `BEHAVIOR` and `TASK`.

Observer-sensitive semantic resolution (`observed as O`) and semantic migration of stored user
agents remain subsequent stages. Current selection provides request context, provenance and
geometry; it does not implement a new observer-specific model-ranking policy.

## Verification

`DefaultObserverTest` covers project conflict warnings, group precedence independent of input
order, blank and invalid declarations, user identity stability/separation, and group JSON transport.
`ProjectSettingsIOTest` covers missing files, metadata round trips, replacement, and malformed-file
failure. `DefaultObserverPreparationTest` checks no-default behavior, reuse without submission,
submission through the returned future, and rejection of conflicting or abstract agent semantics.
`ObserverScopeTest` checks separate geometry transport, default configuration transfer, independent
selection, request tokens, geometry precedence, and isolation of server connection configurations.
`PerceivedGeometryPersistenceTest` executes production transaction code against embedded Neo4j
for temporal/spatial union, rollback, concurrent writers, occupied/shared geometry preservation,
and explicit-agent promotion without losing existing metadata.
It also verifies spatial replacement preserves time and rejects stale manual/automatic baselines
and agents from another twin. `ObserverGeometryViewTest` checks geographic reprojection and missing
space; IDE `ObserverCardTest` checks perceived baselines and invalid viewport rejection.
IDE `TreeModelTest` covers explicit-only agent visibility and `ObservationDropTargetTest` checks
the context/perception preview distinction. These tests do not constitute a live connected IDE
demonstration or a remote Neo4j routing/spatial-plugin deployment test.

### Universal initial geometry and preparation failures

An empty twin prepares its default observer with `Geometry.UNIVERSAL` (`*`). Universal geometry
is nonempty, and its encoding must survive conversion to a runtime scale and graph persistence.
The first concrete observation replaces an absent/universal perceived extent; later concrete
observations use the default union policy. A universal observation does not erase a concrete
perceived extent. Occupied and perceived geometries remain separate.

Preparation failures, including resolution returning an empty observation, are retained in the
connection configuration. The client forwards new-twin configuration notifications through its
notification channel, as it already does when connecting to an existing twin.

Dimensionless geometry encoding is `1` for SCALAR, `*` for UNIVERSAL, and `X` for EMPTY. Scalar and universal are both nonempty. Geometry and runtime Scale parsing/encoding preserve these distinctions.
