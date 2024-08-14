package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Observable.Builder;
import org.integratedmodelling.klab.api.knowledge.Observable.ResolutionDirective;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A builder that just registers actions called on it and gets sent to a reasoner to replicate them in the
 * reasoner and return the result, which may contain operation results in its metadata.
 *
 * @author Ferd
 */
public class ObservableBuildStrategy implements Observable.Builder {

    private static final String REMOVED_CONCEPTS_METADATA_KEY = "observable.builder.metadata.removed" +
            ".concepts";

    private static final long serialVersionUID = -5968594309897960639L;

    transient private Scope scope;

    // all the methods codified as an enum
    public enum OperationType {
        OF, WITH,/* WITHIN, */GOAL, FROM, TO, WITH_ROLE, AS, WITH_TRAITS, WITHOUT, WITHOUT_ANY_CONCEPTS, WITHOUT_ANY_TYPES, ADJACENT,
        COOCCURRENT, COLLECTIVE,
        WITH_UNIT, WITH_CURRENCY, WITH_RANGE, WITH_VALUE_OPERATOR, LINKING, NAMED, WITHOUT_VALUE_OPERATORS, AS_OPTIONAL, WITHOUT_ROLES,
        WITH_TEMPORAL_INHERENT, REFERENCE_NAMED, WITH_INLINE_VALUE,
        WITH_DEFAULT_VALUE, WITH_RESOLUTION_EXCEPTION, AS_GENERIC, WITH_ANNOTATION, AS_DESCRIPTION_TYPE
    }

    public static class Operation implements Serializable {

        private static final long serialVersionUID = -3560499809607527771L;

        private List<Annotation> annotations = new ArrayList<>();
        private ResolutionDirective resolutionDirective;

        private DescriptionType descriptionType;
        private Unit unit;
        private Currency currency;
        private NumericRange range;
        private OperationType type;
        private Object pod;
        private List<Concept> concepts = new ArrayList<>();
        private List<SemanticType> types = new ArrayList<>();
        private List<SemanticRole> roles = new ArrayList<>();

        private Pair<ValueOperator, Object> valueOperation;
        private UnarySemanticOperator operator;

        public Operation() {

        }

        public Operation(OperationType type, Object value) {
            this.type = type;
            this.pod = value;
        }

        public Operation(OperationType type, Concept... concepts) {
            this.type = type;
            for (Concept concept : concepts) {
                this.concepts.add(concept);
            }
        }

        public Operation(OperationType type, SemanticType... types) {
            this.type = type;
            for (SemanticType concept : types) {
                this.types.add(concept);
            }
        }

        public Operation(OperationType type, SemanticRole... types) {
            this.type = type;
            for (SemanticRole concept : types) {
                this.roles.add(concept);
            }
        }

        public Operation(OperationType type, UnarySemanticOperator operator, Concept... concepts) {
            this.type = type;
            this.operator = operator;
            for (Concept concept : concepts) {
                this.concepts.add(concept);
            }
        }

        public Operation(OperationType operationType, Unit unit) {
            this.type = type;
            this.unit = unit;
        }

        public Operation(OperationType operationType, Currency unit) {
            this.type = type;
            this.currency = unit;
        }

        public Operation(OperationType operationType, NumericRange unit) {
            this.type = type;
            this.range = unit;
        }

        public Operation(OperationType operationType, Pair<ValueOperator, Object> unit) {
            this.type = type;
            this.valueOperation = unit;
        }

        public Operation(OperationType operationType, DescriptionType descriptionType) {
            this.type = operationType;
            this.descriptionType = descriptionType;
        }

        public Operation(OperationType operationType, boolean generic) {
            this.type = operationType;
            this.pod = generic;
        }

        public Operation(OperationType operationType) {
            this.type = operationType;
        }

        public Operation(OperationType operationType, String name) {
            this.type = operationType;
            this.pod = name;
        }

        public Operation(OperationType operationType, ResolutionDirective resolutionDirective) {
            this.type = operationType;
            this.resolutionDirective = resolutionDirective;
        }

        public Operation(OperationType operationType, Annotation annotation) {
            this.type = operationType;
            this.annotations.add(annotation);
        }

        public DescriptionType getDescriptionType() {
            return descriptionType;
        }

        public void setDescriptionType(DescriptionType descriptionType) {
            this.descriptionType = descriptionType;
        }

        public OperationType getType() {
            return type;
        }

        public void setType(OperationType type) {
            this.type = type;
        }

        public Object getPod() {
            return pod;
        }

        public void setPod(Object pod) {
            this.pod = pod;
        }

        public List<Concept> getConcepts() {
            return concepts;
        }

        public void setConcepts(List<Concept> concepts) {
            this.concepts = concepts;
        }

        public UnarySemanticOperator getOperator() {
            return operator;
        }

        public void setOperator(UnarySemanticOperator operator) {
            this.operator = operator;
        }

        public List<SemanticType> getTypes() {
            return types;
        }

        public void setTypes(List<SemanticType> types) {
            this.types = types;
        }

        public List<SemanticRole> getRoles() {
            return roles;
        }

        public void setRoles(List<SemanticRole> roles) {
            this.roles = roles;
        }

        public Unit getUnit() {
            return unit;
        }

        public void setUnit(Unit unit) {
            this.unit = unit;
        }

        public Currency getCurrency() {
            return currency;
        }

        public void setCurrency(Currency currency) {
            this.currency = currency;
        }

        public NumericRange getRange() {
            return range;
        }

        public void setRange(NumericRange range) {
            this.range = range;
        }

        public Pair<ValueOperator, Object> getValueOperation() {
            return valueOperation;
        }

        public void setValueOperation(Pair<ValueOperator, Object> valueOperation) {
            this.valueOperation = valueOperation;
        }

        public ResolutionDirective getResolutionException() {
            return resolutionDirective;
        }

        public void setResolutionException(ResolutionDirective resolutionDirective) {
            this.resolutionDirective = resolutionDirective;
        }

        public List<Annotation> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(List<Annotation> annotations) {
            this.annotations = annotations;
        }

    }

    private Observable baseObservable;
    private Concept baseConcept;
    private List<Operation> operations = new ArrayList<>();
    private Object defaultValue;
    private List<Notification> notifications = new ArrayList<>();

    public ObservableBuildStrategy(Observable observable, Scope scope) {
        this.baseObservable = observable;
        this.scope = scope;
    }

    public ObservableBuildStrategy(Concept observable, Scope scope) {
        this.baseConcept = observable;
        this.scope = scope;
    }

    @Override
    public Builder of(Concept inherent) {
        this.operations.add(new Operation(OperationType.OF, inherent));
        return this;
    }

    @Override
    public Builder with(Concept compresent) {
        this.operations.add(new Operation(OperationType.WITH, compresent));
        return this;
    }

//    @Override
//    public Builder within(Concept context) {
//        this.operations.add(new Operation(OperationType.WITHIN, context));
//        return this;
//    }

    @Override
    public Builder withGoal(Concept goal) {
        this.operations.add(new Operation(OperationType.GOAL, goal));
        return this;
    }

    @Override
    public Builder from(Concept causant) {
        this.operations.add(new Operation(OperationType.FROM, causant));
        return this;
    }

    @Override
    public Builder to(Concept caused) {
        this.operations.add(new Operation(OperationType.TO, caused));
        return this;
    }

    @Override
    public Builder withRole(Concept role) {
        this.operations.add(new Operation(OperationType.WITH_ROLE, role));
        return this;
    }

    @Override
    public Builder as(UnarySemanticOperator type, Concept... participants) throws KlabValidationException {
        this.operations.add(new Operation(OperationType.AS, type, participants));
        return this;
    }

    @Override
    public Builder as(DescriptionType descriptionType) {
        this.operations.add(new Operation(OperationType.AS_DESCRIPTION_TYPE, descriptionType));
        return this;
    }

    @Override
    public Builder withTrait(Concept... concepts) {
        this.operations.add(new Operation(OperationType.WITH_TRAITS, concepts));
        return this;
    }

    @Override
    public Builder withTrait(Collection<Concept> concepts) {
        this.operations.add(new Operation(OperationType.WITH_TRAITS,
                concepts.toArray(new Concept[concepts.size()])));
        return this;
    }

    @Override
    public Builder without(Collection<Concept> concepts) {
        this.operations.add(new Operation(OperationType.WITHOUT,
                concepts.toArray(new Concept[concepts.size()])));
        return this;
    }

    @Override
    public Builder without(Concept... concepts) {
        this.operations.add(new Operation(OperationType.WITHOUT, concepts));
        return this;
    }

    @Override
    public Concept buildConcept() throws KlabValidationException {
        var reasoner = this.scope.getService(Reasoner.class);
        return reasoner.buildConcept(this);
    }

    @Override
    public Observable build() throws KlabValidationException {
        var reasoner = this.scope.getService(Reasoner.class);
        return reasoner.buildObservable(this);
    }

    @Override
    public Builder withCooccurrent(Concept cooccurrent) {
        this.operations.add(new Operation(OperationType.WITH, cooccurrent));
        return this;
    }

    @Override
    public Builder withAdjacent(Concept adjacent) {
        this.operations.add(new Operation(OperationType.ADJACENT, adjacent));
        return this;
    }

    @Override
    public Builder withoutAny(Collection<Concept> concepts) {
        this.operations.add(new Operation(OperationType.WITHOUT_ANY_CONCEPTS,
                concepts.toArray(new Concept[concepts.size()])));
        return this;
    }

    @Override
    public Builder withoutAny(SemanticType... type) {
        this.operations.add(new Operation(OperationType.WITHOUT_ANY_TYPES, type));
        return this;
    }

    @Override
    public Builder withoutAny(Concept... concepts) {
        this.operations.add(new Operation(OperationType.WITHOUT_ANY_CONCEPTS, concepts));
        return this;
    }

    @Override
    public Builder withUnit(Unit unit) {
        this.operations.add(new Operation(OperationType.WITH_UNIT, unit));
        return this;
    }

    @Override
    public Builder withCurrency(Currency currency) {
        this.operations.add(new Operation(OperationType.WITH_CURRENCY, currency));
        return this;
    }

    @Override
    public Builder withValueOperator(ValueOperator operator, Object valueOperand) {
        this.operations.add(new Operation(OperationType.WITH_VALUE_OPERATOR, Pair.of(operator,
                valueOperand)));
        return this;
    }

    @Override
    public Collection<Concept> removed(Semantics result) {
        // find the removed concepts in the metadata using private key
        return result.getMetadata().get(REMOVED_CONCEPTS_METADATA_KEY, Collections.emptyList());
    }

    @Override
    public Builder linking(Concept source, Concept target) {
        this.operations.add(new Operation(OperationType.LINKING, source, target));
        return this;
    }

    @Override
    public Builder named(String name) {
        this.operations.add(new Operation(OperationType.NAMED, name));
        return this;
    }
//
//    @Override
//    public Builder withDistributedInherency(boolean ofEach) {
//        this.operations.add(new Operation(OperationType.WITH_DISTRIBUTED_INHERENCY, ofEach));
//        return this;
//    }

    @Override
    public Builder withoutValueOperators() {
        this.operations.add(new Operation(OperationType.WITHOUT_VALUE_OPERATORS));
        return this;
    }

//    @Override
//    public Builder withTargetPredicate(Concept targetPredicate) {
//        this.operations.add(new Operation(OperationType.WITH_TARGET_PREDICATE, targetPredicate));
//        return this;
//    }

    @Override
    public Builder optional(boolean optional) {
        this.operations.add(new Operation(OperationType.AS_OPTIONAL, optional));
        return this;
    }

    @Override
    public Builder without(SemanticRole... roles) {
        this.operations.add(new Operation(OperationType.WITHOUT_ROLES, roles));
        return this;
    }

    @Override
    public Builder withTemporalInherent(Concept concept) {
        this.operations.add(new Operation(OperationType.WITH_TEMPORAL_INHERENT, concept));
        return this;
    }

//    @Override
//    public Builder withDereifiedAttribute(String dereifiedAttribute) {
//        this.operations.add(new Operation(OperationType.WITH_DEREIFIED_ATTRIBUTE, dereifiedAttribute));
//        return this;
//    }

    @Override
    public Builder named(String name, String referenceName) {
        this.operations.add(new Operation(OperationType.REFERENCE_NAMED, referenceName));
        this.operations.add(new Operation(OperationType.NAMED, name));
        return this;
    }

    @Override
    public Builder withUnit(String unit) {
        this.operations.add(new Operation(OperationType.WITH_UNIT, unit));
        return this;
    }

    @Override
    public Builder withCurrency(String currency) {
        this.operations.add(new Operation(OperationType.WITH_CURRENCY, currency));
        return this;
    }

    @Override
    public Builder withInlineValue(Object value) {
        this.operations.add(new Operation(OperationType.WITH_INLINE_VALUE, value));
        return this;
    }

    @Override
    public Builder withDefaultValue(Object defaultValue) {
        this.operations.add(new Operation(OperationType.WITH_DEFAULT_VALUE, defaultValue));
        return this;
    }

    @Override
    public Builder withResolutionException(ResolutionDirective resolutionDirective) {
        this.operations.add(new Operation(OperationType.WITH_RESOLUTION_EXCEPTION, resolutionDirective));
        return this;
    }

    @Override
    public Builder withRange(NumericRange range) {
        this.operations.add(new Operation(OperationType.WITH_RANGE, range));
        return this;
    }

    @Override
    public Builder generic(boolean generic) {
        this.operations.add(new Operation(OperationType.AS_GENERIC, generic));
        return this;
    }

    @Override
    public Builder collective(boolean collective) {
        this.operations.add(new Operation(OperationType.COLLECTIVE, collective));
        return this;
    }

    @Override
    public Builder withAnnotation(Annotation annotation) {
        this.operations.add(new Operation(OperationType.WITH_ANNOTATION, annotation));
        return this;
    }

    @Override
    public Builder withReferenceName(String s) {
        this.operations.add(new Operation(OperationType.REFERENCE_NAMED, s));
        return this;
    }

    public List<Operation> getOperations() {
        return operations;
    }

    public void setOperations(List<Operation> operations) {
        this.operations = operations;
    }

    public Concept getBaseConcept() {
        return baseConcept;
    }

    public void setBaseConcept(Concept baseConcept) {
        this.baseConcept = baseConcept;
    }

    public Observable getBaseObservable() {
        return baseObservable;
    }

    public void setBaseObservable(Observable baseObservable) {
        this.baseObservable = baseObservable;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

//    @Override
//    public Collection<Notification> getNotifications() {
//        return this.notifications;
//    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

}
