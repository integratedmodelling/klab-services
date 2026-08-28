import type { AuthState } from "./services/auth";
import type { ServiceApi } from "./services/api";

export interface AuthenticationSettings {
  enabled: boolean;
  url?: string;
  realm: string;
  clientId: string;
}

export interface DashboardPanel {
  id: string;
  title: string;
  description: string;
  component: string;
  order: number;
  requiresAuthentication: boolean;
}

export interface DashboardLink {
  label: string;
  href: string;
  external: boolean;
}

export interface DashboardConfiguration {
  serviceType: string;
  title: string;
  subtitle: string;
  logoUrl: string;
  authentication: AuthenticationSettings;
  panels: DashboardPanel[];
  links: DashboardLink[];
}

export interface DashboardContext {
  api: ServiceApi;
  auth: AuthState;
  config: DashboardConfiguration;
  status: Record<string, unknown> | null;
  capabilities: Record<string, unknown> | null;
  refresh: () => Promise<void>;
}
