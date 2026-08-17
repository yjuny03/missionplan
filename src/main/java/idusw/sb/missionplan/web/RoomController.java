package idusw.sb.missionplan.web;

import idusw.sb.missionplan.room.RoomFullException;
import idusw.sb.missionplan.room.RoomNotFoundException;
import idusw.sb.missionplan.room.RoomService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoomController {

    private static final int TOKEN_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 90;

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @PostMapping("/rooms")
    public String create(@RequestParam String roomName, @RequestParam String nickname,
                          HttpServletResponse response, RedirectAttributes redirectAttributes) {
        if (roomName.isBlank() || nickname.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "방 이름과 닉네임을 입력해주세요.");
            return "redirect:/";
        }

        RoomService.JoinResult result = roomService.createRoom(roomName.trim(), nickname.trim());
        issueTokenCookie(response, result.member().getToken());
        return "redirect:/room/" + result.room().getInviteCode();
    }

    @PostMapping("/rooms/join")
    public String join(@RequestParam String inviteCode, @RequestParam String nickname,
                        HttpServletResponse response, RedirectAttributes redirectAttributes) {
        if (inviteCode.isBlank() || nickname.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "초대코드와 닉네임을 입력해주세요.");
            return "redirect:/";
        }

        try {
            RoomService.JoinResult result = roomService.joinRoom(inviteCode.trim(), nickname.trim());
            issueTokenCookie(response, result.member().getToken());
            return "redirect:/room/" + result.room().getInviteCode();
        } catch (RoomNotFoundException | RoomFullException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/";
        }
    }

    private void issueTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(MemberSessionFilter.TOKEN_COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(TOKEN_COOKIE_MAX_AGE_SECONDS);
        response.addCookie(cookie);
    }
}
