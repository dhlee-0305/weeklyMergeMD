# WeeklyMergeMD

주간 보고서 `.md` 파일 여러 개를 선택해서 하나의 통합 문서로 병합하는 Java Swing 프로그램입니다.

팀별 주간 보고서를 공통 템플릿 기준으로 불러와 다음 영역을 취합합니다.

- 프로젝트 진행 현황
- 인력 운용 현황
- 거래처 영업/동향 정보
- 주요 업무 사항

사용자는 화면에서 병합할 파일을 선택하고, 지정된 출력 폴더에 통합 문서를 생성할 수 있습니다.



## Windows에서 바로 실행

1. Java 17 이상을 설치합니다. 실행에 Maven은 필요하지 않습니다.
2. `dist/weeklyMergeMD-windows.zip`을 원하는 폴더에 압축 해제합니다.
3. `run-windows.bat`을 더블클릭합니다.
4. 파일을 선택하고 `Merge Reports`를 누릅니다. 여러 파일은 Ctrl 키를 누른 채 원하는 순서로 선택합니다.

배포본의 기본 경로는 `C:/Downloads/weekly_report_md/`와 그 아래 `output/`입니다.
경로를 바꾸려면 배포본의 `app.properties`를 수정합니다. 한글 경로도 UTF-8로 저장하면 됩니다.
같은 날짜의 결과가 이미 있으면 덮어쓰기 여부를 확인합니다.

실제 제공된 `output/주간 경영전략회의_template.md`도 지원합니다.
두 템플릿이 모두 있으면 `경영전략회의_template_java.md`를 우선 사용합니다.
원본은 수정하지 않으며, 원본 인력 합계의 오류를 자동 보정하지 않고 셀별로 합산합니다.
이번 주는 월요일부터 일요일까지로 계산합니다. 토요일과 일요일에도 같은 주 금요일을 사용합니다.

## 주요 기능

- `report.doc.path` 폴더의 `.md` 파일 목록 표시
- 템플릿 파일과 이전 병합 결과 파일(`YYYYMMDD_*.md`)을 입력 목록에서 제외
- 선택한 파일이 있으면 선택 순서대로 병합
- 선택한 파일이 없으면 목록에 표시된 모든 원본 파일을 정렬 순서대로 병합
- 병합 진행률과 현재 처리 단계를 대화상자로 표시
- 템플릿의 `[취합일]` 문구를 이번 주 금요일 날짜로 치환
- 결과 파일을 `YYYYMMDD_IT서비스부문_주간보고.md` 형식으로 생성



## 기술 스택

- Java 17
- Maven
- Java Swing
- Apache POI `poi-ooxml 5.4.1`
- Log4j `2.24.3`



## 개발 환경 설정

### 1. 필수 프로그램 설치

아래 도구가 설치되어 있어야 합니다.

- JDK 17
- Maven 3.9 이상 권장

버전 확인 예시:

```powershell
java -version
mvn -version
```

### 2. 프로젝트 준비

프로젝트 루트에서 Maven이 동작하는지 확인합니다.

```powershell
mvn clean compile
```

### 3. 환경 설정 파일 준비

프로그램은 `app.properties`에서 입출력 경로를 읽습니다.
설정 파일은 아래 순서로 탐색합니다.

1. 현재 실행 폴더의 `app.properties`
2. 실행 중인 JAR 또는 클래스 파일이 있는 폴더의 `app.properties`
3. JAR 내부에 포함된 기본 `app.properties`

```properties
report.doc.path=C:/Downloads/weekly_report_md/
report.doc.out.path=C:/Downloads/weekly_report_md/output/
```

설명:

- `report.doc.path`: 병합할 원본 보고서 `.md` 파일들이 있는 폴더
- `report.doc.out.path`: 통합 결과 파일을 생성하고 템플릿 파일을 읽는 폴더

`src/main/resources/app.properties`는 빌드 산출물에 포함되는 기본 예시 파일입니다.
실제 실행 환경의 경로는 JAR 옆이나 실행 폴더에 별도 `app.properties`를 두어 관리하는 방식을 권장합니다.

설정값이 비어 있거나 절대 경로가 아니면 기본 경로를 사용합니다.

- 원본 폴더 기본값: 현재 작업 폴더
- 출력 폴더 기본값: 현재 작업 폴더의 `output` 폴더

### 4. 메시지 파일 준비

화면 문구와 오류 메시지는 `src/main/resources/messages.properties`에 정의합니다.
이 파일은 Maven 빌드 시 JAR 내부에 포함되므로, 기본 배포에는 별도 `messages.properties`가 필요하지 않습니다.

배포 후 문구만 바꾸고 싶다면 `weeklyMergeMD.jar`와 같은 폴더에 `messages.properties`를 따로 둘 수 있습니다.
실행 폴더의 파일이 JAR 내부 기본 메시지보다 우선 적용됩니다.

### 5. 템플릿 파일 준비

출력 폴더인 `report.doc.out.path` 안에 아래 템플릿 파일이 있어야 합니다.

```text
{report.doc.out.path}\경영전략회의_template_java.md
```

생성 결과 파일 이름은 아래 형식입니다.

```text
YYYYMMDD_IT서비스부문_주간보고.md
```



## 프로그램 실행 방법

### 1. 개발 중 실행

프로젝트 루트에서 아래 명령으로 실행합니다.

```powershell
mvn clean compile exec:java
```

### 2. 배포용 실행 파일 생성

아래 명령으로 모든 의존 라이브러리가 포함된 단일 JAR 파일을 생성합니다.

```powershell
mvn clean package
```

빌드가 완료되면 `target/weeklyMergeMD.jar` 파일이 생성됩니다.

`src/main/resources` 아래의 리소스 파일은 JAR 내부에 함께 포함됩니다.
또한 Windows에서 패키징하면 `src/main/resources/app.properties`의 `report.doc.path` 값이 가리키는 폴더로 `weeklyMergeMD.jar`가 복사됩니다.

실행 환경별 문서 경로를 지정하려면 생성된 JAR 파일과 `app.properties`를 같은 폴더에 두고 아래 명령으로 실행합니다.

```powershell
java -jar weeklyMergeMD.jar
```

JAR에 리소스가 포함되었는지 확인하려면 아래 명령을 사용할 수 있습니다.

```powershell
jar tf target\weeklyMergeMD.jar | findstr "messages.properties app.properties"
```

### 3. 실행 후 동작 방식

1. `report.doc.path` 폴더의 `.md` 파일 목록을 화면에 표시합니다.
2. 병합할 파일을 선택합니다. 선택하지 않으면 표시된 모든 파일을 병합합니다.
3. `Merge Reports` 버튼을 클릭합니다.
4. 통합 문서가 `report.doc.out.path` 폴더에 생성됩니다.



## 병합 규칙

- 프로젝트 진행 현황, 거래처 영업/동향 정보는 원본 문서의 표 데이터를 이어 붙입니다.
- 인력 운용 현황은 같은 위치의 숫자 값을 합산하고, 숫자가 아닌 값은 줄바꿈으로 연결합니다.
- 거래처 영업/동향 정보는 템플릿의 첫 번째 열 값을 유지하면서 나머지 열을 원본 데이터로 채웁니다.
- 주요 업무 사항은 원본 문서의 해당 영역을 결과 문서 끝에 추가합니다. 각 문서 앞에 파일명에서 추출한 `### [팀이름]` 제목을 붙이며, 본부명은 제외합니다.
- 예: `4_20261106_경영전략회의_SD본부 D-Inno 개발팀.md` → `### [D-Inno 개발팀]`. 기존 팀 제목은 교체하고, 업무 제목은 팀 제목 아래 단계로 배치합니다. 팀 이름을 추출할 수 없으면 파일명 확인 오류를 표시합니다.
- 주요 업무 사항의 H4(`####`) 제목은 팀 구분과 관계없이 취합 순서대로 `4.1.`, `4.2.`, …를 붙입니다. 기존 제목 번호는 교체하며 코드 블록과 다른 단계의 제목은 번호를 변경하지 않습니다.
- 원본 문서에 특정 영역이 없으면 해당 문서는 그 영역 병합에서 제외됩니다.



## 프로젝트 구조

```text
weeklyMergeMD
├─ src
│  └─ main
│     ├─ java : 소스 코드
│     │  ├─ AppConfig.java
│     │  ├─ Messages.java
│     │  ├─ WeeklyMergeMDApp.java
│     │  └─ ReportMerger.java
│     └─ resources : 설정 파일
│        ├─ app.properties
│        └─ messages.properties
├─ pom.xml
└─ README.md
```



## 검증

```powershell
mvn test
```

제공된 원본 두 개와 템플릿을 `src/test/resources`에 보관하여 실제 표 합산, 원본 순서,
날짜 치환, 파일 필터링, 텍스트 연결, 잘못된 표 거부 및 덮어쓰기 보호를 검증합니다.
원본 표의 열 수가 템플릿과 다르거나 인력 행 구분/순서가 다르면 오류를 표시합니다.

## 참고 사항

- 실행 폴더의 `app.properties`는 로컬 환경 설정 파일이므로 필요한 경우 배포 환경별로 별도 관리합니다.
- `messages.properties`는 JAR 내부 기본 메시지로 포함되며, 실행 폴더에 같은 이름의 파일을 두면 문구를 덮어쓸 수 있습니다.
- 원본 문서와 템플릿 문서의 표 구조가 다르면 병합 결과가 예상과 다를 수 있습니다.
- `report.doc.out.path` 폴더가 없거나 템플릿 파일이 없으면 병합을 시작할 수 없습니다.
