import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.KimStandaloneSetup;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Read-only G0 probe: no service startup, project registration or source adaptation. */
public class PalParserProbe {
  static Map<String,Object> parse(IParser parser, String name, String text) {
    var parsed = parser.parse(new StringReader(text));
    var errors = new ArrayList<Map<String,Object>>();
    for (var node : parsed.getSyntaxErrors()) {
      if (errors.size() == 100) break;
      errors.add(Map.of("line", node.getStartLine(), "offset", node.getOffset(),
          "length", node.getLength(), "message", node.getSyntaxErrorMessage().getMessage()));
    }
    var result = new LinkedHashMap<String,Object>();
    result.put("path", name);
    result.put("syntax_errors", errors);
    result.put("has_syntax_errors", parsed.hasSyntaxErrors());
    result.put("ast_class", parsed.getRootASTElement() == null ? null : parsed.getRootASTElement().getClass().getName());
    return result;
  }
  public static void main(String[] args) throws Exception {
    if (args.length != 2 || java.nio.file.Files.exists(Path.of(args[1]))) {
      throw new IllegalArgumentException("Supply original ZIP and a fresh output path");
    }
    var parser = new KimStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var results = new ArrayList<Map<String,Object>>();
    results.add(parse(parser, "positive-control", "namespace staging.pal.syntax version 1.0;\n"));
    try (var zip = new ZipFile(args[0])) {
      var entries = zip.entries();
      int count = 0;
      long total = 0;
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        if (!entry.isDirectory() && entry.getName().endsWith(".kim")) {
          total += entry.getSize();
          if (++count > 128 || total > 16 * 1024 * 1024 || entry.getSize() < 0
              || entry.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Source inventory budget exceeded");
          }
          try (var stream = zip.getInputStream(entry)) {
            var source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            results.add(parse(parser, entry.getName(), source));
          }
        }
      }
    }
    try (var output = java.nio.file.Files.newOutputStream(Path.of(args[1]),
        java.nio.file.StandardOpenOption.CREATE_NEW)) {
      new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output, results);
    }
    System.out.println("Read-only parser probe wrote " + results.size() + " results.");
  }
}
