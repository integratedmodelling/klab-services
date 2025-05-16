package org.integratedmodelling.klab.runtime.kactors.tests;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;

@Actor(name = "inspector")
public class Inspector implements Closeable {

  public Inspector(TestScope testScope, SessionScope sessionScope) {}

  @Verb
  public void record(Map<String, Object> a) {}

  @Override
  public void close() throws IOException {}
}
