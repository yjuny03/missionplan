# 트러블슈팅 기록

작업 중 발생한 문제와 원인, 해결 방법을 시간순으로 기록한다.
같은 문제를 다시 마주쳤을 때 여기부터 찾아본다.

---

## 1. `./gradlew test`가 `contextLoads()`에서 실패 — 실제 Neon DB에 의존

**발생 시점:** SPEC §9 3단계(`PenaltyCalculator` + 단위 테스트) 완료 후,
전체 테스트 스위트를 돌려서 확인하던 중.

### 증상

`PenaltyCalculator`/`Cycle` 테스트 13개는 다 통과했는데, Spring Initializr가 기본
생성해준 `MissionplanApplicationTests.contextLoads()`가 실패했다.

```
MissionplanApplicationTests > contextLoads() FAILED
    java.lang.IllegalStateException at DefaultCacheAwareContextLoaderDelegate.java:180
        Caused by: org.springframework.beans.factory.BeanCreationException ...
            Caused by: java.lang.IllegalArgumentException at Assert.java:116

14 tests completed, 1 failed
```

### 원인

`contextLoads()`는 `@SpringBootTest`로 **스프링 컨테이너 전체를 실제로 띄우는** 테스트다.
컨테이너를 띄우려면 `DataSource` 빈이 만들어져야 하는데, `application.properties`의
`spring.datasource.url=${DB_URL}`이 참조하는 `DB_URL` 환경변수가 **그 테스트를 실행한
셸(PowerShell)에는 없었다.** `DB_URL`은 IntelliJ 실행 구성에만 등록해뒀기 때문에,
터미널에서 `./gradlew test`를 돌리면 `DataSource` 생성 단계에서 URL이 null이라 예외가 났다.

더 근본적인 문제는 따로 있었다. 설령 그 셸에 `DB_URL`을 넣어서 당장의 실패는 없앨 수
있었더라도, **단위 테스트가 실제 운영 Neon 계정에 접속해야만 통과하는 구조** 자체가
잘못됐다. CLAUDE.md에 `./gradlew test`를 기본 명령으로 박아뒀는데, 이게 팀원 PC나
CI처럼 `DB_URL`이 없는 모든 환경에서 항상 실패하게 된다.

### 해결

테스트 스코프에서만 쓰는 인메모리 H2 DB를 추가해서, 테스트가 외부 서비스와 완전히
분리되도록 했다.

`build.gradle`:
```gradle
testRuntimeOnly 'com.h2database:h2'
```

`src/test/resources/application.properties` (신규 생성):
```properties
spring.datasource.url=jdbc:h2:mem:missionplan;MODE=PostgreSQL
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
```

Spring Boot는 테스트 실행 시 클래스패스 우선순위상 `src/test/resources`의 설정 파일을
`src/main/resources`보다 먼저 읽기 때문에, 위 파일이 있으면 테스트에서는 `DB_URL`을
전혀 참조하지 않고 H2로 붙는다. `bootRun`(실제 실행)은 여전히 `src/main/resources`
설정을 그대로 써서 Neon에 붙는다 — 운영 경로는 손대지 않았다.

### 검증

```
.\gradlew.bat test --console=plain
```

`DB_URL`을 설정하지 않은 셸에서 14개 테스트 전부 통과 확인.

### 상태

해결 완료.

---

## 2. 방 참여 정원 초과(3번째 멤버) 테스트 중 `GET /;jsessionid=...` → 404

**발생 시점:** SPEC §9 4단계(방 생성/참여 + 세션 필터) 브라우저 테스트 중,
이미 2명이 찬 방에 3번째 닉네임으로 참여를 시도했을 때.

### 증상

`/rooms/join` 요청이 `RoomFullException`을 던지고 `redirect:/`로 돌아가야 하는데,
실제로는 아래 URL로 리다이렉트되면서 Whitelabel 404 페이지가 떴다.

```
GET http://localhost:9080/;jsessionid=B49444B4AE7A320A8D7A680CA93F1B17 → 404
```

### 원인

`RoomController.join()`에서 에러 메시지를 `RedirectAttributes.addFlashAttribute("error", ...)`로
전달하는데, Spring MVC의 flash attribute는 기본적으로 **`HttpSession`을 통해** 다음 요청까지
값을 들고 간다. 이 세션을 이번 요청에서 처음 만들다 보니, 서버가 응답을 내려보내는 시점에는
브라우저가 아직 `JSESSIONID` 쿠키를 받아본 적이 없다. 그래서 톰캣이 "이 클라이언트가 쿠키를
지원하는지 확신할 수 없다"고 판단하고, 리다이렉트 URL 끝에 `;jsessionid=...`를 붙이는
**URL 기반 세션 추적(URL rewriting)** 으로 안전장치를 건다.

문제는 여기서부터다. Spring Boot 3.x(Spring Framework 6)는 기본 경로 매칭기를
`PathPatternParser`로 바꿨는데, 이 매칭기는 옛날 `AntPathMatcher` + `UrlPathHelper` 조합과
달리 **경로 뒤에 붙은 `;jsessionid=...` 같은 matrix-variable 스타일 접미사를 자동으로
잘라내지 않는다.** 그 결과 `/;jsessionid=B49...`라는 경로가 `"/"` 매핑과 매치되지 않고
404가 난 것이다.

즉, 두 가지가 겹쳐서 생긴 문제다.
1. flash attribute가 세션을 새로 만들면서 톰캣이 URL에 jsessionid를 붙이려는 상황을 만들었고,
2. Boot 3.x의 새 경로 매칭기가 그 jsessionid 붙은 URL을 처리 못 했다.

### 해결

세션을 아예 URL로 추적하지 못하게 막았다. 우리는 로그인 상태를 Spring의 `HttpSession`이
아니라 **자체 `token` 쿠키**로 관리하고 있어서, JSESSIONID도 쿠키로만 다니게 해도
잃을 기능이 없다.

`application.properties`에 한 줄 추가:

```properties
server.servlet.session.tracking-modes=cookie
```

이러면 톰캣이 세션을 무조건 `JSESSIONID` 쿠키로만 추적하고, URL에 붙이는 fallback을
쓰지 않는다. 브라우저가 쿠키를 막아둔 극단적인 경우엔 flash 메시지가 안 뜰 수 있지만,
그 경우 애초에 우리 `token` 쿠키도 못 받으므로 로그인 자체가 안 된다 — 즉 이 앱은
이미 쿠키를 필수로 요구하는 구조라 손해볼 게 없다.

**재현 조건이 좁다는 점도 기록해둔다.** 이 문제는 "그 브라우저 세션에서 첫 번째로
flash attribute를 쓰는 요청"에서만 재현된다. 세션이 한 번 만들어져서 쿠키를 주고받은
이후로는 jsessionid가 URL에 안 붙는다. 그래서 방 생성(1번째 요청)이나 정상 참여
(2번째 성공한 요청)에서는 안 나타나다가, 하필 첫 에러 케이스(3번째 참여 시도)에서
처음 터졌다 — 에러 처리 경로를 반드시 브라우저로 직접 눌러봐야 하는 이유이기도 하다.

### 상태

해결 완료. 재시작 후 3번째 참여(정원 초과) 재시도 → 404 없이 홈 화면에
"이미 인원이 가득 찬 방입니다." 메시지가 정상 표시되는 것까지 확인했다.

---

## 3. 쿠키를 잃은 멤버는 방에 영영 재접속할 수 없었음

**발생 시점:** SPEC §9 5단계(오늘 화면) 브라우저 테스트 중, 상대방(지훈) 관점에서
"오늘 현황"이 잘 보이는지 확인하려고 로그인 전환을 시도하다가 발견.

### 증상

`token` 쿠키는 `httpOnly`라 JavaScript로 지울 수 없다(의도된 보안 설정). 이 상태에서
"다른 멤버로 전환해서 보고 싶다"는 이유로 초대코드+다른 닉네임으로 다시 `/rooms/join`을
호출했더니, 기존 로직은 **무조건 새 멤버를 만들었다.** 그 결과 방은 금방 2명(정원)이
꽉 찼고, 원래 첫 번째로 만들었던 멤버(지훈)의 쿠키는 이미 덮어써져서 사라진 뒤였다.
이 시점부터는 지훈이 초대코드+"지훈" 닉네임으로 다시 참여를 시도해도
`RoomFullException`("이미 인원이 가득 찬 방입니다")만 뜨고 **영영 자기 미션 목록에
접근할 방법이 없었다.**

### 원인

`RoomService.joinRoom()`이 "이 닉네임으로 이미 참여한 적이 있는지"를 전혀 확인하지 않고,
호출할 때마다 무조건 `new Member(...)`로 새 행을 만들었다. 팀원이 브라우저 데이터를
지우거나 다른 기기로 접속하는 등 쿠키를 잃는 상황은 실사용에서 충분히 일어날 수 있는데,
이 경로가 아예 막혀 있었다.

### 해결

사용자에게 정책을 확인한 뒤(같은 닉네임이면 재접속 허용), `joinRoom()`에 재접속 분기를
추가했다.

1. `MemberRepository.findByRoomIdAndNickname(roomId, nickname)` 추가.
2. `Member`에 `reissueToken(String newToken)` 메서드 추가 — 재접속 시 새 토큰을 발급해서
   예전에 흘러다니던(잃어버린 기기의) 토큰은 자동으로 무효화된다.
3. `joinRoom()`에서 **정원 체크보다 먼저** 같은 닉네임의 기존 멤버가 있는지 확인한다.
   있으면 새로 만들지 않고 그 멤버의 토큰만 재발급해서 반환한다. 없을 때만 기존의
   정원 체크(`MAX_MEMBERS`) + 신규 생성 로직을 탄다.

이 앱에는 비밀번호 개념이 없으므로 닉네임이 곧 신원이다. 로컬에서 2명이 쓰는 소규모
서비스라는 전제상, 닉네임을 남이 가로챌 위험보다 "쿠키 잃으면 끝"이 훨씬 더 큰 문제였다.

### 상태

해결 완료. 방이 2명(지훈/서연)으로 찬 상태에서 "지훈" 닉네임으로 재참여 →
3번째 멤버가 생기지 않고 기존 자리를 되찾았고, "서연님 오늘 현황"에 서연이 등록한
미션이 정확히 보이는 것까지 브라우저로 확인했다.
