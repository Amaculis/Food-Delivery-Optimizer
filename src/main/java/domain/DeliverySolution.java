package domain;

import ai.timefold.solver.core.api.domain.solution.*;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@PlanningSolution
@Getter
@Setter
public class DeliverySolution {

    @PlanningEntityCollectionProperty
    private List<CourierShift> courierShifts;

    @ValueRangeProvider(id = "visitList")
    @PlanningEntityCollectionProperty
    private List<Visit> visitList;

    @PlanningScore
    private HardSoftScore score;

    //must be multi-slot
    @ProblemFactCollectionProperty
    private List<Restaurant> restaurants;

    @ProblemFactCollectionProperty
    private List<Food> Foods;

    @ProblemFactCollectionProperty
    private List<Order> orders;

    private List<Location> Locations;

    @ProblemFactCollectionProperty
    List<Location> locationList = new ArrayList<>();

    public DeliverySolution() {}

    public DeliverySolution(List<CourierShift> courierShifts, List<Order> orders) {
        this.courierShifts = courierShifts;
        this.orders = orders;
    }

    @ValueRangeProvider(id = "shiftStartRange")
    public List<Integer> getShiftStartRange() {
        return IntStream.range(0, 24 * 60)
                .boxed()
                .collect(Collectors.toList());
    }
}