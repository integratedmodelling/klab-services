package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ResolutionConstraintImpl implements ResolutionConstraint {

    private Type type;
    private List<Object> data = new ArrayList<>();

    public ResolutionConstraintImpl() {
    }

    public ResolutionConstraintImpl(Type type, Object... data) {
        this.type = type;
        for (var o : data) {
            if ((o != null && !type.dataClass.isAssignableFrom(o.getClass()))) {
                throw new KlabIllegalArgumentException("Cannot create resolution constraint: illegal data " +
                        "content");
            }
            this.data.add(o);
        }
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public boolean empty() {
        return data.isEmpty() || data.stream().anyMatch(Objects::isNull);
    }

    @Override
    public Type getType() {
        return type;
    }

    public List<Object> getData() {
        return data;
    }

    public void setData(List<Object> data) {
        this.data = data;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @Override
    public <T> List<T> payload(Class<T> dataClass) {
        return new Utils.Casts<Object, T>().cast(data);
    }

    @Override
    public ResolutionConstraint merge(ResolutionConstraint constraint) {
        ResolutionConstraintImpl ret = new ResolutionConstraintImpl();
        ret.type = this.type;
        if (type == Type.Parameters) {
            for (int i = 0; i < constraint.size(); i++) {
                if (data.size() > i) {
                    var existing = this.payload(Parameters.class).get(i);
                    var merged = Parameters.create(existing);
                    merged.putAll(constraint.payload(Parameters.class).get(i));
                    ret.data.add(merged);
                } else {
                    ret.data.add(constraint.payload(Parameters.class).get(i));
                }
            }
        } else {
          ret.data.addAll(constraint.payload(Object.class));
        }
        return ret;
    }

    @Override
    public String toString() {
        return "ResolutionConstraintImpl{" +
                "data=" + data +
                ", type=" + type +
                '}';
    }
}
