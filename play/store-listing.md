# Play 스토어 등록 초안 — AGV Tag Reader

- 패키지명: `com.leepy0.agvrfidmanager`
- 개인정보처리방침 URL: https://leepy0.github.io/AGV_RFID_Manager_/privacy.html
- 카테고리: 도구 (Tools)
- 앱/게임: 앱, 무료

## 기본 언어: 한국어 (ko-KR)

**앱 이름** (30자 이내)
```
AGV Tag Reader
```

**간단한 설명** (80자 이내)
```
AGV 바닥 RFID 태그를 NFC로 바로 읽고 쓰는 현장 작업용 앱
```

**자세한 설명** (4000자 이내)
```
AGV Tag Reader는 AGV(무인 운반차) 주행로 바닥에 붙은 RFID 태그를 휴대폰 NFC로 읽고 쓰는 현장 작업용 앱입니다.

■ 읽기
휴대폰 뒷면을 태그에 대면 바로 코드를 읽습니다. 버튼을 누를 필요가 없습니다.

■ 쓰기
코드를 입력하고 [쓰기]를 누른 뒤 태그에 대면 됩니다. 쓴 값을 다시 읽어 맞는지 자동으로 확인합니다.

■ 연속 쓰기
[쓰기]를 길게 누르면 여러 태그에 같은 코드를 차례로 쓸 수 있습니다.

■ 프리셋
자주 쓰는 코드를 저장해 두고 한 번에 불러옵니다.

■ 되돌리기
잘못 썼다면 [되돌리기]를 누르고 같은 태그에 대면 이전 코드로 돌아갑니다.

■ 작업 기록
최근에 읽고 쓴 태그를 시간 순서로 보여 줍니다. 기록을 누르면 그 코드를 불러옵니다.

■ 에러 코드 안내
통신 실패나 시간 초과가 나면 원인과 대처 방법을 앱 안에서 바로 확인할 수 있습니다.

■ 현장에 맞춘 화면
큰 글자와 큰 버튼, 성공·실패 진동과 소리, 라이트·다크 테마, 한국어·영어를 지원합니다.

■ 개인정보
인터넷 권한이 없고 어떤 정보도 수집하거나 외부로 보내지 않습니다.

※ NFC가 있는 안드로이드 휴대폰이 필요합니다. ISO 15693(NFC-V) 방식의 AGV 바닥 태그용입니다.
```

## 영어 (en-US) — 선택

**App name**
```
AGV Tag Reader
```

**Short description**
```
Read and write AGV floor RFID tags instantly with your phone's NFC
```

**Full description**
```
AGV Tag Reader reads and writes the RFID tags on AGV (automated guided vehicle) floor routes using your phone's NFC.

■ Read
Hold the back of your phone to a tag. The code is read automatically.

■ Write
Enter a code, press [Write], and hold your phone to the tag. The app reads the value back to verify it.

■ Continuous write
Long press [Write] to write the same code to several tags one after another.

■ Presets
Save frequently used codes and load them with one tap.

■ Undo
Wrote the wrong code? Press [Undo] and hold the same tag to restore its previous code.

■ Activity log
See recently read and written tags in order. Tap a record to load its code.

■ Error code guide
When a write fails or times out, check the cause and fix right in the app.

■ Built for the field
Large text and buttons, success/error vibration and sound, light/dark themes, English and Korean.

■ Privacy
No internet permission. The app collects nothing and sends nothing.

Requires an Android phone with NFC. Designed for ISO 15693 (NFC-V) AGV floor tags.
```

## 그래픽 (필요한 파일)

| 항목 | 규격 | 상태 |
|---|---|---|
| 앱 아이콘 | 512×512 PNG, 32비트, 1MB 이하 | `graphics/icon-512.png` |
| 그래픽 이미지 | 1024×500 PNG/JPG | `graphics/feature-1024x500.png` |
| 휴대폰 스크린샷 | 2~8장, 짧은 변 320px 이상 | `graphics/screenshot-1~3-*.jpg` |

그래픽 원본은 `graphics/*.html` (런처 아이콘 벡터와 같은 도형). 고치면 Edge 헤드리스로 다시 캡처:
`msedge --headless=new --window-size=1024,500 --screenshot=feature-1024x500.png file:///.../feature.html`

## 앱 콘텐츠 설문 답변

**데이터 보안**
- 앱이 필수 사용자 데이터 유형을 수집하거나 공유하나요? → **아니요**
- (아니요를 고르면 암호화·삭제 요청 질문은 건너뜀)

**광고**: 광고 없음

**앱 액세스 권한**: 모든 기능을 제한 없이 사용 가능 (로그인 없음)

**콘텐츠 등급 (IARC)**
- 카테고리: 유틸리티, 생산성, 커뮤니케이션 또는 기타
- 폭력·성적 콘텐츠·욕설·도박·약물: 모두 아니요
- 사용자 간 소통·콘텐츠 공유: 아니요
- 위치 공유: 아니요
- 디지털 상품 구매: 아니요
- 예상 등급: 전체이용가

**타겟층**: 18세 이상 (업무용)

**뉴스 앱**: 아니요 · **정부 앱**: 아니요 · **금융 기능**: 없음 · **건강**: 없음

## 개인 계정 출시 절차

1. 비공개 테스트 트랙에 AAB 업로드, 테스터 **12명 이상** 등록 (Google 계정 이메일)
2. 테스터가 옵트인 링크로 설치하고 **14일 연속** 참여 유지
3. Play Console > 대시보드 > "프로덕션 액세스 신청" → 설문 작성 후 심사 (보통 7일 이내)
4. 승인되면 프로덕션 트랙으로 출시
