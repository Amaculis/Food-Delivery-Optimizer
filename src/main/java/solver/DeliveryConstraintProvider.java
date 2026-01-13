package solver;

import domain.*;
import ai.timefold.solver.core.api.score.stream.*;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.Objects;
import java.util.function.Function;

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
                deliveryRequiresPickup(factory),

                //restaurant hard
                pickupRestaurantMustMatchFoodChain(factory),
                restaurantMustBeAbleToProcessOrder(factory),
                restaurantMaxParallelCapacity(factory),

                //soft
                minimizeCouriers(factory),
                minimizeTotalDistance(factory),
                awardForIsBoostOrder(factory)
        };
    }

    /**
     * HARD:
     * Customer delivery must be inside order delivery window.
     */
    private Constraint orderMustBeDeliveredInTimeWindow(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .join(Order.class,
                        Joiners.equal(Visit::getOrder, o -> o))
                .penalize(HardSoftScore.ONE_HARD,
                        (v, o) -> {
                            Integer time = v.getMinuteTime();
                            if (time == null) return 0;
                            if (time < o.getEarliestMinute() || time > o.getLatestMinute()) return Math.max(o.getEarliestMinute() - time, time - o.getLatestMinute())/10;
                            return 0;
                        })
                .asConstraint("Order must be delivered in time");
    }

    /**
     * HARD:
     * Customer delivery must be inside order delivery window.
     */
    private Constraint orderMustBeDelivered(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .filter(v -> v.getCourier() == null)
                .penalize(HardSoftScore.ofHard(5))
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
                .filter((v1, v2) -> v2.getType() == Visit.VisitType.RESTAURANT && v2.getCourier() != null)
                // If one is assigned and the other isn't, OR they are assigned to different couriers
                .filter((cust, rest) -> !Objects.equals(cust.getCourier(), rest.getCourier()))
                .penalize(HardSoftScore.ofHard(10)) // Make this VERY high
                .asConstraint("Order visits must be on same courier");
    }

    /**
     * HARD:
     * Pickup visit must use a restaurant from the same chain as the ordered food.
     */
    private Constraint pickupRestaurantMustMatchFoodChain(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.RESTAURANT && v.getCourier() != null)
                .filter(v ->
                        v.getOrder().getFoods().stream()
                                .anyMatch(f -> !f.getChainId().equals(v.getRestaurant().getChainId()))
                )
                .penalize(HardSoftScore.ofHard(10))
                .asConstraint("Pickup restaurant must match food chain");
    }

    /**
     * HARD:
     * Pickup visit must use a restaurant from the same chain as the ordered food.
     */
    private Constraint restaurantMustBeAbleToProcessOrder(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.RESTAURANT && v.getCourier() != null)
                .filter(v -> !Objects.equals(v.getRestaurant().getChainId(), v.getOrder().getChainId()))
                .penalize(HardSoftScore.ofHard(10))
                .asConstraint("Order must be processed in correct restaurant chain");
    }

    /**
     * HARD:
     * Pickup visit must use a restaurant from the same chain as the ordered food.
    */
    private Constraint restaurantMaxParallelCapacity(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.RESTAURANT && v.getMinuteTime() != null)
                // Join visits happening at the same restaurant
                .join(Visit.class,
                        Joiners.equal(Visit::getLocation),
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
     * If a customer delivery is assigned, there must be a corresponding restaurant pickup assigned.
     * Ensures that every assigned customer visit has at least one restaurant visit for the same order.
     */
    private Constraint deliveryRequiresPickup(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .filter(v -> v.getCourier() != null) // Customer delivery is assigned
                .ifNotExists(Visit.class,
                        Joiners.equal(Visit::getOrder, Visit::getOrder),
                        Joiners.filtering((cust, rest) ->
                                rest.getType() == Visit.VisitType.RESTAURANT &&
                                        rest.getCourier() != null)) // No assigned restaurant pickup found
                .penalize(HardSoftScore.ofHard(10))
                .asConstraint("Delivery requires pickup");
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
                        Joiners.filtering((cust, rest) -> rest.getType() == Visit.VisitType.RESTAURANT && rest.getCourier() != null))
                .filter((cust, rest) -> {
                    // If times are null (unassigned), we can't compare, so skip
                    if (cust.getMinuteTime() == null || rest.getMinuteTime() == null) return false;
                    // Violation if Pickup Time >= Delivery Time
                    return rest.getMinuteTime() >= cust.getMinuteTime();
                })
                .penalize(HardSoftScore.ofHard(10))
                .asConstraint("Pickup must occur before delivery");
    }
    /**
     * SOFT:
     * Do not use new couriers if possible.
     * Make extra penalty if courier have been added.
     */
    private static final int EXTRA_COURIER_PENALTY = 30;
    private static final int COURIER_TIME_PENALTY = 10;
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

    private Constraint minimizeTotalDistance(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(visit -> visit.getPreviousVisit() != null)
                .join(Visit.class,
                        Joiners.equal(Function.identity(), Visit::getPreviousVisit)) // Join current visit with its previous
                .penalize(HardSoftScore.ONE_SOFT,
                        (currentVisit, previousVisit) -> {
                            Location from = previousVisit.getLocation();
                            Location to = currentVisit.getLocation();
                            if (from == null || to == null) return 0;
                            Long seconds = from.timeTo(to);
                            return seconds != null ? seconds.intValue() / 60 : 0;
                        })
                .asConstraint("Minimize total travel time");
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
                .penalize(HardSoftScore.ONE_HARD, shift -> (shift.getDurationMinutes()/360) * 5)
                .asConstraint("Courier shift must not be more than 6 hours");
    }

    private static final double isBoostCoefficient = 0.15;
    private Constraint awardForIsBoostOrder(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.RESTAURANT)
                .filter(v -> v.getRestaurant() != null && v.getRestaurant().getBoost())
                .reward(HardSoftScore.ONE_SOFT, v -> (int)Math.floor(v.getOrder().getTotalCost() * isBoostCoefficient)*100)
                .asConstraint("Reward for IsBoost Restaurant usage");
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
                .penalize(HardSoftScore.ONE_HARD,
                        (cust, rest) -> {
                            Integer dTime = cust.getMinuteTime();
                            Integer pTime = rest.getMinuteTime();
                            if (dTime == null || pTime == null) return 0;
                            return calculateDeliveryTimePenalty(cust, rest) > 0 ? 1 : 0;
                        })
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