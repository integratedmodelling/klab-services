package org.integratedmodelling.klab.api.knowledge;

import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * Authorities are built from components and are unique to the reasoner. The @Authority annotation
 * tags classes and methods so that an Authority object can be built by the ComponentRegistry. When
 * an authority is referenced, the Reasoner looks up a service in the scope that provides it;
 * failing that, it looks for a component that provides it and installs it. THe worldview must
 * register the authority ID it wishes by asking for a Configuration using the worldview-provided
 * root concepts tagged with <code>requires authority NAME { ....configuration...}</code>.
 */
public interface Authority {

  /**
   * Each authority must be configured to provide the bridge to the reasoner. The resolution API is
   * invoked on the URL returned in configuration.
   */
  interface Configuration {

    /**
     * If not null, the authority won't be loaded unless the worldview served by the reasoner is the
     * same.
     *
     * @return
     */
    String getWorldview();

    /**
     * The URL is the endpoint for the resolution API and CRUD operations on terminology.
     *
     * @return
     */
    URL getResolutionEndpoint();

    /**
     * Entry points are configurable parameters for the configuration of the authority in the
     * worldview. The worldview is connected to the authority and must specify all the mandatory
     * entry points when the link is declared (in a `defines authority` statement).
     *
     * @return
     */
    List<ServiceInfo.Argument> getEntryPoints();

    /**
     * These may come from the configuration and if any error notification appears, the
     * configuration should not be used.
     *
     * @return
     */
    List<Notification> getNotifications();

    /**
     * A list of namespaces that the authority provides. If not empty, the namespaces can be used
     * like normal namespaces for concepts aliases. The list may be empty, established directly by
     * the authority, or negotiated through configuration.
     *
     * @return
     */
    List<String> getNamespaces();

    /**
     * @return
     */
    Metadata getMetadata();

    /**
     * If null, the configuration is for the main authority. Otherwise this can be a path to a
     * sub-authority within the main authority. The path is always converted to upper case for
     * referencing.
     *
     * @return
     */
    String getId();
  }

  interface Identity {

    /**
     * The official authority ID, which may be different from what the user provided.
     *
     * @return
     */
    String getId();

    /**
     * Stable and consistent ID suitable for naming the correspondent concept.
     *
     * @return
     */
    String getConceptName();

    /**
     * The name of the authority, which must be capable of resolving any parents as well. The first
     * term will be used to name the ontology.
     *
     * @return
     */
    String getAuthorityName();

    /**
     * If not null, this will be the label for the concept that provides a parent for the identity.
     * The authority must return an identity for it. It will be declared as the base identity. If
     * null, a base identity will be created from the ontology ID and shared by all identities in
     * the authority.
     *
     * @return
     */
    String getBaseIdentity();

    /**
     * If the concept is expected to have a broader term from the same vocabulary, return its ID
     * here. This will be resolved recursively and used to build the superclass, unless the
     * authority capabilities require a different type.
     */
    List<String> getParentIds();

    /**
     * This may be given to define which property should constrain the parents (in order). If empty
     * and parents are given, they will be superclasses.
     *
     * @return
     */
    List<String> getParentRelationship();

    /**
     * Description in text or markdown.
     *
     * @return
     */
    String getDescription();

    /**
     * Label to use to build the local concept label and display.
     *
     * @return
     */
    String getLabel();

    /**
     * This should be 1 if returned by getIdentity(), or 0-1 if returned through a query.
     *
     * @return
     */
    float getScore();

    /**
     * The original declaration, including the authority namespace, to set into the declaration
     * metadata for the concept.
     *
     * @return
     */
    String getLocator();

    /**
     * Any notifications from the authority. If any of these has level = error, no concepts must be
     * created.
     *
     * @return
     */
    List<Notification> getNotifications();
  }

  interface Capabilities {

    /**
     * @return
     */
    String getDescription();

    /**
     * If true, users can use the search API.
     *
     * @return
     */
    boolean isSearchable();

    /**
     * If true, the authority is capable of accepting unambiguous but different identifiers for the
     * same concept, such as water and h2o, which are resolved through a search. If false, the
     * authority can only deal with correct identifiers or formulas. The main consequence is that if
     * this is true, each search can have multiple results, otherwise it's either 0 or 1.
     *
     * @return
     */
    boolean isFuzzy();

    /**
     * If the authority admits sub-authorities (e.g. GBIF/SPECIES), these should be listed along
     * with their description. For now the rest of the capabilities must apply unaltered to each. If
     * the authority also admits use without subauthorities, the first element should contain an
     * empty string for the authority ID.
     */
    List<PairImpl<String, String>> getSubAuthorities();

    /**
     * Return the media type names for any documentation that this authority is capable of
     * generating given a valid identifier. If not empty, the client will set up the interface for
     * documenting stated or retrieved identities and send requests accordingly.
     *
     * @return
     */
    List<String> getDocumentationFormats();

    /**
     * If not null, the authority won't be loaded unless the certificate commits the engine or node
     * to the returned worldview.
     *
     * @return
     */
    String getWorldview();
  }

  /**
   * Unique URN of this authority. The urn is resolved like that of any component, agent or other
   * plug-in asset; it must be referenced when asking for a configuration. The name used as
   * namespace for the authority is chosen in the worldview where the configuration is registered.
   *
   * @return
   */
  String getURN();

  /**
   * Create the concept corresponding to the identity. It must be an identity semantically, and may
   * or may not have structure to locate it in a hierarchy if appropriate. The client may pass a
   * path to the catalog if the authority has multiple layers.
   *
   * @param identityId
   * @param catalog may be null
   * @return
   */
  Identity getIdentity(String identityId, String catalog);

  /**
   * Get the authority service's capabilities.
   *
   * @return
   */
  Capabilities getCapabilities();

  /**
   * If the authority is based on a codelist, return it here.
   *
   * @return
   */
  Codelist getCodelist();

  /**
   * When two identities from the same authority are compared for semantic distance, the reasoner
   * delegates the distance calculation to the authority. This is also used as a subsumption test
   * (with a distance >= 0 criterion).
   *
   * @param a
   * @param b
   * @return same contract as {@link
   *     org.integratedmodelling.klab.api.services.Reasoner#semanticDistance(Semantics, Semantics)}.
   */
  int getSemanticDistance(Identity a, Identity b);

  /**
   * Write the documentation for the passed identity in the passed media type, which will be one of
   * those returned in the capabilities.
   *
   * <p>FIXME move to a DomainObject with a documentation schema
   *
   * @param identityId
   * @param mediaType
   * @param destination
   */
  void document(String identityId, String mediaType, OutputStream destination);

  /**
   * Called only if {@link Capabilities#isSearchable()} returns true. Implementations must set the
   * {@link Metadata#IM_KEY} to the unique identity ID that will produce the concept when called in
   * {@link #getIdentity(String, String)}. Remaining fields should be set to support the user in
   * choosing an identity.
   *
   * @param query
   * @param catalog may be null
   * @return
   */
  List<Identity> search(String query, String catalog);

  /**
   * May be called explicitly through the API for authorities hosted by nodes that need setup or
   * reset actions.
   *
   * @param options a map of options to specify actions.
   * @return the configured authority if setup was successful. Notifications must be checked in the
   *     returned configuration; if any error notification appears, the request has failed..
   */
  Configuration setup(Parameters<String> options);
}
