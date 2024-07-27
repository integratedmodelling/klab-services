# Reorganize plugin dependencies

The dependencies of the klab.modeler package must individually appear in the plugin configuration (feature definition). They must each appear in a section like

```xml
<artifact>
   <id>org.integratedmodelling:klab.modeler:jar:0.11.0-SNAPSHOT</id>
   <override>true</override>
   <source>true</source>
   <instructions>
      <_noee>true</_noee>
   </instructions>
</artifact>
```

within the `<artifacts>` tag in the plugin configuration in pom.xml. The artifact definitions must be regenerated every time the dependencies change for the klab.modeler package. To do this semi-automatically:

* run `mvn dependency:resolve > ../p2/org.integratedmodelling.klab.modeler/utils/deps.txt` in the `klab.modeler` package directory.
* Run the formatdeps.kts program in this directory in a terminal (ignore the NPE at the end)
* Cut and paste the output in place of the current content of `<artifacts>...<artifacts>` in pom.xml
* Add an <artifact> definition for klab.modeler:jar by copying the klab.core.common one and pasting after it.
