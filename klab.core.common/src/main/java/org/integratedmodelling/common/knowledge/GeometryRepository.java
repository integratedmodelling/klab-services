package org.integratedmodelling.common.knowledge;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.concurrent.ExecutionException;

/**
 * A manager that enables storing and caching geometries and the respective scales, with retrieval
 * based on either the geometry string specification or a geometry key. Holds an amount of
 * geometries that depends on the size of the specification string, assuming it correlates linearly
 * with the
 */
public enum GeometryRepository {
  INSTANCE;

  private Cache<String, Pair<Geometry, Scale>> cache =
      CacheBuilder.newBuilder()
          .concurrencyLevel(20) // TODO configure
          .maximumWeight(800000L) // TODO configure - this is just the spec length
          .weigher((key, value) -> ((Pair<Geometry, Scale>) value).getFirst().encode().length())
          .build();

  public Scale scale(Geometry geometry) {
    if (geometry == null) {
      return null;
    }
    if (geometry instanceof Scale scale) {
      return scale;
    }
    var cached = cache.getIfPresent(geometry.key());
    if (cached == null) {
      var scale = Scale.create(geometry);
      cache.put(geometry.key(), Pair.of(geometry, scale));
      return scale;
    }
    return cached.getSecond();
  }

  public Scale scale(Geometry geometry, Scope scope) {
    if (geometry == null) {
      return null;
    }
    if (geometry instanceof Scale scale) {
      return scale;
    }
    var cached = cache.getIfPresent(geometry.key());
    if (cached == null) {
      var scale = Scale.create(geometry, scope);
      cache.put(geometry.key(), Pair.of(geometry, scale));
      return scale;
    }
    return cached.getSecond();
  }

  public Geometry geometry(Geometry geometry) {
    if (geometry == null) {
      return null;
    }
    if (geometry instanceof Scale scale) {
      var cached = cache.getIfPresent(geometry.key());
      if (cached == null) {
        var ret = scale.as(Geometry.class);
        cache.put(geometry.key(), Pair.of(ret, scale));
        return ret;
      }
      return cached.getFirst();
    }
    return geometry;
  }

  /** Get and cache the */
  public <T extends Geometry> T get(String encoded, Class<T> geometryClass) {
    var identifier = Utils.Strings.hash(encoded);
    var cached = cache.getIfPresent(identifier);
    if (cached == null) {
      var geometry = Geometry.create(encoded, identifier);
      cached = Pair.of(geometry, Scale.create(geometry));
      cache.put(identifier, cached);
    }
    return (T)
        (Scale.class.isAssignableFrom(geometryClass) ? cached.getSecond() : cached.getFirst());
  }

  public <T extends Geometry> T getUnion(
      Geometry geometry, Geometry geometry1, Class<T> geometryClass) {
    var key = geometry.key() + "|" + geometry1.key();
    var cached = cache.getIfPresent(key);
    if (cached == null) {
      var scale1 = Scale.create(geometry);
      var scale2 = Scale.create(geometry1);
      var ret = scale1.merge(scale2, LogicalConnector.UNION);
      cached = Pair.of(ret.as(Geometry.class), (Scale) ret);
      cache.put(key, cached);
    }
    return (T)
        (Scale.class.isAssignableFrom(geometryClass) ? cached.getSecond() : cached.getFirst());
  }

  public <T extends Geometry> T getIntersection(
      Geometry geometry, Geometry geometry1, Class<T> geometryClass) {
    var key = geometry.key() + "|" + geometry1.key();
    var cached = cache.getIfPresent(key);
    if (cached == null) {
      var scale1 = Scale.create(geometry);
      var scale2 = Scale.create(geometry1);
      var ret = scale1.merge(scale2, LogicalConnector.INTERSECTION);
      cached = Pair.of(ret.as(Geometry.class), (Scale) ret);
      cache.put(key, cached);
    }
    return (T)
        (Scale.class.isAssignableFrom(geometryClass) ? cached.getSecond() : cached.getFirst());
  }

  public void put(Geometry geometry, Scale scale) {
    cache.put(geometry.key(), Pair.of(geometry, scale));
  }
}
