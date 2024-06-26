package genbu;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.impl.DynamicModel;
import org.eclipse.rdf4j.model.impl.LinkedHashModelFactory;
import org.eclipse.rdf4j.model.util.ModelException;
import org.eclipse.rdf4j.model.util.Namespaces;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import genbu.parser.TurtleParser;
import genbu.writer.IndentationStyle;
import genbu.writer.PrefixAlignment;
import genbu.writer.TurtleWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

enum IndentationStyleValues {
    space, tab
}


enum PrefixAlignmentValues {
    left, right
}


@Command(name = "genbu", versionProvider = Main.ManifestVersionProvider.class,
        description = "A Turtle formatter", mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true, abbreviateSynopsis = true, descriptionHeading = "%n",
        parameterListHeading = "%nParameters:%n", optionListHeading = "%nOptions:%n")
public class Main implements Callable<Integer> {
    @Parameters(defaultValue = ".", paramLabel = "<files>",
            description = "List of files or directories to format [default: ${DEFAULT-VALUE}]")
    private Set<Path> paths;

    @Option(names = {"-H", "--hidden"}, defaultValue = "true",
            description = "Search hidden files and directories")
    private boolean ignoreHidden;

    @Option(names = "--exclude", paramLabel = "<pattern>",
            description = "List of patterns, used to omit files and/or directories from analysis")
    private Set<String> excludedPatterns;

    @Option(names = "--indentStyle", defaultValue = "space", paramLabel = "<style>",
            description = """
                    Note that when choosing tab, alignPredicates and alignObjects are automatically treated as false.
                    [default: ${DEFAULT-VALUE}] [possible values: ${COMPLETION-CANDIDATES}]""")
    private IndentationStyleValues indentationStyle;

    @Option(names = "--indentWidth", defaultValue = "2", paramLabel = "<width>",
            description = "[default: ${DEFAULT-VALUE}]")
    private int indentationWidth;

    @Option(names = "--alignPrefixes", paramLabel = "<alignment>",
            description = "Align @prefix statements [possible values: ${COMPLETION-CANDIDATES}]")
    private Optional<PrefixAlignmentValues> prefixAlignment;

    @Option(names = "--keepUnusedPrefixes", defaultValue = "true",
            description = "Keeps prefixes that are not part of any statement")
    private boolean discardUnusedPrefixes;

    @Option(names = "--sortPrefixes", description = "Sort prefixes")
    private boolean sortPrefixes;

    @Option(names = "--checkDefaultNamespaces",
            description = "Check namespaces for correct IRIs if they are part of the default namespaces")
    private boolean checkDefaultNamespaces;

    @Option(names = "--firstPredicateInNewLine",
            description = "Write first predicate in new line of block")
    private boolean firstPredicateInNewLine;

    @Option(names = "--useRdfType", description = "Use rdf:type instead of a")
    private boolean useRdfType;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        Set<Path> files = new HashSet<>();
        var excludedMatcher = Optional.ofNullable(excludedPatterns).map(patterns -> FileSystems
                .getDefault().getPathMatcher("glob:{" + String.join(",", patterns) + "}"));

        if (paths.stream().map(path -> {
            try {
                Files.walkFileTree(path.normalize(), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException {
                        if (ignoreHidden && Files.isHidden(dir) || excludedMatcher
                                .map(matcher -> matcher.matches(dir)).orElse(false)) {
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

                return false;
            } catch (NoSuchFileException e) {
                spec.commandLine().getErr().println(spec.commandLine().getColorScheme()
                        .errorText(e.getFile() + ": No such file or directory"));

                return true;
            } catch (Exception e) {
                spec.commandLine().getErr().println(spec.commandLine().getColorScheme()
                        .errorText(path + ": " + e.getMessage()));

                return true;
            }
        }).toList().contains(true)) {
            return spec.exitCodeOnExecutionException();
        }

        if (files.stream().map(file -> {
            try {
                var in = Files.newInputStream(file);
                var parser = new TurtleParser();
                var model = new DynamicModel(new LinkedHashModelFactory());

                parser.setRDFHandler(new StatementCollector(model));
                parser.set(BasicParserSettings.NAMESPACES, Collections.emptySet());
                parser.parse(in);

                var writer = new TurtleWriter(System.out);
                writer.set(BasicWriterSettings.INLINE_BLANK_NODES, true);

                writer.setIndentationStyle(switch (indentationStyle) {
                    case space -> IndentationStyle.SPACE(indentationWidth);
                    case tab -> IndentationStyle.TAB;
                });

                var maxPrefixWidth = model.getNamespaces().stream().map(Namespace::getPrefix)
                        .map(String::length).max(Integer::compare);

                writer.setPrefixAlignment(
                        prefixAlignment.flatMap(alignment -> maxPrefixWidth.map(switch (alignment) {
                            case left -> PrefixAlignment::LEFT;
                            case right -> PrefixAlignment::RIGHT;
                        })));

                if (discardUnusedPrefixes) {
                    var namespaces = new LinkedHashSet<>(model.getNamespaces());

                    for (var statement : model) {
                        for (var component : List.of(statement.getSubject(),
                                statement.getPredicate(), statement.getObject())) {
                            if (component instanceof IRI iri) {
                                namespaces.removeIf(namespace -> namespace.getName()
                                        .equals(iri.getNamespace()));
                            }
                        }
                    }

                    for (var namespace : namespaces) {
                        model.removeNamespace(namespace.getPrefix());
                    }
                }

                var namespaces =
                        sortPrefixes ? new TreeSet<>(model.getNamespaces()) : model.getNamespaces();

                if (checkDefaultNamespaces) {
                    var defaultNamespaces = Namespaces.DEFAULT_RDF4J.stream()
                            .collect(Collectors.toMap(Namespace::getPrefix, Namespace::getName));

                    for (var namespace : model.getNamespaces()) {
                        var prefix = namespace.getPrefix();
                        var name = namespace.getName();
                        var expectedName = defaultNamespaces.get(prefix);

                        if (Optional.ofNullable(expectedName).map(iri -> !iri.equals(name))
                                .orElse(false)) {
                            throw new ModelException("Expected namespace prefix '" + prefix
                                    + "' to be associated with '" + expectedName + "', found '"
                                    + name + "'");
                        }
                    }
                }

                writer.setFirstPredicateInNewLine(firstPredicateInNewLine);
                writer.setUseRdfType(useRdfType);

                writer.startRDF();

                for (var namespace : namespaces) {
                    writer.handleNamespace(namespace.getPrefix(), namespace.getName());
                }

                for (var statement : model) {
                    writer.handleStatement(statement);
                }

                writer.endRDF();

                return false;
            } catch (Exception e) {
                spec.commandLine().getErr().println(spec.commandLine().getColorScheme()
                        .errorText(file + ": " + e.getMessage()));

                return true;
            }
        }).toList().contains(true)) {
            return spec.exitCodeOnExecutionException();
        }

        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main())
                .setExecutionExceptionHandler(new PrintExceptionMessageHandler()).execute(args));
    }

    static class ManifestVersionProvider implements IVersionProvider {
        @Override
        public String[] getVersion() {
            var version = getClass().getPackage().getImplementationVersion();

            return new String[] {"${ROOT-COMMAND-NAME} " + version};
        }
    }

    static class PrintExceptionMessageHandler implements IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(Exception ex, CommandLine commandLine,
                ParseResult fullParseResult) {
            commandLine.getErr().println(commandLine.getColorScheme().errorText(ex.getMessage()));

            return commandLine.getCommandSpec().exitCodeOnExecutionException();
        }
    }
}
