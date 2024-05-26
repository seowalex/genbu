package genbu.writer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import org.eclipse.rdf4j.common.io.CharSink;
import org.eclipse.rdf4j.common.io.IndentingWriter;
import org.eclipse.rdf4j.common.net.ParsedIRI;
import org.eclipse.rdf4j.common.text.StringUtil;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ModelFactory;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil;
import org.eclipse.rdf4j.model.impl.LinkedHashModelFactory;
import org.eclipse.rdf4j.model.impl.SimpleIRI;
import org.eclipse.rdf4j.model.util.Literals;
import org.eclipse.rdf4j.model.util.ModelException;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.util.RDFCollections;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RioSetting;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFWriter;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import org.eclipse.rdf4j.rio.turtle.TurtleUtil;
import org.eclipse.rdf4j.rio.turtle.TurtleWriterSettings;

public class TurtleWriter extends AbstractRDFWriter implements CharSink {
    private static final int LINE_WRAP = 80;

    private static final long DEFAULT_BUFFER_SIZE = 1000l;

    private static final IRI FIRST = new SimpleIRI(RDF.FIRST.stringValue()) {
        private static final long serialVersionUID = -7951518099940758898L;
    };

    private static final IRI REST = new SimpleIRI(RDF.REST.stringValue()) {
        private static final long serialVersionUID = -7951518099940758898L;
    };

    private long bufferSize = DEFAULT_BUFFER_SIZE;
    protected Model bufferedStatements;
    private final Object bufferLock = new Object();

    protected ParsedIRI baseIRI;
    protected IndentingWriter writer;

    protected boolean statementClosed = true;
    protected Resource lastWrittenSubject;
    protected IRI lastWrittenPredicate;

    private final Deque<Resource> stack = new ArrayDeque<>();
    private final Deque<IRI> path = new ArrayDeque<>();

    private Boolean xsdStringToPlainLiteral;
    private Boolean prettyPrint;
    private boolean inlineBNodes;
    private Boolean abbreviateNumbers;

    private ModelFactory modelFactory = new LinkedHashModelFactory();

    private Optional<PrefixAlignment> prefixAlignment = Optional.empty();

    public TurtleWriter(OutputStream out) {
        this(out, null);
    }

    public TurtleWriter(OutputStream out, ParsedIRI baseIRI) {
        this.baseIRI = baseIRI;
        this.writer = new IndentingWriter(
                new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
    }

    public TurtleWriter(Writer writer) {
        this(writer, null);
    }

    public TurtleWriter(Writer writer, ParsedIRI baseIRI) {
        this.baseIRI = baseIRI;
        this.writer = new IndentingWriter(writer);
    }

    @Override
    public Writer getWriter() {
        return writer;
    }

    @Override
    public RDFFormat getRDFFormat() {
        return RDFFormat.TURTLE;
    }

    @Override
    public Collection<RioSetting<?>> getSupportedSettings() {
        final Collection<RioSetting<?>> settings = new HashSet<>(super.getSupportedSettings());
        settings.add(BasicWriterSettings.BASE_DIRECTIVE);
        settings.add(BasicWriterSettings.XSD_STRING_TO_PLAIN_LITERAL);
        settings.add(BasicWriterSettings.PRETTY_PRINT);
        settings.add(BasicWriterSettings.INLINE_BLANK_NODES);
        settings.add(TurtleWriterSettings.ABBREVIATE_NUMBERS);
        return settings;
    }

    public void setIndentationStyle(IndentationStyle indentationStyle) {
        writer.setIndentationString(indentationStyle.toString());
    }

    public void setPrefixAlignment(Optional<PrefixAlignment> prefixAlignment) {
        this.prefixAlignment = prefixAlignment;
    }

    @Override
    public void startRDF() throws RDFHandlerException {
        super.startRDF();

        try {
            xsdStringToPlainLiteral =
                    getWriterConfig().get(BasicWriterSettings.XSD_STRING_TO_PLAIN_LITERAL);
            prettyPrint = getWriterConfig().get(BasicWriterSettings.PRETTY_PRINT);
            inlineBNodes = getWriterConfig().get(BasicWriterSettings.INLINE_BLANK_NODES);
            abbreviateNumbers = getWriterConfig().get(TurtleWriterSettings.ABBREVIATE_NUMBERS);

            if (isBuffering()) {
                this.bufferedStatements = getModelFactory().createEmptyModel();
                this.bufferSize = inlineBNodes ? Long.MAX_VALUE : DEFAULT_BUFFER_SIZE;
            }

            if (prettyPrint) {
                writer.setIndentationString(IndentationStyle.SPACE(4).toString());
            } else {
                writer.setIndentationString("");
            }
            if (baseIRI != null && getWriterConfig().get(BasicWriterSettings.BASE_DIRECTIVE)) {
                writeBase(baseIRI.toString());
            }

            for (Map.Entry<String, String> entry : namespaceTable.entrySet()) {
                String name = entry.getKey();
                String prefix = entry.getValue();

                writeNamespace(prefix, name);
            }

            if (!namespaceTable.isEmpty()) {
                writer.writeEOL();
            }
        } catch (IOException e) {
            throw new RDFHandlerException(e);
        }
    }

    @Override
    public void endRDF() throws RDFHandlerException {
        checkWritingStarted();
        synchronized (bufferLock) {
            processBuffer();
        }
        try {
            closePreviousStatement();
            writer.flush();
        } catch (IOException e) {
            throw new RDFHandlerException(e);
        }
    }

    @Override
    public void handleNamespace(String prefix, String name) throws RDFHandlerException {
        checkWritingStarted();
        try {
            if (!namespaceTable.containsKey(name)) {

                boolean isLegalPrefix = prefix.length() == 0 || TurtleUtil.isPN_PREFIX(prefix);

                if (!isLegalPrefix || namespaceTable.containsValue(prefix)) {

                    if (prefix.length() == 0 || !isLegalPrefix) {
                        prefix = "ns";
                    }

                    int number = 1;

                    while (namespaceTable.containsValue(prefix + number)) {
                        number++;
                    }

                    prefix += number;
                }

                namespaceTable.put(name, prefix);

                closePreviousStatement();

                writeNamespace(prefix, name);
            }
        } catch (IOException e) {
            throw new RDFHandlerException(e);
        }
    }

    public void setModelFactory(ModelFactory modelFactory) {
        this.modelFactory = Objects.requireNonNull(modelFactory);
    }

    protected ModelFactory getModelFactory() {
        return modelFactory;
    }

    @Override
    protected void consumeStatement(Statement st) throws RDFHandlerException {
        if (isBuffering()) {
            synchronized (bufferLock) {
                bufferedStatements.add(st);
                if (bufferedStatements.size() >= this.bufferSize) {
                    processBuffer();
                }
            }
        } else {
            handleStatementInternal(st, false, false, false);
        }
    }

    protected void handleStatementInternal(Statement st, boolean endRDFCalled,
            boolean canShortenSubjectBNode, boolean canShortenObjectBNode) {
        Resource subj = st.getSubject();
        IRI pred = st.getPredicate();
        Value obj = st.getObject();

        try {
            if (inlineBNodes) {
                if ((pred.equals(RDF.FIRST) || pred.equals(RDF.REST))
                        && isWellFormedCollection(subj)) {
                    handleList(st, canShortenObjectBNode);
                } else if (!subj.equals(lastWrittenSubject) && stack.contains(subj)) {
                    handleInlineNode(st, canShortenSubjectBNode, canShortenObjectBNode);
                } else {
                    writeStatement(subj, pred, obj, st.getContext(), canShortenSubjectBNode,
                            canShortenObjectBNode);
                }
            } else {
                writeStatement(subj, pred, obj, st.getContext(), canShortenSubjectBNode,
                        canShortenObjectBNode);
            }
        } catch (IOException e) {
            throw new RDFHandlerException(e);
        }
    }

    private boolean isWellFormedCollection(Resource subj) {
        try {
            final Model collection = RDFCollections.getCollection(bufferedStatements, subj,
                    getModelFactory().createEmptyModel());

            for (Resource s : Models.subjectBNodes(collection)) {
                boolean firstFound = false, restFound = false;
                for (Statement st : bufferedStatements.getStatements(s, null, null)) {
                    IRI pred = st.getPredicate();
                    if (pred.equals(RDF.FIRST)) {
                        if (!firstFound) {
                            firstFound = true;
                        } else {
                            return false;
                        }
                    } else if (pred.equals(RDF.REST)) {
                        if (!restFound) {
                            restFound = true;
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
            return true;
        } catch (ModelException e) {
            return false;
        }
    }

    protected void writeStatement(Resource subj, IRI pred, Value obj, Resource context,
            boolean canShortenSubjectBNode, boolean canShortenObjectBNode) throws IOException {
        closeHangingResource();
        if (subj.equals(lastWrittenSubject)) {
            if (pred.equals(lastWrittenPredicate)) {
                writer.write(",");
                wrapLine(prettyPrint);
            } else {
                writer.write(" ;");
                writer.writeEOL();

                writePredicate(pred);
                wrapLine(true);
                path.removeLast();
                path.addLast(pred);
                lastWrittenPredicate = pred;
            }
        } else {
            closePreviousStatement();
            stack.addLast(subj);

            if (prettyPrint) {
                writer.writeEOL();
            }
            writeResource(subj, canShortenSubjectBNode);
            wrapLine(true);
            writer.increaseIndentation();
            lastWrittenSubject = subj;

            writePredicate(pred);
            wrapLine(true);
            path.addLast(pred);
            lastWrittenPredicate = pred;

            statementClosed = false;
        }

        writeValue(obj, canShortenObjectBNode);
    }

    @Override
    public void handleComment(String comment) throws RDFHandlerException {
        checkWritingStarted();
        try {
            closePreviousStatement();

            if (comment.indexOf('\r') != -1 || comment.indexOf('\n') != -1) {
                StringTokenizer st = new StringTokenizer(comment, "\r\n");
                while (st.hasMoreTokens()) {
                    writeCommentLine(st.nextToken());
                }
            } else {
                writeCommentLine(comment);
            }
        } catch (IOException e) {
            throw new RDFHandlerException(e);
        }
    }

    protected void writeCommentLine(String line) throws IOException {
        writer.write("# ");
        writer.write(line);
        writer.writeEOL();
    }

    protected void writeBase(String baseURI) throws IOException {
        writer.write("@base <");
        StringUtil.simpleEscapeIRI(baseURI, writer, false);
        writer.write("> .");
        writer.writeEOL();
    }

    protected void writeNamespace(String prefix, String name) throws IOException {
        writer.write("@prefix ");

        if (prefixAlignment.orElse(null) instanceof PrefixAlignment prefixAlignment) {
            writer.write(String.format(
                    "%" + prefixAlignment + (prefixAlignment.width() + 1) + "s <", prefix + ":"));
        } else {
            writer.write(prefix);
            writer.write(": <");
        }

        StringUtil.simpleEscapeIRI(name, writer, false);
        writer.write("> .");
        writer.writeEOL();
    }

    protected void writePredicate(IRI predicate) throws IOException {
        if (predicate.equals(RDF.TYPE)) {
            writer.write("a");
        } else {
            writeURI(predicate);
        }
    }

    @Deprecated
    protected void writeValue(Value val) throws IOException {
        writeValue(val, false);
    }

    protected void writeValue(Value val, boolean canShorten) throws IOException {
        if (val instanceof BNode && canShorten && !val.equals(stack.peekLast())
                && !val.equals(lastWrittenSubject)) {
            stack.addLast((BNode) val);
        } else if (val instanceof Resource) {
            writeResource((Resource) val, canShorten);
        } else {
            writeLiteral((Literal) val);
        }
    }

    @Deprecated
    protected void writeResource(Resource res) throws IOException {
        writeResource(res, false);
    }

    protected void writeResource(Resource res, boolean canShorten) throws IOException {
        if (res instanceof IRI) {
            writeURI((IRI) res);
        } else if (res instanceof BNode) {
            writeBNode((BNode) res, canShorten);
        } else {
            writeTriple((Triple) res, canShorten);
        }
    }

    protected void writeURI(IRI uri) throws IOException {
        String prefix = null;
        if (TurtleUtil.isValidPrefixedName(uri.getLocalName())) {
            prefix = namespaceTable.get(uri.getNamespace());
            if (prefix != null) {
                writer.write(prefix);
                writer.write(":");
                writer.write(uri.getLocalName());
                return;
            }
        }

        String uriString = uri.toString();
        int splitIdx = TurtleUtil.findURISplitIndex(uriString);
        if (splitIdx > 0) {
            String namespace = uriString.substring(0, splitIdx);
            prefix = namespaceTable.get(namespace);
        }

        if (prefix != null) {
            writer.write(prefix);
            writer.write(":");
            writer.write(uriString.substring(splitIdx));
        } else if (baseIRI != null) {
            writer.write("<");
            StringUtil.simpleEscapeIRI(baseIRI.relativize(uriString), writer, false);
            writer.write(">");
        } else {
            writer.write("<");
            StringUtil.simpleEscapeIRI(uriString, writer, false);
            writer.write(">");
        }
    }

    @Deprecated
    protected void writeBNode(BNode bNode) throws IOException {
        writeBNode(bNode, false);
    }

    protected void writeBNode(BNode bNode, boolean canShorten) throws IOException {
        if (canShorten) {
            writer.write("[]");
            return;
        }

        writer.write("_:");
        String id = bNode.getID();

        if (id.isEmpty()) {
            if (this.getWriterConfig().get(BasicParserSettings.PRESERVE_BNODE_IDS)) {
                throw new IOException(
                        "Cannot consistently write blank nodes with empty internal identifiers");
            }
            writer.write("genid-hash-");
            writer.write(Integer.toHexString(System.identityHashCode(bNode)));
        } else {
            if (!TurtleUtil.isNameStartChar(id.charAt(0))) {
                writer.write("genid-start-");
                writer.write(Integer.toHexString(id.charAt(0)));
            } else {
                writer.write(id.charAt(0));
            }
            for (int i = 1; i < id.length() - 1; i++) {
                if (TurtleUtil.isPN_CHARS(id.charAt(i))) {
                    writer.write(id.charAt(i));
                } else {
                    writer.write(Integer.toHexString(id.charAt(i)));
                }
            }
            if (id.length() > 1) {
                if (!TurtleUtil.isNameEndChar(id.charAt(id.length() - 1))) {
                    writer.write(Integer.toHexString(id.charAt(id.length() - 1)));
                } else {
                    writer.write(id.charAt(id.length() - 1));
                }
            }
        }
    }

    protected void writeTriple(Triple triple, boolean canShorten) throws IOException {
        throw new IOException(getRDFFormat().getName() + " does not support RDF-star triples");
    }

    protected void writeTripleRDFStar(Triple triple, boolean canShorten) throws IOException {
        writer.write("<<");
        writeResource(triple.getSubject());
        writer.write(" ");
        writeURI(triple.getPredicate());
        writer.write(" ");
        Value object = triple.getObject();
        if (object instanceof Literal) {
            writeLiteral((Literal) object);
        } else {
            writeResource((Resource) object, canShorten);
        }
        writer.write(">>");
    }

    protected void writeLiteral(Literal lit) throws IOException {
        String label = lit.getLabel();
        IRI datatype = lit.getDatatype();

        if (prettyPrint && abbreviateNumbers) {
            if (XSD.INTEGER.equals(datatype) || XSD.DECIMAL.equals(datatype)
                    || XSD.DOUBLE.equals(datatype) || XSD.BOOLEAN.equals(datatype)) {
                try {
                    String normalized = XMLDatatypeUtil.normalize(label, datatype);
                    if (!XMLDatatypeUtil.POSITIVE_INFINITY.equals(normalized)
                            && !XMLDatatypeUtil.NEGATIVE_INFINITY.equals(normalized)
                            && !XMLDatatypeUtil.NaN.equals(normalized)) {
                        writer.write(normalized);
                        return;
                    }
                } catch (IllegalArgumentException e) {
                }
            }
        }

        if (label.indexOf('\n') != -1 || label.indexOf('\r') != -1 || label.indexOf('\t') != -1) {
            writer.write("\"\"\"");
            writer.write(TurtleUtil.encodeLongString(label));
            writer.write("\"\"\"");
        } else {
            writer.write("\"");
            writer.write(TurtleUtil.encodeString(label));
            writer.write("\"");
        }

        if (Literals.isLanguageLiteral(lit)) {
            writer.write("@");
            writer.write(lit.getLanguage().get());
        } else if (!xsdStringToPlainLiteral || !XSD.STRING.equals(datatype)) {
            writer.write("^^");
            writeURI(datatype);
        }
    }

    protected void closePreviousStatement() throws IOException {
        closeNestedResources(null);
        if (!statementClosed) {
            writer.write(" .");
            writer.writeEOL();
            writer.decreaseIndentation();

            stack.pollLast();
            path.pollLast();
            assert stack.isEmpty();
            assert path.isEmpty();
            statementClosed = true;
            lastWrittenSubject = null;
            lastWrittenPredicate = null;
        }
    }

    private boolean isHanging() {
        return !stack.isEmpty() && lastWrittenSubject != null
                && !lastWrittenSubject.equals(stack.peekLast());
    }

    private void closeHangingResource() throws IOException {
        if (isHanging()) {
            Value val = stack.pollLast();
            if (val instanceof Resource) {
                writeResource((Resource) val, inlineBNodes);
            } else {
                writeLiteral((Literal) val);
            }
            assert lastWrittenSubject.equals(stack.peekLast());
        }
    }

    private void closeNestedResources(Resource subj) throws IOException {
        closeHangingResource();
        while (stack.size() > 1 && !stack.peekLast().equals(subj)) {
            if (prettyPrint) {
                writer.writeEOL();
            }
            writer.decreaseIndentation();
            writer.write("]");

            stack.pollLast();
            path.pollLast();
            lastWrittenSubject = stack.peekLast();
            lastWrittenPredicate = path.peekLast();
        }
    }

    private void handleInlineNode(Statement st, boolean inlineSubject, boolean inlineObject)
            throws IOException {
        Resource subj = st.getSubject();
        IRI pred = st.getPredicate();
        if (isHanging() && subj.equals(stack.peekLast())) {
            lastWrittenSubject = subj;
            writer.write("[");
            if (prettyPrint && !RDF.TYPE.equals(pred)) {
                writer.writeEOL();
            } else {
                wrapLine(prettyPrint);
            }
            writer.increaseIndentation();

            writePredicate(pred);
            wrapLine(true);
            path.addLast(pred);
            lastWrittenPredicate = pred;
            writeValue(st.getObject(), inlineObject);
        } else if (!subj.equals(lastWrittenSubject) && stack.contains(subj)) {
            closeNestedResources(subj);
            writeStatement(subj, pred, st.getObject(), st.getContext(), inlineSubject,
                    inlineObject);
        } else {
            assert false;
        }
    }

    private void handleList(Statement st, boolean canInlineObjectBNode) throws IOException {
        Resource subj = st.getSubject();
        boolean first = RDF.FIRST.equals(st.getPredicate());
        boolean rest = RDF.REST.equals(st.getPredicate()) && !RDF.NIL.equals(st.getObject());
        boolean nil = RDF.REST.equals(st.getPredicate()) && RDF.NIL.equals(st.getObject());

        if (first && REST != lastWrittenPredicate && isHanging()) {
            writer.write("(");
            writer.increaseIndentation();
            wrapLine(false);
            lastWrittenSubject = subj;
            path.addLast(FIRST);
            lastWrittenPredicate = FIRST;
            writeValue(st.getObject(), canInlineObjectBNode);
        } else if (first && REST == lastWrittenPredicate) {
            lastWrittenSubject = subj;
            path.addLast(FIRST);
            lastWrittenPredicate = FIRST;
            writeValue(st.getObject(), canInlineObjectBNode);
        } else {
            closeNestedResources(subj);
            if (rest && FIRST == lastWrittenPredicate) {
                wrapLine(true);
                path.removeLast();
                path.addLast(REST);
                lastWrittenPredicate = REST;
                writeValue(st.getObject(), inlineBNodes);
            } else if (nil && FIRST == lastWrittenPredicate) {
                writer.decreaseIndentation();
                writer.write(")");
                path.removeLast();
                path.addLast(REST);
                while (REST == path.peekLast()) {
                    stack.pollLast();
                    path.pollLast();
                    lastWrittenSubject = stack.peekLast();
                    lastWrittenPredicate = path.peekLast();
                }
            } else {
                writeStatement(subj, st.getPredicate(), st.getObject(), st.getContext(),
                        inlineBNodes, inlineBNodes);
            }
        }
    }

    private void wrapLine(boolean space) throws IOException {
        if (prettyPrint && writer.getCharactersSinceEOL() > LINE_WRAP) {
            writer.writeEOL();
        } else if (space) {
            writer.write(" ");
        }
    }

    private void processBuffer() throws RDFHandlerException {
        if (!isBuffering()) {
            return;
        }

        if (this.getRDFFormat().supportsContexts()) {
            for (Resource context : bufferedStatements.contexts()) {
                Model contextData = bufferedStatements.filter(null, null, null, context);
                Set<Resource> processedSubjects = new HashSet<>();
                Optional<Resource> nextSubject = nextSubject(contextData, processedSubjects);
                while (nextSubject.isPresent()) {
                    processSubject(contextData, nextSubject.get(), processedSubjects);
                    nextSubject = nextSubject(contextData, processedSubjects);
                }
            }
        } else {
            Set<Resource> processedSubjects = new HashSet<>();
            Optional<Resource> nextSubject = nextSubject(bufferedStatements, processedSubjects);
            while (nextSubject.isPresent()) {
                processSubject(bufferedStatements, nextSubject.get(), processedSubjects);
                nextSubject = nextSubject(bufferedStatements, processedSubjects);
            }
        }
        bufferedStatements.clear();
    }

    private Optional<Resource> nextSubject(Model contextData, Set<Resource> processedSubjects) {
        for (Resource subject : contextData.subjects()) {
            if (processedSubjects.contains(subject)) {
                continue;
            }
            if (subject.isBNode() && inlineBNodes) {
                Set<Resource> otherSubjects = contextData.filter(null, null, subject).subjects();
                if (otherSubjects.stream().anyMatch(s -> !processedSubjects.contains(s))) {
                    continue;
                }
            }
            return Optional.of(subject);
        }

        return contextData.subjects().stream()
                .filter(subject -> !processedSubjects.contains(subject)).findAny();
    }

    private void processSubject(Model contextData, Resource subject,
            Set<Resource> processedSubjects) {
        if (processedSubjects.contains(subject)) {
            return;
        }

        Set<IRI> processedPredicates = new HashSet<>();

        processPredicate(contextData, subject, RDF.TYPE, processedSubjects, processedPredicates);

        processPredicate(contextData, subject, RDF.FIRST, processedSubjects, processedPredicates);

        for (IRI predicate : contextData.filter(subject, null, null).predicates()) {
            if (!processedPredicates.contains(predicate)) {
                processPredicate(contextData, subject, predicate, processedSubjects,
                        processedPredicates);
            }
        }

        processedSubjects.add(subject);
    }

    private void processPredicate(Model contextData, Resource subject, IRI predicate,
            Set<Resource> processedSubjects, Set<IRI> processedPredicates) {
        for (Statement st : contextData.getStatements(subject, predicate, null)) {
            boolean canInlineObject = canInlineValue(contextData, st.getObject());
            handleStatementInternal(st, false, canInlineValue(contextData, st.getSubject()),
                    canInlineObject);

            if (canInlineObject && st.getObject() instanceof BNode) {
                processSubject(contextData, (BNode) st.getObject(), processedSubjects);
            }
        }
        processedPredicates.add(predicate);
    }

    private boolean canInlineValue(Model contextData, Value v) {
        if (!inlineBNodes) {
            return false;
        }
        if (v instanceof BNode) {
            return (contextData.filter(null, null, v).size() <= 1);
        }
        return true;
    }

    private boolean isBuffering() {
        return inlineBNodes || prettyPrint;
    }
}
