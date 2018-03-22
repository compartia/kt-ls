package org.javacs;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class UNCPathTool {

    public static File uri2file(String uriStr) throws MalformedURLException, URISyntaxException {
        return uri2file(new URI(uriStr));
    }

    public static File uri2file(URI urlString) throws MalformedURLException, URISyntaxException {

        URI uri = urlString;

        if (uri.getAuthority() != null && uri.getAuthority().length() > 0) {
            // Hack for UNC Path
            uri = (new URL("file://" + urlString.toString().substring("file:".length()))).toURI();
        }

        final File file = new File(uri);

        return file;
    }

}