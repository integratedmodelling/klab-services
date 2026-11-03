package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;
class ConfigurationModeledChangesTest {
 @Test void configurationBookkeepingEndpointsAreNotModeledInputChanges() {
  var commit=new CommitImpl();commit.getAddedLinks().add(Triple.of(1L,2L,"EMERGED_FROM"));commit.getAddedLinks().add(Triple.of(2L,1L,"AFFECTS"));
  assertTrue(ConfigurationModeledChanges.candidates(commit).isEmpty());
 }
 @Test void birthAndRevisionIgnoreManagedOutputButRetainRealScientificOutputs() {
  var birth=new CommitImpl();birth.getAddedObservations().add(1L);birth.getAddedLinks().add(Triple.of(100L,1L,"HAS_MEMBER"));
  var config=new ObservationImpl();config.setId(1);config.getMetadata().put("klab.configuration.current","managed");
  assertTrue(ConfigurationModeledChanges.observations(ConfigurationModeledChanges.candidates(birth),Map.of(1L,config)).isEmpty());
  var update=new CommitImpl();update.getModifiedAssets().addAll(Set.of(1L,3L));update.getAddedLinks().add(Triple.of(1L,2L,"EMERGED_FROM"));
  var quality=new ObservationImpl();quality.setId(3);
  assertEquals(Set.of(3L),ConfigurationModeledChanges.observations(ConfigurationModeledChanges.candidates(update),Map.of(1L,config,3L,quality)));
 }
 @Test void actualEndpointAndPlacementChangesRemainEventInputs() {
  var commit=new CommitImpl();commit.getDeletedLinks().add(Triple.of(11L,12L,"HAS_RELATIONSHIP_SOURCE"));
  commit.getAddedLinks().add(Triple.of(50L,13L,"HAS_CHILD"));commit.getAddedLinks().add(Triple.of(100L,14L,"HAS_MEMBER"));
  assertEquals(Set.of(11L,12L,13L,14L),ConfigurationModeledChanges.candidates(commit));
 }

 @Test void durableSchedulerJournalNamesChangedQualitiesEvenWhenCommitOnlyListsStorageWrites() {
  var commit=new CommitImpl();commit.setId(10);commit.getModifiedAssets().add(500L);
  var journal=new org.integratedmodelling.klab.api.digitaltwin.SchedulerJournal(1,"event",null,org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event.Type.TEMPORAL_TRANSITION,0,1,"registration","plan","1",10,List.of(42L),true);
  String encoded=org.integratedmodelling.klab.utilities.Utils.Json.asString(Map.of("version",1,"contextId","context","commit",Map.of("id",10),"journals",List.of(journal)));
  var nativeActivity=new org.integratedmodelling.klab.api.data.KnowledgeGraph.ConsequenceAssetSnapshot(11,"e11",List.of("Activity"),Map.of(org.integratedmodelling.klab.api.digitaltwin.TransitionCommit.METADATA_KEY,encoded),null,null);
  assertEquals(Set.of(42L),ConfigurationModeledChanges.journalChanges(nativeActivity,commit,"context"));
  assertThrows(SecurityException.class,()->ConfigurationModeledChanges.journalChanges(nativeActivity,commit,"other-context"));
  commit.setId(12);assertThrows(SecurityException.class,()->ConfigurationModeledChanges.journalChanges(nativeActivity,commit,"context"));
 }
}
