package org.integratedmodelling.klab.api.authentication;

/**
 * Linked to the user. Individual assets rights override the generic ones. Normally configured in
 * service configuration using a {@link ResourcePrivileges} object per asset.
 */
public enum CRUDOperation {
  /** May connect to service. */
  CONNECT,
  /** May create assets */
  CREATE,
  /** May delete assets owned by the connected user (all users for role ADMIN). */
  DELETE,
  /** Editor access - may update assets managed by this service */
  UPDATE,
  /** Curator access - may modify existing asset metadata but not necessarily create assets */
  UPDATE_METADATA,
  /** General read assets when the asset rights agree */
  READ
}
