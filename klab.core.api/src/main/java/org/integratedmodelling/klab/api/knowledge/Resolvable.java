package org.integratedmodelling.klab.api.knowledge;

/**
 * Tag interface for any asset that can be resolved, either alone or in a pre-existing context. This can also
 * be assigned to new objects defined through <code>define</code> statements, as long as an
 * {@link ObservationStrategy} strategy capable of resolving them is defined. In the core k.LAB
 * implementation, resolvables are observations (specified through the {@link Instance} class),
 * {@link Observable}s and their k.IM counterparts, {@link Model}s and their k.IM counterparts, plus (TODO)
 * {@link Resource}s.
 * <p>
 * The
 */
public interface Resolvable extends KlabAsset {
}
