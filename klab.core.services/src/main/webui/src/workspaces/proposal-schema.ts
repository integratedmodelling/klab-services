import ontologySchema from "../../../../../../klab.services.resources/src/main/resources/schemas/llm/domain-context-proposal.schema.json";
import { prepareProposal, record, reviewId, type RecordValue } from "./project-review";

export interface ProposalField {
  $ref?: string;
  allOf?: ProposalField[];
  oneOf?: ProposalField[];
  type?: string | string[];
  enum?: unknown[];
  const?: unknown;
  minimum?: number;
  description?: string;
  title?: string;
  properties?: Record<string, ProposalField>;
  required?: string[];
  items?: ProposalField;
}
export interface ProposalEditorConfig {
  payload: string;
  fields: ProposalField;
  definitions: Record<string, ProposalField>;
  managed: ReadonlySet<string>;
  prepare: typeof prepareProposal;
}
const configs: Partial<Record<string, ProposalEditorConfig[]>> = {
  ONTOLOGY: [{
    payload: "domain-context-proposal/1.3",
    fields: ontologySchema.properties.proposal,
    definitions: ontologySchema.$defs,
    prepare: prepareProposal,
    managed: new Set(["id", "revision_id", "supersedes_revision", "iteration", "status", "generated_at",
      "iteration_control", "validation"]),
  }],
};
export function proposalEditorConfig(documentType?: string, payload?: string) {
  return documentType ? configs[documentType]?.find(config => !payload || config.payload === payload) : undefined;
}
export function resolveField(config: ProposalEditorConfig, field?: ProposalField, value?: unknown): ProposalField {
  if (!field) return {};
  const definition = field.$ref ? config.definitions[field.$ref.replace("#/$defs/", "")] : undefined;
  if (definition) return resolveField(config, definition, value);
  if (field.allOf) return field.allOf.reduce<ProposalField>((result, part) => {
    const next = resolveField(config, part, value);
    return { ...result, ...next, properties: { ...result.properties, ...next.properties },
      required: [...new Set([...(result.required ?? []), ...(next.required ?? [])])] };
  }, {});
  if (field.oneOf && value !== undefined) {
    const options = field.oneOf.map(option => resolveField(config, option, value));
    const choice = record(value) ? options.find(option => Object.entries(option.properties ?? {})
      .some(([key, member]) => member.const !== undefined && member.const === value[key])) : undefined;
    return choice ?? options[0] ?? {};
  }
  return field;
}
export function fieldLabel(key: string) {
  const label = key.replaceAll("_", " ");
  return label.charAt(0).toUpperCase() + label.slice(1);
}
export function initialField(config: ProposalEditorConfig, field?: ProposalField, statement?: string): unknown {
  const definition = resolveField(config, field);
  if (definition.const !== undefined) return definition.const;
  if (definition.enum?.length) return definition.enum[0];
  const type = Array.isArray(definition.type) ? definition.type[0] : definition.type;
  if (type === "object") {
    const item: RecordValue = {};
    for (const [index, key] of (definition.required ?? []).entries()) {
      const member = definition.properties?.[key];
      if (index === 0 && key.endsWith("_id")) item[key] = reviewId();
      else if (statement && key === "qualified_name") item[key] = statement;
      else if (statement && key === "label") item[key] = statement.split(":").at(-1);
      else item[key] = initialField(config, member);
    }
    return item;
  }
  if (type === "array") return [];
  if (type === "boolean") return false;
  if (type === "number" || type === "integer") return definition.minimum ?? 0;
  return type === "null" ? null : "";
}
