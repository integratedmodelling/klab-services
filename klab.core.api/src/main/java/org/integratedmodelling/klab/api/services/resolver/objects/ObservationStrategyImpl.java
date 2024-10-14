package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ObservationStrategyImpl implements ObservationStrategy {

    private String urn;
    private String namespace;
    private Metadata metadata;
    private int rank;
    private List<Operation> operations = new ArrayList<>();
    private String documentation;

    public static class OperationImpl implements Operation {

        private KimObservationStrategy.Operation.Type type;
        private Observable observable;
        private List<Contextualizable> contextualizables = new ArrayList<>();
        private List<List<Operation>> contextualStrategies = new ArrayList<>();

        @Override
        public KimObservationStrategy.Operation.Type getType() {
            return this.type;
        }

        @Override
        public Observable getObservable() {
            return this.observable;
        }

        @Override
        public List<Contextualizable> getContextualizables() {
            return this.contextualizables;
        }

        public void setContextualizables(List<Contextualizable> contextualizable) {
            this.contextualizables = contextualizable;
        }

        public void setObservable(Observable observable) {
            this.observable = observable;
        }

        public void setType(KimObservationStrategy.Operation.Type type) {
            this.type = type;
        }

        @Override
        public List<List<Operation>> getContextualStrategies() {
            return contextualStrategies;
        }

        public void setContextualStrategies(List<List<Operation>> contextualStrategies) {
            this.contextualStrategies = contextualStrategies;
        }

        @Override
        public String toString() {
            return  type +
                    (observable != null ? (" " + observable) : "") +
                    (contextualizables.isEmpty() ? "" : (" " + contextualizables)) +
                    (contextualStrategies.isEmpty() ? "" : (" *->" + contextualStrategies.size()));
        }
    }

    @Override
    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public String getUrn() {
        return urn;
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public List<Operation> getOperations() {
        return operations;
    }

    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }

    @Override
    public String getDocumentation() {
        return documentation;
    }

    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    @Override
    public String toString() {
        return "(S) " +
                "urn='" + urn + '\'' +
                ", operations=" + operations;
    }
}
