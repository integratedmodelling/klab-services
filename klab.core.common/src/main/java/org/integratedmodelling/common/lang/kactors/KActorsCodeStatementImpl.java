package org.integratedmodelling.common.lang.kactors;

import org.integratedmodelling.common.lang.kim.KimStatementImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsCodeStatement;

public abstract class KActorsCodeStatementImpl extends KimStatementImpl
    implements KActorsCodeStatement {

  private String tag;

  @Override
  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }
}
