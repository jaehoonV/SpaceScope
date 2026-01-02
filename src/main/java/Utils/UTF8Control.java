package Utils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * UTF-8 인코딩으로 .properties 파일을 읽을 수 있게 해주는 ResourceBundle Control 클래스
 *
 * 기본적으로 ResourceBundle은 ISO-8859-1 인코딩만 지원하지만,
 * 이 클래스를 사용하면 UTF-8로 저장된 messages_xx.properties 파일을 그대로 읽을 수 있습니다.
 */
public class UTF8Control extends ResourceBundle.Control {
    @Override
    public ResourceBundle newBundle(String baseName, java.util.Locale locale, String format,
                                    ClassLoader loader, boolean reload)
            throws IllegalAccessException, InstantiationException, IOException {

        // messages_ko.properties → messages_ko
        String bundleName = toBundleName(baseName, locale);
        String resourceName = toResourceName(bundleName, "properties");

        ResourceBundle bundle = null;
        InputStream stream = null;

        if (reload) {
            URL url = loader.getResource(resourceName);
            if (url != null) {
                URLConnection connection = url.openConnection();
                if (connection != null) {
                    connection.setUseCaches(false);
                    stream = connection.getInputStream();
                }
            }
        } else {
            stream = loader.getResourceAsStream(resourceName);
        }

        if (stream != null) {
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                bundle = new PropertyResourceBundle(reader);
            } finally {
                stream.close();
            }
        }

        return bundle;
    }
}
