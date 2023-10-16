package org.integratedmodelling.klab.api.knowledge;

/**
 * All knowledge in k.LAB has a URN and is serializable. Methods in derived classes only use the
 * <code>getXxxx</code> naming pattern for serializable fields, to ensure easy serialization to JSON
 * and the like; everything else is expected to be handled through the reasoner service, with optional caching
 * if latency is significant.
 * <p>
 * Just a tag interface for now, just marks the raised expectations compared to a KlabAsset.
 *
 * @author ferd
 */
public interface Knowledge extends KlabAsset {

}
