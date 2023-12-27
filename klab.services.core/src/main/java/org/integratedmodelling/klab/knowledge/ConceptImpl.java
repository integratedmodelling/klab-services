package org.integratedmodelling.klab.knowledge;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.utilities.Utils;
import org.springframework.util.StringUtils;

import groovy.lang.GroovyObjectSupport;

public class ConceptImpl extends GroovyObjectSupport implements Concept {

    private static final long serialVersionUID = -6871573029225503370L;

    private long id;
    private String urn;
    private Metadata metadata = Metadata.create();
    private Set<SemanticType> type = EnumSet.noneOf(SemanticType.class);
    private String namespace;
    private String name;
    private String referenceName;
    private boolean isAbstract;
    private List<Annotation> annotations = new ArrayList<>();
    private LogicalConnector qualifier;

    public void setQualifier(LogicalConnector qualifier) {
        this.qualifier = qualifier;
    }

    @Override
    public String getUrn() {
        return urn;
    }

    @Override
    public Set<SemanticType> getType() {
        return type;
    }

    @Override
    public boolean is(SemanticType type) {
        return this.type.contains(type);
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    @Override
    public LogicalConnector getQualifier() {
        return this.qualifier;
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

    public void setAbstract(boolean isAbstract) {
        this.isAbstract = isAbstract;
    }

    @Override
    public boolean isAbstract() {
        return isAbstract;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
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

    @Override
    public String codeName() {
        return Utils.CamelCase.toLowerCase(displayName(), '_');
    }
    
    @Override
    public String displayName() {

        // String ret = getMetadata().get(IMetadata.DISPLAY_LABEL_PROPERTY, String.class);
        //
        // if (ret == null) {
        String ret = getMetadata().get(Metadata.DC_LABEL, String.class);
        // }
        if (ret == null) {
            ret = getName();
        }
        if (ret.startsWith("i")) {
            ret = ret.substring(1);
        }

        return ret;
    }

    @Override
    public String displayLabel() {
        String ret = displayName();
        if (!ret.contains(" ")) {
            ret = StringUtils.capitalize(Utils.CamelCase.toLowerCase(ret, ' '));
        }
        return ret;
    }

    @Override
    public Concept asConcept() {
        return this;
    }

    @Override
    public boolean isGeneric() {
        return false;
    }

    @Override
    public String toString() {
        return this.urn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(urn);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ConceptImpl other = (ConceptImpl) obj;
        return Objects.equals(urn, other.urn);
    }

	@Override
	public List<Annotation> getAnnotations() {
		return this.annotations;
	}

	public void setAnnotations(List<Annotation> annotations) {
		this.annotations = annotations;
	}

    
    
}
