package idusw.sb.missionplan.web;

import idusw.sb.missionplan.domain.Member;
import idusw.sb.missionplan.domain.Room;
import idusw.sb.missionplan.repo.MemberRepository;
import idusw.sb.missionplan.repo.RoomRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /room/{code}/** 요청마다 토큰 쿠키로 멤버십을 확인한다.
 * 초대코드가 없거나, 쿠키가 없거나, 다른 방 소속이면 홈으로 돌려보낸다.
 */
@Component
public class MemberSessionFilter extends OncePerRequestFilter {

    public static final String TOKEN_COOKIE_NAME = "token";
    public static final String CURRENT_ROOM_ATTR = "currentRoom";
    public static final String CURRENT_MEMBER_ATTR = "currentMember";

    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;

    public MemberSessionFilter(RoomRepository roomRepository, MemberRepository memberRepository) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/room/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String inviteCode = extractInviteCode(request.getRequestURI());
        Optional<Room> room = inviteCode.isEmpty() ? Optional.empty() : roomRepository.findByInviteCode(inviteCode);
        Optional<Member> member = room.isPresent() ? findMemberByCookie(request) : Optional.empty();

        boolean isMemberOfThisRoom = room.isPresent() && member.isPresent()
                && member.get().getRoomId().equals(room.get().getId());

        if (!isMemberOfThisRoom) {
            response.sendRedirect(request.getContextPath() + "/");
            return;
        }

        request.setAttribute(CURRENT_ROOM_ATTR, room.get());
        request.setAttribute(CURRENT_MEMBER_ATTR, member.get());
        chain.doFilter(request, response);
    }

    private String extractInviteCode(String uri) {
        String[] parts = uri.split("/");
        return parts.length >= 3 ? parts[2] : "";
    }

    private Optional<Member> findMemberByCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return memberRepository.findByToken(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
