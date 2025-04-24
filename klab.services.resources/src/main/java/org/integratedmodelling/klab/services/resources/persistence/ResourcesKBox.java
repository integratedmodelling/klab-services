package org.integratedmodelling.klab.services.resources.persistence;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.List;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.common.mapper.JacksonMapper;
import org.dizitart.no2.common.module.NitriteModule;
import org.dizitart.no2.index.IndexType;
import org.dizitart.no2.repository.EntityDecorator;
import org.dizitart.no2.repository.EntityId;
import org.dizitart.no2.repository.EntityIndex;
import org.dizitart.no2.repository.ObjectRepository;
import org.dizitart.no2.rocksdb.RocksDBModule;
import org.dizitart.no2.spatial.SpatialModule;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.services.ServiceStartupOptions;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;

/**
 * Nitrite-based noSQL embedded storage for observables, resources, models and permissions. The URN
 * is always the primary key. Disk-based with automatic backup. Can navigate semantics and
 * spatial/temporal queries.
 */
public class ResourcesKBox {

  private final Nitrite db;
  private final File databaseFile;
  private final ResourcesProvider resourcesProvider;
  private ObjectRepository<ResourceInfo> resourceMetadata;
  private ObjectRepository<ResourceImpl> resources;

  /** Take over the mapper so we can use interfaces */
  private static class KlabJacksonMapper extends JacksonMapper {

    ObjectMapper om;

    @Override
    public ObjectMapper getObjectMapper() {
      if (om == null) {
        this.om = new ObjectMapper();
        JacksonConfiguration.configureObjectMapperForKlabTypes(this.om);
        this.om.setVisibility(
            this.om
                .getSerializationConfig()
                .getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE));
        this.om.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        this.om.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        this.om.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        this.om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      }
      return this.om;
    }
  }

  public ResourcesKBox(Scope scope, ServiceStartupOptions options, ResourcesProvider service) {

    this.resourcesProvider = service;
    this.databaseFile =
        BaseService.getFileInConfigurationSubdirectory(options, "data", "resources.db");
    RocksDBModule storeModule = RocksDBModule.withConfig().filePath(databaseFile.getPath()).build();

    this.db =
        Nitrite.builder()
            .loadModule(storeModule)
            .loadModule(new SpatialModule())
            .loadModule(NitriteModule.module(new KlabJacksonMapper()))
            .openOrCreate();

    this.resourceMetadata = db.getRepository(new ResourceMetadataDecorator());
    this.resources = db.getRepository(new ResourceDecorator());
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
    // TODO handle version
    return resources.getById(urn);
  }

  /**
   * Store the passed resource with its version. Return true if this was an update of a previously
   * stored resource or this is new.
   *
   * @param resource
   * @return
   */
  public boolean putResource(Resource resource) {
    if (resource instanceof ResourceImpl resource1) {
      var result = resources.update(resource1, true);
      return result.getAffectedCount() == 1;
    }
    return false;
  }

  /**
   * Return the status for the passed URN and version.
   *
   * @param urn same as in {@link #getResource(String, Version)}
   * @param version same as in {@link #getResource(String, Version)}
   * @return status or null
   */
  public ResourceInfo getStatus(String urn, Version version) {
    // TODO handle version
    return resourceMetadata.getById(urn);
  }

  public boolean putStatus(ResourceInfo status) {
    var result = resourceMetadata.update(status, true);
    return result.getAffectedCount() == 1;
  }

  private static class ResourceMetadataDecorator implements EntityDecorator<ResourceInfo> {

    @Override
    public Class<ResourceInfo> getEntityType() {
      return ResourceInfo.class;
    }

    @Override
    public EntityId getIdField() {
      return new EntityId("urn");
    }

    @Override
    public List<EntityIndex> getIndexFields() {
      return List.of(new EntityIndex(IndexType.UNIQUE, "urn"));
    }

    @Override
    public String getEntityName() {
      return "resourceInfo";
    }
  }

  private static class ResourceDecorator implements EntityDecorator<ResourceImpl> {

    @Override
    public Class<ResourceImpl> getEntityType() {
      return ResourceImpl.class;
    }

    @Override
    public EntityId getIdField() {
      return new EntityId("urn");
    }

    @Override
    public List<EntityIndex> getIndexFields() {
      return List.of(new EntityIndex(IndexType.UNIQUE, "urn"));
    }

    @Override
    public String getEntityName() {
      return "resourceInfo";
    }
  }
}
