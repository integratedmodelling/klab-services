package org.integratedmodelling.klab.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class TemporalDeliveryTest {
  @Test void unavailableMessagingCannotRollBackACommittedTransition() throws Exception {
    var scope=mock(ServiceContextScope.class);
    var tx=mock(DigitalTwin.Transaction.class);
    var field=ServiceContextScope.class.getDeclaredField("currentTransaction");field.setAccessible(true);field.set(scope,tx);
    var activity=new ActivityImpl();activity.setType(Activity.Type.SIMULATION);
    when(scope.getActivity()).thenReturn(activity);when(tx.commit()).thenReturn(42L);
    when(scope.send(any(Object[].class))).thenThrow(new IllegalStateException("broker unavailable"));
    when(scope.commit()).thenCallRealMethod();
    assertEquals(42,scope.commit());assertEquals(Activity.Outcome.SUCCESS,activity.getOutcome());
    verify(tx,never()).fail(any(Throwable.class));
  }
}
