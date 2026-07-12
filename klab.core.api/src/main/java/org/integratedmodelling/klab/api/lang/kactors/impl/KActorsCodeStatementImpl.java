package org.integratedmodelling.klab.api.lang.kactors.impl;

import org.integratedmodelling.klab.api.lang.kim.impl.KimStatementImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsCodeStatement;

public abstract class KActorsCodeStatementImpl extends KimStatementImpl
    implements KActorsCodeStatement {

  private String tag;

  @Override
  public String getTag() {
    return tag;
  }

}
