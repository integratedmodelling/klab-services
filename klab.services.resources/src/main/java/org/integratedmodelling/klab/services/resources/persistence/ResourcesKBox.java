package org.integratedmodelling.klab.services.resources.persistence;

import org.dizitart.no2.Nitrite;
import org.dizitart.no2.common.mapper.JacksonMapperModule;
import org.dizitart.no2.rocksdb.RocksDBModule;
import org.dizitart.no2.spatial.SpatialModule;
import org.dizitart.no2.spatial.jackson.GeometryModule;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceStatus;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

import java.io.File;

/**
 * Nitrite-based noSQL embedded storage for observables, resources, models and permissions. The URN
 * is always the primary key. Disk-based with automatic backup. Can navigate semantics and
 * spatial/temporal queries.
 */
public class ResourcesKBox {

  private final Nitrite db;
  private final File databaseFile;
  private final ResourcesProvider resourcesProvider;

  public ResourcesKBox(Scope scope, ServiceStartupOptions options, ResourcesProvider service) {

    this.resourcesProvider = service;
    this.databaseFile =
        BaseService.getFileInConfigurationSubdirectory(options, "data", "resources.db");
    RocksDBModule storeModule = RocksDBModule.withConfig().filePath(databaseFile.getPath()).build();
    this.db =
        Nitrite.builder()
            .loadModule(storeModule)
            .loadModule(new SpatialModule())
            .loadModule(new JacksonMapperModule(new GeometryModule()))
            .openOrCreate();
  }

  public void shutdown() {
    if (this.db != null && !this.db.isClosed()) {
      this.db.close();
    }
  }

  /**
   * Find the resource with the passed URN and version and return it.
   *
   * @param urn can have a @version segment, in which case the <code>version</code> parameter can be
   *     null or empty.
   * @param version Use {@link Version#ANY_VERSION} to obtain the latest resource revision.
   * @return the resource or null
   */
  public Resource getResource(String urn, Version version) {
    return null;
  }

  /**
   * Store the passed resource with its version. Return true if this was an update of a previously
   * stored resource or this is new.
   *
   * @param resource
   * @return
   */
  public boolean putResource(Resource resource) {
    return false;
  }

  /**
   * Return the status for the passed URN and version.
   *
   * @param urn same as in {@link #getResource(String, Version)}
   * @param version same as in {@link #getResource(String, Version)}
   * @return status or null
   */
  public ResourceStatus getStatus(String urn, Version version) {
    return null;
  }

  public boolean putStatus(String urn, Version version, ResourceStatus status) {
    return false;
  }
}
