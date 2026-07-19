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
      // not used, but needed during language resolution by the editor
      case KLAB_EXPRESSION_LANGUAGE -> "kexpr";
    };
  }

  public static KlabLanguage forId(String id) {
    for (KlabLanguage language : values()) {
      if (language.languageId().equals(id)) {
        return language;
      }
    }
    return null;
  }

  /**
   * Language name used in the editor to identify the highlighting configuration.
   *
   * @return
   */
  public String languageName() {
    return switch (this) {
      case KIM -> "kim";
      case WORLDVIEW -> "worldview";
      case OBSERVATION -> "observation";
      case OBSERVABLE -> "observable";
      case K_ACTORS -> "kactor";
      case KLAB_EXPRESSION_LANGUAGE -> "kexpr";
    };
  }

  public String languageId() {
    return switch (this) {
      case KIM -> "org.integratedmodelling.languages.Kim";
      case WORLDVIEW -> "org.integratedmodelling.languages.Worldview";
      case OBSERVATION -> "org.integratedmodelling.languages.Observation";
      case OBSERVABLE -> "org.integratedmodelling.languages.Observable";
      case K_ACTORS -> "org.integratedmodelling.languages.KActors";
      case KLAB_EXPRESSION_LANGUAGE -> "org.integratedmodelling.languages.KlabExpression";
    };
  }
}
