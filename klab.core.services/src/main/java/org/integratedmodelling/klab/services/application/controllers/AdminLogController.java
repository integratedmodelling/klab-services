package org.integratedmodelling.klab.services.application.controllers;

import java.nio.file.Path;
import java.security.Principal;
import java.time.ZoneId;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.services.logging.*;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.klab.services.logging.DiagnosticLogReader;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

/** Administrative access to vetted diagnostics in the effective Spring-configured service log. */
@RestController
@Secured({Role.ADMINISTRATOR, Role.SYSTEM})
public class AdminLogController {
  private static final org.slf4j.Logger AUDIT = org.slf4j.LoggerFactory.getLogger("klab.admin.log.audit");
  private final Environment environment;
  private volatile DiagnosticLogReader reader;
  @org.springframework.beans.factory.annotation.Autowired
  org.integratedmodelling.klab.services.application.ServiceNetworkedInstance<?> instance;

  public AdminLogController(Environment environment) { this.environment = environment; }

  @jakarta.annotation.PreDestroy
  public void close() throws java.io.IOException { if (reader != null) reader.close(); }

  private String actor(Principal principal) {
    if (principal instanceof EngineAuthorization authorization
        && authorization.isAuthenticated()
        && authorization.getScope() instanceof ServiceUserScope scope
        && scope.isAuthorized(CRUDOperation.ADMINISTER)
        && scope.getUser() != null && scope.getUser().getId() != null)
      return fingerprint(scope.getUser().getId());
    AUDIT.info("ADMIN_LOG_ACCESS actor=unavailable outcome=DENIED");
    throw new KlabAuthorizationException("ADMINISTER permission is required for service logs");
  }

  private static String fingerprint(String identity) {
    try {
      return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  private DiagnosticLogReader reader() {
    var current = reader;
    if (current == null) synchronized (this) {
      current = reader;
      if (current == null) {
        // Spring resolves placeholders/precedence. A literal '~' is not assumed to expand.
        String configured = environment.getProperty("logging.file.name");
        if (configured == null || configured.isBlank() || configured.startsWith("~"))
          throw new DiagnosticLogReader.Failure(503, "Resolved service log configuration unavailable");
        try { current = new DiagnosticLogReader(Path.of(configured), ZoneId.systemDefault()); }
        catch (IllegalArgumentException invalid) {
          throw new DiagnosticLogReader.Failure(503, "Resolved service log configuration invalid");
        }
        reader = current;
      }
    }
    return current;
  }

  @GetMapping(ServicesAPI.ADMIN.LOGS_CAPABILITIES)
  public ResponseEntity<LogCapabilities> capabilities(Principal principal) {
    String actor=actor(principal);
    AUDIT.info("ADMIN_LOG_ACCESS actorHash={} operation=CAPABILITIES",actor);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(reader().capabilities());
  }

  @PostMapping(ServicesAPI.ADMIN.LOGS_QUERY)
  public ResponseEntity<LogPage> query(@RequestBody LogQuery request, Principal principal) {
    String actor = actor(principal);
    try {
      var page=reader().query(request, actor)
          .forService(instance==null?null:instance.klabService().serviceId());
      AUDIT.info("ADMIN_LOG_ACCESS actorHash={} operation=QUERY outcome=OK records={}",actor,page.records().size());
      return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(page);
    } catch(DiagnosticLogReader.Failure failure) {
      AUDIT.info("ADMIN_LOG_ACCESS actorHash={} operation=QUERY status={}",actor,failure.status());
      throw failure;
    }
  }

  @ExceptionHandler(DiagnosticLogReader.Failure.class)
  public ResponseEntity<ProblemDetail> failure(DiagnosticLogReader.Failure failure) {
    var response = ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore().cachePrivate());
    if (failure.status() == 429) response.header(HttpHeaders.RETRY_AFTER, "1");
    return response.body(ProblemDetail.forStatusAndDetail(
        HttpStatusCode.valueOf(failure.status()), failure.getMessage()));
  }

  @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> malformedQuery() {
    return ResponseEntity.badRequest().cacheControl(CacheControl.noStore().cachePrivate())
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid log query body"));
  }

  @ExceptionHandler(KlabAuthorizationException.class)
  public ResponseEntity<ProblemDetail> denied() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore().cachePrivate())
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,"Administrative log access denied"));
  }
}
