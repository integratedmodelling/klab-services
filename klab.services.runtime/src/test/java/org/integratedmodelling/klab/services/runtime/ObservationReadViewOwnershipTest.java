package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.junit.jupiter.api.*;

class ObservationReadViewOwnershipTest {
  @BeforeAll static void configure() { CollectiveCompletionQueryTest.configure(); }

  @Test void immutableViewBelongsOnlyToItsActualOpenRootAndChildren() throws Exception {
    var f = new CollectiveCompletionQueryTest(); f.setup();
    var other = new CollectiveCompletionQueryTest(); other.setup();
    var view = f.root.readObservation(f.producer);
    assertTrue(f.root.ownsObservationReadView(view));
    var child = f.root.getChild(Activity.of(Activity.Type.RESOLUTION), f.scope);
    assertTrue(((org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl)child)
        .ownsObservationReadView(view));
    assertFalse(other.root.ownsObservationReadView(view));
    assertTrue(child.commit() >= 0);
    assertTrue(f.root.commit() > 0);
    assertFalse(f.root.ownsObservationReadView(view));
  }

  @Test void arbitraryProxyIsRejectedWithoutCallingAnyGetterOrHandler() throws Exception {
    var f = new CollectiveCompletionQueryTest(); f.setup(); var calls = new AtomicInteger();
    var fake = (Observation) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Observation.class},
        (proxy, method, args) -> { calls.incrementAndGet(); throw new AssertionError("untrusted proxy invoked"); });
    assertFalse(f.root.ownsObservationReadView(fake)); assertEquals(0, calls.get());
    assertFalse(f.root.ownsObservationReadView(f.producer));
  }

  @Test void snapshotOfCustomObservationSubclassIsNotAnAuditedDtoGraph() throws Exception {
    var f = new CollectiveCompletionQueryTest(); f.setup();
    var custom = new ObservationImpl() {};
    custom.setId(-99); custom.setObservable(f.producer.getObservable()); custom.setGeometry(f.producer.getGeometry());
    f.root.add(custom);
    assertFalse(f.root.ownsObservationReadView(f.root.readObservation(custom)));
  }
}
