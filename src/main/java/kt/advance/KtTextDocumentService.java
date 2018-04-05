package kt.advance;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import com.google.common.collect.Sets;

class KtTextDocumentService implements TextDocumentService {
    private final CompletableFuture<LanguageClient> client;
    private final KtLanguageServer server;

    @Deprecated
    private final Map<URI, VersionedContent> activeDocuments = new HashMap<>();

    KtTextDocumentService(CompletableFuture<LanguageClient> client, KtLanguageServer server) {
        this.client = client;
        this.server = server;
    }

    /**
     * All open files, not including things like old git-versions in a diff view
     */
    Set<URI> openFiles() {
        return Sets.filter(activeDocuments.keySet(), uri -> uri.getScheme().equals("file"));
    }

    public static class DiagnosticComparator implements Comparator<Diagnostic> {
        public final static DiagnosticComparator instance = new DiagnosticComparator();

        @Override
        public int compare(Diagnostic a, Diagnostic b) {

            int diff = a.getSeverity().getValue() - b.getSeverity().getValue();
            if (diff == 0) {
                diff = a.getCode().compareTo(b.getCode());
            }
            if (diff == 0) {
                diff = a.getMessage().compareTo(b.getMessage());
            }
            return diff;
        }

    }

    public void reportDiagnosticsByFile(Collection<File> files) {
        files.forEach(file -> reportDiagnostics(file));
    }

    private void reportDiagnostics(File file) {
        server.getPOsByFile(file)
                .ifPresent(list -> {

                    Collections.sort(list, DiagnosticComparator.instance);

                    final PublishDiagnosticsParams eee = new PublishDiagnosticsParams(file.toURI().toString(), list);

                    if (list.size() > 2000) {
                        try {
                            Thread.sleep(200);
                        } catch (final Exception e) {
                            e.printStackTrace();
                        }
                    }

                    client.join().publishDiagnostics(eee);
                    LOG.info("Published "
                            + eee.getDiagnostics().size()
                            + " errors from "
                            + file);

                });
    }

    public void reportDiagnostics(Collection<URI> paths) {
        //        LOG.info("Lint " + Joiner.on(", ").join(paths));
        paths.forEach(uri -> reportDiagnostics(UNCPathTool.uri2file(uri)));

    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
            TextDocumentPositionParams position) {
        return null;
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        return null;
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
        return null;
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(
            TextDocumentPositionParams position) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(
            TextDocumentPositionParams position) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(
            DocumentSymbolParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return null;
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(
            DocumentRangeFormattingParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(
            DocumentOnTypeFormattingParams params) {
        return null;
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return null;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        final TextDocumentItem document = params.getTextDocument();
        final URI uri = URI.create(document.getUri());
        LOG.log(Level.INFO, "didOpen " + uri);
        //XXX: we dont need it?
        activeDocuments.put(uri, new VersionedContent(document.getText(), document.getVersion()));

        //        reportDiagnostics(Collections.singleton(uri));
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        final VersionedTextDocumentIdentifier document = params.getTextDocument();
        final URI uri = URI.create(document.getUri());
        final VersionedContent existing = activeDocuments.get(uri);
        String newText = existing.content;

        if (document.getVersion() > existing.version) {
            for (final TextDocumentContentChangeEvent change : params.getContentChanges()) {
                if (change.getRange() == null) {
                    activeDocuments.put(
                        uri, new VersionedContent(change.getText(), document.getVersion()));
                } else {
                    newText = patch(newText, change);
                }
            }

            activeDocuments.put(uri, new VersionedContent(newText, document.getVersion()));
        } else {
            LOG.warning(
                "Ignored change with version "
                        + document.getVersion()
                        + " <= "
                        + existing.version);
        }
    }

    private String patch(String sourceText, TextDocumentContentChangeEvent change) {
        try {
            final Range range = change.getRange();
            final BufferedReader reader = new BufferedReader(new StringReader(sourceText));
            final StringWriter writer = new StringWriter();

            // Skip unchanged lines
            int line = 0;

            while (line < range.getStart().getLine()) {
                writer.write(reader.readLine() + '\n');
                line++;
            }

            // Skip unchanged chars
            for (int character = 0; character < range.getStart().getCharacter(); character++) {
                writer.write(reader.read());
            }

            // Write replacement text
            writer.write(change.getText());

            // Skip replaced text
            reader.skip(change.getRangeLength());

            // Write remaining text
            while (true) {
                final int next = reader.read();

                if (next == -1) {
                    return writer.toString();
                } else {
                    writer.write(next);
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        final TextDocumentIdentifier document = params.getTextDocument();
        final URI uri = URI.create(document.getUri());

        // Remove from source cache
        activeDocuments.remove(uri);

    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // Re-lint all active documents
        reportDiagnostics(openFiles());
    }

    private static final Logger LOG = Logger.getLogger("main");
}
