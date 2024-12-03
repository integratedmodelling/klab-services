package org.integratedmodelling.klab.api.provenance.impl;

import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.KlabService;

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
    private String taskId;
    private Outcome outcome;
    private String stackTrace;
    private String serviceId;
    private String serviceName;
    private KlabService.Type serviceType;
    private String dataflow;
    private String urn;

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

    /**
     * Non-API: the ID path through which the activity hierarchy can be reconstructed. When an activity is
     * included in a message, the taskId gets in there.
     *
     * @return
     */
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @Override
    public Outcome getOutcome() {
        return outcome;
    }

    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    @Override
    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public KlabService.Type getServiceType() {
        return serviceType;
    }

    public void setServiceType(KlabService.Type serviceType) {
        this.serviceType = serviceType;
    }

    @Override
    public String getDataflow() {
        return dataflow;
    }

    public void setDataflow(String dataflow) {
        this.dataflow = dataflow;
    }

    @Override
    public String getUrn() {
        return urn;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    @Override
    public String toString() {
        return "ActivityImpl{" +
                "type=" + type +
                ", description='" + description + '\'' +
                ", taskId='" + taskId + '\'' +
                ", outcome=" + outcome +
                '}';
    }
}
