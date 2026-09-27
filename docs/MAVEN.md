# Maven Builds And Component Archetype

k.LAB services and components use Maven with JDK 21. This page covers the component archetype,
component packaging, local development, and publication. For the runtime component lifecycle and
installation paths, see [Components](COMPONENTS.md).

## Prerequisites

- JDK 21 selected by `JAVA_HOME`.
- Maven 3.9 or the Maven wrapper supplied by the repository being built.
- Access to the k.LAB SNAPSHOT repositories while k.LAB dependencies remain SNAPSHOTs.
- An `ossrh` server entry in Maven `settings.xml` when deploying artifacts.

Build all service modules from the `klab-services` root with:

```shell
./mvnw clean install
```

On Windows use `mvnw.cmd` instead of `./mvnw`.

## Create A Component Project

The `org.integratedmodelling:klab.component.archetype` Maven archetype is maintained in the sibling
`klab.component.archetype` repository. It follows the same project structure, extension APIs, and
`klab.product:package-component` packaging approach used by `klab.component.generators` and
`klab.component.geospatial`.

For local archetype development, install it first:

```shell
cd ../klab.component.archetype
mvn clean install
```

Generate a component from that local installation:

```shell
mvn archetype:generate \
  -DarchetypeGroupId=org.integratedmodelling \
  -DarchetypeArtifactId=klab.component.archetype \
  -DarchetypeVersion=1.0.0-SNAPSHOT \
  -DgroupId=org.example \
  -DartifactId=klab.component.example \
  -Dversion=1.0.0-SNAPSHOT \
  -Dpackage=org.example.klab.component \
  -DinteractiveMode=false
```

After the archetype is published as a stable release, substitute that release version. To consume
a published SNAPSHOT without installing it locally, add this argument to the command:

```text
-DarchetypeRepository=https://central.sonatype.com/repository/maven-snapshots/
```

Run the command from the directory that should contain the new project. Maven creates a directory
named after `artifactId`.

### Archetype properties

Maven supplies `groupId`, `artifactId`, `version`, and `package`. The archetype adds these
properties:

| Property | Default | Effect |
| --- | --- | --- |
| `componentDescription` | `A k.LAB component` | Component and project description. |
| `vendorName` | `Integrated Modelling Partnership` | Vendor in component and POM metadata. |
| `vendorEmail` | `info@integratedmodelling.org` | Component vendor contact. |
| `licenseName` | `GNU Affero GPL 3.0` | License label in the POM and component manifest. |
| `usageRights` | `*` | Initial k.LAB usage-rights declaration. |
| `klabVersion` | `1.0.0-SNAPSHOT` | `klab-services` parent and API dependency version. |
| `klabProductVersion` | `1.0.1-SNAPSHOT` | Component-packaging Maven plug-in version. |
| `includeLibrary` | `true` | Add a `@Library` and `@KlabFunction` contextualizer/service stub. |
| `includeAgents` | `true` | Add `@Actor`, `@Verb`, and `@AgentAdapter` stubs. |
| `includeAdapter` | `true` | Add a `@ResourceAdapter` stub. |
| `includeIO` | `true` | Add `@Importer` and `@Exporter` schema stubs. |
| `includeAuthority` | `true` | Add an `@Authority` provider stub. |

Pass any property as another `-Dname=value` argument. For example, a component containing only a
library and its mandatory plug-in class can be generated with:

```text
-DincludeAgents=false -DincludeAdapter=false -DincludeIO=false -DincludeAuthority=false
```

Conditional extensions are omitted completely; the archetype does not create empty Java files.

### Generated project

Every generated project contains:

- `ComponentPlugin`, the mandatory PF4J entry point extending `KlabComponent`;
- a POM depending on `klab.core.services` and invoking `klab.product:package-component`;
- Central snapshot and release deployment endpoints matching the k.LAB component projects;
- a project README with build and publication notes;
- the complete GNU Affero General Public License version 3 in `LICENSE.txt`.

Enabled extension stubs are deliberately small and compile as generated. Rename their public
extension identifiers before publication: library, actor, adapter, schema, and authority names
must be unique among components installed in a service. Also update the generated source URL, SCM,
developers, organization, and license metadata if their defaults do not apply.

## Build And Package A Component

From the generated project:

```shell
mvn clean verify
```

The normal JAR is a Maven artifact. The `package-component` goal also creates the importable k.LAB
archive with classifier `component` and type `kar`. The component registry resolves that archive
as:

```text
groupId:artifactId:version:component:kar
```

Install a development build into the local Maven repository with:

```shell
mvn clean install
```

A local service can then import it using the ordinary three-part coordinate. See the Maven import
workflow in [Components](COMPONENTS.md#minimal-workflows).

## Deploy To Maven Central

Both `klab-services` and the archetype-generated POM use repository id `ossrh`. Configure the
credentials outside the repository in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>${env.CENTRAL_USERNAME}</username>
      <password>${env.CENTRAL_TOKEN}</password>
    </server>
  </servers>
</settings>
```

Deploy a SNAPSHOT with:

```shell
mvn clean deploy
```

SNAPSHOTs go to the Central Portal snapshot repository. Stable versions go to the configured
Central staging endpoint and must satisfy Central's current signing and metadata requirements.
Keep credentials and signing material out of POMs and source control. Never replace a published
stable component version; increment the version instead.

Before a release, verify at minimum that:

- the version is not a SNAPSHOT;
- project, SCM, license, organization, and developer metadata are correct;
- all public extension identifiers are stable and unique;
- `mvn clean verify` succeeds on JDK 21;
- the classified `.kar` contains only the component classes and required resources;
- the component is compatible with the k.LAB version declared in its packaging configuration.
