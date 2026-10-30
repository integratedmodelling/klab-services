package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.junit.jupiter.api.Test;

class SemanticExecutionFootprintsTest {
  public static class OtherFixed {
    public static void fixed(org.integratedmodelling.klab.api.data.Data.Builder builder,
        org.integratedmodelling.klab.api.geometry.Geometry geometry,
        org.integratedmodelling.klab.api.knowledge.Observable observable,
        org.integratedmodelling.klab.api.scope.ContextScope scope) {}
  }

  @Test void fixedFixtureFootprintRequiresExactCoreMethodAndFunctionBinding() throws Exception {
    var parameters = new Class<?>[] {org.integratedmodelling.klab.api.data.Data.Builder.class,
        org.integratedmodelling.klab.api.geometry.Geometry.class,
        org.integratedmodelling.klab.api.knowledge.Observable.class,
        org.integratedmodelling.klab.api.scope.ContextScope.class};
    var actual = org.integratedmodelling.klab.runtime.libraries.NativeTestFixtures.class.getMethod("fixed", parameters);
    var call = mock(org.integratedmodelling.klab.api.lang.ServiceCall.class);
    when(call.getUrn()).thenReturn("klab.tests.fixtures.fixed");
    var scope = mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);
    assertTrue(SemanticExecutionFootprints.function(null, actual, null, call, scope));
    assertFalse(SemanticExecutionFootprints.function(null, OtherFixed.class.getMethod("fixed", parameters), null, call, scope));
    when(call.getUrn()).thenReturn("klab.tests.fixtures.empty");
    assertFalse(SemanticExecutionFootprints.function(null, actual, null, call, scope));
  }

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
