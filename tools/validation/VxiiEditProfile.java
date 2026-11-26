import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory;

/** Measure real edits using private source files and a private candidate actor. */
public class VxiiEditProfile {
  public static void main(String[] args) {
    try {
      measure(args);
      System.exit(0);
    } catch (Exception failure) {
      failure.printStackTrace();
      System.exit(1);
    }
  }

  private static void measure(String[] args) throws Exception {
    if (args.length != 4 || !List.of("first", "second").contains(args[3]))
      throw new IllegalArgumentException("Usage: VxiiEditProfile output knowledgeDir reasonerStateDir first|second");
    var output = Files.createDirectories(Path.of(args[0]).toAbsolutePath());
    var sources = Files.createDirectory(output.resolve(args[3]));
    ServiceConfiguration.injectInstantiators();
    var engine = new EngineImpl(status -> {}, (service, status) -> {});
    engine.boot();
    var user = engine.getOwner();
    if (user == null || user.getUser().isAnonymous()) throw new IllegalStateException("Authenticated owner required");
    var resources = user.getServices(ResourcesService.class).stream()
        .filter(s -> KlabService.Type.RESOURCES.localServiceUrl().equals(s.getUrl())).findFirst().orElseThrow();
    var reasoner = user.getServices(Reasoner.class).stream()
        .filter(s -> KlabService.Type.REASONER.localServiceUrl().equals(s.getUrl())).findFirst().orElseThrow();
    if ("first".equals(args[3])) {
      var source = resources.retrieve("staging.vxii.test.selection", KimNamespace.class, user);
      for (int divisions : List.of(4, 5))
        measureNamespace(source, divisions, sources, resources, reasoner, user);
    }
    var world = resources.list(Worldview.class, user).stream()
        .filter(w -> w.getOntologies().stream().anyMatch(o -> "atmosphere".equals(o.getUrn())))
        .findFirst().orElseThrow();
    var original = world.getOntologies().stream().filter(o -> "atmosphere".equals(o.getUrn())).findFirst().orElseThrow();
    String subtype = "first".equals(args[3]) ? "ProfileSpecificHumidity" : "ProfileHumidityWarm";
    String edited = original.getSourceCode() + "\nquantity " + subtype + " is atmosphere:SpecificHumidity;\n";
    var file = Files.writeString(sources.resolve("atmosphere.kwv"), edited, StandardOpenOption.CREATE_NEW);
    long start = System.nanoTime();
    var parsed = (KimOntologyImpl) resources.parseAsset(file.toUri().toURL(), KimOntology.class, user);
    if (parsed == null || parsed.getStatements().size() != original.getStatements().size() + 1)
      throw new IllegalStateException("Private ontology did not contain the added declaration");
    parsed.setSourceCode(edited);
    parsed.setProjectName(original.getProjectName());
    world.getOntologies().set(world.getOntologies().indexOf(original), parsed);
    elapsed("Ontology parse", start);
    var factory = SemanticEnvironmentFactory.fromLocalCatalog(
        output.resolve("private-actors"), Path.of(args[1]), Path.of(args[2]));
    factory.enableAuthorityBootstrap();
    start = System.nanoTime();
    var input = factory.capture(world, user);
    elapsed("Worldview capture", start);
    if (input.beanStatus() != CandidatePublicationCoordinator.BeanStatus.VALID)
      throw new IllegalStateException(input.diagnostics().toString());
    start = System.nanoTime();
    var candidate = factory.build(input, () -> false);
    try {
      elapsed("Private ontology candidate " + candidate.logicalStatus(), start);
      if (candidate.logicalStatus() != CandidatePublicationCoordinator.LogicalStatus.VALID)
        throw new IllegalStateException(candidate.diagnostics().toString());
    } finally {
      if (candidate.state() != null) candidate.state().close();
    }
  }

  private static void measureNamespace(KimNamespace source, int divisions, Path sources,
      ResourcesService resources, Reasoner reasoner, UserScope user) throws Exception {
    String edited = source.getSourceCode().replace("xdivs: 3", "xdivs: " + divisions);
    if (edited.equals(source.getSourceCode())) throw new IllegalStateException("Namespace fixture has changed");
    var folder = Files.createDirectory(sources.resolve("xdivs-" + divisions));
    var file = Files.writeString(folder.resolve("selection.kim"), edited, StandardOpenOption.CREATE_NEW);
    long start = System.nanoTime();
    var parsed = (KimNamespaceImpl) resources.parseAsset(file.toUri().toURL(), KimNamespace.class, user);
    if (parsed == null || parsed.getStatements().size() != source.getStatements().size())
      throw new IllegalStateException("Private namespace did not parse");
    parsed.setSourceCode(edited);
    parsed.setProjectName(source.getProjectName());
    elapsed("Namespace parse xdivs=" + divisions, start);
    var request = SemanticValidationRequest.of(parsed, "private-" + divisions);
    for (int pass = 1; pass <= 2; pass++) {
      start = System.nanoTime();
      var response = reasoner.validateDocument(request, user);
      elapsed("Namespace validation xdivs=" + divisions + " pass=" + pass, start);
      if (!response.valid()) throw new IllegalStateException("Namespace validation failed: " + response.getReason());
    }
  }

  private static void elapsed(String label, long start) {
    System.out.printf("%s %.3f s%n", label, (System.nanoTime() - start) / 1e9);
  }
}
