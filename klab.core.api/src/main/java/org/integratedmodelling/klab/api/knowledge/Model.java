package org.integratedmodelling.klab.api.knowledge;

public interface Model extends Knowledge {

    String getNamespace();

    /**
     * One of CONCEPT, TEXT, NUMBER, BOOLEAN or VOID if inactive because of error or offline resources
     * 
     * @return
     */
    Artifact.Type getType();

}
