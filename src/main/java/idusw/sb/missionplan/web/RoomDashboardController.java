package idusw.sb.missionplan.web;

import idusw.sb.missionplan.domain.Member;
import idusw.sb.missionplan.domain.Mission;
import idusw.sb.missionplan.domain.Room;
import idusw.sb.missionplan.mission.MissionAccessDeniedException;
import idusw.sb.missionplan.mission.MissionService;
import idusw.sb.missionplan.repo.MemberRepository;
import idusw.sb.missionplan.repo.MissionRepository;
import idusw.sb.missionplan.settle.Cycle;
import idusw.sb.missionplan.settle.PenaltyCalculator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 오늘 체크리스트 + 주간 보드 + 정산 요약을 한 화면(대시보드)으로 합친 컨트롤러.
 * 주간 보드는 7일 전부가 항상 펼쳐져 있고(날짜를 클릭해서 리로드하지 않는다), 등록은
 * 그 날짜 칸의 폼에서 바로 한다. 표의 요일 헤더는 서버 왕복 없는 페이지 내 앵커 링크다.
 *
 * 화면에 필요한 미션은 방 전체 기준으로 딱 한 번만 조회한 뒤(findByRoomIdAndTargetDateBetweenOrderByTargetDateAscIdAsc),
 * memberId -> targetDate로 메모리에서 나눠 쓴다. 예전에는 오늘 체크리스트/주간 보드 7칸×2명/
 * 정산까지 따로따로 조회해서 페이지 하나에 DB 왕복이 20번 가까이 났는데, 그게 지연이 있는
 * 무료 DB(Neon)에서 체감 로딩을 느리게 만드는 주범이었다. 지금은 방 통짜 조회 1번 + 멤버
 * 목록 조회 1번, 총 2번으로 끝난다. 요일별 리스트를 항상 펼쳐두는 것도 이미 가져온 데이터를
 * 그대로 뿌리는 것뿐이라 쿼리가 추가로 들지 않는다.
 */
@Controller
@RequestMapping("/room/{code}")
public class RoomDashboardController {

    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN);

    private final MissionService missionService;
    private final MemberRepository memberRepository;
    private final MissionRepository missionRepository;

    public RoomDashboardController(MissionService missionService, MemberRepository memberRepository,
                                    MissionRepository missionRepository) {
        this.missionService = missionService;
        this.memberRepository = memberRepository;
        this.missionRepository = missionRepository;
    }

    public record MissionCard(Long id, String title, boolean done, int penaltyWon) {
    }

    public record DayCell(LocalDate date, String dayLabel, int myDone, int myTotal,
                           Integer partnerDone, Integer partnerTotal, boolean future) {
    }

    public record DayList(LocalDate date, String dateLabel, boolean isToday,
                           List<Mission> myMissions, List<Mission> partnerMissions) {
    }

    public record LateItem(String title, int daysLate, int penaltyWon) {
    }

    public record MemberSettlement(String nickname, List<LateItem> lateItems, int total) {
    }

    @GetMapping
    public String dashboard(@PathVariable String code,
                             @RequestParam(required = false) String start,
                             HttpServletRequest request, Model model) {
        Room room = (Room) request.getAttribute(MemberSessionFilter.CURRENT_ROOM_ATTR);
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);
        LocalDate today = LocalDate.now();

        List<Member> members = memberRepository.findByRoomId(room.getId());
        Optional<Member> partner = members.stream()
                .filter(member -> !member.getId().equals(me.getId()))
                .findFirst();

        LocalDate todayCycleStart = Cycle.startDate(room.getStartDate(), today);
        LocalDate todayCycleEnd = Cycle.endDate(room.getStartDate(), today);
        LocalDate cycleStart = parseOrDefault(start, todayCycleStart);
        LocalDate cycleEnd = cycleStart.plusDays(6);

        // 오늘 체크리스트용 범위와 주간 보드용 범위를 합쳐서 딱 한 번에 다 끌어온다.
        LocalDate rangeStart = minDate(todayCycleStart, cycleStart);
        LocalDate rangeEnd = maxDate(today, cycleEnd);

        Map<Long, Map<LocalDate, List<Mission>>> missionsByMember = missionRepository
                .findByRoomIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(room.getId(), rangeStart, rangeEnd)
                .stream()
                .collect(Collectors.groupingBy(Mission::getMemberId, Collectors.groupingBy(Mission::getTargetDate)));

        Map<LocalDate, List<Mission>> myByDate = missionsByMember.getOrDefault(me.getId(), Map.of());
        Map<LocalDate, List<Mission>> partnerByDate = partner
                .map(p -> missionsByMember.getOrDefault(p.getId(), Map.of()))
                .orElse(Map.of());

        addTodayChecklist(today, todayCycleStart, todayCycleEnd, myByDate, partnerByDate, model);
        addWeekGrid(partner, cycleStart, today, myByDate, partnerByDate, model);
        addWeekDayLists(cycleStart, today, myByDate, partnerByDate, model);
        addSettlement(members, cycleStart, cycleEnd, today, missionsByMember, model);

        model.addAttribute("room", room);
        model.addAttribute("me", me);
        model.addAttribute("partner", partner.orElse(null));
        return "dashboard";
    }

    private void addTodayChecklist(LocalDate today, LocalDate todayCycleStart, LocalDate todayCycleEnd,
                                    Map<LocalDate, List<Mission>> myByDate, Map<LocalDate, List<Mission>> partnerByDate,
                                    Model model) {
        List<Mission> candidates = flatten(myByDate, todayCycleStart, today);
        List<MissionCard> myTodayMissions = missionService.filterActive(candidates, today).stream()
                .map(m -> new MissionCard(m.getId(), m.getTitle(), m.isDone(),
                        PenaltyCalculator.penaltyFor(m, today, todayCycleEnd)))
                .toList();

        List<Mission> partnerTodayMissions = partnerByDate.getOrDefault(today, List.of());

        model.addAttribute("myTodayMissions", myTodayMissions);
        model.addAttribute("partnerTodayMissions", partnerTodayMissions);
    }

    private void addWeekGrid(Optional<Member> partner, LocalDate cycleStart, LocalDate today,
                              Map<LocalDate, List<Mission>> myByDate, Map<LocalDate, List<Mission>> partnerByDate,
                              Model model) {
        List<DayCell> cells = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = cycleStart.plusDays(i);
            boolean future = date.isAfter(today);

            List<Mission> myDayMissions = myByDate.getOrDefault(date, List.of());
            int myTotal = myDayMissions.size();
            int myDone = (int) myDayMissions.stream().filter(Mission::isDone).count();

            Integer partnerDone = null;
            Integer partnerTotal = null;
            if (partner.isPresent()) {
                List<Mission> partnerDayMissions = partnerByDate.getOrDefault(date, List.of());
                partnerTotal = partnerDayMissions.size();
                partnerDone = (int) partnerDayMissions.stream().filter(Mission::isDone).count();
            }

            cells.add(new DayCell(date, koreanDayLabel(date), myDone, myTotal, partnerDone, partnerTotal, future));
        }

        LocalDate cycleEnd = cycleStart.plusDays(6);
        model.addAttribute("cycleStart", cycleStart);
        model.addAttribute("cycleEnd", cycleEnd);
        model.addAttribute("cycleStartLabel", cycleStart.format(DATE_LABEL));
        model.addAttribute("cycleEndLabel", cycleEnd.format(DATE_LABEL));
        model.addAttribute("prevStart", cycleStart.minusDays(7));
        model.addAttribute("nextStart", cycleStart.plusDays(7));
        model.addAttribute("cells", cells);
    }

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

    private void addSettlement(List<Member> members, LocalDate cycleStart, LocalDate cycleEnd, LocalDate today,
                                Map<Long, Map<LocalDate, List<Mission>>> missionsByMember, Model model) {
        boolean hasPartner = members.size() == 2;

        List<MemberSettlement> settlements = members.stream()
                .map(member -> buildSettlement(member, cycleStart, cycleEnd, today,
                        missionsByMember.getOrDefault(member.getId(), Map.of())))
                .toList();

        int total = settlements.stream().mapToInt(MemberSettlement::total).sum();
        String recipientNickname = null;
        boolean tie = false;
        if (hasPartner) {
            MemberSettlement a = settlements.get(0);
            MemberSettlement b = settlements.get(1);
            if (a.total() == b.total()) {
                tie = true;
            } else {
                recipientNickname = a.total() < b.total() ? a.nickname() : b.nickname();
            }
        }

        model.addAttribute("hasPartner", hasPartner);
        model.addAttribute("settlements", settlements);
        model.addAttribute("settleTotal", total);
        model.addAttribute("tie", tie);
        model.addAttribute("recipientNickname", recipientNickname);
    }

    private MemberSettlement buildSettlement(Member member, LocalDate cycleStart, LocalDate cycleEnd, LocalDate today,
                                              Map<LocalDate, List<Mission>> byDate) {
        List<Mission> missions = flatten(byDate, cycleStart, cycleEnd);

        List<LateItem> lateItems = new ArrayList<>();
        int total = 0;
        for (Mission mission : missions) {
            int penalty = PenaltyCalculator.penaltyFor(mission, today, cycleEnd);
            total += penalty;
            if (penalty > 0) {
                lateItems.add(new LateItem(mission.getTitle(), penalty / 1000, penalty));
            }
        }
        return new MemberSettlement(member.getNickname(), lateItems, total);
    }

    @PostMapping("/missions")
    public String addMission(@PathVariable String code,
                              @RequestParam(required = false) String start,
                              @RequestParam String day,
                              @RequestParam String title,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Room room = (Room) request.getAttribute(MemberSessionFilter.CURRENT_ROOM_ATTR);
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        String redirectUrl = redirectUrl(code, start);

        if (title.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "미션 내용을 입력해주세요.");
            return redirectUrl;
        }

        LocalDate targetDate = parseOrDefault(day, null);
        if (targetDate == null) {
            redirectAttributes.addFlashAttribute("error", "날짜가 올바르지 않습니다.");
            return redirectUrl;
        }

        missionService.addMission(room.getId(), me.getId(), targetDate, title.trim());
        return redirectUrl;
    }

    @PostMapping("/missions/{missionId}/toggle")
    public String toggle(@PathVariable String code, @PathVariable Long missionId,
                          @RequestParam(required = false) String start,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        try {
            missionService.toggleDone(missionId, me.getId(), LocalDate.now());
        } catch (MissionAccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectUrl(code, start);
    }

    @PostMapping("/missions/{missionId}/edit")
    public String edit(@PathVariable String code, @PathVariable Long missionId,
                        @RequestParam String title,
                        @RequestParam(required = false) String start,
                        HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        if (title.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "미션 내용을 입력해주세요.");
            return redirectUrl(code, start);
        }

        try {
            missionService.editTitle(missionId, me.getId(), title.trim());
        } catch (MissionAccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectUrl(code, start);
    }

    @PostMapping("/missions/{missionId}/delete")
    public String delete(@PathVariable String code, @PathVariable Long missionId,
                          @RequestParam(required = false) String start,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        try {
            missionService.delete(missionId, me.getId());
        } catch (MissionAccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectUrl(code, start);
    }

    private String redirectUrl(String code, String start) {
        StringBuilder url = new StringBuilder("redirect:/room/").append(code);
        if (start != null) {
            url.append("?start=").append(start);
        }
        return url.toString();
    }

    private List<Mission> flatten(Map<LocalDate, List<Mission>> byDate, LocalDate from, LocalDate to) {
        List<Mission> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            result.addAll(byDate.getOrDefault(date, List.of()));
        }
        return result;
    }

    private String koreanDayLabel(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
    }

    private LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDate parseOrDefault(String value, LocalDate fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }
}
