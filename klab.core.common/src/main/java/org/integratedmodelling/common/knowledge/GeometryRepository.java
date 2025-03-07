package org.integratedmodelling.common.knowledge;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.concurrent.ExecutionException;

/**
 * A manager that enables storing and caching geometries and the respective scales, with retrieval based on
 * either the geometry string specification or a geometry key. Holds an amount of geometries that depends on
 * the size of the specification string, assuming it correlates linearly with the
 */
public enum GeometryRepository {

    INSTANCE;

    private LoadingCache<String, Pair<Geometry, Scale>> cache =
            CacheBuilder.newBuilder()
                        .concurrencyLevel(20) // TODO configure
                        .maximumWeight(800000L) // TODO configure - this is just the spec length
                        .weigher((key, value) -> isKey(key.toString()) ? 0 : key.toString().length())
                        .build(new CacheLoader<>() {
                            @Override
                            public Pair<Geometry, Scale> load(String key) throws Exception {
                                var geometry = Geometry.create(key);
                                Scale scale = Scale.create(geometry);
                                if (geometry instanceof GeometryImpl geometryImpl && geometryImpl.key() == null)  {
                                    geometryImpl.setKey(newKey(geometry));
                                }
                                return Pair.of(geometry, scale);
                            }
                        });

    /**
     * TODO add one with Geometry with key lookup/insertion
     *
     * @param identifier
     * @param geometryClass
     * @return
     * @param <T>
     */
    public <T extends Geometry> T get(String identifier, Class<T> geometryClass) {
        try {
            var ret = cache.get(identifier);
            return (T) (Scale.class.isAssignableFrom(geometryClass) ? ret.getSecond() : ret.getFirst());
        } catch (ExecutionException e) {
            Logging.INSTANCE.warn("Concurrency level in geometry cache exceeded");
            var ret = Geometry.create(identifier);
            if (Coverage.class.isAssignableFrom(geometryClass)) {
                var scale = Scale.create(ret);
                return (T) Coverage.create(scale, 1.0);
            } else if (Scale.class.isAssignableFrom(geometryClass)) {
                return (T) Scale.create(ret);
            }
            return (T) ret;
        }
    }

    private boolean isKey(String identifier) {
        return identifier.startsWith("KG[");
    }

    /**
     * FIXME remove the stupid UUID and use a hash of the encoded geometry value.
     * @return
     */
    private String newKey(Geometry geometry) {
        return "KG[" + Utils.Names.shortUUID() + "]";
    }

    public Geometry getUnion(Geometry geometry, Geometry geometry1) {
        // TODO optimize as much as possible
        var scale1 = Scale.create(geometry);
        var scale2 = Scale.create(geometry1);
        return scale1.merge(scale2, LogicalConnector.UNION);
    }

    public Geometry getIntersection(Geometry geometry, Geometry geometry1) {
        // TODO optimize as much as possible
        var scale1 = Scale.create(geometry);
        var scale2 = Scale.create(geometry1);
        return scale1.merge(scale2, LogicalConnector.INTERSECTION);
    }
}
