package domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@PlanningEntity
@Getter
@Setter
public class CourierShift {

    @PlanningId
    private String id;

    private int hotCapacity;
    private int coldCapacity;

    // private int startMinute;   // minutes since day start
    //private int durationMinutes;

    @PlanningListVariable(valueRangeProviderRefs = "visitList")
    List<Visit> visits = new ArrayList<>();

    //public CourierShift(String c2, int i, int i1) {}
    public CourierShift() {
        // Required by Timefold
    }
    public boolean isUsed() {
        return !visits.isEmpty();
    }

    public CourierShift(String id, int hotCapacity, int coldCapacity) {
        this.id = id;
        this.hotCapacity = hotCapacity;
        this.coldCapacity = coldCapacity;
        //this.startMinute = startMinute;
        //this.durationMinutes = durationMinutes;
    }

    //public int getEndMinute() { return startMinute + durationMinutes; }
    public Integer getEndMinute() {
        return visits.stream()
                .map(Visit::getMinuteTime)
                .filter(t -> t != null)
                .max(Integer::compareTo)
                .orElse(null);
    }

    public Integer getStartMinute() {
        return visits.stream()
                .map(Visit::getMinuteTime)
                .filter(t -> t != null)
                .min(Integer::compareTo)
                .orElse(null);
    }

    public int getDurationMinutes() {
        Integer start = getStartMinute();
        Integer end = getEndMinute();
        if (start == null || end == null) return 0;
        return end - start;
    }
}