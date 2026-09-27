package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.lang.AnnotationImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.junit.jupiter.api.Test;

class ConceptCodelistBuilderTest {

  @Test
  void collectsDescendantCodesByAuthorityAndCarriesKimConceptValues() {
    var root = statement("landcover", "LandCoverType", null);
    root.getAnnotations().add(code("corine", 999));
    var pasture = statement("landcover", "Pastures", "Pasture land");
    pasture.getAnnotations().add(code("corine", 231));
    pasture.getAnnotations().add(code("alternate", 7L));
    var nested = statement("landcover", "NaturalPastures", "Natural pasture");
    nested.getAnnotations().add(code("corine", 321));
    pasture.getChildren().add(nested);
    root.getChildren().add(pasture);

    var codelist =
        ConceptCodelistBuilder.build(
            "landcover:LandCoverType",
            root,
            "resources-one",
            urn -> {
              var concept = new KimConceptImpl();
              concept.setUrn(urn);
              return concept;
            });

    assertEquals(3, codelist.size());
    assertEquals(2, codelist.getAuthorityIds().size());
    assertEquals("landcover:Pastures",
        ((org.integratedmodelling.klab.api.lang.kim.KimConcept)
            codelist.value("corine", 231.0)).getUrn());
    assertEquals("Natural pasture", codelist.getDescription("corine", 321));
    assertNull(codelist.value("corine", 999));
    assertEquals("resources-one", codelist.getServiceId());
  }

  @Test
  void derivedCodelistSurvivesJsonTransport() throws Exception {
    var root = statement("landcover", "LandCoverType", null);
    var pasture = statement("landcover", "Pastures", "Pasture land");
    pasture.getAnnotations().add(code("corine", 231));
    root.getChildren().add(pasture);
    var codelist =
        ConceptCodelistBuilder.build(
            "landcover:LandCoverType",
            root,
            "resources-one",
            urn -> {
              var concept = new KimConceptImpl();
              concept.setUrn(urn);
              return concept;
            });

    var mapper = JacksonConfiguration.newObjectMapper();
    var copy =
        mapper.readValue(
            mapper.writerFor(org.integratedmodelling.klab.api.knowledge.Codelist.class)
                .writeValueAsString(codelist),
            org.integratedmodelling.klab.api.knowledge.Codelist.class);
    assertEquals("landcover:Pastures",
        ((org.integratedmodelling.klab.api.lang.kim.KimConcept)
            copy.value("corine", 231)).getUrn());
    assertEquals("Pasture land", copy.getDescription("corine", 231));
  }

  @Test
  void rejectsDuplicateCodesWithinTheSameHierarchyAndAuthority() {
    var root = statement("landcover", "LandCoverType", null);
    var first = statement("landcover", "First", null);
    var second = statement("landcover", "Second", null);
    first.getAnnotations().add(code("corine", 1));
    second.getAnnotations().add(code("corine", 1));
    root.getChildren().add(first);
    root.getChildren().add(second);

    assertThrows(
        KlabIllegalArgumentException.class,
        () ->
            ConceptCodelistBuilder.build(
                "landcover:LandCoverType", root, "resources-one", urn -> new KimConceptImpl()));
  }

  private static KimConceptStatementImpl statement(
      String namespace, String name, String description) {
    var result = new KimConceptStatementImpl();
    result.setNamespace(namespace);
    result.setUrn(name);
    result.setDocstring(description);
    return result;
  }

  private static AnnotationImpl code(String scheme, Number value) {
    var result = new AnnotationImpl();
    result.setName("code");
    result.putUnnamed(scheme);
    result.putUnnamed(value);
    return result;
  }
}
