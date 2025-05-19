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
package org.integratedmodelling.klab.api.data.mediation.classification;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution;
import org.integratedmodelling.klab.api.lang.Encodeable;
import org.integratedmodelling.klab.api.scope.ContextScope;

/**
 * The Interface IClassifier.
 *
 * @author ferdinando.villa
 * @version $Id: $Id
 */
public interface Classifier extends Encodeable {

    /**
     * True if passed object matches the conditions of the classifier.
     *
     * @param o     the o
     * @param scope the context
     * @return True if passed object matches the conditions of the classifier
     */
    boolean classify(Object o, ContextScope scope);

    /**
     * True if this classifier matches everything.
     *
     * @return True if this classifier matches everything
     */
    boolean isUniversal();

    /**
     * True if this classifier only matches null (unknown).
     *
     * @return True if this classifier only matches null
     */
    boolean isNil();

    /**
     * True if this is an interval classifier.
     *
     * @return True if this is an interval classifier
     */
    boolean isInterval();

    /**
     * True if the classifier must be computed in scope.
     *
     * @return true if computed
     */
    boolean isComputed();

    /**
     * @return
     */
    boolean isStringMatch();

    /**
     * Source code for k.IM and KDL serialization
     *
     * @return parseable source code
     */
    String getSourceCode();

    /**
     * Classifiers may be used as a value; this one should return the most appropriate value translation of
     * the classifier, i.e. the matched object if it's matching a single one, or possibly a random object
     * among the choices if it's in OR.
     *
     * @param scope pass a context for complex evaluations, like expressions
     * @return the value this classifier resolves to.
     */
    Object asValue(ContextScope scope);

    /**
     * Return the type of the classifier when used as a value.
     *
     * @return
     */
    Artifact.Type getType();

    /**
     * True if this classifies a concept
     *
     * @return
     */
    boolean isConcept();

    /**
     * True if this classifies presence/absence
     *
     * @return
     */
    boolean isBoolean();

    /**
     * If isConcept(), getConcept.
     *
     * @return
     */
    Concept getConcept();

    /**
     * This should not return null. Metadata aren't associated to classifiers through k.IM but may be added by
     * code for specific purposes, such as visualization for tables and the like.
     *
     * @return
     */
    Metadata getMetadata();

    /**
     * If isConcept(), return the way the concept is resolved. If Resolution.Only the concept is matched
     * exactly, if Any the concept is matched using the reasoner for subsumption.
     *
     * @return
     */
    Resolution getConceptResolution();

}