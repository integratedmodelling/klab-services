/*
 * This file is part of k.LAB.
 * 
 * k.LAB is free software: you can redistribute it and/or modify it under the terms of the Affero
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * A copy of the GNU Affero General Public License is distributed in the root directory of the k.LAB
 * distribution (LICENSE.txt). If this cannot be found see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (C) 2007-2018 integratedmodelling.org and any authors mentioned in author tags. All
 * rights reserved.
 */
package org.integratedmodelling.klab.api.services.resolver;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import static java.util.Map.entry;

import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * The object that creates a ranking of whatever object is being used to represent a model according
 * to the implementation. Extracts the criteria for ranking from the object and aggregates them into
 * an overall ranking used for comparison.
 * <p>
 * This class isn't directly used in any API methods so far, but it's in the public API for
 * completeness and to provide a vocabulary for the criteria that allow the ranking of alternative
 * observation strategies (i.e. models).
 * <p>
 * Data structure returned from query with ranks computed at server side based on context; sorting
 * happens at request side after merge.
 * <dl>
 * <dt>lexical scope</dt>
 * <dd>locality wrt context 100 = in observation scenario 50 = in same namespace as context 0 =
 * non-private in other namespace</dd>
 * <dt>trait concordance</dt>
 * <dd>in context n = # of traits shared vs. n. of traits possible, normalized to 100</dd>
 * <dt>scale coverage</dt>
 * <dd>of scale in context (minimum of all extents? or one per extent?) 0 = not scale-specific
 * (outside scale will not be returned) (1, 100] = (scale ^ object context)/scale</dd>
 * <dt>scale specificity</dt>
 * <dd>total coverage of object wrt context (minimum of all extents?) = scale / (object coverage) *
 * 100</dd>
 * <dt>inherency</dt>
 * <dd>level wrt observable:
 * <dl>
 * <dt>100</dt>
 * <dd>same thing-ness, specific inherency</dd>
 * <dt>66</dt>
 * <dd>same thing-ness, non-specific inherency</dd>
 * <dt>33</dt>
 * <dd>different thing-ness, mediatable inherency</dd>
 * <dt>0</dt>
 * <dd>secondary observable obtained by running a process model</dd>
 * </dl>
 * </dd>
 * <dt>evidence</dt>
 * <dd>resolved/unresolved 100 = resolved from datasource 50 = computed, no dependencies 0 =
 * unresolved network</dd>
 * <dt>remoteness</dt>
 * <dd>whether coming from remote KBox (added by kbox implementation) 100 -> local 0</dd>
 * <dt>remote scale coherency</dt>
 * <dd>coherency of domains adopted by context vs. the object n = # of domains shared (based on the
 * isSpatial/isTemporal fields) normalize to 100</dd>
 * <dt>subjective concordance</dt>
 * <dd>multi-criteria ranking of user-defined metadata wrt default or namespace priorities n =
 * chosen concordance metric normalized to 100</dd>
 * </dl>
 * Clarifications for the inherency criterion:
 * <ul>
 * <li>same thing-ness, specific: (type) OR (type according to trait) // Second one is a further
 * spec for the classification observation type, different inherent-ness + observation type +
 * inherent type</li>
 * <li>only do this with SUBJECT inherency, i.e. dependency has no inherency stated same thing-ness,
 * non specific: (type) OR (type according to trait) + observation type + (NO inherent type)</li>
 * <li>dereifying: direct observation of <inherent type> where an attribute provides
 * <code>ob type</code> of <code>type</code></li>
 * </ul>
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 * @param <T> the type of model bean that is compared
 */
public interface Prioritizer<T> extends Comparator<T> {

    /**
     * Default prioritization strategy
     */
    public static final Map<String, Integer> defaultRankingStrategy = Map.ofEntries(entry("im:lexical-scope", 1),
            entry("im:evidence", 4), entry("im:semantic-concordance", 2), entry("im:trait-concordance", 3),
            entry("im:time-specificity", 5), entry("im:time-coverage", 6), entry("im:space-specificity", 7),
            entry("im:space-coverage", 8), entry("im:subjective-concordance", 9), entry("im:inherency", 10),
            entry("im:scale-coherency", 11), entry("im:network-remoteness", 0), entry("im:reliability", 100));

    /**
     * Rank all data and return a map of the criteria computed.
     *
     * @param model
     * @param context object
     * @return the criteria values for model in context
     */
    Map<String, Double> computeCriteria(T model, ContextScope context);

    /**
     * Get the computed ranks for the passed object, or null if they were not computed.
     *
     * @param md a T object.
     * @return ranks from object, if any
     */
    Map<String, Double> getRanks(T md);

    /**
     * List the keys of each criterion in the chosen ranking strategy, in order of importance.
     *
     * @return criteria
     */
    List<String> listCriteria();

}
