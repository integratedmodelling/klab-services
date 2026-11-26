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
  sourceVersion?: string;
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
  lock?: ProjectLock | null;
}

export type RepositoryStatus = "CLEAN" | "CONFLICTED" | "MODIFIED" | "ADDED" | "UNTRACKED" | "REMOVED" | "UNAUTHORIZED";

export interface ProjectLock {
  editable: boolean;
  canLock: boolean;
  locked: boolean;
  owned: boolean;
  idleTimeoutMillis?: number | null;
  remainingMillis?: number | null;
}

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
  lock: ProjectLock;
  canManageRepository: boolean;
  administrator: boolean;
  metadata: Record<string, unknown>;
  permissions?: string;
  settingsRevision: string;
  repository?: Repository;
  documents: DocumentItem[];
  navigation?: NavigationNode[];
}

export interface NavigationNode {
  label: string;
  kind: "folder" | "document" | "concept" | "model" | "action" | "statement";
  document?: DocumentItem;
  offset?: number;
  length?: number;
  children?: NavigationNode[];
}

export interface ProjectNavigation {
  urn: string;
  repository?: Repository;
  children: NavigationNode[];
}

export interface WorkspaceTreeNode {
  key: string;
  label: string;
  kind: NavigationNode["kind"] | "project";
  project: string;
  document?: DocumentItem;
  offset?: number;
  length?: number;
  status?: string;
  errors: number;
  warnings: number;
  children: WorkspaceTreeNode[];
}

const folderDirectories: Record<string, string> = {
  Behaviors: "behaviors", Applications: "apps", "Test cases": "testcases",
  Scripts: "scripts", "Observation strategies": "strategies",
};

export function projectTree(name: string, navigation?: ProjectNavigation, catalogStatus?: RepositoryStatus) {
  const repository = navigation?.repository;
  function branches(nodes: NavigationNode[], parentKey: string, parentDocument?: DocumentItem): WorkspaceTreeNode[] {
    const duplicates = new Map<string, number>();
    return nodes.map(node => {
      const document = node.document ?? parentDocument;
      const segment = node.document?.id ?? `${node.kind}:${node.label}`;
      const occurrence = duplicates.get(segment) ?? 0;
      duplicates.set(segment, occurrence + 1);
      const key = parentKey + "/" + encodeURIComponent(segment) + (occurrence ? `:${occurrence}` : "");
      const children = node.children?.length ? branches(node.children, key, document) : [];
      const status = node.document ? documentGitStatus(repository, node.document.path)
        : node.kind === "folder" ? folderGitStatus(repository, folderDirectories[node.label], children.map(child => ({
          status: documentGitStatus(repository, child.document?.path ?? ""),
        }))) : undefined;
      return {
        ...node, key, project: name, document, children, status: gitDecoration(status),
        errors: node.document?.errors ?? children.reduce((count, child) => count + child.errors, 0),
        warnings: node.document?.warnings ?? children.reduce((count, child) => count + child.warnings, 0),
      };
    });
  }
  const key = encodeURIComponent(name);
  const children = navigation ? branches(navigation.children, key) : [];
  return {
    key, label: name, project: name, kind: "project", children,
    status: gitDecoration(navigation ? repository?.status : catalogStatus),
    errors: children.reduce((count, child) => count + child.errors, 0),
    warnings: children.reduce((count, child) => count + child.warnings, 0),
  } satisfies WorkspaceTreeNode;
}

export function filterTree(nodes: WorkspaceTreeNode[], query: string): WorkspaceTreeNode[] {
  if (!query) return nodes;
  return nodes.flatMap(node => {
    const qualifiedName = node.document && node.kind !== "document"
      ? `${node.document.label.split(" [", 1)[0]}:${node.label}` : "";
    if (node.label.toLowerCase().includes(query)
        || qualifiedName.toLowerCase().includes(query)
        || (node.kind === "document" && node.document?.path.toLowerCase().includes(query))) return [node];
    const children = filterTree(node.children, query);
    return children.length ? [{ ...node, children }] : [];
  });
}

export function expandedTreeKeys(nodes: WorkspaceTreeNode[], keys = new Set<string>()) {
  for (const node of nodes) {
    if (!node.children.length) continue;
    keys.add(node.key);
    expandedTreeKeys(node.children, keys);
  }
  return keys;
}

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
