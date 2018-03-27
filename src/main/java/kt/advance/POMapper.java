package kt.advance;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.kt.advance.api.Definitions;
import com.kt.advance.api.Definitions.POLevel;
import com.kt.advance.api.PO;

public class POMapper {
    public static Diagnostic convert(PO po) {

        final Range range = position(po);
        final Diagnostic diagnostic = new Diagnostic();
        final DiagnosticSeverity severity = severity(po);

        diagnostic.setSeverity(severity);
        diagnostic.setRange(range);
        diagnostic.setCode(po.getPredicate().type.label);
        diagnostic.setMessage(description(po));
        diagnostic.setSource(po.getLevel() == POLevel.PRIMARY ? "KT Advance" : "KT Advance (secondary)");

        return diagnostic;

    }

    private static DiagnosticSeverity severity(PO po) {
        switch (po.getStatus()) {
        case violation:
            return DiagnosticSeverity.Error;
        case dead:
            return DiagnosticSeverity.Warning;
        case discharged:
            return DiagnosticSeverity.Information;
        default:
            return DiagnosticSeverity.Hint;
        }
    }

    static String description(PO po) {
        final StringBuffer sb = new StringBuffer();

        sb.append("#").append(po.getId()).append("\t");
        sb.append("<").append(po.getStatus().label).append(">\t ");
        sb.append(po.getPredicate().type.label).append("; \n");

        sb.append(po.getLevel() == POLevel.SECONDARY ? "Secondary; " : "");
        if (null != po.getExplaination()) {
            sb.append(po.getExplaination());
        }
        sb.append("\n").append(po.getPredicate().express());

        if (po.getDeps().level != Definitions.DepsLevel.s /* self */
                && po.getDeps().level != Definitions.DepsLevel.i /* unknown */) {
            sb.append("\n").append(po.getDeps().level.toString());
        }

        return sb.toString();
    }

    static Range position(PO po) {

        int lineNumber = po.getLocation().getLine();

        if (lineNumber > 0) {
            lineNumber = lineNumber - 1;
        }
        return new Range(
                new Position(
                        lineNumber,
                        0),
                new Position(
                        lineNumber,
                        Integer.MAX_VALUE));
    }
}
