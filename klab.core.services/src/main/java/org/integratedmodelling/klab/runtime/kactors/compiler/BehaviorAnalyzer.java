package org.integratedmodelling.klab.runtime.kactors.compiler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.AgentBase;

/** Analyze the behavior and collect infomation for code generation. */
public class BehaviorAnalyzer {

  public record ActionInfo(
      String name, Verb.Type executionType, List<VariableInfo> parameters, BlockInfo body) {}

  public record ImportInfo(String name, Class<?> javaClass) {}

  public record BlockInfo(
      long id,
      List<VariableInfo> variables,
      List<BlockInfo> blocks,
      List<ExpressionInfo> expressions) {}

  public record VariableInfo(String name) {}

  public record ExpressionInfo(String expression, Expression.Descriptor descriptor) {}

  private final List<Notification> notifications = new ArrayList<>();
  private final Map<String, ActionInfo> actions = new LinkedHashMap<>();
  private final List<VariableInfo> fields = new ArrayList<>();
  private final List<ImportInfo> imports = new ArrayList<>();
  private Class<? extends AgentBase> agentClass = AgentBase.class;

  public BehaviorAnalyzer(KActorsBehavior behavior) {
    analyze(behavior);
  }

  private void analyze(KActorsBehavior behavior) {}

  public Map<String, ActionInfo> getActions() {
    return actions;
  }

  public List<VariableInfo> getFields() {
    return fields;
  }

  public Class<? extends AgentBase> getAgentClass() {
    return agentClass;
  }

  public List<Notification> getNotifications() {
    return notifications;
  }

  public List<ImportInfo> getImports() {
    return imports;
  }
}
