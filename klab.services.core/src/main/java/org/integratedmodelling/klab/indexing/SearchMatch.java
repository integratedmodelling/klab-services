package org.integratedmodelling.klab.indexing;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.BinarySemanticOperator;
import org.integratedmodelling.klab.api.lang.SemanticLexicalElement;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;

public class SearchMatch /* implements IIndexingService.Match */ {

    public static enum TokenClass {
        TOKEN, TEXT, INTEGER, DOUBLE, BOOLEAN, UNIT, CURRENCY
    };

    public static enum States {
        FORTHCOMING, EXPERIMENTAL, NEW, STABLE, BETA,
    }

    public enum Type {
        CONCEPT,
        @Deprecated // use UNARY_OPERATOR
        PREFIX_OPERATOR,
        @Deprecated // use SEMANTIC_MODIFIER
        INFIX_OPERATOR,
        OBSERVATION,
        MODEL,
        /**
         * Stuff like "in" for units
         */
        MODIFIER,
        PRESET_OBSERVABLE,
        RESOURCE,
        SEPARATOR,
        OPEN_PARENTHESIS,
        CLOSED_PARENTHESIS,
        VALUE_OPERATOR,
        UNARY_OPERATOR,
        SEMANTIC_MODIFIER,
        BINARY_OPERATOR,
        INLINE_VALUE
    }

    
    String id;
    String name;
    String description = "";
    float score;
    Type matchType;
    Map<String, String> indexableFields = new HashMap<>();
    Set<SemanticType> conceptType = EnumSet.noneOf(SemanticType.class);
    Set<SemanticType> semantics = EnumSet.noneOf(SemanticType.class);
    UnarySemanticOperator unaryOperator = null;
    BinarySemanticOperator binaryOperator = null;
    ValueOperator valueOperator = null;
    SemanticLexicalElement modifier = null;

    boolean isAbstract = false;

//    public org.integratedmodelling.klab.rest.SearchMatch getReference() {
//        org.integratedmodelling.klab.rest.SearchMatch ret = new org.integratedmodelling.klab.rest.SearchMatch();
//        ret.setId(this.id);
//        ret.setDescription(this.description);
//        ret.getSemanticType().addAll(this.semantics);
//        ret.setMatchType(this.matchType);
//        return ret;
//    }
    
    public SearchMatch() {
    }

//    public SearchMatch(org.integratedmodelling.klab.rest.SearchMatch descriptor) {
//        this.id = descriptor.getId();
//        this.description = descriptor.getDescription();
//        this.score = 1;
//        this.semantics.addAll(descriptor.getSemanticType());
//        this.matchType = descriptor.getMatchType();
//    }

    public SearchMatch(Type matchType, Set<SemanticType> conceptType) {
        this.matchType = matchType;
        this.conceptType.addAll(conceptType);
    }

    public SearchMatch(UnarySemanticOperator op) {
        this.unaryOperator = op;
        this.matchType = Type.UNARY_OPERATOR;
        this.id = this.name = op.declaration[0];
    }

    public SearchMatch(ValueOperator op) {
        this.valueOperator = op;
        this.matchType = Type.VALUE_OPERATOR;
        this.id = this.name = op.declaration;
    }

    public SearchMatch(BinarySemanticOperator op) {
        this.binaryOperator = op;
        this.matchType = Type.BINARY_OPERATOR;
        this.id = this.name = op.name().toLowerCase();
    }

    public SearchMatch(SemanticLexicalElement op) {
        this.modifier = op;
        this.matchType = Type.MODIFIER;
        this.id = this.name = modifier.declaration[0];
    }

    public SearchMatch(SemanticRole role) {
    	switch(role) {
        case CURRENCY:
        case UNIT:
            this.matchType = Type.MODIFIER;
            this.id = this.name = "in";
            break;
        case INLINE_VALUE:
            this.matchType = Type.INLINE_VALUE;
            // TODO client should know which kind of value is admitted. Selecting # should enable free editing.
            this.id = this.name = "#";
            break;
        case GROUP_OPEN:
            this.matchType = Type.MODIFIER;
            this.id = this.name = "(";
            break;
        case DISTRIBUTED_UNIT:
            this.matchType = Type.MODIFIER;
            this.id = this.name = "per";
            break;
        default:
    		break;
    	}
	}

    public String getId() {
        return id;
    }

    public TokenClass getTokenClass() {

        if (this.modifier != null) {
            switch(this.modifier) {
            case ADJACENT_TO:
            case BY:
            case CAUSED_BY:
            case CAUSING:
            case CONTAINED_IN:
            case CONTAINING:
            case DOWN_TO:
            case DURING:
            case FOR:
            case WITH:
            case WITHIN:
            case OF:
                break;

            case WITHOUT:
                // only for concepts
                break;

            case GREATER:
            case GREATEREQUAL:
            case LESS:
            case LESSEQUAL:
            case MINUS:
            case OVER:
            case TIMES:
            case PLUS:
                return TokenClass.DOUBLE;
            case IN:
                return this.semantics.contains(SemanticType.MONEY) || this.semantics.contains(SemanticType.MONETARY_VALUE)
                        ? TokenClass.CURRENCY
                        : TokenClass.UNIT;
            case PER:
                // also, should be contextual - spatial and/or temporal only
                return TokenClass.UNIT;
            case IS:
            case SAMEAS:
                // numeric, boolean or concept
                break;
            case WHERE:
                // another contextualizer = parenthesis
                break;
            default:
                break;
            }
        }

        return TokenClass.TOKEN;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public float getScore() {
        return score;
    }

    public Type getMatchType() {
        return matchType;
    }

    public Set<SemanticType> getConceptType() {
        return conceptType;
    }

    public Map<String, String> getIndexableFields() {
        return indexableFields;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public void setScore(float score) {
        this.score = score;
    }

    public void setMatchType(Type matchType) {
        this.matchType = matchType;
    }

    public void setIndexableFields(Map<String, String> indexableFields) {
        this.indexableFields = indexableFields;
    }

    public void setConceptType(Set<SemanticType> conceptType) {
        this.conceptType = conceptType;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean isAbstract) {
        this.isAbstract = isAbstract;
    }

    public Set<SemanticType> getSemantics() {
        return semantics;
    }

    public void setSemantics(Set<SemanticType> semantics) {
        this.semantics = semantics;
    }

    public UnarySemanticOperator getUnaryOperator() {
        return unaryOperator;
    }

    public void setUnaryOperator(UnarySemanticOperator unaryOperator) {
        this.unaryOperator = unaryOperator;
    }

    public BinarySemanticOperator getBinaryOperator() {
        return binaryOperator;
    }

    public void setBinaryOperator(BinarySemanticOperator binaryOperator) {
        this.binaryOperator = binaryOperator;
    }

    public SemanticLexicalElement getModifier() {
        return modifier;
    }

    public void setModifier(SemanticLexicalElement modifier) {
        this.modifier = modifier;
    }

    @Override
    public String toString() {
        return "SearchMatch [id=" + id + ", matchType=" + matchType + ", semantics=" + semantics + "]";
    }

}
