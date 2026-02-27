# Spring Neo4j starter service

Spring Boot application that:

- Accepts only localhost connections.
- Does not require endpoint authentication.
- Exposes:
  - `GET /capabilities`
  - `GET /health`
- Starts an embedded Neo4j server at startup.
- Publishes Neo4j Bolt URL through `/capabilities`.
- Uses a Neo4j plugin drop-in directory (`var/neo4j/plugins`). Eventually should include compatible plugins ready for use (neo4j-spatial).
- Closes Neo4j cleanly at shutdown.

Should become the strategy to enable a local Neo4j DB without requiring external installs. The health endpoint can be used to verify the DB is up and running. Also makes db admin possible without going through the runtime, and enables using spatial extensions to support geospatial queries in the knowledge graph..

## Run

```bash
./mvnw -pl localhost-neo4j-service spring-boot:run
```

## Build

```bash
./mvnw -pl localhost-neo4j-service clean package
```
## Configuration

All runtime options are in `src/main/resources/application.yml` and can be overridden via environment variables or external YAML.

Key properties:

- `server.*` for HTTP bind address/port.
- `app.security.allowed-hosts` for allowed caller addresses.
- `app.neo4j.*` for embedded Neo4j host/ports/directories.
- `app.neo4j.config.*` to pass arbitrary Neo4j settings.
