package domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@PlanningEntity
@Getter
@Setter
public class CourierShift {

    @PlanningId
    private String id;

    private int hotCapacity;
    private int coldCapacity;

    //private Integer  startMinute;   // minutes since day start
    //private int durationMinutes;

    private static final int MAX_DURATION = 360;
    @PlanningListVariable(valueRangeProviderRefs = "visitList")
    List<Visit> visits = new ArrayList<>();

    public CourierShift() {}
    public boolean isUsed() {
        return !visits.isEmpty();
    }

    public CourierShift(String id, int hotCapacity, int coldCapacity,
                        int startMinute, int durationMinutes) {
        this.id = id;
        this.hotCapacity = hotCapacity;
        this.coldCapacity = coldCapacity;
        //this.startMinute = startMinute;
        //this.durationMinutes = durationMinutes;
    }

    public Integer getStartMinute() {
        if (visits == null || visits.isEmpty()) {
            return null;
        }

        return visits.stream()
                .map(Visit::getTimeMinute)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
    }

    public Integer getEndMinute() {
        if (visits == null || visits.isEmpty()) {
            return null;
        }

        return visits.stream()
                .map(Visit::getTimeMinute)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
    }

    public int getDurationMinutes() {
        Integer start = getStartMinute();
        Integer end = getEndMinute();

        if (start == null || end == null) {
            return 0;
        }
        return end - start;
    }

    //public int getEndMinute() { return startMinute + durationMinutes; }
}