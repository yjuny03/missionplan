package idusw.sb.missionplan.mission;

public class MissionAccessDeniedException extends RuntimeException {

    public MissionAccessDeniedException() {
        super("본인 미션에만 접근할 수 있습니다.");
    }
}
