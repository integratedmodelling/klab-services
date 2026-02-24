package org.integratedmodelling.klab.runtime.libraries;

import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.ActionScope;
import org.integratedmodelling.klab.runtime.kactors.compiler.ActorBase;

@Library(name="actors.core")
public class CoreActorLibrary {

    @Actor(name = "console", description = "A simple actor that prints to the console")
    public static class Console extends ActorBase {

        public Console(KActorsBehavior behavior) {
            super(behavior);
        }

        @Override
        protected ActionScope main(ActionScope initialScope, SessionScope session) {
            return null;
        }

        @Verb(name="print")
        public void print(Object message) {

        }
    }

    @Actor(name = "timer", description = "Time event generator")
    public static class Timer extends ActorBase {

        public Timer(KActorsBehavior behavior) {
            super(behavior);
        }

        @Override
        protected ActionScope main(ActionScope initialScope, SessionScope session) {
            return null;
        }

        @Verb(name="print")
        public void random(Quantity quantity) {

        }
    }

}
