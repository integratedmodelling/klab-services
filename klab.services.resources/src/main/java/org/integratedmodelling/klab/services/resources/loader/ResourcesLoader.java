package org.integratedmodelling.klab.services.resources.loader;

import com.google.inject.Inject;
import com.google.inject.Injector;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.languages.KimStandaloneSetup;
import org.integratedmodelling.languages.ObservableStandaloneSetup;
import org.integratedmodelling.languages.WorldviewStandaloneSetup;
import org.integratedmodelling.languages.kim.Model;
import org.integratedmodelling.languages.observable.ObservableSequence;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.integratedmodelling.languages.worldview.Ontology;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResourcesLoader {

    private final Reasoner reasoner;
    private final LanguageValidationScope languageValidationScope;
    private Map<String, LanguageValidationScope.ConceptDescriptor> conceptDescriptors = new HashMap<>();

    private Parser<ObservableSequence> observableParser = new Parser<ObservableSequence>() {
        @Override
        protected Injector createInjector() {
            return new ObservableStandaloneSetup().createInjectorAndDoEMFRegistration();
        }
    };

    private Parser<Ontology> ontologyParser = new Parser<Ontology>() {
        @Override
        protected Injector createInjector() {
            return new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration();
        }
    };

    private Parser<Model> namespaceParser = new Parser<Model>() {
        @Override
        protected Injector createInjector() {
            return new KimStandaloneSetup().createInjectorAndDoEMFRegistration();
        }
    };

    /**
     * Initialize semantic catalog with any pre-existing worldview we may have from a different service.
     *
     * @param worldview
     */
    public ResourcesLoader(ServiceScope serviceScope, List<Project> worldview) {

        this.reasoner = serviceScope.getService(Reasoner.class);

        this.languageValidationScope = new BasicObservableValidationScope() {
            @Override
            public ConceptDescriptor getConceptDescriptor(String name) {
                return conceptDescriptors.get(name);
            }

            @Override
            public boolean hasReasoner() {
                return reasoner != null;
            }
        };
    }

    /**
     * Read all projects in a workspace directory, populating the project list in order of dependency
     *
     * @param workspace
     * @param projects
     */
    public void loadWorkspace(File workspace, List<Project> projects) {

        /**
         * Find all projects, read the manifest and establish project order
         */

        /**
         * Read all worldview files first. As new concepts and models are read, populate the semantic
         * catalog so that the types can be established.
         */
    }


    private abstract class Parser<T extends EObject> {

        @Inject
        private IParser parser;

        public Parser() {
            setupParser();
        }

        private void setupParser() {
            Injector injector = createInjector();
            injector.injectMembers(this);
        }

        protected abstract Injector createInjector();

        /**
         * Parses data provided by an input reader using Xtext and returns the root node of the resulting
         * object tree.
         *
         * @param reader Input reader
         * @return root object node
         * @throws IOException when errors occur during the parsing process
         */
        public T parse(Reader reader, List<Notification> errors) throws IOException {
            IParseResult result = parser.parse(reader);
            for (var error : result.getSyntaxErrors()) {

            }
            return (T) result.getRootASTElement();
        }
    }


}
