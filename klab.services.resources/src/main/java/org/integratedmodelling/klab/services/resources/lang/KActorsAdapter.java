package org.integratedmodelling.klab.services.resources.lang;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.eclipse.xtext.testing.IInjectorProvider;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.integratedmodelling.kactors.api.IKActorsAction;
import org.integratedmodelling.kactors.api.IKActorsBehavior;
import org.integratedmodelling.kactors.api.IKActorsStatement;
import org.integratedmodelling.kactors.api.IKActorsValue;
import org.integratedmodelling.kactors.kactors.Model;
import org.integratedmodelling.kactors.model.KActors;
import org.integratedmodelling.kim.api.IKimAnnotation;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.impl.kactors.KActorsValueImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.exceptions.KlabException;
import org.integratedmodelling.klab.exceptions.KlabIOException;
import org.integratedmodelling.klab.exceptions.KlabValidationException;
import org.integratedmodelling.klab.utils.Pair;
import org.integratedmodelling.klab.utils.Utils;
import org.integratedmodelling.klab.utils.Utils.Lang;

import com.google.inject.Inject;
import com.google.inject.Injector;

public enum KActorsAdapter {

    INSTANCE;

    @Inject
    private ParseHelper<Model> kActorsParser;

    private KActorsAdapter() {
        IInjectorProvider injectorProvider = new KactorsInjectorProvider();
        Injector injector = injectorProvider.getInjector();
        if (injector != null) {
            injector.injectMembers(this);
        }
    }

    public KActorsBehavior readBehavior(File behaviorFile) {
        IKActorsBehavior behavior = declare(behaviorFile);
        return adapt(behavior);
    }

    private KActorsBehavior adapt(IKActorsBehavior behavior) {

        KActorsBehaviorImpl ret = new KActorsBehaviorImpl();

        ret.setName(behavior.getName());
        ret.setDeprecated(behavior.isDeprecated());
        ret.setErrors(behavior.isErrors());
        ret.setDescription(behavior.getDescription());
        ret.setLogo(behavior.getLogo());
        ret.setLabel(behavior.getName());
        ret.setSourceCode(behavior.getSourceCode());
        ret.setDeprecation(behavior.getDeprecation());
        ret.getImports().addAll(behavior.getImports());
        ret.getLocales().addAll(behavior.getLocales());
        ret.setMetadata(Utils.Lang.makeMetadata(behavior.getMetadata()));
        ret.setTag(behavior.getTag());

        for (IKActorsAction action : behavior.getActions()) {
            ret.getActions().add(adaptAction(action));
        }

        for (IKimAnnotation annotation : behavior.getAnnotations()) {
            ret.getAnnotations().add(Utils.Lang.makeAnnotation(annotation));
        }

        return ret;
    }

    KActorsAction adaptAction(IKActorsAction action) {

        KActorsActionImpl ret = new KActorsActionImpl();

        Lang.copyStatementData(action, ret);

        ret.setName(action.getName());
        ret.getArgumentNames().addAll(action.getArgumentNames());
        ret.setCode(adaptStatement(action.getCode()));

        return ret;
    }

    KActorsStatement adaptStatement(IKActorsStatement code) {

        KActorsStatementImpl ret = null;

        switch(code.getType()) {
        case ACTION_CALL:
            ret = new KActorsStatementImpl.CallImpl();
            break;
        // case ASSERTION:
        // ret = new KActorsStatementImpl.AssertionImpl();
        // break;
        case ASSERT_STATEMENT:
            ret = new KActorsStatementImpl.AssertionImpl();
            break;
        case ASSIGNMENT:
            ret = new KActorsStatementImpl.AssignmentImpl();
            break;
        case BREAK_STATEMENT:
            ret = new KActorsStatementImpl.BreakImpl();
            break;
        case CONCURRENT_GROUP:
            ret = new KActorsStatementImpl.ConcurrentGroupImpl();
            for (IKActorsStatement statement : ((IKActorsStatement.ConcurrentGroup) code).getStatements()) {
                ((KActorsStatementImpl.ConcurrentGroupImpl) ret).getStatements().add(adaptStatement(statement));
            }
            for (String key : ((IKActorsStatement.ConcurrentGroup) code).getGroupMetadata().keySet()) {
                ((KActorsStatementImpl.ConcurrentGroupImpl) ret).getGroupMetadata().put(key,
                        adaptValue(((IKActorsStatement.ConcurrentGroup) code).getGroupMetadata().get(key)));
            }
            for (Pair<IKActorsValue, IKActorsStatement> action : ((IKActorsStatement.ConcurrentGroup) code).getGroupActions()) {
                ((KActorsStatementImpl.ConcurrentGroupImpl) ret).getGroupActions()
                        .add(org.integratedmodelling.klab.api.collections.Pair.of(adaptValue(action.getFirst()),
                                adaptStatement(action.getSecond())));
            }
            break;
        case DO_STATEMENT:
            ret = new KActorsStatementImpl.DoImpl();
            break;
        case FAIL_STATEMENT:
            ret = new KActorsStatementImpl.FailImpl();
            break;
        case FIRE_VALUE:
            ret = new KActorsStatementImpl.FireImpl();
            break;
        case FOR_STATEMENT:
            ret = new KActorsStatementImpl.ForImpl();
            break;
        case IF_STATEMENT:
            ret = new KActorsStatementImpl.IfImpl();
            break;
        case INSTANTIATION:
            ret = new KActorsStatementImpl.InstantiationImpl();
            break;
        case SEQUENCE:
            ret = new KActorsStatementImpl.SequenceImpl();
            break;
        case TEXT_BLOCK:
            ret = new KActorsStatementImpl.TextBlockImpl();
            break;
        case WHILE_STATEMENT:
            ret = new KActorsStatementImpl.WhileImpl();
            break;
        default:
            break;
        }

        Lang.copyStatementData(code, ret);

        return ret;
    }

    private KActorsValue adaptValue(IKActorsValue ikActorsValue) {
        KActorsValueImpl ret = new KActorsValueImpl();
        // TODO
        return ret;
    }

    public IKActorsBehavior declare(URL url) throws KlabException {
        try (InputStream stream = url.openStream()) {
            return declare(stream);
        } catch (Exception e) {
            throw new KlabIOException(e);
        }
    }

    public IKActorsBehavior declare(File file) throws KlabException {
        try (InputStream stream = new FileInputStream(file)) {
            return declare(stream);
        } catch (Exception e) {
            throw new KlabIOException(e);
        }
    }

    public IKActorsBehavior declare(InputStream file) throws KlabValidationException {
        IKActorsBehavior ret = null;
        try {
            String definition = IOUtils.toString(file, StandardCharsets.UTF_8);
            Model model = kActorsParser.parse(definition);
            ret = KActors.INSTANCE.declare(model);
        } catch (Exception e) {
            throw new KlabValidationException(e);
        }
        return ret;
    }
}
