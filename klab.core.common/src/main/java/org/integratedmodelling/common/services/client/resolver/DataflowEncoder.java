package org.integratedmodelling.common.services.client.resolver;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Encode a dataflow into a k.LAB-compatible serialized form.
 */
public class DataflowEncoder {

    private final Dataflow<?> dataflow;
    private final ContextScope scope;

    public DataflowEncoder(Dataflow<?> dataflow, ContextScope scope) {
        this.dataflow = dataflow;
        this.scope = scope;
    }

    public void encode(PrintWriter outWriter) {
        encodePreamble(outWriter);
        outWriter.append("\n");
        encodeDefinitions(outWriter);
        for (Actuator actuator : dataflow.getComputation()) {
            encodeActuator(actuator, outWriter, 0);
        }
    }

    private void encodeActuator(Actuator actuator, PrintWriter outWriter, int indent) {

        String singleSpacer = Utils.Strings.spaces(indent);
        String doubleSpacer = Utils.Strings.spaces(indent + 3);

        outWriter.append(singleSpacer).append(actuator.getActuatorType().name().toLowerCase()).append(" ");
        outWriter.append(actuator.getActuatorType() == Actuator.Type.RESOLVE
                        ? ("obs" + actuator.getId())
                        : actuator.getObservable().getUrn());

        if (actuator.getStrategyUrn() != null) {
            outWriter.append("\n").append(doubleSpacer).append("using ").append(actuator.getStrategyUrn());
        }

        if (!actuator.getChildren().isEmpty()) {
            outWriter.append("\n").append(doubleSpacer).append("(\n");
            int i = 0;
            for (var child : actuator.getChildren()) {
                encodeActuator(child, outWriter, indent + 6);
                if (i < (actuator.getChildren().size() - 1)) {
                    outWriter.append(",\n");
                }
                i++;
            }
            outWriter.append("\n").append(doubleSpacer).append(")");
        }

        if (!actuator.getComputation().isEmpty()) {
            outWriter.append("\n").append(doubleSpacer).append("apply");
            int i = 0;
            for (var computation : actuator.getComputation()) {
                if (actuator.getComputation().size() > 1) {
                    outWriter.append("\n").append(singleSpacer).append(doubleSpacer);
                } else {
                    outWriter.append(" ");
                }
                outWriter.append(computation.encode(Language.KIM)).append(i < (actuator.getComputation().size() - 1) ? "," : "");
                i++;
            }
        }

        if (indent == 0) {
            outWriter.append(";");
        }

    }

    private void encodeDefinitions(PrintWriter outWriter) {
    }

    private void encodePreamble(PrintWriter outWriter) {
        outWriter.append("dataflow " + sanitize(scope.getName() == null ? scope.getId() : scope.getName()));
        outWriter.append("\n;");
    }

    private String sanitize(String name) {
        return "dio.porco";
//        var ret = name.replace(" ", ".").toLowerCase();
//        // TODO more
//        return ret;
    }

    @Override
    public String toString() {
        try (var writer = new StringWriter()) {
            var printWriter = new PrintWriter(writer);
            encode(printWriter);
            return writer.toString();
        } catch (Exception e) {
        }
        return "Error encoding dataflow";
    }
}
