package org.integratedmodelling.klab.knowledge;

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.knowledge.SemanticType;

public class ConceptImpl implements Serializable, Concept {

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
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean is(SemanticType type) {
        // TODO Auto-generated method stub
        return false;
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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> children() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> parents() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> allChildren() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> allParents() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Collection<Concept> closure() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Semantics domain() {
        // TODO Auto-generated method stub
        return null;
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

}
