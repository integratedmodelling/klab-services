package org.integratedmodelling.common.lang.kim;

import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;

import java.io.Serial;

/**
 * The syntactic peer of a k.IM 'define' statement.
 *
 * @author ferdinando.villa
 */
public class KimSymbolDefinitionImpl extends KimStatementImpl implements KimSymbolDefinition {

    @Serial
    private static final long serialVersionUID = -3605295215543099841L;

    private String name;
    private String urn;
    private String defineClass;
    private Object value;
    private boolean defaulted;

    @Override
    public String getUrn() {
        return urn;
    }

    @Override
    public String getDefineClass() {
        return defineClass;
    }

    @Override
    public Object getValue() {
        return value;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public void setDefineClass(String defineClass) {
        this.defineClass = defineClass;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void visit(Visitor visitor) {

    }

    public boolean isDefaulted() {
        return defaulted;
    }

    public void setDefaulted(boolean defaulted) {
        this.defaulted = defaulted;
    }
}
