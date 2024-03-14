package org.integratedmodelling.klab.api.services;

/**
 * The community service handles notifications, messaging, boosting etc. for hub users and for the
 * hub itself. It directly supports those activity types relevant to k.LAB, in particular to
 * resource services, such as publishing, reviewing, editing, approving and rejecting.
 * 
 * @author Ferd
 *
 */
public interface Community extends KlabService {

    interface Capabilities extends ServiceCapabilities {

    }
}
