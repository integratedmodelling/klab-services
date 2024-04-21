package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.collections.Literal;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;

import java.io.Serial;

/**
 * The syntactic peer of a k.IM 'define' statement.
 * 
 * @author ferdinando.villa
 *
 */
public class KimSymbolDefinitionImpl extends KimStatementImpl implements KimSymbolDefinition {

    @Serial
    private static final long serialVersionUID = -3605295215543099841L;

    private String urn;
    private String defineClass;
    private Literal value;

    @Override
    public String getUrn() {
        return urn;
    }

    @Override
    public String getDefineClass() {
        return defineClass;
    }

    @Override
    public Literal getValue() {
        return value;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public void setDefineClass(String defineClass) {
        this.defineClass = defineClass;
    }

    public void setValue(Literal value) {
        this.value = value;
    }

    @Override
    public void visit(KlabStatementVisitor visitor) {

    }
}
