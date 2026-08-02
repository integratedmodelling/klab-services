//package org.integratedmodelling.klab.runtime.kactors;
//
//import java.util.*;
//
//import org.integratedmodelling.klab.api.lang.Annotation;
//import org.integratedmodelling.klab.api.scope.ContextScope;
//
//public abstract class TestScope extends AgentScope {
//
//  // container to register contexts created during a test. Managed by the context agent.
//  private Map<String, Set<ContextScope>> contexts = new HashMap<>();
//  private String currentAction;
//
//  public TestScope(RuntimeAgentBase actor) {
//    super(actor);
//  }
//
//  public TestScope(TestScope parent, long actionId) {
//    super(parent, actionId);
//  }
//
//  public void registerContext(ContextScope context) {
//    contexts.computeIfAbsent(currentAction, k -> new HashSet<>()).add(context);
//  }
//
//  @Override
//  public void beforeAction(String actionName, List<Annotation> annotations) {
//    // ACHTUNG we rely on the fact that tests are executed sequentially. If not, we need a new
//    // scope per action.
//    this.currentAction = actionName;
//    super.beforeAction(actionName, annotations);
//  }
//
//  @Override
//  public void afterAction(String actionName, List<Annotation> annotations) {
//    var contexts = this.contexts.get(currentAction);
//    if (contexts != null) {
//      contexts.forEach(ContextScope::close);
//    }
//    super.afterAction(actionName, annotations);
//  }
//}
