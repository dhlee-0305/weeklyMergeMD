import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportMergerTest {
    @TempDir Path temporary;
    private final ReportMerger merger = new ReportMerger(Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Seoul")));
    @Test void actualReportsAreMergedAndPersonnelTotalsAreRecalculated() throws Exception {
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
        assertTrue(result.contains("| 합계 | 24 | 5 | 29 |"));
        assertEquals(2, result.lines().filter(l -> l.contains("| 계약  완료 |")).count());
        assertTrue(result.contains("|  | LGU+/<br /> 메시징플랫폼개발팀 |"));
        assertTrue(result.contains("|  | kt기업사업팀 |"));
        assertTrue(result.indexOf("### [오픈서비스사업팀]") < result.indexOf("#### 4.9. 2025 경찰견문관리시스템"));
        assertEquals(1, result.lines().filter(l -> l.equals("### [오픈서비스사업팀]")).count());
        assertEquals(1, result.lines().filter(l -> l.equals("### [D-Inno 개발팀]")).count());
        assertFalse(result.contains("### [SD본부"));
        assertTrue(result.indexOf("### [D-Inno 개발팀]") < result.indexOf("#### 4.9. 2025 경찰견문관리시스템"));
        assertTrue(result.contains("#### 4.1. 신세계푸드 웹 운영유지"));
        assertTrue(result.contains("#### 4.10. KT 디지털 채널 EOS 대응을 위한 분석설계 검증 사업"));
        assertEquals(10, result.lines().filter(l -> l.startsWith("#### ")).count());
        assertEquals(0, progress.get(0));
        assertTrue(progress.stream().anyMatch(n -> n > 40 && n < 90), "병합 단계에서도 진행률을 갱신해야 합니다.");
        for (int i = 1; i < progress.size(); i++) {
            assertTrue(progress.get(i) >= progress.get(i - 1));
            assertTrue(progress.get(i) <= 100);
        }
        assertEquals(100, progress.get(progress.size() - 1));
        List<Integer> failedProgress = new ArrayList<>();
        assertThrows(FileAlreadyExistsException.class, () -> merger.merge(inputs, temporary, false, (n, name) -> failedProgress.add(n)));
        assertFalse(failedProgress.contains(100), "저장 실패 시 완료로 표시하면 안 됩니다.");
        assertEquals(result, Files.readString(output));
        assertEquals(output, merger.merge(inputs, temporary, true, (n, name) -> {}));
    }
    @Test void extractsOnlyTeamFromFileName() throws Exception {
        assertEquals("오픈서비스사업팀", ReportMerger.teamName("3_20261106_경영전략회의_SD본부 오픈서비스사업팀.md"));
        assertEquals("D-Inno 개발팀", ReportMerger.teamName("4_20261106_경영전략회의_SD본부 D-Inno 개발팀.md"));
        assertEquals("개발팀", ReportMerger.teamName("1_20261113_경영전략회의_개발팀.md"));
        assertEquals("개발팀", ReportMerger.teamName("20261113_개발팀.MD"));
        assertThrows(java.io.IOException.class, () -> ReportMerger.teamName("20261113_SD본부.md"));
    }
    @Test void filenameOverridesExistingLabelAndPreservesNestedContent() throws Exception {
        Files.writeString(temporary.resolve(AppConfig.TEMPLATE), template());
        Path source = temporary.resolve("1_20261113_경영전략회의_SD본부 새개발팀.md");
        Files.writeString(source, "## 4. 주요 업무 사항\n\n### [이전팀]\n\n### 업무 A\n\n#### 세부 업무\n\n```markdown\n### 예제 코드\n```\n");
        Path output = merger.merge(List.of(source), temporary, false, (n, name) -> {});
        String result = Files.readString(output);
        assertTrue(result.contains("### [새개발팀]\n\n#### 4.1. 업무 A"));
        assertTrue(result.contains("##### 세부 업무"));
        assertTrue(result.contains("```markdown\n### 예제 코드\n```"));
        assertFalse(result.contains("[이전팀]"));
    }
    @Test void workNumberingContinuesAcrossTeamsAndSkipsCodeAndOtherLevels() throws Exception {
        String first = "## 1. 프로젝트 진행 현황\n#### 9.9. 다른 영역\n## 4. 주요 업무 사항\n### [개발팀]\n#### 4.8. 첫 업무\n##### 상세 제목\n```markdown\n#### 4.90. 코드\n```\n";
        String second = "## 4. 주요 업무 사항\n### [운영팀]\n#### 2025년 사업\n~~~~markdown\n#### 코드 내부\n```\n#### 여전히 코드\n~~~~\n#### 1. 마지막 업무\n";
        String result = merger.mergeText(template(), List.of(first, second));
        assertTrue(result.contains("#### 4.1. 첫 업무"));
        assertTrue(result.contains("### [운영팀]\n#### 4.2. 2025년 사업"));
        assertTrue(result.contains("#### 4.3. 마지막 업무"));
        assertTrue(result.contains("##### 상세 제목"));
        assertTrue(result.contains("#### 4.90. 코드"));
        assertTrue(result.contains("#### 코드 내부\n```\n#### 여전히 코드"));
        assertEquals(result, merger.mergeText(template(), List.of(result)));
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
        String source = "## 인력 운용 현황\n| 구분 | 인원 |\n| -- | -- |\n| 개발 | 2 | 잘못된 열 |\n";
        assertThrows(java.io.IOException.class, () -> merger.mergeText(template(), List.of(source)));
        assertThrows(java.io.IOException.class, () -> merger.mergeText("# 빈 템플릿", List.of()));
    }
    private String personnelTemplate(String table) {
        return template().replace("| 구분 | 인원 | 사유 |\n| -- | -- | -- |\n| 개발 | | |", table.strip());
    }
    @Test void personnelTotalsIgnoreSourceTotalsAndIncludeNewRowsAndColumns() throws Exception {
        String shape = personnelTemplate("""
                | 구분 | 정규직 | 계약직 | 합계 | 증감 | 증감 사유 |
                | -- | -- | -- | -- | -- | -- |
                | 개발 | | | | | |
                | 합계 | 999 | 999 | 999 | | |
                """);
        String first = """
                ## 인력 운용 현황
                | 구분 | 계약직 | 합계 | 정규직 | 증감 사유 | 증감 |
                | -- | -- | -- | -- | -- | -- |
                | 합계 | 틀린 합계 | 999 | 999 | | |
                | QA | 2 | 888 | 3 | 충원 | 1 |
                | 개발 | 1 | 777 | 10 | 이동 | -1 |
                """;
        String second = """
                ## 인력 운용 현황
                | 구분 | 정규직 | 계약직 | 파견직 | 합계 | 증감 | 증감 사유 |
                | -- | -- | -- | -- | -- | -- | -- |
                | 개발 | 4 | | 2 | 999 | 2 | 복귀 |
                | QA | 1 | 3 | | 999 | | |
                | 보안 | 2 | | 1 | 999 | | |
                """;
        String result = merger.mergeText(shape, List.of(first, second));
        assertTrue(result.contains("| 개발 | 14 | 1 | 17 | 1 | 이동<br />복귀 | 2 |"));
        assertTrue(result.contains("| QA | 4 | 5 | 9 | 1 | 충원 |  |"));
        assertTrue(result.contains("| 보안 | 2 |  | 3 |  |  | 1 |"));
        assertTrue(result.contains("| 합계 | 20 | 6 | 29 |  |  | 3 |"));
        assertTrue(result.indexOf("\n| 보안 |") < result.indexOf("\n| 합계 |"));
        assertFalse(result.contains("999"));
        assertEquals(result, merger.mergeText(shape, List.of(result)));
    }
    @Test void personnelTotalsHandleBlankCommaDecimalAndMissingTotalRow() throws Exception {
        String shape = personnelTemplate("""
                | 구분 | 합계 | 계약직 | 정규직 |
                | -- | -- | -- | -- |
                | 개발 | | | |
                | 기획 | | | |
                """);
        String source = """
                ## 인력 운용 현황
                | 구분 | 정규직 | 계약직 |
                | -- | -- | -- |
                | 개발 | 1,000 | -2.5 |
                | 유휴 인력(미투입) | | 2.5 |
                """;
        String result = merger.mergeText(shape, List.of(source));
        assertTrue(result.contains("| 개발 | 997.5 | -2.5 | 1000 |"));
        assertTrue(result.contains("| 기획 |  |  |  |"));
        assertTrue(result.contains("| 유휴 인력(미투입) | 2.5 | 2.5 |  |"));
        assertTrue(result.contains("| 합계 | 1000 |  | 1000 |"));
    }
    @Test void zeroTotalsAreBlankForEmptyAndOffsettingCounts() throws Exception {
        String shape = personnelTemplate("""
                | 구분 | 정규직 | 계약직 | 합계 |
                | -- | -- | -- | -- |
                | 개발 | | | |
                | 합계 | | | |
                """);
        String empty = merger.mergeText(shape, List.of());
        assertTrue(empty.contains("| 개발 |  |  |  |"));
        assertTrue(empty.contains("| 합계 |  |  |  |"));
        String source = """
                ## 인력 운용 현황
                | 구분 | 정규직 | 계약직 | 합계 |
                | -- | -- | -- | -- |
                | 개발 | 2.5 | -2.5 | 99 |
                | 기획 | -2.5 | 2.5 | 99 |
                """;
        String result = merger.mergeText(shape, List.of(source));
        assertTrue(result.contains("| 개발 | 2.5 | -2.5 |  |"));
        assertTrue(result.contains("| 기획 | -2.5 | 2.5 |  |"));
        assertTrue(result.contains("| 합계 |  |  |  |"));
    }
    @Test void personnelRejectsAmbiguousLabelsAndInvalidCounts() {
        String shape = personnelTemplate("""
                | 구분 | 정규직 | 계약직 | 합계 |
                | -- | -- | -- | -- |
                | 개발 | | | |
                | 합계 | | | |
                """);
        for (String source : List.of(
                "| 구분 | 정규직 | 계약직 |\n| -- | -- | -- |\n| 개발 | 미정 | |",
                "| 구분 | 정규직 | 계약직 |\n| -- | -- | -- |\n| 개발 | 1 | |\n| 개 발 | 2 | |",
                "| 구분 | 정규직 | 정규직 |\n| -- | -- | -- |\n| 개발 | 1 | 2 |")) {
            assertThrows(java.io.IOException.class, () -> merger.mergeText(shape, List.of("## 인력 운용 현황\n" + source)));
        }
    }
    @Test void weekendsUseFridayOfSameWeek() {
        ReportMerger sunday = new ReportMerger(Clock.fixed(Instant.parse("2026-11-15T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        assertEquals("20261113_IT서비스부문_주간보고.md", sunday.resultPath(temporary).getFileName().toString());
    }
}
