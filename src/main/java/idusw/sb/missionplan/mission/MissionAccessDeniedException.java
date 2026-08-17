package idusw.sb.missionplan.mission;

public class MissionAccessDeniedException extends RuntimeException {

    public MissionAccessDeniedException() {
        super("본인 미션만 체크할 수 있습니다.");
    }
}
