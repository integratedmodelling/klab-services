package org.integratedmodelling.klab.tests.services.resources;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.GrammarUtil;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;
import org.integratedmodelling.klab.runtime.kactors.compiler.BehaviorAnalyzer;
import org.integratedmodelling.klab.services.resources.lang.LanguageAdapter;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.languages.BehaviorSyntaxImpl;
import org.integratedmodelling.languages.KActorsStandaloneSetup;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.kActors.Behavior;
import org.integratedmodelling.languages.services.ObservableGrammarAccess;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.integratedmodelling.languages.validation.LanguageValidationScope;

/** Test instrumentation for parsing and semantically analyzing real k.Actors resources. */
public final class KActorsTestSupport {

  /**
   * Complete result of loading one source. Keeping each diagnostic stage separate makes failures
   * attributable while still allowing malformed fixtures to assert their expected diagnostics.
   */
  public record Result(
      URL source,
      KActorsBehavior behavior,
      List<Notification> parserNotifications,
      List<Notification> adaptationNotifications,
      BehaviorAnalyzer analyzer,
      boolean analysisSuccessful) {

    public Result {
      parserNotifications = List.copyOf(parserNotifications);
      adaptationNotifications = List.copyOf(adaptationNotifications);
    }

    public List<Notification> allNotifications() {
      var ret = new ArrayList<Notification>();
      ret.addAll(parserNotifications);
      ret.addAll(adaptationNotifications);
      if (analyzer != null) {
        ret.addAll(analyzer.getNotifications());
      }
      return List.copyOf(ret);
    }

    public KActorsBehavior requireBehavior() {
      return Objects.requireNonNull(behavior, "The k.Actors resource did not produce a behavior");
    }

    public BehaviorAnalyzer requireAnalyzer() {
      return Objects.requireNonNull(analyzer, "The k.Actors resource could not be analyzed");
    }
  }

  private final BehaviorParser parser = new BehaviorParser();
  private final LanguageValidationScope validationScope = new BasicObservableValidationScope();

  public Result loadResource(String resource) {
    return loadResource(resource, new KActorsVisitor.LenientValidator());
  }

  public Result loadResource(String resource, KActorsVisitor.Validator validator) {
    var url = KActorsTestSupport.class.getResource(resource);
    return load(Objects.requireNonNull(url, "Missing test resource " + resource), validator);
  }

  public Result load(URL behaviorUrl) {
    return load(behaviorUrl, new KActorsVisitor.LenientValidator());
  }

  public Result load(URL behaviorUrl, KActorsVisitor.Validator validator) {
    var parserNotifications = new ArrayList<Notification>();
    var adaptationNotifications = new ArrayList<Notification>();
    KActorsBehavior behavior = null;

    try (var input = behaviorUrl.openStream()) {
      var parsed = parser.parse(input, parserNotifications);
      if (parsed != null && !hasErrors(parserNotifications)) {
        var syntaxErrors = new AtomicBoolean(false);
        var syntax =
            new BehaviorSyntaxImpl(parsed, validationScope) {
              @Override
              protected void logWarning(
                  ParsedObject target, EObject object, EStructuralFeature feature, String message) {
                adaptationNotifications.add(
                    makeNotification(
                        target,
                        message,
                        org.integratedmodelling.klab.api.services.runtime.Notification.Level
                            .Warning));
              }

              @Override
              protected void logError(
                  ParsedObject target, EObject object, EStructuralFeature feature, String message) {
                adaptationNotifications.add(
                    makeNotification(
                        target,
                        message,
                        org.integratedmodelling.klab.api.services.runtime.Notification.Level
                            .Error));
                syntaxErrors.set(true);
              }
            };

        if (!syntaxErrors.get()) {
          var timestamp =
              behaviorUrl.getFile().isEmpty()
                  ? System.currentTimeMillis()
                  : new File(behaviorUrl.getFile()).lastModified();
          behavior =
              LanguageAdapter.INSTANCE.adaptBehavior(
                  syntax, syntax.getUrn(), "test.project", adaptationNotifications, timestamp);
        }
      }
    } catch (IOException e) {
      adaptationNotifications.add(Notification.error("Cannot read " + behaviorUrl + ": " + e));
    }

    BehaviorAnalyzer analyzer = null;
    var analysisSuccessful = false;
    if (behavior != null) {
      analyzer = new BehaviorAnalyzer(behavior, validator);
      analysisSuccessful = analyzer.analyze();
    }
    return new Result(
        behaviorUrl,
        behavior,
        parserNotifications,
        adaptationNotifications,
        analyzer,
        analysisSuccessful);
  }

  private static boolean hasErrors(Collection<Notification> notifications) {
    return notifications.stream()
        .anyMatch(
            notification ->
                notification.getLevel().severity >= Notification.Level.Error.severity);
  }

  private static Notification makeNotification(
      ParsedObject target, String message, Notification.Level level) {
    if (target != null) {
      var context = new NotificationImpl.LexicalContextImpl();
      context.setLength(target.getCodeLength());
      context.setOffsetInDocument(target.getCodeOffset());
      return Notification.create(message, level, context);
    }
    return Notification.create(message, level);
  }

  private static class BehaviorParser extends WorkspaceManager.Parser<Behavior> {

    @Inject ObservableGrammarAccess grammarAccess;

    @Override
    protected Injector createInjector() {
      return new KActorsStandaloneSetup().createInjectorAndDoEMFRegistration();
    }

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
  }
}
