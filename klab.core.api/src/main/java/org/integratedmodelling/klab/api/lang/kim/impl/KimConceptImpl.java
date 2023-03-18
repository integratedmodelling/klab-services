package org.integratedmodelling.klab.api.lang.kim.impl;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.integratedmodelling.klab.api.data.Version;
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
    private String urn;
    private List<KimConcept> operands = new ArrayList<>();
    private Expression expressionType;
    private SemanticType fundamentalType;
    private KimConcept cooccurrent;
    private KimConcept adjacent;
    private String codeName;
    private KimConcept temporalInherent;
    private SemanticRole distributedInherent;
    private Version version;

    @Override
    public SemanticRole getDistributedInherent() {
        return distributedInherent;
    }

    public void setDistributedInherent(SemanticRole distributedInherent) {
        this.distributedInherent = distributedInherent;
    }

    public Set<SemanticType> getArgumentType() {
        return argumentType;
    }

    public void setArgumentType(Set<SemanticType> argumentType) {
        this.argumentType = argumentType;
    }

    public KimConceptImpl() {}

    transient private Set<SemanticType> argumentType = EnumSet.noneOf(SemanticType.class);
    
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
        this.urn = other.urn;                                           
        this.operands.addAll(other.operands);               
        this.expressionType = other.expressionType;                                    
        this.fundamentalType = other.fundamentalType;                                
        this.cooccurrent = other.cooccurrent;                                      
        this.adjacent = other.adjacent;                                         
        this.codeName = other.codeName;                                             
        this.temporalInherent = other.temporalInherent;            
        this.distributedInherent = other.distributedInherent;
        this.argumentType = EnumSet.copyOf(other.argumentType);
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
    public String getUrn() {
        return this.urn;
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

    public void setUrn(String urn) {
        this.urn = urn;;
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
        return this.urn;
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
            ret.urn = ret.computeUrn();
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
        
        this.urn = computeUrn();
        
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

        this.urn = computeUrn();

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
    
    

    /**
     * Create a text declaration that can be parsed back into a concept.
     */
    public String computeUrn() {

        String ret = "";
        boolean complex = false;

        if (semanticModifier != null) {
            ret += (ret.isEmpty() ? "" : " ") + semanticModifier.declaration[0];
            complex = true;
        }

        if (negated) {
            ret += (ret.isEmpty() ? "" : " ") + "not";
            complex = true;
        }

        String concepts = "";
        boolean ccomplex = false;

        for (KimConcept trait : traits) {
            concepts += (concepts.isEmpty() ? "" : " ") + parenthesize(trait.getUrn());
            ccomplex = true;
        }

        for (KimConcept role : roles) {
            concepts += (concepts.isEmpty() ? "" : " ") + parenthesize(role.getUrn());
            ccomplex = true;
        }

//        for (KimConcept conc : unclassified) {
//            concepts += (concepts.isEmpty() ? "" : " ") + conc;
//            ccomplex = true;
//        }

        concepts += (concepts.isEmpty() ? "" : " ") + (name == null ? observable.toString() : name);

        ret += (ret.isEmpty() ? "" : " ") + (ccomplex ? "(" : "") + concepts + (ccomplex ? ")" : "");

        if (comparisonConcept != null) {
            ret += " " + semanticModifier.declaration[1] + " " + comparisonConcept.getUrn();
            complex = true;
        }

        if (authority != null) {
            ret += " identified as " + stringify(authorityTerm) + " by " + authority;
            complex = true;
        }

        if (inherent != null) {
            ret += " of " + (distributedInherent == null ? "" : "each ") + inherent;
            complex = true;
        }

        if (context != null) {
            ret += " within " + context;
            complex = true;
        }

        if (causant != null) {
            ret += " caused by " + causant;
            complex = true;
        }

        if (caused != null) {
            ret += " causing " + caused;
            complex = true;
        }

        if (compresent != null) {
            ret += " with " + compresent;
            complex = true;
        }

        if (cooccurrent != null) {
            ret += " during " + cooccurrent;
            complex = true;
        }

        if (temporalInherent != null) {
            ret += " during each " + temporalInherent;
            complex = true;
        }

        if (adjacent != null) {
            ret += " adjacent to " + adjacent;
            complex = true;
        }

        if (motivation != null) {
            ret += " for " + motivation;
            complex = true;
        }

        if (relationshipSource != null) {
            ret += " linking " + relationshipSource;
            if (relationshipTarget != null) {
                ret += " to " + relationshipSource;
            }
            complex = true;
        }

        boolean expression = false;
        for (KimConcept operand : operands) {
            ret += " " + (expressionType == Expression.INTERSECTION ? "and" : "or") + " " + operand;
            complex = true;
            expression = true;
        }

        return (expression /* ccomplex || complex */) ? parenthesize(ret) : ret;
    }

    /**
     * Add parentheses around a declaration unless it is already enclosed in parentheses.
     * 
     * @param ret
     * @return
     */
    private static String parenthesize(String ret) {
        int firstOpening = -1;
        int lastClosing = -1;
        int level = 0;
        for (int i = 0; i < ret.length(); i++) {
            if (ret.charAt(i) == '(') {
                if (level == 0) {
                    firstOpening = i;
                }
                level++;
            } else if (ret.charAt(i) == ')') {
                level--;
                if (level == 0) {
                    lastClosing = i;
                }
            }
        }

        boolean enclosed = firstOpening == 0 && lastClosing == ret.length() - 1;

        return enclosed ? ret : ("(" + ret + ")");
    }

    private String stringify(String term) {

        if (term.startsWith("\"")) {
            return term;
        }

        boolean ws = false;

        // stringify anything that's not a lowercase ID
        for (int i = 0; i < term.length(); i++) {
            if (Character.isWhitespace(term.charAt(i))
                    || !(Character.isLetter(term.charAt(i)) || Character.isDigit(term.charAt(i)) || term.charAt(i) == '_')) {
                ws = true;
                break;
            }
        }

        // TODO should escape any internal double quotes, unlikely
        return ws ? ("\"" + term + "\"") : term;
    }

    @Override
    public Version getVersion() {
        return this.version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }
}
