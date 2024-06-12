package genbu.model;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;

public class GenericStatement<R extends Resource, I extends IRI, V extends Value>
        extends org.eclipse.rdf4j.model.impl.GenericStatement<R, I, V> {
    protected GenericStatement(R subject, I predicate, V object, R context) {
        super(subject, predicate, object, context);
    }
}
