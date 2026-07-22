package org.integratedmodelling.klab.tests.services.resources;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsVisitor;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;

/** Command-line round trip retained as a convenient manual compiler smoke test. */
public class BehaviorTranslator {

  private final KActorsTestSupport support = new KActorsTestSupport();

  public KActorsBehavior parseBehavior(URL behaviorUrl) {
    var result = support.load(behaviorUrl, new KActorsVisitor.LenientValidator());
    if (result.behavior() == null || !result.analysisSuccessful()) {
      Logging.INSTANCE.notifications(
          result
              .allNotifications()
              .toArray(
                  new org.integratedmodelling.klab.api.services.runtime.Notification[0]));
      return null;
    }
    return result.behavior();
  }

  public String translateBehavior(URL behavior) {
    var parsed = parseBehavior(behavior);
    if (parsed == null) {
      Logging.INSTANCE.error("Error loading k.Actors behavior " + behavior);
      return null;
    }
    var compiler = new AgentCompiler(parsed);
    return compiler.compile() ? compiler.getSourceCode() : null;
  }

  public static void main(String[] args) {
    List<URL> urls = new ArrayList<>();
    var translator = new BehaviorTranslator();
    if (args != null && args.length > 0) {
      urls.addAll(Arrays.stream(args).map(Utils.URLs::newURL).toList());
    } else {
      urls.add(translator.getClass().getResource("/simple.kactor"));
    }
    for (var url : urls) {
      System.out.println(translator.translateBehavior(url));
    }
  }
}
