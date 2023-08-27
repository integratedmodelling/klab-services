package org.integratedmodelling.klab.api.knowledge.impl;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ObservationStrategyImpl implements ObservationStrategy {

    private Observable observable;
    private List<Pair<Operation, Arguments>> body = new ArrayList<>();

    @Override
    public Observable getObservable() {
        return observable;
    }

    public List<Pair<Operation, Arguments>> getBody() {
        return body;
    }

    public void setBody(List<Pair<Operation, Arguments>> body) {
        this.body = body;
    }

    public void setObservable(Observable observable) {
        this.observable = observable;
    }

    @Override
    public Iterator<Pair<Operation, Arguments>> iterator() {
        return this.body.iterator();
    }

    public static class Builder implements ObservationStrategy.Builder {

        private Observable observable;

        public Builder(Observable observable) {
            this.observable = observable;
        }

        @Override
        public ObservationStrategy build() {
            ObservationStrategyImpl ret = new ObservationStrategyImpl();

            ret.observable = this.observable;

            // TODO

            // no operations == directly resolve the original observable
            if (ret.body.isEmpty()) {
                ret.body.add(Pair.of(Operation.RESOLVE, new Arguments(observable, null)));
            }

            return ret;
        }
    }

}
