package org.javacs;

//import com.sun.tools.javac.api.JavacTool;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;

public class Main {
    public static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JSR310Module())
            .registerModule(pathAsJson())
            .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

    private static final Logger LOG = Logger.getLogger("main");

    public static void setRootFormat() {
        final Logger root = Logger.getLogger("");

        for (final Handler h : root.getHandlers()) {
            h.setFormatter(new LogFormat());
        }
    }

    private static SimpleModule pathAsJson() {
        final SimpleModule m = new SimpleModule();

        m.addSerializer(
            Path.class,
            new JsonSerializer<Path>() {
                @Override
                public void serialize(
                        Path path, JsonGenerator gen, SerializerProvider serializerProvider)
                        throws IOException, JsonProcessingException {
                    gen.writeString(path.toString());
                }
            });

        m.addDeserializer(
            Path.class,
            new JsonDeserializer<Path>() {
                @Override
                public Path deserialize(
                        JsonParser parse, DeserializationContext deserializationContext)
                        throws IOException, JsonProcessingException {
                    return Paths.get(parse.getText());
                }
            });

        return m;
    }

    public static void main(String[] args) {
        try {

            final Class<?> main = Class.forName("org.javacs.Main");
            final Method run = main.getMethod("run");
            run.invoke(null);
        } catch (final Exception e) {
            LOG.log(Level.SEVERE, "Failed", e);
        }
    }

    public static void run() {

        setRootFormat();

        try {
            final Socket connection = connectToNode();

            run(connection);
        } catch (final Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);

            System.exit(1);
        }
    }

    private static Socket connectToNode() throws IOException {
        final String port = System.getProperty("javacs.port");

        Objects.requireNonNull(port, "-Djavacs.port=? is required");

        LOG.info("Connecting to " + port);

        final Socket socket = new Socket("localhost", Integer.parseInt(port));

        LOG.info("Connected to parent using socket on port " + port);

        return socket;
    }

    /**
     * Listen for requests from the parent node process. Send replies
     * asynchronously. When the request stream is closed, wait for 5s for all
     * outstanding responses to compute, then return.
     */
    public static void run(Socket connection) throws IOException {
        final KtLanguageServer server = new KtLanguageServer();
        final Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
            server, connection.getInputStream(), connection.getOutputStream());

        server.installClient(launcher.getRemoteProxy());
        launcher.startListening();
        LOG.info(String.format("java.version is %s", System.getProperty("java.version")));
    }
}
