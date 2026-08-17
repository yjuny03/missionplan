package idusw.sb.missionplan.room;

import idusw.sb.missionplan.domain.Member;
import idusw.sb.missionplan.domain.Room;
import idusw.sb.missionplan.repo.MemberRepository;
import idusw.sb.missionplan.repo.RoomRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomService {

    private static final int MAX_MEMBERS = 2;
    // 0/O, 1/I처럼 손으로 옮겨 적을 때 헷갈리는 문자는 뺐다.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;
    private final SecureRandom random = new SecureRandom();

    public RoomService(RoomRepository roomRepository, MemberRepository memberRepository) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
    }

    public record JoinResult(Room room, Member member) {
    }

    @Transactional
    public JoinResult createRoom(String roomName, String nickname) {
        Room room = new Room(roomName, generateUniqueInviteCode(), LocalDate.now());
        roomRepository.save(room);

        Member member = new Member(room.getId(), nickname, generateToken());
        memberRepository.save(member);

        return new JoinResult(room, member);
    }

    @Transactional
    public JoinResult joinRoom(String inviteCode, String nickname) {
        Room room = roomRepository.findByInviteCode(inviteCode)
                .orElseThrow(RoomNotFoundException::new);

        // 쿠키를 잃어버린 기존 멤버가 같은 닉네임으로 재접속하는 경우: 새로 만들지 않고 토큰만 재발급한다.
        Member existing = memberRepository.findByRoomIdAndNickname(room.getId(), nickname).orElse(null);
        if (existing != null) {
            existing.reissueToken(generateToken());
            return new JoinResult(room, existing);
        }

        List<Member> members = memberRepository.findByRoomId(room.getId());
        if (members.size() >= MAX_MEMBERS) {
            throw new RoomFullException();
        }

        Member member = new Member(room.getId(), nickname, generateToken());
        memberRepository.save(member);

        return new JoinResult(room, member);
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = generateCode();
        } while (roomRepository.findByInviteCode(code).isPresent());
        return code;
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }
}
