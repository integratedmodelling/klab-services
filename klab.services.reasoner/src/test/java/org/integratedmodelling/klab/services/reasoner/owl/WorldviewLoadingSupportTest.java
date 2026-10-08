package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.WorldviewLoadingSupport;
import org.junit.jupiter.api.Test;

class WorldviewLoadingSupportTest {
  @Test void updatedAuthorityDeclarationsReplaceStartupSnapshotWithoutReorderingOntologies() {
    var worldview = new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();
    var root = new KimOntologyImpl(); root.setUrn("imod");
    var old = new KimOntologyImpl(); old.setUrn("life");
    var previous = new KimConceptStatementImpl();
    previous.setUrn("TaxonomicIdentity");
    previous.setAuthorityRequired("TAXA");
    previous.setAuthorityParameters(java.util.Map.of("urn", "test.authority", "codelists",
        java.util.Map.of("species", "old.species")));
    old.getStatements().add(previous);
    worldview.setOntologies(List.of(root, old));
    var saved = new KimOntologyImpl(); saved.setUrn("life");
    var declaration = new KimConceptStatementImpl();
    declaration.setUrn("TaxonomicIdentity");
    declaration.setAuthorityRequired("TAXA");
    declaration.setAuthorityParameters(java.util.Map.of("urn", "test.authority", "codelists",
        java.util.Map.of("species", "taxonomy.species")));
    saved.getStatements().add(declaration);
    WorldviewLoadingSupport.replaceOntology(worldview, saved);
    assertEquals(List.of(root, saved), worldview.getOntologies());
    assertSame(declaration, worldview.allConceptStatements().stream()
        .filter(s -> "TAXA".equals(s.getAuthorityRequired())).findFirst().orElseThrow());
    var removed = new KimOntologyImpl(); removed.setUrn("life");
    WorldviewLoadingSupport.replaceOntology(worldview, removed);
    assertTrue(worldview.allConceptStatements().isEmpty());
    var added = new KimOntologyImpl(); added.setUrn("chemistry");
    WorldviewLoadingSupport.replaceOntology(worldview, added);
    assertEquals(List.of(root, removed, added), worldview.getOntologies());
  }

  @Test void warningsDoNotBlockLoadingAndLocalErrorsRemainEditable() {
    var worldview = mock(Worldview.class);
    var root = new KimOntologyImpl(); root.setUrn("imod");
    var earth = new KimOntologyImpl(); earth.setUrn("earth"); earth.setDomain(new KimConceptImpl());
    root.getNotifications().add(Notification.warning("Denial semantics not yet compiled"));
    when(worldview.getOntologies()).thenReturn(List.of(root, earth));
    assertTrue(WorldviewLoadingSupport.loadable(worldview, false));
    assertTrue(WorldviewLoadingSupport.loadable(worldview, true));
    var source = new KimConceptImpl(); source.setOffsetInDocument(16528); source.setLength(25);
    earth.getNotifications().add(Notification.error("Unknown concept", Notification.LexicalContext.of(source, earth)));
    when(worldview.isEmpty()).thenReturn(true);
    assertFalse(WorldviewLoadingSupport.loadable(worldview, false));
    assertTrue(WorldviewLoadingSupport.loadable(worldview, true));
    var diagnostics = WorldviewLoadingSupport.diagnostics(worldview);
    assertEquals(2, diagnostics.size());
    assertEquals("earth at 16528: Unknown concept", WorldviewLoadingSupport.errorSummary(diagnostics));
    assertEquals(Notification.Level.Warning, root.getNotifications().iterator().next().getLevel());
  }

  @Test void missingRootCannotBeRecoveredByLocalAuthoring() {
    assertFalse(WorldviewLoadingSupport.loadable(null, true));
    var worldview = mock(Worldview.class);
    assertFalse(WorldviewLoadingSupport.loadable(worldview, true));
    var nonRoot = new KimOntologyImpl(); nonRoot.setDomain(new KimConceptImpl());
    when(worldview.getOntologies()).thenReturn(List.of(nonRoot));
    assertFalse(WorldviewLoadingSupport.loadable(worldview, true));
  }
}
