package org.integratedmodelling.klab.services.resources.workflow;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview;
import org.integratedmodelling.klab.api.services.resources.workflow.ProposalReview.*;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.klab.services.resources.lang.WorldviewValidationScope;
import org.integratedmodelling.languages.OntologySyntaxImpl;
import org.integratedmodelling.languages.WorldviewStandaloneSetup;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.worldview.Ontology;

/** Real, isolated syntax/adaptation checks. Never starts a service, saves a source or loads OWL.
 * The fresh scope intentionally has no imported declarations. Current import context
 * and loaded Reasoner checks stay unavailable. Syntax success never makes a candidate acceptable.
 */
public final class IsolatedProposalCandidateValidator implements ProposalCandidateValidator {
  private static final class ParserHolder {
    private static final IParser PARSER = new WorldviewStandaloneSetup()
        .createInjectorAndDoEMFRegistration().getInstance(IParser.class);
  }

  @Override
  public List<Check> validate(ProposalReview.Candidate candidate, byte[] proposal, byte[] ontology) {
    var checks = new ArrayList<Check>();
    checks.add(ProposalSchemaValidator.bundled().validate(proposal));
    checks.add(new Check(CheckKind.IMPORT_CONTEXT, CheckStatus.BLOCKED,
        List.of("No authoritative current import snapshot; isolated scope contains no imported declarations")));
    checks.addAll(validateOntology(ontology));
    checks.add(new Check(CheckKind.REASONER, CheckStatus.BLOCKED,
        List.of("Existing Reasoner validation requires authoritative saved sources and loaded knowledge; not invoked for this draft")));
    return List.copyOf(checks);
  }

  /** Serialized access to the Xtext parser; each adaptation uses a new nonpersistent scope. */
  public static synchronized List<Check> validateOntology(byte[] bytes) {
    if (bytes == null) return List.of(
        new Check(CheckKind.PARSER, CheckStatus.NOT_RUN, List.of("No candidate ontology")),
        new Check(CheckKind.ADAPTATION, CheckStatus.NOT_RUN, List.of("No candidate ontology")));
    if (bytes.length > ProposalReview.MAX_ONTOLOGY_BYTES) return List.of(
        new Check(CheckKind.PARSER, CheckStatus.FAIL, List.of("Candidate ontology byte limit exceeded")),
        new Check(CheckKind.ADAPTATION, CheckStatus.NOT_RUN, List.of("Candidate exceeds limit")));
    String source;
    try {
      source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    } catch (Exception e) {
      return List.of(new Check(CheckKind.PARSER, CheckStatus.FAIL, List.of("Candidate must be valid UTF-8")),
          new Check(CheckKind.ADAPTATION, CheckStatus.NOT_RUN, List.of("Parsing failed")));
    }
    var checks = new ArrayList<Check>();
    try {
      var parsed = ParserHolder.PARSER.parse(new StringReader(source));
      var parseErrors = new ArrayList<String>();
      for (var error : parsed.getSyntaxErrors())
        parseErrors.add("line " + error.getStartLine() + ": " + error.getSyntaxErrorMessage().getMessage());
      if (parsed.hasSyntaxErrors() || !(parsed.getRootASTElement() instanceof Ontology)) {
        if (parseErrors.isEmpty()) parseErrors.add("No ontology AST");
        return List.of(new Check(CheckKind.PARSER, CheckStatus.FAIL, parseErrors),
            new Check(CheckKind.ADAPTATION, CheckStatus.NOT_RUN, List.of("Parsing failed")));
      }
      checks.add(new Check(CheckKind.PARSER, CheckStatus.PASS,
          List.of("Active Worldview Xtext parser; candidate SHA-256 " + ProposalReviewProtocol.digest(bytes),
              "Syntax only; no scientific, reference, reasoning or consequence support implied")));
      var errors = new ArrayList<String>();
      var warnings = new ArrayList<String>();
      var syntax = new OntologySyntaxImpl((Ontology) parsed.getRootASTElement(), new WorldviewValidationScope()) {
        @Override protected void logWarning(ParsedObject t, EObject o, EStructuralFeature f, String message) { warnings.add(location(o, message)); }
        @Override protected void logError(ParsedObject t, EObject o, EStructuralFeature f, String message) { errors.add(location(o, message)); }
      };
      var adapted = LanguageAdapter.INSTANCE.adaptOntology(syntax, "proposal-review-isolated", List.of(), 0L);
      if (!adapted.getImportedOntologies().isEmpty()) {
        var messages = new ArrayList<String>();
        messages.add("Import resolution unavailable in fresh scope: " + adapted.getImportedOntologies());
        messages.addAll(errors); messages.addAll(warnings);
        checks.add(new Check(CheckKind.ADAPTATION, CheckStatus.BLOCKED, messages));
      } else {
        var messages = new ArrayList<>(errors); messages.addAll(warnings);
        if (messages.isEmpty()) messages.add("Adapted in a fresh scope; this is not loaded-worldview validation");
        checks.add(new Check(CheckKind.ADAPTATION, errors.isEmpty() ? CheckStatus.PASS : CheckStatus.FAIL, messages));
      }
    } catch (RuntimeException e) {
      var kind = checks.isEmpty() ? CheckKind.PARSER : CheckKind.ADAPTATION;
      checks.add(new Check(kind, CheckStatus.BLOCKED,
          List.of("Isolated check could not complete: " + e.getClass().getSimpleName() + ": " + e.getMessage())));
      if (kind == CheckKind.PARSER)
        checks.add(new Check(CheckKind.ADAPTATION, CheckStatus.NOT_RUN, List.of("Parser unavailable")));
    }
    return List.copyOf(checks);
  }
  private static String location(EObject object, String message) {
    var node = object == null ? null : org.eclipse.xtext.nodemodel.util.NodeModelUtils.getNode(object);
    return node == null ? message : "line " + node.getStartLine() + ", offset " + node.getOffset() + ": " + message;
  }

}
