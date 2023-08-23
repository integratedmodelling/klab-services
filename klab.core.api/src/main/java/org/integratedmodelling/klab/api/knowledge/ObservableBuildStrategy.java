package org.integratedmodelling.klab.api.knowledge;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.exceptions.KValidationException;
import org.integratedmodelling.klab.api.knowledge.Observable.Builder;
import org.integratedmodelling.klab.api.knowledge.Observable.Resolution;
import org.integratedmodelling.klab.api.knowledge.Observable.ResolutionException;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * A builder that just registers actions called on it and gets sent to a reasoner to replicate them in the reasoner and
 * return the result, which may contain operation results in its metadata.
 *
 * @author Ferd
 */
public class ObservableBuildStrategy implements Observable.Builder {

    private static final String REMOVED_CONCEPTS_METADATA_KEY = "observable.builder.metadata.removed.concepts";

    private static final long serialVersionUID = -5968594309897960639L;

    transient private Scope scope;

    // all the methods codified as an enum
    public enum OperationType {
        OF, WITH, WITHIN, GOAL, FROM, TO, WITH_ROLE, AS, WITH_TRAITS, WITHOUT, WITHOUT_ANY, ADJACENT, COOCCURRENT,
        WITH_UNIT, WITH_CURRENCY, WITH_RANGE, WITH_VALUE_OPERATOR
    }

    public static class Operation implements Serializable {

        private static final long serialVersionUID = -3560499809607527771L;
        private Unit unit;
        private Currency currency;
        private NumericRange range;
        private OperationType type;
        private Literal pod;
        private List<Concept> concepts = new ArrayList<>();
        private List<SemanticType> types = new ArrayList<>();
        private List<SemanticRole> roles = new ArrayList<>();

        private Pair<ValueOperator, Literal> valueOperation;
        private UnarySemanticOperator operator;

        public Operation() {

        }

        public Operation(OperationType type, Literal value) {
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

        public Operation(OperationType operationType, Pair<ValueOperator, Literal> unit) {
            this.type = type;
            this.valueOperation = unit;
        }

        public OperationType getType() {
            return type;
        }

        public void setType(OperationType type) {
            this.type = type;
        }

        public Literal getPod() {
            return pod;
        }

        public void setPod(Literal pod) {
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

        public Pair<ValueOperator, Literal> getValueOperation() {
            return valueOperation;
        }

        public void setValueOperation(Pair<ValueOperator, Literal> valueOperation) {
            this.valueOperation = valueOperation;
        }
    }

    private Observable baseObservable;
    private Concept baseConcept;
    private List<Operation> operations = new ArrayList<>();
    private Literal defaultValue;
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

    @Override
    public Builder within(Concept context) {
        this.operations.add(new Operation(OperationType.WITHIN, context));
        return this;
    }

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
    public Builder as(UnarySemanticOperator type, Concept... participants) throws KValidationException {
        this.operations.add(new Operation(OperationType.AS, type, participants));
        return this;
    }

    @Override
    public Builder withTrait(Concept... concepts) {
        this.operations.add(new Operation(OperationType.WITH_TRAITS, concepts));
        return this;
    }

    @Override
    public Builder withTrait(Collection<Concept> concepts) {
        this.operations.add(new Operation(OperationType.WITH_TRAITS, concepts.toArray(new Concept[concepts.size()])));
        return this;
    }

    @Override
    public Builder without(Collection<Concept> concepts) {
        this.operations.add(new Operation(OperationType.WITHOUT, concepts.toArray(new Concept[concepts.size()])));
        return this;
    }

    @Override
    public Builder without(Concept... concepts) {
        this.operations.add(new Operation(OperationType.WITHOUT, concepts));
        return this;
    }

    @Override
    public Concept buildConcept() throws KValidationException {
        var reasoner = this.scope.getService(Reasoner.class);
        return reasoner.buildConcept(this);
    }

    @Override
    public Observable buildObservable() throws KValidationException {
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
        this.operations.add(new Operation(OperationType.WITHOUT_ANY, concepts.toArray(new Concept[concepts.size()])));
        return this;
    }

    @Override
    public Builder withoutAny(SemanticType... type) {
        this.operations.add(new Operation(OperationType.WITHOUT_ANY, type));
        return this;
    }

    @Override
    public Builder withoutAny(Concept... concepts) {
        this.operations.add(new Operation(OperationType.WITHOUT_ANY, concepts));
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
        this.operations.add(new Operation(OperationType.WITH_VALUE_OPERATOR, Pair.of(operator, Literal.of(valueOperand))));
        return this;
    }

    @Override
    public Collection<Concept> removed(Semantics result) {
        // find the removed concepts in the metadata using private key
        return result.getMetadata().get(REMOVED_CONCEPTS_METADATA_KEY, Collections.emptyList());
    }

    @Override
    public Builder linking(Concept source, Concept target) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder named(String name) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withDistributedInherency(boolean ofEach) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withoutValueOperators() {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withTargetPredicate(Concept targetPredicate) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder optional(boolean optional) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder without(SemanticRole... roles) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withTemporalInherent(Concept concept) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withDereifiedAttribute(String dereifiedAttribute) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder named(String name, String referenceName) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withUnit(String unit) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withCurrency(String currency) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withInlineValue(Object value) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withDefaultValue(Object defaultValue) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withResolutionException(ResolutionException resolutionException) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withRange(NumericRange range) {
        this.operations.add(new Operation(OperationType.WITH_RANGE, range));
        return this;
    }

    @Override
    public Builder generic(boolean generic) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withResolution(Resolution only) {
        // TODO Auto-generated method stub
        return this;
    }

    @Override
    public Builder withAnnotation(Annotation annotation) {
        // TODO Auto-generated method stub
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

    public Literal getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Literal defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public Collection<Notification> getNotifications() {
        return this.notifications;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

}
