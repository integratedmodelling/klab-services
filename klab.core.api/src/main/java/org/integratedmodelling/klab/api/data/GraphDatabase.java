package org.integratedmodelling.klab.api.data;

import java.net.URL;

/**
 * A graph database instrumented for k.LAB operation. Must be able to store and connect knowledge such as
 * observations, actuators and provenance nodes. Implementations must use this interface only, to implement
 * persistent or non-persistent, distributed or local digital twin operation according to configuration.
 */
public interface GraphDatabase {

    /**
     * If true, the database can create a new database by merging with the URL of another digital twin,
     * enabling federated DTs.
     *
     * @return true if distribution of DTs is enabled
     */
    boolean canDistribute();

    /**
     * Build a federated graph resulting from merging with the URL pointing to a remote digital twin.
     *
     * @param remoteDigitalTwinURL

     * @return the federated database
     */
    GraphDatabase merge(URL remoteDigitalTwinURL);

}
