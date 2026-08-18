package org.integratedmodelling.klab.modeler.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.junit.jupiter.api.Test;

class NavigableBehaviorLifecycleTest {

  @Test
  void behaviorSubtypesUseTheirCanonicalStorageLocations() {
    assertEquals(
        ProjectStorage.ResourceType.BEHAVIOR,
        ProjectStorage.ResourceType.classify(
            behavior("b", KActorsBehavior.Type.COMPONENT)));
    assertEquals(
        ProjectStorage.ResourceType.BEHAVIOR,
        KlabAsset.KnowledgeClass.COMPONENT.getResourceType());
    assertEquals(
        ProjectStorage.ResourceType.APPLICATION,
        ProjectStorage.ResourceType.classify(behavior("a", KActorsBehavior.Type.APP)));
    assertEquals(
        ProjectStorage.ResourceType.SCRIPT,
        ProjectStorage.ResourceType.classify(behavior("s", KActorsBehavior.Type.SCRIPT)));
    assertEquals(
        ProjectStorage.ResourceType.TESTCASE,
        ProjectStorage.ResourceType.classify(behavior("t", KActorsBehavior.Type.UNITTEST)));
  }

  @Test
  void dynamicallyAddedBehaviorsCreateAndUseTheCorrectFolders() {
    var project = new ProjectImpl();
    project.setUrn("test.project");
    var workspace = new WorkspaceImpl();
    workspace.setUrn("test.workspace");
    workspace.getProjects().add(project);
    var navigableWorkspace = new TestWorkspace(workspace);

    assertFolder(
        navigableWorkspace.add(behavior("test.behavior", KActorsBehavior.Type.BEHAVIOR)),
        BehaviorFolder.class);
    assertFolder(
        navigableWorkspace.add(behavior("test.app", KActorsBehavior.Type.APP)), AppFolder.class);
    assertFolder(
        navigableWorkspace.add(behavior("test.script", KActorsBehavior.Type.SCRIPT)),
        ScriptFolder.class);
    assertFolder(
        navigableWorkspace.add(behavior("test.case", KActorsBehavior.Type.UNITTEST)),
        TestCaseFolder.class);

    assertEquals(
        4,
        navigableWorkspace
            .findAsset(
                "test.project", NavigableProject.class, KlabAsset.KnowledgeClass.PROJECT)
            .children()
            .size());
  }

  @Test
  void documentLookupDoesNotClassifyBehaviorActions() {
    var project = new ProjectImpl();
    project.setUrn("klab.staging.vxii");
    var workspace = new WorkspaceImpl();
    workspace.setUrn("testing.core");
    workspace.getProjects().add(project);
    var navigableWorkspace = new TestWorkspace(workspace);
    var testcase = behavior("klab.staging.vxii.testsuite", KActorsBehavior.Type.UNITTEST);
    var init = new KActorsActionImpl();
    init.setUrn("init");
    testcase.getStatements().add(init);

    var added = navigableWorkspace.add(testcase);

    assertSame(
        added,
        navigableWorkspace.findAsset(
            testcase.getUrn(), KlabAsset.class, KlabAsset.KnowledgeClass.TESTCASE));
    assertNull(
        navigableWorkspace.findAsset(
            testcase.getUrn(), KlabAsset.class, KlabAsset.KnowledgeClass.BEHAVIOR));
  }

  @Test
  void specificGitStatusWinsOverJgitsUncommittedUnionForEveryBehaviorKind() {
    for (var type : KActorsBehavior.Type.values()) {
      var project = new ProjectImpl();
      project.setUrn("test.project");
      var workspace = new WorkspaceImpl();
      workspace.setUrn("test.workspace");
      workspace.getProjects().add(project);
      var navigableWorkspace = new TestWorkspace(workspace);
      var document =
          assertInstanceOf(
              NavigableKActorsBehavior.class,
              navigableWorkspace.add(behavior("test.behavior", type)));

      var state = new RepositoryState();
      var path =
          ProjectStorage.getRelativeFilePath(
              document.getUrn(), ProjectStorage.ResourceType.classify(document));
      state.getUntrackedPaths().add(path);
      state.getUncommittedPaths().add(path);

      var changes = new ResourceSet();
      var projectChange = new ResourceSet.Resource();
      projectChange.setResourceUrn("test.project");
      projectChange.setKnowledgeClass(KlabAsset.KnowledgeClass.PROJECT);
      projectChange.setRepositoryState(state);
      changes.getProjects().add(projectChange);
      navigableWorkspace.computeStatistics(changes);

      assertEquals(
          RepositoryState.Status.UNTRACKED,
          document
              .localMetadata()
              .get(NavigableAsset.REPOSITORY_STATUS_KEY, RepositoryState.Status.class),
          type.name());

      state.getUntrackedPaths().clear();
      state.getModifiedPaths().add(path);
      navigableWorkspace.computeStatistics(changes);

      assertEquals(
          RepositoryState.Status.MODIFIED,
          document
              .localMetadata()
              .get(NavigableAsset.REPOSITORY_STATUS_KEY, RepositoryState.Status.class),
          type.name());
    }
  }

  private static void assertFolder(NavigableAsset added, Class<?> folderClass) {
    assertInstanceOf(NavigableKActorsBehavior.class, added);
    assertInstanceOf(folderClass, added.parent());
    assertSame(added, added.parent().children().getFirst());
  }

  private static KActorsBehaviorImpl behavior(String urn, KActorsBehavior.Type type) {
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn(urn);
    behavior.setProjectName("test.project");
    behavior.setBehaviorType(type);
    return behavior;
  }

  private static class TestWorkspace extends NavigableWorkspace {

    TestWorkspace(WorkspaceImpl workspace) {
      super(workspace);
    }

    NavigableAsset add(KlabAsset asset) {
      return addChild(asset);
    }
  }
}
