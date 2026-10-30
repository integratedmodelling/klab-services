package org.integratedmodelling.klab.api.services.logging;

import java.util.Map;

/** Vetted message and redacted cause structure; eventId identifies updates to a partial event. */
public record LogRecord(String eventId, String timestamp, String level, String logger,
    String message, String stackTrace, Map<String, String> diagnostics,
    boolean partial, boolean truncated) {}
