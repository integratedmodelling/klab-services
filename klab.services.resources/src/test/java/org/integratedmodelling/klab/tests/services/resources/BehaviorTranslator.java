package org.integratedmodelling.klab.tests.services.resources;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.GrammarUtil;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.languages.BehaviorSyntaxImpl;
import org.integratedmodelling.languages.KActorsStandaloneSetup;
import org.integratedmodelling.languages.api.BehaviorSyntax;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.kActors.Behavior;
import org.integratedmodelling.languages.services.ObservableGrammarAccess;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.integratedmodelling.languages.validation.LanguageValidationScope;

/**
 * Not a test but contains the instrumentation to read, build, and analyze a KActors behavior from a
 * file, up to its translation into Java code.
 */
public class BehaviorTranslator {

  BehaviorParser behaviorParser = new BehaviorParser();
  LanguageValidationScope languageValidationScope = new BasicObservableValidationScope();

  class BehaviorParser extends WorkspaceManager.Parser<Behavior> {

    @Override
    protected Injector createInjector() {
      return new KActorsStandaloneSetup().createInjectorAndDoEMFRegistration();
    }

    @Inject ObservableGrammarAccess grammarAccess;

    @Override
    public Version getVersion() {
      var version = grammarAccess.getClass().getPackage().getImplementationVersion();
      return version == null ? Version.CURRENT_VERSION : Version.create(version);
    }

    @Override
    public Collection<String> getKeywords() {
      return GrammarUtil.getAllKeywords(grammarAccess.getGrammar());
    }

    @Override
    public String getLanguageId() {
      return GrammarUtil.getLanguageId(grammarAccess.getGrammar());
    }

    @Override
    public String getLanguageSimpleName() {
      return GrammarUtil.getSimpleName(grammarAccess.getGrammar());
    }

    /**
     * Parse a concept definition into its syntactic peer, which should be inspected for errors
     * before turning into semantics.
     *
     * @param conceptDefinition
     * @return the parsed semantic expression, or null if the parser cannot make sense of it.
     */
    public BehaviorSyntax parseBehavior(String conceptDefinition) {
      var result =
          parser.parse(
              grammarAccess.getConceptExpressionRule(), new StringReader(conceptDefinition));
      var ret = result.getRootASTElement();
      if (ret instanceof Behavior parsed) {
        return new BehaviorSyntaxImpl(parsed, languageValidationScope) {

          List<String> errors = new ArrayList<>();

          @Override
          protected void logWarning(
              ParsedObject target, EObject object, EStructuralFeature feature, String message) {
            getNotifications()
                .add(
                    new Notification(
                        object,
                        new LanguageValidationScope.ValidationMessage(
                            message, -1, LanguageValidationScope.Level.WARNING)));
          }

          @Override
          protected void logError(
              ParsedObject target, EObject object, EStructuralFeature feature, String message) {
            getNotifications()
                .add(
                    new Notification(
                        object,
                        new LanguageValidationScope.ValidationMessage(
                            message, -1, LanguageValidationScope.Level.ERROR)));
          }
        };
      }
      return null;
    }
  }

  public KActorsBehavior parseBehavior(URL behaviorUrl) {

    KActorsBehavior ret = null;

    var timestamp =
        behaviorUrl.getFile().isEmpty()
            ? System.currentTimeMillis()
            : new File(behaviorUrl.getFile()).lastModified();

    try (var input = behaviorUrl.openStream()) {

      var errors = new AtomicBoolean(false);
      var notams = new ArrayList<Notification>();
      var parsed = behaviorParser.parse(input, notams);

      if (!notams.isEmpty()) {

        // TODO extract lexical context; fix in main parser in WorkspaceManager
        Logging.INSTANCE.error(
            "k.Actors resource has errors: " + behaviorUrl,
            Klab.ErrorCode.RESOURCE_VALIDATION,
            Klab.ErrorContext.OBSERVATION_STRATEGY);

        Logging.INSTANCE.notifications(notams.toArray(new Notification[0]));

      } else {

        List<Notification> notifications = new ArrayList<>();
        var syntax =
            new BehaviorSyntaxImpl(parsed, this.languageValidationScope) {

              @Override
              protected void logWarning(
                  ParsedObject target, EObject object, EStructuralFeature feature, String message) {
                notifications.add(
                    makeNotification(
                        target,
                        object,
                        feature,
                        message,
                        org.integratedmodelling.klab.api.services.runtime.Notification.Level
                            .Warning));
              }

              @Override
              protected void logError(
                  ParsedObject target, EObject object, EStructuralFeature feature, String message) {
                notifications.add(
                    makeNotification(
                        target,
                        object,
                        feature,
                        message,
                        org.integratedmodelling.klab.api.services.runtime.Notification.Level
                            .Error));
                errors.set(true);
              }
            };

        if (!errors.get()) {
          ret =
              LanguageAdapter.INSTANCE.adaptBehavior(
                  syntax, syntax.getUrn(), "no.project", notifications, timestamp);
        }
      }
    } catch (IOException e) {
      // log error and return failure
      Logging.INSTANCE.error(
          "Error loading k.Actors behavior " + behaviorUrl,
          //                  Klab.ErrorCode.READ_FAILED,
          Klab.ErrorContext.BEHAVIOR);
    }
    return ret;
  }

  public String translateBehavior(URL behavior) {

    var parsed = parseBehavior(behavior);
    if (parsed == null) {
      Logging.INSTANCE.error("Error loading k.Actors behavior " + behavior);
      return null;
    }

    var compiler = new AgentCompiler(parsed);
    if (compiler.compile()) {
      return compiler.getSourceCode();
    }

    return null;
  }

  private Notification makeNotification(
      ParsedObject target,
      EObject object,
      EStructuralFeature feature,
      String message,
      Notification.Level level) {
    if (target != null) {
      var context = new NotificationImpl.LexicalContextImpl();
      context.setLength(target.getCodeLength());
      context.setOffsetInDocument(target.getCodeOffset());
      //            context.setUrl(target.uri());
      return Notification.create(message, level, context);
    }
    return Notification.create(message, level);
  }

  public static void main(String[] args) {

    List<URL> urls = new ArrayList<>();
    BehaviorTranslator translator = new BehaviorTranslator();

    if (args != null && args.length > 0) {
      urls.addAll(Arrays.stream(args).map(Utils.URLs::newURL).toList());
    } else {
      // test file
      urls.add(translator.getClass().getResource("/simple.kactor"));
    }

    for (URL url : urls) {
      System.out.println(translator.translateBehavior(url));
    }
  }
}
