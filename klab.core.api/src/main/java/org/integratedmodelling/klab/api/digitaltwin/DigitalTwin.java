package org.integratedmodelling.klab.api.digitaltwin;

import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

import java.util.Map;
import java.util.Set;

/**
 * The digital twin is a graph model composed of observations and all their history. Each
 * {@link org.integratedmodelling.klab.api.scope.ContextScope} points to a digital twin and contains the
 * methods to access it. Digital twins can be built from pairing others in a federated fashion.
 */
public interface DigitalTwin {

    /**
     * The type of relationships in the graph. All relationship carry further information
     */
    enum Relationship {
        Parent,
        Affects,
        Connects,
        EmergesFrom,
        Observer,
        RootObservation
    }

    /**
     * Return the storage for all "datacube" content.
     *
     * @return
     */
    StateStorage stateStorage();

    /**
     * Return a view of the graph that links observations using the passed relationships (all existing if none
     * is specified). Some relationships may be computed on demand, and their presence in the graph when no
     * relationship type is passed is not guaranteed.
     *
     * @return
     */
    ObservationGraph observationGraph(Relationship... relationships);

    /**
     * Return a view of the graph that only addresses the dataflow.
     *
     * @return
     */
    DataflowGraph dataflowGraph();

    /**
     * Return the view of the graph that represents provenance.
     *
     * @return
     */
    ProvenanceGraph provenanceGraph();

    /**
     * Dispose of all storage and data, either in memory only or also on any attached storage. Whether the
     * disposal is permanent depends on the graph database used and its configuration.
     */
    void dispose();

    /**
     * Ingest an observation created externally. Return the unique ID of the observation in the DT.
     * <p>
     * Submitting a resolved observation that does not belong or unresolved related will throw a
     * {@link org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException}.
     *
     * @param observation  cannot be null, must be unresolved if the relationship is parent or null
     * @param related      may be null but if not, must be already submitted to the DT
     * @param relationship the relationship of the new observation to the second, must be non-null if related
     *                     isn't
     */
    long submit(Observation observation, Observation related, Relationship relationship,
                Metadata relationshipMetadata);

    /**
     * @param resolved
     * @param dataflow
     * @param provenance
     */
    void finalizeObservation(Observation resolved, Dataflow<Observation> dataflow, Provenance provenance);

    /**
     * Assemble the passed parameters into an unresolved Observation, to be passed to
     * {@link #submit(Observation, Observation, Relationship, Metadata)}  for resolution and insertion in the
     * graph.
     * <p>
     * Accepts:
     *      TODO
     *
     * @param resolvables
     * @return
     */
    static Observation createObservation(Scope scope, Object... resolvables) {

        final Set<String> knownKeys = Set.of("observation", "semantics", "space", "time");

        String name = null;
        Geometry geometry = Geometry.EMPTY;
        Observable observable = null;
        String resourceUrn = null;
        String modelUrn = null;
        Geometry observerGeometry = null;
        String defaultValue = null;
        Metadata metadata = Metadata.create();

        if (resolvables != null) {
            for (Object o : resolvables) {
                if (o instanceof Observable obs) {
                    observable = obs;
                } else if (o instanceof Geometry geom) {
                    if (geometry == null) {
                        geometry = geom;
                    } else {
                        observerGeometry = geom;
                    }
                } else if (o instanceof String string) {
                    if (name == null) {
                        name = string;
                    } else {
                        defaultValue = string;
                    }
                } else if (o instanceof Urn urn) {
                    resourceUrn = urn.getUrn();
                } else if (o instanceof KimSymbolDefinition symbol) {

                    // must be an "observation" class
                    if ("observation".equals(symbol.getDefineClass()) && symbol.getValue() instanceof Map<?
                            , ?> definition) {
                        //                        semantics: earth:Region
                        //                        space: {
                        //                            shape: "EPSG:4326 POLYGON((33.796 -7.086, 35.946 -7
                        //                            .086, 35.946 -9.41, 33.796 -9.41, 33.796 -7.086))"
                        //                            grid: 1.km
                        //                        }
                        //                        //	year: 2010 ...or...
                        //                        time: {
                        //                            year: 2010
                        //                            step: 1.day
                        //                        }
                        name = symbol.getName();
                        if (definition.containsKey("semantics")) {
                            observable = scope.getService(Reasoner.class).resolveObservable(definition.get(
                                    "semantics").toString());
                        }
                        if (definition.containsKey("space") || definition.containsKey("time")) {
                            var geometryBuilder = Geometry.builder();
                            if (definition.containsKey("space")) {
                                var spaceBuilder = geometryBuilder.space();
                                if (definition.get("space") instanceof Map<?, ?> spaceDefinition) {
                                    if (spaceDefinition.containsKey("shape")) {
                                        spaceBuilder.shape(spaceDefinition.get("shape").toString());
                                    }
                                    if (spaceDefinition.containsKey("grid")) {
                                        spaceBuilder.resolution(spaceDefinition.get("grid").toString());
                                    }
                                    // TODO add bounding box etc
                                }
                                geometryBuilder = spaceBuilder.build();
                            }
                            if (definition.containsKey("time")) {
                                var timeBuilder = geometryBuilder.time();
                                if (definition.get("time") instanceof Map<?, ?> timeDefinition) {
                                    if (timeDefinition.containsKey("year")) {
                                        var year = timeDefinition.get("year");
                                        if (year instanceof Number number) {
                                            timeBuilder.year(number.intValue());
                                        } else if (year instanceof Identifier identifier && "default".equals(identifier.getValue())) {
                                            timeBuilder.year(TimeInstant.create().getYear());
                                        }
                                    }
                                }
                                geometryBuilder = timeBuilder.build();
                            }
                            geometry = geometryBuilder.build();
                        }

                        for (var key : definition.keySet()) {
                            if (!knownKeys.contains(key.toString())) {
                                metadata.put(key.toString(), definition.get(key));
                            }
                        }
                    }
                } else if (o instanceof KimModel model) {
                    // send the model URN and extract the observable
                    observable =
                            scope.getService(Reasoner.class).declareObservable(model.getObservables().get(0));
                    modelUrn = model.getUrn();
                } else if (o instanceof Map<?, ?> map) {
                    // metadata
                    metadata.putAll((Map<? extends String, ?>) map);
                }
            }
        }

        /*
        least requisite is having an observable
         */
        if (observable != null) {
            ObservationImpl ret = new ObservationImpl();
            ret.setGeometry(geometry);
            ret.setMetadata(metadata);
            ret.setObservable(observable);
            ret.setObserverGeometry(observerGeometry);
            ret.setUrn(resourceUrn == null ? modelUrn : resourceUrn);
            ret.setValue(defaultValue);
            ret.setName(name);
            return ret;
        }

        return Observation.empty();
    }


}
