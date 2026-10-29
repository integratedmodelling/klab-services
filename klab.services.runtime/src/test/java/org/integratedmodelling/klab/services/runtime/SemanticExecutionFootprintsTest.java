package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.junit.jupiter.api.Test;

class SemanticExecutionFootprintsTest {
  public static class AuditedBase {
    public void encode() {}
    public static void category() {}
  }
  public static class UnreviewedOverride extends AuditedBase {
    @Override public void encode() { throw new IllegalStateException("unreviewed dispatch"); }
  }

  @Test void inheritedMethodOnSubclassReceiverCannotReuseDeclaringClassAttestation() throws Exception {
    var implementation = new ComponentRegistry.ServiceImplementation();
    implementation.method = AuditedBase.class.getMethod("encode");
    implementation.mainClassInstance = new AuditedBase();
    assertTrue(SemanticExecutionFootprints.exactReceiver(implementation));
    implementation.mainClassInstance = new UnreviewedOverride();
    assertFalse(SemanticExecutionFootprints.exactReceiver(implementation),
        "Method.invoke dispatches the overriding receiver even when Method declares the base class");
  }

  @Test void deferredOrUnknownReceiverCannotClaimActualExecutionFootprint() throws Exception {
    var implementation = new ComponentRegistry.ServiceImplementation();
    implementation.method = AuditedBase.class.getMethod("encode");
    implementation.constructor = AuditedBase.class.getConstructor();
    assertFalse(SemanticExecutionFootprints.exactReceiver(implementation));
    implementation.method = AuditedBase.class.getMethod("category");
    assertTrue(SemanticExecutionFootprints.exactReceiver(implementation), "A static Method has no virtual receiver dispatch");
  }
}
