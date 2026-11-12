import type { Concept, SemanticResponse, WorldviewCatalog } from "../src/knowledge/model";
export const concepts: Concept[] = [
  { urn: "earth:Subject", name: "Subject", type: ["SUBJECT", "ABSTRACT"] },
  { urn: "earth:Landscape", name: "Landscape", type: ["SUBJECT"], metadata: { "dc:comment": "A spatially continuous environment with interacting natural and human processes." } },
  { urn: "earth:Forest", name: "Forest", type: ["SUBJECT"], metadata: { "dc:comment": "A landscape characterized by tree cover." } },
  { urn: "earth:Wetland", name: "Wetland", type: ["SUBJECT"] },
  { urn: "earth:Elevation", name: "Elevation", type: ["QUALITY"] },
  { urn: "earth:Habitat", name: "Habitat", type: ["ROLE"] },
  { urn: "earth:Natural", name: "Natural", type: ["PREDICATE"] },
];
const declaration = (c: Concept) => ({ urn: c.urn, label: c.name!, type: c.type!, children: [] });
export const catalog: WorldviewCatalog = { urn: "earth", ontologies: [{ urn: "earth", children: [
  { ...declaration(concepts[0]), children: [{ ...declaration(concepts[1]), children: concepts.slice(2, 4).map(declaration) }] },
  ...concepts.slice(4).map(declaration),
] }], authorities: [{ localId: "SPECIES", provider: { searchable: true } }] };

/** Isolated preview data, never used by production components. Also exercises the actual transport shape. */
export function fixtureResponse(path: string, body: any, declarationText = ""): any {
  if (path.endsWith("worldview/explorer")) return catalog;
  if (path.endsWith("resolve/concept")) return concepts.find(c => c.urn === body) || { urn: body, type: ["SUBJECT"] };
  if (path.includes("authority/search")) return { status: "OK", matches: [{ id: "123", label: "Oak", description: "Oak identity." }] };
  if (path.endsWith("semanticSearch")) {
    const declaration = body.searchMode === "UNDO" ? "" : body.searchMode === "SELECT" ? body.selectedMatchId : declarationText;
    const found = concepts.find(c => c.urn === declaration);
    const response: SemanticResponse = { searchId: body.searchId || 1, requestId: body.requestId, declaration,
      code: declaration ? [{ value: declaration, color: found?.type?.[0] || "SUBJECT", needsWhitespaceAfter: true }] : [],
      matches: concepts.filter(c => `${c.name} ${c.urn}`.toLowerCase().includes(body.queryString?.toLowerCase() || ""))
        .map(c => ({ id: c.urn, name: c.name!, description: String(c.metadata?.["dc:comment"] || ""), matchType: "CONCEPT", conceptType: c.type })),
      errors: [], currentConcept: found, canUndo: !!declaration, canOpenScope: true, canCloseScope: false, acceptsValue: false };
    return response;
  }
  const urn = body?.urn, relation = path.split("/").pop()?.split("?")[0];
  const identifiers: Record<string, Record<string, string[]>> = {
    parents: { "earth:Landscape": ["earth:Subject"], "earth:Forest": ["earth:Landscape"], "earth:Wetland": ["earth:Landscape"] },
    children: { "earth:Subject": ["earth:Landscape"], "earth:Landscape": ["earth:Forest", "earth:Wetland"] },
    attributes: { "earth:Landscape": ["earth:Natural"] }, roles: { "earth:Landscape": ["earth:Habitat"] },
    inherent: { "earth:Elevation": ["earth:Landscape"] },
  };
  return (identifiers[relation || ""]?.[urn] || []).map(id => concepts.find(c => c.urn === id));
}
