package org.integratedmodelling.common.knowledge;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.utils.Utils;

/**
 * A manager that enables storing and caching geometries and the respective scales, with retrieval
 * based on either the geometry string specification or a geometry key. Holds an amount of
 * geometries that depends on the size of the specification string, assuming it correlates linearly
 * with the
 */
public enum GeometryRepository {
  INSTANCE;

  private final Cache<String, Pair<Geometry, Scale>> cache =
      CacheBuilder.newBuilder()
          .concurrencyLevel(20) // TODO configure
          .maximumWeight(800000L) // TODO configure - this is just the spec length
          .weigher((String key, Pair<Geometry, Scale> value) -> value.getFirst().encode().length())
          .build();

  private final Cache<String, Pair<Geometry, Scale>> mergeCache =
      CacheBuilder.newBuilder()
          .concurrencyLevel(20) // TODO configure
          .maximumWeight(800000L) // TODO configure - this is just the spec length
          .weigher((String key, Pair<Geometry, Scale> value) -> value.getFirst().encode().length())
          .build();

  /**
   * Ensure that all extents are in a fully specified and usable form. Once that is done, build a
   * scale and cache it. If not, return null.
   *
   * <p>This is called upon {@link
   * org.integratedmodelling.klab.api.services.RuntimeService#submit(Observation, ContextScope)} to
   * ensure that all geometries are fully specified before anything is done with them, and is meant
   * for geometries obtained through JSON and implemented as a standard Geometry. Passing a scale
   * will return the scale as a geometry without changes or caching.
   *
   * @param geometry
   * @return
   */
  public Geometry sanitize(Geometry geometry) {
    if (geometry instanceof Scale scale) {
      return scale.as(Geometry.class);
    }

    var scale = scale(geometry);
    return geometry(scale);
  }

  public Scale scale(Geometry geometry) {
    if (geometry == null) {
      return null;
    }
    if (geometry instanceof Scale scale) {
      return scale;
    }
    return getCached(cache, geometry.key(), () -> Pair.of(geometry, Scale.create(geometry)))
        .getSecond();
  }

  public Scale scale(Geometry geometry, Scope scope) {
    if (geometry == null) {
      return null;
    }
    if (geometry instanceof Scale scale) {
      return scale;
    }
    return getCached(cache, geometry.key(), () -> Pair.of(geometry, Scale.create(geometry, scope)))
        .getSecond();
  }

  public Geometry geometry(Geometry geometry) {
    if (geometry == null) {
      return null;
    }
    if (geometry instanceof Scale scale) {
      return getCached(
              cache,
              geometry.key(),
              () -> {
                var ret = scale.as(Geometry.class);
                return Pair.of(ret, scale);
              })
          .getFirst();
    }
    return geometry;
  }

  /** Get and cache the */
  public <T extends Geometry> T get(String encoded, Class<T> geometryClass) {
    var identifier = Utils.Strings.hash(encoded);
    var cached =
        getCached(
            cache,
            identifier,
            () -> {
              var geometry = Geometry.create(encoded, identifier);
              return Pair.of(geometry, Scale.create(geometry));
            });
    return (T)
        (Scale.class.isAssignableFrom(geometryClass) ? cached.getSecond() : cached.getFirst());
  }

  public <T extends Geometry> T getUnion(
      Geometry geometry, Geometry geometry1, Class<T> geometryClass) {
    return getMerged(geometry, geometry1, LogicalConnector.UNION, geometryClass);
  }

  public <T extends Geometry> T getIntersection(
      Geometry geometry, Geometry geometry1, Class<T> geometryClass) {
    return getMerged(geometry, geometry1, LogicalConnector.INTERSECTION, geometryClass);
  }

  public <T extends Geometry> T getMerged(
      Geometry geometry, Geometry geometry1, LogicalConnector how, Class<T> geometryClass) {

    if (geometry == null || geometry1 == null) {
      return null;
    }

    var key = mergeKey(geometry, geometry1, how);
    var cached =
        getCached(
            mergeCache,
            key,
            () -> {
              var scale1 = scale(geometry);
              var scale2 = scale(geometry1);
              var merged = scale1.merge(scale2, how);
              var mergedScale =
                  merged instanceof Scale mergedAsScale ? mergedAsScale : scale(merged);
              var mergedGeometry = geometry(mergedScale);
              return Pair.of(mergedGeometry, mergedScale);
            });
    return (T)
        (Scale.class.isAssignableFrom(geometryClass) ? cached.getSecond() : cached.getFirst());
  }

  public void put(Geometry geometry, Scale scale) {
    cache.put(geometry.key(), Pair.of(geometry, scale));
  }

  public Scale outerUnion(Geometry total, Geometry incoming) {
    // TODO - keep the union small, using a convex hull or simplifying afterwards. Also should use
    //  options to check if the user/federation wants full precision.
    return getUnion(total, incoming, Scale.class);
  }

  private String mergeKey(Geometry geometry, Geometry geometry1, LogicalConnector how) {
    return "merge:" + how.name() + ':' + geometry.key() + ':' + geometry1.key();
  }

  private Pair<Geometry, Scale> getCached(
      Cache<String, Pair<Geometry, Scale>> target,
      String key,
      Supplier<Pair<Geometry, Scale>> supplier) {
    try {
      return target.get(key, supplier::get);
    } catch (ExecutionException e) {
      throw new IllegalStateException("error creating geometry cache entry for " + key, e);
    }
  }
}
