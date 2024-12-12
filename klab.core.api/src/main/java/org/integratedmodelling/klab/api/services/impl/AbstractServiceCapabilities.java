package org.integratedmodelling.klab.api.services.impl;

import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Message;

import java.net.URI;
import java.net.URL;
import java.util.*;

public abstract class AbstractServiceCapabilities implements KlabService.ServiceCapabilities {

    private String localName;
    private String serviceName;
    private String serviceId;
    private String serverId;
    private URL url;
    private Set<Message.Queue> availableMessagingQueues = EnumSet.noneOf(Message.Queue.class);
    private URI brokerURI;
    private Map<String, List<ResourceTransport.Schema>> importSchemata = new HashMap<>();
    private Map<String, List<ResourceTransport.Schema>> exportSchemata = new HashMap<>();

    @Override
    public String getLocalName() {
        return this.localName;
    }

    @Override
    public String getServiceName() {
        return this.serviceName;
    }

    @Override
    public String getServiceId() {
        return this.serviceId;
    }

    @Override
    public String getServerId() {
        return this.serverId;
    }

    @Override
    public URL getUrl() {
        return this.url;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    @Override
    public Set<Message.Queue> getAvailableMessagingQueues() {
        return availableMessagingQueues;
    }

    public void setAvailableMessagingQueues(Set<Message.Queue> availableMessagingQueues) {
        this.availableMessagingQueues = availableMessagingQueues;
    }

    @Override
    public URI getBrokerURI() {
        return brokerURI;
    }

    public void setBrokerURI(URI brokerURI) {
        this.brokerURI = brokerURI;
    }

    @Override
    public Map<String, List<ResourceTransport.Schema>> getExportSchemata() {
        return exportSchemata;
    }

    @Override
    public Map<String, List<ResourceTransport.Schema>> getImportSchemata() {
        return importSchemata;
    }
}
