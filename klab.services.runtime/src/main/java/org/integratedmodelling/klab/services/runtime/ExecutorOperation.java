package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.lang.ServiceCall;

import java.util.ArrayList;
import java.util.List;

public class ExecutorOperation implements ExecutionContext.Operation {

    protected List<ServiceCall> serviceCallList = new ArrayList<>();

    public ExecutorOperation(ServiceCall process) {
        serviceCallList.add(process);
    }
}
