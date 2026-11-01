# Runtime configuration safety

An existing `runtime.yaml` must supply a nonblank `serviceId` and an explicit,
nonblank `graphDatabaseUrl`. Invalid or empty existing configuration stops Runtime
construction rather than deleting the file, changing its identity, or selecting
the default database. Configuration bytes are preserved on rejection.

This intentionally rejects older hand-written configuration that omitted the
backend and relied on the Java default. Review and explicitly set the intended
backend before restarting; do not use `--clean` to work around a parse error.

An absent file still permits ordinary first-run defaults. Explicit `--clean`
still deliberately replaces configuration with a new identity and defaults.
Neither path connects to the backend while loading configuration.

For isolated fixtures, set
`-Dklab.runtime.requiredGraphDatabaseUrl=bolt://127.0.0.1:17687` to require an exact
backend match. The check runs during constructor admission, before superclass
startup, component loading or scheduled initialization, and again immediately
before constructing the Runtime graph client. A mismatch also prevents first-run
or explicit-clean configuration from being written. The property is a guard,
not an override: it never silently changes the configured backend.

This guard covers the Runtime's custom graph client, not every driver in the
Spring application. Fixtures must separately set `-Dspring.neo4j.uri` to their
private endpoint and preflight generated YAML with the production typed loader
before launching any service. Process ownership, advertised/listener URL checks,
and ordinary-user authorization checks remain necessary.

These safeguards do not establish scientific workflow acceptance or authorize a
new live test batch.
