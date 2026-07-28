package org.integratedmodelling.klab.modeler.model;

import java.util.List;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;

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
  public List<Argument> getArguments() {
    return delegate.getArguments();
  }

  @Override
  public boolean isStatic() {
    return delegate.isStatic();
  }

  @Override
  public org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type getActionType() {
    return delegate.getActionType();
  }

  @Override
  public void setActionType(
      org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type actionType) {
    delegate.setActionType(actionType);
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
