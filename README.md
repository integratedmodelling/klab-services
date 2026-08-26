# k.LAB services

`klab-services` is the Java 21 reference implementation of k.LAB's distributed semantic-modelling and digital-twin service stack.

k.LAB accepts an observation request expressed as meaning in context - conceptually, `observe <concept> in <context>` - and turns it into a semantically validated, executable dataflow. The result is an observation stored in a digital twin together with its semantics, provenance and lifecycle state. This enables independent providers to contribute data, models, components and compute to a shared semantic knowledge commons without moving every asset into one platform.

The current architecture separates four kinds of ownership:

```text
Resources -> available projects, data, models, components and adapters
Reasoner  -> worldview semantics and context-appropriate strategies
Resolver  -> resolution graphs and executable dataflows
Runtime   -> sessions, digital twins, execution, storage and provenance
```

An Engine authenticates the user, starts or discovers services and creates scoped workflows across them. User, session and context scopes preserve identity, permissions, service selection and digital-twin position across process boundaries.

For the conceptual and architectural overview, read [docs/KLAB.md](docs/KLAB.md). For a code-oriented trace of the service stack, read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Repository layout

| Module | Purpose |
| --- | --- |
| `klab.core.api` | Portable service contracts, scopes, language objects, observations, dataflows and digital-twin APIs |
| `klab.core.common` | Shared domain implementations and utilities |
| `klab.core.services` | Service bases, clients, scope management and common service infrastructure |
| `klab.services.resources` | Resource, workspace, project, component and adapter management |
| `klab.services.reasoner` | Worldview loading, semantic inference and observation strategies |
| `klab.services.resolver` | Context-sensitive resolution graphs and dataflow compilation |
| `klab.services.runtime` | Digital twins, knowledge graphs, transactions, scheduling, execution, storage and provenance |
| `*.server` modules | Stand-alone server applications for the corresponding core services |
| `klab.modeler` | Modeler-side orchestration and client functionality |
| `klab.cli` | Command-line tooling |
| `klab.distribution` | Distribution and local service-stack management |
| `support` | Graph database, language-server and AMQP support modules |

## Build

The project uses Maven and requires JDK 21. From the repository root:

```powershell
.\mvnw.cmd clean install
```

The build is a multi-module reactor and may require access to configured snapshot repositories and sibling k.LAB language artifacts. Use targeted module builds while developing when the complete dependency set is not available, for example:

```powershell
.\mvnw.cmd -pl klab.services.resolver -am test
```

Individual server modules contain their Spring application configuration under `src/main/resources`. Deployment, certificates, service discovery and components are environment-specific; do not assume that compiling the repository creates a connected public k.LAB network.

## Documentation

- [k.LAB technical note](docs/KLAB.md): the semantic commons, service architecture, federated network and composable digital twins
- [Architecture](docs/ARCHITECTURE.md): service ownership and end-to-end observation lifecycle
- [Semantic modeling](docs/SEMANTIC_MODELING.md), [observables](docs/OBSERVABLES.md) and [ODO-IM](docs/ODO_IM.md): the knowledge model
- [Resources](docs/RESOURCES.md): resource service contract
- [Resolution](docs/RESOLUTION.md): resolver internals, dataflow compilation, limitations and tests
- [Scopes](docs/SCOPES.md): identity, propagation and digital-twin lifetime
- [Storage](docs/STORAGE.md) and [provenance](docs/PROVENANCE.md): runtime state and traceability
- [Agent compiler](docs/AGENT_COMPILER.md): k.Actors runtime and behavior execution
- [Components](docs/COMPONENTS.md): plug-ins, adapters and service extensions

## Status

This repository is under active development toward the k.LAB 1.0 architecture. The four core service APIs and implementations are present, but not every network capability is complete. In particular, global semantic and capability discovery is planned work, and some external dataflow transport and execution paths remain transitional. The older node/engine terminology describes pre-1.0 deployments and should not be used as the organizing model for new code.

k.LAB is licensed under the [GNU Affero General Public License v3](LICENSE.txt). Project and partnership information is available from [Integrated Modelling](https://integratedmodelling.org/).
