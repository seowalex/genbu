package genbu.parser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.io.input.BOMInputStream;
import org.eclipse.rdf4j.common.text.ASCIIUtil;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RioSetting;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFParser;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.eclipse.rdf4j.rio.turtle.TurtleParserSettings;
import org.eclipse.rdf4j.rio.turtle.TurtleUtil;

public class TurtleParser extends AbstractRDFParser {
	private PushbackReader reader;

	protected Resource subject;

	protected IRI predicate;

	protected Value object;

	private int lineNumber = 1;

	private final StringBuilder parsingBuilder = new StringBuilder();

	private Statement previousStatement;

	public TurtleParser() {
		super();
	}

	public TurtleParser(ValueFactory valueFactory) {
		super(valueFactory);
	}

	@Override
	public RDFFormat getRDFFormat() {
		return RDFFormat.TURTLE;
	}

	@Override
	public Collection<RioSetting<?>> getSupportedSettings() {
		Set<RioSetting<?>> result = new HashSet<>(super.getSupportedSettings());
		result.add(TurtleParserSettings.CASE_INSENSITIVE_DIRECTIVES);

		return result;
	}

	@Override
	public synchronized void parse(InputStream in, String baseURI)
			throws IOException, RDFParseException, RDFHandlerException {
		if (in == null) {
			throw new IllegalArgumentException("Input stream must not be 'null'");
		}

		try {
			parse(new InputStreamReader(new BOMInputStream(in, false), StandardCharsets.UTF_8),
					baseURI);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public synchronized void parse(Reader reader, String baseURI)
			throws IOException, RDFParseException, RDFHandlerException {
		clear();

		try {
			if (reader == null) {
				throw new IllegalArgumentException("Reader must not be 'null'");
			}

			if (rdfHandler != null) {
				rdfHandler.startRDF();
			}

			lineNumber = 1;

			this.reader = new PushbackReader(reader, 10);

			if (baseURI != null) {
				setBaseURI(baseURI);
			}

			reportLocation();

			int c = skipWSC();

			while (c != -1) {
				parseStatement();
				c = skipWSC();
			}
		} finally {
			clear();
		}

		if (rdfHandler != null) {
			rdfHandler.endRDF();
		}
	}

	protected void parseStatement() throws IOException, RDFParseException, RDFHandlerException {
		StringBuilder sb = new StringBuilder(8);

		int codePoint;

		do {
			codePoint = readCodePoint();

			if (codePoint == -1 || TurtleUtil.isWhitespace(codePoint)) {
				unread(codePoint);

				break;
			}

			appendCodepoint(sb, codePoint);
		} while (sb.length() < 8);

		String directive = sb.toString();

		if (directive.startsWith("@") || "prefix".equalsIgnoreCase(directive)
				|| "base".equalsIgnoreCase(directive)) {
			parseDirective(directive);
			skipWSC();

			if (directive.startsWith("@")) {
				verifyCharacterOrFail(readCodePoint(), ".");
			}
		} else {
			unread(directive);
			parseTriples();
			skipWSC();
			verifyCharacterOrFail(readCodePoint(), ".");
		}
	}

	protected void parseDirective(String directive)
			throws IOException, RDFParseException, RDFHandlerException {
		if (directive.length() >= 7 && "@prefix".equals(directive.substring(0, 7))) {
			if (directive.length() > 7) {
				unread(directive.substring(7));
			}

			parsePrefixID();
		} else if (directive.length() >= 5 && "@base".equals(directive.substring(0, 5))) {
			if (directive.length() > 5) {
				unread(directive.substring(5));
			}

			parseBase();
		} else if (directive.length() >= 6
				&& "prefix".equalsIgnoreCase(directive.substring(0, 6))) {
			if (directive.length() > 6) {
				unread(directive.substring(6));
			}

			parsePrefixID();
		} else if ((directive.length() >= 4
				&& "base".equalsIgnoreCase(directive.substring(0, 4)))) {
			if (directive.length() > 4) {
				unread(directive.substring(4));
			}

			parseBase();
		} else if (directive.length() >= 7
				&& "@prefix".equalsIgnoreCase(directive.substring(0, 7))) {
			if (!this.getParserConfig().get(TurtleParserSettings.CASE_INSENSITIVE_DIRECTIVES)) {
				reportFatalError(
						"Cannot strictly support case-insensitive @prefix directive in compliance mode.");
			}

			if (directive.length() > 7) {
				unread(directive.substring(7));
			}

			parsePrefixID();
		} else if (directive.length() >= 5 && "@base".equalsIgnoreCase(directive.substring(0, 5))) {
			if (!this.getParserConfig().get(TurtleParserSettings.CASE_INSENSITIVE_DIRECTIVES)) {
				reportFatalError(
						"Cannot strictly support case-insensitive @base directive in compliance mode.");
			}

			if (directive.length() > 5) {
				unread(directive.substring(5));
			}

			parseBase();
		} else if (directive.length() == 0) {
			reportFatalError("Directive name is missing, expected @prefix or @base");
		} else {
			reportFatalError("Unknown directive \"" + directive + "\"");
		}
	}

	protected void parsePrefixID() throws IOException, RDFParseException, RDFHandlerException {
		skipWSC();

		StringBuilder prefixID = new StringBuilder(8);

		while (true) {
			int c = readCodePoint();

			if (c == ':') {
				unread(c);

				break;
			} else if (TurtleUtil.isWhitespace(c)) {
				break;
			} else if (c == -1) {
				throwEOFException();
			}

			appendCodepoint(prefixID, c);
		}

		skipWSC();

		verifyCharacterOrFail(readCodePoint(), ":");

		skipWSC();

		String namespaceStr = parseURI().toString();

		String prefixStr = prefixID.toString();

		setNamespace(prefixStr, namespaceStr);

		if (rdfHandler != null) {
			rdfHandler.handleNamespace(prefixStr, namespaceStr);
		}
	}

	protected void parseBase() throws IOException, RDFParseException, RDFHandlerException {
		skipWSC();

		IRI baseURI = parseURI();

		setBaseURI(baseURI.toString());
	}

	protected void parseTriples() throws IOException, RDFParseException, RDFHandlerException {
		int c = peekCodePoint();

		if (c == '[') {
			c = readCodePoint();
			skipWSC();
			c = peekCodePoint();

			if (c == ']') {
				c = readCodePoint();
				subject = createNode();
				skipWSC();
				parsePredicateObjectList();
			} else {
				unread('[');
				subject = parseImplicitBlank();
			}

			skipWSC();
			c = peekCodePoint();

			if (c != '.') {
				parsePredicateObjectList();
			}
		} else {
			parseSubject();
			skipWSC();
			parsePredicateObjectList();
		}

		subject = null;
		predicate = null;
		object = null;
	}

	protected void parsePredicateObjectList()
			throws IOException, RDFParseException, RDFHandlerException {
		predicate = parsePredicate();

		skipWSC();

		parseObjectList();

		while (skipWSC() == ';') {
			readCodePoint();

			int c = skipWSC();

			if (c == '.' || c == ']' || c == '}') {
				break;
			} else if (c == ';') {
				continue;
			}

			predicate = parsePredicate();

			skipWSC();

			parseObjectList();
		}
	}

	protected void parseObjectList() throws IOException, RDFParseException, RDFHandlerException {
		parseObject();

		if (skipWSC() == '{') {
			parseAnnotation();
		}

		while (skipWSC() == ',') {
			readCodePoint();
			skipWSC();
			parseObject();

			if (skipWSC() == '{') {
				parseAnnotation();
			}
		}
	}

	protected void parseSubject() throws IOException, RDFParseException, RDFHandlerException {
		int c = peekCodePoint();

		if (c == '(') {
			subject = parseCollection();
		} else if (c == '[') {
			subject = parseImplicitBlank();
		} else {
			Value value = parseValue();

			if (value instanceof Resource) {
				subject = (Resource) value;
			} else if (value != null) {
				reportFatalError("Illegal subject value: " + value);
			}
		}
	}

	protected IRI parsePredicate() throws IOException, RDFParseException, RDFHandlerException {
		int c1 = readCodePoint();

		if (c1 == 'a') {
			int c2 = readCodePoint();

			if (TurtleUtil.isWhitespace(c2)) {
				return RDF.TYPE;
			}

			unread(c2);
		}

		unread(c1);

		Value predicate = parseValue();

		if (predicate instanceof IRI) {
			return (IRI) predicate;
		} else {
			reportFatalError("Illegal predicate value: " + predicate);

			return null;
		}
	}

	protected void parseObject() throws IOException, RDFParseException, RDFHandlerException {
		int c = peekCodePoint();

		switch (c) {
			case '(':
				object = parseCollection();

				break;
			case '[':
				object = parseImplicitBlank();

				break;
			default:
				object = parseValue();
				reportStatement(subject, predicate, object);

				break;
		}
	}

	protected Resource parseCollection()
			throws IOException, RDFParseException, RDFHandlerException {
		verifyCharacterOrFail(readCodePoint(), "(");

		int c = skipWSC();

		if (c == ')') {
			readCodePoint();

			if (subject != null) {
				reportStatement(subject, predicate, RDF.NIL);
			}

			return RDF.NIL;
		} else {
			Resource listRoot = createNode();

			if (subject != null) {
				reportStatement(subject, predicate, listRoot);
			}

			Resource oldSubject = subject;
			IRI oldPredicate = predicate;

			subject = listRoot;
			predicate = RDF.FIRST;

			parseObject();

			Resource bNode = listRoot;

			while (skipWSC() != ')') {
				Resource newNode = createNode();
				reportStatement(bNode, RDF.REST, newNode);

				subject = bNode = newNode;

				parseObject();
			}

			readCodePoint();

			reportStatement(bNode, RDF.REST, RDF.NIL);

			subject = oldSubject;
			predicate = oldPredicate;

			return listRoot;
		}
	}

	protected Resource parseImplicitBlank()
			throws IOException, RDFParseException, RDFHandlerException {
		verifyCharacterOrFail(readCodePoint(), "[");

		Resource bNode = createNode();

		if (subject != null) {
			reportStatement(subject, predicate, bNode);
		}

		skipWSC();
		int c = readCodePoint();

		if (c != ']') {
			unread(c);

			Resource oldSubject = subject;
			IRI oldPredicate = predicate;

			subject = bNode;

			skipWSC();

			parsePredicateObjectList();

			skipWSC();

			verifyCharacterOrFail(readCodePoint(), "]");

			subject = oldSubject;
			predicate = oldPredicate;
		}

		return bNode;
	}

	protected Value parseValue() throws IOException, RDFParseException, RDFHandlerException {
		int c = peekCodePoint();

		if (c == '<') {
			return parseURI();
		} else if (c == ':' || TurtleUtil.isPrefixStartChar(c)) {
			return parseQNameOrBoolean();
		} else if (c == '_') {
			return parseNodeID();
		} else if (c == '"' || c == '\'') {
			return parseQuotedLiteral();
		} else if (ASCIIUtil.isNumber(c) || c == '.' || c == '+' || c == '-') {
			return parseNumber();
		} else if (c == -1) {
			throwEOFException();

			return null;
		} else {
			reportFatalError(
					"Expected an RDF value here, found '" + new String(Character.toChars(c)) + "'");

			return null;
		}
	}

	protected Literal parseQuotedLiteral()
			throws IOException, RDFParseException, RDFHandlerException {
		String label = parseQuotedString();

		int c = peekCodePoint();

		if (c == '@') {
			readCodePoint();

			StringBuilder lang = getBuilder();

			c = readCodePoint();

			if (c == -1) {
				throwEOFException();
			}

			boolean verifyLanguageTag =
					getParserConfig().get(BasicParserSettings.VERIFY_LANGUAGE_TAGS);

			if (verifyLanguageTag && !TurtleUtil.isLanguageStartChar(c)) {
				reportError("Expected a letter, found '" + new String(Character.toChars(c)) + "'",
						BasicParserSettings.VERIFY_LANGUAGE_TAGS);
			}

			appendCodepoint(lang, c);

			c = readCodePoint();

			while (!TurtleUtil.isWhitespace(c)) {
				if (c == '.' || c == ';' || c == ',' || c == ')' || c == ']' || c == '>'
						|| c == -1) {
					break;
				}

				if (verifyLanguageTag && !TurtleUtil.isLanguageChar(c)) {
					reportError(
							"Illegal language tag char: '" + new String(Character.toChars(c)) + "'",
							BasicParserSettings.VERIFY_LANGUAGE_TAGS);
				}

				appendCodepoint(lang, c);
				c = readCodePoint();
			}

			unread(c);

			return createLiteral(label, lang.toString(), null, getLineNumber(), -1);
		} else if (c == '^') {
			readCodePoint();

			verifyCharacterOrFail(readCodePoint(), "^");

			skipWSC();

			Value datatype = parseValue();

			if (datatype == null) {
				reportError("Invalid datatype IRI for literal '" + label + "'",
						BasicParserSettings.VERIFY_URI_SYNTAX);

				return null;
			} else if (!(datatype instanceof IRI)) {
				reportFatalError("Illegal datatype value: " + datatype);
			}

			return createLiteral(label, null, (IRI) datatype, getLineNumber(), -1);
		} else {
			return createLiteral(label, null, null, getLineNumber(), -1);
		}
	}

	protected String parseQuotedString() throws IOException, RDFParseException {
		String result;

		int c1 = readCodePoint();

		verifyCharacterOrFail(c1, "\"\'");

		int c2 = readCodePoint();
		int c3 = readCodePoint();

		if ((c1 == '"' && c2 == '"' && c3 == '"') || (c1 == '\'' && c2 == '\'' && c3 == '\'')) {
			result = parseLongString(c2);
		} else {
			unread(c3);
			unread(c2);

			result = parseString(c1);
		}

		try {
			result = TurtleUtil.decodeString(result);
		} catch (IllegalArgumentException e) {
			reportError(e.getMessage(), BasicParserSettings.VERIFY_DATATYPE_VALUES);
		}

		return result;
	}

	protected String parseString(int closingCharacter) throws IOException, RDFParseException {
		StringBuilder sb = getBuilder();

		while (true) {
			int c = readCodePoint();

			if (c == closingCharacter) {
				break;
			} else if (c == -1) {
				throwEOFException();
			}

			if (c == '\r' || c == '\n') {
				reportFatalError("Illegal carriage return or new line in literal");
			}

			if (c == '\r' || c == '\n') {
				reportFatalError("Illegal carriage return or new line in literal");
			}

			appendCodepoint(sb, c);

			if (c == '\\') {
				c = readCodePoint();

				if (c == -1) {
					throwEOFException();
				}

				appendCodepoint(sb, c);
			}
		}

		return sb.toString();
	}

	protected String parseLongString(int closingCharacter) throws IOException, RDFParseException {
		StringBuilder sb = getBuilder();

		int doubleQuoteCount = 0;
		int c;

		while (doubleQuoteCount < 3) {
			c = readCodePoint();

			if (c == -1) {
				throwEOFException();
			} else if (c == closingCharacter) {
				doubleQuoteCount++;
			} else {
				doubleQuoteCount = 0;
			}

			appendCodepoint(sb, c);

			if (c == '\n') {
				lineNumber++;
				reportLocation();
			}

			if (c == '\\') {
				c = readCodePoint();

				if (c == -1) {
					throwEOFException();
				}

				appendCodepoint(sb, c);
			}
		}

		return sb.substring(0, sb.length() - 3);
	}

	protected Literal parseNumber() throws IOException, RDFParseException {
		StringBuilder value = getBuilder();
		IRI datatype = XSD.INTEGER;

		int c = readCodePoint();

		if (c == '+' || c == '-') {
			appendCodepoint(value, c);
			c = readCodePoint();
		}

		while (ASCIIUtil.isNumber(c)) {
			appendCodepoint(value, c);
			c = readCodePoint();
		}

		if (c == '.' || c == 'e' || c == 'E') {
			if (c == '.') {
				if (TurtleUtil.isWhitespace(peekCodePoint())) {
				} else {
					appendCodepoint(value, c);

					c = readCodePoint();

					while (ASCIIUtil.isNumber(c)) {
						appendCodepoint(value, c);
						c = readCodePoint();
					}

					if (value.length() == 1) {
						reportFatalError("Object for statement missing");
					}

					datatype = XSD.DECIMAL;
				}
			} else {
				if (value.length() == 0) {
					reportFatalError("Object for statement missing");
				}
			}

			if (c == 'e' || c == 'E') {
				datatype = XSD.DOUBLE;
				appendCodepoint(value, c);

				c = readCodePoint();

				if (c == '+' || c == '-') {
					appendCodepoint(value, c);
					c = readCodePoint();
				}

				if (!ASCIIUtil.isNumber(c)) {
					reportError("Exponent value missing",
							BasicParserSettings.VERIFY_DATATYPE_VALUES);
				}

				appendCodepoint(value, c);

				c = readCodePoint();

				while (ASCIIUtil.isNumber(c)) {
					appendCodepoint(value, c);
					c = readCodePoint();
				}
			}
		}

		unread(c);

		return createLiteral(value.toString(), null, datatype, getLineNumber(), -1);
	}

	protected IRI parseURI() throws IOException, RDFParseException {
		StringBuilder uriBuf = getBuilder();

		int c = readCodePoint();
		verifyCharacterOrFail(c, "<");

		boolean uriIsIllegal = false;

		while (true) {
			c = readCodePoint();

			if (c == '>') {
				break;
			} else if (c == -1) {
				throwEOFException();
			}

			if (c == ' ') {
				reportError("IRI included an unencoded space: '" + c + "'",
						BasicParserSettings.VERIFY_URI_SYNTAX);
				uriIsIllegal = true;
			}

			appendCodepoint(uriBuf, c);

			if (c == '\\') {
				c = readCodePoint();

				if (c == -1) {
					throwEOFException();
				}

				if (c != 'u' && c != 'U') {
					reportError("IRI includes string escapes: '\\" + c + "'",
							BasicParserSettings.VERIFY_URI_SYNTAX);
					uriIsIllegal = true;
				}

				appendCodepoint(uriBuf, c);
			}
		}

		if (c == '.') {
			reportError("IRI must not end in a '.'", BasicParserSettings.VERIFY_URI_SYNTAX);
			uriIsIllegal = true;
		}

		if (!(uriIsIllegal && getParserConfig().get(BasicParserSettings.VERIFY_URI_SYNTAX))) {
			String uri = uriBuf.toString();

			try {
				uri = TurtleUtil.decodeString(uri);
			} catch (IllegalArgumentException e) {
				reportError(e.getMessage(), BasicParserSettings.VERIFY_DATATYPE_VALUES);
			}

			return super.resolveURI(uri);
		}

		return null;
	}

	protected Value parseQNameOrBoolean() throws IOException, RDFParseException {
		int c = readCodePoint();

		if (c == -1) {
			throwEOFException();
		}

		if (c != ':' && !TurtleUtil.isPrefixStartChar(c)) {
			reportError(
					"Expected a ':' or a letter, found '" + new String(Character.toChars(c)) + "'",
					BasicParserSettings.VERIFY_RELATIVE_URIS);
		}

		String namespace;

		if (c == ':') {
			namespace = getNamespace("");
		} else {
			StringBuilder prefix = new StringBuilder(8);
			appendCodepoint(prefix, c);

			int previousChar = c;
			c = readCodePoint();

			while (TurtleUtil.isPrefixChar(c)) {
				appendCodepoint(prefix, c);
				previousChar = c;
				c = readCodePoint();
			}

			while (previousChar == '.' && prefix.length() > 0) {
				unread(c);
				c = previousChar;
				prefix.setLength(prefix.length() - 1);
				previousChar = prefix.codePointAt(prefix.codePointCount(0, prefix.length()) - 1);
			}

			if (c != ':') {
				String value = prefix.toString();

				if ("true".equals(value)) {
					unread(c);

					return createLiteral("true", null, XSD.BOOLEAN, getLineNumber(), -1);
				} else if ("false".equals(value)) {
					unread(c);

					return createLiteral("false", null, XSD.BOOLEAN, getLineNumber(), -1);
				}
			}

			verifyCharacterOrFail(c, ":");

			namespace = getNamespace(prefix.toString());
		}

		StringBuilder localName = new StringBuilder(16);
		c = readCodePoint();

		if (TurtleUtil.isNameStartChar(c)) {
			if (c == '\\') {
				localName.append(readLocalEscapedChar());
			} else {
				appendCodepoint(localName, c);
			}

			int previousChar = c;
			c = readCodePoint();

			while (TurtleUtil.isNameChar(c)) {
				if (c == '\\') {
					localName.append(readLocalEscapedChar());
				} else {
					appendCodepoint(localName, c);
				}

				previousChar = c;
				c = readCodePoint();
			}

			unread(c);

			if (previousChar == '.') {
				unread(previousChar);
				localName.deleteCharAt(localName.length() - 1);
			}
		} else {
			unread(c);
		}

		String localNameString = localName.toString();

		for (int i = 0; i < localNameString.length(); i++) {
			if (localNameString.charAt(i) == '%') {
				if (i > localNameString.length() - 3
						|| !ASCIIUtil.isHex(localNameString.charAt(i + 1))
						|| !ASCIIUtil.isHex(localNameString.charAt(i + 2))) {
					reportFatalError(
							"Found incomplete percent-encoded sequence: " + localNameString);
				}
			}
		}

		return createURI(namespace + localNameString);
	}

	private char readLocalEscapedChar() throws RDFParseException, IOException {
		int c = readCodePoint();

		if (TurtleUtil.isLocalEscapedChar(c)) {
			return (char) c;
		} else {
			throw new RDFParseException("found '" + new String(Character.toChars(c))
					+ "', expected one of: " + Arrays.toString(TurtleUtil.LOCAL_ESCAPED_CHARS));
		}
	}

	protected Resource parseNodeID() throws IOException, RDFParseException {
		verifyCharacterOrFail(readCodePoint(), "_");
		verifyCharacterOrFail(readCodePoint(), ":");

		int c = readCodePoint();

		if (c == -1) {
			throwEOFException();
		} else if (!TurtleUtil.isBLANK_NODE_LABEL_StartChar(c)) {
			reportError("Expected a letter, found '" + (char) c + "'",
					BasicParserSettings.PRESERVE_BNODE_IDS);
		}

		StringBuilder name = getBuilder();
		appendCodepoint(name, c);

		c = readCodePoint();

		if (!TurtleUtil.isBLANK_NODE_LABEL_Char(c)) {
			unread(c);
		}

		while (TurtleUtil.isBLANK_NODE_LABEL_Char(c)) {
			int previous = c;
			c = readCodePoint();

			if (previous == '.'
					&& (c == -1 || TurtleUtil.isWhitespace(c) || c == '<' || c == '_')) {
				unread(c);
				unread(previous);

				break;
			}

			appendCodepoint(name, previous);

			if (!TurtleUtil.isBLANK_NODE_LABEL_Char(c)) {
				unread(c);
			}
		}

		return createNode(name.toString());
	}

	protected void reportStatement(Resource subj, IRI pred, Value obj)
			throws RDFParseException, RDFHandlerException {
		if (subj != null && pred != null && obj != null) {
			previousStatement = createStatement(subj, pred, obj);

			if (rdfHandler != null) {
				rdfHandler.handleStatement(previousStatement);
			}
		}
	}

	protected void verifyCharacterOrFail(int codePoint, String expected) throws RDFParseException {
		if (codePoint == -1) {
			throwEOFException();
		}

		final String supplied = new String(Character.toChars(codePoint));

		if (expected.indexOf(supplied) == -1) {
			StringBuilder msg = new StringBuilder(32);
			msg.append("Expected ");

			for (int i = 0; i < expected.length(); i++) {
				if (i > 0) {
					msg.append(" or ");
				}

				msg.append('\'');
				msg.append(expected.charAt(i));
				msg.append('\'');
			}

			msg.append(", found '");
			msg.append(supplied);
			msg.append("'");

			reportFatalError(msg.toString());
		}
	}

	protected int skipWSC() throws IOException, RDFHandlerException {
		int c = readCodePoint();

		while (TurtleUtil.isWhitespace(c) || c == '#') {
			if (c == '#') {
				processComment();
			} else if (c == '\n') {
				lineNumber++;
				reportLocation();
			}

			c = readCodePoint();
		}

		unread(c);

		return c;
	}

	protected void processComment() throws IOException, RDFHandlerException {
		StringBuilder comment = getBuilder();
		int c = readCodePoint();

		while (c != -1 && c != 0xD && c != 0xA) {
			appendCodepoint(comment, c);
			c = readCodePoint();
		}

		if (c == 0xA) {
			lineNumber++;
		}

		if (c == 0xD) {
			c = readCodePoint();
			lineNumber++;

			if (c != 0xA) {
				unread(c);
			}
		}

		if (rdfHandler != null) {
			rdfHandler.handleComment(comment.toString());
		}

		reportLocation();
	}

	protected int readCodePoint() throws IOException {
		int next = reader.read();

		if (Character.isHighSurrogate((char) next)) {
			next = Character.toCodePoint((char) next, (char) reader.read());
		}

		return next;
	}

	protected void unread(int codePoint) throws IOException {
		if (codePoint != -1) {
			if (Character.isSupplementaryCodePoint(codePoint)) {
				final char[] surrogatePair = Character.toChars(codePoint);
				reader.unread(surrogatePair);
			} else {
				reader.unread(codePoint);
			}
		}
	}

	protected void unread(String string) throws IOException {
		int i = string.length();

		while (i > 0) {
			final int codePoint = string.codePointBefore(i);

			if (Character.isSupplementaryCodePoint(codePoint)) {
				final char[] surrogatePair = Character.toChars(codePoint);
				reader.unread(surrogatePair);
				i -= surrogatePair.length;
			} else {
				reader.unread(codePoint);
				i--;
			}
		}
	}

	protected int peekCodePoint() throws IOException {
		int result = readCodePoint();
		unread(result);

		return result;
	}

	protected void reportLocation() {
		reportLocation(getLineNumber(), -1);
	}

	@Override
	protected void reportWarning(String msg) {
		reportWarning(msg, getLineNumber(), -1);
	}

	@Override
	protected void reportError(String msg, RioSetting<Boolean> setting) throws RDFParseException {
		reportError(msg, getLineNumber(), -1, setting);
	}

	@Override
	protected void reportFatalError(String msg) throws RDFParseException {
		reportFatalError(msg, getLineNumber(), -1);
	}

	@Override
	protected void reportFatalError(Exception e) throws RDFParseException {
		reportFatalError(e, getLineNumber(), -1);
	}

	protected void throwEOFException() throws RDFParseException {
		throw new RDFParseException("Unexpected end of file");
	}

	protected int getLineNumber() {
		return lineNumber;
	}

	private StringBuilder getBuilder() {
		parsingBuilder.setLength(0);

		return parsingBuilder;
	}

	private static void appendCodepoint(StringBuilder dst, int codePoint) {
		if (Character.isBmpCodePoint(codePoint)) {
			dst.append((char) codePoint);
		} else if (Character.isValidCodePoint(codePoint)) {
			dst.append(Character.highSurrogate(codePoint));
			dst.append(Character.lowSurrogate(codePoint));
		} else {
			throw new IllegalArgumentException("Invalid codepoint " + codePoint);
		}
	}

	protected void parseAnnotation() throws IOException {
		verifyCharacterOrFail(readCodePoint(), "{");
		verifyCharacterOrFail(readCodePoint(), "|");
		skipWSC();

		final Resource currentSubject = subject;
		final IRI currentPredicate = predicate;
		subject = Values.triple(previousStatement);
		parsePredicateObjectList();
		verifyCharacterOrFail(readCodePoint(), "|");
		verifyCharacterOrFail(readCodePoint(), "}");
		subject = currentSubject;
		predicate = currentPredicate;
	}
}
