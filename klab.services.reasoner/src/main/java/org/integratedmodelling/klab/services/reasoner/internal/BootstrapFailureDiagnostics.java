package org.integratedmodelling.klab.services.reasoner.internal;

import org.slf4j.LoggerFactory;

/** Server-only failure evidence. Never logs provider messages, parameters, or response bodies. */
final class BootstrapFailureDiagnostics {
  enum Phase { RESOLVE_PLAN, PROVIDER_INPUT, PRODUCE, VERIFY_PROOF }
  record Context(Phase phase, String inputHash, String planHash, String bindingsHash, java.util.List<String> components, int bindingCount) {}
  @FunctionalInterface interface Reporter { void report(Context context, Throwable original); }

  static void report(Context context, Throwable original) {
    // Use SLF4J's Throwable overload directly: Logging.error(Object...) drops stack traces.
    LoggerFactory.getLogger(AuthorityBootstrap.class).error(
        "AUTHORITY_BOOTSTRAP_FAILURE phase=" + context.phase()
            + " input=" + context.inputHash() + " plan=" + context.planHash()
            + " providers=" + context.bindingsHash() + " components=" + context.components() + " bindings=" + context.bindingCount(), snapshot(original));
  }

  static Throwable snapshot(Throwable original) {
    return org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics.snapshot(original);
  }
}
