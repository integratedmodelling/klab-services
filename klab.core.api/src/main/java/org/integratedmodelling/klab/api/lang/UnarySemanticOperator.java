package org.integratedmodelling.klab.api.lang;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.SemanticType;

/**
 * All the semantic operators available in k.IM.
 * 
 * @author Ferd
 *
 */
public enum UnarySemanticOperator {

    NOT(new SemanticType[]{SemanticType.DENIABLE}, SemanticType.TRAIT,
            "not"), PRESENCE(new SemanticType[]{SemanticType.COUNTABLE}, SemanticType.QUALITY, "presence of"),
    // FIXME does not account for the different operands
    PROPORTION(new SemanticType[]{SemanticType.TRAIT, SemanticType.QUANTIFIABLE}, SemanticType.QUALITY, "proportion of",
            "in"), PERCENTAGE(new SemanticType[]{SemanticType.TRAIT, SemanticType.QUANTIFIABLE}, SemanticType.QUALITY,
                    "percentage of",
                    "in"), RATIO(new SemanticType[]{SemanticType.QUANTIFIABLE}, SemanticType.QUALITY, "ratio of", "to"),

    // also must be geolocated
    DISTANCE(new SemanticType[]{SemanticType.COUNTABLE}, SemanticType.QUALITY, "distance to"), PROBABILITY(
            new SemanticType[]{SemanticType.EVENT}, SemanticType.QUALITY,
            "probability of"), UNCERTAINTY(new SemanticType[]{SemanticType.QUALITY}, SemanticType.QUALITY,
                    "uncertainty of"), COUNT(new SemanticType[]{SemanticType.COUNTABLE}, SemanticType.QUALITY, "count of"), VALUE(
                            new SemanticType[]{SemanticType.OBSERVABLE, SemanticType.CONFIGURATION}, SemanticType.QUALITY,
                            "value of", "over"), MONETARY_VALUE(
                                    new SemanticType[]{SemanticType.OBSERVABLE, SemanticType.CONFIGURATION}, SemanticType.QUALITY,
                                    "monetary value of"), OCCURRENCE(new SemanticType[]{SemanticType.COUNTABLE},
                                            SemanticType.QUALITY, "occurrence of"), CHANGE(
                                                    new SemanticType[]{SemanticType.QUALITY}, SemanticType.PROCESS,
                                                    "change in"), CHANGED(new SemanticType[]{SemanticType.QUALITY},
                                                            SemanticType.EVENT,
                                                            "changed"), RATE(new SemanticType[]{SemanticType.QUALITY},
                                                                    SemanticType.QUALITY, "change rate of"), MAGNITUDE(
                                                                            new SemanticType[]{SemanticType.QUANTIFIABLE},
                                                                            SemanticType.QUALITY, "magnitude of"), LEVEL(
                                                                                    new SemanticType[]{SemanticType.QUANTIFIABLE},
                                                                                    SemanticType.CLASS, "level of"), TYPE(
                                                                                            new SemanticType[]{
                                                                                                    SemanticType.TRAIT},
                                                                                            SemanticType.QUALITY, "type of");

    public String[] declaration;
    public Set<SemanticType> allowedOperandTypes = EnumSet.noneOf(SemanticType.class);
    public SemanticType returnType;

    UnarySemanticOperator(SemanticType[] allowedOpTypes, SemanticType returnType, String... decl) {
        this.declaration = decl;
        this.returnType = returnType;
        for (SemanticType type : allowedOpTypes) {
            allowedOperandTypes.add(type);
        }
    }

    public static UnarySemanticOperator forCode(String code) {
        for (UnarySemanticOperator val : values()) {
            if (code.equals(val.declaration[0])) {
                return val;
            }
        }
        return null;
    }

    public Set<SemanticType> getAllowedOperandTypes() {
        return allowedOperandTypes;
    }

    public String getReferenceName(String conceptName, String other) {
        String ret = declaration[0].replaceAll(" ", "_") + "_" + conceptName;
        if (other != null) {
            ret += "_" + declaration[1] + "_" + other;
        }
        return ret;
    }

    public String getCodeName(String conceptName, String other) {
        String ret = declaration[0].replaceAll(" ", "-") + "-" + conceptName;
        if (other != null) {
            ret += "-" + declaration[1] + "-" + other;
        }
        return ret;
    }
    
    public Set<SemanticType> getApplicableType(Set<SemanticType> original) {

        switch (this) {
        case COUNT:
            return getType("count", original);
        case DISTANCE:
            return getType("distance", original);
        case MAGNITUDE:
            return getType("magnitude", original);
        case LEVEL:
            return getType("level", original);
        case CHANGE:
            return getType("change", original);
        case MONETARY_VALUE:
            return getType("money", original);
        case OCCURRENCE:
            return getType("occurrence", original);
        case PERCENTAGE:
            return getType("percentage", original);
        case PRESENCE:
            return getType("presence", original);
        case PROBABILITY:
            return getType("probability", original);
        case PROPORTION:
            return getType("proportion", original);
        case RATIO:
            return getType("ratio", original);
        case TYPE:
            return getType("class", original);
        case UNCERTAINTY:
            return getType("uncertainty", original);
        case VALUE:
            return getType("value", original);
        case RATE:
            return getType("rate", original);
        case CHANGED:
            return getType("changed", original);
        case NOT:
        default:
            break;
        }

        return null;
    }

    /**
     * Return the semantic typeset of a concept after applying the operator we represent.
     * 
     * @param operator
     * @param original
     * @return
     */
    public Set<SemanticType> apply(Collection<SemanticType> original) {

        switch(this) {
        case COUNT:
            return getType("count", original);
        case DISTANCE:
            return getType("distance", original);
        case MAGNITUDE:
            return getType("magnitude", original);
        case LEVEL:
            return getType("level", original);
        case CHANGE:
            return getType("change", original);
        case MONETARY_VALUE:
            return getType("money", original);
        case OCCURRENCE:
            return getType("occurrence", original);
        case PERCENTAGE:
            return getType("percentage", original);
        case PRESENCE:
            return getType("presence", original);
        case PROBABILITY:
            return getType("probability", original);
        case PROPORTION:
            return getType("proportion", original);
        case RATIO:
            return getType("ratio", original);
        case TYPE:
            return getType("class", original);
        case UNCERTAINTY:
            return getType("uncertainty", original);
        case VALUE:
            return getType("value", original);
        case RATE:
            return getType("rate", original);
        case CHANGED:
            return getType("changed", original);
        case NOT:
        default:
            break;
        }

        return null;
    }

    public static Set<SemanticType> getType(String string, Collection<SemanticType> original) {

        if (string == null) {
            return EnumSet.noneOf(SemanticType.class);
        }

        String id = string.toLowerCase();

        switch(id) {
        case "thing":
            return EnumSet.of(SemanticType.SUBJECT, SemanticType.DIRECT_OBSERVABLE, SemanticType.COUNTABLE,
                    SemanticType.OBSERVABLE);
        case "class":
            return EnumSet.of(SemanticType.CLASS, SemanticType.QUALITY, SemanticType.OBSERVABLE);
        case "level":
            return EnumSet.of(SemanticType.CLASS, SemanticType.QUALITY, SemanticType.ORDERING, SemanticType.OBSERVABLE);
        case "quantity":
            return EnumSet.of(SemanticType.QUANTITY, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "quality":
            return EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE);
        case "ordering":
            return EnumSet.of(SemanticType.ORDERING, SemanticType.TRAIT, SemanticType.ATTRIBUTE, SemanticType.PREDICATE);
        case "attribute":
            return EnumSet.of(SemanticType.ATTRIBUTE, SemanticType.TRAIT, SemanticType.PREDICATE);
        case "type":
            return EnumSet.of(SemanticType.CLASS, SemanticType.QUALITY, SemanticType.OBSERVABLE);
        case "identity":
            return EnumSet.of(SemanticType.IDENTITY, SemanticType.TRAIT, SemanticType.PREDICATE);
        case "role":
            return EnumSet.of(SemanticType.ROLE, SemanticType.PREDICATE);
        case "realm":
            return EnumSet.of(SemanticType.REALM, SemanticType.TRAIT, SemanticType.PREDICATE);
        case "domain":
            return EnumSet.of(SemanticType.DOMAIN, SemanticType.PREDICATE);
        case "energy":
            return EnumSet.of(SemanticType.ENERGY, SemanticType.QUALITY, SemanticType.EXTENSIVE_PROPERTY, SemanticType.OBSERVABLE,
                    SemanticType.QUANTIFIABLE);
        case "entropy":
            return EnumSet.of(SemanticType.ENTROPY, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY,
                    SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "length":
            return EnumSet.of(SemanticType.LENGTH, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY, SemanticType.OBSERVABLE,
                    SemanticType.QUANTIFIABLE);
        case "mass":
            return EnumSet.of(SemanticType.MASS, SemanticType.QUALITY, SemanticType.EXTENSIVE_PROPERTY, SemanticType.OBSERVABLE,
                    SemanticType.QUANTIFIABLE);
        case "volume":
            return EnumSet.of(SemanticType.VOLUME, SemanticType.QUALITY, SemanticType.EXTENSIVE_PROPERTY, SemanticType.OBSERVABLE,
                    SemanticType.QUANTIFIABLE);
        case "weight":
            return EnumSet.of(SemanticType.WEIGHT, SemanticType.QUALITY, SemanticType.EXTENSIVE_PROPERTY, SemanticType.OBSERVABLE,
                    SemanticType.QUANTIFIABLE);
        case "magnitude":
            return EnumSet.of(SemanticType.MAGNITUDE, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.SUBJECTIVE,
                    SemanticType.QUANTIFIABLE);
        case "monetary_value":
            return EnumSet.of(SemanticType.MONETARY_VALUE, SemanticType.QUALITY, SemanticType.OBSERVABLE,
                    SemanticType.QUANTIFIABLE);
        case "money":
            return EnumSet.of(SemanticType.MONEY, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "duration":
            return EnumSet.of(SemanticType.DURATION, SemanticType.QUALITY, SemanticType.EXTENSIVE_PROPERTY,
                    SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "area":
            return EnumSet.of(SemanticType.AREA, SemanticType.QUALITY, SemanticType.EXTENSIVE_PROPERTY, SemanticType.OBSERVABLE,
                    SemanticType.QUANTIFIABLE);
        case "presence":
            return EnumSet.of(SemanticType.PRESENCE, SemanticType.QUALITY, SemanticType.OBSERVABLE);
        case "proportion":
            return EnumSet.of(SemanticType.PROPORTION, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "percentage":
            return EnumSet.of(SemanticType.PERCENTAGE, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "uncertainty":
            return EnumSet.of(SemanticType.UNCERTAINTY, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "rate":
            EnumSet<SemanticType> ret = EnumSet.of(SemanticType.RATE, SemanticType.QUALITY, SemanticType.OBSERVABLE,
                    SemanticType.QUANTIFIABLE);
            if (original.contains(SemanticType.EXTENSIVE_PROPERTY) || original.contains(SemanticType.INTENSIVE_PROPERTY)) {
                ret.add(SemanticType.INTENSIVE_PROPERTY);
            }
            return ret;
        case "acceleration":
            return EnumSet.of(SemanticType.ACCELERATION, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY,
                    SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "priority":
            return EnumSet.of(SemanticType.PRIORITY, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "value":
            return EnumSet.of(SemanticType.VALUE, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "ratio":
            return EnumSet.of(SemanticType.RATIO, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "count":
            return EnumSet.of(SemanticType.NUMEROSITY, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "electric-potential":
            return EnumSet.of(SemanticType.ELECTRIC_POTENTIAL, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY,
                    SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "charge":
            return EnumSet.of(SemanticType.CHARGE, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY, SemanticType.OBSERVABLE,
                    SemanticType.QUANTIFIABLE);
        case "resistance":
            return EnumSet.of(SemanticType.RESISTANCE, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY,
                    SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "amount":
            return EnumSet.of(SemanticType.AMOUNT, SemanticType.QUALITY, SemanticType.EXTENSIVE_PROPERTY, SemanticType.OBSERVABLE,
                    SemanticType.QUANTIFIABLE);
        case "resistivity":
            return EnumSet.of(SemanticType.RESISTIVITY, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY,
                    SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "occurrence":
            return EnumSet.of(SemanticType.OCCURRENCE, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "probability":
            return EnumSet.of(SemanticType.PROBABILITY, SemanticType.QUALITY, SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "pressure":
            return EnumSet.of(SemanticType.PRESSURE, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY,
                    SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "angle":
            return EnumSet.of(SemanticType.ANGLE, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY, SemanticType.OBSERVABLE,
                    SemanticType.QUANTIFIABLE);
        case "velocity":
            return EnumSet.of(SemanticType.VELOCITY, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY,
                    SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "temperature":
            return EnumSet.of(SemanticType.TEMPERATURE, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY,
                    SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "viscosity":
            return EnumSet.of(SemanticType.VISCOSITY, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY,
                    SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "distance":
            return EnumSet.of(SemanticType.DISTANCE, SemanticType.QUALITY, SemanticType.INTENSIVE_PROPERTY,
                    SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE);
        case "process":
            return EnumSet.of(SemanticType.PROCESS, SemanticType.DIRECT_OBSERVABLE, SemanticType.OBSERVABLE);
        case "change":
            return EnumSet.of(SemanticType.CHANGE, SemanticType.PROCESS, SemanticType.DIRECT_OBSERVABLE, SemanticType.OBSERVABLE);
        case "changed":
            return EnumSet.of(SemanticType.CHANGED, SemanticType.EVENT, SemanticType.COUNTABLE, SemanticType.DIRECT_OBSERVABLE,
                    SemanticType.OBSERVABLE);
        case "agent":
            return EnumSet.of(SemanticType.AGENT, SemanticType.DIRECT_OBSERVABLE, SemanticType.COUNTABLE,
                    SemanticType.OBSERVABLE);
        case "event":
            return EnumSet.of(SemanticType.EVENT, SemanticType.DIRECT_OBSERVABLE, SemanticType.COUNTABLE,
                    SemanticType.OBSERVABLE);
        case "relationship":
            return EnumSet.of(SemanticType.RELATIONSHIP, SemanticType.UNIDIRECTIONAL, SemanticType.DIRECT_OBSERVABLE,
                    SemanticType.COUNTABLE, SemanticType.OBSERVABLE);
        case "bond":
            return EnumSet.of(SemanticType.RELATIONSHIP, SemanticType.BIDIRECTIONAL, SemanticType.DIRECT_OBSERVABLE,
                    SemanticType.COUNTABLE, SemanticType.OBSERVABLE);
        case "configuration":
            return EnumSet.of(SemanticType.CONFIGURATION);
        case "extent":
            return EnumSet.of(SemanticType.EXTENT);
      
        }

        throw new UnsupportedOperationException("internal error: type " + string + " not handled");
    }
}