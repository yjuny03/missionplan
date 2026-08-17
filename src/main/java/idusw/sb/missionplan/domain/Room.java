package idusw.sb.missionplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "room", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = "invite_code"))
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "invite_code", nullable = false)
    private String inviteCode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    protected Room() {
    }

    public Room(String name, String inviteCode, LocalDate startDate) {
        this.name = name;
        this.inviteCode = inviteCode;
        this.startDate = startDate;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public LocalDate getStartDate() {
        return startDate;
    }
}
