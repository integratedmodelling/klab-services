package org.integratedmodelling.klab.services.resources.lang;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.impl.LiteralImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.SemanticRole;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.impl.kim.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.languages.api.*;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Adapter to substitute the current ones, based on older k.IM grammars.
 */
public enum LanguageAdapter {

    INSTANCE;

    public KimObservable adaptObservable(ObservableSyntax observableSyntax) {

        KimObservableImpl ret = new KimObservableImpl();

        ret.setUrn(observableSyntax.encode());
        ret.setSemantics(adaptSemantics(observableSyntax.getSemantics()));
        ret.setCodeName(observableSyntax.codeName());

        return ret;
    }

    public KimConcept adaptSemantics(SemanticSyntax semantics) {

        KimConceptImpl ret = new KimConceptImpl();

        // NAH these should be done internally after generation
        //        ret.setUrn(semantics.encode());
        //        ret.setCodeName(semantics.codeName());

        ret.setType(adaptSemanticType(semantics.getType()));
        ret.setNegated(semantics.isNegated());
        ret.setCollective(semantics.isDistributed());
        ret.setDeprecation(semantics.getDeprecation());
        ret.setDeprecated(semantics.getDeprecation() != null);

        if (semantics.isLeafDeclaration()) {
            ret.setName(semantics.encode());
        } else {
            ret.setObservable(adaptSemantics(semantics.getObservable()));
            for (var cr : semantics.getConceptReferences()) {
                var trait = adaptSemantics(cr);
                if (trait.is(SemanticType.ROLE)) {
                    ret.getRoles().add(trait);
                } else if (trait.is(SemanticType.TRAIT)) {
                    ret.getTraits().add(trait);
                }
            }
        }

        for (var restriction : semantics.getRestrictions()) {
            switch (restriction.getFirst()) {
                case OF, OF_EACH -> {
                    // TODO
                    ret.setInherent(adaptSemantics(restriction.getSecond().get(0)));
                    if (restriction.getFirst() == SemanticSyntax.BinaryOperator.OF_EACH) {
                        ret.setDistributedInherent(SemanticRole.INHERENT);
                    }
                }
                case FOR -> {
                    ret.setGoal(adaptSemantics(restriction.getSecond().get(0)));
                }
                case WITH -> {
                    ret.setCompresent(adaptSemantics(restriction.getSecond().get(0)));
                }
                case ADJACENT -> {
                    ret.setAdjacent(adaptSemantics(restriction.getSecond().get(0)));
                }
                case OR -> {
                }
                case AND -> {
                }
                case CAUSING -> {
                    ret.setCaused(adaptSemantics(restriction.getSecond().get(0)));
                }
                case CAUSED_BY -> {
                    ret.setCausant(adaptSemantics(restriction.getSecond().get(0)));
                }
                case LINKING -> {
                    ret.setRelationshipSource(adaptSemantics(restriction.getSecond().get(0)));
                    ret.setRelationshipTarget(adaptSemantics(restriction.getSecond().get(1)));
                }
                case CONTAINING -> {
                    // TODO
                    throw new IllegalStateException("no syntax for containment");
                }
                case CONTAINED_IN -> {
                    // TODO
                    throw new IllegalStateException("no syntax for containment");
                }
                case DURING -> { // TODO missing DURING_EACH - but is it necessary?
                    ret.setCooccurrent(adaptSemantics(restriction.getSecond().get(0)));
                }
            }
        }

        // TODO establish abstract and generic nature

        ret.setUrn(ret.computeUrn());

        if (!ret.getUrn().contains(":")) {
            System.out.println("PORODIO");
        }

        return ret;
    }

    private KimConcept adaptSemantics(SemanticSyntax.ConceptData observable) {
        KimConceptImpl ret = new KimConceptImpl();
        ret.setUrn(observable.concept().namespace() + ":" + observable.concept().conceptName());
        ret.setName(ret.getUrn());
        ret.setType(adaptSemanticType(observable.concept().mainType()));
        ret.computeUrn();
        return ret;
    }

    private Set<SemanticType> adaptSemanticType(SemanticSyntax.Type type) {
        var ret = switch (type) {
            case VOID, NOTHING -> EnumSet.of(SemanticType.NOTHING);
            case ACCELERATION ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.ACCELERATION);
            case AMOUNT ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.AMOUNT);
            case ANGLE -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                    SemanticType.ANGLE);
            case AREA -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                    SemanticType.AREA);
            case ATTRIBUTE -> EnumSet.of(SemanticType.PREDICATE, SemanticType.ATTRIBUTE, SemanticType.TRAIT);
            case BOND -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.COUNTABLE,
                    SemanticType.DIRECT_OBSERVABLE, SemanticType.RELATIONSHIP,
                    SemanticType.BIDIRECTIONAL);
            case CHARGE ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.CHARGE);
            case CLASS -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUALITY, SemanticType.CLASS);
            case CONFIGURATION -> EnumSet.of(SemanticType.DIRECT_OBSERVABLE, SemanticType.CONFIGURATION);
            case DOMAIN -> EnumSet.of(SemanticType.PREDICATE, SemanticType.DOMAIN);
            case DURATION ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.DURATION);
            case ELECTRIC_POTENTIAL ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.ELECTRIC_POTENTIAL);
            case ENERGY ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.ENERGY);
            case ENTROPY ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.ENTROPY);
            case EVENT -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.COUNTABLE, SemanticType.EVENT);
            case EXTENT -> EnumSet.of(SemanticType.EXTENT, SemanticType.QUALITY);
            case FUNCTIONAL_RELATIONSHIP -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.COUNTABLE,
                    SemanticType.DIRECT_OBSERVABLE, SemanticType.RELATIONSHIP,
                    SemanticType.FUNCTIONAL);
            case GENERIC_QUALITY ->
                // this only happens with core im:Quality. It's deprecated and should not get here.
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUALITY);
            case IDENTITY -> EnumSet.of(SemanticType.PREDICATE, SemanticType.IDENTITY, SemanticType.TRAIT);
            case LENGTH ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.LENGTH);
            case MASS -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                    SemanticType.MASS);
            case MONEY -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                    SemanticType.MONEY);
            case ORDERING ->
                    EnumSet.of(SemanticType.PREDICATE, SemanticType.ORDERING, SemanticType.TRAIT); // TODO
            // attribute?
            case PRESSURE ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.PRESSURE);
            case PRIORITY ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.PRIORITY);
            case PROCESS -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.PROCESS);
            case QUANTITY ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.QUANTITY);
            case AGENT -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.COUNTABLE,
                    SemanticType.DIRECT_OBSERVABLE, SemanticType.AGENT);
            case REALM -> EnumSet.of(SemanticType.PREDICATE, SemanticType.ATTRIBUTE, SemanticType.TRAIT);
            case RESISTANCE ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.RESISTANCE);
            case RESISTIVITY ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.RESISTIVITY);
            case ROLE -> EnumSet.of(SemanticType.PREDICATE, SemanticType.ROLE);
            case STRUCTURAL_RELATIONSHIP -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.COUNTABLE,
                    SemanticType.DIRECT_OBSERVABLE, SemanticType.RELATIONSHIP,
                    SemanticType.STRUCTURAL);
            case SUBJECT -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.COUNTABLE,
                    SemanticType.DIRECT_OBSERVABLE, SemanticType.SUBJECT);
            case TEMPERATURE ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.TEMPERATURE);
            case VELOCITY ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.VELOCITY);
            case VISCOSITY ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.VISCOSITY);
            case MONETARY_VALUE -> null;
            case VOLUME ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.VOLUME);
            case WEIGHT ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.WEIGHT);
            case PROBABILITY ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.PROBABILITY);
            case OCCURRENCE ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.OCCURRENCE);
            case PERCENTAGE ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.PERCENTAGE);
            case RATIO -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                    SemanticType.RATIO);
            case UNCERTAINTY ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.UNCERTAINTY);
            case VALUE -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                    SemanticType.VALUE);
            case PROPORTION ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.PROPORTION);
            case RATE -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                    SemanticType.RATE);
            case PRESENCE -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUALITY, SemanticType.PRESENCE);
            case MAGNITUDE ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.MAGNITUDE);
            case NUMEROSITY ->
                    EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUANTIFIABLE, SemanticType.QUALITY,
                            SemanticType.NUMEROSITY);
        };

        // single source of truth for intensive/extensive nature
        if (type.is(SemanticSyntax.TypeCategory.INTENSIVE)) {
            ret.add(SemanticType.INTENSIVE);
        } else if (type.is(SemanticSyntax.TypeCategory.EXTENSIVE)) {
            ret.add(SemanticType.EXTENSIVE);
        }
        return ret;
    }

    public KimObservationStrategyDocument adaptStrategies(ObservationStrategiesSyntax definition) {

        KimObservationStrategiesImpl ret = new KimObservationStrategiesImpl();
        ret.setUrn(definition.getUrn()); // FIXME use the URN from the preamble name
        // we don't add source code here as each strategy has its own
        for (var strategy : definition.getStrategies()) {
            ret.getStatements().add(adaptStrategy(strategy));
        }
        return ret;
    }

    private KimObservationStrategy adaptStrategy(ObservationStrategySyntax strategy) {

        var ret = new KimObservationStrategyImpl(strategy.encode());

        ret.setUrn(strategy.getName());
        ret.setDescription(strategy.getDescription());
        ret.setOffsetInDocument(strategy.getCodeOffset());
        ret.setLength(strategy.getCodeLength());
        ret.setDeprecation(strategy.getDeprecation());
        ret.setDeprecated(strategy.getDeprecation() != null);


        for (var filter : strategy.getFilters()) {
            var f = new KimObservationStrategyImpl.FilterImpl();
            ret.getFilters().add(f);
        }
        for (var operation : strategy.getOperations()) {
            var o = new KimObservationStrategyImpl.OperationImpl();
            ret.getOperations().add(o);
        }
        for (var let : strategy.getMacroVariables().keySet()) {
            var f = new KimObservationStrategyImpl.FilterImpl();
            // TODO
            ret.getMacroVariables().put(adaptLiteral(let), f);
        }
        return ret;
    }

    private Literal adaptLiteral(ParsedLiteral let) {
        var ret = new LiteralImpl();
        return ret;
    }

    public KimOntology adaptOntology(OntologySyntax ontology, String projectName,
                                     Collection<Notification> notifications) {

        KlabOntologyImpl ret = new KlabOntologyImpl();

        ret.setUrn(ontology.getName());
        ret.getImportedOntologies().addAll(ontology.getImportedOntologies());
        ret.setSourceCode(ontology.getSourceCode());
        ret.getMetadata().put(Metadata.DC_COMMENT, ontology.getDescription());
        ret.setVersion(Version.create(ontology.getVersion()));
        ret.setProjectName(projectName);

        if (ontology.getDomain() == OntologySyntax.rootDomain) {
            ret.setDomain(KimOntology.rootDomain);
            for (var owlImport : ontology.getImportedCoreOntologies().keySet()) {
                ret.getOwlImports().add(Pair.of(owlImport,
                        ontology.getImportedCoreOntologies().get(owlImport)));
            }
        } else {
            ret.setDomain(adaptSemantics(ontology.getDomain()));
        }

        for (var definition : ontology.getConceptDeclarations()) {
            ret.getStatements().add(adaptConceptDefinition(definition, ontology.getName()));
        }

        ret.getNotifications().addAll(notifications);

        return ret;
    }

    private KimConceptStatement adaptConceptDefinition(ConceptDeclarationSyntax definition,
                                                       String namespace) {

        KimConceptStatementImpl ret = new KimConceptStatementImpl();
        ret.setUrn(definition.getName());
        ret.setNamespace(namespace);
        ret.setAbstract(definition.isAbstract());
        ret.setSealed(definition.isSealed());
        ret.setSubjective(definition.isSubjective());
        ret.setDocstring(definition.getDescription());
        ret.setAlias(definition.isAlias());
        ret.setOffsetInDocument(definition.getCodeOffset());
        ret.setLength(definition.getCodeLength());
        ret.setDeprecation(definition.getDeprecation());
        ret.setDeprecated(definition.getDeprecation() != null);

        ret.setType(adaptSemanticType(definition.getDeclaredType()));
        if (definition.isDeniable()) {
            ret.getType().add(SemanticType.DENIABLE);
        }
        if (definition.isAbstract()) {
            ret.getType().add(SemanticType.ABSTRACT);
        }
        if (definition.isSealed()) {
            ret.getType().add(SemanticType.SEALED);
        }
        if (definition.isSubjective()) {
            ret.getType().add(SemanticType.SUBJECTIVE);
        }

        if (definition.isCoreDeclaration()) {
            ret.setUpperConceptDefined(definition.getDeclaredParent().encode());
        } else {
            ret.setDeclaredParent(definition.getDeclaredParent() == null ? null :
                                  adaptSemantics(definition.getDeclaredParent()));
            if (ret.getDeclaredParent() != null && definition.isGenericQuality()) {
                ret.getType().clear();
                ret.getType().addAll(ret.getDeclaredParent().getType());
            }
        }
        for (var child : definition.getChildren()) {
            ret.getChildren().add(adaptConceptDefinition(child, namespace));
        }
        return ret;
    }
}
