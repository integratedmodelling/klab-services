package org.integratedmodelling.klab.api.services.resources.adapters;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;

/**
 * The descriptor for a resource adapter, built from the annotation in a class annotated with {@link
 * ResourceAdapter} and part of a resource service's capabilities.
 *
 * @author Ferd
 */
public interface Adapter {

  String getName();

  /**
   * Return the adapter's type for the specified URN. Some adapters return different types according
   * to the URN; in others, the type is preset and the URN is ignored.
   *
   * @return
   */
  Artifact.Type resourceType(Urn urn);

  /**
   * Version. Cannot be null. Multiple versions of the same adapter may coexist in a service.
   *
   * @return
   */
  Version getVersion();

  /**
   * Return the serializable descriptor for the adapter.
   *
   * @return
   */
  Extensions.AdapterDescriptor getAdapterInfo();

  /**
   * If true, the adapter provides a resource contextualizer method that must be called through
   * {@link #contextualize(Resource, Geometry, Scope, Object...)} (Resource, ContextScope,
   * Object...)} before the resource is used, normally once per process stage in occurrents..
   *
   * @return
   */
  boolean hasContextualizer();

  /**
   * If true, the adapter provides an inspector method that will be automatically called before
   * publication. The inspector may change the adapter type to another if persisting inline storage
   * to e.g. a service or database can provide performance gains. The result of inspecting the
   * resource should become the public version for the URN.
   *
   * @return
   */
  boolean hasInspector();

  /**
   * If true, the adapter provides a specific validator used upon initial submission and any
   * resource update.
   *
   * @return
   */
  boolean hasValidator();

  /**
   * If true, the adapter provides a sanitizer which may extract and externalize credentials or
   * other sensitive info to remove them from the serialized resource body.
   *
   * @return
   */
  boolean hasSanitizer();

  /**
   * If true, the adapter provides a specific publisher method that may interface with external
   * repositories or modify the resource after inspection.
   *
   * @return
   */
  boolean hasPublisher();

  /**
   * Return the encoder descriptor. If the adapter is operational, the component registry must have
   * the Java implementation ready.
   *
   * @return
   */
  Extensions.FunctionDescriptor getEncoder();

  /**
   * Return the encoder descriptor. If the adapter is operational, the component registry must have
   * the Java implementation ready.
   *
   * @return
   */
  Extensions.FunctionDescriptor getInspector();

  /**
   * Return the encoder descriptor. If the adapter is operational, the component registry must have
   * the Java implementation ready.
   *
   * @return
   */
  Extensions.FunctionDescriptor getContextualizer();

  /**
   * Return the encoder descriptor. If the adapter is operational, the component registry must have
   * the Java implementation ready.
   *
   * @return
   */
  Extensions.FunctionDescriptor getPublisher();

  /**
   * Return the encoder descriptor. If the adapter is operational, the component registry must have
   * the Java implementation ready.
   *
   * @return
   */
  Extensions.FunctionDescriptor getSanitizer();

  /**
   * Return the encoder descriptor. If the adapter is operational, the component registry must have
   * the Java implementation ready.
   *
   * @return
   */
  Extensions.FunctionDescriptor getValidator();

  /**
   * Use the underlying implementation to contextualize the passed resource and obtain a copy
   * contextualized to the passed scope. If {@link #hasContextualizer()} returns false, there is no
   * need to call this and the method will simply return the input resource.
   *
   * @param resource
   * @param geometry
   * @param scope
   * @param contextParameters
   * @return
   */
  Resource contextualize(
      Resource resource, Geometry geometry, Scope scope, Object... contextParameters);

  /**
   * Extract the data. Resource must have been contextualized if {@link #hasContextualizer()}
   * returns true.
   *
   * <p>Takes a maximal list of parameters that will be matched to the possible arguments of the
   * encoder method.
   *
   * @return
   */
  boolean encode(
      Resource resource,
      Geometry geometry,
      Scheduler.Event event,
      Data.Builder builder,
      Observation observation,
      Observable observable,
      Urn urn,
      Parameters<String> urnParameters,
      Data inputs,
      Scope scope);
}
