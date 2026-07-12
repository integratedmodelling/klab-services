package org.integratedmodelling.klab.modeler.model;

import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;

import java.util.List;

public class NavigableKActorsAction extends NavigableKlabStatement<KActorsAction>
    implements KActorsAction {

  public NavigableKActorsAction(KActorsAction asset, NavigableKlabAsset<?> parent) {
    super(asset, parent);
  }

  @Override
  public List<KActorsStatement> getCode() {
    return delegate.getCode();
  }

  @Override
  public List<String> getArgumentNames() {
    return delegate.getArgumentNames();
  }

  @Override
  public boolean isFunction() {
    return delegate.isFunction();
  }

  @Override
  public String getTag() {
    return delegate.getTag();
  }

  @Override
  public Type getType() {
    return delegate.getType();
  }

  @Override
  public boolean isSequential() {
    return delegate.isSequential();
  }
}
