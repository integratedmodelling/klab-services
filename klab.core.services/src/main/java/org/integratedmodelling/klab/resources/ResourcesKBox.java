package org.integratedmodelling.klab.resources;

import static org.dizitart.no2.filters.FluentFilter.*;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.ArrayList;
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
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.indexing.ResourceIndexer;
import org.integratedmodelling.klab.services.base.BaseService;

/**
 * Nitrite-based noSQL embedded storage for observables, resources, models and permissions. The URN
 * is always the primary key. Disk-based with automatic backup. Can navigate semantics and
 * spatial/temporal queries.
 */
public class ResourcesKBox {

  private final ResourceIndexer index;

  private final Nitrite db;
  private final File databaseFile;
  private ObjectRepository<ResourceInfo> resourceMetadata;
  private ObjectRepository<ResourceImpl> resources;
  private boolean local;

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
        this.om.configure(JsonParser.Feature.IGNORE_UNDEFINED, true);
        this.om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      }
      return this.om;
    }
  }

  public ResourcesKBox(Scope scope, ServiceStartupOptions options, BaseService service) {

    this.databaseFile =
        BaseService.getFileInConfigurationSubdirectory(options, "data", "resources.db");
    RocksDBModule storeModule = RocksDBModule.withConfig().filePath(databaseFile.getPath()).build();
    this.local = Utils.URLs.isLocalHost(service.getUrl());

    this.db =
        Nitrite.builder()
            .loadModule(storeModule)
            .loadModule(new SpatialModule())
            .loadModule(NitriteModule.module(new KlabJacksonMapper()))
            .openOrCreate();

    this.resourceMetadata = db.getRepository(new ResourceMetadataDecorator());
    this.resources = db.getRepository(new ResourceDecorator());
    this.index =
        ResourceIndexer.create(
            Configuration.INSTANCE.getDataPath(
                "services/" + service.serviceType().name().toLowerCase() + "/index/resources"));
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

  public List<Resource> getResourcesByUrnMatch(String regex) {
    var ret = new ArrayList<Resource>();
    for (var result : resources.find(where("urn").regex(regex))) {
      ret.add(result);
    }
    return ret;
  }

  public List<String> listResourcesUrns() {
    return resources.find().toList().stream().map(ResourceImpl::getUrn).toList();
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
      if (result.getAffectedCount() == 1) {
        index.index(resource1);
        index.commitChanges();
        return true;
      }
    }
    return false;
  }

  public boolean deleteResource(String urn) {
    var resource = resources.getById(urn);
    if (resource != null) {
      return false;
    }
    resources.remove(resource);
    return true;
  }

  public boolean deleteMetadata(String urn) {
    var resource = resourceMetadata.getById(urn);
    if (resource != null) {
      return false;
    }
    resourceMetadata.remove(resource);
    return true;
  }

  public List<ResourceInfo> queryResources(String query) {
    var ret = new ArrayList<ResourceInfo>();
    for (var document : index.query(query)) {
      var info = getStatus(document.getId(), Version.ANY_VERSION);
      if (info != null) {
        ret.add(info);
      }
    }
    return ret;
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
    var ret = resourceMetadata.getById(urn);
    if (ret != null) {
      ret.setLocal(this.local);
    }
    return ret;
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
      return "resources";
    }
  }
}
