package generator;

import domain.DeliverySolution;
import domain.Order;
import domain.Restaurant;
import domain.Visit;

import java.util.ArrayList;
import java.util.List;

public class Generator {
    public final class VisitGenerator {

        private VisitGenerator() {}

        public static List<Visit> generateAll(DeliverySolution solution) {

            List<Visit> visits = new ArrayList<>();

            for (Order order : solution.getOrders()) {


                Visit delivery = new Visit();
                //delivery.setId("DELIVERY-" + order.getId());
                delivery.setVisitType(Visit.VisitType.CUSTOMER);
                delivery.setOrder(order);
                delivery.setLocation(order.getDeliveryLocation());
                visits.add(delivery);


                for (Restaurant restaurant : solution.getRestaurantList()) {
                    Visit pickup = new Visit();
                    //pickup.setId("PICKUP-" + order.getId() + "-" + restaurant.getId());
                    pickup.setVisitType(Visit.VisitType.RESTAURANT);
                    pickup.setOrder(order);
                    pickup.setRestaurant(restaurant);
                    pickup.setLocation(restaurant.getLocation());

                    visits.add(pickup);
                }
            }

            return visits;
        }
    }
}