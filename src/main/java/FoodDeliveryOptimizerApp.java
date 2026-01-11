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

        solution.getCourierShifts().forEach(shift -> {
            log.info("Courier {} schedule:", shift.getId());
            if (shift.getVisits().isEmpty()) {
                log.info("  No visits assigned");
                return;
            }

            // visits are already ordered in the route
            shift.getVisits().forEach(visit -> {
                String type = visit.getType() == Visit.VisitType.RESTAURANT ? "PICKUP" : "DELIVERY";
                Integer time = visit.getMinuteTime();
                String timeStr = (time != null) ? String.format("%02d:%02d", time / 60, time % 60) : "UNASSIGNED";

                log.info("  {} Visit for Order {} at {} ({})",
                        type,
                        visit.getOrder().getId(),
                        timeStr,
                        visit.getLocation().getId()
                );
            });


        });
    }

    @NotNull
    private static DeliverySolution getSolution() {
        Food pizza = new Food("Pizza", 3, 15, 45, Food.Temperature.HOT, 12.0);
        Food salad = new Food("Salad", 2, 5, 30, Food.Temperature.COLD, 7.0);

        Location restaurant1 = new Location(1L, 56.95, 24.11);
        Location restaurant2 = new Location(2L, 56.96, 24.12);
        Location customerA = new Location(100L, 56.97, 24.13);
        Location customerB = new Location(101L, 56.98, 24.14);
        Location customerC = new Location(102L, 56.99, 24.15);

        Restaurant r1 = new Restaurant("R1", "ChainA", 5, false);
        r1.setLocation(restaurant1);
        Restaurant r2 = new Restaurant("R2", "ChainB", 3, false);
        r2.setLocation(restaurant2);

        CourierShift c1 = new CourierShift("C1", 10, 5);//, 480, 240); // 08:00–12:00
        CourierShift c2 = new CourierShift("C2", 6, 10);//, 540, 600);

        // Order 1
        Order o1 = new Order("O1", 500, 560, List.of(pizza));
        Visit o1Pickup = new Visit(o1, restaurant1, Visit.VisitType.RESTAURANT);
        Visit o1Delivery = new Visit(o1, customerA, Visit.VisitType.CUSTOMER);
        o1.setRestaurantVisit(o1Pickup);
        o1.setCustomerVisit(o1Delivery);

        // Order 2
        Order o2 = new Order("O2", 520, 600, List.of(salad));
        Visit o2Pickup = new Visit(o2, restaurant2, Visit.VisitType.RESTAURANT);
        Visit o2Delivery = new Visit(o2, customerB, Visit.VisitType.CUSTOMER);
        o2.setRestaurantVisit(o2Pickup);
        o2.setCustomerVisit(o2Delivery);

        // Order 3
        Order o3 = new Order("O3", 1080, 1100, List.of(pizza, salad));
        Visit o3Pickup = new Visit(o3, restaurant1, Visit.VisitType.RESTAURANT);
        Visit o3Delivery = new Visit(o3, customerC, Visit.VisitType.CUSTOMER);
        o3.setRestaurantVisit(o3Pickup);
        o3.setCustomerVisit(o3Delivery);

        // Order 4 
        Order o4 = new Order("O4", 1130, 1280, List.of(pizza));
        Visit o4Pickup = new Visit(o4, restaurant2, Visit.VisitType.RESTAURANT);
        Visit o4Delivery = new Visit(o4, customerA, Visit.VisitType.CUSTOMER);
        o4.setRestaurantVisit(o4Pickup);
        o4.setCustomerVisit(o4Delivery);

        // Order 5 
        Order o5 = new Order("O5", 800, 850, List.of(salad));
        Visit o5Pickup = new Visit(o5, restaurant1, Visit.VisitType.RESTAURANT);
        Visit o5Delivery = new Visit(o5, customerB, Visit.VisitType.CUSTOMER);
        o5.setRestaurantVisit(o5Pickup);
        o5.setCustomerVisit(o5Delivery);


        List<Visit> visits = List.of(
                o1Pickup, o1Delivery,
                o2Pickup, o2Delivery,
                o3Pickup, o3Delivery,
                o4Pickup, o4Delivery,
                o5Pickup, o5Delivery
        );

        List<Order> orders = List.of(o1, o2, o3);
        List<CourierShift> courierShifts = List.of(c1, c2);
        List<Restaurant> restaurants = List.of(r1, r2);
        List<Food> foods = List.of(pizza, salad);
        List<Location> locations = List.of(restaurant1, restaurant2, customerA, customerB, customerC);

        DeliverySolution problem = new DeliverySolution();
        problem.setOrders(orders);
        problem.setVisitList(visits);
        problem.setCourierShifts(courierShifts);
        problem.setRestaurants(restaurants);
        problem.setFoods(foods);
        problem.setLocationList(locations);

        return problem;
    }
}