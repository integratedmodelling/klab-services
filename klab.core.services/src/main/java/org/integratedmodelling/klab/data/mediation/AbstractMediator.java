package org.integratedmodelling.klab.data.mediation;

import java.util.List;

import javax.measure.UnitConverter;

import org.integratedmodelling.klab.api.data.mediation.ValueMediator;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;

/**
 * Host the scale-driven recontextualization mechanism for other mediators.
 * 
 * @author Ferd
 *
 */
public abstract class AbstractMediator {

    public enum ExtentSize {

        SPACE_M("a in m"), SPACE_M2("area in m^2"), SPACE_M3("volume in m^3"), TIME_MS("time span in milliseconds");

        String description;

        ExtentSize(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

    }

    public enum Operation {
        MULTIPLY, DIVIDE
    }

    public static class Mediation {

        public UnitConverter converter;
        public ExtentSize extentSize;
        public Operation operation;
        public Double factor;
        public String description;

        @Override
        public String toString() {
            return description;
        }
    }

    /*
     * null operations == standard behavior; otherwise, the mediators contain the converter and will
     * take over
     */
    List<Mediation> mediators = null;
    /*
     * the value we were contextualized from. We only need it for reporting or debugging.
     */
    ValueMediator toConvert;

    public void setMediation(ValueMediator toConvert, List<Mediation> mediators) {
        this.toConvert = toConvert;
        this.mediators = mediators;
    }

//    @Override MOVE TO UnitService
    public Number convert(Number value, Locator locator) {

        double val = value.doubleValue();
        for (Mediation mediator : mediators) {
            if (mediator.extentSize != null) {
                switch(mediator.extentSize) {
                case SPACE_M:
                    val = mediator.operation == Operation.MULTIPLY
                            ? val * (getSpace(locator).getStandardizedLength() * (mediator.factor == null ? 1.0 : mediator.factor))
                            : val / (getSpace(locator).getStandardizedLength() * (mediator.factor == null ? 1.0 : mediator.factor));
                    break;
                case SPACE_M2:
                    val = mediator.operation == Operation.MULTIPLY
                            ? val * (getSpace(locator).getStandardizedArea() * (mediator.factor == null ? 1.0 : mediator.factor))
                            : val / (getSpace(locator).getStandardizedArea() * (mediator.factor == null ? 1.0 : mediator.factor));
                    break;
                case SPACE_M3:
                    val = mediator.operation == Operation.MULTIPLY
                            ? val * (getSpace(locator).getStandardizedVolume() * (mediator.factor == null ? 1.0 : mediator.factor))
                            : val / (getSpace(locator).getStandardizedVolume() * (mediator.factor == null ? 1.0 : mediator.factor));
                    break;
                case TIME_MS:
                    val = mediator.operation == Operation.MULTIPLY
                            ? val * (((Time) getTime(locator)).getDimensionSize() * (mediator.factor == null ? 1.0 : mediator.factor))
                            : val / (((Time) getTime(locator)).getDimensionSize() * (mediator.factor == null ? 1.0 : mediator.factor));
                    break;
                }
            }
            if (mediator.converter != null) {
                val = mediator.converter.convert(val);
            }
        }

        return val;
    }

    private Space getSpace(Locator locator) {

        Space ret = null;
        if (locator instanceof Scale) {
            ret = ((Scale) locator).getSpace();
        } else if (locator instanceof Space) {
            ret = (Space) locator;
        }

        if (ret == null) {
            throw new KlabInternalErrorException("cannot find space locator when mediating over space");
        }

        return ret;
    }

    private Time getTime(Locator locator) {

        Time ret = null;
        if (locator instanceof Scale) {
            ret = ((Scale) locator).getTime();
        } else if (locator instanceof Time) {
            ret = (Time) locator;
        }

        if (ret == null) {
            throw new KlabInternalErrorException("cannot find time locator when mediating over space");
        }

        return ret;
    }

}
