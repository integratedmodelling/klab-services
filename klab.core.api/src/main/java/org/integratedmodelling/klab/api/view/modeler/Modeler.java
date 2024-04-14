package org.integratedmodelling.klab.api.view.modeler;

import org.integratedmodelling.klab.api.data.Repository;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;

/**
 * A {@link UIController} that contains all the user actions relevant to a modeler IDE. Implement this to use
 * it in any view type. Can also provide a blueprint to implement a non-Java modeler using websockets to drive
 * a Java counterpart controller.
 * <p>
 * The modeler must wrap all k.LAB assets coming from the services into
 * {@link org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset} to enable navigation and
 * selection.
 * <p>
 * TODO the modeler is itself a view that should receive UI events, particularly those related to
 * showing and hiding views.
 */
public interface Modeler extends UIController {

    public enum Option {

        UseAnsiEscapeSequences(Boolean.class);

        final Class<?>[] payloadClass;

        private Option(Class<?>... payloadClass) {
            this.payloadClass = payloadClass;
        }

    }

    /**
     * Set any of the options above with passed payload, which should be validated before use.
     *
     * @param option
     * @param payload
     * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the payload is
     *                                                                                  unsuitable for the
     *                                                                                  option.
     */
    void setOption(Option option, Object... payload);


    @UIActionHandler(value = UIReactor.UIAction.ImportProject, label = "New project", tooltip =
            "Create a new k.LAB project in the current workspace and scope")
    void importProject(String workspaceName, String projectUrl, boolean overwriteExisting);

    @UIActionHandler(UIReactor.UIAction.DeleteProject)
    void deleteProject(String projectUrl);

    @UIActionHandler(UIReactor.UIAction.DeleteAsset)
    void deleteAsset(NavigableAsset asset);

    @UIActionHandler(UIReactor.UIAction.ManageProject)
    void manageProject(String projectId, Repository.Operation operation, String... arguments);

    @UIActionHandler(UIReactor.UIAction.EditProjectProperties)
    void editProperties(String projectId);

    @UIActionHandler(UIReactor.UIAction.CreateAsset)
    void createAsset(String urn, NavigableAsset parentAsset, KlabAsset.KnowledgeClass assetType);
}
