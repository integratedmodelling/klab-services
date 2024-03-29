package org.integratedmodelling.common.knowledge;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.lang.ServiceCall;

public class ModelBuilderImpl implements Model.Builder {

    public ModelBuilderImpl() {

    }
    public ModelBuilderImpl(String learnedUrn) {

    }
    public ModelBuilderImpl(Resource resource) {

    }
    public ModelBuilderImpl(Artifact.Type type) {

    }
    public ModelBuilderImpl(Literal value) {

    }
    public ModelBuilderImpl(Observable observable) {

    }

    @Override
    public Model.Builder as(Observable observable) {
        return null;
    }

    @Override
    public Model.Builder as(String name, Artifact.Type nonSemanticType) {
        return null;
    }

    @Override
    public Model.Builder observing(Observable dependency) {
        return null;
    }

    @Override
    public Model.Builder observedAs(Identity identity) {
        return null;
    }

    @Override
    public Model.Builder inNamespace(String namespace) {
        return null;
    }

    @Override
    public Model.Builder using(ServiceCall... calls) {
        return null;
    }

    @Override
    public Model.Builder over(ServiceCall... calls) {
        return null;
    }

    @Override
    public Model.Builder withOutput(Observable observable) {
        return null;
    }

    @Override
    public Model build() {
        return null;
    }
}
