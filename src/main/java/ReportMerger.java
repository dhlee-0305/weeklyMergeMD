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
        if (inputs.isEmpty()) throw new IOException("병합할 원본 Markdown 파일이 없습니다.");
        if (!Files.isDirectory(output)) throw new IOException("출력 폴더가 없습니다: " + output);
        Path target = resultPath(output);
        String base = Files.readString(template(output), StandardCharsets.UTF_8);
        List<String> documents = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            progress.accept(i * 40 / inputs.size(), inputs.get(i).getFileName().toString());
            documents.add(Files.readString(inputs.get(i), StandardCharsets.UTF_8));
            progress.accept((i + 1) * 40 / inputs.size(), inputs.get(i).getFileName().toString());
        }
        String result = mergeText(base, documents, inputs.stream().map(p -> p.getFileName().toString()).toList(),
                section -> progress.accept(40 + section * 50 / SECTIONS.size(), "")).replace("[병합일]", friday().toString()).replace("[취합일]", friday().toString());
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
        return mergeText(template, documents, null);
    }
    private String mergeText(String template, List<String> documents, List<String> fileNames) throws IOException {
        return mergeText(template, documents, fileNames, section -> {});
    }
    private String mergeText(String template, List<String> documents, List<String> fileNames, java.util.function.IntConsumer sectionProgress) throws IOException {
        sectionProgress.accept(0);
        String result = template.replace("\r\n", "\n").replace("\uFEFF", "");
        for (int section = 0; section < SECTIONS.size(); section++) {
            Region region = region(result, SECTIONS.get(section));
            if (region == null) throw new IOException("템플릿에 필수 영역이 없습니다: " + SECTIONS.get(section));
            List<String> bodies = new ArrayList<>();
            for (int index = 0; index < documents.size(); index++) {
                String normalized = documents.get(index).replace("\r\n", "\n").replace("\uFEFF", "");
                Region r = region(normalized, SECTIONS.get(section));
                if (r != null) {
                    String content = normalized.substring(r.start, r.end);
                    if (section == 3 && fileNames != null && !content.isBlank())
                        content = teamSection(teamName(fileNames.get(index)), content);
                    bodies.add(content);
                }
            }
            String body = result.substring(region.start, region.end);
            if (section == 3) {
                body = "\n\n" + String.join("\n\n", bodies.stream().map(String::strip).filter(s -> !s.isEmpty()).toList()) + "\n\n";
                body = numberWorkHeadings(body);
            } else {
                Table shape = table(body);
                if (shape == null) throw new IOException("템플릿에 표가 없습니다: " + SECTIONS.get(section));
                List<Table> sources = new ArrayList<>();
                for (String b : bodies) {
                    Table t = table(b);
                    if (t == null) continue;
                    if (section != 1 && t.header.size() != shape.header.size()) throw new IOException("표 열 수가 다릅니다: " + SECTIONS.get(section));
                    sources.add(t);
                }
                List<List<String>> rows = new ArrayList<>();
                if (section == 1) {
                    Table personnel = mergePersonnel(shape, sources);
                    shape = personnel;
                    rows.addAll(personnel.rows);
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
            sectionProgress.accept(section + 1);
        }
        return result.stripTrailing() + "\n";
    }
    private static String label(String text) {
        return text.replaceAll("[\\s*_]", "");
    }
    private static LinkedHashMap<String, Integer> columns(Table table) throws IOException {
        LinkedHashMap<String, Integer> columns = new LinkedHashMap<>();
        for (int c = 0; c < table.header.size(); c++) {
            String key = label(table.header.get(c));
            if (key.isEmpty() || columns.putIfAbsent(key, c) != null)
                throw new IOException("인력 운용 표에 비어 있거나 중복된 열 이름이 있습니다: " + table.header.get(c));
        }
        return columns;
    }
    private static Table mergePersonnel(Table shape, List<Table> sources) throws IOException {
        List<Table> tables = new ArrayList<>();
        tables.add(shape); tables.addAll(sources);
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        for (Table t : tables) {
            columns(t); // Reject ambiguous headers before matching columns by name.
            if (!label(t.header.get(0)).equals(label(shape.header.get(0))))
                throw new IOException("인력 운용 표의 첫 열 이름이 다릅니다: " + t.header.get(0));
            for (String header : t.header) headers.putIfAbsent(label(header), header);
            Set<String> seen = new HashSet<>();
            for (List<String> row : t.rows) {
                String key = label(row.get(0));
                if (key.isEmpty() && row.stream().allMatch(String::isBlank)) continue;
                if (key.isEmpty() || !seen.add(key))
                    throw new IOException("인력 운용 표에 비어 있거나 중복된 항목이 있습니다: " + row.get(0));
                labels.putIfAbsent(key, row.get(0));
            }
        }
        List<String> keys = new ArrayList<>(headers.keySet());
        List<Integer> counts = new ArrayList<>();
        for (int c = 1; c < keys.size(); c++) {
            String key = keys.get(c);
            if (key.endsWith("직") || key.equals("인원")) counts.add(c);
        }
        int totalColumn = keys.indexOf("합계");
        if (totalColumn >= 0 && counts.isEmpty())
            throw new IOException("인력 운용 표에 합산할 인원 열이 없습니다.");
        if (totalColumn >= 0) labels.putIfAbsent("합계", "합계");
        LinkedHashMap<String, List<String>> merged = new LinkedHashMap<>();
        for (var entry : labels.entrySet()) {
            List<String> row = new ArrayList<>(Collections.nCopies(keys.size(), ""));
            row.set(0, entry.getValue()); merged.put(entry.getKey(), row);
        }
        for (Table source : sources) {
            Map<String, Integer> sourceColumns = columns(source);
            for (List<String> input : source.rows) {
                String key = label(input.get(0));
                if (key.isEmpty()) continue;
                List<String> row = merged.get(key);
                for (int c = 1; c < keys.size(); c++) {
                    Integer sourceColumn = sourceColumns.get(keys.get(c));
                    if (sourceColumn == null || c == totalColumn || (key.equals("합계") && counts.contains(c))) continue;
                    String value = input.get(sourceColumn);
                    if (counts.contains(c)) {
                        if (!value.isBlank()) row.set(c, number(row.get(c), input.get(0), keys.get(c))
                                .add(number(value, input.get(0), keys.get(c))).stripTrailingZeros().toPlainString());
                    } else row.set(c, add(row.get(c), value));
                }
            }
        }
        for (var entry : merged.entrySet()) {
            if (entry.getKey().equals("합계") || totalColumn < 0) continue;
            BigDecimal sum = BigDecimal.ZERO;
            for (int c : counts) sum = sum.add(number(entry.getValue().get(c), entry.getKey(), keys.get(c)));
            entry.getValue().set(totalColumn, formatTotal(sum));
        }
        List<String> total = merged.remove("합계");
        if (total != null) {
            List<Integer> summedColumns = new ArrayList<>(counts);
            if (totalColumn >= 0) summedColumns.add(totalColumn);
            for (int c : summedColumns) {
                BigDecimal sum = BigDecimal.ZERO;
                for (var entry : merged.entrySet()) sum = sum.add(number(entry.getValue().get(c), entry.getKey(), keys.get(c)));
                total.set(c, formatTotal(sum));
            }
            merged.put("합계", total);
        }
        String separator = keys.size() == shape.header.size() ? shape.separator
                : render(Collections.nCopies(keys.size(), "---"));
        return new Table(shape.start, shape.end, new ArrayList<>(headers.values()), separator, new ArrayList<>(merged.values()));
    }
    private static String formatTotal(BigDecimal value) {
        return value.signum() == 0 ? "" : value.stripTrailingZeros().toPlainString();
    }
    private static BigDecimal number(String value, String row, String column) throws IOException {
        if (value.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(value.replace(",", "")); }
        catch (NumberFormatException e) { throw new IOException("인력 운용 표의 인원은 숫자여야 합니다: " + row + " / " + column + " = " + value, e); }
    }
    private static String numberWorkHeadings(String body) {
        List<String> lines = new ArrayList<>(Arrays.asList(body.split("\n", -1)));
        int number = 1, fenceLength = 0;
        char fenceChar = 0;
        Pattern fence = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
        Pattern heading = Pattern.compile("^####[ \\t]+(.+)$");
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher marker = fence.matcher(line);
            if (fenceLength > 0) {
                if (marker.matches() && marker.group(1).charAt(0) == fenceChar
                        && marker.group(1).length() >= fenceLength && marker.group(2).isBlank())
                    fenceLength = 0;
                continue;
            }
            if (marker.matches()) {
                fenceChar = marker.group(1).charAt(0);
                fenceLength = marker.group(1).length();
                continue;
            }
            Matcher title = heading.matcher(line);
            if (title.matches()) {
                String text = title.group(1).replaceFirst("^(?:\\d+\\.)+\\d*\\.?\\s*", "");
                lines.set(i, "#### 4." + number++ + ". " + text);
            }
        }
        return String.join("\n", lines);
    }
    static String teamName(String fileName) throws IOException {
        String name = fileName.replaceFirst("(?i)\\.md$", "").strip();
        // The organization prefix ends at 본부; keep spaces within names such as D-Inno 개발팀.
        int division = name.lastIndexOf("본부");
        if (division >= 0) name = name.substring(division + "본부".length());
        else {
            int meeting = name.lastIndexOf("경영전략회의");
            if (meeting >= 0) name = name.substring(meeting + "경영전략회의".length());
            else name = name.replaceFirst("^(?:\\d+[_\\s-]+)?\\d{8}[_\\s-]+", "");
        }
        name = name.replaceFirst("^[_\\s-]+", "").strip();
        if (name.isEmpty() || !name.endsWith("팀"))
            throw new IOException("파일명에서 팀 이름을 확인할 수 없습니다: " + fileName + " (예: 1_20261113_경영전략회의_SD본부 개발팀.md)");
        return name;
    }
    private static String teamSection(String team, String content) {
        String body = content.strip();
        // Replace an existing leading team label with the authoritative filename label.
        body = body.replaceFirst("^###\\s*\\[[^\\]\\r\\n]+\\]\\s*(?:\\n|$)", "").strip();
        List<String> lines = new ArrayList<>(Arrays.asList(body.split("\\n", -1)));
        boolean fenced = false, hasThirdLevel = false;
        for (String line : lines) {
            if (line.stripLeading().startsWith("```") || line.stripLeading().startsWith("~~~")) fenced = !fenced;
            if (!fenced && line.matches("^###\\s+.*")) hasThirdLevel = true;
        }
        if (hasThirdLevel) {
            fenced = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.stripLeading().startsWith("```") || line.stripLeading().startsWith("~~~")) fenced = !fenced;
                Matcher heading = HEADING.matcher(line);
                if (!fenced && heading.matches() && heading.group(1).length() >= 3 && heading.group(1).length() < 6)
                    lines.set(i, "#" + line);
            }
        }
        return "### [" + team + "]\n\n" + String.join("\n", lines);
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
