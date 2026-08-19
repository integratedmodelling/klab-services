package org.integratedmodelling.common.knowledge;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
      Caffeine.newBuilder()
          .maximumWeight(800000L) // TODO configure - this is just the spec length
          .weigher((String key, Pair<Geometry, Scale> value) -> value.getFirst().encode().length())
          .build();

  private final Cache<String, Pair<Geometry, Scale>> mergeCache =
      Caffeine.newBuilder()
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
    return getOrCreate(geometry, () -> Scale.create(geometry)).getSecond();
  }

  public Scale scale(Geometry geometry, Scope scope) {
    if (geometry == null) {
      return null;
    }
    if (geometry instanceof Scale scale) {
      return scale;
    }
    return getOrCreate(geometry, () -> Scale.create(geometry, scope)).getSecond();
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

    if ("X".equals(encoded)) {
      return (T) (Scale.class.isAssignableFrom(geometryClass) ? Scale.empty() : Geometry.EMPTY);
    } else if ("*".equals(encoded)) {
      var scalar = Geometry.create("*");
      return (T) (Scale.class.isAssignableFrom(geometryClass) ? Scale.create(scalar) : scalar);
    }

    var identifier = Utils.Strings.hash(encoded);
    /*
     * getOrCreate() already owns the cache load for this identifier. Wrapping it in another
     * cache.get() for the same key makes the inner call wait for its own in-progress load, which
     * Guava rejects as a recursive load. This path is exercised whenever a geometry is rebuilt
     * from its persisted definition (for example while adapting an observation from Neo4j).
     */
    var cached = getOrCreate(Geometry.create(encoded, identifier), identifier);
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

    var scale1 = scale(geometry);
    var scale2 = scale(geometry1);
    var key = mergeKey(scale1, scale2, how);
    var cached =
        getCached(
            mergeCache,
            key,
            () -> {
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
    var cached = canonicalPair(scale);
    cache.put(geometry.key(), cached);
    cache.put(cached.getFirst().key(), cached);
  }

  public Scale outerUnion(Geometry total, Geometry incoming) {
    // TODO - keep the union small, using a convex hull or simplifying afterwards. Also should use
    //  options to check if the user/federation wants full precision.
    return getUnion(total, incoming, Scale.class);
  }

  private String mergeKey(Geometry geometry, Geometry geometry1, LogicalConnector how) {
    var first = geometry.key();
    var second = geometry1.key();
    if ((how == LogicalConnector.UNION || how == LogicalConnector.INTERSECTION)
        && first.compareTo(second) > 0) {
      var swap = first;
      first = second;
      second = swap;
    }
    return "merge:" + how.name() + ':' + first + ':' + second;
  }

  private Pair<Geometry, Scale> getOrCreate(Geometry geometry, Supplier<Scale> supplier) {
    return getOrCreate(geometry, geometry.key(), supplier);
  }

  private Pair<Geometry, Scale> getOrCreate(Geometry geometry, String lookupKey) {
    return getOrCreate(geometry, lookupKey, () -> Scale.create(geometry));
  }

  private Pair<Geometry, Scale> getOrCreate(
      Geometry geometry, String lookupKey, Supplier<Scale> supplier) {
    var cached = getCached(cache, lookupKey, () -> canonicalPair(supplier.get()));

    /*
     * Publish aliases only after the primary load has completed. Besides avoiding mutation of a
     * key whose value is still loading, this makes every alias point to the same canonical pair.
     */
    if (!lookupKey.equals(cached.getFirst().key())) {
      cache.put(cached.getFirst().key(), cached);
    }
    if (!lookupKey.equals(geometry.key())) {
      cache.put(geometry.key(), cached);
    }
    return cached;
  }

  private Pair<Geometry, Scale> canonicalPair(Scale scale) {
    var geometry = scale.as(Geometry.class);
    return Pair.of(geometry, scale);
  }

  private Pair<Geometry, Scale> getCached(
      Cache<String, Pair<Geometry, Scale>> target,
      String key,
      Supplier<Pair<Geometry, Scale>> supplier) {
    var cached = target.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    /*
     * Do not use Cache.get(key, mappingFunction) here. Scale creation calls the configured
     * geometry promoter, which legitimately publishes the new geometry/scale pair back into this
     * repository. That reentrant put targets the same key and is rejected by both Guava's loading
     * cache and Caffeine's compute-based implementation. Construct outside a cache computation,
     * then converge concurrent creators on whichever value was published first.
     */
    var created = supplier.get();
    var previous = target.asMap().putIfAbsent(key, created);
    return previous == null ? created : previous;
  }
}
