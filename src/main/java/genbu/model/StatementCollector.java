package genbu.model;

import java.util.Collection;
import java.util.Map;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.rio.helpers.ContextStatementCollector;

public class StatementCollector extends ContextStatementCollector {
    public StatementCollector() {
        super(SimpleValueFactory.getInstance());
    }

    public StatementCollector(Collection<Statement> statements) {
        super(statements, SimpleValueFactory.getInstance());
    }

    public StatementCollector(Collection<Statement> statements, Map<String, String> namespaces) {
        super(statements, namespaces, SimpleValueFactory.getInstance());
    }
}
