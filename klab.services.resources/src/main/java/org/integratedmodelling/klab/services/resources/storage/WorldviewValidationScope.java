package org.integratedmodelling.klab.services.resources.storage;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.languages.api.ConceptDeclarationSyntax;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Worldview-aware semantic validation scope
 */
public class WorldviewValidationScope extends BasicObservableValidationScope {

    public WorldviewValidationScope() {
    }

    public WorldviewValidationScope(Worldview worldview) {
        for (var ontology : worldview.getOntologies()) {
            for (var statement : ontology.getStatements()) {
                loadConcepts(statement, ontology.getUrn());
            }
        }
    }

    public void clearNamespace(String namespace) {
        Set<String> keys = new HashSet<>();
        String ns = namespace + ":";
        for (var concept : conceptTypes.keySet()) {
            if (concept.startsWith(ns)) {
                keys.add(concept);
            }
        }
        synchronized (conceptTypes) {
            keys.forEach(key -> conceptTypes.remove(key));
        }
    }

    public void addNamespace(KimOntology ontology) {
        for (var statement : ontology.getStatements()) {
            loadConcepts(statement, ontology.getUrn());
        }
    }

    @Override
    public ConceptDescriptor createConceptDescriptor(ConceptDeclarationSyntax declaration) {
        // trust the "is core" to define the type for all core ontology concepts

        if (declaration.isCoreDeclaration()) {
            SemanticSyntax coreConcept = declaration.getDeclaredParent();
            var coreId = coreConcept.encode();
            var cname = coreConcept.encode().split(":");

            this.conceptTypes.put(coreConcept.encode(), new ConceptDescriptor(cname[0], cname[1],
                    declaration.getDeclaredType(), coreConcept.encode(),
                    "Core concept " + coreConcept.encode() + " for type " + declaration.getDeclaredType(),
                    true));
        }
        return super.createConceptDescriptor(declaration);
    }

    private void loadConcepts(KimConceptStatement statement, String namespace) {
        String defaultLabel = namespace + ":" + statement.getUrn();
        ConceptDescriptor descriptor = new ConceptDescriptor(namespace, statement.getUrn(),
                getMainType(statement.getType()), statement.getMetadata().get(Metadata.DC_LABEL,
                defaultLabel), statement.getMetadata().get(Metadata.DC_COMMENT, String.class),
                statement.isAbstract());
        this.conceptTypes.put(defaultLabel, descriptor);
        for (var child : statement.getChildren()) {
            loadConcepts(child, namespace);
        }
    }

    /**
     * Turn the K.IM main semantic typeset into its syntactic equivalent. No VOID is allowed if a worldview is
     * loaded.
     *
     * @param type
     * @return
     */
    public static SemanticSyntax.Type getMainType(Set<SemanticType> type) {
        Set<SemanticType> strippedType = EnumSet.copyOf(type);
        strippedType.retainAll(SemanticType.DECLARABLE_TYPES);
        if (strippedType.isEmpty() && type.contains(SemanticType.QUALITY)) {
            return SemanticSyntax.Type.GENERIC_QUALITY;
        } else if (strippedType.size() == 1) {
            return switch (strippedType.iterator().next()) {
                case PROPORTION -> SemanticSyntax.Type.PROPORTION;
                case PROBABILITY -> SemanticSyntax.Type.PROBABILITY;
                case DISTANCE, LENGTH -> SemanticSyntax.Type.LENGTH;
                case VALUE -> SemanticSyntax.Type.VALUE;
                case OCCURRENCE -> SemanticSyntax.Type.OCCURRENCE;
                case PRESENCE -> SemanticSyntax.Type.PRESENCE;
                case UNCERTAINTY -> SemanticSyntax.Type.UNCERTAINTY;
                case NUMEROSITY -> SemanticSyntax.Type.NUMEROSITY;
                case RATE -> SemanticSyntax.Type.RATE;
                case CLASS -> SemanticSyntax.Type.CLASS;
                case QUANTITY -> SemanticSyntax.Type.QUANTITY;
                case ENERGY -> SemanticSyntax.Type.ENERGY;
                case ENTROPY -> SemanticSyntax.Type.ENTROPY;
                case ROLE -> SemanticSyntax.Type.ROLE;
                case EXTENT -> SemanticSyntax.Type.EXTENT;
                case MONETARY_VALUE -> SemanticSyntax.Type.MONETARY_VALUE;
                case DOMAIN -> SemanticSyntax.Type.DOMAIN;
                case MASS -> SemanticSyntax.Type.MASS;
                case VOLUME -> SemanticSyntax.Type.VOLUME;
                case WEIGHT -> SemanticSyntax.Type.WEIGHT;
                case MONEY -> SemanticSyntax.Type.MONEY;
                case DURATION -> SemanticSyntax.Type.DURATION;
                case AREA -> SemanticSyntax.Type.AREA;
                case ACCELERATION -> SemanticSyntax.Type.ACCELERATION;
                case PRIORITY -> SemanticSyntax.Type.PRIORITY;
                case ELECTRIC_POTENTIAL -> SemanticSyntax.Type.ELECTRIC_POTENTIAL;
                case CHARGE -> SemanticSyntax.Type.CHARGE;
                case RESISTANCE -> SemanticSyntax.Type.RESISTANCE;
                case RESISTIVITY -> SemanticSyntax.Type.RESISTIVITY;
                case PRESSURE -> SemanticSyntax.Type.PRESSURE;
                case ANGLE -> SemanticSyntax.Type.ANGLE;
                case VELOCITY -> SemanticSyntax.Type.VELOCITY;
                case TEMPERATURE -> SemanticSyntax.Type.TEMPERATURE;
                case VISCOSITY -> SemanticSyntax.Type.VISCOSITY;
                case RATIO -> SemanticSyntax.Type.RATIO;
                case AMOUNT -> SemanticSyntax.Type.AMOUNT;
                case SUBJECT -> SemanticSyntax.Type.SUBJECT;
                case AGENT -> SemanticSyntax.Type.AGENT;
                case EVENT -> SemanticSyntax.Type.EVENT;
                case RELATIONSHIP -> SemanticSyntax.Type.FUNCTIONAL_RELATIONSHIP;
                case PROCESS -> SemanticSyntax.Type.PROCESS;
                case CONFIGURATION -> SemanticSyntax.Type.CONFIGURATION;
                case ATTRIBUTE -> SemanticSyntax.Type.ATTRIBUTE;
                case REALM -> SemanticSyntax.Type.REALM;
                case IDENTITY -> SemanticSyntax.Type.IDENTITY;
                case ORDERING -> SemanticSyntax.Type.ORDERING;
                default -> SemanticSyntax.Type.NOTHING;
            };
        }
        // No VOID admitted if we have a worldview
        return SemanticSyntax.Type.NOTHING;
    }

}
