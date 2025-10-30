package FolderSizeExporter;

import java.io.*;
import java.util.Locale;

public class LocaleManager {
    private static final String LANG_FILE = "lang.ini";

    public static Locale loadLocale() {
        try (BufferedReader reader = new BufferedReader(new FileReader(LANG_FILE))) {
            String code = reader.readLine().trim();
            if ("ko".equalsIgnoreCase(code)) return Locale.KOREAN;
            if ("en".equalsIgnoreCase(code)) return Locale.ENGLISH;
        } catch (IOException e) {
            // ignore, default to system locale
        }
        return Locale.getDefault();
    }

    public static void saveLocale(Locale locale) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LANG_FILE))) {
            String code = locale.getLanguage().equals("ko") ? "ko" : "en";
            writer.write(code);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
