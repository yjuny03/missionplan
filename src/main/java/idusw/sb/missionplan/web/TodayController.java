package idusw.sb.missionplan.web;

import idusw.sb.missionplan.domain.Member;
import idusw.sb.missionplan.domain.Mission;
import idusw.sb.missionplan.domain.Room;
import idusw.sb.missionplan.mission.MissionAccessDeniedException;
import idusw.sb.missionplan.mission.MissionService;
import idusw.sb.missionplan.repo.MemberRepository;
import idusw.sb.missionplan.settle.Cycle;
import idusw.sb.missionplan.settle.PenaltyCalculator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
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
@RequestMapping("/room/{code}")
public class TodayController {

    private final MissionService missionService;
    private final MemberRepository memberRepository;

    public TodayController(MissionService missionService, MemberRepository memberRepository) {
        this.missionService = missionService;
        this.memberRepository = memberRepository;
    }

    public record MissionCard(Long id, String title, boolean done, int penaltyWon) {
    }

    @GetMapping
    public String today(@PathVariable String code, HttpServletRequest request, Model model) {
        Room room = (Room) request.getAttribute(MemberSessionFilter.CURRENT_ROOM_ATTR);
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        LocalDate today = LocalDate.now();
        LocalDate cycleStart = Cycle.startDate(room.getStartDate(), today);
        LocalDate cycleEnd = Cycle.endDate(room.getStartDate(), today);

        List<MissionCard> myMissions = missionService.myActiveMissions(me.getId(), cycleStart, today).stream()
                .map(m -> new MissionCard(m.getId(), m.getTitle(), m.isDone(),
                        PenaltyCalculator.penaltyFor(m, today, cycleEnd)))
                .toList();

        Optional<Member> partner = memberRepository.findByRoomId(room.getId()).stream()
                .filter(member -> !member.getId().equals(me.getId()))
                .findFirst();

        List<Mission> partnerMissions = partner
                .map(p -> missionService.partnerTodayMissions(p.getId(), today))
                .orElse(List.of());

        model.addAttribute("room", room);
        model.addAttribute("me", me);
        model.addAttribute("myMissions", myMissions);
        model.addAttribute("partner", partner.orElse(null));
        model.addAttribute("partnerMissions", partnerMissions);
        return "today";
    }

    @PostMapping("/missions")
    public String addMission(@PathVariable String code, @RequestParam String title,
                              HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Room room = (Room) request.getAttribute(MemberSessionFilter.CURRENT_ROOM_ATTR);
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        if (title.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "미션 내용을 입력해주세요.");
            return "redirect:/room/" + code;
        }

        missionService.addMission(room.getId(), me.getId(), LocalDate.now(), title.trim());
        return "redirect:/room/" + code;
    }

    @PostMapping("/missions/{missionId}/toggle")
    public String toggle(@PathVariable String code, @PathVariable Long missionId,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Member me = (Member) request.getAttribute(MemberSessionFilter.CURRENT_MEMBER_ATTR);

        try {
            missionService.toggleDone(missionId, me.getId(), LocalDate.now());
        } catch (MissionAccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/room/" + code;
    }
}
