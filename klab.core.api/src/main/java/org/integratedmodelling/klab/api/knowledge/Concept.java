package org.integratedmodelling.klab.api.knowledge;

import java.util.List;
import java.util.Set;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public interface Concept extends Semantics {

  /**
   * @return
   */
  Set<SemanticType> getType();

  /**
   * The 'collective' character ('each' ...) belongs to the observable and not to the concept, but
   * for consistent parsing we must add it to the Concept and propagate it to the observable. When
   * the Concept is used alone, the collective character should be ignored as it is an attribute of
   * the observation (determining the {@link DescriptionType#INSTANTIATION}) and does not affect the
   * semantics.
   *
   * @return
   */
  boolean isCollective();

  /**
   * This returns null in "normal" concepts, while a concept qualified with <code>any</code>, <code>
   * all</code> or <code>no</code> will return, respectively, {@link LogicalConnector#UNION}, {@link
   * LogicalConnector#INTERSECTION} or , {@link LogicalConnector#EXCLUSION}.
   *
   * @return
   */
  LogicalConnector getQualifier();

  /**
   * Make and return the singular version of this concept.
   *
   * @return
   */
  Concept singular();

  /**
   * Anything that came up during parsing. If there is any error notification, the type will be
   * SemanticType.NOTHING.
   *
   * @return
   */
  List<Notification> getNotifications();

  /**
   * Make and return the collective version of this concept. Calling this on anything other than a
   * substantial will throw an exception.
   *
   * @return
   */
  Concept collective();

  /**
   * Return a unique non-semantic observable for collective subject observations
   *
   * @return
   */
  static Concept objects() {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException(
              "k.LAB environment not configured");
    }
    return configuration.getNonSemanticConcept(SemanticType.SUBJECT);
  }

  /**
   * Return a unique non-semantic observable for collective event observations
   *
   * @return
   */
  static Concept events() {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException(
              "k.LAB environment not configured");
    }
    return configuration.getNonSemanticConcept(SemanticType.EVENT);
  }

  /**
   * Return a unique non-semantic observable for collective relationship observations
   *
   * @return
   */
  static Concept relationships() {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException(
              "k.LAB environment not configured");
    }
    return configuration.getNonSemanticConcept(SemanticType.RELATIONSHIP);
  }

  /**
   * Return a unique non-semantic observable for number observations.
   *
   * @return
   */
  static Concept number() {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException(
              "k.LAB environment not configured");
    }
    return configuration.getNonSemanticConcept(SemanticType.QUANTIFIABLE);
  }

  /**
   * Return a unique non-semantic observable for textual categories.
   *
   * @return
   */
  static Concept text() {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException(
              "k.LAB environment not configured");
    }
    return configuration.getNonSemanticConcept(SemanticType.CATEGORY);
  }

  /**
   * Return a unique non-semantic observable for booleans.
   *
   * @return
   */
  static Concept bool() {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException(
              "k.LAB environment not configured");
    }
    return configuration.getNonSemanticConcept(SemanticType.PRESENCE);
  }

  /**
   * Return a unique non-semantic observable for booleans.
   *
   * @return
   */
  static Concept nothing() {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException(
              "k.LAB environment not configured");
    }
    return configuration.getNonSemanticConcept(SemanticType.NOTHING);
  }
}
