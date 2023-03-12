package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.codehaus.groovy.transform.trait.Traits;
import org.integratedmodelling.kim.api.IKimConcept;
import org.integratedmodelling.kim.api.IKimConcept.Expression;
import org.integratedmodelling.kim.api.IKimConcept.ObservableRole;
import org.integratedmodelling.kim.api.IKimConcept.Type;
import org.integratedmodelling.klab.Concepts;
import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.IConcept;
import org.integratedmodelling.klab.api.knowledge.IMetadata;
import org.integratedmodelling.klab.api.knowledge.IObservable.Builder;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.ApplicableConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.ParentConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimScope;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.exceptions.KlabValidationException;
import org.integratedmodelling.klab.knowledge.IntelligentMap;
import org.integratedmodelling.klab.owl.ObservableBuilder;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.integratedmodelling.klab.services.reasoner.owl.Axiom;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.integratedmodelling.klab.services.reasoner.owl.Ontology;
import org.springframework.stereotype.Service;

@Service
public class SemanticTranslator {

    private Map<String, String> coreConceptPeers = new HashMap<>();
    Map<Concept, Emergence> emergent = new HashMap<>();
    IntelligentMap<Set<Emergence>> emergence = new IntelligentMap<>();

    /*
     * Record correspondence of core concept peers to worldview concepts. Called by KimValidator for
     * later use at namespace construction.
     */
    public void setWorldviewPeer(String coreConcept, String worldviewConcept) {
        coreConceptPeers.put(worldviewConcept, coreConcept);
    }

    /**
     * An emergence is the appearance of an observation triggered by another, under the assumptions
     * stated in the worldview. It applies to processes and relationships and its emergent
     * observable can be a configuration, subject or process.
     * 
     * @author Ferd
     *
     */
    public class Emergence {

        public Set<Concept> triggerObservables = new LinkedHashSet<>();
        public Concept emergentObservable;
        public String namespaceId;

        public Set<Observation> matches(Concept relationship, ContextScope scope) {

            for (Concept trigger : triggerObservables) {
                Set<Observation> ret = new HashSet<>();
                checkScope(trigger, scope.getCatalog(), relationship, ret);
                if (!ret.isEmpty()) {
                    return ret;
                }
            }

            return Collections.emptySet();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            result = prime * result + Objects.hash(emergentObservable, namespaceId, triggerObservables);
            return result;
        }

        private Object getEnclosingInstance() {
            return SemanticTranslator.this;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Emergence other = (Emergence) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
                return false;
            return Objects.equals(emergentObservable, other.emergentObservable) && Objects.equals(namespaceId, other.namespaceId)
                    && Objects.equals(triggerObservables, other.triggerObservables);
        }

        /*
         * current observable must be one of the triggers, any others need to be in scope
         */
        private void checkScope(Concept trigger, Map<Observable, Observation> map, Concept relationship, Set<Observation> obs) {
            if (trigger.is(SemanticType.UNION)) {
                for (Concept trig : trigger.operands()) {
                    checkScope(trig, map, relationship, obs);
                }
            } else if (trigger.is(SemanticType.INTERSECTION)) {
                for (Concept trig : trigger.operands()) {
                    Set<Observation> oobs = new HashSet<>();
                    checkScope(trig, map, relationship, oobs);
                    if (oobs.isEmpty()) {
                        obs = oobs;
                    }
                }
            } else {
                Observation a = map.get(trigger);
                if (a != null) {
                    obs.add(a);
                }
            }
        }
    }

    public Concept defineConcept(KimConcept parsed) {
        // TODO Auto-generated method stub
        return null;
    }

    public Observable defineObservable(KimObservable parsed) {
        // TODO Auto-generated method stub
        return null;
    }

    public Concept defineConcept(KimConceptStatement statement, Channel monitor) {
        return build(statement, OWL.INSTANCE.requireOntology(statement.getNamespace(), null), null, monitor);
    }

    public Concept build(KimConceptStatement concept, Ontology ontology, KimConceptStatement kimObject, Channel monitor) {

        if (concept.isMacro()) {
            return null;
        }

        try {

            if (concept.isAlias()) {

                /*
                 * can only have 'is' X or 'equals' X
                 */
                Concept parent = null;
                if (concept.getUpperConceptDefined() != null) {
                    parent = OWL.INSTANCE.getConcept(concept.getUpperConceptDefined());
                    if (parent == null) {
                        monitor.error("Core concept " + concept.getUpperConceptDefined() + " is unknown", concept);
                    } else {
                        parent.getType().addAll(concept.getType());
                    }
                } else {

                    List<Concept> concepts = new ArrayList<>();
                    int i = 0;
                    for (ParentConcept p : concept.getParents()) {

                        if (i > 0) {
                            monitor.error("concepts defining aliases with 'equals' cannot have more than one parent", p);
                        }

                        for (KimConcept pdecl : p.getConcepts()) {
                            Concept declared = declare(pdecl, ontology, monitor);
                            if (declared == null) {
                                monitor.error("parent declaration " + pdecl + " does not identify known concepts", pdecl);
                                return null;
                            }
                            concepts.add(declared);
                        }
                        i++;
                    }

                    if (concepts.size() == 1) {
                        parent = concepts.get(0);
                    }
                }

                if (parent != null) {
                    ontology.addDelegateConcept(concept.getName(), ontology.getName(), parent);
                }

                return null;
            }

            Concept ret = buildInternal(concept, ontology, kimObject, monitor);
            Concept upperConceptDefined = null;
            if (concept.getParents().isEmpty()) {
                Concept parent = null;
                if (concept.getUpperConceptDefined() != null) {
                    upperConceptDefined = parent = OWL.INSTANCE.getConcept(concept.getUpperConceptDefined());
                    if (parent == null) {
                        monitor.error("Core concept " + concept.getUpperConceptDefined() + " is unknown", concept);
                    }
                } else {
                    parent = OWL.INSTANCE.getCoreOntology().getCoreType(concept.getType());
                    if (coreConceptPeers.containsKey(ret.toString())) {
                        // ensure that any non-trivial core inheritance is dealt with appropriately
                        parent = OWL.INSTANCE.getCoreOntology().alignCoreInheritance(ret);
                    }
                }

                if (parent != null) {
                    ontology.add(Axiom.SubClass(parent.getUrn(), ret.getName()));
                }
            }

            if (ret != null) {
                ontology.add(Axiom.AnnotationAssertion(ret.getName(), NS.BASE_DECLARATION, "true"));
                createProperties(ret, ontology);
                ontology.define();

                if (coreConceptPeers.containsKey(ret.toString()) && upperConceptDefined != null
                        && "true".equals(upperConceptDefined.getMetadata().get(NS.IS_CORE_KIM_TYPE, "false"))) {
                    OWL.INSTANCE.getCoreOntology().setAsCoreType(ret);
                }

            }

            return ret;

        } catch (Throwable e) {
            monitor.error(e, concept);
        }
        return null;
    }

    private Concept buildInternal(final KimConceptStatement concept, Ontology ontology, KimConceptStatement kimObject,
            final Channel monitor) {

        Concept main = null;
        String mainId = concept.getName();

        ontology.add(Axiom.ClassAssertion(mainId,
                concept.getType().stream().map((c) -> SemanticType.valueOf(c.name())).collect(Collectors.toSet())));

        // set the k.IM definition
        ontology.add(
                Axiom.AnnotationAssertion(mainId, NS.CONCEPT_DEFINITION_PROPERTY, ontology.getName() + ":" + concept.getName()));

        // and the reference name
        ontology.add(Axiom.AnnotationAssertion(mainId, NS.REFERENCE_NAME_PROPERTY,
                OWL.getCleanFullId(ontology.getName(), concept.getName())));

        /*
         * basic attributes subjective deniable internal uni/bidirectional (relationship)
         */
        if (concept.isAbstract()) {
            ontology.add(Axiom.AnnotationAssertion(mainId, CoreOntology.NS.IS_ABSTRACT, "true"));
        }

        ontology.define();
        main = ontology.getConcept(mainId);

        for (ParentConcept parent : concept.getParents()) {

            List<Concept> concepts = new ArrayList<>();
            for (KimConcept pdecl : parent.getConcepts()) {
                Concept declared = declare(pdecl, ontology, monitor);
                if (declared == null) {
                    monitor.error("parent declaration " + pdecl + " does not identify known concepts", pdecl);
                    return null;
                }
                concepts.add(declared);
            }

            if (concepts.size() == 1) {
                ontology.add(Axiom.SubClass(concepts.get(0).getUrn(), mainId));
            } else {
                Concept expr = null;
                switch(parent.getConnector()) {
                case INTERSECTION:
                    expr = OWL.INSTANCE.getIntersection(concepts, ontology, concepts.get(0).getType());
                    break;
                case UNION:
                    expr = OWL.INSTANCE.getUnion(concepts, ontology, concepts.get(0).getType());
                    break;
                case FOLLOWS:
                    expr = OWL.INSTANCE.getConsequentialityEvent(concepts, ontology);
                    break;
                default:
                    // won't happen
                    break;
                }
                if (concept.isAlias()) {
                    ontology.addDelegateConcept(mainId, ontology.getName(), expr);
                } else {
                    ontology.add(Axiom.SubClass(expr.getUrn(), mainId));
                }
            }
            ontology.define();
        }

        for (KimScope child : concept.getChildren()) {
            if (child instanceof KimConceptStatement) {
                try {
                    // KimConceptStatement chobj = kimObject == null ? null : new
                    // KimConceptStatement((IKimConceptStatement) child);
                    Concept childConcept = buildInternal((KimConceptStatement) child, ontology, concept,
                            /*
                             * monitor instanceof ErrorNotifyingMonitor ? ((ErrorNotifyingMonitor)
                             * monitor).contextualize(child) :
                             */ monitor);
                    ontology.add(Axiom.SubClass(mainId, childConcept.getName()));
                    ontology.define();
                    // kimObject.getChildren().add(chobj);
                } catch (Throwable e) {
                    monitor.error(e, child);
                }
            }
        }

        for (KimConcept inherited : concept.getTraitsInherited()) {
            Concept trait = declare(inherited, ontology, monitor);
            if (trait == null) {
                monitor.error("inherited " + inherited.getName() + " does not identify known concepts", inherited);
                return null;
            }
            try {
                OWL.INSTANCE.addTrait(main, trait, ontology);
            } catch (KlabValidationException e) {
                monitor.error(e, inherited);
            }
        }

        // TODO all the rest: creates, ....
        for (KimConcept affected : concept.getQualitiesAffected()) {
            Concept quality = declare(affected, ontology, monitor);
            if (quality == null) {
                monitor.error("affected " + affected.getName() + " does not identify known concepts", affected);
                return null;
            }
            OWL.INSTANCE.restrictSome(main, OWL.INSTANCE.getProperty(CoreOntology.NS.AFFECTS_PROPERTY), quality, ontology);
        }

        for (KimConcept required : concept.getRequiredIdentities()) {
            Concept quality = declare(required, ontology, monitor);
            if (quality == null) {
                monitor.error("required " + required.getName() + " does not identify known concepts", required);
                return null;
            }
            OWL.INSTANCE.restrictSome(main, OWL.INSTANCE.getProperty(NS.REQUIRES_IDENTITY_PROPERTY), quality, ontology);
        }

        for (KimConcept affected : concept.getObservablesCreated()) {
            Concept quality = declare(affected, ontology, monitor);
            if (quality == null) {
                monitor.error("created " + affected.getName() + " does not identify known concepts", affected);
                return null;
            }
            OWL.INSTANCE.restrictSome(main, OWL.INSTANCE.getProperty(NS.CREATES_PROPERTY), quality, ontology);
        }

        for (ApplicableConcept link : concept.getSubjectsLinked()) {
            if (link.getOriginalObservable() == null && link.getSource() != null) {
                // relationship source->target
                OWL.INSTANCE.defineRelationship(main, declare(link.getSource(), ontology, monitor),
                        declare(link.getTarget(), ontology, monitor), ontology);
            } else {
                // TODO
            }
        }

        if (!concept.getEmergenceTriggers().isEmpty()) {
            List<Concept> triggers = new ArrayList<>();
            for (KimConcept trigger : concept.getEmergenceTriggers()) {
                triggers.add(declare(trigger, ontology, monitor));
            }
            registerEmergent(main, triggers);
        }

        // if (kimObject != null) {
        // kimObject.set(main);
        // }

        return main;
    }

    /*
     * Register the triggers and each triggering concept in the emergence map.
     */
    public boolean registerEmergent(Concept configuration, Collection<Concept> triggers) {

        if (!configuration.isAbstract()) {

            // DebugFile.println("CHECK for storage of " + configuration + " based on " + triggers);

            if (this.emergent.containsKey(configuration)) {
                return true;
            }

            // DebugFile.println(" STORED " + configuration);

            Emergence descriptor = new Emergence();
            descriptor.emergentObservable = configuration;
            descriptor.triggerObservables.addAll(triggers);
            descriptor.namespaceId = configuration.getNamespace();
            this.emergent.put(configuration, descriptor);

            for (Concept trigger : triggers) {
                for (Concept tr : OWL.INSTANCE.flattenOperands(trigger)) {
                    Set<Emergence> es = emergence.get(tr);
                    if (es == null) {
                        es = new HashSet<>();
                        emergence.put(tr, es);
                    }
                    es.add(descriptor);
                }
            }

            return true;
        }

        return false;
    }

    private void createProperties(Concept ret, Ontology ns) {

        String pName = null;
        String pProp = null;
        if (ret.is(SemanticType.ATTRIBUTE)) {
            // hasX
            pName = "has" + ret.getName();
            pProp = NS.HAS_ATTRIBUTE_PROPERTY;
        } else if (ret.is(SemanticType.REALM)) {
            // inX
            pName = "in" + ret.getName();
            pProp = NS.HAS_REALM_PROPERTY;
        } else if (ret.is(SemanticType.IDENTITY)) {
            // isX
            pName = "is" + ret.getName();
            pProp = NS.HAS_IDENTITY_PROPERTY;
        }
        if (pName != null) {
            ns.add(Axiom.ObjectPropertyAssertion(pName));
            ns.add(Axiom.ObjectPropertyRange(pName, ret.getName()));
            ns.add(Axiom.SubObjectProperty(pProp, pName));
            ns.add(Axiom.AnnotationAssertion(ret.getName(), NS.TRAIT_RESTRICTING_PROPERTY, ns.getName() + ":" + pName));
        }
    }

    private Concept declare(KimConcept concept, Ontology ontology, Channel monitor) {
        return declareInternal(concept, ontology, monitor);
    }

    private synchronized @Nullable Concept declareInternal(KimConcept concept, Ontology ontology,
            Channel monitor) {

        Concept main = null;

        if (concept.getObservable() != null) {
            main = declareInternal(concept.getObservable(), ontology, monitor);
        } else if (concept.getName() != null) {
            main = OWL.INSTANCE.getConcept(concept.getName());
        }

        if (main == null) {
            return null;
        }

        Builder builder = new ObservableBuilder(main, ontology, monitor).withDeclaration(concept, monitor);

        if (concept.getDistributedInherent() != null) {
            builder.withDistributedInherency(true);
        }

        /*
         * transformations first
         */

        if (concept.getInherent() != null) {
            IConcept c = declareInternal(concept.getInherent(), ontology, monitor);
            if (c != null) {
                builder.of(c);
            }
        }
        if (concept.getContext() != null) {
            IConcept c = declareInternal(concept.getContext(), ontology, monitor);
            if (c != null) {
                if (ObservableRole.CONTEXT.equals(concept.getDistributedInherent())) {
                    builder.of(c);
                } else {
                    builder.within(c);
                }
            }
        }
        if (concept.getCompresent() != null) {
            IConcept c = declareInternal(concept.getCompresent(), ontology, monitor);
            if (c != null) {
                builder.with(c);
            }
        }
        if (concept.getCausant() != null) {
            IConcept c = declareInternal(concept.getCausant(), ontology, monitor);
            if (c != null) {
                builder.from(c);
            }
        }
        if (concept.getCaused() != null) {
            IConcept c = declareInternal(concept.getCaused(), ontology, monitor);
            if (c != null) {
                builder.to(c);
            }
        }
        if (concept.getMotivation() != null) {
            IConcept c = declareInternal(concept.getMotivation(), ontology, monitor);
            if (c != null) {
                if (ObservableRole.GOAL.equals(concept.getDistributedInherent())) {
                    builder.of(c);
                } else {
                    builder.withGoal(c);
                }
            }
        }
        if (concept.getCooccurrent() != null) {
            IConcept c = declareInternal(concept.getCooccurrent(), ontology, monitor);
            if (c != null) {
                builder.withCooccurrent(c);
            }
        }
        if (concept.getAdjacent() != null) {
            IConcept c = declareInternal(concept.getAdjacent(), ontology, monitor);
            if (c != null) {
                builder.withAdjacent(c);
            }
        }
        if (concept.getRelationshipSource() != null) {
            IConcept source = declareInternal(concept.getRelationshipSource(), ontology, monitor);
            IConcept target = declareInternal(concept.getRelationshipTarget(), ontology, monitor);
            if (source != null && target != null) {
                builder.linking(source, target);
            }

        }

        for (IKimConcept c : concept.getTraits()) {
            IConcept trait = declareInternal(c, ontology, monitor);
            if (trait != null) {
                builder.withTrait(trait);
            }
        }

        for (IKimConcept c : concept.getRoles()) {
            IConcept role = declareInternal(c, ontology, monitor);
            if (role != null) {
                builder.withRole(role);
            }
        }

        if (concept.getSemanticModifier() != null) {
            IConcept other = null;
            if (concept.getComparisonConcept() != null) {
                other = declareInternal(concept.getComparisonConcept(), ontology, monitor);
            }
            try {
                builder.as(concept.getSemanticModifier(),
                        other == null ? (IConcept[]) null : new IConcept[]{other});
            } catch (KlabValidationException e) {
                monitor.error(e, concept);
            }
        }

        Concept ret = null;
        try {

            ret = (Concept) builder.buildConcept();

            /*
             * handle unions and intersections
             */
            if (concept.getOperands().size() > 0) {
                List<IConcept> concepts = new ArrayList<>();
                concepts.add(ret);
                for (IKimConcept op : concept.getOperands()) {
                    concepts.add(declareInternal(op, ontology, monitor));
                }
                ret = concept.getExpressionType() == Expression.INTERSECTION
                        ? OWL.INSTANCE.getIntersection(concepts, ontology,
                                concept.getOperands().get(0).getType())
                        : OWL.INSTANCE.getUnion(concepts, ontology, concept.getOperands().get(0).getType());
            }

            // set the k.IM definition in the concept.This must only happen if the
            // concept wasn't there - within build() and repeat if mods are made
            if (builder.axiomsAdded()) {

                ret.getOntology().define(Collections.singletonList(
                        Axiom.AnnotationAssertion(ret.getName(), NS.CONCEPT_DEFINITION_PROPERTY,
                                concept.getDefinition())));

                // consistency check
                if (!Reasoner.INSTANCE.isSatisfiable(ret)) {
                    ((Concept) ret).getTypeSet().add(Type.NOTHING);
                    monitor.error("the definition of this concept has logical errors and is inconsistent",
                            concept);
                }
            }

        } catch (Throwable e) {
            monitor.error(e, concept);
        }

        if (concept.isNegated()) {
            ret = (Concept) Traits.INSTANCE.makeNegation(ret, ontology);
        }

        return ret;
    }

    public static String getCleanId(Concept main) {
        String id = main.getMetadata().get(IMetadata.DC_LABEL, String.class);
        if (id == null) {
            id = main.getName();
        }
        return id;
    }

}
