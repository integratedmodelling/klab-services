package org.integratedmodelling.klab.api.lang;

import java.util.EnumSet;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;

/**
 * These are duplications of syntactic elements from other enums. They are used to provide a common API in incremental concept 
 * definition, and never in parsing or encoding actual knowledge.
 * 
 * @author Ferd
 *
 */
public enum SemanticLexicalElement {

    WITHIN(SemanticRole.CONTEXT, "within", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.AGENT, SemanticType.SUBJECT)),
    OF(SemanticRole.INHERENT, "of", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.COUNTABLE)),
    FOR(SemanticRole.GOAL, "for", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.OBSERVABLE)),
    WITH(SemanticRole.COMPRESENT, "with", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.COUNTABLE)),
    CAUSED_BY(SemanticRole.CAUSANT, "caused by", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.OBSERVABLE)),
    ADJACENT_TO(SemanticRole.ADJACENT, "adjacent to", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.COUNTABLE)),
    CONTAINED_IN(null, "contained in", EnumSet.of(SemanticType.COUNTABLE), EnumSet.of(SemanticType.COUNTABLE)),
    CONTAINING(null, "containing", EnumSet.of(SemanticType.COUNTABLE), EnumSet.of(SemanticType.COUNTABLE)),
    CAUSING(SemanticRole.CAUSED, "causing", EnumSet.of(SemanticType.PROCESS, SemanticType.EVENT), EnumSet.of(SemanticType.OBSERVABLE)),
    DURING(SemanticRole.COOCCURRENT, "during", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.PROCESS, SemanticType.EVENT)),
    LINKING(SemanticRole.RELATIONSHIP_SOURCE, "linking", EnumSet.of(SemanticType.RELATIONSHIP), EnumSet.of(SemanticType.COUNTABLE)),
    TO(SemanticRole.RELATIONSHIP_TARGET, "to", EnumSet.of(SemanticType.RELATIONSHIP), EnumSet.of(SemanticType.COUNTABLE)),

    IN(null, "in", null, null),
    PER(null, "per", null, null),

    // Observable modifiers. TODO remove these in favor of ValueOperator
    BY(null, "by", null, null),
    DOWN_TO(null, "down to", null, null),
    GREATER(null, ">", null, null),
    LESS(null, "<", null, null),
    GREATEREQUAL(null, ">=", null, null),
    LESSEQUAL(null, "<=", null, null),
    IS(null, "=", null, null),
    SAMEAS(null, "==", null, null),
    WITHOUT(null, "without", null, null),
    WHERE(null, "where", null, null),
    PLUS(null, "plus", null, null),
    MINUS(null, "minus", null, null),
    TIMES(null, "times", null, null),
    OVER(null, "over", null, null);

    public String[] declaration;
    public SemanticRole role;
    public Set<SemanticType> applicable;
    public Set<SemanticType> argument;
    
    SemanticLexicalElement(SemanticRole role, String decl, Set<SemanticType> applicable, Set<SemanticType> argument) {
        this.declaration = new String[] {decl};
        this.role = role;
        this.applicable = applicable;
        this.argument = argument;
    }

    public static SemanticLexicalElement forCode(String code) {
        for (SemanticLexicalElement val : values()) {
            if (code.equals(val.declaration[0])) {
                return val;
            }
        }
        return null;
    }
    
    public static SemanticLexicalElement getModifier(String valueModifier) {
        for (SemanticLexicalElement m : SemanticLexicalElement.values()) {
            if (m.declaration[0].equals(valueModifier)) {
                return m;
            }
        }
        return null;
    }

}
