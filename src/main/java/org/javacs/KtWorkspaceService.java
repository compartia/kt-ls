package org.javacs;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.WorkspaceService;

class KtWorkspaceService implements WorkspaceService {
    private final CompletableFuture<LanguageClient> client;
    private final KtLanguageServer server;
    private final KtTextDocumentService textDocuments;

    KtWorkspaceService(
            CompletableFuture<LanguageClient> client,
            KtLanguageServer server,
            KtTextDocumentService textDocuments) {
        this.client = client;
        this.server = server;
        this.textDocuments = textDocuments;
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        LOG.info(params.toString());

        switch (params.getCommand()) {
        case "kt.sampleCommand":
            final String fileString = (String) params.getArguments().get(0);
            final URI fileUri = URI.create(fileString);
            final String packageName = (String) params.getArguments().get(1);
            final String className = (String) params.getArguments().get(2);

            break;
        default:
            LOG.warning("Don't know what to do with " + params.getCommand());
        }

        return CompletableFuture.completedFuture("Done");
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> symbol(
            WorkspaceSymbolParams params) {

        return null;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams change) {
        LOG.info("didChangeConfigurations");
        //        settings = Main.JSON.convertValue(change.getSettings(), JavaSettings.class);
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        textDocuments.doLint(textDocuments.openFiles());
    }

    private static final Logger LOG = Logger.getLogger("main");
}
