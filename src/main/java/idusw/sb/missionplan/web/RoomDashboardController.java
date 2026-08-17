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
import java.util.Optional;
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
 * 미션 등록은 주간 보드의 날짜 선택(day)을 통해서만 이루어진다.
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

    public record LateItem(String title, int daysLate, int penaltyWon) {
    }

    public record MemberSettlement(String nickname, List<LateItem> lateItems, int total) {
    }

    @GetMapping
    public String dashboard(@PathVariable String code,
                             @RequestParam(required = false) String start,
                             @RequestParam(required = false) String day,
                             HttpServletRequest request, Model model) {
        Room room = (Room) request.getAttribute(MemberSessionFilter.CURRENT_ROOM_ATTR);
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);
        LocalDate today = LocalDate.now();

        Optional<Member> partner = memberRepository.findByRoomId(room.getId()).stream()
                .filter(member -> !member.getId().equals(me.getId()))
                .findFirst();

        LocalDate cycleStart = parseOrDefault(start, Cycle.startDate(room.getStartDate(), today));
        LocalDate cycleEnd = cycleStart.plusDays(6);

        addTodayChecklist(room, me, partner, today, model);
        addWeekGrid(me, partner, cycleStart, today, model);
        addDaySelection(me, partner, day, model);
        addSettlement(room, cycleStart, cycleEnd, today, model);

        model.addAttribute("room", room);
        model.addAttribute("me", me);
        model.addAttribute("partner", partner.orElse(null));
        return "dashboard";
    }

    private void addTodayChecklist(Room room, Member me, Optional<Member> partner, LocalDate today, Model model) {
        LocalDate todayCycleStart = Cycle.startDate(room.getStartDate(), today);
        LocalDate todayCycleEnd = Cycle.endDate(room.getStartDate(), today);

        List<MissionCard> myTodayMissions = missionService.myActiveMissions(me.getId(), todayCycleStart, today).stream()
                .map(m -> new MissionCard(m.getId(), m.getTitle(), m.isDone(),
                        PenaltyCalculator.penaltyFor(m, today, todayCycleEnd)))
                .toList();

        List<Mission> partnerTodayMissions = partner
                .map(p -> missionService.partnerTodayMissions(p.getId(), today))
                .orElse(List.of());

        model.addAttribute("myTodayMissions", myTodayMissions);
        model.addAttribute("partnerTodayMissions", partnerTodayMissions);
    }

    private void addWeekGrid(Member me, Optional<Member> partner, LocalDate cycleStart, LocalDate today, Model model) {
        List<DayCell> cells = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = cycleStart.plusDays(i);
            boolean future = date.isAfter(today);

            List<Mission> myDayMissions = missionRepository.findByMemberIdAndTargetDateOrderByIdAsc(me.getId(), date);
            int myTotal = myDayMissions.size();
            int myDone = (int) myDayMissions.stream().filter(Mission::isDone).count();

            Integer partnerDone = null;
            Integer partnerTotal = null;
            if (partner.isPresent()) {
                List<Mission> partnerDayMissions =
                        missionRepository.findByMemberIdAndTargetDateOrderByIdAsc(partner.get().getId(), date);
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

    private void addDaySelection(Member me, Optional<Member> partner, String day, Model model) {
        LocalDate selectedDay = parseOrDefault(day, null);
        if (selectedDay == null) {
            return;
        }
        model.addAttribute("selectedDay", selectedDay);
        model.addAttribute("selectedDayLabel", selectedDay.format(DATE_LABEL));
        model.addAttribute("selectedDayMyMissions",
                missionRepository.findByMemberIdAndTargetDateOrderByIdAsc(me.getId(), selectedDay));
        partner.ifPresent(p -> model.addAttribute("selectedDayPartnerMissions",
                missionRepository.findByMemberIdAndTargetDateOrderByIdAsc(p.getId(), selectedDay)));
    }

    private void addSettlement(Room room, LocalDate cycleStart, LocalDate cycleEnd, LocalDate today, Model model) {
        List<Member> members = memberRepository.findByRoomId(room.getId());
        boolean hasPartner = members.size() == 2;

        List<MemberSettlement> settlements = members.stream()
                .map(member -> buildSettlement(member, cycleStart, cycleEnd, today))
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

    private MemberSettlement buildSettlement(Member member, LocalDate cycleStart, LocalDate cycleEnd, LocalDate today) {
        List<Mission> missions = missionRepository
                .findByMemberIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(member.getId(), cycleStart, cycleEnd);

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
                              @RequestParam String start,
                              @RequestParam String day,
                              @RequestParam String title,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Room room = (Room) request.getAttribute(MemberSessionFilter.CURRENT_ROOM_ATTR);
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        String redirectUrl = redirectUrl(code, start, day);

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
                          @RequestParam(required = false) String day,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        try {
            missionService.toggleDone(missionId, me.getId(), LocalDate.now());
        } catch (MissionAccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectUrl(code, start, day);
    }

    @PostMapping("/missions/{missionId}/edit")
    public String edit(@PathVariable String code, @PathVariable Long missionId,
                        @RequestParam String title,
                        @RequestParam(required = false) String start,
                        @RequestParam(required = false) String day,
                        HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        if (title.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "미션 내용을 입력해주세요.");
            return redirectUrl(code, start, day);
        }

        try {
            missionService.editTitle(missionId, me.getId(), title.trim());
        } catch (MissionAccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectUrl(code, start, day);
    }

    @PostMapping("/missions/{missionId}/delete")
    public String delete(@PathVariable String code, @PathVariable Long missionId,
                          @RequestParam(required = false) String start,
                          @RequestParam(required = false) String day,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        try {
            missionService.delete(missionId, me.getId());
        } catch (MissionAccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectUrl(code, start, day);
    }

    private String redirectUrl(String code, String start, String day) {
        StringBuilder url = new StringBuilder("redirect:/room/").append(code);
        if (start != null) {
            url.append("?start=").append(start);
            if (day != null) {
                url.append("&day=").append(day);
            }
        }
        return url.toString();
    }

    private String koreanDayLabel(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
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
