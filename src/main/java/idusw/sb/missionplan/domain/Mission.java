package idusw.sb.missionplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "mission", indexes = @Index(name = "idx_mission_room_target_date", columnList = "room_id, target_date"))
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false)
    private boolean done = false;

    @Column(name = "done_at")
    private LocalDate doneAt;

    protected Mission() {
    }

    public Mission(Long roomId, Long memberId, LocalDate targetDate, String title) {
        this.roomId = roomId;
        this.memberId = memberId;
        this.targetDate = targetDate;
        this.title = title;
    }

    public void complete(LocalDate doneAt) {
        this.done = true;
        this.doneAt = doneAt;
    }

    public void uncomplete() {
        this.done = false;
        this.doneAt = null;
    }

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public String getTitle() {
        return title;
    }

    public boolean isDone() {
        return done;
    }

    public LocalDate getDoneAt() {
        return doneAt;
    }
}
