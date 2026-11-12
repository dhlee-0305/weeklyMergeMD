import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public record AppConfig(Path inputDirectory, Path outputDirectory) {
    public static final String TEMPLATE = "경영전략회의_template_java.md";
    public static Path applicationDirectory() {
        try {
            Path location = Path.of(AppConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (Exception e) { return Path.of("").toAbsolutePath(); }
    }
    static Properties read(Path path) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(r); }
        return p;
    }
    static Properties defaults(String name) throws IOException {
        Properties p = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/" + name)) {
            if (in != null) p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }
    public static AppConfig load() throws IOException {
        Properties p = defaults("app.properties");
        for (Path dir : List.of(Path.of("").toAbsolutePath(), applicationDirectory())) {
            Path file = dir.resolve("app.properties");
            if (Files.isRegularFile(file)) { p = read(file); break; }
        }
        Path cwd = Path.of("").toAbsolutePath();
        return new AppConfig(absolute(p.getProperty("report.doc.path"), cwd),
                absolute(p.getProperty("report.doc.out.path"), cwd.resolve("output")));
    }
    private static Path absolute(String value, Path fallback) {
        try { Path p = Path.of(value == null ? "" : value.trim()); return p.isAbsolute() ? p.normalize() : fallback; }
        catch (InvalidPathException e) { return fallback; }
    }
}
