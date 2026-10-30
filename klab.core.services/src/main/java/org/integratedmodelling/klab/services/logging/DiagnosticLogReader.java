package org.integratedmodelling.klab.services.logging;

import java.io.*;
import java.nio.*;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import org.integratedmodelling.klab.api.services.logging.*;

/** Bounded active-file diagnostic reader. Unknown messages never cross the remote boundary. */
public final class DiagnosticLogReader implements AutoCloseable {
  public static final int MAX_SCAN = 256 * 1024, MAX_EVENT = 64 * 1024, MAX_CURSORS = 256;
  public static final String POLICY = "vetted-diagnostics-v1";
  private static final Pattern HEADER = Pattern.compile(
      "^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})]\\s+(?:-\\s+[^ ]*\\s+)?(TRACE|DEBUG|INFO|WARN|ERROR)\\s+\\[[^]\\r\\n]{0,256}]\\s+(?:---\\s+)?([A-Za-z0-9_.$]{1,256}): (.*)$");
  private static final Pattern FIELD = Pattern.compile("(?:^| )([A-Za-z]+)=([^ ]+)");
  private static final Pattern FRAME = Pattern.compile(
      "^\\s*at ([A-Za-z0-9_.$/@+\\-]+)\\(((?:[A-Za-z0-9_$.-]+\\.(?:java|groovy|kt|scala|clj)(?::[0-9]+)?)|Native Method|Unknown Source)\\)$");
  private static final Pattern CAUSE = Pattern.compile(
      "^(\\s*(?:(?:Caused by|Suppressed): )?)"
          + "([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+|[A-Za-z_$][A-Za-z0-9_$]*(?:Exception|Error|Failure|Throwable|Redacted))(?::.*)?$");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
  private final Path file;
  private final Path registeredParent;
  private final WatchService rotationWatch;
  private long sourceEpoch;
  private final ZoneId zone;
  private final Clock clock;
  private final String instance = UUID.randomUUID().toString();
  private final Map<String, State> cursors = new LinkedHashMap<>();
  private final Map<String, Long> lastQuery = new HashMap<>();

  public static final class Failure extends RuntimeException {
    private final int status;
    public Failure(int status, String safeMessage) { super(safeMessage); this.status = status; }
    public int status() { return status; }
  }

  public DiagnosticLogReader(Path file, ZoneId zone) { this(file, zone, Clock.systemUTC()); }
  DiagnosticLogReader(Path file, ZoneId zone, Clock clock) {
    this.file = Objects.requireNonNull(file).toAbsolutePath().normalize();
    try {
      this.registeredParent = this.file.getParent().toRealPath();
      this.rotationWatch = this.file.getFileSystem().newWatchService();
      registeredParent.register(rotationWatch, StandardWatchEventKinds.ENTRY_CREATE,
          StandardWatchEventKinds.ENTRY_DELETE);
    }
    catch (IOException unavailable) { throw new Failure(503, "Configured log directory unavailable"); }
    this.zone = zone; this.clock = clock;
    if (file.toString().startsWith("~")) throw new IllegalArgumentException("Log path must be resolved by Spring");
  }
  public LogCapabilities capabilities() {
    return new LogCapabilities(instance, List.of("service"), 200, 1000, MAX_SCAN,
        MAX_EVENT, zone.getId(), POLICY, false, false);
  }

  /** Actor is resolved server-side from the authenticated administrative scope. */
  public synchronized LogPage query(LogQuery query, String actor) {
    if (actor == null || actor.isBlank()) throw new Failure(403, "Administrative actor required");
    if (query == null || !"service".equals(query.streamId())) throw new Failure(400, "Unknown log stream");
    int limit = query.limit() == null ? 200 : query.limit();
    if (limit < 1 || limit > 1000) throw new Failure(400, "Log page limit must be 1..1000");
    String filter = filter(query);
    long now = clock.millis();
    cursors.entrySet().removeIf(e -> now - e.getValue().access > 600_000);
    lastQuery.entrySet().removeIf(e -> now - e.getValue() > 600_000);
    Long previous = lastQuery.get(actor);
    if (previous != null && now - previous < 200) throw new Failure(429, "Log query budget exceeded");
    if (lastQuery.size() >= MAX_CURSORS && !lastQuery.containsKey(actor))
      throw new Failure(429, "Log query capacity exceeded");
    lastQuery.put(actor, now);
    try {
      if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
        throw new Failure(503, "Configured log source unavailable");
      // A resolved parent prevents a replacement symlink from redirecting a registered file.
      Path parent = file.getParent().toRealPath();
      if (!parent.equals(registeredParent) || !file.toRealPath().getParent().equals(parent))
        throw new Failure(503, "Configured log source changed");
      var attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      observeRotation();
      String identity = String.valueOf(attrs.fileKey()) + ":" + attrs.creationTime().toMillis()+":"+sourceEpoch;
      State state;
      if (query.cursor() == null) {
        if (cursors.size() >= MAX_CURSORS) throw new Failure(429, "Log cursor capacity exceeded");
        state = new State(actor, filter, identity, attrs.size(), now);
        state.prefix = fingerprint(0, Math.min(4096, state.boundary));
        state.tail = fingerprint(Math.max(0, state.boundary-4096), Math.min(4096,state.boundary));
      } else {
        if (query.cursor().length() > 128 || !query.cursor().matches("[a-f0-9-]{36}"))
          throw new Failure(400, "Invalid log cursor");
        State saved = cursors.get(query.cursor());
        if (saved == null) throw new Failure(410, "Log cursor expired or service restarted; start a new query");
        if (!saved.actor.equals(actor)) throw new Failure(403, "Log cursor belongs to another actor");
        if (!saved.filter.equals(filter)) throw new Failure(400, "Log cursor filters changed");
        if (!saved.identity.equals(identity))
          throw new Failure(410, "Log source rotated or truncated; start a new query (history gap)");
        if (attrs.size() < saved.boundary)
          { sourceEpoch++; throw new Failure(410, "Log source truncated; start a new query (history gap)"); }
        if (!saved.prefix.equals(fingerprint(0,Math.min(4096,saved.boundary)))
            || !saved.tail.equals(fingerprint(Math.max(0,saved.boundary-4096),Math.min(4096,saved.boundary))))
          { sourceEpoch++; throw new Failure(410,"Log retained boundary changed; start a new query (history gap)"); }
        state = saved.copy();
        if (query.follow()) {
          if (state.position != state.boundary) throw new Failure(400, "Read the snapshot before following");
          state.boundary = attrs.size();
          state.prefix=fingerprint(0,Math.min(4096,state.boundary));
          state.tail=fingerprint(Math.max(0,state.boundary-4096),Math.min(4096,state.boundary));
        }
      }
      var records = new ArrayList<LogRecord>();
      int omitted = 0, responseBudget = 1024 * 1024 - 4096;
      try (SeekableByteChannel channel = Files.newByteChannel(file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
        channel.position(state.position);
        var buffer = ByteBuffer.allocate((int) Math.min(MAX_SCAN, state.boundary - state.position));
        while (buffer.hasRemaining() && channel.read(buffer) > 0) {}
        buffer.flip();
        while (buffer.hasRemaining() && records.size() < limit) {
          byte b = buffer.get(); state.position++;
          if (b == '\n') {
            String line = state.line.toString(StandardCharsets.UTF_8).replaceFirst("\\r$", "");
            state.line.reset();
            var match = HEADER.matcher(line);
            if (match.matches()) {
              if (state.event != null) {
                if (eligible(state.event, query)) {
                  var record = state.event.record(false);
                  int cost = recordBudget(record);
                  if (cost > responseBudget) {
                    // Leave this header unread and retain the preceding event for the next page.
                    state.position = state.lineStart;
                    break;
                  }
                  records.add(record); responseBudget -= cost;
                } else omitted++;
              }
              state.event = event(match, state.lineStart, state.lineTruncated, state.identity);
            } else if (state.event != null) state.event.append(safeContinuation(line), state.lineTruncated);
            state.lineStart = state.position; state.lineTruncated = false;
          } else if (state.line.size() < 8192) state.line.write(b);
          else state.lineTruncated = true;
        }
      }
      // EOF can precede a later stack continuation. Return an updatable partial event, retaining it.
      boolean caughtUp = state.position == state.boundary;
      if (caughtUp && state.event != null && records.size() < limit && eligible(state.event, query)
          && recordBudget(state.event.record(true)) <= responseBudget)
        records.add(state.event.record(true));
      var after = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      observeRotation();
      if (!identity.equals(String.valueOf(after.fileKey()) + ":" + after.creationTime().toMillis()+":"+sourceEpoch)
          || after.size() < state.boundary)
        throw new Failure(410, "Log source changed while reading; start a new query (history gap)");
      state.access = now;
      String next = UUID.randomUUID().toString();
      if (cursors.size() >= MAX_CURSORS) cursors.remove(cursors.keySet().iterator().next());
      cursors.put(next, state);
      return new LogPage(instance, zone.getId(), List.copyOf(records), next, caughtUp,
          state.boundary, omitted, POLICY);
    } catch (IOException error) {
      throw new Failure(503, "Configured log source could not be read");
    }
  }

  private String fingerprint(long offset,long count) throws IOException {
    try(var channel=Files.newByteChannel(file,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS))) {
      var bytes=ByteBuffer.allocate((int)count);channel.position(offset);
      while(bytes.hasRemaining() && channel.read(bytes)>0) {}
      return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
          .digest(java.util.Arrays.copyOf(bytes.array(),bytes.position())));
    } catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
  }

  private void observeRotation() {
    try {
      WatchKey key=rotationWatch.poll(10, java.util.concurrent.TimeUnit.MILLISECONDS);
      while(key!=null) {
        for(var event:key.pollEvents()) {
          if(event.kind()==StandardWatchEventKinds.OVERFLOW
              || file.getFileName().equals(event.context())) sourceEpoch++;
        }
        if(!key.reset()) throw new Failure(503,"Configured log directory unavailable");
        key=rotationWatch.poll();
      }
    } catch(InterruptedException interrupted) {
      Thread.currentThread().interrupt();throw new Failure(503,"Log read interrupted");
    }
  }

  @Override public synchronized void close() throws IOException { rotationWatch.close(); cursors.clear(); }

  private static int recordBudget(LogRecord record) {
    // All emitted values are ASCII vetted text; six bytes per character also bounds JSON escaping.
    int chars = record.eventId().length()+record.timestamp().length()+record.level().length()
        +record.logger().length()+record.message().length()+record.stackTrace().length();
    for (var entry : record.diagnostics().entrySet()) chars += entry.getKey().length()+entry.getValue().length();
    return chars * 6 + 1024;
  }

  private String filter(LogQuery q) {
    try {
      if(q.from()!=null && q.from().length()>64 || q.to()!=null && q.to().length()>64)
        throw new IllegalArgumentException();
      Instant from = q.from() == null ? null : Instant.parse(q.from());
      Instant to = q.to() == null ? null : Instant.parse(q.to());
      if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException();
      if (q.minLevel() != null && !List.of("TRACE","DEBUG","INFO","WARN","ERROR").contains(q.minLevel()))
        throw new IllegalArgumentException();
      if (q.loggerPrefix() != null && !q.loggerPrefix().matches("[A-Za-z0-9_.$]{1,256}"))
        throw new IllegalArgumentException();
      return Arrays.asList(q.from(), q.to(), q.minLevel(), q.loggerPrefix()).toString();
    } catch (RuntimeException invalid) { throw new Failure(400, "Invalid log filter"); }
  }
  private boolean eligible(Event event, LogQuery q) {
    if (!event.vetted) return false;
    var levels = List.of("TRACE","DEBUG","INFO","WARN","ERROR");
    return (q.from() == null || !event.time.isBefore(Instant.parse(q.from())))
        && (q.to() == null || !event.time.isAfter(Instant.parse(q.to())))
        && (q.minLevel() == null || levels.indexOf(event.level) >= levels.indexOf(q.minLevel()))
        && (q.loggerPrefix() == null || event.logger.startsWith(q.loggerPrefix()));
  }
  private Event event(Matcher m, long offset, boolean truncated, String sourceIdentity) {
    var fields = new LinkedHashMap<String,String>();
    String message = m.group(4).replaceFirst("^(Resources|Reasoner|Runtime|Resolver|Community) service: ",""), code = null;
    for (String known : List.of("AUTHORITY_BOOTSTRAP_COMPONENT_FAILURE", "AUTHORITY_BOOTSTRAP_FAILURE",
        "Unhandled service request failure"))
      if (message.equals(known) || message.startsWith(known + " ")) { code = known; break; }
    String expected=code==null?null:switch(code) {
      case "AUTHORITY_BOOTSTRAP_COMPONENT_FAILURE" -> "org.integratedmodelling.klab.components.OwnedBootstrapDiagnostics";
      case "AUTHORITY_BOOTSTRAP_FAILURE" -> "org.integratedmodelling.klab.services.reasoner.internal.AuthorityBootstrap";
      default -> "org.integratedmodelling.common.logging.Logging";
    };
    if(expected!=null && !knownEmitter(m.group(3),expected))
      code=null;
    if (code != null) {
      Matcher f = FIELD.matcher(message);
      while (f.find()) {
        String key = f.group(1), value = f.group(2);
        boolean safe = switch (key) {
          case "phase" -> Set.of("COMPONENT_LOAD","COMPONENT_EXPORT","COMPONENT_VERIFY","COMPONENT_INSTALL",
              "AUTHORITY_CONFIGURATION","RESOLVE_PLAN","PROVIDER_INPUT","PRODUCE","VERIFY_PROOF").contains(value);
          case "reason" -> Set.of("EXCEPTION","REGISTRY_UNINITIALIZED","SERVICE_UNAVAILABLE","SOURCE_MISMATCH",
              "MD5_MISMATCH","SHA256_MISMATCH","INSTALL_REJECTED","DEPENDENCY_REFRESH_REJECTED").contains(value);
          case "component" -> value.matches("[A-Za-z0-9_.:@+\\-]{1,256}");
          case "httpStatus" -> value.matches("[1-5][0-9]{2}");
          case "input", "plan", "providers" -> value.matches("[a-fA-F0-9]{64}");
          case "bindings" -> value.matches("[0-9]{1,4}");
          default -> false;
        };
        if (safe) fields.put(key, value);
      }
    }
    Instant time;
    try { time = LocalDateTime.parse(m.group(1), TIME).atZone(zone).toInstant(); }
    catch (RuntimeException invalid) { return null; }
    String logger = m.group(3).startsWith("org.integratedmodelling.") || m.group(3).startsWith("o.i.")
        ? m.group(3) : "[redacted logger]";
    return new Event(instance + ":" + UUID.nameUUIDFromBytes(sourceIdentity.getBytes(StandardCharsets.UTF_8))
        + ":" + offset, time, m.group(2), logger,
        code == null ? "[unvetted diagnostic]" : code, fields, code != null, truncated);
  }
  private static boolean knownEmitter(String logger,String expected) {
    // Standard %logger abbreviation shortens package segments, never the producer class name.
    String[] actual=logger.split("\\."), full=expected.split("\\.");
    if(actual.length!=full.length || !actual[actual.length-1].equals(full[full.length-1])) return false;
    for(int i=0;i<actual.length-1;i++)
      if(!actual[i].equals(full[i]) && !actual[i].equals(full[i].substring(0,1))) return false;
    return true;
  }
  private static String safeContinuation(String line) {
    Matcher frame = FRAME.matcher(line);
    if (frame.matches()) return "\tat " + frame.group(1) + "(" + frame.group(2) + ")";
    Matcher cause = CAUSE.matcher(line);
    if (cause.matches()) return cause.group(1) + cause.group(2) + ": message redacted";
    if (line.matches("\\s*\\.\\.\\. [0-9]{1,5} (more|common frames omitted)")) return line.strip();
    return line.isBlank() ? "" : "[redacted continuation]";
  }
  private static final class Event {
    final String id, level, logger, message;
    final Instant time;
    final Map<String,String> fields;
    final boolean vetted;
    final StringBuilder stack = new StringBuilder();
    boolean truncated;
    Event(String id, Instant time, String level, String logger, String message,
        Map<String,String> fields, boolean vetted, boolean truncated) {
      this.id=id;this.time=time;this.level=level;this.logger=logger;this.message=message;
      this.fields=fields;this.vetted=vetted;this.truncated=truncated;
    }
    void append(String line, boolean lineTruncated) {
      if (stack.length()+line.length()+1 <= MAX_EVENT) stack.append(line).append('\n');
      else truncated=true;
      truncated |= lineTruncated;
    }
    Event copy() {
      var e=new Event(id,time,level,logger,message,fields,vetted,truncated);e.stack.append(stack);return e;
    }
    LogRecord record(boolean partial) {
      return new LogRecord(id,time.toString(),level,logger,message,stack.toString(),
          Map.copyOf(fields),partial,truncated);
    }
  }
  private static final class State {
    final String actor, filter, identity;
    long boundary, position, lineStart, access;
    String prefix,tail;
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    boolean lineTruncated;
    Event event;
    State(String actor,String filter,String identity,long boundary,long access) {
      this.actor=actor;this.filter=filter;this.identity=identity;this.boundary=boundary;this.access=access;
    }
    State copy() {
      var s=new State(actor,filter,identity,boundary,access);s.position=position;s.lineStart=lineStart;
      s.prefix=prefix;s.tail=tail;
      s.line.writeBytes(line.toByteArray());s.lineTruncated=lineTruncated;s.event=event==null?null:event.copy();return s;
    }
  }
}
