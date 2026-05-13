package org.integratedmodelling.klab.api.knowledge.organization;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.MetadataConvention;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public interface Project extends KlabAsset {

  /**
   * Each project must publish a manifest with all the needed information. In source project this
   * should be in META-INF/manifest.json. Much of the manifest also ends up in the metadata based on
   * the schema.
   *
   * @author Ferd
   */
  interface Manifest extends Serializable {

    String getDescription();

    /**
     * Worldview that the project is committed to. Should never be null.
     *
     * @return
     */
    String getWorldview();

    /**
     * If this returns non-null, this project contributes to the passed worldview and cannot contain
     * resources or models.
     *
     * @return
     */
    String getDefinedWorldview();

    ResourcePrivileges getPrivileges();

    Version getVersion();

    Map<String, String> getMetadata();

    Collection<MetadataConvention> getMetadataConventions();

    List<Pair<String, Version>> getPrerequisiteProjects();

    List<Pair<String, Version>> getPrerequisiteComponents();

    /**
     * Time of manifest creation. Won't change with updates or mirroring.
     *
     * @return
     */
    long getCreationTimestamp();
  }

  Manifest getManifest();

  /**
   * State of the project re: any shared repository it's tracked from.
   *
   * @return
   */
  RepositoryState getRepositoryState();

  /**
   * @return
   */
  List<KimNamespace> getNamespaces();

  List<KimOntology> getOntologies();

  List<KimObservationStrategyDocument> getObservationStrategies();

  /**
   * @return
   */
  List<String> getResourceUrns();

  /**
   * All the legitimate behaviors (in the source files)
   *
   * @return
   */
  List<KActorsBehavior> getBehaviors();

  /**
   * All the scripts in the apps directory.
   *
   * @return
   */
  List<KActorsBehavior> getScripts();

  /**
   * All the behaviors in the apps directory.
   *
   * @return
   */
  List<KActorsBehavior> getApps();

  /**
   * All the behaviors in the tests directory (which may also contain k.IM scripts).
   *
   * @return
   */
  List<KActorsBehavior> getTestCases();

  /**
   * List of any notifications pertaining to the project. If any of these is an error level
   * notification, the project is unfit for loading. Any errors in namespaces, behaviors or
   * resources should cause a single error notification in the project, listing the offending
   * resources.
   *
   * @return
   */
  List<Notification> getNotifications();

  /**
   * All the documents in the project.
   *
   * @return
   */
  default Collection<KlabDocument<?>> allDocuments() {
    List<KlabDocument<?>> documents = new ArrayList<>();
    documents.addAll(getNamespaces());
    documents.addAll(getOntologies());
    documents.addAll(getObservationStrategies());
    documents.addAll(getBehaviors());
    documents.addAll(getScripts());
    documents.addAll(getApps());
    documents.addAll(getTestCases());
    return documents;
  }

  /**
   * Compute a timestamp that reflects the state of the project, including all its components. This
   * timestamp is used to determine if the project has changed since the last time it was loaded or
   * used.
   *
   * <p>If the project is empty, returns 0L, not the time of creation.
   *
   * @return
   */
  default long computeTimestamp() {
    return allDocuments().stream()
        .map(KlabDocument::getLastUpdateTimestamp)
        .max(Long::compare)
        .orElse(getManifest().getCreationTimestamp());
  }
}
