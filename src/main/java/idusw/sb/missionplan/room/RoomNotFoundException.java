package idusw.sb.missionplan.room;

public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException() {
        super("존재하지 않는 초대코드입니다.");
    }
}
