package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;

import java.util.List;

public interface Model extends Knowledge, Resolvable {

    /**
     * Builders are used by the resolver when creating on-the-fly models when the observation strategy
     * involves creating ad-hoc models that are not coming from k.IM statements. These models should live in
     * an internal namespace of the implementation choice unless {@link #inNamespace(String)} is called on the
     * builder. This builder should be usable in any other legitimate situation.
     */
    interface Builder {

        /**
         * Call this to specify semantics for a semantic resource annotation.
         *
         * @param observable the observable to use as semantics
         * @return this builder instance for method chaining
         */
        Builder as(Observable observable);

        /**
         * Call this to build a non-semantic learner
         *
         * @param name the name for the non-semantic learner
         * @param nonSemanticType the type of artifact to be produced
         * @return this builder instance for method chaining
         */
        Builder as(String name, Artifact.Type nonSemanticType);

        Builder observing(Observable dependency);

        Builder observedAs(Identity identity);

        Builder inNamespace(String namespace);

        Builder using(ServiceCall... calls);

        Builder over(ServiceCall... calls);

        Builder withOutput(Observable observable);

        Model build();
    }

    /**
     * Models are in namespaces, which are relevant to organization and scoping.
     *
     * @return the namespace that contains this model
     */
    String getNamespace();

    /**
     * Project name is needed by the resolver for prioritization
     *
     * @return the name of the project this model belongs to
     */
    String getProjectName();

    /**
     * One of CONCEPT, TEXT, NUMBER, BOOLEAN or VOID if inactive because of error or offline resources.
     *
     * @return the artifact type of this model
     */
    Artifact.Type getType();

    /**
     * Models can be annotated for runtime options.
     *
     * @return a list of annotations associated with this model
     */
    List<Annotation> getAnnotations();

    /**
     * The kind of description this model represents. Instantiators return
     * {@link DescriptionType#INSTANTIATION}.
     *
     * @return the description type of this model
     */
    DescriptionType getDescriptionType();

    /**
     * All the observables contextualized by the model, including the "root" one that defines the model
     * semantics.
     *
     * @return a list of all observables contextualized by this model
     */
    List<Observable> getObservables();

    /**
     * All the observables needed by the model before contextualization. If the list has size 0, the model is
     * resolved, i.e. it can be computed without referencing any other observation.
     *
     * @return a list of observable dependencies required by this model
     */
    List<Observable> getDependencies();

    /**
     * Models may have a coverage, either explicitly set in the model definition or in the namespace, or
     * inherited by their resources. Models with universal coverage should return an empty scale. FIXME there
     * should be a "universal" scale that isn't empty.
     *
     * @return the geometry representing the coverage of this model
     */
    Geometry getCoverage();

    /**
     * If the model (or the containing namespace) is stated as <code>observed by</code> some type, return it
     * so that the model can be matched to the observer.
     *
     * @return the concept representing the observer type for this model, or null if not specified
     */
    Concept getObserverType();

    /**
     * The sequence of contextualizables (resources, function calls, expressions etc.) that composes the
     * computable part of the model.
     *
     * @return a list of contextualizables that make up the computation for this model
     */
    List<Contextualizable> getComputation();
//
//    List<Action> getActions();

    /**
     * Create a builder for a model that will observe the passed observable.
     *
     * @param observable
     * @return
     */
    static Builder builder(Observable observable) {
        Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
        if (configuration == null) {
            throw new KlabIllegalStateException("k.LAB environment not configured to promote a concept to " +
                    "observable");
        }
        return configuration.getModelBuilder(observable);
    }

    /**
     * Create a builder for a non-semantic model.
     *
     * @param nonSemanticType
     * @return
     */
    static Builder builder(Artifact.Type nonSemanticType) {
        Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
        if (configuration == null) {
            throw new KlabIllegalStateException("k.LAB environment not configured to promote a concept to " +
                    "observable");
        }
        return configuration.getModelBuilder(nonSemanticType);
    }

    /**
     * Create a builder for a model that annotates the passed resource. Call {@link Builder#as(Observable)}
     * immediately after.
     *
     * @param resource
     * @return
     */
    static Builder builder(Resource resource) {
        Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
        if (configuration == null) {
            throw new KlabIllegalStateException("k.LAB environment not configured to promote a concept to " +
                    "observable");
        }
        return configuration.getModelBuilder(resource);
    }

    /**
     * Create a builder for a model that annotates the passed literal value. Call
     * {@link Builder#as(Observable)} immediately after.
     *
     * @param value
     * @return
     */
    static Builder builder(Object value) {
        Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
        if (configuration == null) {
            throw new KlabIllegalStateException("k.LAB environment not configured to promote a concept to " +
                    "observable");
        }
        return configuration.getModelBuilder(value);
    }

    /**
     * Create a builder for a model that learns a resource with the passed URN. Call
     * {@link Builder#as(Observable)} immediately after.
     *
     * @param outputResourceUrn
     * @return
     */
    static Builder learner(String outputResourceUrn) {
        Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
        if (configuration == null) {
            throw new KlabIllegalStateException("k.LAB environment not configured to promote a concept to " +
                    "observable");
        }
        return configuration.getModelLearner(outputResourceUrn);
    }

}
