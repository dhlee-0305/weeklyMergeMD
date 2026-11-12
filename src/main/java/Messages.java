import java.io.IOException;
import java.nio.file.*;
import java.text.MessageFormat;
import java.util.Properties;

public final class Messages {
    private final Properties values;
    public Messages() throws IOException {
        values = AppConfig.defaults("messages.properties");
        Path besideJar = AppConfig.applicationDirectory().resolve("messages.properties");
        Path local = Path.of("messages.properties").toAbsolutePath();
        if (Files.isRegularFile(besideJar)) values.putAll(AppConfig.read(besideJar));
        if (Files.isRegularFile(local)) values.putAll(AppConfig.read(local));
    }
    public String get(String key, Object... args) { return MessageFormat.format(values.getProperty(key, key), args); }
}
