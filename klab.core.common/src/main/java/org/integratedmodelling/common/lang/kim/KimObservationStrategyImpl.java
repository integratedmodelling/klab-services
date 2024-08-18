package org.integratedmodelling.common.lang.kim;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimLiteral;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.*;

public class KimObservationStrategyImpl implements KimObservationStrategy {

    private Metadata metadata = Metadata.create();
    private List<Annotation> annotations = new ArrayList<>();
    private int length;
    private int offsetInDocument;
    private String deprecation;
    private boolean deprecated;
    private Collection<Notification> notifications = new ArrayList<>();
    private String urn;
    private String description;
    private Scope scope = Scope.PUBLIC;
    private String namespace;
    private String projectName;
    private List<Operation> operations = new ArrayList<>();
    private Map<KimLiteral, Filter> macroVariables = new LinkedHashMap<>();
    private List<Filter> filters = new ArrayList<>();
    private int rank;
    private KnowledgeClass documentClass = KnowledgeClass.OBSERVATION_STRATEGY;

    public KimObservationStrategyImpl() {}

    @Override
    public Metadata getMetadata() {
        return this.metadata;
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

//    @Override
//    public String sourceCode() {
//        return sourceCode;
//    }

    @Override
    public Collection<Notification> getNotifications() {
        return this.notifications;
    }

    @Override
    public void visit(Visitor visitor) {

    }

    @Override
    public String getUrn() {
        return this.urn;
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
    public Map<KimLiteral, Filter> getMacroVariables() {
        return this.macroVariables;
    }

    @Override
    public List<Operation> getOperations() {
        return this.operations;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
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

    public void setUrn(String urn) {
        this.urn = urn;
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
    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }

    public void setMacroVariables(Map<KimLiteral, Filter> macroVariables) {
        this.macroVariables = macroVariables;
    }

    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    @Override
    public Scope getScope() {
        return scope;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    @Override
    public KnowledgeClass getDocumentClass() {
        return documentClass;
    }

    public void setDocumentClass(KnowledgeClass documentClass) {
        this.documentClass = documentClass;
    }

    public static class FilterImpl implements Filter {

        private boolean negated;
        private KimObservable match;
        private ServiceCall function;
        private Object literal;

        @Override
        public boolean isNegated() {
            return this.negated;
        }

        @Override
        public KimObservable getMatch() {
            return this.match;
        }

        @Override
        public ServiceCall getFunction() {
            return this.function;
        }

        @Override
        public Object getLiteral() {
            return this.literal;
        }

        public void setNegated(boolean negated) {
            this.negated = negated;
        }

        public void setMatch(KimObservable match) {
            this.match = match;
        }

        public void setFunction(ServiceCall function) {
            this.function = function;
        }

        public void setLiteral(Object literal) {
            this.literal = literal;
        }
    }

    public static class OperationImpl implements Operation {

        private Type type;
        private KimObservable observable;
        private List<ServiceCall> functions = new ArrayList<>();
        private List<KimObservationStrategy> deferredStrategies = new ArrayList<>();

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public KimObservable getObservable() {
            return this.observable;
        }

        @Override
        public List<ServiceCall> getFunctions() {
            return this.functions;
        }

        @Override
        public List<KimObservationStrategy> getDeferredStrategies() {
            return this.deferredStrategies;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setObservable(KimObservable observable) {
            this.observable = observable;
        }

        public void setFunctions(List<ServiceCall> functions) {
            this.functions = functions;
        }

        public void setDeferredStrategies(List<KimObservationStrategy> deferredStrategies) {
            this.deferredStrategies = deferredStrategies;
        }

    }
}
