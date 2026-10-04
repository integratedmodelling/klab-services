package org.integratedmodelling.klab.api.services;

import java.net.URL;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public interface Authority {

  /** Context for one independent worldview bridge. Parameters include the provider urn. */
  record ConfigurationRequest(
      String worldview, String name, String rootIdentity, Map<String, Object> parameters) {
    public ConfigurationRequest {
      if (worldview == null || worldview.isBlank())
        throw new IllegalArgumentException("A worldview is required");
      if (name == null || !name.matches("[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*)*"))
        throw new IllegalArgumentException("An uppercase local authority name is required");
      if (rootIdentity == null || rootIdentity.isBlank())
        throw new IllegalArgumentException("A root identity concept URN is required");
      if (parameters == null || !(parameters.get("urn") instanceof String urn) || urn.isBlank())
        throw new IllegalArgumentException("A nonblank string urn parameter is required");
      parameters = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(parameters));
    }
  }

  /**
   * Create an independent bridge and return its opaque provider-held configuration ID. The provider
   * must retain configuration state separately for each bridge, and throw a validation exception on
   * failure. The root identity is supplied by the worldview, not by the provider.
   */
  String configure(ConfigurationRequest request);

  /** Release provider-held state when a bridge is removed or its worldview is reloaded. */
  default void releaseConfiguration(String configurationId) {}

  /**
   * Lifetimes in seconds for successful Reasoner-side cached results. Zero disables caching for an
   * operation; Long.MAX_VALUE means immutable data without expiry. Providers with mutable
   * vocabularies should choose conservative lifetimes. Diagnostics and failures are never cached.
   * Revision must change whenever the provider changes the meaning/format of its cached results.
   */
  record CachePolicy(
      String revision, long identitySeconds, long searchSeconds, long reconciliationSeconds) {
    public CachePolicy {
      if (revision == null
          || revision.isBlank()
          || identitySeconds < 0
          || searchSeconds < 0
          || reconciliationSeconds < 0)
        throw new IllegalArgumentException("Invalid authority cache policy");
    }
  }

  /** Default retention: one day for identities, five minutes for query results. */
  default CachePolicy getCachePolicy() {
    return new CachePolicy("1", 86400, 300, 300);
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
     * Optional ID of the vocabulary's base identity. The Reasoner recursively resolves this and the
     * parent IDs until known concepts are reached. It may be the configured worldview root URN,
     * which is already known. Provider top-level identities inherit from that root; other
     * identities inherit through the supplied hierarchy. The authority owns hierarchy validity.
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
     * Documentation resources keyed by media type. Providers should supply at least {@code
     * text/markdown}, and may include images, PDF or other media. UIs must tolerate its absence.
     * Each URL must retrieve a resource in the associated media type.
     */
    default Map<String, URL> getDocumentation() {
      return Map.of();
    }

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
     * This should be 1 for a resolved identity, or 0-1 for a search candidate. Search scores
     * describe provider-specific relevance, not necessarily a probability of correctness.
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
     * this is true, resolution may accept aliases. Search may return multiple candidates regardless
     * of this flag; candidates must be selected explicitly when ambiguous.
     *
     * @return
     */
    boolean isFuzzy();

    /** Whether explicit, provider-defined name reconciliation is available. */
    default boolean isReconciliationSupported() {
      return false;
    }

    /**
     * If true, declared sub-authorities only filter searches: NAME.RANK:id resolves through NAME's
     * bridge, with identical codes, parents and canonical concepts. Otherwise a dotted name must
     * have its own configured binding; the Reasoner must not guess its semantics.
     */
    default boolean areSubAuthoritiesSearchFilters() {
      return false;
    }

    /**
     * If the authority admits sub-authorities (e.g. GBIF/SPECIES), these should be listed along
     * with their description. For now the rest of the capabilities must apply unaltered to each. If
     * the authority also admits use without subauthorities, the first element should contain an
     * empty string for the authority ID.
     */
    List<Pair<String, String>> getSubAuthorities();

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
   * Unique URN of this authority. Worldviews bind this to a local name when binding the root
   * concept with a <code>requires authority</code> clause.
   *
   * @return
   */
  String getUrn();

  /**
   * Create the concept corresponding to the identity. It must be an identity semantically, and may
   * or may not have structure to locate it in a hierarchy if appropriate. The client may pass a
   * path to the catalog if the authority has multiple layers.
   *
   * @param identityId
   * @return
   */
  Identity resolveIdentity(String configurationId, String identityId);

  /**
   * Explicitly reconcile a name or external identifier with optional disambiguating fields. Field
   * names and accepted match policies belong to the provider. Return a canonical identity only when
   * the match is unambiguous; report failed/ambiguous matches with error notifications. This
   * operation must not silently replace code lookup or select the first search candidate.
   */
  default Identity reconcile(String configurationId, Map<String, String> fields) {
    throw new UnsupportedOperationException("This authority does not support reconciliation");
  }

  /**
   * Get the authority service's capabilities.
   *
   * @return
   */
  Capabilities getCapabilities();

  /**
   * Non-empty iif the authority provides codelists. Codelists can be bound to worldview-local
   * namespaces and be used as a vocabulary for identities that bridges transparently to the
   * authority.
   *
   * <p>Codes can be proposed by users by inserting proposal annotations in namespace code, to help
   * construct shared, recognizable terminology without compromising on authority-specific
   * semantics.
   *
   * @return
   */
  Map<String, Codelist> getCodelists();

  /**
   * If the authority has lower-level subcatalogs, return the singleton that will handle the catalog
   * below us. Asking for a path that's not declared in the capabilities is an internal error and
   * should never happen. The subauthority must be completely initialized and get stored for any
   * successive request.
   *
   * @return
   */
  Authority subAuthority(String catalog);

  /**
   * Can be called only if {@link Capabilities#isSearchable()} returns true. Each candidate's {@link
   * Identity#getId()} must resolve through {@link #resolveIdentity(String, String)} in the same
   * configuration. Remaining fields support the user in choosing an identity. An empty list means
   * no matches, not a transport failure; failures must be reported explicitly.
   *
   * @param query
   * @param subAuthority may be null
   * @param configurationId the active provider-held bridge ID
   * @return
   */
  List<Identity> search(String query, String subAuthority, String configurationId);
}
