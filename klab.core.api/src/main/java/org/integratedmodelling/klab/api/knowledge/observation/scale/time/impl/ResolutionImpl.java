package org.integratedmodelling.klab.api.knowledge.observation.scale.time.impl;

import java.util.Objects;

import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;

public class ResolutionImpl implements Time.Resolution {

    private Type type;
    private double multiplier;

    public ResolutionImpl(Type type, double multiplier) {
        this.type = type;
        this.multiplier = multiplier;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public double getMultiplier() {
        return multiplier;
    }

    @Override
    public long getSpan() {
        return (long) (type.getMilliseconds() * multiplier);
    }

    public Time.Resolution copy() {
        return new ResolutionImpl(type, multiplier);
    }

    @Override
    public double getMultiplier(TimeInstant start, TimeInstant end) {
        if (start == null || end == null) {
            return multiplier;
        }
        double span = end.getMilliseconds() - start.getMilliseconds();
        return span / type.getMilliseconds();
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public String toString() {
        return multiplier + " " + type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(multiplier, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ResolutionImpl other = (ResolutionImpl) obj;
        return Double.doubleToLongBits(multiplier) == Double.doubleToLongBits(other.multiplier) && type == other.type;
    }

}
