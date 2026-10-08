package org.integratedmodelling.klab.services.reasoner.internal;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.util.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.Codelist;
import org.integratedmodelling.klab.api.knowledge.impl.CodelistImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityCodelistResponse.*;

/** Durable review ledger. Publish only after an atomic, forced write; never recover corruption as empty. */
public final class AuthorityCodelists {
  public record State(long revision, List<Proposal> proposals) {}
  private final AuthorityBindings.Binding binding;
  private final Map<String, Codelist> seeds;
  private final Map<String, CodelistPolicy> policies;
  private final Path file;
  private State state;

  public AuthorityCodelists(AuthorityBindings.Binding binding, Path directory) {
    this.binding = binding;
    var configured = binding.request().parameters().get("codelists");
    var result = new LinkedHashMap<String, Codelist>();
    var configuredPolicies = new LinkedHashMap<String, CodelistPolicy>();
    if (configured != null) {
      if (!(configured instanceof Map<?, ?> names)) throw new IllegalArgumentException("codelists must map provider IDs to local namespaces");
      var advertised = Objects.requireNonNull(binding.provider().getCodelistDefinitions(binding.id()),
          "Provider returned no codelist definitions");
      for (var entry : names.entrySet()) {
        if (!(entry.getKey() instanceof String id) || id.isBlank() || !(entry.getValue() instanceof String namespace)
            || !namespace.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*"))
          throw new IllegalArgumentException("Invalid codelist namespace binding");
        var definition = advertised.get(id);
        if (advertised.containsKey(id) && definition == null)
          throw new IllegalArgumentException("Provider returned an invalid codelist definition for " + id);
        var seed = definition == null ? new CodelistImpl() : definition.codelist();
        var snapshot = new CodelistImpl();
        snapshot.setName(id);
        for (var keyCode : seed.codes()) {
          if (!(keyCode instanceof String alias) || !(seed.value(keyCode) instanceof String code))
            throw new IllegalArgumentException("Authority codelists require string aliases and authority codes");
          alias(alias);
          snapshot.getEntries().add(new CodelistImpl.Entry(requestAuthority(binding), alias, canonical(code), null, true));
        }
        if (result.putIfAbsent(namespace, snapshot) != null)
          throw new IllegalArgumentException("Duplicate codelist namespace " + namespace);
        configuredPolicies.put(namespace, new CodelistPolicy(id, definition != null,
            definition == null || definition.acceptsProposals()));
      }
    }
    seeds = Map.copyOf(result);
    policies = Map.copyOf(configuredPolicies);
    String key = binding.request().worldview() + "\0" + binding.request().name() + "\0"
        + binding.request().rootIdentity() + "\0" + binding.request().parameters().get("urn");
    file = directory == null ? null : directory.resolve(CachedAuthority.digest(key) + ".json");
    state = new State(0, List.of());
    if (file != null && Files.exists(file)) {
      try { state = Utils.Json.newObjectMapper().readValue(Files.readAllBytes(file), State.class); }
      catch (Exception e) { throw new IllegalStateException("Cannot read authority proposal ledger", e); }
      if (state == null || state.proposals() == null) throw new IllegalStateException("Invalid authority proposal ledger");
    }
    // Validate seed shape before making the bridge available.
    published();
  }

  private static String requestAuthority(AuthorityBindings.Binding binding) { return binding.request().name(); }

  public Set<String> namespaces() { return seeds.keySet(); }
  public synchronized long revision() { return state.revision(); }

  private String canonical(String code) {
    if (code == null || code.isBlank()) throw new IllegalArgumentException("An authority code is required");
    var identity = binding.provider().resolveIdentity(binding.id(), code);
    if (identity == null || identity.getId() == null || identity.getId().isBlank()
        || org.integratedmodelling.klab.api.utils.Utils.Notifications.hasErrors(identity.getNotifications()))
      throw new IllegalArgumentException("Authority code cannot be resolved");
    AuthorityIdentitySyntax.encode(binding.request().name(), identity.getId());
    return identity.getId();
  }

  private void namespace(String namespace) {
    if (namespace == null || !seeds.containsKey(namespace)) throw new IllegalArgumentException("Codelist namespace is not configured");
  }
  private static void alias(String alias) {
    if (alias == null || !alias.matches("[A-Z][A-Za-z0-9_]*"))
      throw new IllegalArgumentException("Alias must be a concept identifier");
  }

  private Map<String, LinkedHashMap<String, String>> published() {
    var result = new LinkedHashMap<String, LinkedHashMap<String, String>>();
    seeds.forEach((namespace, seed) -> {
      var entries = new LinkedHashMap<String, String>();
      for (var key : seed.codes()) {
        if (!(key instanceof String name) || !(seed.value(key) instanceof String code))
          throw new IllegalArgumentException("Authority codelists require string aliases and authority codes");
        alias(name);
        entries.put(name, code);
      }
      result.put(namespace, entries);
    });
    for (var proposal : state.proposals()) {
      var entries = result.get(proposal.namespace());
      if (entries == null) continue;
      if (proposal.status() == Status.ACCEPTED || proposal.status() == Status.REDIRECTED || proposal.status() == Status.MANAGED)
        entries.put(proposal.approvedAlias(), proposal.identity());
      else if (proposal.status() == Status.DELETED) entries.remove(proposal.approvedAlias());
    }
    return result;
  }

  private boolean protectedAlias(String namespace, String alias) {
    return seeds.get(namespace).value(alias) != null || state.proposals().stream().anyMatch(p ->
        p.namespace().equals(namespace) && alias.equals(p.approvedAlias())
        && (p.status() == Status.ACCEPTED || p.status() == Status.REDIRECTED));
  }

  private void requireEditable(String namespace, String alias) {
    if (resolve(namespace, alias) == null) throw new NoSuchElementException("Alias is not published");
    if (protectedAlias(namespace, alias)) throw new IllegalArgumentException("Community and predefined aliases are read-only");
    if (state.proposals().stream().noneMatch(p -> p.namespace().equals(namespace)
        && alias.equals(p.approvedAlias()) && p.status() == Status.MANAGED))
      throw new IllegalArgumentException("Only directly managed aliases can be edited");
  }

  public synchronized String resolve(String namespace, String alias) {
    var entries = published().get(namespace);
    return entries == null ? null : entries.get(alias);
  }

  public synchronized AuthorityCodelistResponse snapshot() {
    var lists = new LinkedHashMap<String, Codelist>();
    published().forEach((namespace, entries) -> {
      var list = new CodelistImpl();
      list.setName(namespace); list.setUrn(namespace); list.setWorldview(binding.request().worldview());
      list.setAuthorityId(binding.request().name()); list.setRootConceptId(binding.request().rootIdentity());
      entries.forEach((alias, code) -> list.getEntries().add(new CodelistImpl.Entry(
          binding.request().name(), alias, code, null, true)));
      lists.put(namespace, list);
    });
    return new AuthorityCodelistResponse(state.revision(), Map.copyOf(lists), List.copyOf(state.proposals()), policies);
  }

  public synchronized AuthorityCodelistResponse execute(AuthorityCodelistRequest request, String actor) {
    if (request.operation() == AuthorityCodelistRequest.Operation.LIST) return snapshot();
    namespace(request.namespace());
    var next = new ArrayList<>(state.proposals());
    long revision = state.revision() + 1;
    if (request.operation() == AuthorityCodelistRequest.Operation.SUBMIT) {
      if (!policies.get(request.namespace()).acceptsProposals())
        throw new IllegalArgumentException("Codelist " + request.namespace() + " does not accept proposals");
      alias(request.alias());
      String identity = canonical(request.identity());
      String id = CachedAuthority.digest(request.namespace() + "\0" + request.alias() + "\0" + identity);
      var source = request.source();
      if (source != null && (source.document() == null || source.document().isBlank()
          || source.sourceHash() == null || !source.sourceHash().matches("[a-fA-F0-9]{64}")
          || source.offset() < 0 || source.length() <= 0)) throw new IllegalArgumentException("Invalid proposal source");
      var previous = next.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
      if (previous != null) {
        if (source == null || previous.sources().contains(source)) return snapshot();
        var sources = new ArrayList<>(previous.sources()); sources.add(source);
        next.set(next.indexOf(previous), new Proposal(previous.id(), previous.namespace(), previous.alias(),
            previous.identity(), previous.status(), previous.approvedAlias(), previous.message(),
            previous.submittedBy(), previous.reviewedBy(), revision, List.copyOf(sources)));
      } else {
        next.add(new Proposal(id, request.namespace(), request.alias(), identity, Status.PENDING,
            null, null, actor, null, revision, source == null ? List.of() : List.of(source)));
      }
    } else {
      if (request.expectedRevision() != state.revision()) throw new ConcurrentModificationException("Proposal ledger changed");
      if (request.operation() == AuthorityCodelistRequest.Operation.CREATE
          || request.operation() == AuthorityCodelistRequest.Operation.UPDATE) {
        boolean update = request.operation() == AuthorityCodelistRequest.Operation.UPDATE;
        String chosen = update ? request.approvedAlias() : request.alias();
        alias(chosen);
        String identity = canonical(request.identity());
        if (update) requireEditable(request.namespace(), request.alias());
        if (protectedAlias(request.namespace(), chosen))
          throw new IllegalArgumentException("Community and predefined aliases are read-only");
        if ((!update || !chosen.equals(request.alias())) && resolve(request.namespace(), chosen) != null)
          throw new IllegalArgumentException("Alias already exists");
        if (update) {
          next.add(new Proposal(UUID.randomUUID().toString(), request.namespace(), request.alias(),
              resolve(request.namespace(), request.alias()), Status.DELETED, request.alias(),
              request.message(), actor, actor, revision, List.of()));
        }
        next.add(new Proposal(UUID.randomUUID().toString(), request.namespace(), chosen, identity,
            Status.MANAGED, chosen, request.message(), actor, actor, revision, List.of()));
      } else if (request.operation() == AuthorityCodelistRequest.Operation.DELETE) {
        requireEditable(request.namespace(), request.alias());
        alias(request.alias());
        String code = resolve(request.namespace(), request.alias());
        if (code == null) throw new NoSuchElementException("Alias is not published");
        for (int i = 0; i < next.size(); i++) {
          var previous = next.get(i);
          if (previous.namespace().equals(request.namespace()) && request.alias().equals(previous.approvedAlias())
              && (previous.status() == Status.ACCEPTED || previous.status() == Status.REDIRECTED))
            next.set(i, new Proposal(previous.id(), previous.namespace(), previous.alias(), previous.identity(),
                Status.DELETED, previous.approvedAlias(), request.message(), previous.submittedBy(), actor,
                revision, previous.sources()));
        }
        next.add(new Proposal(UUID.randomUUID().toString(), request.namespace(), request.alias(), code,
            Status.DELETED, request.alias(), request.message(), actor, actor, revision, List.of()));
      } else {
        var previous = next.stream().filter(p -> p.id().equals(request.proposalId())).findFirst()
            .orElseThrow(() -> new NoSuchElementException("Unknown proposal"));
        if (!previous.namespace().equals(request.namespace()) || previous.status() != Status.PENDING)
          throw new IllegalArgumentException("Proposal is not pending in this namespace");
        if (request.decision() == null) throw new IllegalArgumentException("Decision required");
        String approved = null;
        Status status = Status.REJECTED;
        if (request.decision() == AuthorityCodelistRequest.Decision.ACCEPT) {
          if (!canonical(previous.identity()).equals(previous.identity()))
            throw new IllegalArgumentException("Authority identity changed; submit a new proposal");
          approved = request.approvedAlias() == null ? previous.alias() : request.approvedAlias();
          alias(approved);
          String old = resolve(previous.namespace(), approved);
          if (old != null && !old.equals(previous.identity())) throw new IllegalArgumentException("Alias already denotes another identity");
          final String chosen = approved;
          if (next.stream().anyMatch(p -> p.namespace().equals(previous.namespace())
              && chosen.equals(p.approvedAlias()) && (p.status() == Status.ACCEPTED || p.status() == Status.REDIRECTED) && !p.identity().equals(previous.identity())))
            throw new IllegalArgumentException("A historical alias cannot be reassigned");
          status = approved.equals(previous.alias()) ? Status.ACCEPTED : Status.REDIRECTED;
        }
        next.set(next.indexOf(previous), new Proposal(previous.id(), previous.namespace(), previous.alias(),
            previous.identity(), status, approved, request.message(), previous.submittedBy(), actor,
            revision, previous.sources()));
      }
    }
    commit(new State(revision, List.copyOf(next)));
    return snapshot();
  }

  private void commit(State next) {
    if (file != null) {
      Path temporary = null;
      try {
        Files.createDirectories(file.getParent());
        temporary = Files.createTempFile(file.getParent(), "proposals-", ".tmp");
        byte[] bytes = Utils.Json.newObjectMapper().writeValueAsBytes(next);
        try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
          var buffer = ByteBuffer.wrap(bytes);
          while (buffer.hasRemaining()) channel.write(buffer);
          channel.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) { throw new IllegalStateException("Authority proposal was not persisted", e); }
      finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) {} }
    }
    state = next;
  }
}
