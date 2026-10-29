package org.integratedmodelling.klab.services.reasoner;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

/** Opt-in OWLAPI module diagnostics; reject before formatting, preserving warnings and errors. */
final class ModuleExtractionTraceFilter extends TurboFilter {
  static final String PROPERTY = "klab.reasoner.traceOwlModules";
  static final String LOGGER =
      "uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor";

  static void install() {
    if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) install(context);
  }

  static void install(LoggerContext context) {
    synchronized (context) {
      if (context.getTurboFilterList().stream().anyMatch(ModuleExtractionTraceFilter.class::isInstance))
        return;
      var filter = new ModuleExtractionTraceFilter();
      filter.setContext(context);
      filter.start();
      context.addTurboFilter(filter);
    }
  }

  @Override
  public FilterReply decide(Marker marker, Logger logger, Level level, String format,
      Object[] arguments, Throwable error) {
    return LOGGER.equals(logger.getName()) && level.toInt() < Level.WARN_INT
            && !Boolean.getBoolean(PROPERTY)
        ? FilterReply.DENY : FilterReply.NEUTRAL;
  }
}
