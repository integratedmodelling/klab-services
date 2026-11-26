import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.collections.DomainObject;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;

/** Run the deployed staging testcase through the same authenticated Runtime agent as the IDE. */
public class VxiiSuiteProfile {
  public static void main(String[] args) {
    try {
      run(args);
      System.exit(0);
    } catch (Exception failure) {
      failure.printStackTrace();
      System.exit(1);
    }
  }

  private static void run(String[] args) throws Exception {
    if (args.length < 1 || args.length > 4)
      throw new IllegalArgumentException("Usage: VxiiSuiteProfile output [full|action] [runs] [testcase.kactors]");
    var directory = Files.createDirectories(Path.of(args[0]));
    var action = args.length > 1 ? args[1] : "full";
    int passes = args.length > 2 ? Integer.parseInt(args[2]) : 1;
    if (passes < 1) throw new IllegalArgumentException("Run count must be positive");
    ServiceConfiguration.injectInstantiators();
    var engine = new EngineImpl(status -> {}, (service, status) -> {});
    engine.boot();
    var user = engine.getOwner();
    if (user == null || user.getUser().isAnonymous())
      throw new IllegalStateException("The staging testcase requires an authenticated owner");
    var runtime = user.getServices(RuntimeService.class).stream()
        .filter(s -> KlabService.Type.RUNTIME.localServiceUrl().equals(s.getUrl()) && s.status().isOperational())
        .findFirst().orElseThrow(() -> new IllegalStateException("Local Runtime is unavailable"));
    var resources = user.getServices(ResourcesService.class).stream()
        .filter(s -> KlabService.Type.RESOURCES.localServiceUrl().equals(s.getUrl()) && s.status().isOperational())
        .findFirst().orElseThrow(() -> new IllegalStateException("Local Resources is unavailable"));
    KActorsBehavior behavior;
    if (args.length == 4) {
      behavior = resources.parseAsset(Path.of(args[3]).toUri().toURL(), KActorsBehavior.class, user);
    } else {
      var project = resources.retrieve("klab.staging.vxii", Project.class, user);
      behavior = project.getTestCases().stream().filter(t -> "klab.staging.vxii.testsuite".equals(t.getUrn()))
          .findFirst().orElseThrow(() -> new IllegalStateException("Staging testcase is unavailable"));
    }
    if (behavior == null) throw new IllegalStateException("Could not load testcase");
    for (int pass = 1; pass <= passes; pass++) {
      var path = directory.resolve("run-" + pass + ".json");
      if (Files.exists(path)) throw new IllegalStateException("Report already exists: " + path);
      var selected = Utils.Json.parseObject(Utils.Json.asString(behavior), KActorsBehaviorImpl.class);
      if ("full".equals(action)) selected.getProperties().remove("testAction");
      else selected.getProperties().put("testAction", action);
      var created = runtime.createAgent(selected, "VXII performance " + action,
          EnumSet.of(RuntimeAgent.CompilationOptions.DO_NOT_START), user);
      if (!(created instanceof AgentImpl agent) || !agent.isViable()
          || !agent.isStartDeferred() || !agent.isMessagingConnected())
        throw new IllegalStateException("Cannot connect to staging testcase agent");
      var completed = new CompletableFuture<DomainObject>();
      try (var events = agent.addMessageListener(message -> {
        if (message.getMessageType() != Message.MessageType.CustomAgentMessage) return;
        var event = message.getPayload(RuntimeAgent.CustomMessage.class);
        if (event == null || event.type() == null || !(event.payload() instanceof DomainObject data)) return;
        var type = event.type().getValue();
        if (RuntimeAgent.TestMessageType.TEST_FINISHED.messageClass().equals(type))
          System.out.printf("%s %s start=%s end=%s%n", data.get("urn"), data.get("outcome"), data.get("start"), data.get("end"));
        if (RuntimeAgent.TestMessageType.TESTCASE_FINISHED.messageClass().equals(type)) completed.complete(data);
      })) {
        if (!agent.start()) throw new IllegalStateException("Could not start staging testcase");
        var report = completed.get(180, TimeUnit.MINUTES);
        Files.writeString(path, Utils.Json.asString(report), StandardOpenOption.CREATE_NEW);
        var tests = report.getChildren().stream().filter(child -> "test".equals(child.get("type"))).toList();
        if (tests.isEmpty() || !matchingCount(report, "tests", tests.size())
            || !matchingCount(report, "testsFinished", tests.size())
            || !matchingCount(report, "testsPassed", tests.size())
            || !(report.get("assertions") instanceof Number assertions)
            || !matchingCount(report, "assertionsPassed", assertions.intValue())
            || (!"full".equals(action) && (tests.size() != 1 || !action.equals(tests.getFirst().get("urn")))))
          throw new IllegalStateException("Incomplete, failing or unexpected testcase: " + path);
      }
    }
  }

  private static boolean matchingCount(DomainObject report, String field, int expected) {
    return report.get(field) instanceof Number actual && actual.intValue() == expected;
  }
}
