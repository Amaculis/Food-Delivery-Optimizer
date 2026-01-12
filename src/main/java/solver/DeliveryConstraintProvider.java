package solver;

import domain.*;
import ai.timefold.solver.core.api.score.stream.*;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.Objects;

public class DeliveryConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                //order hard
                orderMustBeDelivered(factory),
                orderMustBeDeliveredInTimeWindow(factory),
                foodMaxDeliveryTimeNotExceeded(factory),

                //courier hard
                courierShiftDurationBetween3And6Hours(factory),

                //courier-order hard
                hotCapacityExceeded(factory),
                coldCapacityExceeded(factory),
                pickupBeforeDelivery(factory),
                sameOrderSameCourier(factory),

                //restaurant hard
                pickupRestaurantMustMatchFoodChain(factory),
                pickupMustHaveRestaurant(factory),
                restaurantMustBeAbleToProcessOrder(factory),
                restaurantMaxParallelCapacity(factory),

                //courier soft
                minimizeCouriers(factory),
        };
    }

    /**
     * HARD:
     * Customer delivery must be inside order delivery window.
     */
    private Constraint orderMustBeDeliveredInTimeWindow(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .filter(v -> v.getMinuteTime() < v.getOrder().getEarliestMinute() || v.getMinuteTime() > v.getOrder().getLatestMinute())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Order must be delivered in time");
    }

    /**
     * HARD:
     * Customer delivery must be inside order delivery window.
     */
    private Constraint orderMustBeDelivered(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .filter(v -> v.getCourier() == null)
                .penalize(HardSoftScore.ofHard(50))
                .asConstraint("Order must be delivered");
    }

    /**
     * HARD:
     * Both pickup and delivery visits for the same order must be assigned to the same courier.
     */
    private Constraint sameOrderSameCourier(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .join(Visit.class, Joiners.equal(Visit::getOrder))
                .filter((v1, v2) -> v2.getType() == Visit.VisitType.RESTAURANT)
                // If one is assigned and the other isn't, OR they are assigned to different couriers
                .filter((cust, rest) -> !Objects.equals(cust.getCourier(), rest.getCourier()))
                .penalize(HardSoftScore.ofHard(10_000)) // Make this VERY high
                .asConstraint("Order visits must be on same courier");
    }

    /**
     * HARD:
     * Pickup visit must use a restaurant from the same chain as the ordered food.
     */
    private Constraint pickupRestaurantMustMatchFoodChain(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.RESTAURANT)
                .filter(v -> v.getRestaurant() != null)
                .filter(v ->
                        v.getOrder().getFoods().stream()
                                .anyMatch(f -> !f.getChainId().equals(v.getRestaurant().getChainId()))
                )
                .penalize(HardSoftScore.ofHard(1_000))
                .asConstraint("Pickup restaurant must match food chain");
    }

    /**
     * HARD:
     * Pickup visit must use a restaurant from the same chain as the ordered food.
     */
    private Constraint pickupMustHaveRestaurant(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.RESTAURANT)
                .filter(v -> v.getRestaurant() == null)
                .penalize(HardSoftScore.ofHard(1_000))
                .asConstraint("Pickup must have restaurant");
    }

    /**
     * HARD:
     * Pickup visit must use a restaurant from the same chain as the ordered food.
     */
    private Constraint restaurantMustBeAbleToProcessOrder(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.RESTAURANT)
                .filter(v -> v.getRestaurant() != null)
                .filter(v -> !Objects.equals(v.getRestaurant().getChainId(), v.getOrder().getChainId()))
                .penalize(HardSoftScore.ofHard(1_000))
                .asConstraint("Order must be processed in correct restaurant chain");
    }

    /**
     * HARD:
     * Pickup visit must use a restaurant from the same chain as the ordered food.
     */
    private Constraint restaurantMaxParallelCapacity(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.RESTAURANT && v.getRestaurant() != null && v.getMinuteTime() != null)
                // Join visits happening at the same restaurant
                .join(Visit.class,
                        Joiners.equal(Visit::getRestaurant),
                        Joiners.lessThan(Visit::getId) // Avoid double-counting (A-B and B-A) and self-comparison
                )
                // Filter for time overlaps
                // An overlap exists if: Visit A starts before Visit B finishes, AND Visit B starts before Visit A finishes
                .filter((v1, v2) -> {
                    int start1 = v1.getMinuteTime() - v1.getOrder().getTotalCookTime();
                    int end1 = v1.getMinuteTime();

                    int start2 = v2.getMinuteTime() - v2.getOrder().getTotalCookTime();
                    int end2 = v2.getMinuteTime();

                    return start1 < end2 && start2 < end1;
                })
                // Group by the first visit to count how many other visits overlap with it
                .groupBy((v1, v2) -> v1, ConstraintCollectors.countBi())
                // Penalize if the number of simultaneous orders (count + 1 for self) > maxParallel
                .filter((v1, overlapCount) -> (overlapCount + 1) > v1.getRestaurant().getParallelCookingCapacity())
                .penalize(HardSoftScore.ONE_HARD, (v1, overlapCount) -> (overlapCount + 1) - v1.getRestaurant().getParallelCookingCapacity())
                .asConstraint("Restaurant max parallel capacity exceeded");
    }

    /**
     * HARD:
     * Hot food capacity per courier shift.
     * Sum HOT food volumes of all CUSTOMER visits in the shift.
     */
    private Constraint hotCapacityExceeded(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .groupBy(
                        Visit::getCourier,
                        ConstraintCollectors.sum(
                                v -> v.getOrder().getFoods().stream()
                                        .filter(f -> f.getTemperature() == Food.Temperature.HOT)
                                        .mapToInt(Food::getVolume)
                                        .sum()
                        )
                )
                .filter((shift, volume) ->
                        shift != null && volume > shift.getHotCapacity())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Hot capacity exceeded");
    }

    /**
     * HARD:
     * Cold food capacity per courier shift.
     */
    private Constraint coldCapacityExceeded(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .groupBy(
                        Visit::getCourier,
                        ConstraintCollectors.sum(
                                v -> v.getOrder().getFoods().stream()
                                        .filter(f -> f.getTemperature() == Food.Temperature.COLD)
                                        .mapToInt(Food::getVolume)
                                        .sum()
                        )
                )
                .filter((shift, volume) ->
                        shift != null && volume > shift.getColdCapacity())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Cold capacity exceeded");
    }

    /**
     * HARD:
     * Pickup must occur before delivery.
     * Ensures the customer visit comes after the restaurant visit in the route.
     */
    private Constraint pickupBeforeDelivery(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .join(Visit.class,
                        Joiners.equal(Visit::getOrder, Visit::getOrder),
                        Joiners.filtering((cust, rest) -> rest.getType() == Visit.VisitType.RESTAURANT))
                .filter((cust, rest) -> {
                    // If times are null (unassigned), we can't compare, so skip
                    if (cust.getMinuteTime() == null || rest.getMinuteTime() == null) return false;
                    // Violation if Pickup Time >= Delivery Time
                    return rest.getMinuteTime() >= cust.getMinuteTime();
                })
                .penalize(HardSoftScore.ofHard(1_000))
                .asConstraint("Pickup must occur before delivery");
    }
    /**
     * SOFT:
     * Do not use new couriers if possible.
     * Make extra penalty if courier have been added.
     */
    private static final int EXTRA_COURIER_PENALTY = 20;
    private static final int COURIER_TIME_PENALTY = 5;
    private Constraint minimizeCouriers(ConstraintFactory factory) {
        return factory.forEach(CourierShift.class)
                .filter(CourierShift::isUsed)
                .penalize(HardSoftScore.ONE_SOFT,
                        c -> {
                            var endTime = (int)Math.ceil((float)Math.max(c.getEndMinute() - c.getStartMinute(), 180)/60);
                            return EXTRA_COURIER_PENALTY + endTime * COURIER_TIME_PENALTY;
                        }
                )
                .asConstraint("Extra couriers are expensive");
    }

    /**
     * Hard:
     * Courier shift limits.
     * Courier is not allowed to work between 3 and 6 hours.
     */
    private Constraint courierShiftDurationBetween3And6Hours(ConstraintFactory factory) {
        return factory.forEach(CourierShift.class)
                .filter(CourierShift::isUsed)
                .filter(shift -> {
                    int d = shift.getDurationMinutes();
                    return d > 360;
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Courier shift must not be more than 6 hours");
    }

    /**
     * HARD:
     * Food maximum delivery time must not be exceeded.
     * Measured from pickup minuteTime to delivery minuteTime.
     */
    private Constraint foodMaxDeliveryTimeNotExceeded(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .join(Visit.class,
                        Joiners.equal(Visit::getOrder),
                        Joiners.filtering((cust, rest) -> rest.getType() == Visit.VisitType.RESTAURANT))
                .filter((cust, rest) -> cust.getMinuteTime() != null && rest.getMinuteTime() != null)
                .filter((cust, rest) -> calculateDeliveryTimePenalty(cust, rest) > 0)
                .penalize(HardSoftScore.ONE_HARD, this::calculateDeliveryTimePenalty)
                .asConstraint("Food max delivery time exceeded");
    }

    // private method that calculates the penalty
    private int calculateDeliveryTimePenalty(Visit cust, Visit rest) {
        int pickupTime = rest.getMinuteTime();
        int customerDeliveryTime = cust.getMinuteTime();

        if (customerDeliveryTime <= pickupTime) {
            return 0;
        }

        int allowedDeliveryTime = cust.getOrder().getMinAllowedTimeToDeliver();

        int actualDeliveryTime = customerDeliveryTime - pickupTime;

        return Math.max(0, actualDeliveryTime - allowedDeliveryTime);
    }
}