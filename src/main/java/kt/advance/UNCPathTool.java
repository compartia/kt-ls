package kt.advance;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class UNCPathTool {

    public static File uri2file(String uriStr) throws MalformedURLException, URISyntaxException {
        return uri2file(new URI(uriStr));
    }

    public static File uri2file(URI urlString) {

        URI uri = urlString;

        if (uri.getAuthority() != null && uri.getAuthority().length() > 0) {
            // Hack for UNC Path
            try {
                uri = (new URL("file://" + urlString.toString().substring("file:".length()))).toURI();
            } catch (MalformedURLException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        final File file = new File(uri);

        return file;
    }

}