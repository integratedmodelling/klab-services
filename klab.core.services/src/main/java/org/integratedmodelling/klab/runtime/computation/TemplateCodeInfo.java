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
  private List<String> constructorInitializationStatements = new ArrayList<>();
  private List<String> mainCodeBlocks = new ArrayList<>();
  private List<String> loopVariableAssignments = new ArrayList<>();
  private List<String> additionalImports = new ArrayList<>();
  private List<String> bodyInitializationStatements = new ArrayList<>();

  private String className;
  private String templateName;

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

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public List<String> getConstructorInitializationStatements() {
    return constructorInitializationStatements;
  }

  public void setConstructorInitializationStatements(
      List<String> constructorInitializationStatements) {
    this.constructorInitializationStatements = constructorInitializationStatements;
  }

  public String getTemplateName() {
    return templateName;
  }

  public void setTemplateName(String templateName) {
    this.templateName = templateName;
  }

  public List<String> getLoopVariableAssignments() {
    return loopVariableAssignments;
  }

  public void setLoopVariableAssignments(List<String> loopVariableAssignments) {
    this.loopVariableAssignments = loopVariableAssignments;
  }

  public List<String> getAdditionalImports() {
    return additionalImports;
  }

  public void setAdditionalImports(List<String> additionalImports) {
    this.additionalImports = additionalImports;
  }

  public List<String> getBodyInitializationStatements() {
    return bodyInitializationStatements;
  }

  public void setBodyInitializationStatements(List<String> bodyInitializationStatements) {
    this.bodyInitializationStatements = bodyInitializationStatements;
  }
}
