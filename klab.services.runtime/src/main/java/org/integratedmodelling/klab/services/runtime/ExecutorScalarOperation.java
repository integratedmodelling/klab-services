package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.lang.ServiceCall;

public class ExecutorScalarOperation extends ExecutorOperation implements ExecutionContext.ScalarOperation {

    public ExecutorScalarOperation(ServiceCall process) {
        super(process);
    }

    @Override
    public void add(ServiceCall scalarProcessingStep) {

    }
}
