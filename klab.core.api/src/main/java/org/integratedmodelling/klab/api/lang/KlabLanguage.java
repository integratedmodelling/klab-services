package org.integratedmodelling.klab.api.lang;

public enum KlabLanguage {

    KIM,
    WORLDVIEW,
    OBSERVATION,
    OBSERVABLE,
    K_ACTORS;

    public String fileExtension() {
        return switch(this) {
            case KIM -> "kim";
            case WORLDVIEW -> "kwv";
            case OBSERVATION -> "obs";
            case OBSERVABLE -> "semantics";
            case K_ACTORS -> "kactor";
        };
    }

    public String languageId() {
        return switch(this) {
            case KIM -> "org.integratedmodelling.languages.Kim";
            case WORLDVIEW -> "org.integratedmodelling.languages.Worldview";
            case OBSERVATION -> "org.integratedmodelling.languages.Observation";
            case OBSERVABLE -> "org.integratedmodelling.languages.Observable";
            case K_ACTORS -> "org.integratedmodelling.languages.KActors";
        };
    }

}
