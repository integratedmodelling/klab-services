package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.slf4j.LoggerFactory;
import uk.ac.manchester.cs.owlapi.modularity.*;

@ResourceLock("SYSTEM_PROPERTIES")
class ModuleExtractionTraceFilterTest {
  private String previous;
  private LoggerContext context;
  private ListAppender<ILoggingEvent> output;

  @BeforeEach void setup() {
    previous = System.getProperty(ModuleExtractionTraceFilter.PROPERTY);
    System.clearProperty(ModuleExtractionTraceFilter.PROPERTY);
    context = new LoggerContext();
    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(Level.INFO);
    output = new ListAppender<>(); output.setContext(context); output.start();
    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(output);
    ModuleExtractionTraceFilter.install(context);
  }

  @AfterEach void cleanup() {
    context.stop();
    if (previous == null) System.clearProperty(ModuleExtractionTraceFilter.PROPERTY);
    else System.setProperty(ModuleExtractionTraceFilter.PROPERTY, previous);
  }

  @Test void defaultOffRejectsDiagnosticsBeforeFormattingAndPreservesOtherMessages() {
    var logger = context.getLogger(ModuleExtractionTraceFilter.LOGGER);
    logger.setLevel(Level.TRACE);
    var formatted = new AtomicInteger();
    Object annotation = new Object() {
      @Override public String toString() { formatted.incrementAndGet(); return "AnnotationAssertion(...)"; }
    };
    assertFalse(logger.isInfoEnabled()); assertFalse(logger.isDebugEnabled());
    logger.info("Added entity annotation axiom: {}", annotation);
    logger.debug("Local axiom: {}", annotation);
    assertEquals(0, formatted.get()); assertTrue(output.list.isEmpty());
    logger.warn("warning"); logger.error("error");
    context.getLogger("another.library").info("unrelated information");
    assertEquals(java.util.List.of("warning", "error", "unrelated information"),
        output.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
  }

  @Test void explicitTrueEnablesDiagnosticsAndRepeatedInstallationIsIdempotent() {
    var logger = context.getLogger(ModuleExtractionTraceFilter.LOGGER);
    System.setProperty(ModuleExtractionTraceFilter.PROPERTY, "true");
    ModuleExtractionTraceFilter.install(context);
    assertEquals(1, context.getTurboFilterList().size()); assertTrue(logger.isInfoEnabled());
    logger.info("Added entity annotation axiom: {}", "sample");
    assertEquals("Added entity annotation axiom: sample", output.list.getFirst().getFormattedMessage());
    System.setProperty(ModuleExtractionTraceFilter.PROPERTY, "false");
    assertFalse(logger.isInfoEnabled());
  }

  @Test void actualOwlApiExtractionRetainsIdenticalAnnotatedModulesWithGuardOffAndOn() throws Exception {
    var backend = assertInstanceOf(LoggerContext.class, LoggerFactory.getILoggerFactory());
    var logger = backend.getLogger(ModuleExtractionTraceFilter.LOGGER);
    var oldLevel = logger.getLevel(); boolean oldAdditive = logger.isAdditive();
    var captured = new ListAppender<ILoggingEvent>(); captured.setContext(backend); captured.start();
    var oldFilters = Set.copyOf(backend.getTurboFilterList());
    try {
      ModuleExtractionTraceFilter.install();
      logger.setLevel(Level.INFO); logger.setAdditive(false); logger.addAppender(captured);
      var manager = OWLManager.createOWLOntologyManager(); var factory = manager.getOWLDataFactory();
      var a = factory.getOWLClass(IRI.create("urn:test:A")); var b = factory.getOWLClass(IRI.create("urn:test:B"));
      var annotation = factory.getOWLAnnotationAssertionAxiom(factory.getRDFSComment(),
          a.getIRI(), factory.getOWLLiteral("guard test"));
      Set<OWLAxiom> axioms = Set.of(factory.getOWLSubClassOfAxiom(a,b), annotation);
      var disabled = new SyntacticLocalityModuleExtractor(manager, axioms.stream(), ModuleType.BOT).extract(Set.of(a));
      assertTrue(disabled.contains(annotation)); assertTrue(captured.list.isEmpty());
      System.setProperty(ModuleExtractionTraceFilter.PROPERTY, "true");
      var enabled = new SyntacticLocalityModuleExtractor(manager, axioms.stream(), ModuleType.BOT).extract(Set.of(a));
      assertEquals(disabled, enabled);
      assertTrue(captured.list.stream().map(ILoggingEvent::getFormattedMessage)
          .anyMatch(message -> message.contains("Added entity annotation axiom:")));
    } finally {
      logger.detachAppender(captured); captured.stop(); logger.setLevel(oldLevel); logger.setAdditive(oldAdditive);
      backend.getTurboFilterList().removeIf(filter -> !oldFilters.contains(filter)
          && filter instanceof ModuleExtractionTraceFilter);
    }
  }
}
