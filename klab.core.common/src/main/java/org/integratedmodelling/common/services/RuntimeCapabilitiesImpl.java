package org.integratedmodelling.common.services;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.impl.AbstractServiceCapabilities;

import java.util.*;

public class RuntimeCapabilitiesImpl extends AbstractServiceCapabilities
    implements RuntimeService.Capabilities {

  private KlabService.Type type;
  private Storage.Type defaultStorageType;
  private Set<CRUDOperation> permissions = EnumSet.of(CRUDOperation.READ);

  @Override
  public KlabService.Type getType() {
    return type;
  }

  public void setType(KlabService.Type type) {
    this.type = type;
  }

  @Override
  public Storage.Type getDefaultStorageType() {
    return defaultStorageType;
  }

  @Override
  public Set<CRUDOperation> getPermissions() {
    return permissions;
  }

  public void setPermissions(Set<CRUDOperation> permissions) {
    this.permissions = permissions;
  }

  public void setDefaultStorageType(Storage.Type defaultStorageType) {
    this.defaultStorageType = defaultStorageType;
  }
}
