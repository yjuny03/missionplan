package idusw.sb.missionplan.settle;

import static org.assertj.core.api.Assertions.assertThat;

import idusw.sb.missionplan.domain.Mission;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC.md §3.3 검산표와 1:1로 대응하는 테스트. 표를 고치면 이 테스트도 같이 고친다.
 *
 * 주기를 월요일 시작으로 고정: MON(8/10) ~ SUN(8/16, cycleEnd).
 */
class PenaltyCalculatorTest {

    private static final LocalDate MON = LocalDate.of(2026, 8, 10);
    private static final LocalDate TUE = LocalDate.of(2026, 8, 11);
    private static final LocalDate WED = LocalDate.of(2026, 8, 12);
    private static final LocalDate FRI = LocalDate.of(2026, 8, 14);
    private static final LocalDate SUN = LocalDate.of(2026, 8, 16);
    private static final LocalDate CYCLE_END = SUN;

    private static Mission missionOn(LocalDate targetDate) {
        return new Mission(1L, 1L, targetDate, "테스트 미션");
    }

    @Test
    @DisplayName("월요일 미션, 당일 완료 -> 0원")
    void monday_completedSameDay() {
        Mission mission = missionOn(MON);
        mission.complete(MON);

        int penalty = PenaltyCalculator.penaltyFor(mission, MON, CYCLE_END);

        assertThat(penalty).isEqualTo(0);
    }

    @Test
    @DisplayName("월요일 미션, 오늘이 월요일이고 미완료(유예) -> 0원")
    void monday_incompleteOnDeadlineDay() {
        Mission mission = missionOn(MON);

        int penalty = PenaltyCalculator.penaltyFor(mission, MON, CYCLE_END);

        assertThat(penalty).isEqualTo(0);
    }

    @Test
    @DisplayName("월요일 미션, 오늘이 화요일이고 미완료 -> 1,000원")
    void monday_incompleteOnTuesday() {
        Mission mission = missionOn(MON);

        int penalty = PenaltyCalculator.penaltyFor(mission, TUE, CYCLE_END);

        assertThat(penalty).isEqualTo(1000);
    }

    @Test
    @DisplayName("월요일 미션, 수요일에 뒤늦게 완료 -> 2,000원")
    void monday_completedOnWednesday() {
        Mission mission = missionOn(MON);
        mission.complete(WED);

        int penalty = PenaltyCalculator.penaltyFor(mission, WED, CYCLE_END);

        assertThat(penalty).isEqualTo(2000);
    }

    @Test
    @DisplayName("월요일 미션, 오늘이 금요일이고 미완료 -> 4,000원")
    void monday_incompleteOnFriday() {
        Mission mission = missionOn(MON);

        int penalty = PenaltyCalculator.penaltyFor(mission, FRI, CYCLE_END);

        assertThat(penalty).isEqualTo(4000);
    }

    @Test
    @DisplayName("월요일 미션, 일요일까지 미완료(최대치) -> 7,000원")
    void monday_incompleteUntilCycleEnd() {
        Mission mission = missionOn(MON);
        LocalDate afterCycleEnd = CYCLE_END.plusDays(1);

        int penalty = PenaltyCalculator.penaltyFor(mission, afterCycleEnd, CYCLE_END);

        assertThat(penalty).isEqualTo(7000);
    }

    @Test
    @DisplayName("수요일 미션, 오늘이 금요일이고 미완료 -> 2,000원")
    void wednesday_incompleteOnFriday() {
        Mission mission = missionOn(WED);

        int penalty = PenaltyCalculator.penaltyFor(mission, FRI, CYCLE_END);

        assertThat(penalty).isEqualTo(2000);
    }

    @Test
    @DisplayName("일요일 미션, 미완료 -> 1,000원")
    void sunday_incomplete() {
        Mission mission = missionOn(SUN);
        LocalDate afterCycleEnd = CYCLE_END.plusDays(1);

        int penalty = PenaltyCalculator.penaltyFor(mission, afterCycleEnd, CYCLE_END);

        assertThat(penalty).isEqualTo(1000);
    }

    @Test
    @DisplayName("주기 종료 후 아무리 시간이 지나도 7,000원을 넘지 않는다")
    void penaltyNeverExceedsCapEvenLongAfterCycleEnd() {
        Mission mission = missionOn(MON);
        LocalDate wayAfterCycleEnd = CYCLE_END.plusDays(30);

        int penalty = PenaltyCalculator.penaltyFor(mission, wayAfterCycleEnd, CYCLE_END);

        assertThat(penalty).isEqualTo(7000);
    }
}
