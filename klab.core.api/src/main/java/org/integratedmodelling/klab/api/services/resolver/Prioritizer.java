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

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * The object that creates a ranking of whatever object is being used to represent a model according
 * to the implementation. Extracts the criteria for ranking from the object and aggregates them into
 * an overall ranking used for comparison.
 *
 * <p>The default ranking strategy is specified in the resolver configuration. Individual namespaces
 * may redefine it if needed. The ranking strategy is upstream of the {@link
 * org.integratedmodelling.klab.api.knowledge.ObservationStrategy} and is used to find a model for
 * an observable when an observation strategy specifies <code>observe</code>.
 *
 * <p>This class isn't directly used in any API methods so far, but it's in the public API for
 * completeness and to provide a vocabulary for the criteria that allow the ranking of alternative
 * observation strategies (i.e. models).
 *
 * <p>Data structure returned from query with ranks computed at server side based on context;
 * sorting happens at request side after merge.
 *
 * <dl>
 *   <dt>lexical scope
 *   <dd>locality wrt context 100 = in observation scenario 50 = in same namespace as context 0 =
 *       non-private in other namespace
 *   <dt>semantic distance
 *   <dd>as defined by reasoner for the focal observable. Includes generic and abstract components
 *       as well as # of traits shared and their distance. Capped and thresholded to "normalize" to
 *       100
 *   <dt>community
 *   <dd>(unimplemented) depends on which communities are followed and are linked to the compared
 *       object
 *   <dt>scale coherency
 *   <dd>coherency of scale in context (minimum of all extents? or one per extent?) 0 = not
 *       scale-specific (outside scale will not be returned) (1, 100] = (scale ^ object
 *       context)/scale
 *   <dt>scale specificity
 *   <dd>total coverage of object wrt context (minimum of all extents?) = scale / (object coverage)
 *       * 100
 *   <dt>inherency (deprecated, should be part of semantic distance)
 *   <dd>level wrt observable:
 *       <dl>
 *         <dt>100
 *         <dd>same thing-ness, specific inherency
 *         <dt>66
 *         <dd>same thing-ness, non-specific inherency
 *         <dt>33
 *         <dd>different thing-ness, mediatable inherency
 *         <dt>0
 *         <dd>secondary observable obtained by running a process model
 *       </dl>
 *   <dt>evidence
 *   <dd>resolved/unresolved 100 = resolved from datasource 50 = computed, no dependencies 0 =
 *       unresolved network
 *   <dt>remoteness
 *   <dd>whether coming from remote KBox (added by kbox implementation) 100 -> local 0
 *   <dt>remote scale coherency
 *   <dd>coherency of domains adopted by context vs. the object n = # of domains shared (based on
 *       the isSpatial/isTemporal fields) normalize to 100
 *   <dt>subjective concordance
 *   <dd>multi-criteria ranking of user-defined metadata wrt default or namespace priorities n =
 *       chosen concordance metric normalized to 100
 * </dl>
 *
 * Clarifications for the inherency criterion:
 *
 * <ul>
 *   <li>same thing-ness, specific: (type) OR (type according to trait) // Second one is a further
 *       spec for the classification observation type, different inherent-ness + observation type +
 *       inherent type
 *   <li>only do this with SUBJECT inherency, i.e. dependency has no inherency stated same
 *       thing-ness, non specific: (type) OR (type according to trait) + observation type + (NO
 *       inherent type)
 *   <li>dereifying: direct observation of <inherent type> where an attribute provides <code>ob type
 *       </code> of <code>type</code>
 * </ul>
 *
 * @param <T> the type of model bean that is compared
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Prioritizer<T> extends Comparator<T> {

  // Standard criteria for ranking. More could be added in extensions (not so far).
  enum Criterion {
    LEXICAL_SCOPE("im:lexical-scope"),
    TRAIT_CONCORDANCE("im:trait-concordance"),
    SEMANTIC_DISTANCE("im:semantic-concordance"),
    INHERENCY("im:inherency"),
    EVIDENCE("im:evidence"),
    NETWORK_REMOTENESS("im:network-remoteness"),
    SUBJECTIVE_CONCORDANCE("im:subjective-concordance"),
    SCALE_COVERAGE("im:scale-coverage"),
    SCALE_SPECIFICITY("im:scale-specificity"),
    SCALE_COHERENCY("im:scale-coherency"),
    SPACE_COVERAGE("im:space-coverage"),
    SPACE_SPECIFICITY("im:space-specificity"),
    SPACE_COHERENCY("im:space-coherency"),
    TIME_COVERAGE("im:time-coverage"),
    TIME_SPECIFICITY("im:time-specificity"),
    TIME_COHERENCY("im:time-coherency"),
    RELIABILITY("im:reliability");

    public final String property;

    Criterion(String property) {
      this.property = property;
    }

    public static Criterion forProperty(String criterion) {

      return switch (criterion) {
        case "im:lexical-scope" -> LEXICAL_SCOPE;
        case "im:trait-concordance" -> TRAIT_CONCORDANCE;
        case "im:semantic-concordance" -> SEMANTIC_DISTANCE;
        case "im:inherency" -> INHERENCY;
        case "im:evidence" -> EVIDENCE;
        case "im:network-remoteness" -> NETWORK_REMOTENESS;
        case "im:subjective-concordance" -> SUBJECTIVE_CONCORDANCE;
        case "im:scale-coverage" -> SCALE_COVERAGE;
        case "im:scale-specificity" -> SCALE_SPECIFICITY;
        case "im:scale-coherency" -> SCALE_COHERENCY;
        case "im:space-coverage" -> SPACE_COVERAGE;
        case "im:space-specificity" -> SPACE_SPECIFICITY;
        case "im:space-coherency" -> SPACE_COHERENCY;
        case "im:time-coverage" -> TIME_COVERAGE;
        case "im:time-specificity" -> TIME_SPECIFICITY;
        case "im:time-coherency" -> TIME_COHERENCY;
        case "im:reliability" -> RELIABILITY;
        default -> throw new KlabIllegalArgumentException("Unknown criterion: " + criterion);
      };
    }
  }

  /**
   * Rank all data and return a map of the criteria computed. The context of comparison should be
   * set in the constructor according to the resolver's needs.
   *
   * @param model
   * @return the criteria values for model in context
   */
  Map<Criterion, Double> computeCriteria(T model);

  /**
   * List the keys of each criterion in the chosen ranking strategy, in order of importance.
   *
   * @return criteria
   */
  List<String> listCriteria();

  /**
   * Retrieve the computed ranking for the passed object, or null.
   *
   * @param ranked
   * @return
   */
  Map<Criterion, Double> getRanking(T ranked);
}
