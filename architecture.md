# 2D Camouflage Party Game - Architecture Specification

## 1. 문서 목적

본 문서는 파티 공간에서 실제 종이 캐릭터를 출력해 숨기고 찾는 2D Camouflage Party Game 웹앱의 MVP 아키텍처 명세서다.

이 문서는 Codex가 프로젝트의 전체 구조와 핵심 도메인 규칙을 이해하고 일관된 방식으로 구현하도록 하기 위한 기준 문서로 사용한다.

핵심 사용자 경험은 다음과 같다.

> 방 생성 → 참가 → 역할 배정 → 장소 촬영 → 캐릭터 디자인 → 제출 → Character별 QR 자동 생성 → 양면 인쇄 → 실제 배치 → 탐색 → QR 스캔 → 탈락 → 결과

MVP에서는 기능 확장보다 위 핵심 플로우의 완주를 우선한다.

---

# 2. 서비스 개요

## 2.1 사용자 역할

서비스에는 두 종류의 상위 사용자 역할이 있다.

### HOST

파티 주최자이자 게임 방 관리자.

주요 책임:
- 게임 방 생성
- 게임 설정
- 참가자 관리
- 게임 시작
- 제출 현황 확인
- Character별 QR 생성 결과 확인
- 앞면 Character / 뒷면 QR 양면 인쇄
- 각 Phase 진행
- 게임 종료 및 결과 확인

### PLAYER

파티 참가자.

게임 시작 시 아래 역할 중 하나로 랜덤 배정된다.

#### HIDER

- 실제 파티 공간에서 숨을 위치 선택
- 웹앱 카메라로 배경 촬영
- 사람 모형 선택 및 위장 디자인
- 디자인 제출
- Character 전용 QR 자동 발급
- 출력된 캐릭터를 실제 공간에 부착
- 찾힐 때까지 생존

#### SEEKER

- HIDER 준비 시간 동안 대기
- 탐색 Phase 시작 후 실제 공간에서 캐릭터 탐색
- 캐릭터 발견 시 뒷면 QR 스캔
- QR에 매핑된 HIDER를 탈락 처리

---

# 3. MVP 범위

## 3.1 반드시 구현

### 방

- HOST 게임 방 생성
- 6자리 방 코드 생성
- 참가용 QR 생성
- 방 코드 또는 QR을 통한 PLAYER 입장
- 닉네임 입력
- 대기방 참가자 목록

### 게임 설정

HOST가 설정 가능:
- HIDER 디자인 제한시간
- 실제 캐릭터 배치 제한시간
- SEEK 제한시간
- SEEKER 인원 수

### 역할 배정

- HOST가 게임 시작
- 서버에서 HIDER / SEEKER 랜덤 배정
- 각 PLAYER는 자신의 역할만 확인

### HIDER 디자인

- 모바일 웹 카메라 사용
- 배경 사진 촬영
- 사전 정의된 흰색 2D 사람 모형 중 하나 선택
- 모형 이동
- 확대 / 축소
- 회전
- 브러시
- 지우개
- 스포이드
- Undo
- 최종 디자인 제출
- 제한시간 종료 시 현재 Canvas 결과 자동 제출

### Character별 QR 자동 생성

- Character 제출 시 서버에서 고유 `qrToken` 자동 생성
- 각 Character와 QR은 1:1 매핑
- QR은 서버에서 자동 생성
- QR에는 DB ID, nickname, participantId 등을 직접 포함하지 않음
- QR에는 `qrToken`을 포함하는 URL 또는 식별값만 포함
- 동일 QR Token이 서로 다른 Character에 중복 사용되지 않도록 함

예:

```text
https://service.example.com/c/{qrToken}
```

### HOST 인쇄

- 제출된 HIDER 디자인 목록 조회
- 각 Character별 QR 자동 생성
- 인쇄용 Front / Back 레이아웃 자동 생성
- 앞면: 사용자가 디자인한 Character
- 뒷면: 해당 Character를 식별하는 QR
- 앞면 Character와 뒷면 QR이 정확히 1:1 대응되도록 배치
- 한 페이지에 여러 Character를 배치 가능
- 양면 인쇄 가능한 형태 제공
- 잘라 사용할 수 있도록 Cutting Guide 제공

### 실제 숨기기

- 출력물 배부
- HIDER 캐릭터 배치
- HIDER가 "숨기기 완료" 가능
- HOST가 탐색 시작 가능

### 탐색

- SEEKER용 QR Scanner
- 유효한 Character QR 검증
- 해당 HIDER 탈락
- 중복 스캔 방지
- 발견 결과 화면에 원본 사진 및 숨긴 위치 이미지 표시

### 게임 종료

- 모든 HIDER 발견 시 SEEKER 승리
- 탐색 제한시간 종료까지 HIDER가 1명 이상 생존 시 HIDER 승리
- 결과 화면
- HIDER 생존 시간 순위
- SEEKER별 발견 수

---

## 3.2 MVP에서 제외

다음은 구현하지 않는다.

- AI 기능
- 아이템
- 특수 능력
- 팀전
- 채팅
- 친구 기능
- 랭킹 시스템
- 소셜 로그인
- 결제 시스템
- 여러 종류의 게임 모드
- 자동 이미지 생성
- 자체 프린터 드라이버 개발
- 네이티브 앱

---

# 4. 기술 스택

## 4.1 Backend

- Java 21
- Spring Boot 4.x
- Spring Web MVC
- Spring Data JPA
- Spring Validation
- Spring Security
- Spring WebSocket
- PostgreSQL
- Flyway
- Gradle
- ZXing 또는 동등한 QR 생성 라이브러리

## 4.2 Frontend

MVP는 모바일 WebView 환경을 우선한다.

권장:
- React
- TypeScript
- Vite
- Canvas API 또는 Fabric.js / Konva.js
- getUserMedia / MediaDevices API
- WebSocket 또는 STOMP Client
- QR Scanner Library

프론트엔드는 독립 SPA로 구현하고 Spring Boot는 REST API 및 WebSocket 서버 역할을 맡는다.

## 4.3 Storage

이미지는 DB에 Binary로 저장하지 않는다.

권장:
- 개발: Local File Storage
- 운영: S3 Compatible Object Storage

저장 대상:
- HIDER 원본 장소 사진
- 최종 캐릭터 PNG
- 원본 사진 + 캐릭터 Overlay Preview
- 필요한 경우 인쇄용 생성 파일

DB에는 Object URL 또는 Storage Key만 저장한다.

QR 이미지는 DB에 저장하지 않고 `qrToken`을 기준으로 인쇄 시 동적으로 생성하는 방식을 우선한다.

---

# 5. 전체 시스템 구조

```text
┌─────────────────────────────┐
│        Mobile Web App       │
│ React + TypeScript          │
│                             │
│ Camera / Canvas / QR Scan   │
└──────────────┬──────────────┘
               │
       HTTPS / WebSocket
               │
┌──────────────▼──────────────┐
│      Spring Boot Server     │
│                             │
│ Room                        │
│ Participant                 │
│ Game                        │
│ Character                   │
│ Scan                        │
│ Print                       │
│ QR                          │
│ Realtime                    │
└───────┬──────────┬──────────┘
        │          │
        │          └───────────────┐
        │                          │
┌───────▼────────┐       ┌─────────▼────────┐
│   PostgreSQL   │       │  Object Storage  │
│                │       │                  │
│ Game State     │       │ Photos / PNG     │
│ Participants   │       │ Print Assets     │
│ Characters     │       │                  │
└────────────────┘       └──────────────────┘
```

MVP에서는 Microservice Architecture를 사용하지 않는다.

단일 Spring Boot Application 내부에서 Domain 기준으로 모듈을 분리하는 Modular Monolith 구조를 사용한다.

---

# 6. Backend Package Architecture

```text
src/main/java/com/hackathon/gdg

├── global
│   ├── config
│   ├── exception
│   ├── security
│   ├── websocket
│   └── storage
│
├── room
│   ├── controller
│   ├── service
│   ├── domain
│   ├── repository
│   └── dto
│
├── participant
│   ├── controller
│   ├── service
│   ├── domain
│   ├── repository
│   └── dto
│
├── game
│   ├── controller
│   ├── service
│   ├── domain
│   ├── repository
│   └── dto
│
├── character
│   ├── controller
│   ├── service
│   ├── domain
│   ├── repository
│   └── dto
│
├── scan
│   ├── controller
│   ├── service
│   └── dto
│
├── qr
│   └── service
│
└── print
    ├── controller
    ├── service
    └── dto
```

Controller는 HTTP 요청 및 응답 변환만 담당한다.

Business Rule은 Service / Domain Layer에 둔다.

Repository를 Controller에서 직접 호출하지 않는다.

QR 생성 로직은 `QrService`와 같이 별도 서비스로 분리한다.

---

# 7. 핵심 Domain Model

## 7.1 Room

게임이 이루어지는 하나의 파티 방.

필드 예시:

```text
id
roomCode
name
hostTokenHash
status
createdAt
updatedAt
```

RoomStatus:

```text
WAITING
PLAYING
FINISHED
CLOSED
```

---

## 7.2 Participant

방에 참여한 사람.

```text
id
roomId
nickname
participantTokenHash
type
gameRole
status
joinedAt
```

ParticipantType:

```text
HOST
PLAYER
```

GameRole:

```text
NONE
HIDER
SEEKER
```

ParticipantStatus:

```text
WAITING
ACTIVE
ELIMINATED
SURVIVED
LEFT
```

PLAYER는 회원가입 없이 `participantToken`을 발급받아 세션을 유지한다.

브라우저 LocalStorage에 token을 저장한다.

---

## 7.3 Game

Room에서 실행되는 실제 한 판의 게임.

```text
id
roomId
status
designDurationSeconds
hideDurationSeconds
seekDurationSeconds
seekerCount
designStartedAt
hideStartedAt
seekStartedAt
finishedAt
winner
```

GameStatus:

```text
WAITING
ROLE_ASSIGNED
DESIGNING
PRINTING
HIDING
SEEKING
FINISHED
```

Winner:

```text
HIDER
SEEKER
NONE
```

---

## 7.4 Character

HIDER 한 명이 제작한 실제 숨길 캐릭터.

```text
id
gameId
participantId
templateType

originalPhotoUrl
characterImageUrl
previewImageUrl

positionX
positionY
scale
rotation

qrToken

status
submittedAt
printedAt
foundAt
foundByParticipantId
```

CharacterStatus:

```text
SUBMITTED
PRINTED
HIDDEN
FOUND
SURVIVED
```

중요:
- 각 Game에서 HIDER는 Character 하나만 제출한다.
- `(game_id, participant_id)` Unique Constraint 적용.
- 모든 Character는 하나의 고유 `qrToken`을 가진다.
- `qrToken`은 Character와 1:1 관계를 가진다.
- QR 이미지는 영속화하지 않고 `qrToken`을 기반으로 필요 시 생성한다.

### Character와 QR의 관계

```text
HIDER
  ↓
Character
  ↓
qrToken
  ↓
QR Code
```

즉 다음 관계가 유지되어야 한다.

```text
1 HIDER Character = 1 qrToken = 1 QR Code
```

---

# 8. 게임 State Machine

게임 상태 전이는 서버에서 강제한다.

```text
WAITING
   ↓ HOST starts game
ROLE_ASSIGNED
   ↓
DESIGNING
   ↓ all HIDER submissions received
PRINTING
   ↓ HOST confirms distribution
HIDING
   ↓ hide timer expired AND all HIDERs ready AND HOST starts seek
SEEKING
   ↓
FINISHED
```

허용되지 않은 상태에서 요청이 들어오면 409 Conflict를 반환한다.

예:

```text
SEEKING 상태에서 Character 디자인 수정 → 409
DESIGNING 상태에서 QR Scan → 409
WAITING 상태에서 Print 요청 → 409
```

게임 상태를 Client가 결정해서는 안 된다.

Server가 Source of Truth다.

`ROLE_ASSIGNED`는 역할 배정 결과를 표현하지만 게임 시작 Transaction 안에서 즉시
`DESIGNING`으로 전환한다. 일반 Client가 별도의 Phase로 진행시키지 않는다.

디자인 제한시간이 끝나면 Frontend는 현재 Canvas를 즉시 PNG로 Export하고 기존
Character 제출 API를 정확히 한 번 호출한다. 사용자가 아무것도 그리지 않았다면 현재
Canvas의 흰색 Template가 그대로 제출된다. 별도의 Draft API나 주기적 자동 저장은
구현하지 않는다. MVP에서는 HIDER가 디자인 화면을 유지하고 네트워크에 연결되어
있다는 전제에서 자동 제출을 처리한다.

`HIDING → SEEKING` 전이는 숨기기 제한시간이 지난 뒤 HOST가 명시적으로 요청한다.
서버는 모든 HIDER의 Character가 `HIDDEN`인지 검증하며, 한 명이라도 준비되지 않았다면
409 Conflict를 반환한다. 시간 만료만으로 탐색을 자동 시작하지 않는다.

---

# 9. 주요 User Scenario

## 9.1 HOST

```text
서비스 접속
→ 방 생성
→ 게임 설정
→ 방 코드 / 참가 QR 표시
→ PLAYER 참가 대기
→ 게임 시작
→ 랜덤 역할 배정 확인
→ HIDER 디자인 제출 현황 확인
→ Character별 QR 자동 생성
→ 디자인 완료
→ 인쇄 화면 진입
→ 앞면 Character / 뒷면 QR 양면 인쇄
→ HIDER에게 배부
→ 배치 시간 시작
→ 탐색 시작
→ 실시간 발견 현황 확인
→ 게임 종료
→ 결과 확인
```

---

## 9.2 HIDER

```text
QR / 방 코드 입력
→ 닉네임 입력
→ 대기방 입장
→ 게임 시작
→ HIDER 역할 확인
→ 남은 디자인 시간 확인
→ 파티 공간 이동
→ 카메라로 숨을 위치 촬영
→ 사람 모형 선택
→ 사진 위에 배치
→ 스포이드 / 브러시로 색칠
→ 완성
→ 제출
→ Character 전용 QR 자동 생성
→ 인쇄 대기
→ HOST에게 양면 출력물 수령
→ 앞면 Character 확인
→ 뒷면 QR 확인
→ 실제 장소에 부착
→ 숨기기 완료
→ 탐색 중 대기
→ 발견되면 탈락
→ 찾히지 않으면 생존
→ 결과 확인
```

---

## 9.3 SEEKER

```text
QR / 방 코드 입력
→ 닉네임 입력
→ 대기방 입장
→ 게임 시작
→ SEEKER 역할 확인
→ HIDER 준비 시간 동안 대기
→ 탐색 시작 알림
→ 파티 공간 탐색
→ 캐릭터 발견
→ 캐릭터 제거
→ 뒷면 QR 스캔
→ 서버 검증
→ Character와 HIDER 식별
→ 발견 성공
→ HIDER의 원래 숨긴 사진 확인
→ 탐색 계속
→ 게임 종료
→ 결과 확인
```

---

# 10. REST API 초안

Base URL:

```text
/api/v1
```

## 10.1 Room

### 방 생성

```http
POST /api/v1/rooms
```

Request:

```json
{
  "name": "Birthday Party",
  "designDurationSeconds": 600,
  "hideDurationSeconds": 300,
  "seekDurationSeconds": 1200,
  "seekerCount": 2
}
```

Response:

```json
{
  "roomId": 1,
  "roomCode": "A7K2Q9",
  "hostToken": "...",
  "joinUrl": "...",
  "joinQrUrl": "..."
}
```

### 방 조회

```http
GET /api/v1/rooms/{roomCode}
```

### 방 참가

```http
POST /api/v1/rooms/{roomCode}/participants
```

Request:

```json
{
  "nickname": "재원"
}
```

Response:

```json
{
  "participantId": 10,
  "participantToken": "...",
  "roomId": 1
}
```

### 참가자 목록

```http
GET /api/v1/rooms/{roomId}/participants
```

HOST 전용.

---

# 11. Game API

### 게임 시작

```http
POST /api/v1/rooms/{roomId}/games/start
```

HOST 전용.

서버 동작:

1. 참가자 수 확인
2. seekerCount 검증
3. 랜덤 역할 배정
4. Game 생성
5. 상태 DESIGNING으로 변경
6. designStartedAt 기록
7. WebSocket Event 발행

### 현재 게임 조회

```http
GET /api/v1/games/{gameId}
```

### 숨기기 Phase 시작

```http
POST /api/v1/games/{gameId}/hiding/start
```

HOST 전용.

### 탐색 Phase 시작

```http
POST /api/v1/games/{gameId}/seeking/start
```

HOST 전용.

### 게임 종료

```http
POST /api/v1/games/{gameId}/finish
```

HOST 또는 서버 내부 자동 종료.

---

# 12. Character API

### 이미지 업로드 URL 발급

```http
POST /api/v1/games/{gameId}/characters/upload-url
```

또는 MVP에서는 Multipart Upload 사용 가능.

### Character 제출

```http
POST /api/v1/games/{gameId}/characters
```

Request 예시:

```json
{
  "templateType": "STANDING_01",
  "originalPhotoUrl": "...",
  "characterImageUrl": "...",
  "previewImageUrl": "...",
  "positionX": 0.42,
  "positionY": 0.58,
  "scale": 0.7,
  "rotation": 15
}
```

서버 동작:

1. 현재 사용자가 HIDER인지 검증
2. Game 상태가 DESIGNING인지 검증
3. 해당 Game에서 기존 Character 제출 여부 확인
4. Character 생성
5. Character 전용 Secure Random `qrToken` 생성
6. `qrToken`을 Character와 1:1 매핑
7. Character 저장
8. HOST에게 `DESIGN_SUBMITTED` WebSocket Event 발행

디자인 제한시간이 0이 되면 Frontend는 현재 Canvas 결과로 위 API를 자동 호출한다.
수동 제출과 자동 제출은 같은 API와 검증을 사용한다. 중복 Click이나 Timer Callback
재실행이 발생해도 `(game_id, participant_id)` Unique Constraint와 Service 검증으로
Character 하나만 생성되어야 한다.

QR은 사용자가 직접 만들거나 업로드하지 않는다.

모든 QR은 Backend가 자동 생성하며 Character의 `qrToken`을 기준으로 생성한다.

### 본인 Character 조회

```http
GET /api/v1/games/{gameId}/characters/me
```

### 제출 Character 목록

```http
GET /api/v1/games/{gameId}/characters
```

HOST 전용.

### 숨기기 완료

```http
POST /api/v1/games/{gameId}/characters/{characterId}/hidden
```

HIDER 본인 Character만 가능.

---

# 13. QR 생성 및 Scan Architecture

## 13.1 QR 생성 원칙

모든 HIDER Character는 하나의 고유 QR을 가진다.

QR은 Character 제출 시 Backend에서 자동 생성한다.

QR 내부에는 다음 형태의 URL 또는 식별 정보를 담는다.

```text
https://service.example.com/c/{qrToken}
```

QR 자체에는 다음 정보를 포함하지 않는다.

- participantId
- characterId
- nickname
- gameId
- 사용자 개인정보

조회 흐름:

```text
QR Scan
  ↓
qrToken
  ↓
Character
  ↓
Participant
```

`qrToken`은 UUID 또는 충분한 길이의 Secure Random Token을 사용한다.

QR 이미지 파일 자체는 DB에 저장하지 않는다.

QR 이미지는 인쇄 페이지 생성 시 `qrToken`을 이용해 동적으로 생성한다.

---

## 13.2 QR Character 확인

```http
GET /api/v1/characters/qr/{qrToken}
```

---

## 13.3 발견 처리

```http
POST /api/v1/games/{gameId}/characters/{qrToken}/found
```

서버 검증:

1. 요청자가 해당 게임의 SEEKER인지
2. Game이 SEEKING 상태인지
3. qrToken이 유효한지
4. Character가 해당 Game에 속하는지
5. Character가 이미 FOUND 상태가 아닌지

성공 시:

```text
Character.status = FOUND
Character.foundAt = now
Character.foundByParticipantId = seekerId
Participant.status = ELIMINATED
```

Response:

```json
{
  "characterId": 22,
  "hiderNickname": "재원",
  "originalPhotoUrl": "...",
  "previewImageUrl": "...",
  "survivalSeconds": 732
}
```

---

# 14. Realtime Event

WebSocket을 사용한다.

Endpoint:

```text
/ws
```

Room별 Topic:

```text
/topic/rooms/{roomId}
```

필요 Event:

```text
PARTICIPANT_JOINED
PARTICIPANT_LEFT
GAME_STARTED
ROLE_ASSIGNED
DESIGN_SUBMITTED
DESIGN_PHASE_ENDED
HIDING_STARTED
HIDER_READY
SEEKING_STARTED
CHARACTER_FOUND
GAME_FINISHED
```

민감한 역할 정보는 Room Broadcast로 전체에게 보내지 않는다.

예:

```text
/user/queue/game
```

를 통해 개인 역할을 전달한다.

---

# 15. Timer 설계

Timer는 Client countdown만 신뢰하지 않는다.

DB에 각 Phase의 시작 시각과 Duration을 저장한다.

예:

```text
designStartedAt = 15:00:00
designDurationSeconds = 600
```

Client는 서버에서 받은 시간을 이용해:

```text
remaining = startedAt + duration - currentTime
```

으로 표시한다.

서버 API에서도 요청 시 현재 시간이 제한 시간을 초과했는지 항상 검증한다. Frontend는
0초에 Canvas 편집을 잠근 뒤 해당 시점의 이미지를 제출한다. 네트워크 전달 지연만
수용하도록 서버는 자동 제출에 고정 5초의 유예시간을 적용하며, 이 시간 동안 Client가
그림을 더 수정할 수는 없다.

화면 타이머는 UX용이며 서버 시간이 실제 판정 기준이다.

DESIGNING 제한시간이 끝나면 Frontend가 현재 Canvas를 자동 제출한다. 서버는 모든
HIDER의 제출을 확인한 뒤에만 PRINTING으로 전환한다. HIDING 만료는 SEEKING으로 자동
전환하지 않으며, 모든 HIDER의 숨기기 완료와 HOST의 명시적 시작 요청을 기다린다.

MVP에서는 별도의 분산 Scheduler를 사용하지 않는다.

필요한 경우 Spring `@Scheduled`로 만료 Game을 주기적으로 확인한다.

---

# 16. 이미지 편집 데이터 구조

캐릭터 디자인의 최종 결과는 Frontend Canvas에서 생성한다.

Frontend가 담당:
- 배경 표시
- Template 표시
- Brush
- Eraser
- Eyedropper
- Transform
- PNG Export

Backend는 이미지 편집을 수행하지 않는다.

업로드 파일:

```text
original.jpg
character.png
preview.jpg
```

### original.jpg

사용자가 촬영한 원본 공간 사진.

### character.png

투명 배경의 최종 사람 캐릭터.

### preview.jpg

원본 공간 사진 위에 최종 Character를 실제 위치대로 합성한 이미지.

QR 스캔 성공 시 preview.jpg를 보여준다.

---

# 17. 인쇄 Architecture

MVP에서 서버가 실제 Printer Hardware를 직접 제어하지 않는다.

HOST가 모든 HIDER Character를 한 번에 양면 인쇄할 수 있도록 Print Sheet를 자동 생성한다.

## 17.1 전체 흐름

```text
HIDER Character 제출
        ↓
Backend에서 Character별 qrToken 생성
        ↓
모든 HIDER 제출 완료
        ↓
HOST Print Page 진입
        ↓
Character 이미지 조회
        +
qrToken 기반 QR Code 생성
        ↓
Front Sheet 생성
        +
Back Sheet 생성
        ↓
HOST 브라우저 양면 인쇄
        ↓
Character 절단
        ↓
앞면: 위장 Character
뒷면: 해당 Character의 QR
```

## 17.2 Front / Back Pairing

하나의 실제 출력물은 하나의 Character와 정확히 1:1 매핑된다.

예를 들어 한 페이지에 Character 6개를 출력한다고 가정한다.

Front Page:

```text
┌────────┬────────┬────────┐
│ Char A │ Char B │ Char C │
├────────┼────────┼────────┤
│ Char D │ Char E │ Char F │
└────────┴────────┴────────┘
```

Back Page:

```text
┌────────┬────────┬────────┐
│ QR A   │ QR B   │ QR C   │
├────────┼────────┼────────┤
│ QR D   │ QR E   │ QR F   │
└────────┴────────┴────────┘
```

논리적인 Pairing:

```text
Char A ↔ QR A
Char B ↔ QR B
Char C ↔ QR C
Char D ↔ QR D
Char E ↔ QR E
Char F ↔ QR F
```

Print Layout 생성 중 Character 순서가 변경되어 앞면과 뒷면 Pair가 뒤바뀌어서는 안 된다.

Character ID 또는 고정된 Print Slot을 기준으로 Front / Back Pairing을 유지한다.

---

## 17.3 양면 인쇄 정렬

실제 양면 인쇄에서는 Printer의 뒤집기 방식에 따라 Back Page가 좌우 또는 상하 반전될 수 있다.

지원할 인쇄 방식을 명확히 정의한다.

예:

```text
Paper: A4 Portrait
Duplex: Enabled
Scale: Actual Size / 100%
Flip: Long Edge
```

Print Page는 위 설정을 기본 권장 방식으로 안내한다.

브라우저에서 프린터의 양면 옵션 자체를 강제로 변경할 수는 없으므로 HOST가 Print Dialog에서 직접 양면 인쇄를 선택한다.

필요하다면 추후 다음 옵션을 Print Layout 설정으로 지원할 수 있다.

```text
LONG_EDGE_FLIP
SHORT_EDGE_FLIP
```

MVP에서는 우선 하나의 권장 방식만 공식 지원한다.

---

## 17.4 Cutting Guide

각 Character에는 절단 영역을 제공한다.

```text
┌ - - - - - - - ┐
|                |
|   Character    |
|                |
└ - - - - - - - ┘
```

앞면과 뒷면의 Cutting Area는 정확히 동일해야 한다.

QR은 절단 후 Character 뒷면 안쪽에 완전히 포함되어야 한다.

---

## 17.5 Print API

권장 API:

```http
GET /api/v1/games/{gameId}/print-sheet
```

Response 예시:

```json
{
  "gameId": 10,
  "characters": [
    {
      "characterId": 101,
      "characterImageUrl": "...",
      "qrToken": "..."
    },
    {
      "characterId": 102,
      "characterImageUrl": "...",
      "qrToken": "..."
    }
  ]
}
```

Frontend Print Page는 Character 목록 순서를 고정한 뒤 동일한 순서를 이용해 Front / Back Sheet를 생성한다.

Frontend Route:

```text
/host/games/{gameId}/print
```

CSS:

```css
@media print
```

를 이용해 일반 UI는 숨기고 Print Layout만 출력한다.

MVP에서는 Browser Print 기반을 우선한다.

PDF 생성이 실제 테스트 과정에서 필요하다고 판단되는 경우에만 서버 PDF Generation을 추가한다.

---

# 18. 출력물 식별 규칙

각 실제 출력물은 하나의 Character와 정확히 1:1 매핑된다.

```text
Character
   ↓
qrToken
   ↓
Front / Back Pair
```

앞면:
- HIDER가 디자인한 최종 Character Image

뒷면:
- 해당 Character 전용 QR Code

QR에는 PLAYER 닉네임을 직접 넣지 않는다.

조회 흐름:

```text
QR Scan
  ↓
qrToken
  ↓
Character
  ↓
Participant
```

Front Character와 Back QR의 Pair가 뒤바뀌면 잘못된 HIDER가 탈락할 수 있으므로 Print Layout에서 Pairing은 핵심 Business Rule로 취급한다.

---

# 19. Database Schema 초안

## rooms

```sql
id BIGSERIAL PRIMARY KEY
room_code VARCHAR(6) UNIQUE NOT NULL
name VARCHAR(100) NOT NULL
host_token_hash VARCHAR(64) UNIQUE NOT NULL
status VARCHAR(30) NOT NULL
created_at TIMESTAMP WITH TIME ZONE NOT NULL
updated_at TIMESTAMP WITH TIME ZONE NOT NULL
```

## participants

```sql
id BIGSERIAL PRIMARY KEY
room_id BIGINT NOT NULL
nickname VARCHAR(30) NOT NULL
participant_token_hash VARCHAR(64) UNIQUE NOT NULL
type VARCHAR(20) NOT NULL
game_role VARCHAR(20) NOT NULL
status VARCHAR(20) NOT NULL
joined_at TIMESTAMP WITH TIME ZONE NOT NULL
```

Unique 권장:

```text
(room_id, nickname)
```

## games

```sql
id BIGSERIAL PRIMARY KEY
room_id BIGINT NOT NULL
status VARCHAR(30) NOT NULL
design_duration_seconds INT NOT NULL
hide_duration_seconds INT NOT NULL
seek_duration_seconds INT NOT NULL
seeker_count INT NOT NULL
design_started_at TIMESTAMP WITH TIME ZONE
hide_started_at TIMESTAMP WITH TIME ZONE
seek_started_at TIMESTAMP WITH TIME ZONE
finished_at TIMESTAMP WITH TIME ZONE
winner VARCHAR(20)
created_at TIMESTAMP WITH TIME ZONE NOT NULL
```

## characters

```sql
id BIGSERIAL PRIMARY KEY
game_id BIGINT NOT NULL
participant_id BIGINT NOT NULL

template_type VARCHAR(50) NOT NULL

original_photo_url TEXT NOT NULL
character_image_url TEXT NOT NULL
preview_image_url TEXT NOT NULL

position_x DOUBLE PRECISION
position_y DOUBLE PRECISION
scale DOUBLE PRECISION
rotation DOUBLE PRECISION

qr_token VARCHAR(255) UNIQUE NOT NULL

status VARCHAR(20) NOT NULL

submitted_at TIMESTAMP WITH TIME ZONE NOT NULL
printed_at TIMESTAMP WITH TIME ZONE
found_at TIMESTAMP WITH TIME ZONE
found_by_participant_id BIGINT
```

Unique:

```text
(game_id, participant_id)
```

`qr_token`에는 반드시 Unique Constraint를 적용한다.

QR 이미지 자체를 별도 Column이나 Table에 저장하지 않는다.

---

# 20. 인증 및 권한

MVP에서는 회원가입을 만들지 않는다.

## HOST

방 생성 시 Secure Random `hostToken` 발급.

Frontend LocalStorage 저장.

HOST 요청:

```http
Authorization: Bearer {hostToken}
```

## PLAYER

방 참가 시 `participantToken` 발급.

Frontend LocalStorage 저장.

PLAYER 요청:

```http
Authorization: Bearer {participantToken}
```

서버에서는 Token을 통해 Request Actor를 식별한다.

Token 원문은 최초 발급 시 Client에 한 번만 반환하고 DB에는 SHA-256 Hash만 저장한다.

---

# 21. 주요 Business Rules

1. HOST만 게임을 시작할 수 있다.
2. HOST만 게임 Phase를 변경할 수 있다.
3. SEEKER 인원은 전체 PLAYER 수보다 작아야 한다.
4. HIDER만 Character를 제작할 수 있다.
5. HIDER는 Game당 Character 하나만 제출한다.
6. DESIGNING Phase가 끝나면 Character 수정 불가.
7. SEEKER만 Character QR을 발견 처리할 수 있다.
8. SEEKING Phase에서만 발견 처리 가능.
9. 이미 FOUND인 Character는 다시 발견 처리할 수 없다.
10. 자신의 방 / 게임과 관계없는 Token 접근을 금지한다.
11. 모든 HIDER가 FOUND 상태가 되면 SEEKER 즉시 승리 가능.
12. Seek 제한시간이 끝났을 때 HIDER가 하나라도 남아 있으면 HIDER 승리.
13. QR Token은 추측하기 어려워야 한다.
14. Client의 role/status 값을 신뢰하지 않는다.
15. 모든 권한 및 상태 검증은 Backend에서 다시 수행한다.
16. 모든 Character는 제출 시 하나의 고유 QR Token을 자동 발급받는다.
17. QR Token은 Client가 생성하거나 임의로 지정할 수 없다.
18. Character와 QR Token은 1:1 관계를 유지한다.
19. 동일 qrToken이 둘 이상의 Character에 할당되어서는 안 된다.
20. Print Sheet에서 앞면 Character와 뒷면 QR의 Pairing이 변경되어서는 안 된다.
21. Front / Back Print Layout은 동일한 Character 정렬 기준을 사용해야 한다.
22. 앞면과 뒷면은 동일한 Cutting Area를 사용해야 한다.
23. QR은 절단 후에도 출력물 내부에 완전히 포함되어야 한다.
24. QR Image는 영속화하지 않고 qrToken을 이용해 필요 시 생성한다.
25. 디자인 제한시간 종료 시 Frontend는 현재 Canvas를 기존 Character 제출 API로 자동 제출한다.
26. 탐색은 숨기기 제한시간 종료, 모든 HIDER 준비 완료, HOST의 명시적 요청을 모두 만족해야 시작한다.

---

# 22. 동시성 처리

동일 QR을 SEEKER 여러 명이 거의 동시에 Scan할 수 있다.

따라서 발견 처리 API는 원자적이어야 한다.

권장 방법:

```sql
UPDATE characters
SET status = 'FOUND',
    found_at = :now,
    found_by_participant_id = :seekerId
WHERE qr_token = :token
  AND game_id = :gameId
  AND status = 'HIDDEN';
```

업데이트된 row count가 1이면 성공.

0이면:
- 이미 발견된 Character
- 현재 게임에 속하지 않는 Character
- 유효하지 않은 상태

409 Conflict 또는 적절한 Error를 반환한다.

단순 JPA 사용 시 `@Version` Optimistic Lock도 사용할 수 있으나 MVP에서는 조건부 UPDATE를 우선한다.

---

# 23. Error Response

공통 Format:

```json
{
  "code": "GAME_INVALID_STATE",
  "message": "현재 게임 상태에서는 수행할 수 없습니다.",
  "timestamp": "2026-08-29T14:00:00Z"
}
```

주요 Error Code:

```text
ROOM_NOT_FOUND
ROOM_FULL
DUPLICATE_NICKNAME
INVALID_TOKEN
ACCESS_DENIED
GAME_NOT_FOUND
GAME_INVALID_STATE
INVALID_GAME_ROLE
CHARACTER_NOT_FOUND
CHARACTER_ALREADY_SUBMITTED
CHARACTER_ALREADY_FOUND
DESIGN_TIME_EXPIRED
SEEK_TIME_EXPIRED
INVALID_QR_TOKEN
DUPLICATE_QR_TOKEN
PRINT_NOT_READY
```

---

# 24. Frontend Page Structure

```text
/
├── host
│   ├── create
│   ├── room/:roomCode
│   ├── game/:gameId
│   ├── print/:gameId
│   └── result/:gameId
│
├── join/:roomCode
│
├── c/:qrToken
│
└── game/:gameId
    ├── role
    ├── hider/design
    ├── hider/wait
    ├── hider/hide
    ├── seeker/wait
    ├── seeker/scan
    ├── found/:characterId
    └── result
```

`/c/:qrToken`은 QR을 카메라로 읽었을 때 접근 가능한 Route다.

해당 Route 자체에서 바로 탈락 처리를 수행하지 않는다.

로그인된 Participant와 Game 상태를 서버에서 검증한 뒤 발견 처리 API를 호출해야 한다.

---

# 25. 구현 우선순위

## Phase 1 - Game Skeleton

먼저 이미지 기능 없이 게임 상태 흐름만 완성한다.

```text
방 생성
→ 참가
→ 게임 시작
→ 역할 배정
→ Phase 전환
→ 게임 종료
```

## Phase 2 - Character Design

```text
카메라 촬영
→ Template 선택
→ Canvas 편집
→ PNG 생성
→ 이미지 업로드
→ Character 제출
```

## Phase 3 - QR & Duplex Print

```text
Character 제출
→ Character별 qrToken 자동 생성
→ qrToken 기반 QR Code 생성
→ 인쇄 화면
→ Front Print Sheet 생성
→ Back QR Print Sheet 생성
→ 앞면 / 뒷면 Pairing 유지
→ 양면 인쇄 정렬
→ Cutting Guide
→ QR Scanner
→ 발견 처리
→ HIDER 탈락
```

## Phase 4 - Realtime

```text
참가자 실시간 입장
→ Phase 변경 Broadcast
→ 제출 현황
→ 발견 현황
→ 게임 종료
```

## Phase 5 - Polish

```text
Timer UX
Error Handling
Mobile UI
Print CSS
Duplex Print 실기기 테스트
WebView Camera Permission
Game Result
```

---

# 26. Codex 구현 원칙

Codex는 아래 원칙을 반드시 지킨다.

1. MVP 범위를 넘어가는 기능을 임의로 추가하지 않는다.
2. AI 기능을 추가하지 않는다.
3. Microservice로 분리하지 않는다.
4. Backend는 Modular Monolith로 구성한다.
5. Domain별 Package를 사용한다.
6. Controller에 Business Logic을 작성하지 않는다.
7. Game State Transition은 반드시 Backend에서 검증한다.
8. Client에서 보내는 Role, Status를 신뢰하지 않는다.
9. 이미지 Binary를 DB에 저장하지 않는다.
10. QR에 Database ID나 사용자 개인정보를 직접 노출하지 않는다.
11. QR Token은 반드시 Backend에서 생성한다.
12. QR Token에 Unique Constraint를 적용한다.
13. QR 이미지 자체는 DB에 저장하지 않는다.
14. 동일 QR 중복 발견에 대한 동시성 처리를 구현한다.
15. Print Layout에서 Character / QR Pairing을 절대 깨뜨리지 않는다.
16. 양면 인쇄 Front / Back Sheet는 동일한 정렬 기준을 사용한다.
17. Flyway Migration 없이 DB Schema를 임의 변경하지 않는다.
18. API Request / Response에는 Entity를 직접 노출하지 않고 DTO를 사용한다.
19. 테스트 가능한 Service 구조를 유지한다.
20. 기능 구현 후 최소한 핵심 Business Rule에 대한 테스트를 작성한다.

---

# 27. 필수 테스트

최소 다음 테스트를 작성한다.

### Room

- 방 코드 중복 없이 생성
- 동일 방 닉네임 중복 방지

### Role

- seekerCount만큼 SEEKER가 생성되는지
- 나머지가 HIDER인지

### Game

- 잘못된 Phase Transition 거부
- HOST가 아닌 사용자의 Phase 변경 거부

### Character

- SEEKER Character 제출 거부
- 동일 HIDER 중복 제출 거부
- Design Phase 종료 후 제출 거부
- 제한시간 종료 시 현재 Canvas 자동 제출
- 자동 제출 Callback이 중복 실행되어도 Character 하나만 생성
- Character 제출 시 qrToken 자동 생성
- 서로 다른 Character에 서로 다른 qrToken 생성
- qrToken Unique Constraint 확인

### Print

- 제출된 모든 Character가 Front Sheet에 포함되는지
- 모든 Character에 대응되는 QR이 Back Sheet에 포함되는지
- Front / Back Character Pairing이 동일하게 유지되는지
- 페이지가 여러 장이어도 Pairing이 유지되는지
- Cutting Area가 앞면과 뒷면에서 일치하는지

### QR

- HIDER가 QR 발견 처리 불가
- SEEKING 이전 QR 처리 불가
- 다른 Game의 QR 처리 불가
- 잘못된 qrToken 거부
- 이미 발견된 Character 중복 처리 불가
- 정상 QR Scan 시 Character FOUND
- 정상 QR Scan 시 HIDER ELIMINATED
- 동시에 동일 QR Scan 시 하나의 요청만 성공

### Result

- 모든 HIDER 발견 시 SEEKER 승리
- 시간 종료 시 생존 HIDER가 있으면 HIDER 승리

---

# 28. 핵심 성공 기준

MVP 성공 기준은 다음 End-to-End Flow가 실제 모바일 및 프린터 환경에서 끊김 없이 수행되는 것이다.

```text
HOST 방 생성
→ PLAYER 참가 QR 스캔
→ 닉네임 입력 및 참가
→ HOST 게임 시작
→ HIDER / SEEKER 역할 배정
→ HIDER 카메라 촬영
→ Character 디자인
→ Character 제출
→ Character별 qrToken 자동 생성
→ Character별 QR 자동 생성
→ HOST 인쇄 화면 진입
→ 앞면 Character / 뒷면 QR 양면 인쇄
→ HIDER에게 출력물 배부
→ 실제 공간에 Character 부착
→ HOST 탐색 시작
→ SEEKER Character 발견
→ 출력물 뒷면 QR 스캔
→ qrToken 기반 Character 식별
→ 해당 HIDER 식별 및 탈락
→ 숨긴 위치 Preview 공개
→ 모든 HIDER 발견 또는 제한시간 종료
→ 승패 및 결과 반영
```

추가 기능보다 이 흐름의 안정성과 사용자 경험을 우선한다.

특히 다음 3개 구간은 MVP의 핵심 검증 포인트로 본다.

```text
1. 촬영 → 디자인 → 제출
2. Character → QR → 정확한 양면 인쇄
3. 실제 발견 → QR Scan → 올바른 HIDER 탈락
```

위 세 흐름이 실제 파티 환경에서 안정적으로 연결되는 것을 MVP 성공의 최우선 기준으로 한다.
