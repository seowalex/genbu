package genbu.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.xml.datatype.XMLGregorianCalendar;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.base.AbstractValueFactory;
import org.eclipse.rdf4j.model.base.CoreDatatype;

public class SimpleValueFactory extends AbstractValueFactory {
    private static final SimpleValueFactory sharedInstance = new SimpleValueFactory();

    private final static String uniqueIdPrefix = UUID.randomUUID().toString().replace("-", "");
    private final static AtomicLong uniqueIdSuffix = new AtomicLong();

    public static SimpleValueFactory getInstance() {
        return sharedInstance;
    }

    protected SimpleValueFactory() {}

    @Override
    public BNode createBNode() {
        return new SimpleBNode(uniqueIdPrefix + uniqueIdSuffix.incrementAndGet());
    }

    @Override
    public BNode createBNode(String nodeID) {
        return new SimpleBNode(nodeID);
    }

    @Override
    public IRI createIRI(String iri) {
        return new SimpleIRI(iri);
    }

    @Override
    public IRI createIRI(String namespace, String localName) {
        return new SimpleIRI(namespace, localName);
    }

    @Override
    public Literal createLiteral(String value) {
        return new SimpleLiteral(value, CoreDatatype.XSD.STRING);
    }

    @Override
    public Literal createLiteral(String value, IRI datatype) {
        return new SimpleLiteral(value, datatype);
    }

    @Override
    public Literal createLiteral(String label, CoreDatatype coreDatatype) {
        return new SimpleLiteral(label, coreDatatype);
    }

    @Override
    public Literal createLiteral(String label, IRI datatype, CoreDatatype coreDatatype) {
        return new SimpleLiteral(label, datatype, coreDatatype);
    }

    @Override
    public Literal createLiteral(String value, String language) {
        return new SimpleLiteral(value, language);
    }

    @Override
    public Literal createLiteral(boolean b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(byte value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(short value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(long value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(float value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(double value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(BigInteger bigInteger) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(BigDecimal bigDecimal) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(TemporalAccessor value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(TemporalAmount value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(XMLGregorianCalendar calendar) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Literal createLiteral(Date date) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Triple createTriple(Resource subject, IRI predicate, Value object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Statement createStatement(Resource subject, IRI predicate, Value object) {
        return new GenericStatement<>(subject, predicate, object, null);
    }

    @Override
    public Statement createStatement(Resource subject, IRI predicate, Value object,
            Resource context) {
        return new GenericStatement<>(subject, predicate, object, context);
    }
}
