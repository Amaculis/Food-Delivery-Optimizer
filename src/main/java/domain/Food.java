package domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Food {

    public enum Temperature {
        HOT, COLD, ANY
    }

    @PlanningId
    private String id;
    private String chainId;
    private int volume;
    private int prepTimeMinutes;
    private int maxDeliveryMinutes;
    private Temperature temperature;
    private double price;

    public Food() {}

    public Food(String id, int volume, int prepTimeMinutes,
                int maxDeliveryMinutes, Temperature temperature, double price) {
        this.id = id;
        this.volume = volume;
        this.prepTimeMinutes = prepTimeMinutes;
        this.maxDeliveryMinutes = maxDeliveryMinutes;
        this.temperature = temperature;
        this.price = price;
    }

}