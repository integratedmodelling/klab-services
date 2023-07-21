/*
 * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root
 * directory of the k.LAB distribution (LICENSE.txt). If this cannot be found 
 * see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned
 * in author tags. All rights reserved.
 */
package org.integratedmodelling.klab.api.services.resolver;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * The object that creates a ranking of whatever object is being used to
 * represent a model according to the implementation. Extracts the criteria for
 * ranking from the object and aggregates them into an overall ranking used for
 * comparison.
 * <p>
 * This class isn't directly used in any API methods so far, but it's in the
 * public API for completeness and to provide a vocabulary for the criteria that
 * allow the ranking of alternative observation strategies (i.e. models).
 * <p>
 * Data structure returned from query with ranks computed at server side based
 * on context; sorting happens at request side after merge.
 * <dl>
 * <dt>lexical scope</dt>
 * <dd>locality wrt context 100 = in observation scenario 50 = in same namespace
 * as context 0 = non-private in other namespace</dd>
 * <dt>trait concordance</dt>
 * <dd>in context n = # of traits shared vs. n. of traits possible, normalized
 * to 100</dd>
 * <dt>scale coverage</dt>
 * <dd>of scale in context (minimum of all extents? or one per extent?) 0 = not
 * scale-specific (outside scale will not be returned) (1, 100] = (scale ^
 * object context)/scale</dd>
 * <dt>scale specificity</dt>
 * <dd>total coverage of object wrt context (minimum of all extents?) = scale /
 * (object coverage) * 100</dd>
 * <dt>inherency</dt>
 * <dd>level wrt observable: 100 = same thing-ness, specific inherency 66 = same
 * thing-ness, non-specific inherency 33 = different thing-ness, mediatable
 * inherency 0 = secondary observable obtained by running a process model</dd>
 * <dt>evidence</dt>
 * <dd>resolved/unresolved 100 = resolved from datasource 50 = computed, no
 * dependencies 0 = unresolved network</dd>
 * <dt>remoteness</dt>
 * <dd>whether coming from remote KBox (added by kbox implementation) 100 ->
 * local 0</dd>
 * <dt>remote scale coherency</dt>
 * <dd>coherency of domains adopted by context vs. the object n = # of domains
 * shared (based on the isSpatial/isTemporal fields) normalize to 100</dd>
 * <dt>subjective concordance</dt>
 * <dd>multi-criteria ranking of user-defined metadata wrt default or namespace
 * priorities n = chosen concordance metric normalized to 100</dd>
 * </dl>
 * Clarifications for the inherency criterion:
 * <p>
 * same thing-ness, specific: (type) OR (type according to trait) // Second one
 * is a further spec for the classification observation type, different
 * inherent-ness + observation type + inherent type
 * <p>
 * only do this with SUBJECT inherency, i.e. dependency has no inherency
 * stated same thing-ness, non specific: (type) OR (type according to trait) +
 * observation type + (NO inherent type)
 * <p>
 * dereifying: direct observation of <inherent type> where an attribute provides
 * <ob type> of <type>
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 * @param <T> the type of model bean that is compared. Usually not the actual
 *            model as beans from the network need to be compared and building
 *            an actual model that won't be used may be expensive.
 */
public interface Prioritizer<T> extends Comparator<T> {

	/**
	 * The default ranking strategy in the form that can be given in klab.properties
	 * for overriding.
	 */
	public static final String DEFAULT_RANKING_STRATEGY = "im:lexical-scope 1 " + "im:evidence 4 "
			+ "im:semantic-concordance 2 " + "im:trait-concordance 3 " + "im:time-specificity 5 "
			+ "im:time-coverage 6 " + "im:space-specificity 7 " + "im:space-coverage 8 "
//          + "im:scale-specificity 5 "
//            + "im:scale-coverage 6 "
			+ "im:subjective-concordance 9 " + "im:inherency 10 " + "im:scale-coherency 0 " + "im:network-remoteness 0 "
			+ "im:reliability 100";

	/** The Constant DEFAULT_STRATEGY_PROPERTY_NAME. */
	public static final String DEFAULT_STRATEGY_PROPERTY_NAME = "klab.ranking.strategy";

	/**
	 * These are the fields that will be directly available in the object returned,
	 * to allow further ranking of subjective information and retrieval of the
	 * object if selected.
	 */
	public static final String METADATA = "metadata";

	/** The Constant OBJECT_NAME. */
	public static final String OBJECT_NAME = "name";

	/** The Constant PROJECT_NAME. */
	public static final String PROJECT_NAME = "project";

	/** The Constant NAMESPACE_ID. */
	public static final String NAMESPACE_ID = "namespace";

	/** The Constant SERVER_ID. */
	public static final String SERVER_ID = "server";

	/**
	 * Rank all data and return a map of the criteria computed.
	 *
	 * @param model   a T object.
	 * @param context a
	 *                {@link org.integratedmodelling.klab.api.resolution.IResolutionScope}
	 *                object.
	 * @return the criteria values for model in context
	 */
	Map<String, Double> computeCriteria(T model, ContextScope context);

	/**
	 * Get the computed ranks for the passed object, or null if they were not
	 * computed.
	 *
	 * @param md a T object.
	 * @return ranks from object, if any
	 */
	Map<String, Double> getRanks(T md);

	/**
	 * List the keys of each criterion in the chosen ranking strategy, in order of
	 * importance.
	 *
	 * @return criteria
	 */
	List<String> listCriteria();

}
