package org.integratedmodelling.klab.api.lang.impl.kim;

import org.integratedmodelling.klab.api.knowledge.Artifact.Type;
import org.integratedmodelling.klab.api.lang.kim.KimClassifier;
import org.integratedmodelling.klab.api.lang.kim.KimLookupTable;
import org.integratedmodelling.klab.api.lang.kim.KimTable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

public class KimLookupTableImpl extends KimStatementImpl implements KimLookupTable {

    @Serial
    private static final long serialVersionUID = 1081054386576296191L;

    private Type lookupType;
    private List<Argument> arguments = new ArrayList<>();
    private KimTable table;
    private boolean twoWay;
    private List<KimClassifier> rowClassifiers = new ArrayList<>();
    private List<KimClassifier> columnClassifiers = new ArrayList<>();
    private int lookupColumnIndex;

    private String urn;

    @Override
    public Type getLookupType() {
        return lookupType;
    }

    @Override
    public List<Argument> getArguments() {
        return arguments;
    }

    @Override
    public KimTable getTable() {
        return table;
    }

    @Override
    public boolean isTwoWay() {
        return twoWay;
    }

    @Override
    public List<KimClassifier> getRowClassifiers() {
        return rowClassifiers;
    }

    @Override
    public List<KimClassifier> getColumnClassifiers() {
        return columnClassifiers;
    }

    @Override
    public int getLookupColumnIndex() {
        return lookupColumnIndex;
    }

    public void setLookupType(Type lookupType) {
        this.lookupType = lookupType;
    }

    public void setArguments(List<Argument> arguments) {
        this.arguments = arguments;
    }

    public void setTable(KimTable table) {
        this.table = table;
    }

    public void setTwoWay(boolean twoWay) {
        this.twoWay = twoWay;
    }

    public void setRowClassifiers(List<KimClassifier> rowClassifiers) {
        this.rowClassifiers = rowClassifiers;
    }

    public void setColumnClassifiers(List<KimClassifier> columnClassifiers) {
        this.columnClassifiers = columnClassifiers;
    }

    public void setLookupColumnIndex(int lookupColumnIndex) {
        this.lookupColumnIndex = lookupColumnIndex;
    }

    @Override
    public String getUrn() {
        return urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    @Override
    public void visit(KlabStatementVisitor visitor) {

    }
}
