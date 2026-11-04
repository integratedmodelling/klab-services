package org.integratedmodelling.klab.runtime.lifecycle;

import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Server-local bridge from an admitted native transaction to its frozen execution scope. */
public interface CycleScopedTransaction {
  ServiceContextScope executionScope();
}
