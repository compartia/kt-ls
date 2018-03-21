package org.javacs;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class UNCPathTool {

    public static void main(String[] args) throws MalformedURLException, URISyntaxException {
        final UNCPathTool upt = new UNCPathTool();

        upt.uri2file(new URI("file://server/dir/file.txt"));  // Windows UNC Path
        //
        upt.uri2file(new URI("file:///Z:/dir/file.txt"));     // Windows drive letter path
        //
        upt.uri2file(new URI("file:///dir/file.txt"));        // Unix (absolute) path
    }

    public static File uri2file(String uriStr) throws MalformedURLException, URISyntaxException {
        return uri2file(new URI(uriStr));

    }

    public static File uri2file(URI urlString) throws MalformedURLException, URISyntaxException {

        URI uri = urlString;//url.toURI();

        if (uri.getAuthority() != null && uri.getAuthority().length() > 0) {
            // Hack for UNC Path
            uri = (new URL("file://" + urlString.toString().substring("file:".length()))).toURI();
        }

        final File file = new File(uri);

        return file;
    }

}