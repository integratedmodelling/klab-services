# Project review format, version 1

Store review contributions under the project root's `review/` directory. The project and target identifiers are separate from the material URN `project/path`. Resources treats snapshots and candidates here as project material. Apply approved changes through the ordinary document editing workflow.

| Location | Contents |
| --- | --- |
| `bootstrap/<target-key>/` | Question registers, research dossiers, draft narratives and their methods. Use the project target for shared methods and cross-domain dossiers. |
| `evidence/<target-key>/<evidence-id>/` | Sources, historical documents, and retained validation evidence. Record each item's origin, attribution, relation to the review and SHA-256 for copied bytes. |
| `proposals/<target-key>/<bundle>/` | `manifest.json`, `proposal.yaml`, its exact base snapshot, and the optional candidate. |
| `comments/<target-key>/<bundle>/` | `manifest.json`, `comments.json`, and a base snapshot when the comments refer directly to a document. |
| `decisions.jsonl` | Append-only editor events. Record the decision ID, UTC timestamp, editor, target, disposition, rationale and exact input references. Use a subsequent event for application and record the resulting commit. Historical entries may instead point to the original dated decision record and provenance. |
| `history/` | Service-owned workflow archives. Existing `review-history/` receipts keep their paths. |

Every manifest carries a `target` with `project`, `kind`, and the exact `urn`. For example, `ontology--imod` and `project--imod` identify different targets. For a URN matching `[a-z0-9][a-z0-9._-]{0,63}` without a trailing dot, use `<kind>--<urn>`. Otherwise use `<kind>--sha256--<lowercase SHA-256 of the UTF-8 URN>`. Use the manifest to recover the exact identity. Assets within a document can use their own target and the containing document's snapshot.

A bundle directory uses `yyyyMMddTHHmmssZ--<contributor-key>--<id>`. The API generates the contributor key from the authenticated username. Historical imports may use Git attribution and explicitly record its basis. `manifest.json` records the bundle version, kind, target, ID, creation time, contributor, status, payload schema, base, inputs, and file descriptors. Each file descriptor gives the SHA-256 and byte size. Use the bundled [manifest schema](../klab.services.resources/src/main/resources/schemas/llm/project-review-manifest.schema.json).

For an existing document use `base.state: snapshot` and supply the exact bytes reviewed, original path, and revision when known. For a new document use `base.state: absent`. Record a missing historical snapshot as `unavailable` and exclude that bundle from automated application. Preserve candidate bytes separately. In Git, use `review/** -text -filter -ident` or equivalent attributes to preserve reviewed bytes across checkout.

A revision creates a new bundle. An ontology revision retains its proposal ID, gets a new revision ID, and names the preceding revision in `supersedes_revision`. The manifest records that predecessor and additional synthesis inputs by path and hash. Use stable asset and comment IDs when merging. Preserve conflicting feedback, author attribution and earlier decisions. Comments use the existing per-concept and corpus record fields in JSON, with the transport's camel-case names such as `commentId` and `proposalRevisionId`. Use a base snapshot or set `base.state` to `reference` and include the exact proposal manifest among the inputs.

## Resources API

`POST /api/v1/project-review/bundles` accepts a JSON request with `kind` (`proposal` or `comment`), `target`, `id`, `created_at` (UTC ISO 8601), `base`, `payload`, optional `candidate`, and optional `inputs`. The `base` has `state`, and for snapshots also `path`, `content`, and optional `revision`. JSON byte fields are Base64. Each input has `manifest` (the project-relative path) and `sha256`. The response gives the manifest `path` and `sha256`.

The endpoint accepts ontology proposal YAML validated against context pack 1.3 and project comments as a nonempty JSON array. It requires metadata write access to a writable Git project, respects project locks, publishes the complete bundle, and stages its exact bytes. An identical retry returns the same receipt when the bundle and index agree. Use the ordinary repository operations to commit and publish. Read files through `ADDITIONAL_MATERIAL`. Use that API for ordinary bootstrap and evidence writes. Proposal and comment bundles, the editor ledger and archive paths are protected from generic material mutations. Editor decisions can be appended through project Git editing. The archive retains the workflow service's existing readership checks and receipt semantics.

New proposal payload schemas for namespaces, behaviors and project definitions can reuse this layout.
