package domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Order {

    private String id;
    private List<Food> foods;
    private int earliestMinute;
    private int latestMinute;
    private Location deliveryLocation;

    @InverseRelationShadowVariable(sourceVariableName = "order")
    private Visit restaurantVisit;
    @InverseRelationShadowVariable(sourceVariableName = "order")
    private Visit customerVisit;
    
    private String chainId;
    private int minAllowedTimeToDeliver;
    private int totalCookTime;
    private double totalCost;
    public Order() {}

    public Order(String id, int earliestMinute, int latestMinute, List<Food> foods) {
        this.id = id;
        this.earliestMinute = earliestMinute;
        this.latestMinute = latestMinute;
        this.foods = foods;
        minAllowedTimeToDeliver = foods.stream()
                .mapToInt(Food::getMaxDeliveryMinutes)
                .min()
                .orElse(Integer.MAX_VALUE);
        totalCookTime = foods.stream()
                .mapToInt(Food::getPrepTimeMinutes)
                .sum();
        totalCost = foods.stream()
                .mapToDouble(Food::getPrice)
                .sum();
        chainId = foods.get(0).getChainId();
    }
}