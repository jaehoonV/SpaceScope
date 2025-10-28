# 📘 Build Guide — SpaceScope

## 🧩 개요
이 문서는 **SpaceScope** 애플리케이션을 빌드하고  
`jpackage`를 이용해 **실행 가능한 앱 이미지(app-image)**를 생성하는 방법을 안내합니다.

SpaceScope는 **Java 17 기반**의 데스크톱 GUI 프로그램이며,  
폴더 용량을 분석하고 트리 구조로 시각화한 결과를 CSV로 내보내는 기능을 제공합니다.  
본 문서는 개발자 또는 유지보수 담당자가 **로컬에서 빌드 환경을 재현**할 수 있도록 작성되었습니다.

---

## 🧱 1. 사전 준비

| 항목 | 설명 |
|------|------|
| **JDK** | Oracle JDK 또는 OpenJDK **17 버전** (필수) |
| **jpackage** | JDK 14 이상에 기본 포함됨 (JDK 17에 포함) |
| **IDE** | IntelliJ IDEA 또는 Eclipse (선택) |
| **운영체제** | Windows 10 이상 (64비트) |

### 🗂️ 빌드 전 준비 사항
1. IntelliJ IDEA에서 `Build → Build Artifacts → Build`를 실행하여  
   `out/artifacts/SpaceScope_jar/SpaceScope.jar` 파일을 생성합니다.
2. 아이콘 파일(`icon.ico`)은 `docs/image/` 폴더 내에 위치시킵니다.
3. 프로젝트 루트에서 아래 `jpackage` 명령어를 실행합니다.

---

## ⚙️ 2. jpackage를 이용한 앱 이미지 생성

다음 명령어를 **프로젝트 루트 경로**에서 실행합니다:

```bash
"C:\Program Files\Java\jdk-17\bin\jpackage.exe" ^
  --input "out\artifacts\SpaceScope_jar" ^
  --main-jar "SpaceScope.jar" ^
  --main-class "FolderSizeExporter.FolderSizeExporterGUI" ^
  --name "SpaceScope" ^
  --type app-image ^
  --dest "exe\SpaceScope" ^
  --icon "docs\image\icon.ico" ^
  --java-options "--enable-native-access=ALL-UNNAMED"
```

### 🔍 주요 옵션 설명

| 옵션 | 설명 |
|------|------|
| `--input` | JAR 파일이 위치한 폴더 경로 |
| `--main-jar` | 실행 진입점이 포함된 JAR 파일명 |
| `--main-class` | `public static void main()`이 포함된 클래스 경로 |
| `--name` | 생성될 애플리케이션 이름 |
| `--type` | 출력 형식 (`app-image`, `exe`, `msi` 등) |
| `--dest` | 결과물 출력 폴더 |
| `--icon` | 앱 아이콘 파일 경로 (`.ico`, `.icns`, `.png` 지원) |
| `--java-options` | 런타임 JVM 옵션 (예: 네이티브 접근 허용 등) |

> 💡 `--type app-image`는 단독 실행 가능한 앱 폴더 구조를 생성합니다.  
> (`.exe` 빌드는 WiX Toolset이 필요하지만 본 문서에서는 사용하지 않습니다.)

---

## 🧩 3. 결과 예시

명령 실행 후, `exe/SpaceScope/` 폴더 내부에 다음과 같은 구조가 생성됩니다.

```
exe/
└── SpaceScope/
    ├── SpaceScope/
    │   ├── app/
    │   ├── runtime/
    │   └── SpaceScope.exe
    └── SpaceScope.cfg
```

> `SpaceScope.exe`를 직접 실행하면 프로그램이 바로 구동됩니다.

---

## 📚 4. 참고

- **jpackage 공식 문서**  
  🔗 https://docs.oracle.com/en/java/javase/17/docs/specs/man/jpackage.html
- **OpenJDK 17 다운로드**  
  🔗 https://jdk.java.net/17/
- **SpaceScope 프로젝트 경로 예시**
  ```
  SpaceScope/
  ├── out/artifacts/SpaceScope_jar/SpaceScope.jar
  ├── exe/SpaceScope/
  ├── docs/image/icon.ico
  └── docs/build_guide.md
  ```
