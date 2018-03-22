package kt.advance;

import java.io.File;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

class Lints {

    static Optional<Diagnostic> convert(javax.tools.Diagnostic<? extends File> error) {

        if (error.getStartPosition() != javax.tools.Diagnostic.NOPOS) {
            final Range range = position(error);
            final Diagnostic diagnostic = new Diagnostic();
            final DiagnosticSeverity severity = severity(error.getKind());

            diagnostic.setSeverity(severity);
            diagnostic.setRange(range);
            diagnostic.setCode(error.getCode());
            diagnostic.setMessage(error.getMessage(null));
            diagnostic.setSource("KT Advance");

            return Optional.of(diagnostic);
        } else {
            LOG.warning("Skipped " + error.getMessage(Locale.getDefault()));
            return Optional.empty();
        }
    }

    private static DiagnosticSeverity severity(javax.tools.Diagnostic.Kind kind) {
        switch (kind) {
        case ERROR:
            return DiagnosticSeverity.Error;
        case WARNING:
        case MANDATORY_WARNING:
            return DiagnosticSeverity.Warning;
        case NOTE:
            return DiagnosticSeverity.Hint;
        case OTHER:
        default:
            return DiagnosticSeverity.Information;
        }
    }

    private static Range position(javax.tools.Diagnostic<? extends File> error) {

        int lineNumber = (int) error.getLineNumber();
        if (lineNumber > 0) {
            lineNumber = lineNumber - 1;
        }
        return new Range(
                new Position(
                        lineNumber,
                        (int) error.getStartPosition()),
                new Position(
                        lineNumber,
                        (int) error.getEndPosition()));
    }

    private static final Logger LOG = Logger.getLogger("main");
}
