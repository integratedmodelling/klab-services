import java.util.concurrent.TimeUnit;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;

/** Normal authenticated client submission in a fresh, one-off profiling twin. */
class ResolutionProfile {
  public static void main(String[] args) {
    try { run(args); System.exit(0); }
    catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
  }
  private static void run(String[] args) throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var engine = new EngineImpl(null, null);
    var user = engine.authenticate(); // Connect to existing services; do not boot/stop the stack.
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
    while ((user.getServices(RuntimeService.class).stream().noneMatch(s -> s.status().isOperational())
        || user.getServices(Reasoner.class).stream().noneMatch(s -> s.status().isOperational())
        || user.getServices(ResourcesService.class).stream().noneMatch(s -> s.status().isOperational()))
        && System.nanoTime() < deadline) Thread.sleep(250);
    var runtime = user.getService(RuntimeService.class);
    var resources = user.getService(ResourcesService.class);
    if (runtime == null || resources == null || user.getService(Reasoner.class) == null)
      throw new IllegalStateException("Local stack is not ready");
    var namespace = resources.retrieve("staging.vxii.basic", KimNamespace.class, user);
    var definition = (KimSymbolDefinition) namespace.getStatements().stream()
        .filter(s -> s instanceof KimSymbolDefinition && s.getUrn().endsWith("cotechino"))
        .findFirst().orElseThrow();
    int repetitions = args.length == 0 ? 1 : Integer.parseInt(args[0]);
    for (int i = 0; i < repetitions; i++) {
      var config = DigitalTwin.Configuration.builder().name("Cotechino resolution profile " + System.currentTimeMillis())
          .serverUrl(runtime.getUrl()).serviceId(runtime.serviceId()).createWhenAbsent(true)
          .persistence(Persistence.ONE_OFF).build();
      var context = user.getUserSession(runtime).createContext(config);
      try {
        var observation = new Observation.NaiveBuilder(definition, context).make();
        var constrained = context.withResolutionConstraints(
            ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionNamespace, definition.getNamespace()),
            ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionProject, definition.getProjectName()));
        System.out.println("PROFILE begin=" + java.time.Instant.now() + " context=" + context.getId());
        System.out.println("PROFILE cachesBefore=" + user.getService(Reasoner.class).status().getMetadata().get("semantic.caches"));
        long started = System.nanoTime();
        var result = runtime.submit(observation, constrained).get(12, TimeUnit.MINUTES);
        System.out.println("PROFILE run=" + (i + 1) + " elapsedMs=" + (System.nanoTime()-started)/1_000_000
            + " empty=" + result.isEmpty() + " id=" + result.getId());
        System.out.println("PROFILE sourceProofs=" + user.getService(Reasoner.class).status().getMetadata()
            .get("semantic.validation.sourceProofs"));
        System.out.println("PROFILE cachesAfter=" + user.getService(Reasoner.class).status().getMetadata().get("semantic.caches"));
      } finally { context.close(); }
    }
  }
}
