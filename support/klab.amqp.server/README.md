# Explicit scoped notification broker profile

This optional **loopback-only** profile provisions an isolated Qpid virtual host
for each explicitly approved root context. It is a broker prerequisite for the
bounded Python activity-notification work, not durable replay, Runtime routing
discovery, agent control, production deployment, or a general security clearance.

The existing default local/guest broker configuration is unchanged. **It is not
the authenticated context-notification profile.** Nothing automatically migrates
existing users, sessions, contexts, exchanges, credentials or deployments.

## Provisioning contract

An operator supplies a private UTF-8 JSON file with exactly this structure:

```json
{
  "port": 18197,
  "contexts": [
    {
      "id": "s.a",
      "reader": {
        "username": "reader-a",
        "password": "REPLACE WITH DISTINCT RANDOM BROKER SECRET"
      },
      "publisher": {
        "username": "publisher-a",
        "password": "REPLACE WITH ANOTHER RANDOM BROKER SECRET"
      }
    }
  ]
}
```

The example passwords intentionally contain spaces and **fail validation**. Use
distinct cryptographically random secrets for every account, never HTTP tokens,
certificates, provenance identities or real production credentials in a fixture.
The profile file is an operator-held secret. Protect its directory/file using
platform permissions; the application does not establish Windows/POSIX ACLs for
an existing operator file. Do not commit it, print it, or put its contents on the
command line. Input is limited to 64KiB, 1..16 contexts, ports1024..65535, safe
root `session.context` identifiers and distinct safe account names. Secret length
24..256 is a sanity check, not proof of randomness. Unknown/duplicate fields,
duplicate contexts/accounts/secrets, reserved principals and invalid identifiers
fail before broker startup. Diagnostic messages omit profile content.

Launch the product with these **JVM system properties** before its main class:

```text
-Dklab.amqp.scopedProfile=C:/private/scoped-notifications.json
-DQPID_WORK=C:/private/owned-broker-work
```

Use a separate approved work directory. The caller is responsible for its private
ownership and lifecycle. This profile uses memory stores: it is not a persisted
subscription/replay service, and restart does not reconstruct missed events.
Do not replace a retained deployment or stop an unrelated broker to obtain a port.
An occupied port is a startup failure, not authorization to reclaim the listener.

In scoped mode the listener binds only `127.0.0.1`. There is no management plugin,
default virtual-host alias, guest account, administrator account or HTTP shutdown
listener. The owner controls the process lifecycle. Remote access/TLS termination
is deliberately not configured here and needs a separately verified deployment
policy; do not forward this plaintext port to a network interface.

The generated broker and ACL definitions use Qpid's supported data-URL input,
without writing a second credential-bearing JSON/ACL file. That data URL contains
secrets: it must never be logged, exported or treated as public configuration.

## Actual authorization boundary

- A context's virtual host has exactly the same name as its root ID. For `s.a`,
  use AMQP virtual host `s.a` (URI path `/s.a`), not `/` and not another context.
- Each reader and publisher account may connect to **only its own context's
  virtual host**. Broker-enforced virtual-host access is the isolation boundary;
  exchange-binding rules in a shared virtual host are not the admitted contract.
- A publisher may create/publish only its exact root fanout exchange in that
  virtual host. It cannot consume or create queues. The reader may create,
  consume and delete transient exclusive auto-delete queues in its own host, but
  may not publish. ACLs end with default deny, and management access is denied.
- A native publisher must initialize its own context exchange before a passive
  Python receiver can attach. Closing a receiver must not delete the exchange.
- Per-host incoming message size is1MiB; queue defaults request maximum128 messages,
  16MiB and reject overflow. These defaults are not a complete per-principal
  resource quota or malicious-reader denial-of-service defense.

The profile does **not** discover or provision Runtime HTTP identities. The
operator must independently configure the native Runtime publisher's broker URI
for the same approved virtual host and publisher account. Existing
`Federation.local()`/guest defaults do not perform that mapping. If a Runtime
deployment cannot select that origin, its Runtime-backed notification acceptance
remains blocked; never restore shared-host access or forward HTTP credentials to
manufacture a pass. Root context names are not bearer permissions or provenance
agent identities.

## Tests and acceptance state

Run with Java21:

```text
./mvnw -pl klab.core.common -am -Dmaven.test.skip=true install
./mvnw -f support/klab.amqp.server/pom.xml test jacoco:report jacoco:check
```

The module's tests validate input and start an actual isolated Qpid broker with
two virtual hosts and distinct random accounts. Required gates include each
context's own delivery, all four cross-context connection denials, reader publish
denial, owned queue removal inspected independently through the broker model and
retention of the shared per-context exchanges. Arbitrary connection errors do not
count as broker permission denial. The dedicated workflow runs these tests; the
repository's ordinary skip-tests build alone is not acceptance.

Standalone socket probes of the virtual-host boundary passed with Qpid9.2.0, both
file-backed and inline configuration. The module run passes38 tests with no skips,
including actual two-context authorization and independent cleanup. The new profile
compiler has100% line/branch coverage with enforced checks, and a plain module JAR
build passes. CLI startup, source-matched CI prerequisite build and final hosted
checks remain under verification; do not merge until those gates pass. Earlier failed
attempts remain preserved privately. This profile neither repairs all broker
policies nor establishes Python/Runtime activity, TLS, replay or scientific
acceptance. No vendor contact or running deployment change is included.
