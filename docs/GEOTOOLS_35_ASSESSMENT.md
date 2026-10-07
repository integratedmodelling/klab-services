# GeoTools 35.x and Jackson 3: global k.LAB upgrade assessment

**Date:** 7 October 2026  
**Decision scope:** All of k.LAB, including the service hosts, shared libraries, clients and plug-in components, with particular attention to `klab.component.geospatial`. HortonMachine is assumed to support GeoTools 35 natively, as confirmed by its developer; its internal dependency set is not assessed here.

## Recommendation

Proceed with a **coordinated global GeoTools 35 migration**, subject to the release gates below. Do not treat it as a version-property change or make removal of Jackson 2 a prerequisite. The most manageable first target is a globally consistent GeoTools 35 platform with Jackson 3 at the geospatial boundaries and Jackson 2 retained for existing Spring and other consumers. A global migration of k.LAB-owned serialization to Jackson 3 is a worthwhile second stage, coupled to Spring Boot 4 / Spring Framework 7 adoption. It is a substantially larger undertaking.

There are three immediate findings:

1. **A concrete upstream interoperability defect remains in the released GeoTools artifacts inspected.** Both `gt-geojson-core:35.0` and `35.1` register their Jackson 3 `JtsModule` as a Jackson 2 service provider. Jackson 2 module discovery can consequently fail. An upstream fix exists, but its presence must be verified in the artifact selected for deployment; neither inspected release satisfies this gate.
2. **The geospatial component has a direct source incompatibility.** Its `GeodataIO` registers GeoTools' `JtsModule` with a Jackson 2 `ObjectMapper`. The same module class name in GeoTools 35 extends Jackson 3's `SimpleModule`, making that registration incompatible. Local STAC code also consumes Jackson nodes returned by HortonMachine and must follow the new API at that boundary.
3. **Dependency consistency is already imperfect.** The resolved current graph forces Jackson 2.19.0 over GeoTools 34.4's request for 2.21.0 and Avro 1.12.1's request for 2.20.0. GeoTools 35 introduces a second Jackson family and a shared annotations dependency, making explicit platform ownership essential.

These findings support a conditional migration, not an unconditional rejection of GeoTools 35. They also rule out claiming that the upgrade is safe solely because all Maven projects compile or HortonMachine is compatible.

## Evidence and limits

This assessment used the actual local checkouts, source inspection, two successful offline Maven dependency-tree resolutions, upstream tagged source, and inspection of released JAR contents. No application dependency or Java source was changed, and no upgraded application was built or run.

| Repository | Inspected HEAD | Qualification |
| --- | --- | --- |
| `klab-services` | `64754ea2b4d7207d51db454506f51b4b18741817` | Existing uncommitted work was present and preserved. |
| `klab.component.geospatial` | `8d4ef8665016761eb12a504a304f02eb3b55f1c7` | Assessment includes the existing working-copy change in `GeodataIO.java`. |
| `klab.product` | `0c80acb1f16a1d405f888cf99286d6e365293559` | Packaging source inspected; the installed Maven plug-in binary was not compared byte-for-byte with this source. |

The resolved graphs are for the current `klab.core.services` and separate geospatial Maven projects, using the local Maven cache. They are stronger evidence than POM declarations alone, but do not establish that every cached SNAPSHOT matches the current source. Sibling PubChem and Taxa components were sampled for Jackson exposure. The complete distribution, every client and every external component still require a release-wide inventory.

**Confidence:** high for the identified source incompatibility, loader policy, current resolved versions and released service-provider defect; conditional for the feasibility of the resulting application combination until integration tests pass. Ratings below describe migration exposure, not a statistical probability of failure.

## Current platform and proposed dependency boundary

| Area | Current evidence | Implication |
| --- | --- | --- |
| Java | Java 21 in the root and geospatial POMs | Jackson 3's Java 17 baseline is already met. |
| GeoTools | 34.4 in both root and component properties; `gt-bom` and `gt-platform-dependencies` imported by core services and geospatial | Change all independently resolved projects and build artifacts together. |
| Spring | Boot 3.4.4, Framework 6.2.5, Security 6.4.4; Spring Data Neo4j 7.4.3 | Existing integrations are Jackson 2 consumers. Their conversion is a separate framework migration. |
| Jackson | Root explicitly manages core, databind, annotations, YAML and multiple modules at 2.19.0 | A single `jackson-version` value cannot correctly describe both generations and annotations. |
| Geometry | JTS 1.20.0 | Also used by the inspected GeoTools 35 platform; one important version already aligns. |
| Components | PF4J 3.13.0; explicit application-first class loading | Host-provided dependency versions determine what components actually execute. |
| Geospatial packaging | `klab.product:1.0.1-SNAPSHOT`, with Runtime and Resources server artifacts as host closures | Rebuild packaging against the new actual hosts, not stale server SNAPSHOTs. |

Source: [root POM](C:/Users/Ferd/git/klab-services/pom.xml), [core services POM](C:/Users/Ferd/git/klab-services/klab.core.services/pom.xml), [geospatial POM](C:/Users/Ferd/git/klab.component.geospatial/pom.xml).

Jackson 3 uses `tools.jackson` packages and Maven groups, while Jackson 2 uses `com.fasterxml.jackson`. This permits both on one classpath. It does **not** make their mappers, tree nodes, serializers or modules interchangeable. The important exception is `com.fasterxml.jackson.core:jackson-annotations`, which remains shared and uses 2.x versioning. Databind-specific annotations such as `JsonSerialize` move with databind; they are not covered by that exception. [Jackson migration guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md), [annotations project](https://github.com/FasterXML/jackson-annotations).

For the first stage, maintain these boundaries throughout the global release:

| Boundary | Intended owner |
| --- | --- |
| REST, WebSocket, k.LAB polymorphic payloads, YAML configuration, existing persistence mapping | Existing Jackson 2 configuration and consumers, with an explicitly tested 2.x version set |
| GeoTools 35 and migrated geometry export / geospatial JSON adapters | Jackson 3, matching the selected GeoTools platform |
| Shared domain interfaces and plug-in contracts | Host-owned k.LAB types; avoid exposing Jackson tree or mapper types where a neutral DTO or serialized representation suffices |
| Annotations, JTS and common third-party libraries | One deliberately selected version per shared artifact, checked against both consumers |

This is a global upgrade with controlled coexistence. It does not imply leaving any host on GeoTools 34 while a component uses 35.

## Highest-priority risks

### 1. Released Jackson service-provider defect — release blocker

The GeoTools 35.0 tagged `JtsModule` imports `tools.jackson.databind.module.SimpleModule`, but the module also contains:

```text
META-INF/services/com.fasterxml.jackson.databind.Module
    com.bedatadriven.jackson.datatype.jts.JtsModule
```

Inspection of the released 35.0 and 35.1 JARs from OSGeo confirmed that this descriptor remains present; the 35.1 descriptor names the same provider. A Jackson 2 discovery operation can attempt to load a provider which is not a Jackson 2 module and raise `ServiceConfigurationError`. This can affect a dependency's discovery path even where k.LAB explicitly configures its own mapper. It is not evidence that every current Spring startup necessarily invokes the failing path.

Upstream GEOT-7954 removes the incorrect descriptor, adds a Jackson 2 discovery regression test, and makes `ObjectMapperFactory` public. The reviewed fix is commit `9e29f414d1ca34658939089a4f858d4c9047736b`. A fix on an upstream branch is not proof that a release contains it.

**Gate:** select a release containing the fix or a reproducibly built, distinctly versioned backport. Inspect the final host and component archives and run Jackson 2 discovery with the complete classpath. Do not silently replace a published artifact under its original version, and do not rely on disabling discovery only in k.LAB while third-party discovery remains untested.

Sources: [35.0 module source](https://github.com/geotools/geotools/blob/35.0/modules/unsupported/geojson-core/src/main/java/com/bedatadriven/jackson/datatype/jts/JtsModule.java), [35.0 descriptor](https://github.com/geotools/geotools/blob/35.0/modules/unsupported/geojson-core/src/main/resources/META-INF/services/com.fasterxml.jackson.databind.Module), [upstream fix](https://github.com/geotools/geotools/commit/9e29f414d1ca34658939089a4f858d4c9047736b), [released 35.1 JAR](https://repo.osgeo.org/repository/release/org/geotools/gt-geojson-core/35.1/gt-geojson-core-35.1.jar).

### 2. Geospatial source boundaries — required changes

| Location | Finding | Required treatment |
| --- | --- | --- |
| `GeodataIO.java:3–5,62–63` | Jackson 2 mapper registers `com.bedatadriven.jackson.datatype.jts.JtsModule`; GeoTools 35 changes the superclass behind that unchanged name. | Migrate the local GeoJSON writer, generator and module registration together to Jackson 3. The current expression is incompatible with the 35.0 module API. |
| `StacResource.java:363,395–407` | Casts an asset's `eo:bands` to Jackson 2 `ArrayNode`, tests for Jackson 2 `ObjectNode`, and passes a mutated node into `HMStacAsset`. | Align these caller types with the assured new HortonMachine API. Depending on its exact signatures, stale types can fail compilation, fail a cast, or bypass the `instanceof` branch and skip the URL rewrite. |
| `StacParser.java` | Unirest JSON is converted to text before `GeoJSONReader.parseFeature`. | This textual boundary is comparatively robust. Test feature properties and geometry semantics; do not replace `kong.unirest.JsonNode` imports as though they were Jackson imports. |
| Core-services `runtime/data/stac/parser/STACParser.java` | Independent Jackson 2 mapper and custom deserializers | It can remain on Jackson 2 in stage one. A global Jackson 3 conversion must port these custom modules explicitly. |

The STAC finding concerns k.LAB's calling code, not a reassessment of HortonMachine's internal compatibility. No changes to HortonMachine itself are proposed here.

An appropriate local writer shape is `tools.jackson.databind.json.JsonMapper.builder().addModule(new JtsModule()).build()`, with matching Jackson 3 generator APIs. This is a migration direction, not a complete patch. Preserve GeoJSON feature IDs, geometry precision, null handling and k.LAB metadata semantics. Existing `GeodataIOTest` can continue reading emitted JSON with Jackson 2: that is a useful cross-generation wire-format check.

Local evidence: [GeodataIO](C:/Users/Ferd/git/klab.component.geospatial/src/main/java/org/integratedmodelling/geospatial/library/GeodataIO.java:62), [STAC resource](C:/Users/Ferd/git/klab.component.geospatial/src/main/java/org/integratedmodelling/geospatial/adapters/stac/StacResource.java:363), [component STAC parser](C:/Users/Ferd/git/klab.component.geospatial/src/main/java/org/integratedmodelling/geospatial/adapters/stac/StacParser.java:61), [core STAC parser](C:/Users/Ferd/git/klab-services/klab.core.services/src/main/java/org/integratedmodelling/klab/runtime/data/stac/parser/STACParser.java:22), [GeoJSON export tests](C:/Users/Ferd/git/klab.component.geospatial/src/test/java/org/integratedmodelling/geospatial/library/GeodataIOTest.java).

### 3. Host and component consistency — high, despite a global rollout

`KlabPluginLoader` explicitly chooses PF4J `APD`: application, plug-in, dependencies. The inspected packager excludes host-provided artifacts even when the component requested a different version; mismatches generate warnings. It also considers dependencies reachable through k.LAB core artifacts host-provided. Consequently, a successful component package can still represent an incompatible compile-time/runtime pairing.

A global rollout removes the intentional mismatch, but stale local SNAPSHOTs, cached component archives, independent builds or partial deployment can recreate it. The unchanged class name of `JtsModule` makes this especially dangerous: ordinary Java class lookup does not encode which Jackson superclass was expected when the component was compiled.

The packager shades included dependencies without relocation. Its configured transformers handle the manifest, `plugin.properties` and signatures, but do not include a service-descriptor merger. Where multiple included dependencies contain the same `META-INF/services` path, provider entries therefore need explicit verification; do not assume the final archive preserves all of them. Jackson, GeoTools and image-reader discovery all deserve coverage.

**Required controls:** publish one platform dependency contract; make incompatible host/component version differences fail packaging; record resolved versions and hashes; inspect final archives; test loading through PF4J in both Resources and Runtime hosts. Retain application-first loading unless a separate isolation design is justified. Child-first loading or shading the entire geospatial stack would introduce new type-identity and provider-discovery problems.

References: [loader](C:/Users/Ferd/git/klab-services/klab.core.services/src/main/java/org/integratedmodelling/klab/components/KlabPluginLoader.java), [packager exclusions](C:/Users/Ferd/git/klab.product/src/main/java/org/integratedmodelling/klab/maven/PackageComponentMojo.java:247), [archive creation](C:/Users/Ferd/git/klab.product/src/main/java/org/integratedmodelling/klab/maven/PackageComponentMojo.java:608).

### 4. BOMs and shared annotations — high

GeoTools itself manages both Jackson generations. The inspected 35.0 platform source specifies Jackson 2 BOM 2.21.0 and Jackson 3 BOM 3.1.2. The published **35.1** platform POM imports Jackson 2 BOM **2.22.0** and Jackson 3 BOM **3.2.0**. These are different candidate baselines; “35.x” is not a sufficient dependency specification. Neither is a recommendation to override Jackson independently of the selected GeoTools patch release.

The current root's explicit Jackson 2.19.0 entries, repeated component properties and direct dependency versions prevent a clean assumption that importing the GeoTools BOM settles everything. The shared annotations artifact is particularly important: do not leave it at 2.19.0 merely because existing Jackson 2 consumers currently use that version. Resolve the chosen Jackson BOMs, select a compatible shared annotations version, and test both generations against it. There is no separate Jackson 3 annotations JAR to install beside the 2.x one.

The current trees demonstrate version overrides in both core services and geospatial. They do not prove that those overrides currently cause runtime failures. They do show why “dependency convergence” and “upstream-tested combination” are different claims. The root configures Maven Enforcer under `pluginManagement`, but the scanned POMs contain no execution binding for `enforce`; configuration alone does not provide an active release gate.

Use a shared k.LAB platform BOM/parent with explicit ownership of Spring, GeoTools, Jackson 2, Jackson 3 and annotations. Review overlaps deliberately, and compare effective POMs as well as resolved graphs. Maven's dependency management does not translate Jackson 2 coordinates into Jackson 3 or validate binary compatibility. [Maven dependency mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html).

Sources: [GeoTools 35.0 platform source](https://github.com/geotools/geotools/blob/35.0/platform-dependencies/pom.xml), [published 35.1 platform POM](https://repo.osgeo.org/repository/release/org/geotools/gt-platform-dependencies/35.1/gt-platform-dependencies-35.1.pom), [root management](C:/Users/Ferd/git/klab-services/pom.xml:329).

## Spring and the wider dependency ecosystem

Spring Boot 4 and Framework 7 introduce first-class Jackson 3 integration; Boot 4 defaults to it and retains deprecated Jackson 2 support to assist migration. Merely adding Jackson 3 to the present Boot 3 / Framework 6 application does not port its Jackson 2 converters or mapper customizations. GeoTools can use Jackson 3 while the current service integration continues using Jackson 2. [Spring's migration overview](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/), [Boot JSON reference](https://docs.spring.io/spring-boot/4.0/reference/features/json.html).

| Consumer | Observed evidence | Coexistence stage | Full Jackson 3 stage |
| --- | --- | --- | --- |
| Spring MVC, WebSocket, Actuator and service clients | Boot 3.4.4 graph; explicit Jackson 2 mapper bean | Retain existing integration; test it with the new shared platform. | Migrate framework family, converters and mapper customization together. Boot 4 expects format-specific mapper customization; a generic mapper bean alone is not equivalent. |
| k.LAB shared serialization | `JacksonConfiguration` defines custom polymorphic serializers/deserializers and configures many domain types; common JSON/YAML utilities expose Jackson 2 mappers | Leave service serialization stable while adapting geospatial-only paths. | Port custom implementations, type handling, builders and exception/API changes; validate stored and exchanged payloads. This is much more than changing imports. |
| Nitrite 4.3.0 | `nitrite-jackson-mapper` and `nitrite-spatial` resolve Jackson 2; `ResourcesKBox.KlabJacksonMapper` overrides a Jackson 2 mapper API | Keep Jackson 2 for this persistence boundary. | Verify an appropriate supported integration or implement a compatible adapter. A Jackson 3 return type cannot satisfy the current override. Reopen existing databases in tests. |
| Avro 1.12.1 | Resolves Jackson 2 core/databind, requesting 2.20.0 before k.LAB overrides | Retain its required family; exercise schemas and round trips. | Do not remove Jackson 2 until the selected Avro version no longer requires it. Application-level migration does not replace Avro's internal dependency. |
| springdoc 2.8.6 / Swagger Jakarta 2.2.29 | Current graph uses Jackson 2 annotations, databind, YAML and Java-time module | Keep current Spring-compatible integration and test schema generation. | Select a Boot-compatible line; springdoc documents 3.x for Boot 4. Check actual Swagger/Jackson dependencies rather than inferring them from “Jakarta”. |
| Groovy YAML 4.0.25 | Resolved Jackson 2 YAML/databind | Keep its family; test YAML conversion/configuration. | Verify the chosen Groovy integration separately. |
| H2GIS 1.5.0 | Resolved Jackson 2 core; shared spatial/database dependencies | Check JSON and database paths, H2 compatibility and shared JTS. | Requires its own consumer review; GeoTools removing its H2 datastore does not remove k.LAB's H2GIS dependency. |
| Spring Security, Spring Data Neo4j / Neo4j driver | Root explicitly manages versions alongside Spring | Keep the Spring family coherent; regression-test authentication and persistence paths. | Review Spring Security/Data migration together. Do not assume the Neo4j driver must change solely because Jackson changes. |
| PubChem and Taxa plug-ins | Both declare Jackson databind as `provided`; their source uses Jackson 2 nodes | Host must continue supplying Jackson 2. | Rebuild and port their JSON code or retain explicit compatibility. A component import can succeed before a rarely invoked method exposes missing classes. |
| Unirest 3.14.5, underscore 1.120, AWS lightweight client 0.1.16 | Explicit geospatial dependencies; Unirest JSON wrapper used in STAC | Inspect actual transitive dependencies and configured mapper adapters; test HTTP/STAC/S3 paths. | No incompatibility is established merely by their presence. Migrate adapters only where they actually expose Jackson APIs. |

The first six rows are supported by current source and resolved dependency trees, not by an assumption that all older libraries are incompatible. For consumers that keep Jackson entirely internal, coexistence can be sufficient. For APIs that exchange Jackson objects, conversion or migration is necessary.

Key local references: [shared Jackson configuration](C:/Users/Ferd/git/klab-services/klab.core.common/src/main/java/org/integratedmodelling/common/data/jackson/JacksonConfiguration.java:435), [Spring mapper bean](C:/Users/Ferd/git/klab-services/klab.core.services/src/main/java/org/integratedmodelling/klab/services/application/ServiceNetworkedInstance.java:254), [Nitrite adapter](C:/Users/Ferd/git/klab-services/klab.core.services/src/main/java/org/integratedmodelling/klab/resources/ResourcesKBox.java:61), [common POM](C:/Users/Ferd/git/klab-services/klab.core.common/pom.xml), [Taxa POM](C:/Users/Ferd/git/klab.authority.taxa/pom.xml:68), [PubChem POM](C:/Users/Ferd/git/klab.authority.pubchem/pom.xml:68). Upstream: [Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide), [springdoc compatibility](https://springdoc.org/).

## Non-Jackson changes that still matter

**JAXB/Jakarta: high integration risk.** GeoTools 35 migrates relevant Java EE APIs to Jakarta. The geospatial POM explicitly includes `javax.xml.bind:jaxb-api` and JAXB 2.x implementation/core artifacts, and `RasterAuxXml` uses `javax.xml.bind.annotation`. Core common declares JAXB runtime 2.3.9 while using Ehcache's Jakarta classifier; core services separately declares Jakarta JAXB API 4.0.2. The current geospatial tree resolves GlassFish JAXB runtime to 2.4.0-b180830.0438. GeoTools 35.1's platform manages JAXB runtime 4.0.4. This is a concrete API/provider alignment task: retaining both API namespaces does not guarantee the matching implementations and consumers work. Trace XML use and migrate the k.LAB-owned boundary where practical. Do not replace every `javax.*` import indiscriminately; Java SE APIs such as `javax.imageio` remain valid.

**Raster/image stack: medium to high validation effort.** The inspected component already imports `org.eclipse.imagen`, so migration from old JAI packages is not wholly new work for this 34.4-to-35 transition. Nevertheless, GeoTools 35.1 manages ImageN 0.9.2 and ImageIO-Ext 2.1.1. Recheck TIFF metadata, nodata, reprojection, rendering and image-reader discovery in packaged hosts. Direct ImageIO use in `GeodataIO` makes these meaningful application tests.

**XML services and storage: conditional risks.** GeoTools 35 changes entity-resolution defaults, removes NetCDF external indexing, and removes `gt-jdbc-h2`. Test k.LAB's WFS/WCS paths against real representative capabilities documents. Audit the final global dependency graph for NetCDF and removed datastore use; neither is assumed to be a required direct geospatial dependency from the POM alone. Preserve data/cache rollback options where indexing behavior changes.

**Other managed libraries:** the component directly pins Commons JXPath 1.3, while GeoTools 35.0's platform specifies 1.4.0. Review that override and other direct pins instead of assuming the BOM upgrades them. Not every transitive version change is a Jackson issue.

Sources: [GeoTools upgrade guidance](https://docs.geotools.org/stable/userguide/welcome/upgrade.html), [35.0 release notes](https://github.com/geotools/geotools/releases/tag/35.0), [35.1 platform POM](https://repo.osgeo.org/repository/release/org/geotools/gt-platform-dependencies/35.1/gt-platform-dependencies-35.1.pom), [raster XML model](C:/Users/Ferd/git/klab.component.geospatial/src/main/java/org/integratedmodelling/geospatial/library/RasterAuxXml.java).

## Benefits, timing and opportunity

The immediate benefit is alignment with the collaborator's supported HortonMachine/GeoTools direction, reducing future divergence in the geospatial stack. GeoTools 35 also brings maintained dependency updates and geospatial fixes. Their value should be tied to k.LAB's actual formats and workloads; this assessment establishes no numerical performance improvement or specific security remediation for the deployed application. [GeoTools 35 release notes](https://github.com/geotools/geotools/releases/tag/35.0).

The larger opportunity is to make a global release reproducible across the plug-in ecosystem: one platform definition, explicit host-provided APIs, packaging failures for incompatible versions, and contract tests that include the final archives. The existing Jackson overrides and JAXB mixture mean some of that work is useful even if GeoTools 35 is deferred.

A later Jackson 3 / Boot 4 migration can consolidate k.LAB-owned mapper configuration and modernize the framework platform. Jackson's builder-oriented configuration offers a natural point to replace dispersed mutable mapper setup with explicit factories. The cost is porting custom serialization and proving compatibility of network and stored data. Two generations also mean additional maintenance and scanning during transition; coexistence should be documented and periodically reviewed rather than left accidental.

| Option | Benefit | Cost/risk | Assessment |
| --- | --- | --- | --- |
| Keep 34.4 temporarily | No immediate migration disruption | Delays collaborator alignment; existing dependency overrides remain | Reasonable until the concrete release gates can be met, not a solution to existing consistency debt. |
| Global GeoTools 35, retain required Jackson 2 consumers | Delivers geospatial alignment without simultaneously replacing all serialization | Moderate source work, substantial packaging/integration verification, upstream fix required | **Preferred first stage.** |
| Global GeoTools 35 plus migration of k.LAB-owned code to Jackson 3 and Boot 4 | Consolidates application serialization and modernizes Spring | High effort across core, persistence, clients, components and deployment | Valuable planned second stage, or one larger release with separately validated milestones. |
| Require no Jackson 2 anywhere in the graph | Removes a compatibility family eventually | Blocked until every relevant third-party consumer supports it or is replaced | Do not make this the acceptance condition for GeoTools 35. |

The main scheduling uncertainty is integration and dependency readiness, not HortonMachine. Avoid a fixed effort estimate before the global graph and serializer inventory are complete. Full Spring/Jackson conversion is a platform project; the coexistence path is narrower but still needs release-level testing.

## Implementation and release gates

1. **Freeze the global baseline.** Inventory every service/server, CLI/modeler distribution, component and build plug-in. Record effective POMs, runtime trees, resolved SNAPSHOT hashes and current wire/persistence fixtures. Include separately built sibling repositories and their cached host artifacts. Confirm the intended GeoTools patch release and HortonMachine build with the collaborator.
2. **Resolve the upstream blocker.** Obtain a GeoTools artifact containing GEOT-7954, or maintain a clearly versioned backport. Verify the final JAR contents and Jackson 2 discovery. Neither 35.0 nor 35.1 as inspected passes this check.
3. **Define the shared platform.** Align GeoTools modules, Jackson 2/3 BOMs, annotations, JTS, JAXB and image libraries. Remove or justify conflicting direct overrides. Bind meaningful dependency checks to the build. Cross-generation Jackson coexistence is allowed; duplicate or incompatible versions within a shared family are not.
4. **Port the geospatial boundaries.** Convert `GeodataIO`'s writer and adapt the k.LAB STAC callers. Keep the common service/persistence mapper stable during this stage. Resolve JAXB callers and providers explicitly.
5. **Build and package against the same hosts.** Rebuild shared libraries, all affected hosts and components, with version mismatch warnings promoted to failures for platform-owned dependencies. Inspect shaded service resources and record class origins in a packaged integration test.
6. **Run the compatibility matrix below.** Passing compilation, a dependency tree or a component unit test alone is insufficient.
7. **Deploy as one compatible platform release.** Stop accepting new work, drain active component operations, and restart the relevant hosts with the matching component set. Disable automatic SNAPSHOT component replacement during the cutover. Preserve the old distribution, component archives and recoverable data/configuration. A running JVM cannot safely replace its already loaded host libraries through a component hot update.
8. **Then evaluate full Jackson/Spring migration.** Port shared serializers and all k.LAB-owned consumers, choose compatible framework integrations, and retain Jackson 2 only for explicitly identified dependencies. The Boot compatibility switch can assist default-setting migration, but does not port custom serializers or guarantee persisted-data compatibility.

### Minimum acceptance matrix

| Test | What must be demonstrated |
| --- | --- |
| Packaged Resources and Runtime host startup with geospatial, Taxa and PubChem components | Expected class origins; no linkage errors; Jackson 2 and 3 remain independently usable. |
| Jackson provider discovery under host and component classloaders | No invalid Jackson 2 provider; intended modules remain discoverable after shading. |
| GeoJSON export and import | Existing export fixtures, geometry families, precision, IDs, metadata, empty geometry and null behavior preserved. Read stage-one exports with the existing Jackson 2 consumer. |
| STAC end-to-end | Collection/item parsing, `eo:bands` selection, S3 `href` rewrites, authentication and raster access; assert that the rewrite actually runs. |
| Geospatial operations | GeoTIFF import/export, nodata, CRS transformations, raster rendering, shapefile and PostGIS operations; WFS/WCS service interaction. |
| REST/WebSocket/agent payloads and OpenAPI | Existing polymorphic types, time values, nulls, enums, errors and schema generation remain compatible with clients. |
| Persisted data and configuration | Existing Nitrite resources reopen; representative JSON/YAML and Avro data round-trip. Use data copies and verify rollback. |
| Upgrade lifecycle | Fresh install and upgrade from the current distribution; stale or incompatible components rejected; rollback rehearsed. |

Existing starting points include `GeodataIOTest`, `RasterRendererTest`, `RasterAdapterTest`, `ResourcesKBoxVersionTest` and component registry tests. Review their coverage before relying on them; no claim is made that they already cover the full matrix.

## Checks performed and reproducibility

Both commands below succeeded against the current baseline. They were dependency inspections, not compilation or application tests:

```powershell
mvn -o -f klab.core.services/pom.xml dependency:tree -Dverbose '-Dincludes=com.fasterxml.jackson.*:*,tools.jackson.*:*,org.dizitart:*,org.apache.avro:*,org.springdoc:*'
mvn -o -f ../klab.component.geospatial/pom.xml dependency:tree -Dverbose '-Dincludes=com.fasterxml.jackson.*:*,tools.jackson.*:*,org.geotools:*,commons-jxpath:*,org.glassfish.jaxb:*,com.sun.xml.bind:*'
```

Representative resolved findings were:

```text
nitrite-jackson-mapper:4.3.0 -> jackson-databind:2.19.0 (requested 2.17.1)
avro:1.12.1                -> jackson-databind:2.19.0 (requested 2.20.0)
spring-boot-starter-json:3.4.4 -> jackson-databind:2.19.0 (requested 2.18.3)
gt-geojson-core:34.4       -> jackson-databind:2.19.0 (requested 2.21.0)
```

The [OSGeo release metadata](https://repo.osgeo.org/repository/release/org/geotools/gt-geojson-core/maven-metadata.xml) listed 35.0 and 35.1. Both released GeoJSON-core JARs were inspected in memory. The 35.1 platform POM was read directly from the release repository. No inference about a future fixed release is made.

**Decision condition:** adopt GeoTools 35 globally once the provider defect, serialization boundaries, shared version management and packaged integration tests are resolved. If the desired release also migrates all k.LAB-owned code to Jackson 3, include the Spring and persistence work explicitly in that release's scope; do not assume GeoTools compatibility covers it.
