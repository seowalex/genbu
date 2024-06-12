package genbu.model;

import java.util.Objects;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.base.CoreDatatype;

public class SimpleLiteral extends org.eclipse.rdf4j.model.impl.SimpleLiteral
        implements Commentable {
    private Comments comments;

    protected SimpleLiteral() {}

    protected SimpleLiteral(String label) {
        super(label);
    }

    protected SimpleLiteral(String label, String language) {
        super(label, language);
    }

    protected SimpleLiteral(String label, IRI datatype) {
        super(label, datatype);
    }

    protected SimpleLiteral(String label, IRI datatype, CoreDatatype coreDatatype) {
        super(label, datatype, coreDatatype);
    }

    protected SimpleLiteral(String label, CoreDatatype datatype) {
        super(label, datatype);
    }

    @Override
    public Comments getComments() {
        return comments;
    }

    @Override
    public void setComments(Comments comments) {
        this.comments = comments;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) {
            return false;
        }

        if (o instanceof Commentable other) {
            return Objects.equals(comments, other.getComments());
        }

        return true;
    }
}

