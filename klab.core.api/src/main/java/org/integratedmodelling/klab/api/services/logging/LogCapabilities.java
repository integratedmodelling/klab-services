package org.integratedmodelling.klab.api.services.logging;

import java.util.List;

/** Only the active managed file is exposed initially; rotated-away cursor ranges return 410. */
public record LogCapabilities(String instanceId, List<String> streamIds, int defaultLimit,
    int maxLimit, int maxScanBytes, int maxEventBytes, String sourceZone,
    String redactionPolicy, boolean archivedSegments, boolean correlationFilter) {}
