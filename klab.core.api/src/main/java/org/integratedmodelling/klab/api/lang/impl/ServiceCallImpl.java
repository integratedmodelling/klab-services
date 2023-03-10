package org.integratedmodelling.klab.api.lang.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.collections.impl.Range;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.classification.KClassification;
import org.integratedmodelling.klab.api.data.mediation.classification.KClassifier;
import org.integratedmodelling.klab.api.data.mediation.classification.KLookupTable;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.Prototype;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.KimExpression;
import org.integratedmodelling.klab.api.lang.kim.impl.KimStatementImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

public class ServiceCallImpl extends KimStatementImpl implements ServiceCall {

    private static final long serialVersionUID = 8447771460330621498L;

    protected String name;
    protected ParametersImpl<String> parameters = new ParametersImpl<>();
    protected Set<String> interactiveParameterIds = new HashSet<>();

    public List<Notification> validateUsage(Set<Artifact.Type> expectedType) {
        return null;// Kim.INSTANCE.validateFunctionCall(this, expectedType);
    }

    @SuppressWarnings("unchecked")
    public ServiceCallImpl(String name, Object[] parameters) {
        this.name = name;
        if (parameters != null && parameters.length == 1 && parameters[0] instanceof Parameters) {
            this.parameters.putAll((Parameters<String>) (parameters[0]));
        } else if (parameters != null) {
            for (int i = 0; i < parameters.length; i++) {
                String key = parameters[i].toString();
                Object val = parameters[++i];
                this.parameters.put(key, val);
            }
        }
    }

    public ServiceCallImpl(String name, Map<String, Object> parameters) {
        this.name = name;
        this.parameters.putAll(parameters);
    }

    @Override
    public String getSourceCode() {

        if (super.getSourceCode() == null || super.getSourceCode().trim().isEmpty()) {
            String ret = name + "(";
            int i = 0;
            for (String key : parameters.keySet()) {

                // internal parameters
                if (key.startsWith("__")) {
                    continue;
                }
                ret += (i == 0 ? "" : ", ") + key + " = ";
                Object val = parameters.get(key);
                ret += val instanceof String
                        ? ("\"" + Utils.Escape.forDoubleQuotedString((String) val, false) + "\"")
                        : (val == null ? "unknown" : getStringValue(val));
                i++;
            }
            ret += ")";
            return ret;
        }

        return super.getSourceCode();
    }

    @Override
    public void visit(Visitor visitor) {
        // TODO must visit concept declarations in maps
    }

    @Override
    public String toString() {
        return getSourceCode();
    }

    private String getStringValue(Object val) {

        if (val instanceof List) {
            String ret = "(";
            for (Object o : ((List<?>) val)) {
                ret += (ret.length() == 1 ? "" : " ") + getStringValue(o);
            }
            return ret + ")";
        } else if (val instanceof Map) {
            String ret = "{";
            for (Object o : ((Map<?, ?>) val).keySet()) {
                ret += (ret.length() == 1 ? "" : " ") + o + " " + getStringValue(((Map<?, ?>) val).get(o));
            }
            return ret + "}";
        } else if (val instanceof Range) {
            return ((Range) val).getLowerBound() + " to " + ((Range) val).getUpperBound();
        } else if (val instanceof KClassification) {
            String ret = "";
            for (Pair<Concept, KClassifier> o : ((KClassification) val)) {
                ret += (ret.isEmpty() ? "" : ", ") + o.getSecond().getSourceCode() + " : '" + o.getFirst()
                        + "'";
            }
            return "{" + ret + "}";
        } else if (val instanceof KLookupTable) {
            String ret = "";
            // TODO table literal
            // TODO must also pass argument list to the same function...
            return "{{" + ret + "}}";
        } else if (val instanceof KimExpression) {
            return "[" + ((KimExpression) val).getCode() + "]";
        } else if (val instanceof ServiceCallImpl) {
            return ((ServiceCallImpl) val).getSourceCode();
        } else if (val instanceof Contextualizable) {
            return ((Contextualizable) val).getSourceCode();
        } else if (val instanceof Unit || val instanceof Currency) {
            return "\"" + val + "\"";
        }
        return val.toString();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ParametersImpl<String> getParameters() {
        return parameters;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setParameters(ParametersImpl<String> parameters) {
        this.parameters = parameters;
    }

    @Override
    public Collection<String> getInteractiveParameters() {
        return interactiveParameterIds;
    }

    public ServiceCall copy() {
        return ServiceCall.create(this.name, this.parameters);
    }

    @Override
    public int getParameterCount() {
        int n = 0;
        if (parameters.size() > 0) {
            for (String s : parameters.keySet()) {
                if (!s.startsWith("_")) {
                    n++;
                }
            }
        }
        return n;
    }

    @Override
    public Prototype getPrototype() {
//        IExtensionService exts = Services.INSTANCE.getService(IExtensionService.class);
        return null; // exts == null ? null : exts.getPrototype(name);
    }

}
