package org.integratedmodelling.klab.api.data;

/**
 * An in-process view that must not cross a serialized service boundary. Implementing a domain API
 * (including a Serializable API) does not make a local view a portable value. Callers must create
 * an explicit transport representation, such as Observation.forTransport or Geometry.forTransport.
 * JSON serializers reject these views, including when nested in a transport payload.
 */
public interface LocalView {}
