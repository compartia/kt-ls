package kt.advance;

//import com.sun.tools.javac.api.JavacTool;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
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
        //XXX: use arguments to select master or slave mode
        try {

            final Class<?> main = Class.forName("kt.advance.Main");
            final Method run = main.getMethod("run");
            run.invoke(null);
        } catch (final Exception e) {
            LOG.log(Level.SEVERE, "Failed", e);
        }
    }

    public static void runSlave() {

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

    public static void run() throws InterruptedException, IOException {
        final String port = System.getProperty("javacs.port");
        Objects.requireNonNull(port, "-Djavacs.port=? is required");
        LOG.info("Binding to " + port);

        final KtLanguageServer languageServer = new KtLanguageServer();

        final Function<MessageConsumer, MessageConsumer> wrapper = consumer -> {
            final MessageConsumer result = consumer;
            return result;
        };

        final Launcher<LanguageClient> launcher = createSocketLauncher(languageServer, LanguageClient.class,
            new InetSocketAddress("localhost", Integer.parseInt(port)), Executors.newCachedThreadPool(), wrapper);

        languageServer.installClient(launcher.getRemoteProxy());
        final Future<?> future = launcher.startListening();
        while (!future.isDone()) {
            Thread.sleep(10_000l);
        }
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

    static <T> Launcher<T> createSocketLauncher(Object localService, Class<T> remoteInterface,
            SocketAddress socketAddress, ExecutorService executorService,
            Function<MessageConsumer, MessageConsumer> wrapper) throws IOException {
        final AsynchronousServerSocketChannel serverSocket = AsynchronousServerSocketChannel.open().bind(socketAddress);
        AsynchronousSocketChannel socketChannel;
        try {
            socketChannel = serverSocket.accept().get();
            return Launcher.createIoLauncher(localService, remoteInterface, Channels.newInputStream(socketChannel),
                Channels.newOutputStream(socketChannel), executorService, wrapper);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

}
