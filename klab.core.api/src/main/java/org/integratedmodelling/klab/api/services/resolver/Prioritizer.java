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

import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;

/**
 * Request-scoped lexicographic model ranking, used by Resolver after resource discovery.
 * The scope's ResolutionNamespace supplies overrides to service defaults for every candidate,
 * regardless of the candidate's namespace. One effective criterion order applies to the session. Positive priorities enable
 * criteria (smaller priorities first); nonpositive priorities disable them. Equal priorities use
 * property-name order, not weighting. Semantic distance is ascending; benefit scores descend.
 * Unavailable benefit scores are -1. Criteria not implemented by the ranking session are exposed
 * through {@link #unsupportedCriteria()}. Scores are diagnostics, not resolution coverage or
 * guarantees of successful execution. Ranking sessions are local, not service transport beans.
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

  /** Active criteria lacking an implementation; their unavailable values do not discriminate. */
  java.util.Set<Criterion> unsupportedCriteria();

  /**
   * Return the immutable ranking for the object, computing it if necessary.
   *
   * @param ranked
   * @return
   */
  Map<Criterion, Double> getRanking(T ranked);
}
