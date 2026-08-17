package idusw.sb.missionplan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "member", uniqueConstraints = @UniqueConstraint(columnNames = "token"))
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String token;

    protected Member() {
    }

    public Member(Long roomId, String nickname, String token) {
        this.roomId = roomId;
        this.nickname = nickname;
        this.token = token;
    }

    public Long getId() {
        return id;
    }

    public Long getRoomId() {
        return roomId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getToken() {
        return token;
    }

    public void reissueToken(String newToken) {
        this.token = newToken;
    }
}
