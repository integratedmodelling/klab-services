import type { ServiceApi } from "../services/api";

export interface Concept {
  urn: string;
  name?: string;
  type?: string[];
  metadata?: Record<string, unknown>;
  abstract?: boolean;
  collective?: boolean;
  notifications?: { message: string; level?: string }[];
}
export interface Declaration {
  urn: string;
  label: string;
  type: string[];
  children: Declaration[];
}
export interface WorldviewCatalog {
  urn: string;
  declaredLinks?: { relation: string; source: string; target: string; label: string }[];
  ontologies: { urn: string; children: Declaration[] }[];
  authorities: { localId: string; provider?: { searchable?: boolean } }[];
}
export interface SemanticMatch {
  id: string; name: string; description?: string; matchType: string; conceptType?: string[];
}
export interface SemanticResponse {
  searchId: number; requestId: number; declaration: string;
  code: { value: string; color?: string; font?: string; description?: string;
    needsWhitespaceBefore?: boolean; needsWhitespaceAfter?: boolean }[];
  matches: SemanticMatch[]; errors: string[]; description?: string;
  currentConcept?: Concept; observable?: { semantics?: Concept; urn?: string };
  canUndo: boolean; canOpenScope: boolean; canCloseScope: boolean; acceptsValue: boolean;
}
export type SemanticMode = "TOKEN" | "SELECT" | "VALUE" | "IDENTITY" | "UNDO" | "OPEN_SCOPE" | "CLOSE_SCOPE";
export function post<T>(api: ServiceApi, path: string, body: unknown, signal?: AbortSignal): Promise<T> {
  return api.request<T>(`api/v1/${path}`, { method: "POST", signal,
    headers: { "Content-Type": typeof body === "string" ? "text/plain" : "application/json" },
    body: typeof body === "string" ? body : JSON.stringify(body) });
}
export function conceptMetadata(concept: Concept): Record<string, unknown> {
  const metadata = concept.metadata || {};
  const delegate = metadata.delegate;
  return { ...(delegate && typeof delegate === "object" ? delegate : {}), ...metadata };
}
export function conceptLabel(concept: Concept): string {
  const metadata = conceptMetadata(concept);
  for (const key of ["klab:displayLabel", "dc:label", "rdfs:label", "im:suggested-name"]) {
    const value = metadata[key];
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return concept.name || concept.urn.split(":").pop() || concept.urn;
}
export function conceptDocumentation(concept: Concept): string {
  const metadata = conceptMetadata(concept);
  for (const key of ["dc:comment", "rdfs:comment", "dc:description"]) {
    const value = metadata[key];
    if (typeof value === "string" && value.trim()) return value.trim();
  }
  return "";
}
export function semanticColor(types: string[] = []): string {
  return ["DOMAIN", "QUALITY", "SUBJECT", "AGENT", "RELATIONSHIP", "EXTENT", "EVENT", "PROCESS", "PREDICATE", "ROLE", "CONFIGURATION"]
    .find(type => types.includes(type))?.replace("AGENT", "SUBJECT").replace("PREDICATE", "TRAIT") || "UNKNOWN";
}

export const graphRelations = [
  { id: "SUBCLASS", label: "Subclass", selected: true },
  { id: "AFFECTS", label: "Affects", selected: true },
  { id: "CREATES", label: "Creates", selected: true },
  { id: "MARKS", label: "Marks", selected: true },
  { id: "INCREASES_WITH", label: "Increases with", selected: true },
  { id: "DECREASES_WITH", label: "Decreases with", selected: true },
  { id: "DISCRETIZES", label: "Discretizes", selected: true },
  { id: "CLASSIFIES", label: "Classifies", selected: true },
  { id: "DESCRIBES", label: "Described type", selected: true },
  { id: "IDENTITIES", label: "Identity traits", selected: true },
  { id: "ATTRIBUTES", label: "Attributes", selected: true },
  { id: "REALMS", label: "Realm qualifications", selected: true },
  { id: "ROLES", label: "Roles", selected: true },
  { id: "INHERENT", label: "Of: native bearer", selected: true,
    description: "Quality or process → native bearer. Independent of observational containment." },
  { id: "CONTAINS", label: "Contains / within: observational containment", selected: true,
    description: "Declared container → contained type. Does not imply members exist, physical enclosure or inherency." },
  { id: "QUANTITATIVE_WITHIN", label: "Within: quantitative identities", selected: true,
    description: "Measured quantity → ordered portion and whole identities, including authority identities. Does not assert physical parthood or a ratio." },
  { id: "EMERGENCE", label: "Emerges from: configuration ingredients", selected: true,
    description: "Declared configuration → ingredient expression. Ingredients are not native bearers or application targets." },
  { id: "REQUIRED_IDENTITIES", label: "Requires identity (declared)", selected: true,
    description: "Declared identity requirement. On qualities and processes this constrains the bearer, not their own traits." },
  { id: "REQUIRED_REALMS", label: "Requires realm (declared)", selected: false },
  { id: "ADJACENT", label: "Adjacent to", selected: false },
  { id: "CAUSED", label: "Causing", selected: false },
  { id: "CAUSANT", label: "Caused by", selected: false },
  { id: "COMPRESENT", label: "With", selected: false },
  { id: "GOAL", label: "For", selected: false },
  { id: "COOCCURRENT", label: "During", selected: false },
  { id: "RELATIVE", label: "Relative to", selected: false },
  { id: "SOURCE", label: "Links: source", selected: true },
  { id: "TARGET", label: "Links: target", selected: true },
  { id: "APPLIES", label: "Applies to: predicate / configuration target", selected: true,
    description: "Application scope, including predicate 'of'. Distinct from native dependence and emergence ingredients." },
  { id: "IMPLIES", label: "Implies roles", selected: true },
  { id: "OPERANDS", label: "Logical operands", selected: false },
] as const;
export const defaultGraphRelations: string[] = graphRelations.filter(r => r.selected).map(r => r.id);
export interface Neighborhood { concepts: Concept[]; links: { source: string; target: string; label: string }[]; truncated: boolean; availableRelations: string[] }
/** Owned by one browser/service context, never shared between authenticated hosts. */
export class WorldviewCache {
  readonly concepts = new Map<string, Concept>();
  readonly relations = new Map<string, unknown>();
  clear() { this.concepts.clear(); this.relations.clear(); }
  remember<T>(map: Map<string, T>, key: string, value: T, limit: number) {
    map.delete(key); map.set(key, value);
    if (map.size > limit) map.delete(map.keys().next().value!);
  }
  concept(value: Concept) { this.remember(this.concepts, value.urn, value, 1000); }
}
/** Bounded breadth-first traversal: at most six HTTP relation requests, not six serial chains. */
export async function loadNeighborhood(api: ServiceApi, urn: string, depth: number, relations: string[], signal: AbortSignal,
  cache = new WorldviewCache(), catalog?: WorldviewCatalog, discoverRelations = false): Promise<Neighborhood> {
  // One limiter covers all requests, including multi-endpoint switches and source endpoint resolution.
  let active = 0;
  const waiting: (() => void)[] = [];
  async function limited<T>(operation: () => Promise<T>): Promise<T> {
    if (active >= 6) await new Promise<void>(resolve => waiting.push(resolve));
    else active++;
    try { signal.throwIfAborted(); return await operation(); }
    finally { const next = waiting.shift(); if (next) next(); else active--; }
  }
  const pending = new Map<string, Promise<unknown>>();
  async function query<T>(path: string, concept: Concept): Promise<T> {
    const key = JSON.stringify([concept.urn, path]);
    if (cache.relations.has(key)) return cache.relations.get(key) as T;
    if (!pending.has(key)) pending.set(key, limited(async () => {
      const result = await post<T>(api, path, concept, signal);
      signal.throwIfAborted(); cache.remember(cache.relations, key, result, 5000); return result;
    }));
    return pending.get(key) as Promise<T>;
  }
  const resolving = new Map<string, Promise<Concept | undefined>>();
  async function resolve(value: string): Promise<Concept | undefined> {
    if (cache.concepts.has(value)) return cache.concepts.get(value);
    if (!resolving.has(value)) resolving.set(value, limited(async () => {
      const concept = await post<Concept>(api, "resolve/concept", value, signal);
      signal.throwIfAborted();
      if (!valid(concept)) return;
      cache.concept(concept); cache.remember(cache.concepts, value, concept, 1000); return concept;
    }));
    return resolving.get(value);
  }
  function valid(c: Concept | undefined | null): c is Concept {
    return !!c?.urn && c.urn !== "owl:Nothing" && !c.type?.includes("NOTHING");
  }
  function list(value: Concept | Concept[] | null | undefined): Concept[] {
    return (value == null ? [] : Array.isArray(value) ? value : [value]).filter(valid);
  }
  interface Edge { source: Concept; target: Concept; label: string }
  function edges(source: Concept, targets: Concept[], label: string, reverse = false): Edge[] {
    targets.forEach(c => cache.concept(c));
    return targets.map(target => ({ source: reverse ? target : source, target: reverse ? source : target, label }));
  }
  const predicate = (c: Concept) => c.type?.some(t => ["PREDICATE", "ROLE", "ATTRIBUTE", "REALM", "IDENTITY"].includes(t));
  const family = (c: Concept) => c.type?.some(t => ["EVENT", "PROCESS"].includes(t)) ? 2 :
    c.type?.some(t => ["SUBJECT", "AGENT"].includes(t)) ? 1 : 0;
  const influences = new Set(["AFFECTS", "CREATES", "MARKS", "INCREASES_WITH", "DECREASES_WITH", "DISCRETIZES", "CLASSIFIES"]);
  const direct: Record<string, [string, string]> = {
    DESCRIBES: ["described", "describes"], IDENTITIES: ["identities", "identity"], ATTRIBUTES: ["attributes", "attribute"],
    REALMS: ["realms", "realm"], ROLES: ["roles", "role"], INHERENT: ["inherent", "of"], ADJACENT: ["adjacent", "adjacent to"],
    CAUSED: ["caused", "causing"], CAUSANT: ["causant", "caused by"], COMPRESENT: ["compresent", "with"], GOAL: ["goal", "for"],
    COOCCURRENT: ["cooccurent", "during"], RELATIVE: ["relativeTo", "relative to"],
    SOURCE: ["relationshipSources", "links source"], TARGET: ["relationshipTargets", "links target"], OPERANDS: ["operands", "operand"],
  };
  async function relationEdges(concept: Concept, relation: string): Promise<Edge[]> {
    if (relation === "QUANTITATIVE_WITHIN") {
      const metadata = conceptMetadata(concept);
      const portion = metadata["urn:klab:semantics:quantitative-portion-identity"];
      const whole = metadata["urn:klab:semantics:quantitative-whole-identity"];
      if (typeof portion !== "string" || !portion.trim() || typeof whole !== "string" || !whole.trim()) return [];
      const [portionConcept, wholeConcept] = await Promise.all([resolve(portion), resolve(whole)]);
      if (!portionConcept || !wholeConcept) return [];
      return [...edges(concept, [portionConcept], "portion identity (quantitative within)"),
        ...edges(concept, [wholeConcept], "whole identity (quantitative within)")];
    }
    // The operands API returns the concept itself for atomic concepts: that is not a graph edge.
    if (relation === "OPERANDS" && !concept.type?.some(t => ["UNION", "INTERSECTION"].includes(t))) return [];
    if (relation === "SUBCLASS") {
      const [parents, children] = await Promise.all([query<Concept[]>("parents?direct=true", concept), query<Concept[]>("children?direct=true", concept)]);
      return [...edges(concept, list(parents), "is"), ...edges(concept, list(children), "is", true)];
    }
    if (influences.has(relation)) {
      const values = await query<{ source?: Concept; target: Concept; kind: string }[] | null>("influences", concept);
      return (values || []).filter(i => i.kind === relation && valid(i.target) && (!i.source || valid(i.source)))
        .flatMap(i => { const source = i.source || concept; cache.concept(source);
          return edges(source, [i.target], relation.toLowerCase().replaceAll("_", " ")); });
    }
    if (["CONTAINS", "EMERGENCE", "REQUIRED_IDENTITIES", "REQUIRED_REALMS"].includes(relation)) {
      const incident = (catalog?.declaredLinks || []).filter(link => link.relation === relation &&
        [link.source, link.target].some(value => value === concept.urn || cache.concepts.get(value)?.urn === concept.urn));
      const result: Edge[] = [];
      for (const link of incident) {
        const [source, target] = await Promise.all([resolve(link.source), resolve(link.target)]);
        if (!source || !target) continue;
        if (relation === "CONTAINS" && (!family(source) || family(source) !== family(target))) continue;
        if (relation === "EMERGENCE" && !source.type?.includes("CONFIGURATION")) continue;
        result.push(...edges(source, [target], link.label));
      }
      return result;
    }
    if (relation === "APPLIES") {
      const result = edges(concept, list(await query<Concept[]>("applicable", concept)), "applies to");
      if (predicate(concept)) result.push(...edges(concept, list(await query<Concept>("inherent?direct=true", concept)), "applies to"));
      return result;
    }
    if (relation === "IMPLIES") return concept.type?.includes("ROLE") ?
      edges(concept, list(await query<Concept[]>("impliedRoles?includeRelationshipEndpoints=false", concept)), "implies") : [];
    if (relation === "INHERENT" && (!concept.type?.some(t => ["QUALITY", "PROCESS"].includes(t)) || concept.type.includes("CONFIGURATION"))) return [];
    const [endpoint, label] = direct[relation];
    return edges(concept, list(await query<Concept | Concept[]>(`${endpoint}?direct=true`, concept)), label);
  }
  const focus = await resolve(urn);
  if (!focus?.urn || focus.type?.includes("NOTHING")) throw new Error("The concept could not be resolved.");
  signal.throwIfAborted();
  cache.concept(focus); cache.remember(cache.concepts, urn, focus, 1000);
  const nodes = new Map([[focus.urn, focus]]), links = new Map<string, Neighborhood["links"][number]>();
  let frontier = [focus], truncated = false;
  const visited = new Set<string>();
  const availableRelations = new Set<string>();
  for (let level = 0; level < depth; level++) {
    const next: Concept[] = [];
    const tasks = frontier.filter(concept => !visited.has(concept.urn)).flatMap(concept => {
      visited.add(concept.urn);
      return graphRelations.filter(r => discoverRelations || relations.includes(r.id)).map(relation => ({ concept, relation }));
    });
    const results = await Promise.all(tasks.map(({ concept, relation }) => relationEdges(concept, relation.id)));
    for (let index = 0; index < results.length; index++) {
      const result = results[index];
      if (result.length) availableRelations.add(tasks[index].relation.id);
      for (const edge of result) {
        for (const endpoint of [edge.source, edge.target]) {
          if (!nodes.has(endpoint.urn)) {
            if (nodes.size >= 150) { truncated = true; continue; }
            nodes.set(endpoint.urn, endpoint); next.push(endpoint);
          }
        }
        if (nodes.has(edge.source.urn) && nodes.has(edge.target.urn)) {
          const link = { source: edge.source.urn, target: edge.target.urn, label: edge.label };
          links.set(JSON.stringify(link), link);
        }
      }
    }
    frontier = next;
  }
  return { concepts: [...nodes.values()], links: [...links.values()], truncated, availableRelations: [...availableRelations] };
}
