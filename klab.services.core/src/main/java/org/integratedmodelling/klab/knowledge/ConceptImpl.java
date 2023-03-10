package org.integratedmodelling.klab.knowledge;

import java.util.Collection;
import java.util.Set;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.configuration.Services;

public class ConceptImpl implements Concept {

    private static final long serialVersionUID = -6871573029225503370L;
    
    private long id;
    private String urn;
    private Metadata metadata;
    private Set<SemanticType> type;
    private String namespace;
    private String name;
    private String referenceName;
    
    @Override
    public String getUrn() {
        return urn;
    }

    @Override
    public Collection<SemanticType> getType() {
        return type;
    }

    @Override
    public boolean is(Semantics other) {
        return Services.INSTANCE.getReasoner().subsumes(this, other);
    }

    @Override
    public boolean is(SemanticType type) {
        return this.type.contains(type);
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public void setType(Set<SemanticType> type) {
        this.type = type;
    }

    @Override
    public Semantics semantics() {
        return this;
    }

    @Override
    public boolean isAbstract() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Collection<Concept> operands() {
        return Services.INSTANCE.getReasoner().operands(this);
    }

    @Override
    public Collection<Concept> children() {
        return Services.INSTANCE.getReasoner().children(this);
    }

    @Override
    public Collection<Concept> parents() {
        return Services.INSTANCE.getReasoner().parents(this);
    }

    @Override
    public Collection<Concept> allChildren() {
        return Services.INSTANCE.getReasoner().allChildren(this);
    }

    @Override
    public Collection<Concept> allParents() {
        return Services.INSTANCE.getReasoner().allParents(this);
    }

    @Override
    public Collection<Concept> closure() {
        return Services.INSTANCE.getReasoner().closure(this);
    }

    @Override
    public Semantics domain() {
        return Services.INSTANCE.getReasoner().domain(this);
    }

    @Override
    public Concept parent() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getReferenceName() {
        return referenceName;
    }

    @Override
    public Version getVersion() {
        // TODO Auto-generated method stub
        return null;
    }

}
