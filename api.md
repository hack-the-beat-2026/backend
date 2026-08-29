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

DB에는 Token 원문이 아닌 SHA-256 Hash만 저장된다. 현재 공개 API는 방 생성, 방 코드
조회, 방 참가이며 참가자 목록은 해당 방 HOST만 조회할 수 있다.

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
