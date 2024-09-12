package org.integratedmodelling.common.lang;

import org.integratedmodelling.common.lang.kim.KimStatementImpl;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.data.mediation.Currency;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.data.mediation.classification.Classification;
import org.integratedmodelling.klab.api.data.mediation.classification.Classifier;
import org.integratedmodelling.klab.api.data.mediation.classification.LookupTable;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.Encodeable;
import org.integratedmodelling.klab.api.lang.Prototype;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.Serial;
import java.util.*;

public class ServiceCallImpl extends KimStatementImpl implements ServiceCall {

    @Serial
    private static final long serialVersionUID = 8447771460330621498L;

    protected String urn;
    protected ParametersImpl<String> parameters = new ParametersImpl<>();
    protected Set<String> interactiveParameterIds = new HashSet<>();

    public ServiceCallImpl() {
    }

    @SuppressWarnings("unchecked")
    public ServiceCallImpl(String name, Object[] parameters) {
        this.urn = name;
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
        this.urn = name;
        this.parameters.putAll(parameters);
    }

    @Override
    public String encode(String language) {

        //		if (sourceCode() == null || sourceCode().trim().isEmpty()) {
        String ret = urn + "(";
        int i = 0;
        for (String key : parameters.keySet()) {

            // internal parameters
            if (key.startsWith("__")) {
                continue;
            }
            ret += (i == 0 ? "" : ", ") + key + " = ";
            Object val = parameters.get(key);
            ret += val instanceof String ?
                   ("\"" + Utils.Escape.forDoubleQuotedString((String) val, false) + "\"")
                                         : (val == null ? "unknown" : stringValue(val));
            i++;
        }
        ret += ")";
        return ret;
        //		}
        //
        //		return sourceCode();
    }

    @Override
    public String toString() {
        return encode(Language.KIM);
    }

    private String stringValue(Object val) {

        if (val instanceof Encodeable) {
            return ((Encodeable) val).encode(Language.KIM);
        } else if (val instanceof List) {
            String ret = "(";
            for (Object o : ((List<?>) val)) {
                ret += (ret.length() == 1 ? "" : " ") + stringValue(o);
            }
            return ret + ")";
        } else if (val instanceof Map) {
            String ret = "{";
            for (Object o : ((Map<?, ?>) val).keySet()) {
                ret += (ret.length() == 1 ? "" : " ") + o + " " + stringValue(((Map<?, ?>) val).get(o));
            }
            return ret + "}";
        } else if (val instanceof NumericRangeImpl) {
            return ((NumericRangeImpl) val).getLowerBound() + " to " + ((NumericRangeImpl) val).getUpperBound();
        } else if (val instanceof Classification) {
            String ret = "";
            for (Pair<Concept, Classifier> o : ((Classification) val)) {
                ret += (ret.isEmpty() ? "" : ", ") + o.getSecond().getSourceCode() + " : '" + o.getFirst() + "'";
            }
            return "{" + ret + "}";
        } else if (val instanceof LookupTable) {
            String ret = "";
            // TODO table literal
            // TODO must also pass argument list to the same function...
            return "{{" + ret + "}}";
        } else if (val instanceof Contextualizable) {
            //			return ((Contextualizable) val).sourceCode();
        } else if (val instanceof Unit || val instanceof Currency) {
            return "\"" + val + "\"";
        }
        return val.toString();
    }

    @Override
    public String getUrn() {
        return urn;
    }

    @Override
    public ParametersImpl<String> getParameters() {
        return parameters;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public void setParameters(ParametersImpl<String> parameters) {
        this.parameters = parameters;
    }

    @Override
    public Collection<String> getInteractiveParameters() {
        return interactiveParameterIds;
    }

    public ServiceCall copy() {
        return ServiceCallImpl.create(this.urn, this.parameters);
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


    public Set<String> getInteractiveParameterIds() {
        return interactiveParameterIds;
    }

    public void setInteractiveParameterIds(Set<String> interactiveParameterIds) {
        this.interactiveParameterIds = interactiveParameterIds;
    }

    /**
     * Create a function call from the passed parameters. All parameters after the name must be given in
     * pairs: (string, value)*
     *
     * @param name
     * @param parameters
     * @return a new service call
     */
    public static ServiceCall create(String name, Object... parameters) {
        return new ServiceCallImpl(name, parameters);
    }

    /**
     * Return a service call for the same prototype with the passed arguments added as unnamed parameters,
     * starting at the first unassigned.
     * <p>
     * TODO should validate the arguments w.r.t. the prototype.
     *
     * @param unnamedParameters
     * @return
     */
    @Override
    public ServiceCall withUnnamedParameters(Object... unnamedParameters) {
        var ret = new ServiceCallImpl(this.getUrn(), new Object[]{});
        for (Object o : unnamedParameters) {
            ret.getParameters().getUnnamedArguments().add(o);
        }
        return ret;
    }

    /**
     * Return a service call for the same prototype with the passed arguments, parsing the argument list as
     * key, value pairs. If a single map is passed as argument, that becomes the parameter map.
     * <p>
     * TODO should validate the arguments w.r.t. the prototype.
     *
     * @param keyValuePairList
     * @return
     */
    @Override
    public ServiceCall withNamedParameters(Object... keyValuePairList) {
        return new ServiceCallImpl(this.getUrn(), Parameters.create(keyValuePairList));
    }

    @Override
    public void visit(Visitor visitor) {

    }
}
