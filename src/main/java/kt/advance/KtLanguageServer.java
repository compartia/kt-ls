package kt.advance;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Path;
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
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;

import org.eclipse.lsp4j.Diagnostic;
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
import com.kt.advance.api.FsAbstraction;
import com.kt.advance.xml.model.FsAbstractionImpl;

class KtLanguageServer implements LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");

    private final CompletableFuture<LanguageClient> client = new CompletableFuture<>();
    private final KtTextDocumentService textDocuments = new KtTextDocumentService(client, this);
    private final KtWorkspaceService workspace = new KtWorkspaceService(client, this, textDocuments);
    private File workspaceRoot;
    private Map<File, List<Diagnostic>> poByFileMap;
    private CAnalysisImpl cAnalysis;

    void clearFileDiagnostics(Path file) {
        client.thenAccept(
            c -> c.publishDiagnostics(
                new PublishDiagnosticsParams(
                        file.toUri().toString(), new ArrayList<>())));
    }

    private void readXmls(Collection<CApplication> apps) {
        apps.forEach(app -> app.read());
        poByFileMap = new HashMap<>();
        apps.forEach(app -> {
            app.getCfiles().forEach(f -> mapFilePpos(f, poByFileMap));
        });
    }

    private void mapFilePpos(CFile file, Map<File, List<Diagnostic>> poByFileMap) {

        final List<Diagnostic> filePOs = poByFileMap.computeIfAbsent(
            file.getSourceFile(),
            f -> new ArrayList<Diagnostic>());

        file.getCFunctions().forEach(function -> {
            filePOs.addAll(
                function.getPPOs()
                        .stream()
                        .map(POMapper::convert)
                        .collect(Collectors.toList()));

            function.getCallsites().forEach(callsite -> filePOs.addAll(
                callsite.getSpos()
                        .stream()
                        .map(POMapper::convert)
                        .collect(Collectors.toList())));
        });
    }

    public Optional<List<Diagnostic>> getPOsByFile(File file) {
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

    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {

        try {

            workspaceRoot = UNCPathTool.uri2file(params.getRootUri());//.t  Paths.get(params.getRootUri()).toAbsolutePath().normalize();

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

            this.runXmlScanner(workspaceRoot);

            return CompletableFuture.completedFuture(result);
        } catch (final JAXBException e) {
            throw new RuntimeException(e);
        } catch (final MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }

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

    //    static void onDiagnostic(Diagnostic diagnostic) {
    //        final Level level = level(diagnostic.getKind());
    //        final String message = diagnostic.getMessage(null);
    //
    //        LOG.log(level, message);
    //    }
    //
    //    private static Level level(Diagnostic.Kind kind) {
    //        switch (kind) {
    //        case ERROR:
    //            return Level.SEVERE;
    //        case WARNING:
    //        case MANDATORY_WARNING:
    //            return Level.WARNING;
    //        case NOTE:
    //            return Level.INFO;
    //        case OTHER:
    //        default:
    //            return Level.FINE;
    //        }
    //    }

}
