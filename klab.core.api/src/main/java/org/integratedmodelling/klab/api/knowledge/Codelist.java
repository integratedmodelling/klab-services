package org.integratedmodelling.klab.api.knowledge;

import java.util.Collection;

/**
 * A portable mapping of codes to k.LAB values. Keys may be strings or numbers; values may
 * be concepts or other supported transport objects. Multiple keys may denote the same value,
 * with one preferred key for reverse lookup. Code-only lists may omit values.
 *
 * <p>Authority-owned lists use string concept identifiers as keys and canonical provider codes
 * as values. Their worldview-local namespace is configured separately. These are aliases for
 * existing authority identities, not additional ontology declarations. Proposal state is carried
 * separately; published lists contain only approved mappings.
 *
 * @author Ferd
 */
public interface Codelist extends KlabAsset {

  /**
   * Name of codelist. Usually a string with agency/name/version.
   *
   * @return
   */
  String getName();

  /**
   * @return
   */
  String getDescription();

  /**
   * If not null, specifies the authority this either incarnates (if {@link #isAuthority()}) or maps
   * to. The authority may be an ontology if this derives from annotations or metadata on concepts.
   * If this is not null, {@link #getType()} must return CONCEPT.
   *
   * @return
   */
  String getAuthorityId();

  /**
   * All code authorities represented by this codelist. Most codelists contain one authority; a
   * codelist derived from concept annotations may contain several because a concept can carry more
   * than one {@code @code} annotation.
   */
  default Collection<String> getAuthorityIds() {
    return getAuthorityId() == null ? java.util.List.of() : java.util.List.of(getAuthorityId());
  }

  /**
   * Total number of codes.
   *
   * @return
   */
  int size();

  /**
   * If true, the codelist is exposed as an authority, referenceable through the URN of the resource
   * containing it. There may still be an authority that this maps to without being an authority
   * itself.
   *
   * @return
   */
  boolean isAuthority();

  /**
   * Stable worldview identifier when this list depends on a worldview, otherwise null.
   *
   * @return
   */
  String getWorldview();

  /**
   * The type corresponding to the mapped value. Must agree with T.
   *
   * @return
   */
  Artifact.Type getType();

  /**
   * If a root concepts has been declared for the codelist, return it. This is only used when the
   * codelist is exposed as an authority, an inverse equivalent of the 'defines authority' link in
   * the semantics. If the returned string is a fully specified concept URN, the concept will be
   * assumed existing and validated as an identity. If just a valid concept name is returned (with
   * no ontology prefix), it will be created in the authority's ontology instead.
   *
   * @return
   */
  String getRootConceptId();

  /**
   * A regex or other interpretive key needed to turn the codes into the artifact type, without
   * remapping each single one of them. Those which do have a mapping will use the mapping instead.
   *
   * @return
   */
  String getPattern();

  /**
   * Value corresponding to a key. Multiple values aren't possible but different keys may point to
   * the same value.
   *
   * @param key
   * @return
   */
  Object value(Object key);

  /** Resolve a key within one named authority. */
  default Object value(String authorityId, Object key) {
    return java.util.Objects.equals(authorityId, getAuthorityId()) ? value(key) : null;
  }

  /** All codes represented by this codelist, in declaration order. */
  Collection<Object> codes();

  /** All codes represented by one named authority, in declaration order. */
  default Collection<Object> codes(String authorityId) {
    return java.util.Objects.equals(authorityId, getAuthorityId())
        ? codes()
        : java.util.List.of();
  }

  /**
   * All keys correspondent to a value.
   *
   * @param value
   * @return
   */
  Collection<Object> keys(Object value);

  /**
   * The preferential key correspondent to a value.
   *
   * @param value
   * @return
   */
  Object key(Object value);

  /**
   * If codes have a description, return it.
   *
   * @param code
   * @return
   */
  String getDescription(Object code);

  /** Return the description for a code within one named authority. */
  default String getDescription(String authorityId, Object code) {
    return java.util.Objects.equals(authorityId, getAuthorityId()) ? getDescription(code) : null;
  }
}
