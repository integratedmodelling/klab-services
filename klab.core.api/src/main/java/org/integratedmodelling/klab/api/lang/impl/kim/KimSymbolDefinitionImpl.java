package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;

/**
 * The syntactic peer of a k.IM 'define' statement.
 * 
 * @author ferdinando.villa
 *
 */
public class KimSymbolDefinitionImpl extends KimStatementImpl implements KimSymbolDefinition {

    private static final long serialVersionUID = -3605295215543099841L;

    private String name;
    private String defineClass;
    private Literal value;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDefineClass() {
        return defineClass;
    }

    @Override
    public Literal getValue() {
        return value;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDefineClass(String defineClass) {
        this.defineClass = defineClass;
    }

    public void setValue(Literal value) {
        this.value = value;
    }

}
