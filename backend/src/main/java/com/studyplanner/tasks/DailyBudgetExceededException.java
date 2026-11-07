package com.studyplanner.tasks;

import java.time.LocalDate;

public class DailyBudgetExceededException extends RuntimeException {
    private final LocalDate scheduledDate;
    private final int budgetMinutes;
    private final int scheduledMinutes;
    private final int excessMinutes;

    public DailyBudgetExceededException(LocalDate scheduledDate, int budgetMinutes, int scheduledMinutes, int excessMinutes) {
        super(String.format("CÃ´ng viá»‡c ngÃ y %s vÆ°á»£t ngÃ¢n sÃ¡ch há»c má»—i ngÃ y (%d/%d phÃºt, vÆ°á»£t %d phÃºt).",
                scheduledDate, scheduledMinutes, budgetMinutes, excessMinutes));
        this.scheduledDate = scheduledDate;
        this.budgetMinutes = budgetMinutes;
        this.scheduledMinutes = scheduledMinutes;
        this.excessMinutes = excessMinutes;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public int getBudgetMinutes() {
        return budgetMinutes;
    }

    public int getScheduledMinutes() {
        return scheduledMinutes;
    }

    public int getExcessMinutes() {
        return excessMinutes;
    }
}
