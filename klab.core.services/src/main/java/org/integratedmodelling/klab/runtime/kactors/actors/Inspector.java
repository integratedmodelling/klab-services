package org.integratedmodelling.klab.runtime.kactors.actors;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.extension.ActionParameter;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.TestScope;

/**
 * TODO TBI Any actors that are Closeable are created explicitly as variables in each action inside
 *  a try-with-resources block, and not as fields in the construction.
 */
@Actor(name = "inspector")
public class Inspector implements Closeable {

  public Inspector(TestScope testScope, SessionScope sessionScope) {}

  @Verb
  public void record(@ActionParameter Map<String, Object> events) {}

  @Override
  public void close() throws IOException {}
}
