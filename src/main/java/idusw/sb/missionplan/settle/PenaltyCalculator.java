package idusw.sb.missionplan.settle;

import idusw.sb.missionplan.domain.Mission;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class PenaltyCalculator {

    private static final int DAILY_PENALTY = 1000;

    private PenaltyCalculator() {
    }

    public static int penaltyFor(Mission mission, LocalDate today, LocalDate cycleEndDate) {
        LocalDate cutoff = mission.isDone()
                ? mission.getDoneAt().minusDays(1)
                : earlierOf(today.minusDays(1), cycleEndDate);

        long daysLate = ChronoUnit.DAYS.between(mission.getTargetDate(), cutoff) + 1;
        return (int) (Math.max(0, daysLate) * DAILY_PENALTY);
    }

    private static LocalDate earlierOf(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
