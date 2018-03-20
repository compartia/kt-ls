package org.javacs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
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

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.kt.advance.api.PO;

class KtTextDocumentService implements TextDocumentService {
    private final CompletableFuture<LanguageClient> client;
    private final KtLanguageServer server;

    @Deprecated
    private final Map<URI, VersionedContent> activeDocuments = new HashMap<>();

    KtTextDocumentService(CompletableFuture<LanguageClient> client, KtLanguageServer server) {
        this.client = client;
        this.server = server;
    }

    /** Text of file, if it is in the active set */
    Optional<String> activeContent(URI file) {
        return Optional.ofNullable(activeDocuments.get(file)).map(doc -> doc.content);
    }

    /**
     * All open files, not including things like old git-versions in a diff view
     */
    Set<URI> openFiles() {
        return Sets.filter(activeDocuments.keySet(), uri -> uri.getScheme().equals("file"));
    }

    static class FileDiagnostic implements Diagnostic<File> {
        final PO po;
        final File source;

        public FileDiagnostic(PO po, File source) {
            this.po = po;
            this.source = source;
        }

        @Override
        public javax.tools.Diagnostic.Kind getKind() {
            switch (po.getStatus()) {
            case discharged:
                return javax.tools.Diagnostic.Kind.NOTE;
            case open:
                return javax.tools.Diagnostic.Kind.OTHER;
            case violation:
                return javax.tools.Diagnostic.Kind.ERROR;
            default:
                return javax.tools.Diagnostic.Kind.WARNING;
            }
        }

        @Override
        public File getSource() {
            return source;
        }

        @Override
        public long getPosition() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public long getStartPosition() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public long getEndPosition() {
            // TODO Auto-generated method stub
            return Integer.MAX_VALUE;
        }

        @Override
        public long getLineNumber() {
            // TODO Auto-generated method stub
            return po.getLocation().getLine();
        }

        @Override
        public long getColumnNumber() {
            // TODO Auto-generated method stub
            return 0;
        }

        @Override
        public String getCode() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getMessage(Locale locale) {
            return po.toString();
        }

    }

    private void reportPosByFile(File file, DiagnosticCollector<File> dc) {
        final Optional<List<PO>> pOsByFile = server.getPOsByFile(file);

        pOsByFile.ifPresent(
            list -> list.stream()
                    .map(x -> new FileDiagnostic(x, file))
                    .forEach(diagnostic -> dc.report(diagnostic)));
    }

    void doLint(Collection<URI> paths) {
        LOG.info("Lint " + Joiner.on(", ").join(paths));

        final DiagnosticCollector<File> dc = new DiagnosticCollector<>();

        paths.stream()
                .map(File::new)
                .forEach(file -> {
                    reportPosByFile(file, dc);
                });

        final Map<URI, Optional<String>> content = paths
                .stream()
                .collect(Collectors.toMap(f -> f, this::activeContent));

        publishDiagnostics(paths, dc);

    }

    private void publishDiagnostics(
            Collection<URI> touched,
            DiagnosticCollector<File> diagnostics) {

        final Map<URI, PublishDiagnosticsParams> files = touched.stream()
                .collect(
                    Collectors.toMap(
                        uri -> uri,
                        newUri -> new PublishDiagnosticsParams(
                                newUri.toString(), new ArrayList<>())));

        // Organize diagnostics by file
        for (final javax.tools.Diagnostic<? extends File> error : diagnostics.getDiagnostics()) {
            final URI uri = error.getSource().toURI();
            final PublishDiagnosticsParams publish = files.computeIfAbsent(
                uri,
                newUri -> new PublishDiagnosticsParams(
                        newUri.toString(), new ArrayList<>()));
            Lints.convert(error).ifPresent(publish.getDiagnostics()::add);
        }

        // If there are no errors in a file, put an empty PublishDiagnosticsParams
        for (final URI each : touched) {
            files.putIfAbsent(each, new PublishDiagnosticsParams());
        }

        files.forEach(
            (file, errors) -> {
                if (touched.contains(file)) {
                    client.join().publishDiagnostics(errors);

                    LOG.info(
                        "Published "
                                + errors.getDiagnostics().size()
                                + " errors from "
                                + file);
                } else {
                    LOG.info(
                        "Ignored "
                                + errors.getDiagnostics().size()
                                + " errors from not-open "
                                + file);
                }
            });
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

        //XXX: we dont need it?
        activeDocuments.put(uri, new VersionedContent(document.getText(), document.getVersion()));

        doLint(Collections.singleton(uri));
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

        // Clear diagnostics
        client.join()
                .publishDiagnostics(
                    new PublishDiagnosticsParams(uri.toString(), new ArrayList<>()));
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // Re-lint all active documents
        doLint(openFiles());
    }

    private static final Logger LOG = Logger.getLogger("main");
}
