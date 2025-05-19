package org.integratedmodelling.cli;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.utils.Utils;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.PrintWriter;
import java.util.List;

@Command(name = "set", mixinStandardHelpOptions = true, version = Version.CURRENT, description = {
        "Commands to find, access and manipulate resources.",
        ""}, subcommands = {})
public class Set implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec commandSpec;

    @CommandLine.Parameters
    java.util.List<String> observables;

    // TODO add parameters and API to modify settings in local services (individually or collectively)
    // TODO integrate with persistent settings from Configuration and StartupOptions when they're enums

    @Override
    public void run() {

        PrintWriter out = commandSpec.commandLine().getOut();
        PrintWriter err = commandSpec.commandLine().getErr();

        if (observables == null || observables.isEmpty()) {
            for (var setting : KlabCLI.INSTANCE.engine().getSettings().keySet()) {
                out.println(CommandLine.Help.Ansi.AUTO.string("@|cyan " + settingToName(setting) + "|@: " +
                        "@|green " + KlabCLI.INSTANCE.engine().getSettings().get(setting) + "|@"));
            }
            return;
        }

        if (observables.size() == 2) {
            var ok = false;
            var setting = nameToSetting(observables.get(0), err);
            if (setting != null) {
                var value = Utils.Data.asType(observables.get(1), setting.valueClass);
                if (setting.validate(value)) {
                    KlabCLI.INSTANCE.engine().getSettings().put(setting, value);
                    ok = true;
                }
            }

            if (!ok && setting != null) {
                err.println("Invalid value " + observables.get(1) + " for setting " + setting);
            }

        } else {
            err.println("Not enough parameters for set command");
        }

    }

    private String settingToName(Engine.Setting setting) {
        return setting.name().toLowerCase().replace("_", ".");
    }

    private Engine.Setting nameToSetting(String setting, PrintWriter err) {
        try {
            return Engine.Setting.valueOf(setting.replace(".", "_").toUpperCase());
        } catch (Throwable t) {
            err.println("Unknown setting " + setting);
        }
        return null;
    }

}