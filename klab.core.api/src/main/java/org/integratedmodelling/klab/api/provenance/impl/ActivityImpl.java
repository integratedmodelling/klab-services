package org.integratedmodelling.klab.api.provenance.impl;

import org.integratedmodelling.klab.api.provenance.Activity;

import java.util.ArrayList;
import java.util.List;

public class ActivityImpl extends ProvenanceNodeImpl implements Activity {

    private long start;
    private long end;
    private List<Long> schedulerTime = new ArrayList<>();
    private long size;
    private long credits;
    private Activity.Type type;
    private String description;
    private long id;
    private Activity parent;

    @Override
    public long getStart() {
        return start;
    }

    public void setStart(long start) {
        this.start = start;
    }

    @Override
    public long getEnd() {
        return end;
    }

    public void setEnd(long end) {
        this.end = end;
    }

    @Override
    public List<Long> getSchedulerTime() {
        return schedulerTime;
    }

    public void setSchedulerTime(List<Long> schedulerTime) {
        this.schedulerTime = schedulerTime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getCredits() {
        return credits;
    }

    public void setCredits(long credits) {
        this.credits = credits;
    }

    @Override
    public Activity.Type getType() {
        return type;
    }

    public void setType(Activity.Type type) {
        this.type = type;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public Activity getParent() {
        return parent;
    }

    public void setParent(Activity parent) {
        this.parent = parent;
    }
}
