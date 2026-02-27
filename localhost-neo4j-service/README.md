# localhost-neo4j-service

Spring Boot application that:

- Accepts only localhost connections.
- Does not require endpoint authentication.
- Exposes:
  - `GET /capabilities`
  - `GET /health`
- Starts an embedded Neo4j server at startup.
- Publishes Neo4j Bolt URL through `/capabilities`.
- Uses a Neo4j plugin drop-in directory (`var/neo4j/plugins`).
- Closes Neo4j cleanly at shutdown.

## Run

```bash
./mvnw -pl localhost-neo4j-service spring-boot:run
```

## Build

```bash
./mvnw -pl localhost-neo4j-service clean package
```

## IntelliJ IDEA

1. Open the repository root as a Maven project.
2. Import module `localhost-neo4j-service`.
3. Run `LocalhostNeo4jApplication`.

## Configuration

All runtime options are in `src/main/resources/application.yml` and can be overridden via environment variables or external YAML.

Key properties are under:

- `server.*` for HTTP bind address/port.
- `app.security.allowed-hosts` for allowed caller addresses.
- `app.neo4j.*` for embedded Neo4j host/ports/directories.
- `app.neo4j.config.*` to pass arbitrary Neo4j settings.
