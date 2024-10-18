package org.integratedmodelling.klab.services.resolver;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

import java.io.PrintWriter;

/**
 * Encode a dataflow into a k.LAB-compatible serialized form.
 */
public class DataflowEncoder {

    private final Dataflow<Observation> dataflow;
    private final ContextScope scope;

    public DataflowEncoder(Dataflow<Observation> dataflow, ContextScope scope) {
        this.dataflow = dataflow;
        this.scope = scope;
    }

    public void encode(PrintWriter outWriter) {
        encodePreamble(outWriter);
        outWriter.append("\n");
        encodeDefinitions(outWriter);
//        outWriter.append("\n");
        for (Actuator actuator : dataflow.getComputation()) {
            encodeActuator(actuator, outWriter, 0);
        }
    }

    private void encodeActuator(Actuator actuator, PrintWriter outWriter, int indent) {

        String spacer = Utils.Strings.spaces(indent);
        String dspacer = Utils.Strings.spaces(indent * 2);

        outWriter.append(spacer).append(actuator.getActuatorType().name().toLowerCase());
        outWriter.append(actuator.getActuatorType() == Actuator.Type.RESOLVE
                ? ("obs" + actuator.getId())
                : actuator.getObservable().getUrn())
                .append("\n");

        if (actuator.getStrategyUrn() != null) {
            outWriter.append(dspacer).append("using ").append(actuator.getStrategyUrn());
        }

        outWriter.append("\n");

        if (!actuator.getChildren().isEmpty()) {
            outWriter.append(dspacer).append("(\n");
            for (var child : actuator.getChildren()) {
                encodeActuator(child, outWriter, indent * 3);
            }
            outWriter.append(dspacer).append(")\n");
        }

        if (!actuator.getComputation().isEmpty()) {
            outWriter.append(dspacer).append("apply\n");
            for (var computation : actuator.getComputation()) {
                outWriter.append(spacer).append(dspacer).append(computation.encode(Language.KIM)).append("\n");
            }
        }

    }

    private void encodeDefinitions(PrintWriter outWriter) {
    }

    private void encodePreamble(PrintWriter outWriter) {
        outWriter.append("dataflow " + scope.getId());
        outWriter.append("\n;");
    }

}
