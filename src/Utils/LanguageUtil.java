package Utils;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class LanguageUtil {

    private static final String BASE_NAME = "resources.messages";
    private static volatile ResourceBundle bundle;

    private LanguageUtil() {}

    public static void init() {
        setLocale(LocaleManager.loadLocale());
    }

    public static void setLocale(Locale locale) {
        bundle = ResourceBundle.getBundle(BASE_NAME, locale, new UTF8Control());
    }

    public static ResourceBundle getBundle() {
        if (bundle == null) init();
        return bundle;
    }

    public static String ln(String key) {
        return ln(key, key);
    }

    public static String ln(String key, String fallback) {
        try {
            return getBundle().getString(key);
        } catch (MissingResourceException e) {
            return fallback;
        }
    }

    public static String fmt(String key, Object... args) {
        return MessageFormat.format(ln(key, key), args);
    }

}
