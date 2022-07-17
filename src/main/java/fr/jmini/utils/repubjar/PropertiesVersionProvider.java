package fr.jmini.utils.repubjar;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

public class PropertiesVersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() throws Exception {
        final URL url = getClass().getResource("/version.txt");
        if (url == null) {
            return new String[] {
                    App.APPLICATION_NAME,
                    "Undefined version, not running from a jar file?"
            };
        }
        final Properties properties = new Properties();
        try (InputStream is = url.openStream()) {
            properties.load(is);
            return new String[] {
                    App.APPLICATION_NAME + " version \"" + properties.getProperty("version") + "\"",
                    "build timestamp: " + properties.getProperty("buildtime"),
            };
        }
    }
}