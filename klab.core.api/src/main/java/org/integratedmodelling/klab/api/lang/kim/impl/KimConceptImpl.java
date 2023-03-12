package org.integratedmodelling.klab.api.lang.kim.impl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;

/**
 * A IKimConcept is the declaration of a concept, i.e. a semantic expression built out of known
 * concepts and conforming to k.IM semantic constraints. Concept expressions compile to this
 * structure, which retains the final concept names only as fully qualified names. External
 * infrastructure can create the actual concepts that a reasoner can operate on.
 * 
 * @author ferdinando.villa
 *
 */
public class KimConceptImpl extends KimStatementImpl implements KimConcept {

    private static final long serialVersionUID = 8531431719010407385L;

    private SemanticRole semanticRole;
    private boolean traitObservable;
    private String name;
    private Set<SemanticType> type = EnumSet.noneOf(SemanticType.class);
    private KimConcept observable;
    private KimConcept context;
    private KimConcept inherent;
    private KimConcept motivation;
    private KimConcept causant;
    private KimConcept caused;
    private KimConcept compresent;
    private KimConcept comparisonConcept;
    private String authorityTerm;
    private String authority;
    private UnarySemanticOperator semanticModifier;
    private KimConcept relationshipSource;
    private KimConcept relationshipTarget;
    private List<KimConcept> traits = new ArrayList<>();
    private List<KimConcept> roles = new ArrayList<>();
    private boolean template;
    private boolean negated;
    private String definition;
    private List<KimConcept> operands = new ArrayList<>();
    private Expression expressionType;
    private SemanticType fundamentalType;
    private KimConcept cooccurrent;
    private KimConcept adjacent;
    private String codeName;
    private KimConcept temporalInherent;

    public KimConceptImpl() {}
    
    private KimConceptImpl(KimConceptImpl other) {
        this.semanticRole = other.semanticRole;                                   
        this.traitObservable = other.traitObservable;                                     
        this.name = other.name;                                                 
        this.type = EnumSet.copyOf(other.type); 
        this.observable = other.observable;                                       
        this.context = other.context;                                          
        this.inherent = other.inherent;                                         
        this.motivation = other.motivation;                                       
        this.causant = other.causant;                                          
        this.caused = other.caused;                                           
        this.compresent = other.compresent;                                       
        this.comparisonConcept = other.comparisonConcept;                                
        this.authorityTerm = other.authority;                                        
        this.authority = other.authority;                                            
        this.semanticModifier = other.semanticModifier;                      
        this.relationshipSource = other.relationshipSource;                               
        this.relationshipTarget = other.relationshipTarget;                               
        this.traits.addAll(other.traits);                 
        this.roles.addAll(other.roles);                  
        this.template = other.template;                                            
        this.negated = other.negated;                                             
        this.definition = other.definition;                                           
        this.operands.addAll(other.operands);               
        this.expressionType = other.expressionType;                                    
        this.fundamentalType = other.fundamentalType;                                
        this.cooccurrent = other.cooccurrent;                                      
        this.adjacent = other.adjacent;                                         
        this.codeName = other.codeName;                                             
        this.temporalInherent = other.temporalInherent;                                 
    }
    
    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Set<SemanticType> getType() {
        return this.type;
    }

    @Override
    public KimConcept getObservable() {
        return this.observable;
    }

    @Override
    public KimConcept getContext() {
        return this.context;
    }

    @Override
    public KimConcept getInherent() {
        return this.inherent;
    }

    @Override
    public KimConcept getMotivation() {
        return this.motivation;
    }

    @Override
    public KimConcept getCausant() {
        return this.causant;
    }

    @Override
    public KimConcept getCaused() {
        return this.caused;
    }

    @Override
    public KimConcept getCompresent() {
        return this.compresent;
    }

    @Override
    public KimConcept getComparisonConcept() {
        return this.comparisonConcept;
    }

    @Override
    public String getAuthorityTerm() {
        return this.authorityTerm;
    }

    @Override
    public String getAuthority() {
        return this.authority;
    }

    @Override
    public UnarySemanticOperator getSemanticModifier() {
        return this.semanticModifier;
    }

    @Override
    public KimConcept getRelationshipSource() {
        return this.relationshipSource;
    }

    @Override
    public KimConcept getRelationshipTarget() {
        return this.relationshipTarget;
    }

    @Override
    public List<KimConcept> getTraits() {
        return this.traits;
    }

    @Override
    public List<KimConcept> getRoles() {
        return this.roles;
    }

    @Override
    public boolean isTemplate() {
        return this.template;
    }

    @Override
    public boolean isNegated() {
        return this.negated;
    }

    @Override
    public String getDefinition() {
        return this.definition;
    }

    @Override
    public boolean is(SemanticType type) {
        return this.type.contains(type);
    }

    @Override
    public List<KimConcept> getOperands() {
        return this.operands;
    }

    @Override
    public Expression getExpressionType() {
        return this.expressionType;
    }

    @Override
    public SemanticType getFundamentalType() {
        return this.fundamentalType;
    }

    @Override
    public KimConcept getCooccurrent() {
        return this.cooccurrent;
    }

    @Override
    public KimConcept getAdjacent() {
        return this.adjacent;
    }

    @Override
    public String getCodeName() {
        return this.codeName;
    }

    @Override
    public SemanticRole getSemanticRole() {
        return this.semanticRole;
    }

    @Override
    public boolean isTraitObservable() {
        return this.traitObservable;
    }

    @Override
    public KimConcept getTemporalInherent() {
        return this.temporalInherent;
    }

    public void setSemanticRole(SemanticRole semanticRole) {
        this.semanticRole = semanticRole;
    }

    public void setTraitObservable(boolean traitObservable) {
        this.traitObservable = traitObservable;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(Set<SemanticType> type) {
        this.type = type;
    }

    public void setObservable(KimConcept observable) {
        this.observable = observable;
    }

    public void setContext(KimConcept context) {
        this.context = context;
    }

    public void setInherent(KimConcept inherent) {
        this.inherent = inherent;
    }

    public void setMotivation(KimConcept motivation) {
        this.motivation = motivation;
    }

    public void setCausant(KimConcept causant) {
        this.causant = causant;
    }

    public void setCaused(KimConcept caused) {
        this.caused = caused;
    }

    public void setCompresent(KimConcept compresent) {
        this.compresent = compresent;
    }

    public void setComparisonConcept(KimConcept comparisonConcept) {
        this.comparisonConcept = comparisonConcept;
    }

    public void setAuthorityTerm(String authorityTerm) {
        this.authorityTerm = authorityTerm;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public void setSemanticModifier(UnarySemanticOperator semanticModifier) {
        this.semanticModifier = semanticModifier;
    }

    public void setRelationshipSource(KimConcept relationshipSource) {
        this.relationshipSource = relationshipSource;
    }

    public void setRelationshipTarget(KimConcept relationshipTarget) {
        this.relationshipTarget = relationshipTarget;
    }

    public void setTraits(List<KimConcept> traits) {
        this.traits = traits;
    }

    public void setRoles(List<KimConcept> roles) {
        this.roles = roles;
    }

    public void setTemplate(boolean template) {
        this.template = template;
    }

    public void setNegated(boolean negated) {
        this.negated = negated;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public void setOperands(List<KimConcept> operands) {
        this.operands = operands;
    }

    public void setExpressionType(Expression expressionType) {
        this.expressionType = expressionType;
    }

    public void setFundamentalType(SemanticType fundamentalType) {
        this.fundamentalType = fundamentalType;
    }

    public void setCooccurrent(KimConcept cooccurrent) {
        this.cooccurrent = cooccurrent;
    }

    public void setAdjacent(KimConcept adjacent) {
        this.adjacent = adjacent;
    }

    public void setCodeName(String codeName) {
        this.codeName = codeName;
    }

    public void setTemporalInherent(KimConcept temporalInherent) {
        this.temporalInherent = temporalInherent;
    }

    @Override
    public String toString() {
        return this.definition;
    }
    
    /*
     * modification methods
     */
    
    public KimConcept removeOperator() {
        KimConceptImpl ret = new KimConceptImpl(this);
        if (this.semanticModifier != null) {
            ret.semanticModifier = null;
            ret.comparisonConcept = null;
            ret.type = this.argumentType;
        }
        return ret;
    }

    public KimConcept removeComponents(SemanticRole... roles) {

        KimConceptImpl ret = new KimConceptImpl(this);

        for (SemanticRole role : roles) {

            switch(role) {
            case ADJACENT:
                ret.adjacent = null;
                break;
            case CAUSANT:
                ret.causant = null;
                break;
            case CAUSED:
                ret.caused = null;
                break;
            case COMPRESENT:
                ret.compresent = null;
                break;
            case CONTEXT:
                ret.context = null;
                break;
            case COOCCURRENT:
                ret.cooccurrent = null;
                break;
            case GOAL:
                ret.motivation = null;
                break;
            case INHERENT:
                ret.inherent = null;
                break;
            case ROLE:
                ret.roles.clear();
                break;
            case TRAIT:
                ret.traits.clear();
                break;
            case TEMPORAL_INHERENT:
                ret.temporalInherent = null;
                break;
            case UNARY_OPERATOR:
                ((KimConceptImpl)ret.observable).semanticModifier = null;
                break;
            default:
                break;
            }
        }
        
        // RECOMPUTE DEFINITION
        
        return ret;
    }

    public KimConcept removeComponents(List<String> declarations, List<SemanticRole> roles) {

        KimConceptImpl ret = new KimConceptImpl(this);

        for (int i = 0; i < declarations.size(); i++) {

            String declaration = declarations.get(i);
            SemanticRole role = roles.get(i);

            switch(role) {
            case ADJACENT:
                ret.adjacent = null;
                break;
            case CAUSANT:
                ret.causant = null;
                break;
            case CAUSED:
                ret.caused = null;
                break;
            case COMPRESENT:
                ret.compresent = null;
                break;
            case CONTEXT:
                ret.context = null;
                break;
            case COOCCURRENT:
                ret.cooccurrent = null;
                break;
            case GOAL:
                ret.motivation = null;
                break;
            case INHERENT:
                ret.inherent = null;
                break;
            case TEMPORAL_INHERENT:
                ret.temporalInherent = null;
                break;
            case ROLE:
                ret.roles = copyWithout(ret.roles, declaration);
                break;
            case TRAIT:
                ret.traits = copyWithout(ret.traits, declaration);
                break;
            default:
                break;
            }
        }

        // RECOMPUTE DEFINITION, CODENAME AND...?

        return ret;
    }

    private static List<KimConcept> copyWithout(List<KimConcept> concepts, String declaration) {
        List<KimConcept> ret = new ArrayList<>();
        for (KimConcept c : concepts) {
            if (!c.toString().equals(declaration)) {
                ret.add(c);
            }
        }
        return ret;
    }
    
}
