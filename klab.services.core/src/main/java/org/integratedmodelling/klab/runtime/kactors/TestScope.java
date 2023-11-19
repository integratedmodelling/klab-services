package org.integratedmodelling.klab.runtime.kactors;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Fail;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Assert.Assertion;
import org.integratedmodelling.klab.api.lang.kactors.beans.ActionStatistics;
import org.integratedmodelling.klab.api.lang.kactors.beans.AssertionStatistics;
import org.integratedmodelling.klab.api.lang.kactors.beans.TestStatistics;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.documentation.AsciiDocBuilder;
import org.integratedmodelling.klab.documentation.AsciiDocBuilder.Option;
import org.integratedmodelling.klab.documentation.AsciiDocBuilder.Section;
import org.integratedmodelling.klab.documentation.AsciiDocBuilder.Table;
import org.integratedmodelling.klab.testing.LogFile;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * Additional scope for actions in test scripts.
 * 
 * @author Ferd
 *
 */
public class TestScope {

	// created in the root scope and passed on to the children as is
	private List<TestStatistics> statistics;

	// test-scoped scopes make one of these and add it to statistics
	private TestStatistics testStatistics;

	// action-scoped scopes make one of these through the test statistics
	private ActionStatistics actionStatistics;
	private LogFile log;
	private KActorsBehavior behavior;
	private int level = 0;
	private File logFile = null;
	private KActorsBehavior parentBehavior = null;
	private List<Throwable> exceptions = new ArrayList<>();

	/*
	 * The root scope will build and pass around a document builder based on the
	 * extension of the doc file. Lower-level doc file specs will be ignored.
	 */
	AsciiDocBuilder docBuilder;
	private KActorsAction action;
	private Section docSection;
	private TestScope parent;
	// unique, used for communication organization only
	private String testScopeId;
	private Channel monitor;
	private Identity identity;

//	private ISession session;

	// set only by an explicit fail instruction
	private String failureMessage;

	/*
	 * TODO constraint system for URNs to use. Must be part of runtime, not the
	 * actor system.
	 */
	public TestScope(TestScope other) {

//		this.session = other.session;
		this.statistics = other.statistics;
		this.testStatistics = other.testStatistics;
		this.actionStatistics = other.actionStatistics;
		this.action = other.action;
		this.parentBehavior = other.parentBehavior;
		this.behavior = other.behavior;
		this.level = other.level;
		this.logFile = other.logFile;
		this.log = other.log;
		this.docBuilder = other.docBuilder;
		this.testScopeId = other.testScopeId;
		this.monitor = other.monitor;
		this.identity = other.identity;
	}

	public TestScope(Identity identity, Channel monitor) {
		this.identity = identity;
		this.monitor = monitor;
		this.statistics = new ArrayList<>();
		this.docBuilder = new AsciiDocBuilder("Test report",
				"Run by " + identity + " on " + TimeInstant.create() + " [k.LAB " + Version.CURRENT + "]",
				Option.NUMBER_SECTIONS);
		this.docSection = this.docBuilder.getRootSection();
		this.docSection.action(() -> getAsciidocDescription());
		this.testScopeId = Utils.Names.newName("testscope");
	}

	public void onException(Throwable t) {
		exceptions.add(t);
	}

	public String getTestId() {
		return this.testScopeId;
	}

	public List<TestStatistics> getStatistics() {
		return statistics;
	}

	public void setStatistics(List<TestStatistics> statistics) {
		this.statistics = statistics;
	}

	public TestStatistics getTestStatistics() {
		return testStatistics;
	}

	public void setTestStatistics(TestStatistics testStatistics) {
		this.testStatistics = testStatistics;
	}

	public ActionStatistics getActionStatistics() {
		return actionStatistics;
	}

	public void setActionStatistics(ActionStatistics actionStatistics) {
		this.actionStatistics = actionStatistics;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public KActorsBehavior getParentBehavior() {
		return parentBehavior;
	}

	public void setParentBehavior(KActorsBehavior parentBehavior) {
		this.parentBehavior = parentBehavior;
	}

	public List<Throwable> getExceptions() {
		return exceptions;
	}

	public void setExceptions(List<Throwable> exceptions) {
		this.exceptions = exceptions;
	}

	public KActorsAction getAction() {
		return action;
	}

	public void setAction(KActorsAction action) {
		this.action = action;
	}

	public TestScope getParent() {
		return parent;
	}

	public void setParent(TestScope parent) {
		this.parent = parent;
	}

	public String getTestScopeId() {
		return testScopeId;
	}

	public void setTestScopeId(String testScopeId) {
		this.testScopeId = testScopeId;
	}

	public String getFailureMessage() {
		return failureMessage;
	}

	public void setFailureMessage(String failureMessage) {
		this.failureMessage = failureMessage;
	}

	/**
	 * Called at end of each @test action
	 * 
	 * @param action
	 * @param returnValue
	 */
	public void finalizeTest(KActorsAction action, Object returnValue) {

		this.actionStatistics.setEnd(System.currentTimeMillis());
		for (AssertionStatistics a : this.actionStatistics.getAssertions()) {
			if (a.isSuccess()) {
				this.actionStatistics.setSuccess(this.actionStatistics.getSuccess() + 1);
			} else {
				this.actionStatistics.setFailure(this.actionStatistics.getFailure() + 1);
			}
		}

		this.monitor.send(Message.create(identity.getId(), Message.MessageClass.UnitTests, Message.MessageType.TestFinished,
				this.actionStatistics));

	}

	public ActionStatistics newAction(TestStatistics test, KActorsAction action) {

		ActionStatistics ret = new ActionStatistics();
		ret.setPath(action.getName());
		ret.setName(Utils.Paths.getLast(ret.getPath(), '.'));
		ret.setLabel(ret.getName());
		ret.setTestCaseName(test.getName());
		Annotation tann = Utils.Annotations.getAnnotation(action.getAnnotations(), "test");
		if (tann != null) {
			if (tann.containsKey("comment")) {
				ret.setDescription(tann.get("comment", String.class));
			}
			if (tann.containsKey("label")) {
				ret.setLabel(tann.get("label", String.class));
			}
		}
		ret.setSourceCode(action.getSourceCode());
		ret.setStart(System.currentTimeMillis());

		monitor.send(Message.create(identity.getId(), Message.MessageClass.UnitTests, Message.MessageType.TestStarted, ret));

		test.getActions().add(ret);
		return ret;
	}

	/**
	 * Called at the end of each testcase behavior
	 */
	public void finalizeTestRun() {

		/*
		 * TODO remove - should be optional after everything has ended
		 */
		docBuilder.writeToFile(new File(System.getProperty("user.home") + File.separator + "testoutput.adoc").toPath(),
				Charset.forName("UTF-8"));

		monitor.send(Message.create(identity.getId(), Message.MessageClass.UnitTests, Message.MessageType.TestCaseFinished,
				this.testStatistics));

	}

	public TestScope getChild(KActorsAction action) {

		TestScope ret = new TestScope(this);
		ret.docSection = this.docSection.getChild("anchor:" + action.getName() + "[]" + "Test:  " + action.getName());
		ret.action = action;
		ret.parent = this;
		ret.actionStatistics = newAction(ret.testStatistics, action);
		ret.docSection.action(() -> {

			StringBuffer buffer = new StringBuffer();

			if (ret.actionStatistics.getDescription() != null && !ret.actionStatistics.getDescription().isEmpty()) {
				buffer.append(ret.actionStatistics.getDescription() + "\n\n");
			}

			buffer.append("Test completed in "
					+ TimeDuration.print(ret.actionStatistics.getEnd() - ret.actionStatistics.getStart())
					+ ": result is " + (ret.actionStatistics.getFailure() == 0 ? "[lime]#SUCCESS#" : "[red]#FAIL#")
					+ "\n\n");

			buffer.append(".Source code\n");
			buffer.append(AsciiDocBuilder.listingBlock(action.getSourceCode(), "kactors", Option.COLLAPSIBLE));

			if (ret.actionStatistics.getAssertions().size() > 0) {
				Table table = new Table("Description", "Outcome").title("Assertions").spans(7, 1);
				for (AssertionStatistics assertion : ret.actionStatistics.getAssertions()) {
					table.addRow(assertion.getDescriptor(), assertion.isSuccess() ? "[lime]#SUCCESS#" : "[red]#FAIL#");
				}
				buffer.append("\n" + table.toString());
			}

			return buffer.toString();
		});
		return ret;
	}

	public TestScope getChild(KActorsBehavior behavior) {
		TestScope ret = new TestScope(this);
		ret.parentBehavior = this.behavior;
		ret.behavior = behavior;
		ret.level = this.level + 1;
		ret.docSection = this.docSection.getChild("anchor:" + behavior.getUrn() + "[]" + behavior.getUrn());
		ret.parent = this;
		ret.testStatistics = new TestStatistics(behavior);
		ret.docSection.action(() -> {

			StringBuffer buffer = new StringBuffer();
			int success = ret.testStatistics.successCount();
			int failed = ret.testStatistics.failureCount();
			long elapsed = 0;
			for (ActionStatistics action : ret.testStatistics.getActions()) {
				elapsed += action.getEnd() - action.getStart();
			}

			if (ret.behavior.getMetadata().containsKey(Metadata.DC_COMMENT)) {
				buffer.append("\n" + ret.behavior.getMetadata().get(Metadata.DC_COMMENT) + "\n");
			}

			buffer.append(
					"\nTotal tests run: " + (success + failed) + " of which [lime]#" + success + "# successful, [red]#"
							+ failed + "# failed. Total test run time " + TimeDuration.print(elapsed) + ".\n");

			return buffer.toString();
		});

		this.statistics.add(ret.testStatistics);
		return ret;
	}

	/*
	 * Top-level test suite description. By now all tests have finished.
	 */
	private String getAsciidocDescription() {

		int totalOk = 0, totalFail = 0, totalSkipped = 0;
		long start = Long.MAX_VALUE, end = Long.MIN_VALUE;

		for (TestStatistics child : this.statistics) {
			for (ActionStatistics action : child.getActions()) {
				if (action.getStart() < start) {
					start = action.getStart();
				}
				if (action.getEnd() > end) {
					end = action.getEnd();
				}
				if (action.isSkipped()) {
					totalSkipped++;
				} else if (action.getFailure() > 0) {
					totalFail++;
				} else {
					totalOk++;
				}
			}
		}

		Table table = new Table(3).spans(1, 7, 1);
		table.addRow(new Table.Span(2, 1), "**Overall test results**", (totalOk + "/" + (totalOk + totalFail)));

		for (TestStatistics child : this.statistics) {

			table.addRow(new Table.Span(2, 1), "<<" + child.getName() + ", Test case **" + child.getName() + "**>>",
					((child.successCount() + "/" + (child.successCount() + child.failureCount()))));

			int i = 1;
			for (ActionStatistics action : child.getActions()) {
				table.addRow(">Test #" + (i++), "<<" + action.getName() + ", " + action.getName() + ">>",
						action.isSkipped() ? "SKIP" : (action.getFailure() == 0 ? "[lime]#SUCCESS#" : "[red]#FAIL#"));
			}
		}

		return (totalOk + totalFail) + " tests run in " + TimeDuration.print(end - start) + " ([lime]#" + totalOk
				+ "# succeeded, [red]#" + totalFail + "# failed, " + totalSkipped + " skipped)\n\n" + table.toString();
	}

	/**
	 * The root scope holds all the statistics
	 * 
	 * @return
	 */
	public TestScope getRoot() {
		return this.parent == null ? this : this.parent.getRoot();
	}

	public void notifyAssertion(Object result, KActorsValue expected, boolean ok, Assertion assertion) {

		AssertionStatistics desc = this.actionStatistics.createAssertion(assertion);
		if ((desc.setSuccess(ok))) {
			this.actionStatistics.setSuccess(this.actionStatistics.getSuccess() + 1);
		} else {
			this.actionStatistics.setFailure(this.actionStatistics.getFailure() + 1);
		}
		if (ok) {
			if (assertion.getMetadata().containsKey("success")) {
				desc.setDescriptor(Utils.Templates.substitute(assertion.getMetadata().get("success", String.class),
						"value", result, "expected", expected));
			} else {
				if (expected != null) {
					desc.setDescriptor("Expected value " + expected + " was returned");
				}
			}
		} else {
			if (assertion.getMetadata().containsKey("fail")) {
				desc.setDescriptor(Utils.Templates.substitute(assertion.getMetadata().get("fail", String.class), "value",
						result, "expected", expected));
			} else {
				if (expected != null) {
					desc.setDescriptor("Expected " + expected + ", got " + result);
				}
			}
		}

		if (desc.getDescriptor() == null) {
			desc.setDescriptor(Utils.Strings.abbreviate(Utils.Strings.getFirstLine(assertion.getSourceCode()), 60));
		}

	}

	public void fail(Fail code) {
		this.actionStatistics.setFailure(this.actionStatistics.getFailure() + 1);
		this.failureMessage = code.getMessage();
	}

}
