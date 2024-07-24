# Reorganize dependencies

The dependencies of the klab.modeler package must individually appear in the plugin configuration (feature
definition). They must each appear in a section like

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

To generate this automatically use the formatdeps.kts program in this directory after
running `mvn dependency:resolve > deps.txt` in klab.modeler, then moving and editing the file leaving only lines like

```
commons-io:commons-io:jar:2.11.0:compile -- module org.apache.commons.io [auto]
org.slf4j:slf4j-api:jar:2.0.11:compile -- module org.slf4j
....etc
```

(use Alt-Shift-Ins on Idea to get in/out of rectangular selection mode).

The program will print out the
artifact definitions to substitute into the <artifacts> section of the plugin configuration in pom.xml.
Ignore the NPE at the end
Some dependency cleanup is needed after (remove :runtime and all the :pom dependencies) - these are fixable in the script, so fix either