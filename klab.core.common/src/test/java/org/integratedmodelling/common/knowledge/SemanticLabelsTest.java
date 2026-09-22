package org.integratedmodelling.common.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;

class SemanticLabelsTest {
  private ConceptImpl concept(String name) {
    var concept = new ConceptImpl(); concept.setName(name); concept.setNamespace("earth");
    concept.setUrn("earth:" + name); concept.setReferenceName("earth_" + name.toLowerCase());
    concept.getType().add(SemanticType.SUBJECT); return concept;
  }
  private KimConceptImpl syntax(String name) {
    var syntax = new KimConceptImpl(); syntax.setName("earth:" + name); return syntax;
  }

  @Test void collectiveLabelsPluralizeWithoutChangingSingularOrReferenceIdentifiers() {
    var region = concept("Region");
    region.getMetadata().put(Metadata.DISPLAY_LABEL, "Terrestrial region");
    var collective = region.collective();
    assertEquals("Terrestrial regions", collective.displayLabel());
    assertEquals("Terrestrial regions", collective.displayName());
    assertEquals("Terrestrial region", region.displayLabel());
    assertEquals(region.displayLabel(), collective.singular().displayLabel());
    assertEquals("each_earth_region", collective.getReferenceName());
    assertEquals("Region", collective.getName());
    assertEquals("region", collective.codeName());
    assertEquals("Children of a region", SemanticLabels.plural("Child of a region"));
    assertEquals("Species", SemanticLabels.plural("Species"));
  }

  @Test void derivedSnapshotsNeverExposeInternalIdsWhenDefinitionIsAvailable() {
    var derived = concept("N1234"); derived.setNamespace("k.derived");
    derived.setUrn("earth:Terrestrial earth:Region");
    assertEquals("Terrestrial region", derived.displayLabel());
    assertEquals("Terrestrial regions", derived.collective().displayLabel());
    derived.setUrn("presence of earth:Region");
    assertEquals("Presence of region", derived.displayLabel());
    derived.getMetadata().put(Metadata.RDFS_LABEL, "K_DERIVED_000000123Presence");
    assertEquals("Presence of region", derived.displayLabel());
    derived.setUrn("k.derived:1234");
    assertEquals("Concept", derived.displayLabel());
  }

  @Test void unaryOperatorsUseDescriptiveEnglishAndRetainComparison() {
    assertEquals("Presence of region", SemanticLabels.unary(UnarySemanticOperator.PRESENCE, "Region", null));
    assertEquals("Number of regions", SemanticLabels.unary(UnarySemanticOperator.COUNT, "Region", null));
    assertEquals("Rate of change of temperature", SemanticLabels.unary(UnarySemanticOperator.RATE, "Temperature", null));
    assertEquals("Ratio of forest area to land area", SemanticLabels.unary(UnarySemanticOperator.RATIO, "Forest area", "Land area"));
    for (var operator : UnarySemanticOperator.values()) {
      assertFalse(SemanticLabels.unary(operator, "Region", null).isBlank());
    }
  }

  @Test void syntaxUsesLabelsForTraitsInherencyCollectivesAndValueOperators() {
    var region = syntax("Region"); region.getTraits().add(syntax("Terrestrial")); region.setCollective(true);
    java.util.function.Function<String, Concept> resolve = urn -> concept(urn.substring(urn.indexOf(':') + 1));
    assertEquals("Terrestrial regions", SemanticLabels.describe(region, resolve, true));
    var temperature = syntax("Temperature"); temperature.setInherent(region);
    temperature.getValueOperators().add(Pair.of(ValueOperator.GREATER, 20));
    assertEquals("Temperature of terrestrial regions greater than 20", SemanticLabels.describe(temperature, resolve, true));
    var presence = new KimConceptImpl(); presence.setObservable(region);
    presence.setSemanticModifier(UnarySemanticOperator.PRESENCE);
    assertEquals("Presence of terrestrial regions", SemanticLabels.describe(presence, resolve, true));
  }

  @Test void observableAndObservationMetadataCarryDefaultsAndExplicitNamesWin() {
    var region = concept("Region").collective();
    var observable = ObservableImpl.promote(region, null);
    var observation = new ObservationImpl(); observation.setObservable(observable);
    assertEquals("Regions", observation.getName());
    assertEquals("Regions", observable.displayName());
    assertEquals("Regions", observable.getMetadata().get(Metadata.SUGGESTED_NAME));
    assertEquals("Regions", observation.getMetadata().get(Metadata.SUGGESTED_NAME));
    assertEquals("region", observable.getName());
    assertEquals("each_earth_region", observable.getReferenceName());
    observable.setStatedName("Survey regions");
    assertEquals("Survey regions", observation.getName());
    observation.getMetadata().put(Metadata.DC_LABEL, "Mapped regions");
    assertEquals("Mapped regions", observation.getName());
    observation.setName("My sample");
    assertEquals("My sample", observation.getName());
    assertEquals("Survey regions", observation.getMetadata().get(Metadata.SUGGESTED_NAME));
    assertEquals("Mapped regions", observation.getMetadata().get(Metadata.DC_LABEL));
  }

  @Test void serviceRoundTripRetainsLabelsForDerivedCollectives() throws Exception {
    var concept = concept("N1234"); concept.setNamespace("k.derived");
    concept.setUrn("earth:Terrestrial earth:Region");
    concept.getMetadata().put(Metadata.DISPLAY_LABEL, "Terrestrial region");
    var observation = new ObservationImpl(); observation.setObservable(ObservableImpl.promote(concept.collective(), null));
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var remote = mapper.readValue(mapper.writeValueAsString(observation),
        org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
    assertEquals("Terrestrial regions", remote.getName());
    assertEquals("Terrestrial regions", remote.getMetadata().get(Metadata.SUGGESTED_NAME));
    assertEquals("Terrestrial regions", remote.getObservable().getMetadata().get(Metadata.SUGGESTED_NAME));
    assertEquals("Terrestrial regions", remote.getObservable().getSemantics().displayLabel());
  }

  @Test void identityNamesRemainPreferredAndMetadataRefreshesWithSemantics() {
    var observable = ObservableImpl.promote(concept("Region"), null);
    var observation = new ObservationImpl(); observation.setObservable(observable);
    observation.setUrn("survey:plot1");
    assertEquals("plot1", observation.getName());
    assertEquals("Region", observation.getMetadata().get(Metadata.SUGGESTED_NAME));
    observable.setSemantics(concept("Tree").collective());
    assertEquals("Trees", observation.getMetadata().get(Metadata.SUGGESTED_NAME));
  }
}
