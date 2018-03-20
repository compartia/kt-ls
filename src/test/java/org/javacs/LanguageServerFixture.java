package org.javacs;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;

class LanguageServerFixture {

    public static Path DEFAULT_WORKSPACE_ROOT =
            Paths.get("src/test/test-project/workspace").toAbsolutePath();

    static {
        Main.setRootFormat();
    }

    static KtLanguageServer getJavaLanguageServer() {
        return getJavaLanguageServer(
                DEFAULT_WORKSPACE_ROOT, diagnostic -> LOG.info(diagnostic.getMessage()));
    }

    static KtLanguageServer getJavaLanguageServer(
            Path workspaceRoot, Consumer<Diagnostic> onError) {
        return getJavaLanguageServer(
                workspaceRoot,
                new LanguageClient() {
                    @Override
                    public void telemetryEvent(Object o) {}

                    @Override
                    public void publishDiagnostics(
                            PublishDiagnosticsParams publishDiagnosticsParams) {
                        publishDiagnosticsParams.getDiagnostics().forEach(onError);
                    }

                    @Override
                    public void showMessage(MessageParams messageParams) {}

                    @Override
                    public CompletableFuture<MessageActionItem> showMessageRequest(
                            ShowMessageRequestParams showMessageRequestParams) {
                        return null;
                    }

                    @Override
                    public void logMessage(MessageParams messageParams) {}
                });
    }

    private static KtLanguageServer getJavaLanguageServer(
            Path workspaceRoot, LanguageClient client) {
        KtLanguageServer server = new KtLanguageServer();
        InitializeParams init = new InitializeParams();

        init.setRootPath(workspaceRoot.toString());

        server.initialize(init);
        server.installClient(client);

        server.maxItems = 100;

        return server;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
