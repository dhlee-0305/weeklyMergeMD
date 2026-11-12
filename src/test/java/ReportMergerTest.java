import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportMergerTest {
    @TempDir Path temporary;
    private final ReportMerger merger = new ReportMerger(Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Seoul")));
    @Test void actualReportsAreMergedAndSourceCellsArePreserved() throws Exception {
        Path fixtures = Path.of("src/test/resources");
        Files.copy(fixtures.resolve("output/" + ReportMerger.ALTERNATE_TEMPLATE), temporary.resolve(ReportMerger.ALTERNATE_TEMPLATE));
        List<Path> inputs = ReportMerger.listReports(fixtures.resolve("reports"));
        List<Integer> progress = new ArrayList<>();
        Path output = merger.merge(inputs, temporary, false, (n, name) -> progress.add(n));
        String result = Files.readString(output);
        assertEquals("20261113_IT서비스부문_주간보고.md", output.getFileName().toString());
        assertTrue(result.contains("IT서비스부문 / 2026-11-13"));
        assertTrue(result.contains("| 팀장/부서장 | 2 |  | 2 |"));
        assertTrue(result.contains("| PM | 5 | 1 | 6 |"));
        assertTrue(result.contains("| 개발 | 17 | 4 | 21 |"));
        assertTrue(result.contains("| 합계 | 22 | 12 | 27 |"));
        assertEquals(2, result.lines().filter(l -> l.contains("| 계약  완료 |")).count());
        assertTrue(result.contains("|  | LGU+/<br /> 메시징플랫폼개발팀 |"));
        assertTrue(result.contains("|  | kt기업사업팀 |"));
        assertTrue(result.indexOf("### [오픈서비스사업팀]") < result.indexOf("### 2025 경찰견문관리시스템"));
        assertEquals(100, progress.get(progress.size() - 1));
        assertThrows(FileAlreadyExistsException.class, () -> merger.merge(inputs, temporary, false, (n, name) -> {}));
        assertEquals(result, Files.readString(output));
        assertEquals(output, merger.merge(inputs, temporary, true, (n, name) -> {}));
    }
    @Test void filteringAndOrdering() throws Exception {
        for (String name : List.of("b.md", "a.MD", "20261113_result.md", "주간_template.md", "note.txt")) Files.writeString(temporary.resolve(name), "");
        Files.createDirectory(temporary.resolve("folder.md"));
        assertEquals(List.of("a.MD", "b.md"), ReportMerger.listReports(temporary).stream().map(p -> p.getFileName().toString()).toList());
    }
    private String template() { return "# 보고서\n## 1. 프로젝트 진행 현황\n\n| 구분 | 내용 |\n| -- | -- |\n| | |\n\n## 2. 인력 운용 현황\n\n| 구분 | 인원 | 사유 |\n| -- | -- | -- |\n| 개발 | | |\n\n## 3. 거래처 영업/동향 정보\n\n| 구분 | 내용 |\n| -- | -- |\n| 회의 | |\n\n## 4. 주요 업무 사항\n"; }
    @Test void textNumbersMissingSectionsAndEscapedPipes() throws Exception {
        String a = "## 인력 운용 현황\n| 구분 | 인원 | 사유 |\n| -- | -- | -- |\n| 개발 | 1,000 | 충원 |\n";
        String b = "## 인력 운용 현황\n| 구분 | 인원 | 사유 |\n| -- | -- | -- |\n| 개발 | -2.5 | 이동 |\n## 거래처 영업/동향 정보\n| 구분 | 내용 |\n| -- | -- |\n| 원본 | A\\|B |\n";
        String merged = merger.mergeText(template(), List.of(a, b));
        assertTrue(merged.contains("| 개발 | 997.5 | 충원<br />이동 |"));
        assertTrue(merged.contains("| 회의 | A\\|B |"));
    }
    @Test void refusesMalformedTables() {
        String source = "## 인력 운용 현황\n| 구분 | 인원 |\n| -- | -- |\n| 개발 | 2 |\n";
        assertThrows(java.io.IOException.class, () -> merger.mergeText(template(), List.of(source)));
        assertThrows(java.io.IOException.class, () -> merger.mergeText("# 빈 템플릿", List.of()));
    }
    @Test void weekendsUseFridayOfSameWeek() {
        ReportMerger sunday = new ReportMerger(Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        assertEquals("20261113_IT서비스부문_주간보고.md", sunday.resultPath(temporary).getFileName().toString());
    }
}
