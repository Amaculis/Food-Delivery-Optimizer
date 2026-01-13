package domain;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Restaurant {
    @PlanningId
    private String id;
    private String chainId;

    private int startMinute; // e.g., 480 = 08:00
    private int endMinute;   // e.g., 1320 = 22:00

    private Location Location;

    private int parallelCookingCapacity;

    private boolean boost;
    public boolean getBoost() { return boost; }

    public Restaurant() {}

    public Restaurant(String id, String chainId, int parallelCookingCapacity, boolean boost) {
        this.id = id;
        this.chainId = chainId;
        this.parallelCookingCapacity = parallelCookingCapacity;
        this.boost = boost;
    }

}