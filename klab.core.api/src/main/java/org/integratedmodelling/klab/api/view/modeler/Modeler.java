package org.integratedmodelling.klab.api.view.modeler;

import org.integratedmodelling.klab.api.view.UIController;
import org.integratedmodelling.klab.api.view.UIReactor;
import org.integratedmodelling.klab.api.view.annotations.UIActionHandler;

/**
 * A {@link UIController} that contains all the user actions relevant to a modeler IDE. Implement this to use
 * it in any view type. Can also provide a blueprint to implement a non-Java modeler using websockets to drive
 * a Java counterpart controller.
 * <p>
 * The modeler must wrap all k.LAB assets coming from the services into
 * {@link org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset} to enable navigation and
 * selection.
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

    /**
     * User action "Import project...". Must not throw exceptions, run asynchronously and dispatch all
     * relevant events upon success or failure.
     *
     * TODO provide default implementations for all obvious actions.
     *
     * @param projectUrl
     * @param workspace
     * @param service
     */
    @UIActionHandler(UIReactor.UIAction.ImportProject)
    void importProject(String projectUrl, String workspace, String service);
}
