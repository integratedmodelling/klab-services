package org.integratedmodelling.klab.services.community.workflows;

import org.copperengine.core.Interrupt;
import org.copperengine.core.persistent.PersistentWorkflow;
import org.copperengine.core.WorkflowDescription;

@WorkflowDescription(alias = "ResourceSubmission", majorVersion = 1, minorVersion = 0, patchLevelVersion = 0l)
public class ResourceSubmission extends PersistentWorkflow<ResourceSubmissionState> {

    @Override
    public void main() throws Interrupt {

        /*
        1. request arrives from resource service upon publishing or reconsideration
         */

        /*
        2. initial status assigned and request for editor(s)
         */

        /*
        3. editor(s) assigned, request for reviewer(s)
         */

        /*
        4. reviewer(s) assigned, wait for all reviews to be in
         */

        /*
         5. reviews in, back to editor for final decision
         */

        /*
         6. final decision in, back to originating resource service to update review tier and status
         */
    }
}
