package idusw.sb.missionplan.repo;

import idusw.sb.missionplan.domain.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByToken(String token);

    List<Member> findByRoomId(Long roomId);

    Optional<Member> findByRoomIdAndNickname(Long roomId, String nickname);
}
