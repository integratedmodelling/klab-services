package org.integratedmodelling.klab.api.lang;

import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;

public enum KlabLanguage {
  KIM,
  WORLDVIEW,
  OBSERVATION,
  OBSERVABLE,
  KLAB_EXPRESSION_LANGUAGE,
  K_ACTORS;

  public String fileExtension() {
    return switch (this) {
      case KIM -> "kim";
      case WORLDVIEW -> "kwv";
      case OBSERVATION -> "obs";
      case OBSERVABLE -> "semantics";
      case K_ACTORS -> "kactor";
      default ->
          throw new KlabIllegalStateException(
              "Language not supported for document extensions: " + this + "");
    };
  }

  public String languageId() {
    return switch (this) {
      case KIM -> "org.integratedmodelling.languages.Kim";
      case WORLDVIEW -> "org.integratedmodelling.languages.Worldview";
      case OBSERVATION -> "org.integratedmodelling.languages.Observation";
      case OBSERVABLE -> "org.integratedmodelling.languages.Observable";
      case K_ACTORS -> "org.integratedmodelling.languages.KActors";
      default ->
          throw new KlabIllegalStateException("Language not supported by a parser: " + this + "");
    };
  }
}
