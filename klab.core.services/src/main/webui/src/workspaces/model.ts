export interface Workspace {
  urn: string;
  projects: string[];
  repositoryStatuses?: Record<string, RepositoryStatus>;
}

export interface DocumentItem {
  id: string;
  label: string;
  language: string;
  extension: string;
  path: string;
  type: string;
  errors: number;
  warnings: number;
}

export interface Diagnostic {
  level: string;
  message: string;
  offset?: number | null;
  length?: number | null;
  normalizedOffsets?: boolean;
}

export interface Source {
  document: DocumentItem;
  text: string;
  revision: string;
  diagnostics: Diagnostic[];
  concepts?: Record<string, string>;
}

export type RepositoryStatus = "CLEAN" | "CONFLICTED" | "MODIFIED" | "ADDED" | "UNTRACKED" | "REMOVED" | "UNAUTHORIZED";

interface Repository {
  branch?: string;
  branches: string[];
  status: RepositoryStatus;
  remote: boolean;
  files: Record<string, RepositoryStatus>;
  untrackedFolders?: string[];
}

export interface Project {
  urn: string;
  description?: string;
  worldview?: string;
  definedWorldview?: string;
  version?: string;
  snapshot: boolean;
  editable: boolean;
  canManageRepository: boolean;
  administrator: boolean;
  metadata: Record<string, unknown>;
  permissions?: string;
  settingsRevision: string;
  repository?: Repository;
  documents: DocumentItem[];
}

export const documentGroups = [
  { label: "", directory: "", types: ["ONTOLOGY", "MODEL_NAMESPACE"] },
  { label: "Behaviors", directory: "behaviors", types: ["BEHAVIOR", "BEHAVIOR_COMPONENT"] },
  { label: "Applications", directory: "apps", types: ["APPLICATION"] },
  { label: "Test cases", directory: "testcases", types: ["TESTCASE"] },
  { label: "Scripts", directory: "scripts", types: ["SCRIPT"] },
  { label: "Observation strategies", directory: "strategies", types: ["STRATEGY"] },
];

export function gitDecoration(status?: RepositoryStatus) {
  return status && status !== "CLEAN" && status !== "UNAUTHORIZED" ? status.toLowerCase() : undefined;
}

export function documentGitStatus(repository: Repository | undefined, path: string) {
  if (!repository || !path || repository.status === "UNAUTHORIZED") return;
  const alternate = path.replace(/\.kactors?$/, extension => extension === ".kactor" ? ".kactors" : ".kactor");
  return repository.files[path] ?? repository.files[alternate]
    ?? (repository.untrackedFolders?.some(folder => path.startsWith(folder + "/")) ? "UNTRACKED" : undefined);
}

export function folderGitStatus(repository: Repository | undefined, directory: string,
                                documents: { status?: RepositoryStatus }[]) {
  if (!repository || !directory || repository.status === "UNAUTHORIZED") return;
  const prefix = directory + "/";
  const statuses = new Set(documents.map(document => document.status));
  for (const [path, status] of Object.entries(repository.files)) {
    if (path.startsWith(prefix)) statuses.add(status);
  }
  if (repository.untrackedFolders?.some(folder => folder === directory || folder.startsWith(prefix))) {
    statuses.add("UNTRACKED");
  }
  statuses.delete(undefined);
  statuses.delete("CLEAN");
  statuses.delete("UNAUTHORIZED");
  if (statuses.has("CONFLICTED")) return "CONFLICTED";
  if (statuses.size === 1 && statuses.has("UNTRACKED")) return "UNTRACKED";
  if (statuses.size === 1 && statuses.has("ADDED")) return "ADDED";
  if (statuses.size) return "MODIFIED";
}

export interface ReviewGlyph {
  id: string;
  line: number;
  tooltip: string;
}
import type { editor, IDisposable } from "monaco-editor/esm/vs/editor/editor.api";


export type EditorExtension = (editor: editor.IStandaloneCodeEditor) => IDisposable;
