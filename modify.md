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
