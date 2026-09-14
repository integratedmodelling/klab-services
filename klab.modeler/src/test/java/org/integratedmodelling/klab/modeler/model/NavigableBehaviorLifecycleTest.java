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

  @Test
  void correctedRepositoryDocumentDoesNotReintroduceOldDescriptorErrors() {
    var project = new ProjectImpl();
    project.setUrn("test.project");
    var workspace = new WorkspaceImpl();
    workspace.setUrn("test.workspace");
    workspace.getProjects().add(project);
    var navigable = new TestWorkspace(workspace);
    var broken = behavior("test.behavior", KActorsBehavior.Type.BEHAVIOR);
    var error = org.integratedmodelling.klab.api.services.runtime.Notification.error("old error");
    broken.getNotifications().add(error);
    var document = (NavigableKActorsBehavior) navigable.add(broken);
    var corrected = behavior("test.behavior", KActorsBehavior.Type.BEHAVIOR);
    var service = (org.integratedmodelling.klab.api.services.ResourcesService)
        java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {org.integratedmodelling.klab.api.services.ResourcesService.class},
            (proxy, method, args) -> switch (method.getName()) {
              case "serviceId" -> "resources";
              case "retrieve" -> corrected;
              default -> null;
            });
    var scope = (org.integratedmodelling.klab.api.scope.UserScope)
        java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {org.integratedmodelling.klab.api.scope.UserScope.class},
            (proxy, method, args) -> method.getName().equals("findService")
                ? java.util.Optional.of(service) : null);
    var changes = new ResourceSet();
    var change = new ResourceSet.Resource();
    change.setResourceUrn(corrected.getUrn());
    change.setServiceId("resources");
    change.setKnowledgeClass(KlabAsset.KnowledgeClass.BEHAVIOR);
    change.getNotifications().add(error);
    changes.getBehaviors().add(change);
    var projectChange = new ResourceSet.Resource();
    projectChange.setResourceUrn("test.project");
    projectChange.setRepositoryState(new RepositoryState());
    changes.getProjects().add(projectChange);
    navigable.mergeChanges(changes, scope);
    navigable.mergeChanges(changes, scope);
    assertEquals(0, document.getNotifications().size());
    assertEquals(0, corrected.getNotifications().size());
    assertEquals(0, navigable.localMetadata().get(NavigableAsset.ERROR_NOTIFICATION_COUNT_KEY, 0));
    assertEquals(RepositoryState.Status.CLEAN,
        document.localMetadata().get(NavigableAsset.REPOSITORY_STATUS_KEY));
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
