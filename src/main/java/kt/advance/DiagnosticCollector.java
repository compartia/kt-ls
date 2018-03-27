package kt.advance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

public class DiagnosticCollector {
    private final List<Diagnostic> diagnostics = Collections.synchronizedList(new ArrayList<Diagnostic>());

    public void report(Diagnostic diagnostic) {
        diagnostic.getClass(); // null check
        diagnostics.add(diagnostic);
    }

    /**
     * Gets a list view of diagnostics collected by this object.
     *
     * @return a list view of diagnostics
     */
    public List<Diagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }
}
