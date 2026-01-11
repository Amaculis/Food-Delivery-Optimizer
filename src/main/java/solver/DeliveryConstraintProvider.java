package solver;

import domain.*;
import ai.timefold.solver.core.api.score.stream.*;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

public class DeliveryConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                orderMustBeDelivered(factory),
                deliveryMustBeAssigned(factory),
                //courierShiftMustCoverOrderTime(factory),
                hotCapacityExceeded(factory),
                coldCapacityExceeded(factory),
                customerDeliveryNotInTimeWindow(factory),
                pickupBeforeDelivery(factory),
                sameOrderSameCourier(factory),
                minimizeCouriers(factory),
                courierShiftDurationBetween3And6Hours(factory),
                courierIdleGapTooLarge(factory)
                foodMaxDeliveryTimeNotExceeded(factory),
        };
    }

    /**
     * HARD:
     * Customer delivery must be inside order delivery window.
     */
    //??? What is the difference with deliveryMustBeAssigned
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
                // Join with the Restaurant visit of the SAME order
                .join(Visit.class,
                        Joiners.equal(Visit::getOrder),
                        Joiners.filtering((cust, rest) -> rest.getType() == Visit.VisitType.RESTAURANT))//get the pair only if second entity is restaurant (first then will always be customer)
                // Now we have both CLONED instances currently managed by the solver
                .filter((cust, rest) -> cust.getCourier() != null && (rest.getCourier() == null
                        || !cust.getCourier().getId().equals(rest.getCourier().getId())))
                .penalize(HardSoftScore.ofHard(1000))
                .asConstraint("Order must be picked up and delivered by the same courier");
    }

    //??? What is the difference with orderMustBeDelivered
    private Constraint deliveryMustBeAssigned(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .filter(v -> v.getCourier() == null)
                .penalize(HardSoftScore.ofHard(1_000))
                .asConstraint("Delivery must be assigned");
    }

        /**
         * HARD:
         * Courier shift must cover the order time window.
         * We evaluate this on CUSTOMER visits (one per order).
         * !!!!!!!!!! Temporarary commented out, because could not use second courier  - ArtjomsM
    //private Constraint courierShiftMustCoverOrderTime(ConstraintFactory factory) {
    /*    return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .filter(v -> v.getCourier() != null)
                .filter(v -> {
                    CourierShift shift = v.getCourier();
                    Order order = v.getOrder();
                    return shift.getStartMinute() > order.getEarliestMinute()
                            || shift.getEndMinute() < order.getLatestMinute();
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Courier shift outside order time window");
    }
*/

    private Constraint visitMustBeWithinCourierShift(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getCourier() != null)
                .filter(v -> v.getMinuteTime() != null)
                .filter(v -> {
                    CourierShift shift = v.getCourier();
                    int t = v.getMinuteTime();
                    int start = shift.getStartMinute();
                    int end = shift.getEndMinute();
                    return t < start || t > end;
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Visit must be within courier shift");
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
     * Customer delivery must be inside order delivery window.
     */
    private Constraint customerDeliveryNotInTimeWindow(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getType() == Visit.VisitType.CUSTOMER)
                .filter(v -> v.getMinuteTime() != null)
                .filter(v -> {
                    int t = v.getMinuteTime();
                    Order o = v.getOrder();
                    return t < o.getEarliestMinute() || t > o.getLatestMinute();
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Customer delivery time window violated");
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
    private static final int EXTRA_COURIER_PENALTY = 10;

    private Constraint minimizeCouriers(ConstraintFactory factory) {
        return factory.forEach(CourierShift.class)
                .filter(CourierShift::isUsed)
                .groupBy(ConstraintCollectors.count())
                .filter(c -> c > 1)
                .penalize(
                        HardSoftScore.ofSoft(EXTRA_COURIER_PENALTY),
                        c -> c - 1
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
                    return d < 180 || d > 360;
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Courier shift must be between 3 and 6 hours");
    }

    private Constraint courierIdleGapTooLarge(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
                .filter(v -> v.getCourier() != null)
                .filter(v -> v.getPreviousVisit() != null)
                .filter(v -> v.getMinuteTime() != null)
                .filter(v -> v.getPreviousVisit().getMinuteTime() != null)
                .filter(v -> {
                    int gap = v.getMinuteTime()
                            - v.getPreviousVisit().getMinuteTime();
                    return gap > 45;
                })
                //.penalize(HardSoftScore.ONE_HARD)
                .penalize(HardSoftScore.ofHard(10))

                .asConstraint("Courier shift too large");
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
                        Joiners.filtering((cust, rest) ->
                                rest.getType() == Visit.VisitType.RESTAURANT))
                .filter((cust, rest) ->
                        cust.getMinuteTime() != null && rest.getMinuteTime() != null)
                .penalize(HardSoftScore.ONE_HARD, (cust, rest) -> {
                    int pickupTime = rest.getMinuteTime();
                    int customerDeliveryTime = cust.getMinuteTime();

                    // !!!TODO handle delivery before pickup via another constraint
                    if (customerDeliveryTime <= pickupTime) {
                        return 0;
                    }

                    int allowedDeliveryTime = cust.getOrder().getFoods().stream()
                            .mapToInt(Food::getMaxDeliveryMinutes)
                            .min()
                            .orElse(Integer.MAX_VALUE);

                    if (allowedDeliveryTime == Integer.MAX_VALUE) {
                        return 0;
                    }

                    int actualDeliveryTime = customerDeliveryTime - pickupTime;

                    return Math.max(0, actualDeliveryTime - allowedDeliveryTime);
                })
                .asConstraint("Food max delivery time exceeded");
    }
}