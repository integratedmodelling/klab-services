package org.integratedmodelling.klab.api.knowledge.impl;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.lang.ServiceCall;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ObservationStrategyImpl implements ObservationStrategy {

    private Observable originalObservable;
    private List<Pair<Operation, Arguments>> body = new ArrayList<>();

    private int rank;

    public List<Pair<Operation, Arguments>> getBody() {
        return body;
    }

    public void setBody(List<Pair<Operation, Arguments>> body) {
        this.body = body;
    }

    @Override
    public Observable getOriginalObservable() {
        return originalObservable;
    }

    public void setOriginalObservable(Observable originalObservable) {
        this.originalObservable = originalObservable;
    }

    @Override
    public Iterator<Pair<Operation, Arguments>> iterator() {
        return this.body.iterator();
    }

    @Override
    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public static class Builder implements ObservationStrategy.Builder {

        private int rank;

        public Observable getOriginalObservable() {
            return originalObservable;
        }

        public void setOriginalObservable(Observable originalObservable) {
            this.originalObservable = originalObservable;
        }

        Observable originalObservable;
        private List<Pair<Operation, Arguments>> operations = new ArrayList<>();

        public Builder() {
        }

        public Builder withRank(int rank) {
            this.rank = rank;
            return this;
        }

        @Override
        public ObservationStrategy.Builder withOperation(Operation operation, Observable target) {
            this.operations.add(Pair.of(operation, new Arguments(target, null)));
            return this;
        }

        @Override
        public ObservationStrategy.Builder withOperation(Operation operation, ServiceCall target) {
            this.operations.add(Pair.of(operation, new Arguments(null, target)));
            return this;
        }

        @Override
        public ObservationStrategy build() {
            ObservationStrategyImpl ret = new ObservationStrategyImpl();
            ret.setOriginalObservable(this.originalObservable);
            ret.setRank(this.rank);
            ret.body.addAll(operations);
            return ret;
        }
    }

}
