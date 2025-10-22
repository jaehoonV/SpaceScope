# 📁 SpaceScope

**SpaceScope**는 폴더 용량을 시각적으로 분석하고,  
트리 형태로 구조화된 결과를 CSV 파일로 내보내는 Java 기반 GUI 프로그램

<img src="docs/Image/image1.png" alt="SpaceScope Screenshot" width="600"/>


---

## 🚀 주요 기능

- 📂 **폴더 용량 분석** – 모든 하위 폴더 포함
- 🌲 **트리 구조 시각화** – 계층적 폴더 구조 표시
- 📊 **진행률 표시** – 분석 중 실시간 진행 상태 갱신
- 💾 **CSV 내보내기** – UTF-8 with BOM 형식으로 저장
- 🎨 **모던 다크 테마 UI** – [FlatLaf](https://www.formdev.com/flatlaf/) 기반

---

## 🧩 기술 스택

| 분류 | 사용 기술 |
|------|------------|
| Language | Java 17 |
| UI | Swing + FlatLaf |
| Concurrency | ForkJoinPool (병렬 폴더 크기 계산) |
| Export | CSV |
| Build | IntelliJ IDEA / Maven / Gradle |

---

## 🖥️ 실행 방법

### ✅ JAR 실행
```bash
java -jar SpaceScope.jar
