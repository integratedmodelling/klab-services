package org.integratedmodelling.klab.api.lang;

/**
 * Canonical source-generation boundary, reusable for dataflow and subsequently k.IM documents.
 * Implementations must validate the entire supported semantic model before returning source and
 * reject unsupported required fields or node kinds rather than omit them. The returned string is
 * source, not a Resource registration or an assurance that dependencies are available.
 *
 * <p>No dataflow implementation is registered yet. Source round-trip tests against the deployed
 * grammar are required before exposing an implementation as an export capability. Comment/trivia
 * preservation is a separate concern from this semantic, canonical serializer.
 */
@FunctionalInterface
public interface DocumentSerializer<T> {
  /**
   * @throws IllegalArgumentException if the document is invalid or cannot be represented losslessly
   */
  String serialize(T document);
}
