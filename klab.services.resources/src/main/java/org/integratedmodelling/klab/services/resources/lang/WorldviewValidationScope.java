package org.integratedmodelling.klab.services.resources.lang;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;

import java.util.EnumSet;
import java.util.Set;

/**
 * Worldview-aware semantic validation scope
 */
public class WorldviewValidationScope extends BasicObservableValidationScope {

    public WorldviewValidationScope(Worldview worldview) {
        for (var ontology : worldview.getOntologies()) {
            for (var statement : ontology.getStatements()) {
                loadConcepts(statement, ontology.getUrn());
            }
        }
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
        type = EnumSet.copyOf(type);
        type.retainAll(SemanticType.DECLARABLE_TYPES);
        if (type.size() == 1) {
            return switch (type.iterator().next()) {
                case PROPORTION -> SemanticSyntax.Type.PROPORTION;
                case PROBABILITY -> SemanticSyntax.Type.PROBABILITY;
                case DISTANCE -> SemanticSyntax.Type.LENGTH;
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
                case LENGTH -> SemanticSyntax.Type.LENGTH;
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
