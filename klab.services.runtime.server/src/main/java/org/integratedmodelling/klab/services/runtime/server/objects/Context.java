package org.integratedmodelling.klab.services.runtime.server.objects;

public record Context(String id, String name, Geometry geometry, Observation observer) {
}
