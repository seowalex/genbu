package genbu;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import genbu.writer.IndentationStyle;
import genbu.writer.PrefixAlignment;
import genbu.writer.TurtleWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "genbu", versionProvider = Main.ManifestVersionProvider.class,
        description = "A Turtle formatter", mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true)
public class Main implements Callable<Integer> {
    @Parameters(defaultValue = ".", paramLabel = "<files>",
            description = "List of files or directories to format")
    private Set<Path> paths;

    @Option(names = "--exclude", paramLabel = "<pattern>",
            description = "List of patterns, used to omit files and/or directories from analysis")
    private Set<String> excludedPatterns;

    @Option(names = {"-H", "--hidden"}, defaultValue = "true",
            description = "Search hidden files and directories")
    private boolean ignoreHidden;

    @Override
    public Integer call() throws IOException {
        Set<Path> files = new HashSet<>();
        var excludedMatcher = Optional.ofNullable(excludedPatterns).map(patterns -> FileSystems
                .getDefault().getPathMatcher("glob:{" + String.join(",", patterns) + "}"));

        for (var path : paths) {
            Files.walkFileTree(path.normalize(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    if (ignoreHidden && Files.isHidden(dir)
                            || excludedMatcher.map(matcher -> matcher.matches(dir)).orElse(false)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return super.preVisitDirectory(dir, attrs);
                };

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (attrs.isRegularFile() && file.toString().endsWith(".ttl")
                            && !(ignoreHidden && Files.isHidden(file)) && !excludedMatcher
                                    .map(matcher -> matcher.matches(file)).orElse(false)) {
                        files.add(file);
                    }

                    return super.visitFile(file, attrs);
                };
            });
        }

        for (var file : files) {
            var in = Files.newInputStream(file);

            var model = Rio.parse(in, RDFFormat.TURTLE);

            var writer = new TurtleWriter(System.out);
            writer.set(BasicWriterSettings.INLINE_BLANK_NODES, true);

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

            // KEEP UNUSED PREFIXES / CHECK PREFIXES / SORT PREFIXES

            var namespaces = new LinkedHashSet<>(model.getNamespaces());

            for (var statement : model) {
                for (var component : List.of(statement.getSubject(), statement.getPredicate(),
                        statement.getObject())) {
                    if (component instanceof IRI iri) {
                        namespaces.removeIf(
                                namespace -> namespace.getName().equals(iri.getNamespace()));
                    }
                }
            }

            for (var namespace : namespaces) {
                model.removeNamespace(namespace.getPrefix());
            }

            System.out.println(new ParserConfig().get(BasicParserSettings.NAMESPACES));

            //

            writer.startRDF();

            for (var namespace : model.getNamespaces()) {
                writer.handleNamespace(namespace.getPrefix(), namespace.getName());
            }

            for (var statement : model) {
                writer.handleStatement(statement);
            }

            writer.endRDF();
        }

        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    static class ManifestVersionProvider implements IVersionProvider {
        @Override
        public String[] getVersion() {
            var version = getClass().getPackage().getImplementationVersion();

            return new String[] {"${ROOT-COMMAND-NAME} " + version};
        }
    }
}
