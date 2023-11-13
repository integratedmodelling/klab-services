package org.integratedmodelling.klab.services.community.workflows;

import java.io.Serializable;

/**
 * The bean tracking a resource submission from initial message from the
 * {@link org.integratedmodelling.klab.api.services.ResourcesService} through publishing and review. Process
 * ends with a decision from the editor; resource reconsideration is a new submission. Community server keeps
 * the workflows, their full history and all associated documents; the resources server keeps resource state
 * authoritatively.
 * <p>
 * The resource may be a URN-specified resource, a project or a component.
 */
public class ResourceSubmissionState implements Serializable {

    private String doi;
    private String urn;

}
