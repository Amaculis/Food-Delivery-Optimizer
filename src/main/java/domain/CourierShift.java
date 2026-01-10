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

    private int startMinute;   // minutes since day start
    private int durationMinutes;

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
        this.startMinute = startMinute;
        this.durationMinutes = durationMinutes;
    }

    public int getEndMinute() { return startMinute + durationMinutes; }
}