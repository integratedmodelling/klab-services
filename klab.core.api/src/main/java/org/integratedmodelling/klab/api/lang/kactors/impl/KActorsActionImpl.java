package org.integratedmodelling.klab.api.lang.kactors.impl;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;

public class KActorsActionImpl extends KActorsStatementImpl implements KActorsAction {

  @Serial private static final long serialVersionUID = 5202922350235994909L;

  private String urn;
  private List<KActorsStatement> code = new ArrayList<>();
  private List<String> argumentNames = new ArrayList<>();
  private boolean isStatic;
  private org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type actionType;

  @Override
  public String getUrn() {
    return this.urn;
  }

  @Override
  public List<KActorsStatement> getCode() {
    return this.code;
  }

  @Override
  public List<String> getArgumentNames() {
    return this.argumentNames;
  }

  @Override
  public boolean isStatic() {
    return this.isStatic;
  }

  @Override
  public org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type getActionType() {
    return actionType;
  }

  public void setUrn(String urn) {
    this.urn = urn;
  }

  public void setCode(List<KActorsStatement> code) {
    this.code = code;
  }

  public void setArgumentNames(List<String> argumentNames) {
    this.argumentNames = argumentNames;
  }

  public void setStatic(boolean isStatic) {
    this.isStatic = isStatic;
  }

  @Override
  public void visit(Visitor visitor) {}

  @Override
  public void setActionType(
      org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type actionType) {
    this.actionType = actionType;
  }

  @Override
  public <T> T format(CodeAppender<T> appender) {
    return null;
  }
}
