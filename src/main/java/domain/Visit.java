package domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.CascadingUpdateShadowVariable;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.*;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;

@PlanningEntity
@Getter
@Setter

public class Visit {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    @PlanningId
    private Long id;

 //   @PlanningVariable(valueRangeProviderRefs = "restaurantList")
    //private Restaurant restaurant; //with allowUnassigned = true it doesn't work... It was better to split visit for Restaurant and Customer initially, now we need to assign dummy restaurant for all Customers

    private Location location;

    private Order order;
    private VisitType type; // RESTAURANT or CUSTOMER

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    private CourierShift courier;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    private Visit previousVisit;
    @NextElementShadowVariable(sourceVariableName = "visits")
    private Visit nextVisit;

    @CascadingUpdateShadowVariable(targetMethodName = "updateData")
    private Integer minuteTime;
    public enum VisitType {
        RESTAURANT,
        CUSTOMER
    }

    private Restaurant restaurant;

    public Visit() {this.id = ID_GENERATOR.incrementAndGet();}

    public Visit(Order order, Location location, VisitType type) {
        this.id = ID_GENERATOR.incrementAndGet();
        this.order = order;
        this.location = location;
        this.type = type;
    }

    public Visit(Order order, Location location, VisitType type, Restaurant restaurant) {
        this.id = ID_GENERATOR.incrementAndGet();
        this.order = order;
        this.location = location;
        this.type = type;
        this.restaurant = restaurant;
    }
    public void setVisitType(VisitType visitType) {
        this.type = visitType;
    }
    public void updateData() {
        updateDeliveryTime();
    }
    private void updateDeliveryTime() {
        if (this.getCourier() == null) {
            this.setMinuteTime(null);
            return;
        }

        int arrivalTime;
        if (this.getPreviousVisit() == null) {
            // The courier starts their shift
            arrivalTime = this.getOrder().getEarliestMinute();
        } else {
            Integer prevTime = this.getPreviousVisit().getMinuteTime();
            if (prevTime == null) {
                this.setMinuteTime(null);
                return;
            }
            int travelTime = calculateTravelTime(this.getPreviousVisit().getLocation(), this.getLocation());
            int serviceTime = getServiceTime(this.getPreviousVisit());
            arrivalTime = prevTime + travelTime + serviceTime;
        }

        // IMPORTANT: If we arrive before the order is ready, we MUST wait.
        // This pushes the time forward and ensures constraints are calculated correctly.
        int readyTime = order.getEarliestMinute();
        this.setMinuteTime(Math.max(arrivalTime, readyTime));
    }

    private int calculateTravelTime(Location from, Location to) {
        if (from == null || to == null) return 0;

        Long seconds = from.timeTo(to);
        if (seconds == null) {
            return 0;
        }

        return (int) Math.ceil(seconds / 60.0);
    }

    private int getServiceTime(Visit visit) {
        // Time spent at the location (pickup or delivery)
        if (visit.getType() == VisitType.RESTAURANT) {
            // Pickup time at restaurant
            return 5; // minutes
        } else {
            // Delivery time at customer
            return 3; // minutes
        }
    }

    public void setCourierShift(CourierShift courierShift) {
        this.courier = courierShift;
    }
}