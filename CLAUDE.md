# CLAUDE.md

2인 스터디용 7일 주기 미션 트래커. Spring Boot + Thymeleaf 서버렌더.

## 먼저 읽을 것

**[SPEC.md](SPEC.md)에 모든 규칙이 있다.** 벌금 계산, 정산 방식, 화면 구성, 데이터 모델,
구현 순서까지 확정되어 있으니 작업 전에 반드시 읽는다. 스펙에 없는 결정이 필요하면
임의로 정하지 말고 물어본다.

**[TROUBLESHOOTING.md](TROUBLESHOOTING.md)에 겪었던 문제와 해결법이 있다.** 이상 동작을
마주치면 새로 헤매기 전에 먼저 여기서 찾아본다. 새로운 문제를 해결하면 원인과 해결법을
자세히 적어 이 파일에 추가한다.

## 지켜야 할 제약

- **Spring Security를 쓰지 않는다.** 인증은 초대코드 + 쿠키 + 필터 1개로 끝낸다.
- **벌금을 테이블에 저장하지 않는다.** 미션 상태만 저장하고 매번 계산한다.
- **주기(Cycle) 테이블을 만들지 않는다.** `Room.startDate` 기준 나눗셈으로 구한다.
- **DB 접속 정보를 커밋하지 않는다.** `spring.datasource.url=${DB_URL}` 형태로만 쓴다.
- **JS 프레임워크를 도입하지 않는다.** Thymeleaf + 단일 `style.css`.

## 벌금 계산

`PenaltyCalculator`가 이 프로젝트의 핵심이다. SPEC §3.3 검산표가 곧 테스트 케이스이며,
표와 테스트는 1:1로 대응해야 한다. 규칙을 바꿀 일이 생기면 표를 먼저 고친다.

## 명령어

```bash
./gradlew test
```

```bash
./gradlew bootRun
```

## 현재 상태

Spring Initializr 직후의 빈 프로젝트다. `build.gradle`이 아직 Java 25 / Boot 4.1.0이므로
SPEC §9의 1단계에서 Java 21 / Boot 3.5.x로 낮추는 것부터 시작한다.
