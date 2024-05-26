package genbu;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import genbu.writer.PrefixAlignment;
import genbu.writer.TurtleWriter;

public class Main {
    public static void main(String[] args) throws IOException {
        var in = new FileInputStream("example.ttl");
        var model = Rio.parse(in, RDFFormat.TURTLE);

        var writer = new TurtleWriter(System.out);
        writer.set(BasicWriterSettings.INLINE_BLANK_NODES, true);

        writer.startRDF();

        var maxPrefixWidth = model.getNamespaces().stream().map(Namespace::getPrefix)
                .map(String::length).max(Integer::compare);

        writer.setPrefixAlignment(Optional.empty());
        writer.setPrefixAlignment(maxPrefixWidth.map(PrefixAlignment.RIGHT::new));
        writer.setPrefixAlignment(maxPrefixWidth.map(PrefixAlignment.LEFT::new));

        for (var nextNamespace : model.getNamespaces()) {
            writer.handleNamespace(nextNamespace.getPrefix(), nextNamespace.getName());
        }

        for (var st : model) {
            writer.handleStatement(st);
        }

        writer.endRDF();
    }
}
