package org.integratedmodelling.klab.knowledge;

import java.util.Collection;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.KValueMediator;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Activity.Description;

public class ObservableImpl implements Observable {

    private static final long serialVersionUID = 6188649888474774359L;

    private ConceptImpl semantics;
    private Version version;
    private DirectObservation observer;
    private Activity.Description descriptionType;
    private Artifact.Type artifactType;
    private boolean isAbstract;
    private String urn;
    
    @Override
    public String getUrn() {
        return urn;
    }

    @Override
    public Semantics semantics() {
        return semantics;
    }

    @Override
    public Semantics domain() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean is(Semantics other) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean is(SemanticType type) {
        return semantics.is(type);
    }

    @Override
    public boolean isAbstract() {
        return isAbstract;
    }

    @Override
    public String getNamespace() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getReferenceName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    @Override
    public Concept getSemantics() {
        return this.semantics;
    }

    @Override
    public Unit getUnit() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Currency getCurrency() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Pair<ValueOperator, Object>> getValueOperators() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Annotation> getAnnotations() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> abstractPredicates() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Description getDescriptionType() {
        return this.descriptionType;
    }

    @Override
    public Type getArtifactType() {
        return this.artifactType;
    }

    @Override
    public String getStatedName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KValueMediator mediator() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Literal getValue() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept context() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept inherent() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Concept temporalInherent() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Literal getDefaultValue() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<ResolutionException> getResolutionExceptions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isGeneric() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isOptional() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public DirectObservation getObserver() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getDefinition() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getDeclaration() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isGlobal() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Resolution getResolution() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> getContextualRoles() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean resolves(Observable other, Concept context) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Concept> getAbstractPredicates() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<Concept, Concept> getResolvedPredicates() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isDereified() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isSpecialized() {
        // TODO Auto-generated method stub
        return false;
    }
}
