import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;

import domain.*;
import domain.Food.Temperature;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Slf4j
public class FoodDeliveryOptimizerApp {

    public static void main(String[] args) {
        // --- Courier shifts ---
        DeliverySolution problem = getSolution();

        Router router = Router.getDefaultRouterInstance();
        router.setDistanceTimeMap(problem.getLocationList());

        SolverFactory<DeliverySolution> solverFactory =
                SolverFactory.createFromXmlResource("solverConfig.xml");

        Solver<DeliverySolution> solver = solverFactory.buildSolver();
        DeliverySolution solution = solver.solve(problem);

        var solutionManager = SolutionManager.create(solverFactory);
        var scoreAnalysis = solutionManager.analyze(solution);
        System.out.println(scoreAnalysis.summarize());

        // --- Result ---
        log.info("");
        log.info("Final score: {}", solution.getScore());

        var t = solution.getVisitList();
        solution.getCourierShifts().forEach(shift -> {
            String startStr = shift.getStartMinute() != null
                    ? String.format("%02d:%02d", shift.getStartMinute() / 60, shift.getStartMinute() % 60)
                    : "UNASSIGNED";

            String endStr = shift.getEndMinute() != null
                    ? String.format("%02d:%02d", shift.getEndMinute() / 60, shift.getEndMinute() % 60)
                    : "UNASSIGNED";

            log.info("Courier {} ({}-{}) schedule:", shift.getId(), startStr, endStr);
            if (shift.getVisits().isEmpty()) {
                log.info("  No visits assigned");
                return;
            }

            // visits are already ordered in the route
            shift.getVisits().forEach(visit -> {
                String type = visit.getType() == Visit.VisitType.RESTAURANT ? "PICKUP" : "DELIVERY";
                Integer time = visit.getMinuteTime();
                String timeStr = (time != null) ? String.format("%02d:%02d", time / 60, time % 60) : "UNASSIGNED";

                if(visit.getType() == Visit.VisitType.RESTAURANT) {
                    log.info("  {} Visit for Order {}({}-{}) at {} ({})",
                            type,
                            visit.getOrder().getId(),
                            visit.getRestaurant().getId(),
                            visit.getRestaurant().getChainId(),
                            timeStr,
                            visit.getLocation().getId()
                    );
                }
                else {
                    log.info("  {} Visit for Order {} at {} ({})",
                            type,
                            visit.getOrder().getId(),
                            timeStr,
                            visit.getLocation().getId()
                    );
                }
            });
        });
    }

    @NotNull
    private static DeliverySolution getSolution() {

        // -------- Foods (linked to chain, NOT restaurant) --------
        Food pizza = new Food("Pizza", 3, 15, 45, Food.Temperature.HOT, 12.0);
        pizza.setChainId("ChainA");

        Food burger = new Food("Burger", 4, 12, 40, Food.Temperature.HOT, 10.0);
        burger.setChainId("ChainA");

        Food salad = new Food("Salad", 2, 5, 30, Food.Temperature.COLD, 7.0);
        salad.setChainId("ChainB");

        Food sushi = new Food("Sushi", 2, 20, 50, Food.Temperature.COLD, 15.0);
        sushi.setChainId("ChainB");

        // -------- Locations --------
        Location rA1Loc = new Location(1L, 56.95, 24.11);
        Location rA2Loc = new Location(2L, 56.96, 24.10);
        Location rB1Loc = new Location(3L, 56.97, 24.12);
        Location rB2Loc = new Location(4L, 56.98, 24.13);

        Location customerA = new Location(100L, 56.99, 24.14);
        Location customerB = new Location(101L, 57.00, 24.15);
        Location customerC = new Location(102L, 57.01, 24.16);

        // -------- Restaurants (multiple per chain) --------
        Restaurant rA1 = new Restaurant("RA1", "ChainA", 4, false);
        rA1.setLocation(rA1Loc);

        Restaurant rA2 = new Restaurant("RA2", "ChainA", 6, false);
        rA2.setLocation(rA2Loc);

        Restaurant rB1 = new Restaurant("RB1", "ChainB", 2, false);
        rB1.setLocation(rB1Loc);

        Restaurant rB2 = new Restaurant("RB2", "ChainB", 1, false);
        rB2.setLocation(rB2Loc);

        // -------- Courier shifts --------
        CourierShift c1 = new CourierShift("C1", 12, 6);
        CourierShift c2 = new CourierShift("C2", 8, 10);
        CourierShift c3 = new CourierShift("C3", 10, 8);

        // -------- Orders (chain-based, NOT restaurant-based) --------
        Order o1 = new Order("O1", 480, 540, List.of(pizza, burger));
        Order o2 = new Order("O2", 500, 580, List.of(salad));
        Order o2_2 = new Order("O2_2", 500, 580, List.of(salad));
        Order o3 = new Order("O3", 520, 600, List.of(sushi));
        Order o4 = new Order("O4", 700, 780, List.of(pizza));
        Order o5 = new Order("O5", 900, 980, List.of(burger));

        o1.setDeliveryLocation(customerA);
        o2.setDeliveryLocation(customerB);
        o2_2.setDeliveryLocation(customerB);
        o3.setDeliveryLocation(customerC);
        o4.setDeliveryLocation(customerA);
        o5.setDeliveryLocation(customerB);

        // -------- Visits (restaurant chosen later by solver) --------
        Visit o1Pickup = new Visit(o1, rA1Loc, rA1, Visit.VisitType.RESTAURANT);
        Visit o1Delivery = new Visit(o1, customerA, rA1, Visit.VisitType.CUSTOMER);

        Visit o2Pickup = new Visit(o2, rA1Loc, rA1, Visit.VisitType.RESTAURANT);
        Visit o2Delivery = new Visit(o2, customerB, rA1, Visit.VisitType.CUSTOMER);

        Visit o2_2Pickup = new Visit(o2_2, rA1Loc, rA1, Visit.VisitType.RESTAURANT);
        Visit o2_2Delivery = new Visit(o2_2, customerB, rA1, Visit.VisitType.CUSTOMER);

        Visit o3Pickup = new Visit(o3, rA1Loc, rA1, Visit.VisitType.RESTAURANT);
        Visit o3Delivery = new Visit(o3, customerC, rA1, Visit.VisitType.CUSTOMER);

        Visit o4Pickup = new Visit(o4, rA1Loc, rA1, Visit.VisitType.RESTAURANT);
        Visit o4Delivery = new Visit(o4, customerA, rA1, Visit.VisitType.CUSTOMER);

        Visit o5Pickup = new Visit(o5, rA1Loc, rA1, Visit.VisitType.RESTAURANT);
        Visit o5Delivery = new Visit(o5, customerB, rA1, Visit.VisitType.CUSTOMER);

        o1.setRestaurantVisit(o1Pickup);
        o1.setCustomerVisit(o1Delivery);
        o2.setRestaurantVisit(o2Pickup);
        o2.setCustomerVisit(o2Delivery);
        o2_2.setRestaurantVisit(o2_2Pickup);
        o2_2.setCustomerVisit(o2_2Delivery);
        o3.setRestaurantVisit(o3Pickup);
        o3.setCustomerVisit(o3Delivery);
        o4.setRestaurantVisit(o4Pickup);
        o4.setCustomerVisit(o4Delivery);
        o5.setRestaurantVisit(o5Pickup);
        o5.setCustomerVisit(o5Delivery);

        List<Visit> visits = List.of(
                o1Pickup, o1Delivery,
                o2Pickup, o2Delivery,
                o2_2Pickup, o2_2Delivery,
                o3Pickup, o3Delivery,
                o4Pickup, o4Delivery,
                o5Pickup, o5Delivery
        );

        // -------- Solution --------
        DeliverySolution problem = new DeliverySolution();
        problem.setCourierShifts(List.of(c1, c2, c3));
        problem.setVisitList(visits);
        problem.setOrders(List.of(o1, o2, o2_2, o3, o4, o5));
        problem.setRestaurantList(List.of(rA1, rA2, rB1, rB2));
        problem.setFoods(List.of(pizza, burger, salad, sushi));
        problem.setLocationList(List.of(
                rA1Loc, rA2Loc, rB1Loc, rB2Loc,
                customerA, customerB, customerC
        ));

        return problem;
    }
}