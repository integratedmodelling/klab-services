//package org.integratedmodelling.klab.runtime.computation;
//
//import java.util.Collection;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//import org.integratedmodelling.kim.validation.KimNotification;
//import org.integratedmodelling.klab.api.collections.Parameters;
//import org.integratedmodelling.klab.api.data.general.IExpression;
//import org.integratedmodelling.klab.api.data.general.IExpression.CompilerOption;
//import org.integratedmodelling.klab.api.extensions.ILanguageExpression;
//import org.integratedmodelling.klab.api.extensions.ILanguageProcessor;
//import org.integratedmodelling.klab.api.knowledge.Expression;
//import org.integratedmodelling.klab.api.knowledge.observation.Observation;
//import org.integratedmodelling.klab.api.model.INamespace;
//import org.integratedmodelling.klab.api.observations.IObservation;
//import org.integratedmodelling.klab.api.services.runtime.Notification;
//import org.integratedmodelling.klab.engine.runtime.api.IRuntimeScope;
//import org.integratedmodelling.klab.exceptions.KlabValidationException;
//
//import com.google.common.collect.Sets;
//
//public enum GroovyProcessor /*implements LanguageProcessor*/ {
//
//    INSTANCE;
//
//    public static final String ID = "groovy";
//
//    class GroovyDescriptor implements Expression.Descriptor {
//
//        String processedCode;
//        Collection<String> identifiers;
//        private Set<String> scalarIds;
//        private Set<String> objectIds;
//        private Set<String> contextualizers;
//        private List<Notification> errors;
//        private Map<String, Set<String>> mapIdentifiers;
//        private Set<Expression.CompilerOption> options;
//        private Parameters<String> variables;
//        private boolean forceScalar;
//        // private IExpression.Scope context;
//
//        GroovyDescriptor(String expression, Expression.Scope context, Expression.CompilerOption... options) {
//
//            this.options = Sets.newHashSet(options == null ? new Expression.CompilerOption[]{} : options);
//
//            /*
//             * Context should most definitely be nullable
//             */
//            INamespace namespace = context == null ? null : context.getNamespace();
//            // this.context = context;
//            GroovyExpressionPreprocessor processor = new GroovyExpressionPreprocessor(namespace, context, this.options);
//
//            this.processedCode = processor.process(expression);
//            this.identifiers = processor.getIdentifiers();
//            this.scalarIds = processor.getScalarIdentifiers();
//            this.objectIds = processor.getObjectIdentifiers();
//            this.contextualizers = processor.getContextualizers();
//            this.mapIdentifiers = processor.getMapIdentifiers();
//            this.variables = processor.getVariables();
//            this.errors = processor.getErrors();
//            this.forceScalar = context == null ? false : context.isForcedScalar();
//
//            if (context.getRuntimeScope() != null && !this.options.contains(CompilerOption.IgnoreContext)) {
//                Map<String, Observation> catalog = ((IRuntimeScope)context.getRuntimeScope()).getLocalCatalog(IObservation.class);
//                for (String key : catalog.keySet()) {
//                    Observation obs = catalog.get(key);
//                    if (!this.variables.containsKey(key) && !this.variables.containsValue(obs)) {
//                        variables.put(key, obs);
//                    }
//                }
//            }
//
//        }
//
//        @Override
//        public Collection<String> getIdentifiers() {
//            return identifiers;
//        }
//
//        @Override
//        public Collection<Expression.CompilerOption> getOptions() {
//            return options;
//        }
//
//        @Override
//        public boolean isScalar(Collection<String> stateIdentifiers) {
//
//            for (String id : stateIdentifiers) {
//                if (this.scalarIds.contains(id)) {
//                    return true;
//                }
//            }
//            return false;
//        }
//
//        public List<Notification> getNotifications() {
//            return errors;
//        }
//
//        public boolean hasErrors() {
//            return errors.size() > 0;
//        }
//
//        @Override
//        public Collection<String> getIdentifiersInScalarScope() {
//            return this.scalarIds;
//        }
//
//        @Override
//        public Collection<String> getIdentifiersInNonscalarScope() {
//            return this.objectIds;
//        }
//
//        @Override
//        public Expression compile() {
//            return new GroovyExpression(processedCode, true, this);
//        }
//
//        @Override
//        public boolean isScalar(String identifier) {
//            return scalarIds.contains(identifier);
//        }
//
//        @Override
//        public boolean isNonscalar(String identifier) {
//            return objectIds.contains(identifier);
//        }
//
//        @Override
//        public boolean isNonscalar(Collection<String> stateIdentifiers) {
//            for (String id : stateIdentifiers) {
//                if (this.objectIds.contains(id)) {
//                    return true;
//                }
//            }
//            return false;
//
//        }
//
//        @Override
//        public Collection<String> getContextualizers() {
//            return contextualizers;
//        }
//
//        @Override
//        public Map<String, Set<String>> getMapIdentifiers() {
//            return mapIdentifiers;
//        }
//
//        @Override
//        public Parameters<String> getVariables() {
//            return variables;
//        }
//
//        @Override
//        public boolean isScalar() {
//
//            if (forceScalar) {
//                return true;
//            }
//
//            for (String id : scalarIds) {
//                if (isScalar(id)) {
//                    return true;
//                }
//            }
//
//            return false;
//        }
//    }
//
//    @Override
//    public Expression compile(String expression, Expression.Scope context, Expression.CompilerOption... options) {
//        return new GroovyDescriptor(expression, context, options).compile();
//    }
//
//    @Override
//    public Expression.Descriptor describe(String expression, Expression.Scope context, Expression.CompilerOption... options) {
//        return new GroovyDescriptor(expression, context, options);
//    }
//
//    @Override
//    public String negate(String expression) {
//        return "!(" + expression + ")";
//    }
//
//}
