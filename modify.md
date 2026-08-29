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

## 2026-08-29 16:34 - HIDING부터 결과까지 전체 게임 루프 구현

### 작업 목적

인쇄 완료 이후 숨기기, 탐색, QR 발견, 제한시간 종료와 결과 조회까지 MVP Backend 게임 흐름을 완주했다.

### 변경 파일

- `api.md`, `architecture.md`
- `src/main/java/com/hackathon/gdg/{game,character,participant,room}/domain/*`
- `src/main/java/com/hackathon/gdg/{game,character}/repository/*`
- `src/main/java/com/hackathon/gdg/game/{controller,service,dto}/*`
- `src/main/java/com/hackathon/gdg/character/{controller,service}/*`
- `src/main/java/com/hackathon/gdg/scan/{controller,dto,service}/*`
- `src/main/java/com/hackathon/gdg/result/{controller,dto,service}/*`
- `src/main/java/com/hackathon/gdg/global/error/ErrorCode.java`
- `src/test/java/com/hackathon/gdg/game/GameFlowIntegrationTests.java`

### 변경 내용

- HOST 인쇄 완료 확인으로 모든 Character를 `PRINTED`, Game을 `HIDING`으로 전환하고 타이머 시작
- HIDER 본인 Character의 `PRINTED → HIDDEN` 준비 완료 처리
- 숨기기 시간 만료, 모든 HIDER 준비, HOST 요청을 모두 검증한 `SEEKING` 시작
- ACTIVE SEEKER 전용 QR Token 조회와 발견 처리 API 구현
- Game 및 Character 비관적 잠금으로 동일 QR 동시 Scan 중 하나만 성공하도록 보장
- 발견 시 Character `FOUND`, HIDER `ELIMINATED` 처리 및 마지막 발견 시 SEEKER 즉시 승리
- 탐색 만료 시 남은 HIDER/Character를 `SURVIVED` 처리하고 HIDER 승리로 종료
- 만료 QR 요청은 종료 상태를 Commit하면서 Scan은 409로 거절하도록 Transaction 정책 분리
- HIDER 생존시간 순위와 SEEKER 발견 수를 포함한 결과 API 구현
- Game 응답에 `hideEndsAt`, `seekEndsAt` 추가

### 테스트

- 전체 게임 루프 통합 테스트 4개 추가
- SEEKER 승리, HIDER 시간 승리, 권한·상태·타이머 조건, 잘못된 QR과 다른 Game QR 차단 확인
- 동시 동일 QR Scan 2건 중 정확히 1건 성공 및 1건 중복 거부 검증
- 총 34개 테스트를 전체 실행 대상으로 구성
- 전체 34개 테스트: 실패 0, 오류 0
- `./gradlew clean build`: `BUILD SUCCESSFUL`

### 참고사항

- 현재 MVP는 분산 Scheduler 없이 Game 조회, 종료, 결과 조회와 QR 요청 시 만료를 동기화한다.
- WebSocket 실시간 Event와 Frontend 화면 연동은 별도 후속 작업이다.

## 2026-08-29 - WebSocket 제외 및 Polling 사용 결정

### 결정 내용

해커톤 MVP에서는 WebSocket을 구현하지 않는다. 프론트엔드는 REST API Polling으로 참가자와 게임 상태를 동기화한다.

- 게임 진행 중 `GET /api/v1/games/{gameId}`를 2~3초마다 호출한다.
- Lobby의 HOST는 `GET /api/v1/rooms/{roomId}/participants`를 2~3초마다 호출한다.
- 인쇄·숨기기·탐색·발견 등 상태 변경 요청이 성공하면 다음 Polling 주기를 기다리지 않고 즉시 게임 상태를 재조회한다.
- `designEndsAt`, `hideEndsAt`, `seekEndsAt`을 기준으로 화면 Timer를 표시하되 실제 상태와 승패는 서버 응답을 따른다.
- 화면을 벗어나거나 Game이 `FINISHED`가 되면 불필요한 Polling을 중단한다.

### 참고사항

WebSocket 의존성은 당장 제거하지 않지만 MVP 기능에서는 사용하지 않는다. 실시간 UX 개선이 필요할 때만 후속 작업으로 검토한다.

## 2026-08-29 - Render Docker Web Service 배포 준비

### 작업 목적

현재 Java 21/Spring Boot 4.1.1 애플리케이션을 Render Web Service에서 Docker 방식으로 빌드하고 실행할 수 있도록 배포 구성을 추가했다.

### 변경 파일

- `Dockerfile`
- `.dockerignore`
- `.gitignore`
- `src/main/resources/application.properties`
- `modify.md`

### 변경 내용

- Eclipse Temurin 21 JDK Builder와 Temurin 21 JRE Runtime을 사용하는 Multi-stage Docker build 추가
- Gradle Wrapper 9.7.1로 `clean bootJar -x test`를 실행하고 `*-plain.jar`를 제외한 실행 JAR을 안전하게 선택
- Runtime image에서 Build Tool을 제외하고 `spring:spring` non-root 사용자로 애플리케이션 실행
- Runtime의 `/app/storage`를 생성하고 애플리케이션 사용자에게 쓰기 권한 부여
- `server.port=${PORT:8080}`을 추가해 Render `PORT`와 로컬 기본 8080을 모두 지원
- Docker context에서 Git/IDE/Build/Test/Log/Local Storage/Secret 파일을 제외
- `.env`, 인증서, Key, Credential JSON 등의 실수 Commit 방지 규칙을 `.gitignore`에 추가
- 기존 Actuator `GET /actuator/health`와 Security 공개 설정을 Render Health Check로 재사용
- Main 설정에는 운영 DB Credential이 없으므로 Spring Boot 표준 `SPRING_DATASOURCE_*` 환경변수 주입 방식을 사용하고 로컬 Docker Compose 자동 연결은 유지

### 빌드 및 검증 결과

- `./gradlew clean build`: `BUILD SUCCESSFUL`
- 전체 34개 테스트: 실패 0, 오류 0
- `docker build --tag gdg-render-validation:local .`: 성공
- Docker image 사용자: `spring:spring`
- Docker ENTRYPOINT: `java -jar /app/app.jar`
- 임시 컨테이너에 `PORT=18080`과 Local PostgreSQL 환경변수를 주입해 정상 기동 확인
- `GET /actuator/health`: HTTP 200, `status=UP`
- 검증용 Container와 Image는 검증 후 제거

### Render Environment Variables

필수:

- `SPRING_DATASOURCE_URL`: `jdbc:postgresql://{Render Internal Host}:5432/{Database Name}` 형식
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `FRONTEND_BASE_URL`: 배포된 Frontend Origin

선택:

- `STORAGE_ROOT`: 기본값은 Docker Working Directory 기준 `./storage`이며 명시한다면 `/app/storage`
- `PORT`: Render가 자동 제공하므로 Dashboard에서 직접 추가하지 않아도 됨

### 보안 및 참고사항

- 운영 DB Password, JWT Secret, API Key, AWS/Supabase Key, Service Account Credential은 Repository에서 발견되지 않았다.
- `compose.yaml`과 `application-test.properties`의 DB Credential은 Local/Test 전용 값이며 운영에서는 사용하지 않는다.
- Render 무료 Web Service 파일시스템은 재시작, 재배포, Idle Spin-down 시 초기화된다. 현재 Local File Storage의 업로드 이미지는 영구 보존되지 않으므로 해커톤 데모 이후에는 Object Storage로 교체해야 한다.
- Render 무료 PostgreSQL은 만료와 용량 제한이 있으므로 Dashboard의 현재 Free Plan 제한을 확인해야 한다.

## 2026-08-29 - Vercel Frontend CORS 연결

### 작업 목적

배포된 Vercel Frontend가 Render Backend의 REST API와 업로드 이미지에 브라우저에서 접근할 수 있도록 명시적인 CORS 정책을 추가했다.

### 변경 파일

- `src/main/java/com/hackathon/gdg/global/config/CorsConfig.java`
- `src/main/java/com/hackathon/gdg/global/security/SecurityConfig.java`
- `src/main/resources/application.properties`
- `src/test/java/com/hackathon/gdg/global/security/CorsIntegrationTests.java`
- `modify.md`

### 변경 내용

- `https://temporary-agile-sapphire-croc76y.vercel.app`과 로컬 개발용 `http://localhost:5173`을 기본 허용 Origin으로 설정
- `/api/**`, `/files/**`에 대해 현재 API가 사용하는 `GET`, `POST`, `OPTIONS`와 `Authorization`, `Content-Type`, `Accept` Header 허용
- Spring Security Filter Chain에 CORS 처리를 연결해 인증되지 않은 Preflight 요청이 먼저 처리되도록 구성
- `CORS_ALLOWED_ORIGINS` 환경변수로 운영 허용 Origin 목록을 교체할 수 있도록 설정
- Credential Cookie를 사용하지 않는 Bearer Token 구조에 맞춰 `allowCredentials=false` 유지

### 빌드 및 테스트 결과

- Vercel Origin 및 localhost 허용, 미등록 Origin 차단 CORS 통합 테스트 3개 추가
- `./gradlew clean build`: `BUILD SUCCESSFUL`
- 전체 37개 테스트: 실패 0, 오류 0
- `docker build --tag gdg-render-cors-validation:local .`: 성공

### Render Environment Variables

- `FRONTEND_BASE_URL=https://temporary-agile-sapphire-croc76y.vercel.app`: 생성되는 참가 URL과 QR URL의 Frontend 주소
- `CORS_ALLOWED_ORIGINS=https://temporary-agile-sapphire-croc76y.vercel.app`: 운영에서 허용할 브라우저 Origin. 설정하지 않아도 현재 주소가 기본값에 포함되지만 명시 설정을 권장

### 참고사항

- Origin 값 끝에는 `/`를 붙이지 않는다.
- Vercel 배포 URL이 변경되거나 Custom Domain을 추가하면 쉼표로 구분해 `CORS_ALLOWED_ORIGINS`를 갱신하고 Backend를 재배포한다.

## 2026-08-29 - Vercel Frontend Origin 변경

### 작업 목적

새 Vercel 배포 주소에 맞춰 Backend의 기본 CORS 허용 Origin을 변경했다.

### 변경 파일

- `src/main/resources/application.properties`
- `src/test/java/com/hackathon/gdg/global/security/CorsIntegrationTests.java`
- `modify.md`

### 변경 내용

- 기존 Vercel Origin을 `https://temporary-agile-sapphire-croc76y.vercel.app`으로 교체
- CORS 통합 테스트의 기대 Origin도 새 주소와 일치하도록 변경

### 빌드 및 테스트 결과

- 사용자 요청에 따라 Gradle 테스트는 실행하지 않음
- `docker build --tag gdg-render-cors-validation:local .`: 성공

### Render Environment Variables

- `FRONTEND_BASE_URL=https://temporary-agile-sapphire-croc76y.vercel.app`
- `CORS_ALLOWED_ORIGINS=https://temporary-agile-sapphire-croc76y.vercel.app`

## 2026-08-29 - Vercel Frontend Origin 재변경

### 작업 목적

새 Vercel 배포 주소에서 Render Backend API를 호출할 수 있도록 기본 CORS 허용 Origin을 갱신했다.

### 변경 파일

- `src/main/resources/application.properties`
- `src/test/java/com/hackathon/gdg/global/security/CorsIntegrationTests.java`
- `modify.md`

### 변경 내용

- 기본 허용 Vercel Origin을 `https://dist-two-ecru-35.vercel.app`으로 교체
- CORS 통합 테스트의 배포 Frontend Origin도 같은 주소로 변경

### 테스트 결과

- `./gradlew --gradle-user-home ./.gradle-local test --tests com.hackathon.gdg.global.security.CorsIntegrationTests`: `BUILD SUCCESSFUL`

### Render Environment Variables

- `FRONTEND_BASE_URL=https://dist-two-ecru-35.vercel.app`
- `CORS_ALLOWED_ORIGINS=https://dist-two-ecru-35.vercel.app`
