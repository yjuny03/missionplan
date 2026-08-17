package idusw.sb.missionplan.room;

public class RoomFullException extends RuntimeException {

    public RoomFullException() {
        super("이미 인원이 가득 찬 방입니다.");
    }
}
