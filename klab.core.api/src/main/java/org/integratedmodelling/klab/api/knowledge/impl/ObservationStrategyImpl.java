package org.integratedmodelling.klab.api.knowledge.impl;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ObservationStrategyImpl implements ObservationStrategy {

    private Observable originalObservable;
    private List<Pair<Operation, Arguments>> body = new ArrayList<>();

    private int cost;

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
    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }


    @Override
    public String toString() {
        return toString(this, 0);
    }

    private String toString(ObservationStrategy strategy, int spaces) {
        StringBuffer ret = new StringBuffer(512);
        String spacer = Utils.Strings.spaces(spaces);
        for (var op : strategy) {
            ret.append(spacer + op.getFirst().name() + " ");
            ret.append(switch(op.getFirst()) {
                case RESOLVE -> op.getSecond().observable() + "\n";
                case DEFER -> op.getSecond().contextualStrategy().getOriginalObservable() + "\n" + toString(op.getSecond().contextualStrategy(), spaces + 3);
                case APPLY -> op.getSecond().serviceCall() + "\n";
                case CONCRETIZE -> op.getSecond().observable() + "\n";
            });
        }
        return ret.toString();
    }

    @Override
    public String getUrn() {
        return null;
    }

    @Override
    public Version getVersion() {
        return null;
    }

    @Override
    public Metadata getMetadata() {
        return null;
    }

    @Override
    public List<Annotation> getAnnotations() {
        return null;
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

        public Builder withCost(int cost) {
            this.rank = cost;
            return this;
        }

        @Override
        public ObservationStrategy.Builder withOperation(Operation operation, Observable target) {
            this.operations.add(Pair.of(operation, new Arguments(target, null, null)));
            return this;
        }

        @Override
        public ObservationStrategy.Builder withOperation(Operation operation, ServiceCall target) {
            this.operations.add(Pair.of(operation, new Arguments(null, target, null)));
            return this;
        }

        @Override
        public ObservationStrategy.Builder withStrategy(Operation operation, ObservationStrategy strategy) {
            this.operations.add(Pair.of(operation, new Arguments(null, null, strategy)));
            return this;
        }

        @Override
        public ObservationStrategy build() {
            ObservationStrategyImpl ret = new ObservationStrategyImpl();
            ret.setOriginalObservable(this.originalObservable);
            ret.setCost(this.rank);
            ret.body.addAll(operations);
            return ret;
        }
    }

}
