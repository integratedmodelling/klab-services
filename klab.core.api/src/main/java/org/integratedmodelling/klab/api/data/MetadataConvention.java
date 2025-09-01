package org.integratedmodelling.klab.api.data;

import java.io.Serializable;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;

/**
 * A metadata convention is a schema for the metadata, with validators and other constraints.
 * Conventions can be applied to any {@link KlabAsset}'s metadata for validation.
 *
 * <p>Metadata conventions should be defined in resources, one per resource with a set asset
 * name/URL, and identified through the resource URN. Details still TBD.
 *
 * <p>TODO needs fleshing out and implementation.
 *
 * @author Ferd
 */
public interface MetadataConvention extends Serializable {}
