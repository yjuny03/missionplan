package idusw.sb.missionplan.web;

import idusw.sb.missionplan.domain.Member;
import idusw.sb.missionplan.domain.Mission;
import idusw.sb.missionplan.domain.Room;
import idusw.sb.missionplan.repo.MemberRepository;
import idusw.sb.missionplan.repo.MissionRepository;
import idusw.sb.missionplan.settle.Cycle;
import idusw.sb.missionplan.settle.PenaltyCalculator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/room/{code}/settle")
public class SettleController {

    private final MemberRepository memberRepository;
    private final MissionRepository missionRepository;

    public SettleController(MemberRepository memberRepository, MissionRepository missionRepository) {
        this.memberRepository = memberRepository;
        this.missionRepository = missionRepository;
    }

    public record LateItem(String title, int daysLate, int penaltyWon) {
    }

    public record MemberSettlement(String nickname, List<LateItem> lateItems, int total) {
    }

    @GetMapping
    public String settle(@PathVariable String code,
                          @RequestParam(required = false) String start,
                          HttpServletRequest request, Model model) {
        Room room = (Room) request.getAttribute(MemberSessionFilter.CURRENT_ROOM_ATTR);
        LocalDate today = LocalDate.now();

        LocalDate cycleStart = parseOrDefault(start, Cycle.startDate(room.getStartDate(), today));
        LocalDate cycleEnd = cycleStart.plusDays(6);

        List<Member> members = memberRepository.findByRoomId(room.getId());
        List<MemberSettlement> settlements = members.stream()
                .map(member -> buildSettlement(member, cycleStart, cycleEnd, today))
                .toList();

        boolean hasPartner = members.size() == 2;
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

        model.addAttribute("room", room);
        model.addAttribute("cycleStart", cycleStart);
        model.addAttribute("cycleEnd", cycleEnd);
        model.addAttribute("prevStart", cycleStart.minusDays(7));
        model.addAttribute("nextStart", cycleStart.plusDays(7));
        model.addAttribute("hasPartner", hasPartner);
        model.addAttribute("settlements", settlements);
        model.addAttribute("total", total);
        model.addAttribute("tie", tie);
        model.addAttribute("recipientNickname", recipientNickname);

        return "settle";
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
