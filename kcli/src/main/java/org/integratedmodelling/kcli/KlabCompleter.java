package org.integratedmodelling.kcli;

import java.util.List;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import picocli.CommandLine.Model.CommandSpec;
import picocli.shell.jline3.PicocliJLineCompleter;

public class KlabCompleter extends PicocliJLineCompleter {

    public KlabCompleter(CommandSpec spec) {
        super(spec);
    }

    
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
//        delegate.complete(reader, line, candidates);
    }

}
