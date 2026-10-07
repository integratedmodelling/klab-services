package org.integratedmodelling.klab.services.resources.persistence;

import java.util.Locale;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.base.BaseService;

/** Explicit opt-in; unknown configurations fail instead of silently choosing another database. */
public final class ModelCatalogs {
  private ModelCatalogs() {}
  public static ModelCatalog create(ResourcesService service, ServiceStartupOptions options) {
    return switch (System.getProperty("klab.model.catalog", "h2").toLowerCase(Locale.ROOT)) {
      case "h2" -> ModelKbox.create(service);
      case "nitrite" -> new DocumentModelKbox(service, new NitriteKboxStore(
          BaseService.getFileInConfigurationSubdirectory(options, "data", "models-v1.db").toPath()));
      case "mongo", "mongodb" -> new DocumentModelKbox(service,
          new MongoKboxStore(required("klab.model.catalog.mongo.uri"), required("klab.model.catalog.mongo.database")));
      default -> throw new IllegalArgumentException("Unknown klab.model.catalog backend");
    };
  }
  private static String required(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
    return value;
  }
}
