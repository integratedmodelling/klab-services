package org.integratedmodelling.klab.api.lang.kim.impl;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimModelStatement;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;

public class KimModelStatementImpl extends KimActiveStatementImpl implements KimModelStatement {

    private static final long serialVersionUID = -6068429551009652469L;
    private KimConcept reinterpretingRole;
    private List<KimObservable> dependencies = new ArrayList<>();
    private List<KimObservable> observables = new ArrayList<>();
    private Type type;
    private List<String> resourceUrns = new ArrayList();
    private boolean learningModel;
    private boolean interpreter;
    private boolean instantiator;
    private String name;
    private Literal inlineValue;
    private List<Contextualizable> contextualization = new ArrayList<>();
    private String docstring;
    private boolean semantic;

    @Override
    public KimConcept getReinterpretingRole() {
        return this.reinterpretingRole;
    }

    @Override
    public List<KimObservable> getDependencies() {
        return this.dependencies;
    }

    @Override
    public List<KimObservable> getObservables() {
        return this.observables;
    }

    @Override
    public Artifact.Type getType() {
        return this.type;
    }

    @Override
    public List<String> getResourceUrns() {
        return this.resourceUrns;
    }

    @Override
    public boolean isLearningModel() {
        return this.learningModel;
    }

    @Override
    public boolean isInterpreter() {
        return this.interpreter;
    }

    @Override
    public boolean isInstantiator() {
        return this.instantiator;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Literal getInlineValue() {
        return this.inlineValue;
    }

    @Override
    public List<Contextualizable> getContextualization() {
        return this.contextualization;
    }

    @Override
    public String getDocstring() {
        return this.docstring;
    }

    @Override
    public boolean isSemantic() {
        return this.semantic;
    }

    public void setReinterpretingRole(KimConcept reinterpretingRole) {
        this.reinterpretingRole = reinterpretingRole;
    }

    public void setDependencies(List<KimObservable> dependencies) {
        this.dependencies = dependencies;
    }

    public void setObservables(List<KimObservable> observables) {
        this.observables = observables;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setResourceUrns(List<String> resourceUrns) {
        this.resourceUrns = resourceUrns;
    }

    public void setLearningModel(boolean learningModel) {
        this.learningModel = learningModel;
    }

    public void setInterpreter(boolean interpreter) {
        this.interpreter = interpreter;
    }

    public void setInstantiator(boolean instantiator) {
        this.instantiator = instantiator;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setInlineValue(Literal inlineValue) {
        this.inlineValue = inlineValue;
    }

    public void setContextualization(List<Contextualizable> contextualization) {
        this.contextualization = contextualization;
    }

    public void setDocstring(String docstring) {
        this.docstring = docstring;
    }

    public void setSemantic(boolean semantic) {
        this.semantic = semantic;
    }

}
