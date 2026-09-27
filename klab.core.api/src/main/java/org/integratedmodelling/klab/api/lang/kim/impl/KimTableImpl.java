package org.integratedmodelling.klab.api.lang.kim.impl;

import org.integratedmodelling.klab.api.lang.kim.KimClassifier;
import org.integratedmodelling.klab.api.lang.kim.KimTable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

public class KimTableImpl extends KimStatementImpl implements KimTable {

    @Serial
    private static final long serialVersionUID = -8528877830924327222L;

    private List<String> headers = new ArrayList<>();
    private boolean twoWay;
    private List<KimClassifier> columnClassifiers = new ArrayList<>();
    private int rowCount;
    private int columnCount;
    private List<KimClassifier> rowClassifiers = new ArrayList<>();
    private List<KimClassifier[]> rows = new ArrayList<>();
    private String urn;

    @Override
    public List<String> getHeaders() {
        return headers;
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
    public int getRowCount() {
        return rowCount;
    }

    @Override
    public int getColumnCount() {
        return columnCount;
    }

    @Override
    public KimClassifier[] row(int i) {
        return rows.get(i);
    }

    @Override
    public List<KimClassifier[]> rows() {
        return rows;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public void setTwoWay(boolean twoWay) {
        this.twoWay = twoWay;
    }

    public void setColumnClassifiers(List<KimClassifier> columnClassifiers) {
        this.columnClassifiers = columnClassifiers;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }

    public void setColumnCount(int columnCount) {
        this.columnCount = columnCount;
    }

    public void setRowClassifiers(List<KimClassifier> rowClassifiers) {
        this.rowClassifiers = rowClassifiers;
    }

    public void setRows(List<KimClassifier[]> rows) {
        this.rows = rows == null ? new ArrayList<>() : rows;
        this.rowCount = this.rows.size();
        this.columnCount = this.rows.isEmpty() ? 0 : this.rows.getFirst().length;
    }

    @Override
    public String getUrn() {
        return urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    @Override
    public <T> T format(CodeAppender<T> appender) {
        return null;
    }
}
