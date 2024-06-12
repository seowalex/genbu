package genbu.model;

import java.util.Objects;

public class SimpleIRI extends org.eclipse.rdf4j.model.impl.SimpleIRI implements Commentable {
    private Comments comments;

    protected SimpleIRI() {}

    protected SimpleIRI(String iriString) {
        super(iriString);
    }

    protected SimpleIRI(String namespace, String localname) {
        super(namespace, localname);
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
