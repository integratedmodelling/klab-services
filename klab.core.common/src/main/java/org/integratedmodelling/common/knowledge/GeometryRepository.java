package org.integratedmodelling.common.knowledge;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
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
                                if (geometry instanceof GeometryImpl geometryImpl && geometryImpl.getKey() == null)  {
                                    geometryImpl.setKey(newKey());
                                }
                                return Pair.of(geometry, scale);
                            }
                        });

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

    private String newKey() {
        return "KG[" + Utils.Names.shortUUID() + "]";
    }

}
