package org.integratedmodelling.klab.runtime.computation;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects all code elements for substitution in templates. Applies to expressions, contextualizers
 * and behaviors.
 */
public class TemplateCodeInfo {

    private List<String> fieldDeclarations = new ArrayList<>();
    private List<String> constructorArguments = new ArrayList<>();
    private List<String> localVariableDeclarations = new ArrayList<>();
    private List<String> mainCodeBlocks = new ArrayList<>();

    public List<String> getFieldDeclarations() {
        return fieldDeclarations;
    }

    public void setFieldDeclarations(List<String> fieldDeclarations) {
        this.fieldDeclarations = fieldDeclarations;
    }

    public List<String> getConstructorArguments() {
        return constructorArguments;
    }

    public void setConstructorArguments(List<String> constructorArguments) {
        this.constructorArguments = constructorArguments;
    }

    public List<String> getLocalVariableDeclarations() {
        return localVariableDeclarations;
    }

    public void setLocalVariableDeclarations(List<String> localVariableDeclarations) {
        this.localVariableDeclarations = localVariableDeclarations;
    }

    public List<String> getMainCodeBlocks() {
        return mainCodeBlocks;
    }

    public void setMainCodeBlocks(List<String> mainCodeBlocks) {
        this.mainCodeBlocks = mainCodeBlocks;
    }
}
