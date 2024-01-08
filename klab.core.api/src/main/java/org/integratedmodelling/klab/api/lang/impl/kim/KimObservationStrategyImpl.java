package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.lang.kim.KimScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.*;

public class KimObservationStrategyImpl implements KimObservationStrategy {
    //    private String urn;
//    private Version version;
    private Metadata metadata = Metadata.create();
    private List<Annotation> annotations = new ArrayList<>();
    private long creationTimestamp;
    private int length;
    private int offsetInDocument;
    private String deprecation;
    private boolean deprecated;
    private Collection<Notification> notifications = new ArrayList<>();
    private String name;
    private String description;
    private Scope scope = Scope.PUBLIC;
    private String namespace;
    private Parameters<String> documentationMetadata = Parameters.create();
    private String locationDescriptor;
    private List<Operation> operations = new ArrayList<>();
    private Map<Literal, Filter> macroVariables = new LinkedHashMap<>();
    private List<Filter> filters = new ArrayList<>();
    private int rank;

    @Override
    public Metadata getMetadata() {
        return this.metadata;
    }

    @Override
    public void visit(Visitor visitor) {

    }

    @Override
    public int getOffsetInDocument() {
        return this.offsetInDocument;
    }

    @Override
    public int getLength() {
        return this.length;
    }

    @Override
    public List<Annotation> getAnnotations() {
        return this.annotations;
    }

    @Override
    public String getDeprecation() {
        return this.deprecation;
    }

    @Override
    public boolean isDeprecated() {
        return this.deprecated;
    }

    @Override
    public String sourceCode() {
        return null;
    }

    @Override
    public Collection<Notification> getNotifications() {
        return this.notifications;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public int getRank() {
        return this.rank;
    }

    @Override
    public List<Filter> getFilters() {
        return this.filters;
    }

    @Override
    public Map<Literal, Filter> getMacroVariables() {
        return this.macroVariables;
    }

    @Override
    public List<Operation> getOperations() {
        return this.operations;
    }

    @Override
    public List<KimScope> getChildren() {
        return null;
    }

    @Override
    public String getLocationDescriptor() {
        return this.locationDescriptor;
    }

    @Override
    public Parameters<String> getDocumentationMetadata() {
        return this.documentationMetadata;
    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }

    @Override
    public Scope getScope() {
        return this.scope;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    public void setCreationTimestamp(long creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public void setOffsetInDocument(int offsetInDocument) {
        this.offsetInDocument = offsetInDocument;
    }

    public void setDeprecation(String deprecation) {
        this.deprecation = deprecation;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public void setNotifications(Collection<Notification> notifications) {
        this.notifications = notifications;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public void setDocumentationMetadata(Parameters<String> documentationMetadata) {
        this.documentationMetadata = documentationMetadata;
    }

    public void setLocationDescriptor(String locationDescriptor) {
        this.locationDescriptor = locationDescriptor;
    }

    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }

    public void setMacroVariables(Map<Literal, Filter> macroVariables) {
        this.macroVariables = macroVariables;
    }

    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }
}
