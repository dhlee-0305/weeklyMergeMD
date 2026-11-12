import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.*;

public final class ReportMerger {
    public static final List<String> SECTIONS = List.of("프로젝트 진행 현황", "인력 운용 현황", "거래처 영업/동향 정보", "주요 업무 사항");
    public static final String ALTERNATE_TEMPLATE = "주간 경영전략회의_template.md";
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private final Clock clock;
    public ReportMerger() { this(Clock.systemDefaultZone()); }
    public ReportMerger(Clock clock) { this.clock = clock; }
    public Path resultPath(Path output) {
        return output.resolve(friday().format(DateTimeFormatter.BASIC_ISO_DATE) + "_IT서비스부문_주간보고.md");
    }
    private LocalDate friday() { return LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(4); }
    public static Path template(Path output) throws IOException {
        for (String name : List.of(AppConfig.TEMPLATE, ALTERNATE_TEMPLATE)) {
            Path p = output.resolve(name); if (Files.isRegularFile(p)) return p;
        }
        throw new IOException("템플릿 파일이 없습니다: " + output.resolve(AppConfig.TEMPLATE) + " 또는 " + ALTERNATE_TEMPLATE);
    }
    public static List<Path> listReports(Path input) throws IOException {
        try (var stream = Files.list(input)) {
            return stream.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".md") && !n.contains("template") && !n.matches("\\d{8}_.*\\.md");
            }).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
    }
    public Path merge(List<Path> inputs, Path output, boolean overwrite, BiConsumer<Integer, String> progress) throws IOException {
        if (inputs.isEmpty()) throw new IOException("취합할 원본 Markdown 파일이 없습니다.");
        if (!Files.isDirectory(output)) throw new IOException("출력 폴더가 없습니다: " + output);
        Path target = resultPath(output);
        String base = Files.readString(template(output), StandardCharsets.UTF_8);
        List<String> documents = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            progress.accept(i * 80 / inputs.size(), inputs.get(i).getFileName().toString());
            documents.add(Files.readString(inputs.get(i), StandardCharsets.UTF_8));
        }
        String result = mergeText(base, documents).replace("[취합일]", friday().toString());
        progress.accept(90, "");
        Path temporary = Files.createTempFile(output, ".weekly-", ".tmp");
        try {
            Files.writeString(temporary, result, StandardCharsets.UTF_8);
            if (overwrite) Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, target);
        } finally { Files.deleteIfExists(temporary); }
        progress.accept(100, "");
        return target;
    }
    public String mergeText(String template, List<String> documents) throws IOException {
        String result = template.replace("\r\n", "\n").replace("\uFEFF", "");
        for (int section = 0; section < SECTIONS.size(); section++) {
            Region region = region(result, SECTIONS.get(section));
            if (region == null) throw new IOException("템플릿에 필수 영역이 없습니다: " + SECTIONS.get(section));
            List<String> bodies = new ArrayList<>();
            for (String doc : documents) {
                String normalized = doc.replace("\r\n", "\n").replace("\uFEFF", "");
                Region r = region(normalized, SECTIONS.get(section));
                if (r != null) bodies.add(normalized.substring(r.start, r.end));
            }
            String body = result.substring(region.start, region.end);
            if (section == 3) {
                body = "\n\n" + String.join("\n\n", bodies.stream().map(String::strip).filter(s -> !s.isEmpty()).toList()) + "\n\n";
            } else {
                Table shape = table(body);
                if (shape == null) throw new IOException("템플릿에 표가 없습니다: " + SECTIONS.get(section));
                List<Table> sources = new ArrayList<>();
                for (String b : bodies) {
                    Table t = table(b);
                    if (t == null) continue;
                    if (t.header.size() != shape.header.size()) throw new IOException("표 열 수가 다릅니다: " + SECTIONS.get(section));
                    sources.add(t);
                }
                List<List<String>> rows = new ArrayList<>();
                if (section == 1) {
                    for (List<String> row : shape.rows) {
                        List<String> blank = new ArrayList<>(Collections.nCopies(shape.header.size(), ""));
                        blank.set(0, row.get(0)); rows.add(blank);
                    }
                    for (Table t : sources) {
                        if (t.rows.size() != rows.size()) throw new IOException("인력 운용 표의 행 수가 다릅니다.");
                        for (int r = 0; r < rows.size(); r++) {
                            if (!t.rows.get(r).get(0).replaceAll("\\s", "").equals(rows.get(r).get(0).replaceAll("\\s", "")))
                                throw new IOException("인력 운용 표의 행 순서가 다릅니다: " + t.rows.get(r).get(0));
                            for (int c = 1; c < shape.header.size(); c++) rows.get(r).set(c, add(rows.get(r).get(c), t.rows.get(r).get(c)));
                        }
                    }
                } else {
                    for (Table t : sources) for (List<String> source : t.rows) {
                        if (source.stream().allMatch(String::isBlank)) continue;
                        List<String> row = new ArrayList<>(source);
                        if (section == 2) row.set(0, shape.rows.isEmpty() ? "" : shape.rows.get(Math.min(rows.size(), shape.rows.size() - 1)).get(0));
                        rows.add(row);
                    }
                }
                StringBuilder rendered = new StringBuilder(render(shape.header)).append('\n').append(shape.separator).append('\n');
                for (List<String> row : rows) rendered.append(render(row)).append('\n');
                body = body.substring(0, shape.start) + rendered + body.substring(shape.end);
            }
            result = result.substring(0, region.start) + body + result.substring(region.end);
        }
        return result.stripTrailing() + "\n";
    }
    private static String add(String a, String b) {
        if (a.isBlank()) return b;
        if (b.isBlank()) return a;
        try { return new BigDecimal(a.replace(",", "")).add(new BigDecimal(b.replace(",", ""))).stripTrailingZeros().toPlainString(); }
        catch (NumberFormatException e) { return a + "<br />" + b; }
    }
    private record Region(int start, int end) {}
    private static Region region(String doc, String title) {
        int start = -1, depth = 0, offset = 0;
        boolean fenced = false;
        for (String line : doc.split("\n", -1)) {
            if (line.stripLeading().startsWith("```") || line.stripLeading().startsWith("~~~")) fenced = !fenced;
            Matcher m = HEADING.matcher(line);
            if (!fenced && m.matches()) {
                int level = m.group(1).length();
                if (start >= 0 && level <= depth) return new Region(start, offset);
                if (start < 0 && m.group(2).replaceFirst("^\\d+[.)]?\\s*", "").equals(title)) { start = Math.min(offset + line.length() + 1, doc.length()); depth = level; }
            }
            offset += line.length() + 1;
        }
        return start < 0 ? null : new Region(start, doc.length());
    }
    private record Table(int start, int end, List<String> header, String separator, List<List<String>> rows) {}
    private static Table table(String body) throws IOException {
        String[] lines = body.split("\n", -1); int offset = 0;
        for (int i = 0; i + 1 < lines.length; i++) {
            if (lines[i].strip().startsWith("|") && lines[i + 1].strip().startsWith("|") && cells(lines[i + 1]).stream().allMatch(c -> c.matches(":?-{2,}:?"))) {
                List<String> header = cells(lines[i]); List<List<String>> rows = new ArrayList<>();
                int end = offset + lines[i].length() + lines[i + 1].length() + 2;
                for (int j = i + 2; j < lines.length && lines[j].strip().startsWith("|"); j++) {
                    List<String> row = cells(lines[j]);
                    if (row.size() != header.size()) throw new IOException("Markdown 표의 열 수가 일정하지 않습니다: " + lines[j]);
                    rows.add(row); end += lines[j].length() + 1;
                }
                return new Table(offset, Math.min(end, body.length()), header, lines[i + 1], rows);
            }
            offset += lines[i].length() + 1;
        }
        return null;
    }
    private static List<String> cells(String line) {
        String text = line.strip();
        if (text.startsWith("|")) text = text.substring(1);
        if (text.endsWith("|") && !text.endsWith("\\|")) text = text.substring(0, text.length() - 1);
        return Arrays.stream(text.split("(?<!\\\\)\\|", -1)).map(String::strip).toList();
    }
    private static String render(List<String> cells) { return "| " + String.join(" | ", cells) + " |"; }
}
