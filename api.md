# API Guide

## 실행 및 기본 규칙

로컬 Base URL은 `http://localhost:8080/api/v1`이다.

```bash
docker compose up -d postgres
./gradlew bootRun
```

Request와 Response는 JSON이며 시간 단위는 초다. HOST와 PLAYER Token은 최초 발급
응답에서만 원문으로 제공된다. Client는 Token을 LocalStorage 등에 저장하고 보호 API에
다음 Header를 보낸다.

```http
Authorization: Bearer {token}
```

DB에는 인증 Token 원문이 아닌 SHA-256 Hash만 저장된다. 공개 API는 방 생성, 방 코드
조회, 방 참가와 `/files/**` 이미지 조회이며 나머지 API는 Bearer Token이 필요하다.

## 공통 오류 응답

```json
{
  "code": "DUPLICATE_NICKNAME",
  "message": "이미 사용 중인 닉네임입니다.",
  "timestamp": "2026-08-29T06:00:00Z",
  "fieldErrors": {}
}
```

입력 검증 실패 시 `fieldErrors`에 Field별 메시지가 포함된다. 주요 상태 코드는 `400`
잘못된 입력, `401` Token 없음·무효, `403` 권한 없음, `404` 방 없음, `409` 상태 또는
중복 충돌이다.

## 방 생성

새 Room과 설정값을 보관하는 `WAITING` Game을 함께 생성한다. 인증은 필요 없다.

```http
POST /api/v1/rooms
Content-Type: application/json
```

```json
{
  "name": "Birthday Party",
  "designDurationSeconds": 600,
  "hideDurationSeconds": 300,
  "seekDurationSeconds": 1200,
  "seekerCount": 1
}
```

- `name`: 공백 제외 필수, 최대 100자
- 각 제한시간: 1~86400초
- `seekerCount`: 1~100명이며 실제 참가자 수 검증은 게임 시작 시 수행

성공: `201 Created`

```json
{
  "roomId": 1,
  "gameId": 1,
  "roomCode": "A7K2Q9",
  "hostToken": "최초 한 번만 반환되는 Token",
  "joinUrl": "http://localhost:5173/join/A7K2Q9"
}
```

```bash
curl -X POST http://localhost:8080/api/v1/rooms \
  -H 'Content-Type: application/json' \
  -d '{"name":"Birthday Party","designDurationSeconds":600,"hideDurationSeconds":300,"seekDurationSeconds":1200,"seekerCount":1}'
```

`joinUrl`의 Origin은 `FRONTEND_BASE_URL` 환경변수로 변경할 수 있다.

## 방 조회

방 코드로 Lobby와 게임 설정을 조회한다. 인증은 필요 없으며 방 코드는 대소문자를
구분하지 않는다.

```http
GET /api/v1/rooms/{roomCode}
```

성공: `200 OK`

```json
{
  "roomId": 1,
  "gameId": 1,
  "roomCode": "A7K2Q9",
  "name": "Birthday Party",
  "roomStatus": "WAITING",
  "gameStatus": "WAITING",
  "designDurationSeconds": 600,
  "hideDurationSeconds": 300,
  "seekDurationSeconds": 1200,
  "seekerCount": 1
}
```

```bash
curl http://localhost:8080/api/v1/rooms/A7K2Q9
```

존재하지 않는 방은 `404 ROOM_NOT_FOUND`를 반환한다.

## 방 참가

WAITING Room에 PLAYER로 참가하고 Participant Token을 발급한다. 인증은 필요 없다.

```http
POST /api/v1/rooms/{roomCode}/participants
Content-Type: application/json
```

```json
{
  "nickname": "재원"
}
```

닉네임은 공백 제외 필수이며 최대 30자다. 같은 방에서는 대소문자를 무시하고 고유해야
한다.

성공: `201 Created`

```json
{
  "participantId": 10,
  "participantToken": "최초 한 번만 반환되는 Token",
  "roomId": 1,
  "gameId": 1
}
```

```bash
curl -X POST http://localhost:8080/api/v1/rooms/A7K2Q9/participants \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"재원"}'
```

- 동일 닉네임: `409 DUPLICATE_NICKNAME`
- 참가할 수 없는 Room 상태: `409 ROOM_NOT_JOINABLE`
- 존재하지 않는 방: `404 ROOM_NOT_FOUND`

## 참가자 목록

Lobby 참가자를 참가 순서로 조회한다. 해당 Room에서 발급된 HOST Token이 필요하다.

```http
GET /api/v1/rooms/{roomId}/participants
Authorization: Bearer {hostToken}
```

성공: `200 OK`

```json
[
  {
    "participantId": 10,
    "nickname": "재원",
    "type": "PLAYER",
    "gameRole": "NONE",
    "status": "WAITING",
    "joinedAt": "2026-08-29T06:00:00Z"
  }
]
```

```bash
curl http://localhost:8080/api/v1/rooms/1/participants \
  -H 'Authorization: Bearer YOUR_HOST_TOKEN'
```

- Token 없음·무효: `401 INVALID_TOKEN`
- PLAYER Token 또는 다른 Room HOST Token: `403 ACCESS_DENIED`

응답에는 Host/Participant Token 또는 Token Hash가 포함되지 않는다.

## 게임 시작

대기 중인 PLAYER에게 HIDER/SEEKER 역할을 무작위 배정하고 디자인 Phase를 시작한다.
해당 Room의 HOST Token이 필요하다.

```http
POST /api/v1/rooms/{roomId}/games/start
Authorization: Bearer {hostToken}
```

시작 조건:

- Room과 Game이 모두 `WAITING`
- 대기 중인 PLAYER가 최소 2명
- `seekerCount < 전체 PLAYER 수`

성공 시 하나의 Transaction에서 Room은 `PLAYING`, Game은 `DESIGNING`, 모든 PLAYER는
`ACTIVE`가 된다. `designEndsAt`은 서버 시간 기준 `designStartedAt + 설정 시간`이다.

성공: `200 OK`

```json
{
  "gameId": 1,
  "roomId": 1,
  "status": "DESIGNING",
  "myRole": "NONE",
  "seekerCount": 1,
  "hiderCount": 2,
  "designDurationSeconds": 600,
  "hideDurationSeconds": 300,
  "seekDurationSeconds": 1200,
  "designStartedAt": "2026-08-29T07:00:00Z",
  "designEndsAt": "2026-08-29T07:10:00Z",
  "hideStartedAt": null,
  "seekStartedAt": null,
  "finishedAt": null,
  "winner": "NONE"
}
```

HOST 응답의 `myRole`은 `NONE`이며 `myParticipantStatus`는 포함되지 않는다.

```bash
curl -X POST http://localhost:8080/api/v1/rooms/1/games/start \
  -H 'Authorization: Bearer YOUR_HOST_TOKEN'
```

- Token 없음·무효: `401 INVALID_TOKEN`
- PLAYER 또는 다른 Room HOST: `403 ACCESS_DENIED`
- PLAYER 2명 미만: `409 INSUFFICIENT_PARTICIPANTS`
- `seekerCount >= PLAYER 수`: `409 INVALID_SEEKER_COUNT`
- 이미 시작했거나 잘못된 상태: `409 GAME_INVALID_STATE`

## 현재 게임 및 내 역할 조회

HOST 또는 해당 Room PLAYER가 현재 Game 상태를 조회한다. PLAYER에게는 본인의 역할만
`myRole`로 반환하며 다른 PLAYER의 역할은 노출하지 않는다.

```http
GET /api/v1/games/{gameId}
Authorization: Bearer {hostToken|participantToken}
```

PLAYER 성공 응답: `200 OK`

```json
{
  "gameId": 1,
  "roomId": 1,
  "status": "DESIGNING",
  "myRole": "HIDER",
  "myParticipantStatus": "ACTIVE",
  "seekerCount": 1,
  "hiderCount": 2,
  "designDurationSeconds": 600,
  "hideDurationSeconds": 300,
  "seekDurationSeconds": 1200,
  "designStartedAt": "2026-08-29T07:00:00Z",
  "designEndsAt": "2026-08-29T07:10:00Z",
  "hideStartedAt": null,
  "seekStartedAt": null,
  "finishedAt": null,
  "winner": "NONE"
}
```

```bash
curl http://localhost:8080/api/v1/games/1 \
  -H 'Authorization: Bearer YOUR_PARTICIPANT_TOKEN'
```

- Token 없음·무효: `401 INVALID_TOKEN`
- 다른 Room의 Token: `403 ACCESS_DENIED`
- 존재하지 않는 Game: `404 GAME_NOT_FOUND`

## Character 제출

`DESIGNING` 상태의 `ACTIVE HIDER`가 최종 Canvas 결과를 제출한다. 수동 제출 버튼과
제한시간 0초의 자동 제출은 모두 이 API를 사용한다. Frontend는 0초에 Canvas 편집을
잠그고 즉시 세 이미지를 전송해야 하며 서버는 네트워크 전달을 위해 5초만 유예한다.

```http
POST /api/v1/games/{gameId}/characters
Authorization: Bearer {hiderParticipantToken}
Content-Type: multipart/form-data
```

Multipart Part:

- `metadata`: `application/json`, `templateType` 필수·최대 50자
- `originalPhoto`: 원본 장소 사진, PNG/JPEG
- `characterImage`: 배경 제거된 최종 캐릭터, PNG만 가능
- `previewImage`: 원본과 캐릭터를 합성한 미리보기, PNG/JPEG

좌표는 `0.0~1.0`, `scale`은 0보다 커야 한다. 파일당 최대 15MB, 요청 전체는 최대
45MB다.

```bash
curl -X POST http://localhost:8080/api/v1/games/1/characters \
  -H 'Authorization: Bearer YOUR_HIDER_TOKEN' \
  -F 'metadata={"templateType":"STANDING_01","positionX":0.42,"positionY":0.58,"scale":0.7,"rotation":15};type=application/json' \
  -F 'originalPhoto=@original.jpg' \
  -F 'characterImage=@character.png;type=image/png' \
  -F 'previewImage=@preview.jpg'
```

성공: `201 Created`

```json
{
  "characterId": 11,
  "gameId": 1,
  "participantId": 10,
  "nickname": "재원",
  "templateType": "STANDING_01",
  "originalPhotoUrl": "/files/1/10/original-uuid.jpg",
  "characterImageUrl": "/files/1/10/character-uuid.png",
  "previewImageUrl": "/files/1/10/preview-uuid.jpg",
  "positionX": 0.42,
  "positionY": 0.58,
  "scale": 0.7,
  "rotation": 15.0,
  "qrToken": "서버가 생성한 고유 Token",
  "status": "SUBMITTED",
  "submittedAt": "2026-08-29T07:10:00Z"
}
```

서버가 이미지 URL과 고유 `qrToken`을 생성한다. 같은 HIDER는 Game당 한 번만 제출할
수 있으며 마지막 HIDER 제출 후 Game 상태는 `PRINTING`이 된다.

- SEEKER 또는 비활성 참가자: `403 INVALID_GAME_ROLE`
- 디자인 시간 만료: `409 DESIGN_TIME_EXPIRED`
- 이미 제출함: `409 CHARACTER_ALREADY_SUBMITTED`
- 실제 이미지가 아니거나 형식 불일치: `400 INVALID_IMAGE`
- `DESIGNING`이 아닌 상태: `409 GAME_INVALID_STATE`

## 내 Character 조회

제출한 HIDER 본인의 Character를 조회한다.

```http
GET /api/v1/games/{gameId}/characters/me
Authorization: Bearer {hiderParticipantToken}
```

성공 응답은 Character 제출 응답과 같다. 아직 제출하지 않았다면
`404 CHARACTER_NOT_FOUND`, HIDER가 아니면 `403 INVALID_GAME_ROLE`이다.

```bash
curl http://localhost:8080/api/v1/games/1/characters/me \
  -H 'Authorization: Bearer YOUR_HIDER_TOKEN'
```

## 제출 Character 목록

인쇄 준비를 위해 Character를 ID 오름차순으로 조회한다. 해당 Room의 HOST만 가능하며
각 항목은 제출 응답과 같은 구조다.

```http
GET /api/v1/games/{gameId}/characters
Authorization: Bearer {hostToken}
```

```bash
curl http://localhost:8080/api/v1/games/1/characters \
  -H 'Authorization: Bearer YOUR_HOST_TOKEN'
```

PLAYER 또는 다른 Room HOST는 `403 ACCESS_DENIED`를 반환한다.

개발 환경의 이미지 URL(`/files/**`)은 `<img src>`에서 바로 사용할 수 있도록 공개되어
있다. 운영 배포 전에는 Private Object Storage와 만료 URL로 교체한다.

## 인쇄 Sheet 조회

모든 HIDER 제출이 끝나 Game이 `PRINTING`이 되면 해당 Room HOST가 양면 인쇄 데이터를
조회한다.

```http
GET /api/v1/games/{gameId}/print-sheet
Authorization: Bearer {hostToken}
```

```bash
curl http://localhost:8080/api/v1/games/1/print-sheet \
  -H 'Authorization: Bearer YOUR_HOST_TOKEN'
```

성공: `200 OK`

```json
{
  "gameId": 1,
  "paperSize": "A4",
  "orientation": "PORTRAIT",
  "duplexFlip": "LONG_EDGE",
  "scalePercent": 100,
  "columns": 3,
  "characters": [
    {
      "printSlot": 1,
      "characterId": 11,
      "characterImageUrl": "/files/1/10/character-uuid.png",
      "qrImageUrl": "/api/v1/games/1/characters/11/qr",
      "qrToken": "Character 고유 QR Token"
    }
  ]
}
```

서버는 Character ID 오름차순으로 `printSlot`을 고정한다. Front Page의 Character와
Back Page의 QR에 반드시 같은 `printSlot`과 절단 영역을 사용해야 한다.

권장 인쇄 설정:

- A4 세로, 3열
- 실제 크기 100%
- 양면 인쇄, 긴 모서리 넘김

- Token 없음·무효: `401 INVALID_TOKEN`
- PLAYER 또는 다른 Room HOST: `403 ACCESS_DENIED`
- 모든 HIDER가 제출하기 전: `409 GAME_INVALID_STATE`
- 존재하지 않는 Game: `404 GAME_NOT_FOUND`

## 인쇄용 QR PNG 조회

인쇄 Sheet의 `qrImageUrl`로 Character 전용 QR PNG를 가져온다. HOST 인증이 필요하므로
일반 `<img src>` 대신 Bearer Header로 Fetch한 Blob URL을 사용한다.

```http
GET /api/v1/games/{gameId}/characters/{characterId}/qr
Authorization: Bearer {hostToken}
Accept: image/png
```

```bash
curl http://localhost:8080/api/v1/games/1/characters/11/qr \
  -H 'Authorization: Bearer YOUR_HOST_TOKEN' \
  -o character-11-qr.png
```

성공 시 `512×512 image/png`을 반환한다. QR에는 다음 URL만 들어가며 닉네임,
Participant ID, Character ID, Game ID는 포함하지 않는다.

```text
{FRONTEND_BASE_URL}/c/{qrToken}
```

로컬 기본 Payload는 `http://localhost:5173/c/{qrToken}`이다. QR 이미지는 DB나 파일
시스템에 저장하지 않고 요청할 때 ZXing으로 생성한다.

- 다른 Game의 Character 또는 없는 Character: `404 CHARACTER_NOT_FOUND`
- `PRINTING`이 아닌 상태: `409 GAME_INVALID_STATE`
- QR 생성 실패: `500 QR_GENERATION_FAILED`

## 숨기기 Phase 시작

인쇄와 출력물 배부가 끝나면 HOST가 숨기기 타이머를 시작한다.

```http
POST /api/v1/games/{gameId}/hiding/start
Authorization: Bearer {hostToken}
```

성공 시 모든 Character가 `SUBMITTED → PRINTED`, Game이 `PRINTING → HIDING`으로
전환된다.

```json
{
  "gameId": 1,
  "status": "HIDING",
  "hideStartedAt": "2026-08-29T08:00:00Z",
  "hideEndsAt": "2026-08-29T08:05:00Z"
}
```

```bash
curl -X POST http://localhost:8080/api/v1/games/1/hiding/start \
  -H 'Authorization: Bearer YOUR_HOST_TOKEN'
```

- HOST가 아님: `403 ACCESS_DENIED`
- `PRINTING`이 아님: `409 GAME_INVALID_STATE`

## HIDER 숨기기 완료

출력물을 실제 위치에 배치한 HIDER가 본인 Character를 준비 완료 처리한다. `characterId`는
내 Character 조회 API에서 얻는다.

```http
POST /api/v1/games/{gameId}/characters/{characterId}/hidden
Authorization: Bearer {hiderParticipantToken}
```

성공 응답은 Character 응답과 같고 `status`가 `HIDDEN`이다.

```bash
curl -X POST http://localhost:8080/api/v1/games/1/characters/11/hidden \
  -H 'Authorization: Bearer YOUR_HIDER_TOKEN'
```

- 다른 사람의 Character: `403 ACCESS_DENIED`
- HIDER가 아님: `403 INVALID_GAME_ROLE`
- 이미 완료함: `409 CHARACTER_ALREADY_HIDDEN`
- `HIDING`이 아님: `409 GAME_INVALID_STATE`

## 탐색 Phase 시작

숨기기 제한시간이 끝나고 모든 HIDER가 준비된 뒤 HOST가 명시적으로 시작한다. 시간
만료만으로 자동 시작하지 않는다.

```http
POST /api/v1/games/{gameId}/seeking/start
Authorization: Bearer {hostToken}
```

```json
{
  "gameId": 1,
  "status": "SEEKING",
  "seekStartedAt": "2026-08-29T08:05:00Z",
  "seekEndsAt": "2026-08-29T08:25:00Z"
}
```

- 숨기기 시간 전: `409 HIDE_TIME_NOT_EXPIRED`
- 준비되지 않은 HIDER 존재: `409 HIDERS_NOT_READY`
- `HIDING`이 아님: `409 GAME_INVALID_STATE`

## QR Token 조회

QR 스캔으로 얻은 Token이 현재 탐색 대상인지 ACTIVE SEEKER가 확인한다.

```http
GET /api/v1/characters/qr/{qrToken}
Authorization: Bearer {seekerParticipantToken}
```

```json
{
  "gameId": 1,
  "characterId": 11,
  "status": "HIDDEN"
}
```

- HIDER 또는 HOST: `403 INVALID_GAME_ROLE` 또는 `403 ACCESS_DENIED`
- 잘못된 Token: `404 INVALID_QR_TOKEN`
- `SEEKING`이 아님: `409 GAME_INVALID_STATE`

## Character 발견 처리

ACTIVE SEEKER가 QR Token에 연결된 HIDER를 발견 처리한다.

```http
POST /api/v1/games/{gameId}/characters/{qrToken}/found
Authorization: Bearer {seekerParticipantToken}
```

```json
{
  "characterId": 11,
  "hiderNickname": "재원",
  "originalPhotoUrl": "/files/1/10/original-uuid.jpg",
  "previewImageUrl": "/files/1/10/preview-uuid.jpg",
  "survivalSeconds": 732,
  "gameFinished": false,
  "winner": "NONE"
}
```

성공 시 Character는 `HIDDEN → FOUND`, HIDER는 `ACTIVE → ELIMINATED`가 된다. 마지막
HIDER가 발견되면 같은 Transaction에서 Game과 Room을 종료하고 `winner=SEEKER`를
반환한다. Game과 Character 비관적 잠금으로 동일 QR 동시 요청은 하나만 성공한다.

- 이미 발견됨: `409 CHARACTER_ALREADY_FOUND`
- 다른 Game의 QR: `404 INVALID_QR_TOKEN`
- 탐색시간 만료: HIDER 승리 종료를 저장한 뒤 `409 GAME_INVALID_STATE`

## 게임 종료 확인

HOST가 탐색 타이머 종료 시 호출한다. 모든 HIDER 발견은 마지막 Scan에서 자동 종료된다.

```http
POST /api/v1/games/{gameId}/finish
Authorization: Bearer {hostToken}
```

탐색시간이 남았으면 `409 SEEK_TIME_NOT_EXPIRED`다. 마감 시 남은 `HIDDEN` Character와
HIDER를 각각 `SURVIVED`로 바꾸고 `winner=HIDER`, Game/Room을 `FINISHED`로 전환한다.
이미 종료된 Game에 다시 호출하면 현재 종료 상태를 반환한다.

게임 조회, 결과 조회 또는 QR 요청에서도 서버 Clock으로 탐색 만료를 확인하므로 Client
Timer만 신뢰하지 않는다.

## 게임 결과 조회

해당 Room의 HOST와 PLAYER가 종료 결과를 조회한다.

```http
GET /api/v1/games/{gameId}/result
Authorization: Bearer {hostToken|participantToken}
```

```json
{
  "gameId": 1,
  "status": "FINISHED",
  "winner": "SEEKER",
  "seekStartedAt": "2026-08-29T08:05:00Z",
  "seekEndsAt": "2026-08-29T08:25:00Z",
  "finishedAt": "2026-08-29T08:17:12Z",
  "hiders": [
    {
      "participantId": 10,
      "nickname": "재원",
      "characterId": 11,
      "participantStatus": "ELIMINATED",
      "characterStatus": "FOUND",
      "survivalSeconds": 732,
      "foundAt": "2026-08-29T08:17:12Z",
      "foundByParticipantId": 12,
      "foundByNickname": "민수",
      "previewImageUrl": "/files/1/10/preview-uuid.jpg"
    }
  ],
  "seekers": [
    {
      "participantId": 12,
      "nickname": "민수",
      "foundCount": 1
    }
  ]
}
```

HIDER는 `survivalSeconds` 내림차순, SEEKER는 `foundCount` 내림차순이다. 아직 종료되지
않은 Game은 `409 GAME_INVALID_STATE`, 다른 Room Token은 `403 ACCESS_DENIED`다.
