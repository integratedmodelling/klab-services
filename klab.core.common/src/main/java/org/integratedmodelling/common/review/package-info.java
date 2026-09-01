/**
 * API beans for versioned, feedback-driven k.LAB asset proposals.
 *
 * <p>{@link ProposalDocument} is the serialization root for
 * documents with media type {@code application/vnd.klab.proposal+yaml}. Consumers should validate
 * input against the schema named by {@code ProposalDocument.SCHEMA_RESOURCE} before mapping it.
 * Schema validation establishes required fields and cross-record structure; these mutable beans do
 * not duplicate that validation policy.
 *
 * <p>The asset discriminator maps to typed concept, ontology, namespace, and model subclasses.
 * Objects that the schema deliberately leaves extensible remain ordered {@code Map<String,
 * Object>} values so future asset-specific proposal data and apply payloads survive a round trip.
 */
package org.integratedmodelling.common.review;
