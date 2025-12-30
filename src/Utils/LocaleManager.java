package Utils;

import java.io.*;
import java.util.Locale;

public class LocaleManager {
    private static final String APP_NAME = "SpaceScope";
    private static final String LANG_FILE_NAME = "lang.ini";

    private static File userLangFile() {
        String appData = System.getenv("APPDATA"); // Roaming
        File dir = new File(appData, APP_NAME);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, LANG_FILE_NAME);
    }

    private static File commonLangFile() {
        String programData = System.getenv("PROGRAMDATA"); // ProgramData
        File dir = new File(programData, APP_NAME);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, LANG_FILE_NAME);
    }

    private static Locale readLocaleFromFile(File f) {
        if (f == null || !f.exists()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String code = reader.readLine();
            if (code == null) return null;
            code = code.trim();
            if ("ko".equalsIgnoreCase(code)) return Locale.KOREAN;
            if ("en".equalsIgnoreCase(code)) return Locale.ENGLISH;
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    public static Locale loadLocale() {
        // 1) 사용자 설정
        Locale l = readLocaleFromFile(userLangFile());
        if (l != null) return l;

        // 2) 설치 기본값(공통)
        l = readLocaleFromFile(commonLangFile());
        if (l != null) return l;

        // 3) OS 기본
        return Locale.getDefault();
    }

    public static void saveLocale(Locale locale) {
        File langFile = userLangFile();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(langFile))) {
            String code = locale.getLanguage().equals("ko") ? "ko" : "en";
            writer.write(code);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
