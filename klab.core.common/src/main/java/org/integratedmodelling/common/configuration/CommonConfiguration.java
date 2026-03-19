package org.integratedmodelling.common.configuration;

import org.integratedmodelling.common.distribution.StackImpl;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Extent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Envelope;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Shape;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resolver.Coverage;

import java.util.Collection;

/**
 * For configuration in non-service environments. Unable to do much except creating a software
 * stack, which is needed in the modeler and for testing. Over time we may move some more methods
 * from of the service configuration to this class.
 */
public class CommonConfiguration implements Klab.Configuration {

  @Override
  public Observable promoteConceptToObservable(Concept concept) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Observable promoteConceptToObservable(Concept concept, String named) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Observable.Builder getObservableBuilder(Concept observable, Scope scope) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Observable.Builder getObservableBuilder(Observable observable, Scope scope) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Scale promoteGeometryToScale(Geometry geometry, Scope scope) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Projection getDefaultSpatialProjection() {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Projection getLatLonSpatialProjection() {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Scale createScaleFromExtents(Collection<Extent<?>> extents) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Shape createShapeFromTextSpecification(String shapeText, Projection projection) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Projection getSpatialProjection(String string) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Coverage promoteScaleToCoverage(Scale geometry, double coverage) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Model.Builder getModelBuilder(Observable observable) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Model.Builder getModelBuilder(Artifact.Type nonSemanticType) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Model.Builder getModelBuilder(Resource resource) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Model.Builder getModelBuilder(Object value) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Model.Builder getModelLearner(String outputResourceUrn) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Quantity parseQuantity(String quantityDescription) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Extent<?> createExtentCopy(Extent<?> extent) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Concept getNonSemanticConcept(SemanticType semanticType) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Envelope getSpatialEnvelope(
      double minX, double minY, double maxX, double maxY, Projection projection) {
    throw new KlabIllegalStateException(
        "k.LAB environment not configured for service-level operations");
  }

  @Override
  public Stack createSoftwareStack(String name, Settings settings) {
    return new StackImpl(name, settings);
  }
}
