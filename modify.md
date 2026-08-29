## 2026-08-29 15:28 - 기술 기준 통일 및 PostgreSQL 테스트 환경 구성

### 작업 목적

프로젝트 문서를 현재 Spring Boot와 Package 기준에 맞추고, Security/WebSocket/QR 기능 및 향후 API 통합 테스트를 위한 의존성과 실제 PostgreSQL 테스트 환경을 준비했다.

### 변경 파일

- `AGENTS.md`
- `architecture.md`
- `build.gradle`
- `compose.yaml`
- `src/test/java/com/hackathon/gdg/GdgApplicationTests.java`
- `src/test/java/com/hackathon/gdg/PostgresTestContainerConfiguration.java`
- `src/test/resources/application-test.properties`

### 변경 내용

- 기술 기준을 Spring Boot 4.x와 `com.hackathon.gdg` Package로 통일
- `AGENTS.md`를 작업 규칙, 핵심 불변식, 테스트 및 완료 조건 중심으로 축약
- Spring Security, WebSocket, ZXing 3.5.4 의존성 추가
- Testcontainers 2.0.5 기반 PostgreSQL 17 통합 테스트 환경 추가
- 개발 및 테스트 PostgreSQL Image를 `postgres:17-alpine`으로 고정
- 디자인 Draft 자동 저장과 제한시간 강제 제출 규칙 추가
- Draft가 없을 때 흰색 기본 Character를 제출하도록 명시
- 숨기기 시간 종료, 모든 HIDER 준비, HOST 요청을 탐색 시작 조건으로 명시

### 테스트

- `./gradlew test`
- PostgreSQL Testcontainer 기동, Flyway/JPA/Security Application Context 로딩 확인
- 결과: `BUILD SUCCESSFUL`

### 참고사항

- Docker를 사용할 수 있어야 PostgreSQL 통합 테스트가 실행된다.
- Draft 저장 API와 만료 강제 제출 로직은 설계만 반영했으며 이후 Character 구현 단계에서 개발한다.
- WebSocket 의존성은 준비했지만 Realtime 기능 구현은 후순위 Phase로 유지한다.

## 2026-08-29 15:32 - Canvas 자동 제출 및 Compose 테스트 DB로 단순화

### 작업 목적

디자인 중간 저장 없이 제한시간의 현재 Canvas만 자동 제출하도록 설계를 단순화하고, 테스트용 PostgreSQL을 Testcontainers가 아닌 Docker Compose로 실행하도록 변경했다.

### 변경 파일

- `AGENTS.md`
- `architecture.md`
- `build.gradle`
- `compose.yaml`
- `src/test/java/com/hackathon/gdg/GdgApplicationTests.java`
- `src/test/java/com/hackathon/gdg/PostgresTestContainerConfiguration.java` (삭제)
- `src/test/resources/application-test.properties`

### 변경 내용

- Character Draft API, 주기적 자동 저장, 서버 강제 제출 및 기본 Asset 대체 규칙 제거
- 제한시간 종료 시 Frontend가 Canvas를 잠그고 현재 이미지를 기존 제출 API로 한 번 전송하도록 변경
- 자동 제출의 네트워크 전송을 위한 고정 5초 유예 규칙 추가
- Testcontainers 의존성과 설정 제거
- Compose PostgreSQL을 `localhost:5432`에 고정하고 Healthcheck 추가
- Compose 초기화 Script로 API 테스트 전용 `gdg_test` Database 생성
- PostgreSQL Data Directory를 명시적 `postgres-data` Volume으로 관리
- Test Profile이 개발용 `mydatabase`와 분리된 `gdg_test`에 접속하도록 설정

### 테스트

- `docker compose config` 성공
- `docker compose up -d postgres` 실행
- `docker compose ps`: `gdg-postgres-1` healthy 확인
- `./gradlew test`: `BUILD SUCCESSFUL`

### 참고사항

- 테스트 전에 `docker compose up -d postgres`를 실행해야 한다.
- 자동 제출은 HIDER가 디자인 화면을 유지하고 네트워크에 연결되어 있다는 MVP 전제를 사용한다.

## 2026-08-29 15:45 - 핵심 도메인 영속성 기반 구현

### 작업 목적

Room부터 Character까지 게임 핵심 데이터를 PostgreSQL에 안전하게 저장하고 이후 API와 Service 구현이 사용할 영속성 기반을 마련했다.

### 변경 파일

- `architecture.md`
- `src/main/resources/application.properties`
- `src/main/resources/db/migration/V1__init.sql`
- `src/main/java/com/hackathon/gdg/room/domain/*`
- `src/main/java/com/hackathon/gdg/room/repository/RoomRepository.java`
- `src/main/java/com/hackathon/gdg/participant/domain/*`
- `src/main/java/com/hackathon/gdg/participant/repository/ParticipantRepository.java`
- `src/main/java/com/hackathon/gdg/game/domain/*`
- `src/main/java/com/hackathon/gdg/game/repository/GameRepository.java`
- `src/main/java/com/hackathon/gdg/character/domain/*`
- `src/main/java/com/hackathon/gdg/character/repository/CharacterRepository.java`
- `src/test/resources/application-test.properties`
- `src/test/java/com/hackathon/gdg/persistence/PersistenceConstraintsTests.java`

### 변경 내용

- `rooms`, `participants`, `games`, `characters`와 Flyway V1 Migration 구현
- Room, Participant, Game, Character JPA Entity와 상태 Enum 구현
- Domain별 Spring Data JPA Repository 구현
- Token 원문 대신 SHA-256 Hash를 저장하도록 Column과 문서 정리
- 방 코드 형식, 상태값, 양수 Duration, Canvas 좌표와 Scale Check Constraint 추가
- 같은 방의 닉네임을 대소문자 구분 없이 막는 Unique Index 추가
- Room Code, Token Hash, Character/QR 1:1 규칙을 Database Unique Constraint로 보장
- UTC `TIMESTAMP WITH TIME ZONE`, Hibernate Schema Validation, Open Session in View 비활성화 적용

### 테스트

- PostgreSQL `gdg_test`에서 Application Context 및 Flyway/JPA Schema Validation 통과
- 핵심 Domain 저장·조회와 6개 무결성 위반 시나리오 통과
- 총 8개 테스트: 실패 0, 오류 0
- `./gradlew test`: `BUILD SUCCESSFUL`
- `./gradlew build`: `BUILD SUCCESSFUL`
- `./gradlew bootRun`: 개발 DB에 V1 적용 및 Tomcat 8080 기동 확인 후 정상적으로 수동 종료
- `mydatabase`, `gdg_test` 모두 핵심 4개 Table과 `flyway_schema_history` 생성 확인

### 참고사항

- PostgreSQL Compose 컨테이너는 계속 실행 중이다.
- Token 생성·Hash Service와 Bearer 인증 Filter는 다음 Room API 작업에서 구현한다.
- 현재 Entity는 생성과 조회에 필요한 최소 동작만 제공하며 상태 전이 메서드는 Game Skeleton 단계에서 추가한다.

## 2026-08-29 15:58 - Room API와 Bearer Token 인증 구현

### 작업 목적

HOST가 방을 만들고 PLAYER가 참가한 뒤 HOST가 Lobby 참가자를 안전하게 조회할 수 있는 첫 End-to-End API 흐름을 구현했다.

### 변경 파일

- `api.md`
- `architecture.md`
- `src/main/resources/application.properties`
- `src/main/java/com/hackathon/gdg/global/error/*`
- `src/main/java/com/hackathon/gdg/global/security/*`
- `src/main/java/com/hackathon/gdg/room/controller/RoomController.java`
- `src/main/java/com/hackathon/gdg/room/dto/*`
- `src/main/java/com/hackathon/gdg/room/service/RoomCodeGenerator.java`
- `src/main/java/com/hackathon/gdg/room/service/RoomService.java`
- `src/main/java/com/hackathon/gdg/room/repository/RoomRepository.java`
- `src/main/java/com/hackathon/gdg/participant/repository/ParticipantRepository.java`
- `src/test/java/com/hackathon/gdg/room/RoomApiIntegrationTests.java`

### 변경 내용

- 방 생성, 방 코드 조회, PLAYER 참가, HOST 참가자 목록 API 구현
- 방 생성 시 설정값을 가진 WAITING Game을 같은 Transaction에서 생성
- Secure Random 256-bit URL-safe Token과 SHA-256 Hash 처리 구현
- Token 충돌 확인 및 6자리 고유 Room Code 생성 구현
- Stateless Bearer 인증 Filter와 HOST/PLAYER Actor 식별 구현
- 참가자 목록에 해당 Room HOST 소유권 검증 적용
- Bean Validation, 공통 Error Response, 인증·인가 Error JSON 구현
- Token 원문은 최초 응답에만 포함하고 목록·조회 응답에서 제외
- 실제 네 API의 사용법, 요청·응답, 오류, curl 예제를 `api.md`에 작성

### 테스트

- Room API 통합 테스트 6개 추가
- 방 생성과 Token Hash 저장, 공개 조회, 참가, 대소문자 닉네임 중복, HOST 권한, 입력 검증, 404 확인
- Token 없음·무효, PLAYER Token, 다른 Room HOST Token 접근 차단 확인
- 전체 14개 테스트: 실패 0, 오류 0
- `./gradlew clean test build`: `BUILD SUCCESSFUL`

### 참고사항

- 현재 구현 API는 `api.md`에 기재된 네 개 Endpoint다.
- 참가 QR Image 생성 Endpoint와 Game 시작·역할 배정은 아직 구현하지 않았다.
- 다음 작업은 HOST Game 시작, seekerCount 검증, Random Role 배정과 DESIGNING 전환이다.

## 2026-08-29 16:05 - 게임 시작 및 개인 역할 조회 구현

### 작업 목적

HOST가 Lobby 참가자를 기준으로 게임을 시작하고 각 PLAYER가 자신의 역할만 안전하게 확인할 수 있도록 Game Skeleton의 첫 상태 전이를 구현했다.

### 변경 파일

- `api.md`
- `architecture.md`
- `src/main/java/com/hackathon/gdg/global/config/TimeConfig.java`
- `src/main/java/com/hackathon/gdg/global/error/ErrorCode.java`
- `src/main/java/com/hackathon/gdg/room/domain/Room.java`
- `src/main/java/com/hackathon/gdg/room/repository/RoomRepository.java`
- `src/main/java/com/hackathon/gdg/participant/domain/Participant.java`
- `src/main/java/com/hackathon/gdg/participant/repository/ParticipantRepository.java`
- `src/main/java/com/hackathon/gdg/game/domain/Game.java`
- `src/main/java/com/hackathon/gdg/game/controller/GameController.java`
- `src/main/java/com/hackathon/gdg/game/dto/GameResponse.java`
- `src/main/java/com/hackathon/gdg/game/service/GameService.java`
- `src/main/java/com/hackathon/gdg/game/service/RandomRoleAssigner.java`
- `src/test/java/com/hackathon/gdg/game/GameApiIntegrationTests.java`

### 변경 내용

- HOST 전용 게임 시작 API와 인증 Actor별 게임 조회 API 구현
- PLAYER 최소 2명 및 `seekerCount < PLAYER 수` 검증
- SecureRandom Shuffle로 설정된 수의 SEEKER와 나머지 HIDER 배정
- 한 Transaction에서 Room `PLAYING`, Game `DESIGNING`, PLAYER `ACTIVE` 전환
- 서버 UTC Clock 기준 `designStartedAt`과 `designEndsAt` 계산
- Room 비관적 잠금으로 동시·중복 게임 시작 직렬화
- PLAYER에게 본인의 `myRole`과 상태만 반환하고 다른 PLAYER 역할 목록은 비공개
- Game 시작·조회 요청, 응답, 오류와 curl 사용법을 `api.md`에 추가

### 테스트

- Game API 통합 테스트 7개 추가
- SEEKER/HIDER 인원, ACTIVE 전환, Room/Game 상태와 디자인 시간 검증
- 각 PLAYER의 개인 역할 조회 검증
- Token 없음, PLAYER, 다른 Room HOST의 시작 요청 거부 검증
- 인원 부족, 잘못된 SEEKER 수, 중복 시작, 다른 Room 게임 조회 거부 검증
- 전체 21개 테스트: 실패 0, 오류 0
- `./gradlew clean test build`: `BUILD SUCCESSFUL`

### 참고사항

- WebSocket `GAME_STARTED` Event는 Realtime Phase에서 추가한다.
- 다음 작업은 디자인 Phase 종료와 Character 제출 흐름 또는 상태 전이 API 확장이다.

## 2026-08-29 16:15 - Character 이미지 제출 및 조회 구현

### 작업 목적

HIDER가 디자인 결과를 이미지와 함께 한 번만 제출하고 HOST가 인쇄 준비용 목록을 조회할 수 있도록 Character 흐름을 구현했다.

### 변경 파일

- `api.md`, `architecture.md`, `.gitignore`
- `src/main/resources/application.properties`
- `src/main/java/com/hackathon/gdg/character/{controller,dto,service,repository}/*`
- `src/main/java/com/hackathon/gdg/game/{domain,repository}/*`
- `src/main/java/com/hackathon/gdg/global/{error,security,storage}/*`
- `src/test/resources/application-test.properties`
- `src/test/java/com/hackathon/gdg/character/CharacterApiIntegrationTests.java`

### 변경 내용

- 원본 사진, 투명 Character PNG, Preview와 JSON Metadata를 받는 Multipart 제출 API 구현
- 실제 PNG/JPEG 판별, 4천만 Pixel 제한, 파일당 15MB·요청당 45MB 제한 적용
- UUID 기반 Local File Storage와 `/files/**` 정적 이미지 제공 구현
- ACTIVE HIDER, DESIGNING 상태, 제한시간과 5초 전송 유예, Game당 1회 제출 검증
- Character별 Secure Random QR Token 자동 발급
- HIDER 본인 조회와 HOST 전용 제출 목록 조회 API 구현
- Game 비관적 잠금으로 동시 제출을 직렬화하고 모든 HIDER 제출 완료 시 `PRINTING` 전환
- DB 저장 실패 시 해당 요청에서 생성한 이미지 파일 정리
- 실제 API 계약, curl 사용법, 응답과 오류를 `api.md`에 문서화

### 테스트

- Character API 통합 테스트 6개 추가
- 제출·조회·이미지 제공, SEEKER 차단, 중복 방지, HOST 권한, 이미지 형식 검증 확인
- 마지막 HIDER 제출 시 `PRINTING` 전환 확인
- 전체 27개 테스트: 실패 0, 오류 0
- `./gradlew test`: `BUILD SUCCESSFUL`

### 참고사항

- 로컬 `/files/**`는 개발 편의를 위해 공개한다. 운영에서는 Private Object Storage와 만료 URL로 교체한다.
- QR 이미지는 저장하지 않으며 향후 인쇄 API에서 `qrToken`으로 동적 생성한다.
- `DESIGN_SUBMITTED` WebSocket Event는 Realtime Phase에서 추가한다.

## 2026-08-29 16:22 - QR PNG 및 인쇄 Sheet API 구현

### 작업 목적

HOST가 제출된 Character와 고유 QR을 안정적으로 1:1 배치해 브라우저에서 양면 인쇄할 수 있는 서버 계약을 구현했다.

### 변경 파일

- `api.md`, `architecture.md`
- `src/main/java/com/hackathon/gdg/global/error/ErrorCode.java`
- `src/main/java/com/hackathon/gdg/qr/service/QrService.java`
- `src/main/java/com/hackathon/gdg/print/{controller,dto,service}/*`
- `src/test/java/com/hackathon/gdg/print/PrintApiIntegrationTests.java`

### 변경 내용

- ZXing으로 Character QR을 512×512 PNG로 동적 생성하는 HOST 전용 API 구현
- QR Payload를 `{FRONTEND_BASE_URL}/c/{qrToken}`으로 제한해 개인정보와 DB ID 제외
- A4 세로, 100%, 긴 모서리 넘김 기준의 HOST 전용 Print Sheet API 구현
- Character ID 오름차순을 고정 `printSlot`으로 제공해 Front Character와 Back QR Pairing 보장
- `PRINTING` 상태와 해당 Room HOST 소유권 검증 적용
- QR 이미지는 DB와 File Storage에 저장하지 않고 요청마다 생성
- API 응답, curl, Frontend Blob 처리와 인쇄 설정을 `api.md`에 문서화

### 테스트

- 인쇄 API 통합 테스트 3개 추가
- 고정 PrintSlot과 Character 순서, 인쇄 설정, 권한, 준비 전 접근 차단 확인
- 생성된 PNG를 ZXing으로 다시 Decode해 실제 Payload와 Character Token 일치 확인
- 전체 30개 테스트: 실패 0, 오류 0
- `./gradlew test`: `BUILD SUCCESSFUL`

### 참고사항

- Browser Print 화면과 CSS는 Frontend 구현 범위다.
- 인쇄 확인 후 `HIDING` 전환과 Character `PRINTED` 처리는 다음 상태 전이 단계에서 구현한다.
