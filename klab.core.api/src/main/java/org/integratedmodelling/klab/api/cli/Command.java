package org.integratedmodelling.klab.api.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class Command {

    List<Option> options = new ArrayList<>();
    List<Command> subCommands = new ArrayList<>();
    private Function<CommandLine, Object> handler;


}
