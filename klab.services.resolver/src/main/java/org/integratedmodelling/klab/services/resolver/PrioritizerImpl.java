package org.integratedmodelling.klab.services.resolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.Prioritizer;

public class PrioritizerImpl implements Prioritizer<Model> {

  private ContextScope scope;
  private Scale scale;
  private Map<Model, Metadata> ranks = new HashMap<>();

  public PrioritizerImpl(ContextScope scope, Scale scale) {
    this.scope = scope;
    this.scale = scale;
    // TODO establish the comparison strategy from the scope or the resolution namespace,
    // defaulting at the default.
  }

  @Override
  public int compare(Model o1, Model o2) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public Metadata computeCriteria(Model model) {
    if (this.ranks.containsKey(model)) {
      return this.ranks.get(model);
    }
    return null;
  }

  @Override
  public List<String> listCriteria() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Metadata getRanking(Model ranked) {
    return this.ranks.get(ranked);
  }
}
