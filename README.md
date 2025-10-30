# 📁 SpaceScope
<a href="https://github.com/jaehoonV/SpaceScope/releases/latest"><img src="https://img.shields.io/badge/version-1.2.0-blue.svg"/></a>

**SpaceScope**는 폴더 및 파일 용량을 시각적으로 분석하고,  
트리 형태로 구조화된 결과를 CSV 파일로 내보내는 Java 기반 GUI 프로그램입니다.

<img src="docs/Image/image1.png" alt="SpaceScope Screenshot" width="600"/>


---

## 🚀 주요 기능

- 📂 **폴더 용량 분석** – 모든 하위 폴더와 파일 포함
- 🌲 **트리 구조 시각화** – 계층적 폴더/파일 구조 표시
- 📊 **진행률 표시** – 실시간 진행률(%) 및 처리 개수 표시
- 💾 **CSV 내보내기** – UTF-8 with BOM 형식으로 저장
- 🧮 **정렬 기능** – 용량 / 이름 / 수정일 기준 정렬 가능
- 🎨 **모던 다크 테마 UI** – [FlatLaf](https://www.formdev.com/flatlaf/) 기반
- ⚙️ **설치형 프로그램 제공** – JDK 없이 바로 실행 가능 (v1.0.0 이상)
- 🧰 **중단 기능** – 분석 중 언제든 즉시 중단 가능
- 🌐 **다국어 지원** – 한국어 / 영어 전환 가능
- ⚙️ **설정 메뉴** – 언어 선택 및 프로그램 정보 보기 기능

---

## 🧩 기술 스택

| 분류 | 사용 기술 |
|------|------------|
| Language | Java 17 |
| UI | Swing + FlatLaf |
| Concurrency | ForkJoinPool (병렬 폴더 크기 계산) |
| Export | CSV (UTF-8 with BOM) |
| i18n | ResourceBundle + UTF8Control (다국어 처리) |
| Installer | Inno Setup 6 |
| IDE | IntelliJ IDEA |

---

## 🆕 최신 버전 다운로드
[⬇️ Download SpaceScope v1.2.0](https://github.com/jaehoonV/SpaceScope/releases/latest)

> 항상 최신 안정 버전을 설치하세요.  
> 이전 버전은 [GitHub Releases](https://github.com/jaehoonV/SpaceScope/releases) 페이지에서 확인 가능합니다.

---

## 🖥️ 실행 방법

### ✅ 설치형(EXE) 실행 (권장)
> **버전 1.0.0 이상에서는 Java 설치가 필요 없습니다.**

1. `SpaceScope_Installer.exe` 실행
2. 설치 중 원하는 옵션 선택
    - [✔] 바탕화면에 바로가기 만들기
    - [✔] 시작 메뉴에 바로가기 만들기
3. 설치 완료 후 **자동 실행** 또는 **바탕화면 아이콘 더블클릭**

---

## 🧾 CSV 출력 예시
| Type   | Depth | Path                    | Size (bytes) | Formatted Size | Last Modified       |
| ------ | ----- | ----------------------- | ------------ | -------------- | ------------------- |
| Folder | 0     | `C:\Projects`           | 132423245    | 126.28 MB      | 2025-10-28 11:24:13 |
| File   | 1     | `C:\Projects\README.md` | 1244         | 1.21 KB        | 2025-10-27 17:52:00 |

---

## 🆙 버전별 변경 이력 (CHANGELOG)
| 버전         | 날짜         | 주요 변경사항                                                                                                                                                         |
| ---------- | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **v1.2.0** | 2025-10-30 | 🌐 **다국어 처리 (한국어 / 영어 지원)**<br>⚙️ **설정 메뉴 추가** – 언어 선택 및 정보 보기<br>🧵 **EDT 적용** – UI 변경 시 `SwingUtilities.invokeLater()` 적용 |
| **v1.1.0** | 2025-10-28 | ⚡ **폴더 + 파일 단위 분석 기능 추가**<br>📊 **진행 상태 라벨 표시** (`진행 중: 123 / 456 폴더`)<br>💾 **CSV 구조 개선** (`Type` 열 추가)<br>🧭 **중단 버튼 개선** |
| **v1.0.0** | 2025-10-22 | 🚀 첫 정식 릴리스<br>- 설치형 인스톨러 추가 (Inno Setup)<br>- JDK 없이 실행 가능한 독립 환경 구성<br>- CSV 내보내기 기능 개선 |
| **v0.9.0** | 2025-10-20 | 🧩 초기 개발 버전<br>- 폴더 용량 분석 및 트리 구조 출력<br>- CSV 내보내기 기능 추가<br>- FlatLaf 다크 테마 적용 |

---

## 📜 라이선스
이 프로젝트는 [MIT License](LICENSE) 하에 배포됩니다.
