package org.integratedmodelling.klab.modeler.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.kim.impl.*;

class NavigableObserverTransportTest {
  @Test void navigableModelRetainsBeanAndDocumentTraversalIncludesObserver()throws Exception {
    var observer=new KimConceptImpl();observer.setName("demo:Observer");
    var semantics=new KimConceptImpl();semantics.setName("demo:Height");
    var observable=new KimObservableImpl();observable.setObserverSemantics(observer);observable.setSemantics(semantics);
    var model=new KimModelImpl();model.setUrn("test");model.setObservables(List.of(observable));
    var navigable=new NavigableKimModel(model,null);
    assertSame(observer,navigable.getObservables().getFirst().getObserverSemantics());
    var namespace=new KimNamespaceImpl();namespace.setUrn("test.observers");namespace.setStatements(List.of(model));
    var document=new NavigableKimNamespace(namespace,null);
    var nested=NavigableKlabDocument.class.getDeclaredMethod("nestedStatements",Statement.class);nested.setAccessible(true);
    var children=(List<?>)nested.invoke(document,observable);
    assertEquals(List.of(semantics,observer),children);
  }
}
