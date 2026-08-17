package idusw.sb.missionplan.settle;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class Cycle {

    private static final int CYCLE_LENGTH_DAYS = 7;

    private Cycle() {
    }

    public static LocalDate startDate(LocalDate roomStartDate, LocalDate targetDate) {
        long cycleIndex = Math.floorDiv(ChronoUnit.DAYS.between(roomStartDate, targetDate), CYCLE_LENGTH_DAYS);
        return roomStartDate.plusDays(cycleIndex * CYCLE_LENGTH_DAYS);
    }

    public static LocalDate endDate(LocalDate roomStartDate, LocalDate targetDate) {
        return startDate(roomStartDate, targetDate).plusDays(CYCLE_LENGTH_DAYS - 1);
    }
}
