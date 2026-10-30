package org.integratedmodelling.klab.resources;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class FileProjectStoragePhysicalSourceTest {
 @TempDir Path directory;
 @Test void physicalSourceOperationsPreserveNeighborsAndRejectForeignOrNonFileLocators()throws Exception{
  var project=Files.createDirectories(directory.resolve("project"));var sources=Files.createDirectories(project.resolve("src"));var actual=sources.resolve("actual.kwv");var neighbor=sources.resolve("imod.kwv");
  Files.writeString(actual,"old actual");Files.writeString(neighbor,"unrelated");var storage=new FileProjectStorage(project.toFile(),"project",null);
  storage.writeExistingOntologySource(actual.toUri().toURL(),"new actual");assertEquals("new actual",storage.readExistingOntologySource(actual.toUri().toURL()));assertEquals("unrelated",Files.readString(neighbor));
  var foreign=directory.resolve("foreign.kwv");Files.writeString(foreign,"foreign");
  assertThrows(RuntimeException.class,()->storage.readExistingOntologySource(foreign.toUri().toURL()));assertThrows(RuntimeException.class,()->storage.writeExistingOntologySource(foreign.toUri().toURL(),"overwrite"));assertThrows(RuntimeException.class,()->storage.deleteExistingOntologySource(foreign.toUri().toURL()));assertEquals("foreign",Files.readString(foreign));
  assertThrows(RuntimeException.class,()->storage.readExistingOntologySource(new java.net.URL("https://example.invalid/source.kwv")));
 }
 @Test void deletingTrackedPhysicalSourceStagesOnlyThatFile()throws Exception{
  var project=Files.createDirectories(directory.resolve("project"));var sources=Files.createDirectories(project.resolve("src"));var actual=sources.resolve("actual.kwv");var neighbor=sources.resolve("imod.kwv");Files.writeString(actual,"actual");Files.writeString(neighbor,"neighbor");
  try(var git=Git.init().setDirectory(project.toFile()).call()){
   git.add().addFilepattern(".").call();git.commit().setMessage("fixture").setAuthor("Fixture","fixture@localhost").setCommitter("Fixture","fixture@localhost").call();
   var storage=new FileProjectStorage(project.toFile(),"project",null);storage.deleteExistingOntologySource(actual.toUri().toURL());
   assertFalse(Files.exists(actual));assertEquals("neighbor",Files.readString(neighbor));assertEquals(java.util.Set.of("src/actual.kwv"),git.status().call().getRemoved());
  }
 }
}
