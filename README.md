# k.LAB services

`klab-services` is the Java 21 reference implementation of k.LAB's distributed semantic-modelling and digital-twin service stack.

k.LAB accepts an observation request expressed as meaning in context -
conceptually, `observe <observable> in <context>` - and turns it into a
semantically validated, executable dataflow that creates live scientific artifacts incarnating the observation. The [observable expression language](docs/OBSERVABLES.md) supplies the query and semantic annotation grammar used to express meaning and catalog candidate assets. The result of an `observe` query is an _observation_, hosted in a _digital twin_ of configurable persistence, indissolubly linked to its semantics, provenance and lifecycle state. The k.LAB paradigm enables independent providers to contribute data, models, components and compute to a shared semantic knowledge commons, and make it immediately actionable without moving every asset into one platform or specifically planning for interoperability.

The current architecture separates four kinds of artifacts, whose ownership is assigned to different services:

```text
Resources -> projects, data, models, components and adapters
Reasoner  -> worldview semantics and context-appropriate strategies
Resolver  -> resolution graphs and executable dataflows
Runtime   -> sessions, digital twins, execution, storage and provenance
```

The services are used through an Engine process, which authenticates a human user, institution or agent, discovers services (possibly starting local instances) and creates an initial _user scope_ that gives access to coordinated pathways across services to implement the k.LAB observation workflow. User, session and context scope abstractions are used to drive the workflow, preserving identity, permissions, service selection and digital-twin position across process boundaries.

For the conceptual and architectural overview, read [docs/KLAB.md](docs/KLAB.md). For a code-oriented trace of the service stack, read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## User-facing languages

The semantic commons is instrumented through three languages, all sharing one common
_observable_ syntax to express meaning. Assets written in these languages are assembled into versioned projects, hosted and indexed by Resources services. The latter make them available as semantic assets to their intended communities, forming the k.LAB semantic web.

| Language | Layer and purpose |
| --- | --- |
| [Observable expressions](docs/OBSERVABLES.md) | Shared semantic query and asset-description syntax. inherited by all three languages |
| [Worldview ontology language](docs/ONTOLOGY_LANGUAGE.md) (`.kwv`) | Defines the concepts and relationships maintained by a worldview community |
| [k.IM](docs/KIM.md) (`.kim`) | Publishes context-explicit models, including resource annotations, algorithms with dependencies, and semantic bridges to external services and models |
| [k.Actors](docs/AGENTS.md) (`.kactor`) | Gives observations, digital twins, users, and sessions reactive behavior, enabling agent-based models, interactive applications and monitoring systems |

Observable expressions are used directly to specialized concepts in `.kwv` definitions and in `.kim`
models to annotate resources and outputs, and to specify dependencies. In value positions, such as in function parameters or metadata, they are written as `{{ ... }}` semantic literals;
k.Actors uses only this literal form. Together the languages separate shared
meaning, ways of producing observations, and behavior after observations enter
a digital twin.

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

The k.LAB Modeler IDE, a specialized Maven plug-in to aid deployment and testing, and several plug-in components are available in this same Github repository as separate projects.

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

No release is available yet, but the artifacts are deployed as SNAPSHOTs in the Maven Central snapshot repository.

## Documentation

- [k.LAB technical note](docs/KLAB.md): the semantic commons, service architecture, federated network and composable digital twins
- [Architecture](docs/ARCHITECTURE.md): service ownership and end-to-end observation lifecycle
- [Observable expressions](docs/OBSERVABLES.md), [worldview ontologies](docs/ONTOLOGY_LANGUAGE.md), [k.IM](docs/KIM.md) and [k.Actors](docs/AGENTS.md): the user-facing language guides
- [Semantic modeling](docs/SEMANTIC_MODELING.md) and [ODO-IM](docs/ODO_IM.md): the conceptual knowledge model
- [Resources](docs/RESOURCES.md): resource service contract
- [Resolution](docs/RESOLUTION.md): resolver internals, dataflow compilation, limitations and tests
- [Scopes](docs/SCOPES.md): identity, propagation and digital-twin lifetime
- [Storage](docs/STORAGE.md) and [provenance](docs/PROVENANCE.md): runtime state and traceability
- [Agent compiler](docs/AGENT_COMPILER.md): k.Actors runtime and behavior execution internals
- [Components](docs/COMPONENTS.md): plug-ins, adapters and service extensions

## Status

This repository is under active development toward the k.LAB 1.0 architecture. The four core service APIs and implementations are present, but not every network capability is complete. In particular, global semantic and capability discovery is planned work, and some external dataflow transport and execution paths remain transitional. The older node/engine terminology describes pre-1.0 deployments and should not be used as the organizing model for new code.

k.LAB is licensed under the [GNU Affero General Public License v3](LICENSE.txt). Project and partnership information is available from [Integrated Modelling](https://integratedmodelling.org/).
