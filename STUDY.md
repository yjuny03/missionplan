# MissionPlan 만든 과정 정리 (공부용)

이 문서는 "이 프로젝트를 어떻게 만들었고, 왜 이렇게 만들었는지"를 남한테 설명할 수 있도록
정리한 것이다. [SPEC.md](SPEC.md)가 "무엇을 만들지"에 대한 확정본이라면, 이 문서는
"그걸 코드로 어떻게 옮겼는지"에 대한 설명이다.

---

## 1. 한 줄 요약

2인 스터디용 7일 주기 미션 트래커. 각자 미션을 등록하고 체크하면, 못 지킨 만큼 하루
1,000원씩(최대 7,000원) 벌금이 쌓이고, 주기가 끝나면 적게 낸 사람이 총액을 받는다.
팀원과 서로 다른 곳에서 접속해야 해서, **앱은 각자 자기 PC에서 실행하고 DB만 인터넷에
있는 공용 Postgres(Neon)를 공유**하는 구조로 만들었다.

---

## 2. 기술 스택과 "왜 이걸 골랐는가"

| 항목 | 선택 | 이유 |
|---|---|---|
| Java | 21 | Spring Boot 4.1 + Java 25 조합은 너무 최신이라 레퍼런스가 적었다. 안정적인 조합(3.5.x + 21)으로 낮췄다 |
| Spring Boot | 3.5.4 | 위와 동일한 이유 |
| 화면 | Thymeleaf (서버사이드 렌더링) | 화면이 몇 장 안 되는(시작/대시보드/캘린더) 소규모 프로젝트에 React 같은 SPA는 과하다. 서버가 HTML을 완성해서 내려주는 방식이 훨씬 단순하다 |
| DB | PostgreSQL (Neon 무료 티어) | H2 같은 로컬 파일 DB로는 두 사람이 데이터를 공유할 수 없다. Neon은 카드 등록 없이 무료로 쓸 수 있는 클라우드 Postgres다 |
| 인증 | 초대코드 + 쿠키 + 필터 1개 | Spring Security는 로그인 폼, 권한 체계, CSRF 설정 등 이 프로젝트 규모에 비해 설정할 게 너무 많다. 그냥 랜덤 토큰을 쿠키에 담아 누구인지 식별하는 것으로 충분했다 |
| CSS | 순수 CSS 1개 파일 (`style.css`) | Bootstrap 같은 프레임워크를 붙이면 클래스 이름 외우는 비용이 더 크다. 화면이 몇 장 안 되니 직접 짜는 게 빠르다 |
| JS | **아예 안 씀** | 모든 상호작용(미션 추가, 체크, 날짜 이동)은 `<form>` 태그의 GET/POST 요청으로 처리한다. 페이지 전체가 새로고침되지만, 그 대신 "JS 코드가 서버 상태랑 안 맞아서 생기는 버그"가 원천적으로 없다 |

---

## 3. 프로젝트 구조

```
src/main/java/idusw/sb/missionplan/
├─ domain/       Room, Member, Mission           ← JPA 엔티티 (DB 테이블과 1:1)
├─ repo/         RoomRepository 등 3개            ← DB 조회만 담당 (Spring Data JPA)
├─ settle/       Cycle, PenaltyCalculator         ← 벌금 계산 (순수 함수, 이 프로젝트의 핵심)
├─ room/         RoomService, 예외 2개            ← 방 생성/참여 비즈니스 로직
├─ mission/      MissionService, 예외 1개         ← 미션 추가/체크/수정/삭제 비즈니스 로직
└─ web/          Controller 3개 + 필터 1개        ← HTTP 요청을 받아 서비스를 호출하고 화면을 고름
                 (RoomController, RoomDashboardController, CalendarController)

src/main/resources/
├─ templates/    home, dashboard, calendar,
│                fragments/nav, fragments/mission-item              ← Thymeleaf 화면
├─ static/       style.css
└─ application.properties                        ← DB 연결, 포트 등 설정
```

**컨트롤러가 처음엔 5개였다가 3개로 줄었다.** 원래 오늘/주간/정산 화면을 각각
TodayController/WeekController/SettleController로 나눴는데, 세 화면을 오가는 게
불편하다는 피드백을 받고 `RoomDashboardController` 하나로 합쳤다(8-2 참고). 화면
개수와 컨트롤러 개수가 항상 1:1일 필요는 없다 — 오히려 한 화면에 여러 정보 조각을
같이 보여줄 땐 컨트롤러도 합쳐야 데이터를 한 번에 모아서 효율적으로 넘길 수 있다.

**계층을 나눈 이유**: Controller가 직접 DB에 접근하거나 벌금을 계산하게 하면, 화면 로직과
비즈니스 로직이 뒤섞여서 테스트하기 어려워진다. 특히 `settle` 패키지의 계산 로직은
**Spring이나 DB 없이도 실행되는 순수 함수**로 분리했는데, 덕분에 단위 테스트가 아주
빠르고(밀리초 단위) 확실하게 돌아간다.

---

## 4. 데이터 모델: 왜 테이블이 3개뿐인가

```
Room    : id, name, inviteCode, startDate
Member  : id, roomId, nickname, token
Mission : id, roomId, memberId, targetDate, title, done, doneAt
```

**벌금을 저장하는 테이블이 없다.** 처음엔 "누가 얼마 벌금 냈는지" 기록하는 테이블을
따로 둘까 생각할 수 있는데, 그러면 문제가 생긴다. 미션 체크를 해제하면(`완료 취소`)
벌금도 다시 계산해야 하는데, 저장된 벌금 값과 실제 상태가 어긋나는(동기화 안 되는)
버그가 생기기 쉽다. 그래서 **미션의 완료 여부(`done`, `doneAt`)만 저장하고, 벌금은
화면을 그릴 때마다 그 자리에서 계산**한다. 계산이 워낙 가벼워서(하루 1,000원 곱셈 수준)
성능 문제도 없다.

**"주기(Cycle)" 테이블도 없다.** "3주차"라는 개념을 테이블에 저장하지 않고, `Room.startDate`
(방 시작일)로부터 날짜 계산만으로 구한다. 자세한 건 5번 항목 참고.

---

## 5. 핵심 아이디어 ①: 주기(Cycle) 계산

[Cycle.java](src/main/java/idusw/sb/missionplan/settle/Cycle.java)

```java
public static LocalDate startDate(LocalDate roomStartDate, LocalDate targetDate) {
    long cycleIndex = Math.floorDiv(ChronoUnit.DAYS.between(roomStartDate, targetDate), 7);
    return roomStartDate.plusDays(cycleIndex * 7);
}
```

방 시작일과 어떤 날짜 사이의 일수 차이를 7로 나누면 "몇 번째 주기인지"가 나온다.
`Math.floorDiv`를 쓴 이유는, 나눗셈 결과가 음수일 때도(이론상 방 시작 전 날짜를 넣는
경우) 올바르게 내림 처리되기 때문이다. 자바의 기본 나눗셈(`/`)은 음수를 0 방향으로
버림하는데, `floorDiv`는 항상 더 작은 쪽으로 내림한다.

이 방식 덕분에 **"주기가 끝나면 뭘 해야 하나"라는 로직이 아예 없다.** 다음 주 날짜로
미션을 등록하면 그 계산식이 자동으로 다음 주기로 인식한다. Room을 매주 새로 만들 필요가
없다는 뜻이다.

---

## 6. 핵심 아이디어 ②: 벌금 계산 (PenaltyCalculator)

이 프로젝트에서 **가장 많이 검증한 코드**다. [PenaltyCalculator.java](src/main/java/idusw/sb/missionplan/settle/PenaltyCalculator.java)

```java
public static int penaltyFor(Mission mission, LocalDate today, LocalDate cycleEndDate) {
    LocalDate cutoff = mission.isDone()
            ? mission.getDoneAt().minusDays(1)
            : earlierOf(today.minusDays(1), cycleEndDate);

    long daysLate = ChronoUnit.DAYS.between(mission.getTargetDate(), cutoff) + 1;
    return (int) (Math.max(0, daysLate) * 1000);
}
```

### 어떻게 읽는가

- **완료한 미션**: `cutoff`를 완료일 하루 전으로 잡는다. 그래서 당일 완료하면
  `cutoff = targetDate - 1`이 되고, `daysLate = -1 + 1 = 0`이 되어 자동으로 0원이 나온다.
  "당일 완료면 무조건 0원"이라는 규칙을 위해 따로 if문을 안 써도 된다.
- **미완료 미션**: `cutoff`를 "어제"와 "주기 종료일" 중 더 이른 날짜로 잡는다.
  - 아직 주기가 안 끝났으면 `어제`가 기준이 된다. 오늘 날짜는 셈에 안 들어가므로,
    등록한 당일에는 항상 0원이다(유예).
  - 주기가 이미 끝났으면(정산 시점) `cycleEndDate`로 고정된다. 그래서 정산 후 아무리
    시간이 지나도 금액이 더 안 불어난다. 이 부분은 `PenaltyCalculatorTest`에
    `penaltyNeverExceedsCapEvenLongAfterCycleEnd`라는 테스트로 따로 박아뒀다.
- **최대 7,000원**이라는 규칙도 따로 코드로 안 짰다. 한 주기가 7일이니까, 미션 날짜와
  주기 종료일 사이 최대 간격이 7일이라서 계산식 자체가 7을 넘을 수가 없다.

### 왜 순수 함수로 만들었나

이 메서드는 DB도, Spring도, 웹 요청도 필요 없다. `Mission` 객체 하나와 날짜 두 개만
넣으면 답이 나온다. 그래서 테스트가 이렇게 짧다:

```java
Mission mission = new Mission(1L, 1L, MON, "테스트 미션");
mission.complete(WED);
assertThat(PenaltyCalculator.penaltyFor(mission, WED, CYCLE_END)).isEqualTo(2000);
```

서버를 띄우거나 DB에 연결할 필요 없이, `./gradlew test` 한 번이면 이 계산 규칙이
안 깨졌는지 몇 초 안에 확인된다. [SPEC.md](SPEC.md) §3.3의 검산표를 그대로
[PenaltyCalculatorTest.java](src/test/java/idusw/sb/missionplan/settle/PenaltyCalculatorTest.java)의
테스트 케이스로 옮겨놨다 — 표 1행이 테스트 1개에 대응한다.

---

## 7. 인증: 로그인 없는 로그인

이 앱에는 아이디/비밀번호가 없다. 대신:

1. 방을 만들거나 참여하면 서버가 `UUID.randomUUID()`로 랜덤 토큰을 만들어서
   `Member.token`에 저장하고, 같은 값을 브라우저 쿠키(`token`)에도 심는다.
2. 이후 요청마다 이 쿠키를 읽어서 "이 사람이 누구인지" 알아낸다.

### MemberSessionFilter — 문지기 역할

[MemberSessionFilter.java](src/main/java/idusw/sb/missionplan/web/MemberSessionFilter.java)는
`/room/**` 로 시작하는 모든 요청을 가로챈다 (`shouldNotFilter`에서 그 외 경로는 통과시킴).

```java
protected void doFilterInternal(request, response, chain) {
    String inviteCode = extractInviteCode(request.getRequestURI());   // URL에서 초대코드 뽑기
    Optional<Room> room = roomRepository.findByInviteCode(inviteCode);
    Optional<Member> member = findMemberByCookie(request);            // 쿠키로 멤버 찾기

    boolean isMemberOfThisRoom = room.isPresent() && member.isPresent()
            && member.get().getRoomId().equals(room.get().getId());

    if (!isMemberOfThisRoom) {
        response.sendRedirect("/");   // 셋 중 하나라도 안 맞으면 홈으로
        return;
    }

    request.setAttribute("currentRoom", room.get());     // 컨트롤러가 다시 조회 안 해도 되게
    request.setAttribute("currentMember", member.get());
    chain.doFilter(request, response);
}
```

**필터(Filter)란**: 서블릿(웹 요청을 처리하는 자바 표준 컴포넌트)에 요청이 도달하기
*전에* 가로채서 처리하는 컴포넌트다. 여기서는 "이 사람이 이 방의 멤버가 맞는지"를
모든 컨트롤러 메서드마다 반복해서 검사하지 않도록, 입구에서 한 번에 걸러준다.
검증에 성공하면 `request.setAttribute`로 Room/Member를 실어서 넘기고, 각 컨트롤러는
`request.getAttribute(...)`로 다시 꺼내 쓴다 — DB 재조회 없이.

### 재접속(reissue) — 쿠키를 잃어버린 경우

개발 중에 실제로 겪은 문제인데, 브라우저가 바뀌거나 쿠키가 지워지면 그 사람은 원래
멤버 자리로 못 돌아온다. `httpOnly` 쿠키라 JS로 복구할 수도 없고, 참여 폼은 원래
무조건 새 멤버를 만들었기 때문에 방 정원(2명)이 금방 차서 영영 못 들어오는 문제였다.

해결: [RoomService.joinRoom()](src/main/java/idusw/sb/missionplan/room/RoomService.java)에서
**정원을 체크하기 전에** 같은 닉네임의 기존 멤버가 있는지 먼저 확인한다.

```java
Member existing = memberRepository.findByRoomIdAndNickname(room.getId(), nickname).orElse(null);
if (existing != null) {
    existing.reissueToken(generateToken());   // 새로 만들지 않고 토큰만 재발급
    return new JoinResult(room, existing);
}
```

비밀번호가 없는 앱이라 닉네임이 곧 신원이라는 전제를 깔았다. 로컬에서 2명이 쓰는
용도라 닉네임을 남이 가로챌 위험보다, "잃어버리면 끝"이 훨씬 큰 문제였다.

---

## 8. 화면별 컨트롤러 요약

### 8-1. RoomController — 방 만들기/참여

`POST /rooms`, `POST /rooms/join`. 여기서 만든 결과를 쿠키에 심는 게 이 컨트롤러의
유일한 책임이다. 실제 방 생성 로직은 `RoomService`에 위임했다(컨트롤러는 얇게 유지).

### 8-2. RoomDashboardController — 대시보드 (`/room/{code}`)

가장 많이 갈아엎은 화면이다. 처음엔 오늘/주간/정산을 TodayController·WeekController·
SettleController로 따로 만들었는데, 실제로 써보니 화면을 오가는 게 불편하다는
피드백을 받고 컨트롤러 하나로 합쳤다. 이 컨트롤러가 대시보드에 필요한 조각 4개를
전부 조립한다: 오늘 체크리스트, 주간 요약 표, 요일별 카드 7장, 정산 요약.

**① 오늘 체크리스트 규칙.** SPEC 문장만으로는 "오늘 화면에 뭘 보여줄까"를 정할 수
없어서, 미리 만들어둔 UI 목업을 다시 보고 규칙을 정했다:

> **이번 주기에서 아직 안 끝난 것 전부 + 오늘 끝낸 것만 보여준다.**

```java
public List<Mission> filterActive(List<Mission> candidates, LocalDate today) {
    return candidates.stream()
            .filter(m -> !m.isDone() || today.equals(m.getDoneAt()))
            .sorted(Comparator.comparing(Mission::getTargetDate).thenComparing(Mission::getId))
            .toList();
}
```

그저께 등록한 미션을 아직 안 끝냈으면 오늘도 계속 보인다(밀림 배지와 함께). 어제
끝낸 건 화면에서 빠지고 주간 보드/캘린더로 넘어간다 — 안 그러면 끝낸 일이 계속
쌓여서 화면이 길어진다. 상대방 영역(`partnerTodayMissions`)은 반대로 **오늘 날짜
항목만** 단순하게 보여준다. 밀림 배지도 없다 — 매일 압박을 주는 건 "내 미션"으로
충분하고, 상대방 화면까지 압박용 정보를 넣으면 부담스러워진다는 판단이었다.

**② 페이지 하나에 쿼리 20번 → 2번.** 처음 구현은 "요일 칸 하나, 상대방 목록 하나"
단위로 그때그때 DB를 조회했다. 세어보니 대시보드 하나 여는 데 왕복이 22번이었다
(오늘 체크 2 + 주간표 7일×2명=14 + 정산 3 + 중복 조회 등). Neon처럼 지연 있는
원격 DB에서는 이것만으로 로딩이 몇 초씩 걸렸다 — 전형적인 N+1 쿼리 문제다
(자세한 진단 과정은 TROUBLESHOOTING.md #4). "요일마다 따로"가 아니라 **"방 전체를
한 번에 가져와서 메모리에서 나눠 쓰기"**로 바꿨다.

```java
Map<Long, Map<LocalDate, List<Mission>>> missionsByMember = missionRepository
        .findByRoomIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(room.getId(), rangeStart, rangeEnd)
        .stream()
        .collect(Collectors.groupingBy(Mission::getMemberId, Collectors.groupingBy(Mission::getTargetDate)));
```

`Collectors.groupingBy`를 두 번 중첩해서 `멤버 ID → 날짜 → 미션 목록` 구조로
한 번에 정리한다. 이후 오늘 체크리스트/주간 표/요일 카드/정산까지 전부 이 맵에서
`getOrDefault(date, List.of())`로 꺼내 쓴다. DB는 한 번도 더 안 두드린다. 쿼리
22번이 2번(멤버 목록 1 + 미션 통짜 조회 1)으로 줄었다.

**③ 요일 카드 7장을 클릭 없이 전부 펼친다.** 처음엔 날짜를 클릭해야 그 날짜의
등록 폼과 목록이 나오는 구조였다(URL에 `?day=2026-08-19`가 붙고, 그 값이 있을 때만
상세를 모델에 추가). 그런데 한 주 계획을 몰아서 짜려는 사람 입장에선 "클릭 →
페이지 로딩 → 등록 → 페이지 로딩"을 7번 반복해야 해서 불편했다. ②에서 쿼리를 이미
다 배치로 가져오고 있었기 때문에, 굳이 한 날짜씩 골라 보여줄 이유가 없다는 걸 깨닫고
**7일치 카드를 처음부터 다 렌더링**하도록 바꿨다 — 데이터는 이미 메모리에 있으니
쿼리가 하나도 늘지 않는다.

```java
private void addWeekDayLists(LocalDate cycleStart, LocalDate today, Map<LocalDate, List<Mission>> myByDate,
                              Map<LocalDate, List<Mission>> partnerByDate, Model model) {
    List<DayList> dayLists = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
        LocalDate date = cycleStart.plusDays(i);
        dayLists.add(new DayList(date, date.format(DATE_LABEL), date.equals(today),
                myByDate.getOrDefault(date, List.of()),
                partnerByDate.getOrDefault(date, List.of())));
    }
    model.addAttribute("dayLists", dayLists);
}
```

위쪽 요약 표(완료수/전체수)의 요일 헤더는 이제 서버에 아무것도 안 물어보는
**페이지 내 앵커 링크**로 바꿨다 — `<a href="#day-2026-08-19">`. 클릭하면 그
카드로 스크롤만 이동하고 네트워크 요청이 아예 없다.

```java
missionService.addMission(room.getId(), me.getId(), targetDate, title.trim());
```

`MissionService.addMission()`은 처음부터 날짜를 파라미터로 받게 만들어뒀기 때문에,
"오늘 전용 등록"에서 "아무 날짜나 등록"으로, 다시 "요일 카드 7개 각각 등록"으로
용도가 바뀌는 동안 이 메서드는 한 글자도 안 고쳤다 — 처음부터 좁게 만들지 않은 덕이다.

**④ 정산 요약.** 각 멤버의 그 주기 미션을 순회하며 `PenaltyCalculator.penaltyFor()`를
그대로 합산한다. 새로운 계산 로직이 없다 — 이미 검증된 함수를 여러 번 부르는 것뿐이다.

```java
int total = 0;
for (Mission mission : missions) {
    int penalty = PenaltyCalculator.penaltyFor(mission, today, cycleEnd);
    total += penalty;
    if (penalty > 0) lateItems.add(new LateItem(mission.getTitle(), penalty / 1000, penalty));
}
```

수령자 결정도 단순 비교다: `총액이 더 적은 쪽이 수령`, 같으면 `tie = true`. 원래
목업은 "내 내역"만 보여줬는데, SPEC의 "금액만 보여주면 서로 납득이 안 된다"는
문장을 다시 읽고 **양쪽 다 항목별 내역을 보여주는 쪽으로 바꿨다.** 최종 정산에서
"왜 내가 8천원인지" 상대방이 확인할 수 있어야 다툼이 안 생긴다고 판단했다. 이
패널은 대시보드에서 **화면에 보이는 주기(week)와 연동**된다 — 주간 표에서 "이전
주"로 넘기면 정산 숫자도 그 주 걸로 바뀐다. 항상 "지금 보고 있는 것 = 정산 숫자"가
되도록 같은 `cycleStart`/`cycleEnd`를 공유해서 계산한다.

### 8-3. CalendarController — 캘린더 (`/room/{code}/calendar`)

한 달치 날짜 그리드를 만들고, 각 날짜에 "완료(done) / 미완료(late) / 없음(none)"
셋 중 하나를 매긴다.

```java
private String statusFor(LocalDate date, LocalDate today, List<Mission> missions) {
    if (missions.isEmpty()) return "none";
    boolean allDone = missions.stream().allMatch(Mission::isDone);
    if (allDone) return "done";
    return date.isBefore(today) ? "late" : "none";   // 오늘이면 아직 유예
}
```

오늘 날짜인데 미완료 항목이 있어도 "late"로 표시하지 않는다 — 아직 하루가 안
끝났으니 실패로 단정할 수 없다는, `PenaltyCalculator`의 "당일 유예" 원칙을
캘린더 화면에도 그대로 반영한 것이다.

월요일 시작 그리드를 만들기 위해, 매달 1일의 요일에 따라 앞쪽에 빈 칸을 채우고
(`leadingBlanks`), 7의 배수가 될 때까지 뒤에도 빈 칸을 채운 다음, 7개씩
끊어서(`weeks`) 표를 그린다. Thymeleaf 안에서 이 계산을 하면 복잡해지므로,
컨트롤러에서 미리 `List<List<DayCell>>`로 다 만들어서 넘긴다.

---

## 9. 화면(Thymeleaf) 쪽 설계

### 왜 JS를 하나도 안 썼나

체크박스 하나 토글하는 데도 보통은 JS로 `fetch()`를 날리고 DOM을 갱신하는 방식을
쓰는데, 여기서는 **각 미션 옆에 작은 `<form>`을 두고 버튼을 누르면 그 폼이
그대로 POST 요청을 보내고 페이지가 다시 로드**된다.

```html
<form th:action="@{/room/{code}/missions/{id}/toggle(code=${room.inviteCode},id=${mission.id})}"
      method="post" style="display:inline">
    <button type="submit" th:text="${mission.done} ? '완료 취소' : '완료로 표시'"></button>
</form>
```

느리게 느껴질 수 있지만(매번 새로고침), 이 프로젝트 규모에서는 그게 오히려 장점이다.
"화면에 보이는 상태"와 "서버에 저장된 상태"가 어긋날 일이 구조적으로 없다 — 항상
서버가 마지막으로 그린 HTML이 곧 진실이다.

### 프래그먼트로 중복 제거

대시보드/캘린더 화면에 똑같이 들어가는 상단 탭 메뉴를
[fragments/nav.html](src/main/resources/templates/fragments/nav.html) 하나로 빼고,
각 화면에서 이렇게 불러 쓴다.

```html
<div th:replace="~{fragments/nav :: nav(${room.inviteCode}, 'dashboard')}"></div>
```

Thymeleaf의 fragment는 "재사용 가능한 HTML 조각에 이름을 붙이는 기능"이다.
`nav(code, active)`처럼 파라미터도 받을 수 있어서, 마치 함수처럼 각 화면에서 자기
`room.inviteCode`와 "지금 이 화면이 어디인지"(`'dashboard'` 또는 `'calendar'`)를
넘긴다. `active`로 지금 보고 있는 탭에 `class="active"`를 붙여서 색을 채운다.

같은 방식으로 [fragments/mission-item.html](src/main/resources/templates/fragments/mission-item.html)도
만들었다. 미션 한 줄마다 붙는 완료/수정/삭제 버튼 3종 세트를 오늘 체크리스트와
주간 보드의 요일 카드 양쪽에서 똑같이 써야 했는데, 마크업을 두 번 베껴 쓰는 대신
`actions(mission, code, start)` 프래그먼트 하나로 빼서 양쪽에서 호출한다.
수정/삭제는 JS 없이 `<details>`/`<summary>` 태그로 구현했다 — 클릭하면 브라우저가
알아서 펼치고 접어주는 HTML 표준 기능이라, "삭제 누르면 확인 버튼이 한 번 더
뜨는" 동작을 자바스크립트 한 줄 없이 만들 수 있다.

### 에러 메시지 전달 (Flash Attribute)

폼 검증 실패(예: 빈 미션 이름) 시 `redirect:/` 로 돌아가면서 에러 메시지를
같이 들고 가야 하는데, 리다이렉트는 새 요청이라 원래는 값을 못 들고 간다.
Spring의 `RedirectAttributes.addFlashAttribute()`가 이걸 해결해준다 —
서버 세션에 잠깐 담아뒀다가, **바로 다음 요청 한 번에만** 자동으로 꺼내서
모델에 넣어준다. 그래서 컨트롤러 쪽 코드는 이렇게 간단하다.

```java
redirectAttributes.addFlashAttribute("error", "미션 내용을 입력해주세요.");
return "redirect:/room/" + code;
```

```html
<p th:if="${error}" th:text="${error}" class="error"></p>
```

---

## 10. 데이터베이스: 왜 이런 배포 구조인가

터널링(ngrok, Cloudflare Tunnel 등)으로 "내 PC를 서버로 쓰는" 방법도 있었지만,
그러면 **내 PC가 꺼지면 팀원이 접속을 못 한다.** 대신 이렇게 했다:

```
[내 PC: 앱 실행]  ──┐                      ┌──  [팀원 PC: 앱 실행]
                    └──→  Neon Postgres  ←──┘
```

각자 자기 PC에서 `bootRun`으로 앱을 띄우고, 둘 다 인터넷 어딘가에 있는 같은
Postgres DB를 바라본다. 앱 코드는 완전히 로컬인데, 데이터만 공유된다.

### DB 접속 정보는 코드에 없다

```properties
spring.datasource.url=${DB_URL}
```

비밀번호가 든 연결 문자열을 `application.properties`에 직접 쓰면 git에
커밋되어 유출된다. `${DB_URL}`은 **환경 변수**를 가리키는데, 실제 값은
IntelliJ의 실행 구성(Run Configuration)에만 등록되어 있고 코드 저장소에는
안 들어간다. `.gitignore`에도 `.env`, `application-local.properties` 같은
패턴을 미리 막아뒀다.

### Hikari(커넥션 풀) 설정을 튜닝한 이유

```properties
spring.datasource.hikari.minimum-idle=0
spring.datasource.hikari.idle-timeout=60000
spring.datasource.hikari.maximum-pool-size=3
```

Neon 무료 티어는 "컴퓨트 사용 시간" 100시간/월이 한도이고, 5분간 아무 연결이
없으면 자동으로 잠들어서 시간을 안 깎아먹는다. 그런데 커넥션 풀(HikariCP,
Spring Boot 기본 DB 연결 풀)이 연결을 계속 쥐고 있으면 DB가 못 잠든다.
`minimum-idle=0`은 "쓰지 않을 때는 유휴 연결을 0개로 줄여도 된다"는 뜻이고,
`idle-timeout=60000`은 "1분 이상 안 쓴 연결은 반납한다"는 뜻이다. 이 설정이
없으면 앱을 하루 종일 켜두는 것만으로 무료 한도가 며칠 안에 소진된다.

---

## 11. 테스트 전략: 두 개의 다른 DB

이 프로젝트에는 DB가 두 종류 등장한다.

| 상황 | DB | 이유 |
|---|---|---|
| `bootRun` (실제 실행) | Neon Postgres | 팀원과 데이터를 공유해야 하니까 |
| `test` (단위 테스트) | H2 (인메모리) | 테스트가 실제 계정에 의존하면 안 되니까 |

처음엔 테스트도 `DB_URL`을 그대로 썼는데, 그러면 `DB_URL`이 없는 환경(팀원의 다른
PC, 나중에 CI를 붙인다면 그곳)에서 테스트가 무조건 실패한다. `src/test/resources/application.properties`를
따로 만들어서 테스트에서만 H2를 쓰게 분리했다.

```properties
spring.datasource.url=jdbc:h2:mem:missionplan;MODE=PostgreSQL
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
```

Spring Boot는 클래스패스에서 `application.properties`를 찾을 때 **test 리소스가
main 리소스보다 우선순위가 높아서**, 테스트를 돌릴 때는 이 파일이 알아서 적용되고
`bootRun`에는 영향이 없다.

---

## 12. 겪었던 문제 4가지 (요약)

자세한 원인·해결 과정은 [TROUBLESHOOTING.md](TROUBLESHOOTING.md) 참고.

1. **테스트가 실제 Neon DB에 의존해서 실패** → H2 인메모리 DB로 테스트 분리 (11번 항목)
2. **`GET /;jsessionid=...` → 404** → Spring Boot 3.x의 새 경로 매칭기(`PathPatternParser`)가
   jsessionid가 붙은 URL을 처리 못 해서 생긴 문제. `server.servlet.session.tracking-modes=cookie`로
   세션을 URL이 아닌 쿠키로만 추적하게 해서 해결
3. **쿠키를 잃은 멤버가 방에 영영 재접속 못함** → 참여 로직에 "같은 닉네임이면 재접속으로
   처리" 분기 추가 (7번 항목의 reissue 로직)
4. **대시보드 페이지 하나에 DB 왕복이 22번** → 팀원이 "느리다"고 보고한 걸 그냥 넘기지
   않고 코드로 직접 세어봐서 원인을 확정했다. 요일·사람별로 따로 조회하던 걸 방 전체
   기준 조회 1번으로 합치고 메모리에서 나눠 써서 2번으로 줄임 (8-2 항목)

네 가지 다 "브라우저로 실제 시나리오를 눌러봐야만(또는 실사용해봐야만) 발견되는"
종류의 문제였다. 컴파일이 되고 단위 테스트가 통과해도 실제 동작·성능은 다를 수
있다는 걸 보여주는 사례들이다.

---

## 13. 누가 물어보면 이렇게 설명하면 됨

**Q. 왜 벌금을 DB에 저장 안 해요?**
A. 미션 완료 여부만 저장하고 벌금은 그때그때 계산해요. 저장하면 체크 해제할 때
   동기화 버그가 생기기 쉽거든요. 계산 자체가 순수 함수라 매번 계산해도 부담 없어요.

**Q. 로그인은 어떻게 처리해요?**
A. 아이디/비밀번호 없이, 방 참여할 때 랜덤 토큰을 쿠키에 심고 그걸로 식별해요.
   Spring Security는 이 정도 규모엔 과해서 필터 하나로 직접 짰어요.

**Q. 왜 React 안 쓰고 Thymeleaf예요?**
A. 화면이 몇 장 안 돼서 서버가 완성된 HTML을 내려주는 게 더 단순해요. 상호작용도
   전부 폼 제출(새로고침)로 처리해서 프론트-백엔드 상태 불일치 문제가 아예 없어요.

**Q. 로딩이 느렸다면서요? 어떻게 고쳤어요?**
A. 페이지 하나 열 때 DB를 22번 왕복하고 있었어요. 요일 칸 하나, 사람 한 명마다
   따로따로 조회했거든요. 방 전체 데이터를 한 번에 가져와서 자바 코드로 메모리에서
   나눠 쓰는 방식으로 바꿔서 2번으로 줄였어요. "무료 DB라 느리다"가 아니라 "그 DB를
   너무 많이 두드리는 코드"가 진짜 원인이었던 거죠.

**Q. 팀원이랑 어떻게 데이터를 공유해요?**
A. 앱은 각자 자기 컴퓨터에서 실행하고, DB만 인터넷에 있는 무료 Postgres(Neon)를
   같이 봐요. 한쪽 컴퓨터가 꺼져도 상대방은 계속 쓸 수 있어요.

**Q. 테스트는 어떻게 짰어요?**
A. 벌금 계산 규칙표를 그대로 테스트 케이스로 옮겼어요. 서버 없이 순수 함수만
   테스트하니까 몇 초 안에 다 돌아가고, 규칙이 깨지면 바로 잡혀요.
