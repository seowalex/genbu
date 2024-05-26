package genbu;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import genbu.writer.IndentationStyle;
import genbu.writer.PrefixAlignment;
import genbu.writer.TurtleWriter;

public class Main {
    public static void main(String[] args) throws IOException {
        var in = new FileInputStream("example.ttl");
        var model = Rio.parse(in, RDFFormat.TURTLE);

        var writer = new TurtleWriter(System.out);
        writer.set(BasicWriterSettings.INLINE_BLANK_NODES, true);

        writer.startRDF();

        // ALIGN PREFIXES

        var maxPrefixWidth = model.getNamespaces().stream().map(Namespace::getPrefix)
                .map(String::length).max(Integer::compare);

        writer.setPrefixAlignment(Optional.empty());
        writer.setPrefixAlignment(maxPrefixWidth.map(PrefixAlignment::RIGHT));
        writer.setPrefixAlignment(maxPrefixWidth.map(PrefixAlignment::LEFT));

        // INDENTATION STYLE

        writer.setIndentationStyle(IndentationStyle.TAB);
        writer.setIndentationStyle(IndentationStyle.SPACE(2));
        writer.setIndentationStyle(IndentationStyle.SPACE(4));

        for (var nextNamespace : model.getNamespaces()) {
            writer.handleNamespace(nextNamespace.getPrefix(), nextNamespace.getName());
        }

        for (var st : model) {
            writer.handleStatement(st);
        }

        writer.endRDF();
    }
}
