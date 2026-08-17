package idusw.sb.missionplan.repo;

import idusw.sb.missionplan.domain.Mission;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    List<Mission> findByRoomIdAndTargetDateBetween(Long roomId, LocalDate start, LocalDate end);

    List<Mission> findByMemberIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(
            Long memberId, LocalDate start, LocalDate end);

    List<Mission> findByMemberIdAndTargetDateOrderByIdAsc(Long memberId, LocalDate targetDate);
}
