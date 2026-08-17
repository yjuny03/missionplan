package idusw.sb.missionplan.mission;

import idusw.sb.missionplan.domain.Mission;
import idusw.sb.missionplan.repo.MissionRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MissionService {

    private final MissionRepository missionRepository;

    public MissionService(MissionRepository missionRepository) {
        this.missionRepository = missionRepository;
    }

    @Transactional
    public void addMission(Long roomId, Long memberId, LocalDate targetDate, String title) {
        missionRepository.save(new Mission(roomId, memberId, targetDate, title));
    }

    @Transactional
    public void toggleDone(Long missionId, Long requesterMemberId, LocalDate today) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(MissionAccessDeniedException::new);

        if (!mission.getMemberId().equals(requesterMemberId)) {
            throw new MissionAccessDeniedException();
        }

        if (mission.isDone()) {
            mission.uncomplete();
        } else {
            mission.complete(today);
        }
    }

    @Transactional
    public void editTitle(Long missionId, Long requesterMemberId, String newTitle) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(MissionAccessDeniedException::new);

        if (!mission.getMemberId().equals(requesterMemberId)) {
            throw new MissionAccessDeniedException();
        }

        mission.editTitle(newTitle);
    }

    @Transactional
    public void delete(Long missionId, Long requesterMemberId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(MissionAccessDeniedException::new);

        if (!mission.getMemberId().equals(requesterMemberId)) {
            throw new MissionAccessDeniedException();
        }

        missionRepository.delete(mission);
    }

    /**
     * 이번 주기에서 아직 안 끝난 것 전부 + 오늘 끝낸 것. 밀린 항목은 끝낼 때까지 계속 보인다.
     * DB를 다시 조회하지 않고, 호출하는 쪽이 이미 가져와 둔 후보 목록에서 걸러낸다
     * (대시보드 페이지 하나에서 여러 화면 조각이 같은 미션 목록을 나눠 쓰기 때문).
     */
    public List<Mission> filterActive(List<Mission> candidates, LocalDate today) {
        return candidates.stream()
                .filter(m -> !m.isDone() || today.equals(m.getDoneAt()))
                .sorted(Comparator.comparing(Mission::getTargetDate).thenComparing(Mission::getId))
                .toList();
    }
}
