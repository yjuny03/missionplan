package idusw.sb.missionplan.web;

import idusw.sb.missionplan.domain.Member;
import idusw.sb.missionplan.domain.Mission;
import idusw.sb.missionplan.domain.Room;
import idusw.sb.missionplan.repo.MemberRepository;
import idusw.sb.missionplan.repo.MissionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/room/{code}/calendar")
public class CalendarController {

    private final MemberRepository memberRepository;
    private final MissionRepository missionRepository;

    public CalendarController(MemberRepository memberRepository, MissionRepository missionRepository) {
        this.memberRepository = memberRepository;
        this.missionRepository = missionRepository;
    }

    /** status: "done" | "late" | "none" (오늘 미완료도 유예라서 "none"으로 취급) */
    public record DayCell(LocalDate date, String status) {
    }

    @GetMapping
    public String calendar(@PathVariable String code,
                            @RequestParam(required = false) String month,
                            @RequestParam(required = false) String day,
                            HttpServletRequest request, Model model) {
        Room room = (Room) request.getAttribute(MemberSessionFilter.CURRENT_ROOM_ATTR);
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);
        LocalDate today = LocalDate.now();

        YearMonth yearMonth = parseMonthOrDefault(month, YearMonth.from(today));
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        Map<LocalDate, List<Mission>> missionsByDate = missionRepository
                .findByMemberIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(me.getId(), firstDay, lastDay)
                .stream()
                .collect(Collectors.groupingBy(Mission::getTargetDate));

        List<DayCell> flat = new ArrayList<>();
        int leadingBlanks = firstDay.getDayOfWeek().getValue() - 1;
        for (int i = 0; i < leadingBlanks; i++) {
            flat.add(new DayCell(null, null));
        }
        for (LocalDate date = firstDay; !date.isAfter(lastDay); date = date.plusDays(1)) {
            String status = statusFor(date, today, missionsByDate.getOrDefault(date, List.of()));
            flat.add(new DayCell(date, status));
        }
        while (flat.size() % 7 != 0) {
            flat.add(new DayCell(null, null));
        }

        List<List<DayCell>> weeks = new ArrayList<>();
        for (int i = 0; i < flat.size(); i += 7) {
            weeks.add(flat.subList(i, i + 7));
        }

        Optional<Member> partner = memberRepository.findByRoomId(room.getId()).stream()
                .filter(member -> !member.getId().equals(me.getId()))
                .findFirst();

        model.addAttribute("room", room);
        model.addAttribute("me", me);
        model.addAttribute("partner", partner.orElse(null));
        model.addAttribute("yearMonth", yearMonth);
        model.addAttribute("prevMonth", yearMonth.minusMonths(1));
        model.addAttribute("nextMonth", yearMonth.plusMonths(1));
        model.addAttribute("weeks", weeks);

        LocalDate selectedDay = parseDateOrDefault(day, null);
        if (selectedDay != null) {
            model.addAttribute("selectedDay", selectedDay);
            model.addAttribute("selectedDayMyMissions",
                    missionRepository.findByMemberIdAndTargetDateOrderByIdAsc(me.getId(), selectedDay));
            partner.ifPresent(p -> model.addAttribute("selectedDayPartnerMissions",
                    missionRepository.findByMemberIdAndTargetDateOrderByIdAsc(p.getId(), selectedDay)));
        }

        return "calendar";
    }

    private String statusFor(LocalDate date, LocalDate today, List<Mission> missions) {
        if (missions.isEmpty()) {
            return "none";
        }
        boolean allDone = missions.stream().allMatch(Mission::isDone);
        if (allDone) {
            return "done";
        }
        return date.isBefore(today) ? "late" : "none";
    }

    private YearMonth parseMonthOrDefault(String value, YearMonth fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return YearMonth.parse(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private LocalDate parseDateOrDefault(String value, LocalDate fallback) {
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
