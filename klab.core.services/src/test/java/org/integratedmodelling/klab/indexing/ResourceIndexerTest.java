package org.integratedmodelling.klab.indexing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResourceIndexerTest {

  private final ResourceIndexer index = ResourceIndexer.create();

  @AfterEach
  void closeIndex() {
    index.ensureClosed();
  }

  @Test
  void ranksIdentifiersAndNamesBeforeDescriptionsAndSupportsTypeAhead() {
    var identifierMatch = resource("local:alice:climate:temperature", "Air temperature", "Daily values", "netcdf");
    var descriptionMatch = resource("local:alice:climate:stations", "Weather stations", "Temperature observations", "csv");

    index.index(descriptionMatch, info(descriptionMatch.getUrn(), "bob"));
    index.index(identifierMatch, info(identifierMatch.getUrn(), "alice"));
    index.commitChanges();

    var matches = index.query("temp", 10);
    assertEquals(2, matches.size());
    assertEquals(identifierMatch.getUrn(), matches.getFirst().getId());
    assertTrue(matches.getFirst().getScore() > matches.getLast().getScore());
  }

  @Test
  void searchesOwnerAdapterAndMetadataAndReplacesUpdatedDocuments() {
    var resource = resource("local:alice:hydrology:flow", "River discharge", "Stream flow", "geotiff");
    resource.getMetadata().put(Metadata.IM_KEYWORDS, "catchment watershed");
    index.index(resource, info(resource.getUrn(), "submitted-by-alice"));
    index.commitChanges();

    assertEquals(resource.getUrn(), index.query("submitted-by-al", 10).getFirst().getId());
    assertEquals(resource.getUrn(), index.query("geotif", 10).getFirst().getId());
    assertEquals(resource.getUrn(), index.query("watersh", 10).getFirst().getId());

    resource.getMetadata().put(Metadata.DC_TITLE, "Aquifer levels");
    resource.getMetadata().put(Metadata.DC_DESCRIPTION, "Groundwater monitoring");
    index.index(resource, info(resource.getUrn(), "carol"));
    index.commitChanges();

    assertTrue(index.query("submitted-by-alice", 10).isEmpty());
    assertEquals(1, index.query("aquifer", 10).size());
  }

  @Test
  void treatsLuceneSyntaxAsPlainTypeAheadText() {
    var resource = resource("local:alice:test:safe", "Safe resource", "A searchable entry", "csv");
    index.index(resource, info(resource.getUrn(), "alice"));
    index.commitChanges();

    // Must not throw a QueryParser exception for partially typed punctuation.
    assertTrue(index.query("safe:(", 10).stream().anyMatch(match -> resource.getUrn().equals(match.getId())));
  }

  private static ResourceImpl resource(
      String urn, String title, String description, String adapter) {
    var ret = new ResourceImpl();
    ret.setUrn(urn);
    ret.setLocalName(urn.substring(urn.lastIndexOf(':') + 1));
    ret.setAdapterType(adapter);
    ret.getMetadata().put(Metadata.DC_TITLE, title);
    ret.getMetadata().put(Metadata.DC_DESCRIPTION, description);
    return ret;
  }

  private static ResourceInfo info(String urn, String owner) {
    var ret = ResourceInfo.immediate();
    ret.setUrn(urn);
    ret.setOwner(owner);
    return ret;
  }
}
