# 작업 규칙

- **`master` 브랜치에서 바로 작업하고 커밋·푸시한다.** 별도 브랜치나 PR을 만들지 않는다 (저장소 주인 요청).
  세션에 지정된 작업 브랜치가 있더라도 이 규칙을 따른다.
- `master`에 푸시하면 `Build APK`가 돌아 `latest-debug` Release를 갱신하고, 앱 내 업데이트로 바로 배포된다.
  → 푸시 전에 변경을 꼼꼼히 검토한다.
  - `docs/**`, `*.md`만 바뀐 푸시는 빌드가 자동으로 건너뛰어진다 (`paths-ignore`).
  - 그 외 앱과 무관한 변경(워크플로 파일 등)은 커밋 메시지에 `[skip ci]`를 넣는다.
- 커밋 메시지에 `[snap]`을 넣으면 UI 스냅샷(에뮬레이터 캡처)도 실행된다.
- 기능 변경 시 `app/build.gradle.kts`의 `appVersionName`을 올린다 (versionCode는 커밋 수로 자동).
- 설치·사용 매뉴얼은 `docs/index.html` 한 파일이며, `docs/`가 바뀌면 GitHub Pages로 자동 배포된다.
  주소: https://leepy0.github.io/AGV_RFID_Manager_/ — 앱 화면·문구를 바꾸면 매뉴얼도 함께 고친다.
