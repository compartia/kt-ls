package org.javacs;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.xml.bind.JAXBException;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import com.kt.advance.api.CAnalysisImpl;
import com.kt.advance.api.CApplication;
import com.kt.advance.api.CFile;
import com.kt.advance.api.CFunctionCallsiteSPO;
import com.kt.advance.api.FsAbstraction;
import com.kt.advance.api.PO;
import com.kt.advance.xml.model.FsAbstractionImpl;

class KtLanguageServer implements LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");
    int maxItems = 50;
    private final CompletableFuture<LanguageClient> client = new CompletableFuture<>();
    private final KtTextDocumentService textDocuments = new KtTextDocumentService(client, this);
    private final KtWorkspaceService workspace = new KtWorkspaceService(client, this, textDocuments);
    private Path workspaceRoot = Paths.get(".");

    void clearFileDiagnostics(Path file) {
        client.thenAccept(
            c -> c.publishDiagnostics(
                new PublishDiagnosticsParams(
                        file.toUri().toString(), new ArrayList<>())));
    }

    public CAnalysisImpl cAnalysis;

    private void readXmls(Collection<CApplication> apps) {
        apps.forEach(app -> app.read());
        poByFileMap = new HashMap<>();
        apps.forEach(app -> {

            app.getCfiles().forEach(f -> mapFilePpos(f));

        });
    }

    Map<File, List<PO>> poByFileMap;

    private void mapFilePpos(CFile file) {

        final List<PO> filePOs = poByFileMap.computeIfAbsent(file.getSourceFile(), f -> new ArrayList<PO>());
        file.getCFunctions().forEach(function -> {

            filePOs.addAll(function.getPPOs());

            for (final CFunctionCallsiteSPO callsite : function.getCallsites()) {
                filePOs.addAll(callsite.getSpos());
            }
        });
    }

    public Optional<List<PO>> getPOsByFile(File file) {
        return Optional.ofNullable(poByFileMap.get(file));
    }

    private void runXmlScanner(File wsRoot) throws JAXBException {
        final FsAbstraction fs = new FsAbstractionImpl(wsRoot);
        cAnalysis = new CAnalysisImpl(fs);
        cAnalysis.readTargetDirs();

        final CApplication appByBaseDir = cAnalysis.getAppByBaseDir(wsRoot);
        if (appByBaseDir != null) {
            readXmls(Collections.singleton(appByBaseDir));
        } else {
            readXmls(cAnalysis.getApps());
        }

        //        for (final XmlParsingIssue pi : xmlParsingIssues) {
        //            saveParsingIssueToSq(pi);
        //        }
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        workspaceRoot = Paths.get(params.getRootUri()).toAbsolutePath().normalize();

        final InitializeResult result = new InitializeResult();
        final ServerCapabilities c = new ServerCapabilities();

        c.setTextDocumentSync(TextDocumentSyncKind.Incremental);

        //        c.setDefinitionProvider(true);
        //        c.setCompletionProvider(new CompletionOptions(true, ImmutableList.of(".")));
        //        c.setHoverProvider(true);
        //        c.setWorkspaceSymbolProvider(true);
        //        c.setReferencesProvider(true);
        //        c.setDocumentSymbolProvider(true);
        //        c.setCodeActionProvider(true);
        //        c.setExecuteCommandProvider(
        //            new ExecuteCommandOptions(ImmutableList.of("Java.importClass")));
        //        c.setSignatureHelpProvider(new SignatureHelpOptions(ImmutableList.of("(", ",")));

        result.setCapabilities(c);

        try {
            this.runXmlScanner(workspaceRoot.toFile());
        } catch (final JAXBException e) {
            throw new RuntimeException(e);
        }

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocuments;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspace;
    }

    void installClient(LanguageClient client) {
        this.client.complete(client);

        final Handler sendToClient = new Handler() {
            @Override
            public void publish(LogRecord record) {
                String message = record.getMessage();

                if (record.getThrown() != null) {
                    final StringWriter trace = new StringWriter();

                    record.getThrown().printStackTrace(new PrintWriter(trace));
                    message += "\n" + trace;
                }

                client.logMessage(
                    new MessageParams(
                            messageType(record.getLevel().intValue()), message));
            }

            private MessageType messageType(int level) {
                if (level >= Level.SEVERE.intValue()) {
                    return MessageType.Error;
                } else if (level >= Level.WARNING.intValue()) {
                    return MessageType.Warning;
                } else if (level >= Level.INFO.intValue()) {
                    return MessageType.Info;
                } else {
                    return MessageType.Log;
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        };

        Logger.getLogger("").addHandler(sendToClient);
    }

    static void onDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        final Level level = level(diagnostic.getKind());
        final String message = diagnostic.getMessage(null);

        LOG.log(level, message);
    }

    private static Level level(Diagnostic.Kind kind) {
        switch (kind) {
        case ERROR:
            return Level.SEVERE;
        case WARNING:
        case MANDATORY_WARNING:
            return Level.WARNING;
        case NOTE:
            return Level.INFO;
        case OTHER:
        default:
            return Level.FINE;
        }
    }
    //
    //    /**
    //     * Compile a .java source and emit a .class file.
    //     *
    //     * <p>
    //     * Useful for testing that the language server works when driven by .class
    //     * files.
    //     */
    //    void compile(URI file) {
    //        Objects.requireNonNull(file, "file is null");
    //
    //        configured().compiler
    //                .compileBatch(Collections.singletonMap(file, textDocuments.activeContent(file)));
    //    }

    //    private static String jsonStringify(Object value) {
    //        try {
    //            return Main.JSON.writeValueAsString(value);
    //        } catch (final JsonProcessingException e) {
    //            throw new RuntimeException(e);
    //        }
    //    }
}
