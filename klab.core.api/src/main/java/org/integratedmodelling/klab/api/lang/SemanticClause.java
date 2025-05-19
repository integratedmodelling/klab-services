package org.integratedmodelling.klab.api.lang;

import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;

import java.util.EnumSet;
import java.util.Set;

public enum SemanticClause {

    OF(SemanticRole.INHERENT, "of", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.COUNTABLE)),
    FOR(SemanticRole.GOAL, "for", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.OBSERVABLE)),
    WITH(SemanticRole.COMPRESENT, "with", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.COUNTABLE)),
    CAUSED_BY(SemanticRole.CAUSANT, "caused by", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.OBSERVABLE)),
    ADJACENT_TO(SemanticRole.ADJACENT, "adjacent to", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.COUNTABLE)),
    CAUSING(SemanticRole.CAUSED, "causing", EnumSet.of(SemanticType.PROCESS, SemanticType.EVENT), EnumSet.of(SemanticType.OBSERVABLE)),
    DURING(SemanticRole.COOCCURRENT, "during", EnumSet.of(SemanticType.OBSERVABLE), EnumSet.of(SemanticType.PROCESS, SemanticType.EVENT)),
    LINKING(SemanticRole.RELATIONSHIP_SOURCE, "linking", EnumSet.of(SemanticType.RELATIONSHIP), EnumSet.of(SemanticType.COUNTABLE)),
    TO(SemanticRole.RELATIONSHIP_TARGET, "to", EnumSet.of(SemanticType.RELATIONSHIP), EnumSet.of(SemanticType.COUNTABLE));

    public String[] declaration;
    public SemanticRole role;
    public Set<SemanticType> applicable;
    public Set<SemanticType> argument;

    SemanticClause(SemanticRole role, String declaration, Set<SemanticType> applicable, Set<SemanticType> argument) {
        this.declaration = new String[] {declaration};
        this.role = role;
        this.applicable = applicable;
        this.argument = argument;
    }

}
