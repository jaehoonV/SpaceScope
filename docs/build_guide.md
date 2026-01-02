# 📘 Build Guide — SpaceScope

## 🧩 개요
이 문서는 **SpaceScope** 애플리케이션을  **Maven 프로젝트 기준**으로 빌드하고
`jpackage`를 이용해 **실행 가능한 앱 이미지(app-image)**를 생성하는 방법을 안내합니다.

SpaceScope는 **Java 17 기반**의 데스크톱 GUI 프로그램이며,  
폴더 용량을 분석하고 트리 구조로 시각화한 결과를 **XLSX(Excel)** 형식으로 내보낼 수 있습니다.

본 문서는 개발자 또는 유지보수 담당자가 **로컬에서 동일한 빌드·배포 환경을 재현**할 수 있도록 작성되었습니다.

---

## 🧱 1. 사전 준비

| 항목 | 설명 |
|------|------|
| **JDK** | Oracle JDK 또는 OpenJDK **17 버전** (필수) |
| **Maven** | Apache Maven **3.8 이상 권장** |
| **jpackage** | JDK 14 이상에 기본 포함됨 (JDK 17에 포함) |
| **IDE** | IntelliJ IDEA (권장), Eclipse 등 |
| **운영체제** | Windows 10 이상 (64비트) |

### 🔍 버전 확인
java -version  
mvn -version

---

## 🗂️ 2. 프로젝트 구조 (Maven 표준)

SpaceScope는 Maven 표준 디렉터리 구조를 따릅니다.

```
SpaceScope/
├─ pom.xml
├─ src/
│  └─ main/
│     ├─ java/
│     └─ resources/
├─ target/
│  └─ SpaceScope.jar
├─ exe/
└─ docs/build_guide.md
```

---

## ⚙️ 3. Maven 빌드 (JAR 생성)

### 3-1. 클린 빌드
프로젝트 루트에서 실행합니다.

mvn clean package

### 3-2. 결과물
빌드가 성공하면 다음 파일이 생성됩니다.

target/SpaceScope-2.0.0.jar

이 JAR은 **의존성이 포함된 실행 가능한 JAR**이며  
아래 jpackage 단계에서 그대로 사용됩니다.

### 3-3. 단독 실행 테스트 (권장)

java -jar target/SpaceScope-2.0.0.jar

---

## 📦 4. jpackage를 이용한 앱 이미지 생성

아래 명령어를 **프로젝트 루트 경로**에서 실행합니다.

```
"C:\Program Files\Java\jdk-17\bin\jpackage.exe" ^
--type app-image ^
--name SpaceScope ^
--input target ^
--main-jar SpaceScope-2.0.0.jar ^
--main-class FolderSizeViz.FolderSizeVizApp ^
--dest exe ^
--icon docs\image\icon.ico ^
--java-options "--enable-native-access=ALL-UNNAMED"
```

---

## 🔍 5. jpackage 옵션 설명

| 옵션 | 설명 |
|------|------|
| --type | 출력 형식 (app-image, exe, msi 등) |
| --input | 입력 파일 경로 (Maven 빌드 결과 폴더) |
| --main-jar | 실행 진입점이 포함된 JAR 파일 |
| --main-class | public static void main()을 포함한 클래스 |
| --name | 생성될 애플리케이션 이름 |
| --dest | 결과물 출력 폴더 |
| --icon | 애플리케이션 아이콘 (.ico) |
| --java-options | JVM 런타임 옵션 |

> 💡 app-image 타입은 설치 없이 실행 가능한 앱 폴더를 생성합니다.

---

## 🧩 6. 결과 예시

```
exe/
└── SpaceScope/
    ├── SpaceScope/
    │   ├── app/
    │   ├── runtime/
    │   └── SpaceScope.exe
    └── SpaceScope.cfg
```

- SpaceScope.exe 실행 → 프로그램 즉시 실행
- runtime/ → JRE 포함 (사용자 PC에 Java 설치 불필요)

---

## 🧰 7. Inno Setup 6 연동 (선택)

- exe/SpaceScope/ 폴더 전체를 Inno Setup의 SourceDir로 지정
- 최종 설치 파일(SpaceScope_Setup.exe) 생성 가능
- Maven 전환 여부와 무관하게 동일하게 적용 가능

---

## 📚 8. 참고 자료

- Apache Maven  
  https://maven.apache.org/
- jpackage 공식 문서  
  https://docs.oracle.com/en/java/javase/17/docs/specs/man/jpackage.html
- OpenJDK 17  
  https://jdk.java.net/17/

---

