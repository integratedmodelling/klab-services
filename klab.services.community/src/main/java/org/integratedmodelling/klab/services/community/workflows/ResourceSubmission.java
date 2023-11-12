package org.integratedmodelling.klab.services.community.workflows;

import org.copperengine.core.Interrupt;
import org.copperengine.core.persistent.PersistentWorkflow;
import org.copperengine.core.WorkflowDescription;

@WorkflowDescription(alias = "ResourceSubmission", majorVersion = 1, minorVersion = 0, patchLevelVersion = 0l)
public class ResourceSubmission extends PersistentWorkflow<ResourceSubmissionState> {

    @Override
    public void main() throws Interrupt {

    }
}
