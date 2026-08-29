# Repository Guidelines

## 기준 문서와 MVP 범위

`architecture.md`는 제품 범위, 도메인, API, 게임 상태, 저장소, QR 스캔, 인쇄, 필수 테스트의 Source of Truth다. 구현 전에 반드시 읽는다. 코드와 문서가 충돌하면 제3의 설계를 만들지 말고, 사용자가 변경을 지시하지 않는 한 문서를 따른다. 규칙 중복을 피하기 위해 이 문서에는 작업 방식만 둔다.

MVP 목표는 방 생성·참가, 역할 배정, 촬영과 디자인, QR 양면 인쇄, 숨기기, 스캔, 결과까지 모바일 흐름을 완주하는 것이다. 요청 없이 AI, 채팅, 결제, 소셜 기능, Microservice, Kafka, Redis, Kubernetes 또는 Printer Driver를 추가하지 않는다.

## 구조와 설계 원칙

Java 21과 Spring Boot 4.x 기반 Modular Monolith다. 운영 코드는 `src/main/java/com/hackathon/gdg`, 리소스와 Flyway Migration은 `src/main/resources`, 테스트는 동일한 Package 구조의 `src/test/java`에 둔다. `room`, `participant`, `game`, `character`, `scan`, `qr`, `print` Domain별로 구성하고 공통 기능은 `global`에 둔다.

Controller는 입력 검증, 인증 Actor 식별, Service 호출, DTO 응답만 담당한다. Business Rule과 Transaction은 Service 또는 Domain에 둔다. Controller에서 Repository를 직접 호출하거나 API에 JPA Entity를 노출하지 않는다. Schema 변경은 항상 Flyway를 사용하며 `ddl-auto=update`를 사용하지 않는다.

## 핵심 불변식

- Game State, Role, 권한, Timer 판정은 서버가 결정한다.
- HIDER는 Game당 Character 하나만 가지며 제출된 Character에는 서버가 생성한 고유 `qrToken` 하나가 존재한다.
- 디자인 제한시간이 끝나면 Frontend가 현재 Canvas를 잠그고 기존 제출 API를 한 번 호출한다. Draft 저장은 하지 않는다.
- 숨기기 시간 종료, 모든 HIDER 준비 완료, HOST의 명시적 요청을 모두 만족해야 탐색을 시작한다.
- 앞면 Character와 뒷면 QR은 동일한 PrintSlot과 Cutting Area를 사용한다.
- `SEEKING` 상태의 SEEKER만 원자적 `HIDDEN → FOUND` 전이를 수행하며 동시 Scan은 하나만 성공한다.
- PostgreSQL에는 이미지 Binary가 아닌 URL 또는 Storage Key만 저장한다. QR에 ID나 개인정보를 넣지 않는다.

## 명령어, 스타일, 테스트

`docker compose up -d postgres`로 PostgreSQL을 먼저 실행한 뒤 `./gradlew bootRun`, `./gradlew test`, `./gradlew build`와 저장소의 Gradle Wrapper를 사용한다. Package는 소문자, Class는 PascalCase, Method와 Field는 camelCase, Constant는 UPPER_SNAKE_CASE를 사용한다. `RoomController`, `GameService`, `CreateRoomRequest`처럼 역할이 드러나는 이름을 사용한다.

Business Rule은 빠른 Service Test로 검증하고 Flyway, Constraint, Repository, Transaction, 인증, HTTP Contract, Scan 동시성은 Integration Test로 검증한다. 권한, 상태, 잘못된 입력, DB Constraint와 관련 테스트가 모두 처리되고 전체 테스트가 통과해야 완료다.

## 작업 절차와 변경 기록

작업 전에 `architecture.md`, 기존 코드와 테스트, `modify.md`의 최근 기록을 확인한다. 가장 작은 일관된 변경만 수행하고 관련 없는 Refactoring은 피한다. 코드, 설정, 의존성, Schema, API 또는 테스트를 변경했다면 기존 내용을 지우지 말고 `modify.md` 끝에 목적, 변경 파일과 동작, 실행한 명령과 결과, 남은 제한사항을 기록한다. 분석만 한 작업은 기록하지 않아도 된다.

Commit은 짧은 명령형으로 작성한다. Pull Request에는 동작 설명, 관련 Issue, Migration 및 설정 변경, 테스트 증거를 포함하고 사용자에게 보이는 변경에는 Screenshot 또는 Sample Response를 첨부한다.
