package org.integratedmodelling.klab.runtime.kactors.compiler;

import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.lang.model.element.Modifier;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.runtime.kactors.AgentBase;

public class AgentCompiler {

  // TODO URN should contain the version/build number (with build == timestamp)
  private static Map<String, Class<? extends AgentBase>> compiledActorClasses =
      new ConcurrentHashMap<>();

  private KActorsBehavior behavior;
  private UserScope scope;
  private String packageName = "org.integratedmodelling.klab.runtime.kactors.generated";
  private String sourceCode;
  private BehaviorAnalyzer analyzer;

  public AgentCompiler(String behaviorUrn, UserScope scope) {
    this.scope = scope;
    // TODO use all services
    this.behavior =
        scope
            .getService(ResourcesService.class)
            .retrieve(behaviorUrn, KActorsBehavior.class, scope);
  }

  public AgentCompiler(KActorsBehavior behavior, UserScope scope) {
    this.scope = scope;
    this.behavior = behavior;
  }

  /**
   * For testing only!
   *
   * @param behavior
   */
  public AgentCompiler(KActorsBehavior behavior) {
    this.scope = null;
    this.behavior = behavior;
    this.analyzer = new BehaviorAnalyzer(behavior);
  }

  public boolean compile() {

    if (!analyzer.analyze()) {
      return false;
    }

    // TODO use versions intelligently. All versions should have the timestamp of the behavior.
    // TODO store the behavior's last update timestamp in a separate hash and re-compile if it's
    //  different.
    var existing = compiledActorClasses.get(behavior.getUrn());

    if (existing != null) {
      return true;
    }

    var compiled = compileBehavior(behavior);
    if (compiled != null) {
      compiledActorClasses.put(behavior.getUrn(), compiled);
      return true;
    }

    return false;
  }

  /// Pattern for the compiler:
  /// 1. Choose the base class and analyze the execution mode
  /// 2. Override the necessary methods and the constructor
  /// 3. In the constructor after calling super(),add any global state to the root scope
  ///
  /// First pass should ensure that all identifiers and verbs are defined; build a catalog
  /// of reactive forms and assign IDs to them proactively. Inferrable return types should be
  /// remembered for parameter matching. Actions that return from the main thread are functions;
  /// actions that return from a reactor body are suppliers. Any fire call makes the action an
  /// emitter. These determine the execution mode of the action. The analysis should build the
  /// same data structure for a k.Actors action that a @Verb annotation provides.
  ///
  /// Compilation pass: for each action:
  ///   Compile each statement and add the code to a temporary buffer.
  ///     When statement is a reactive message call:
  ///         create a temporary buffer for the reactor setup code
  ///         assign action ID for the reactor scope, add scope creation to setup buffer
  ///         listener setup: call onEvent(scope, handler, EventType...) to install a scoped
  // subscriber
  ///           compile the reaction body within the subscription closure
  ///         add the call in asyncRun or completable future consequence to the main code buffer
  ///
  private Class<? extends AgentBase> compileBehavior(KActorsBehavior behavior) {

    var classFile = generateClass(behavior);
    if (classFile != null) {
      Logging.INSTANCE.info("Generated class: " + classFile.typeSpec().name());
      sourceCode = classFile.toString();
      // TODO compile, load and return the class
    }

    return null;
  }

  /**
   * After successful compilation, the source code can be retrieved.
   *
   * @return
   */
  public String getSourceCode() {
    return sourceCode;
  }

  private JavaFile generateClass(KActorsBehavior behavior) {

    var className = Utils.CamelCase.toUpperCamelCase(behavior.getUrn(), '.');
    var packageName = "org.integratedmodelling.klab.runtime.kactors.generated";
    List<MethodSpec> methods = new ArrayList<>();
    List<FieldSpec> fields = new ArrayList<>();

    if (Utils.Notifications.hasErrors(analyzer.getNotifications())) {
      // TODO notify somehow
      return null;
    }

    var classSpec =
        TypeSpec.classBuilder(className)
            .superclass(analyzer.getAgentClass())
            .addFields(fields)
            .addMethods(methods)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .build();

    return JavaFile.builder(packageName, classSpec).build();
  }
}
