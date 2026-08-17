package idusw.sb.missionplan.web;

import idusw.sb.missionplan.domain.Member;
import idusw.sb.missionplan.domain.Mission;
import idusw.sb.missionplan.domain.Room;
import idusw.sb.missionplan.mission.MissionService;
import idusw.sb.missionplan.repo.MemberRepository;
import idusw.sb.missionplan.repo.MissionRepository;
import idusw.sb.missionplan.settle.Cycle;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
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

@Controller
@RequestMapping("/room/{code}/week")
public class WeekController {

    private final MemberRepository memberRepository;
    private final MissionRepository missionRepository;
    private final MissionService missionService;

    public WeekController(MemberRepository memberRepository, MissionRepository missionRepository,
                           MissionService missionService) {
        this.memberRepository = memberRepository;
        this.missionRepository = missionRepository;
        this.missionService = missionService;
    }

    public record DayCell(LocalDate date, String dayLabel, int myDone, int myTotal,
                           Integer partnerDone, Integer partnerTotal, boolean future) {
    }

    @GetMapping
    public String week(@PathVariable String code,
                        @RequestParam(required = false) String start,
                        @RequestParam(required = false) String day,
                        HttpServletRequest request, Model model) {
        Room room = (Room) request.getAttribute(MemberSessionFilter.CURRENT_ROOM_ATTR);
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);
        LocalDate today = LocalDate.now();

        LocalDate cycleStart = parseOrDefault(start, Cycle.startDate(room.getStartDate(), today));
        LocalDate cycleEnd = cycleStart.plusDays(6);

        Optional<Member> partner = memberRepository.findByRoomId(room.getId()).stream()
                .filter(member -> !member.getId().equals(me.getId()))
                .findFirst();

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

        model.addAttribute("room", room);
        model.addAttribute("me", me);
        model.addAttribute("partner", partner.orElse(null));
        model.addAttribute("cycleStart", cycleStart);
        model.addAttribute("cycleEnd", cycleEnd);
        model.addAttribute("prevStart", cycleStart.minusDays(7));
        model.addAttribute("nextStart", cycleStart.plusDays(7));
        model.addAttribute("cells", cells);

        LocalDate selectedDay = parseOrDefault(day, null);
        if (selectedDay != null) {
            model.addAttribute("selectedDay", selectedDay);
            model.addAttribute("selectedDayMyMissions",
                    missionRepository.findByMemberIdAndTargetDateOrderByIdAsc(me.getId(), selectedDay));
            partner.ifPresent(p -> model.addAttribute("selectedDayPartnerMissions",
                    missionRepository.findByMemberIdAndTargetDateOrderByIdAsc(p.getId(), selectedDay)));
        }

        return "week";
    }

    @PostMapping("/missions")
    public String addMission(@PathVariable String code,
                              @RequestParam String start,
                              @RequestParam String day,
                              @RequestParam String title,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Room room = (Room) request.getAttribute(MemberSessionFilter.CURRENT_ROOM_ATTR);
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        String redirectUrl = "redirect:/room/" + code + "/week?start=" + start + "&day=" + day;

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
