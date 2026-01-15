package generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import domain.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonIO {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static DeliverySolution read_json(String jsonPath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File(jsonPath));

            // Locations
            Map<Long, Location> locationMap = new HashMap<>();
            JsonNode locationList = root.get("locationList");
            if (locationList != null) {
                for (JsonNode locNode : locationList) {
                    Location location = new Location(
                            locNode.get("id").asLong(),
                            locNode.get("lat").asDouble(),
                            locNode.get("lon").asDouble()
                    );
                    locationMap.put(location.getId(), location);
                }
            }

            // Food
            Map<String, Food> foodMap = new HashMap<>();
            JsonNode foodList = root.get("foods");
            if (foodList != null) {
                for (JsonNode foodNode : foodList) {
                    Food food = new Food(
                            foodNode.get("id").asText(),
                            foodNode.get("volume").asInt(),
                            foodNode.get("prepTimeMinutes").asInt(),
                            foodNode.get("maxDeliveryMinutes").asInt(),
                            Food.Temperature.valueOf(foodNode.get("temperature").asText()),
                            foodNode.get("price").asDouble()
                    );
                    food.setChainId(foodNode.get("chainId").asText());
                    foodMap.put(food.getId(), food);
                }
            }

            // Create restaurants with locations
            List<Restaurant> restaurants = new ArrayList<>();
            JsonNode restaurantList = root.get("restaurantList");
            if (restaurantList != null) {
                for (JsonNode restNode : restaurantList) {
                    // Обработка location (может быть строка, число или объект)
                    Long locationId = extractLocationId(restNode.get("location"));
                    Location location = locationMap.get(locationId);

                    if (location == null) {
                        throw new RuntimeException("Location with id " + locationId + " not found for restaurant " + restNode.get("id").asText());
                    }

                    Restaurant restaurant = new Restaurant(
                            restNode.get("id").asText(),
                            restNode.get("chainId").asText(),
                            restNode.get("parallelCookingCapacity").asInt(),
                            restNode.get("boost").asBoolean()
                    );
                    restaurant.setLocation(location);
                    restaurants.add(restaurant);
                }
            }

            // Create orders with restaurants and locations
            List<Order> orders = new ArrayList<>();
            JsonNode orderList = root.get("orders");
            if (orderList != null) {
                for (JsonNode orderNode : orderList) {
                    // Delivery
                    Long deliveryLocationId = extractLocationId(orderNode.get("deliveryLocation"));
                    Location deliveryLocation = locationMap.get(deliveryLocationId);

                    if (deliveryLocation == null) {
                        throw new RuntimeException("Location with id " + deliveryLocationId + " not found for order " + orderNode.get("id").asText());
                    }

                    // Check "foods"
                    List<Food> orderFoods = new ArrayList<>();
                    JsonNode foodsNode = orderNode.get("foods");

                    if (foodsNode != null) {
                        for (JsonNode foodIdNode : foodsNode) {
                            String foodId = foodIdNode.asText();
                            Food food = foodMap.get(foodId);
                            if (food == null) {
                                throw new RuntimeException("Food with id " + foodId + " not found for order " + orderNode.get("id").asText());
                            }
                            orderFoods.add(food);
                        }
                    }

                    Order order = new Order(
                            orderNode.get("id").asText(),
                            orderNode.get("earliestMinute").asInt(),
                            orderNode.get("latestMinute").asInt(),
                            orderFoods
                    );
                    order.setDeliveryLocation(deliveryLocation);
                    orders.add(order);
                }
            }

            //  CourierShift
            List<CourierShift> couriers = new ArrayList<>();
            JsonNode courierList = root.get("courierShifts");
            if (courierList != null) {
                for (JsonNode courierNode : courierList) {
                    CourierShift courier = new CourierShift(
                            courierNode.get("id").asText(),
                            courierNode.get("hotCapacity").asInt(),
                            courierNode.get("coldCapacity").asInt()
                    );
                    courier.setVisits(new ArrayList<>());
                    couriers.add(courier);
                }
            }

            // Create solution
            DeliverySolution solution = new DeliverySolution();
            solution.setCourierShifts(couriers);
            solution.setOrders(orders);
            solution.setRestaurantList(restaurants);
            solution.setFoods(new ArrayList<>(foodMap.values()));
            solution.setLocationList(new ArrayList<>(locationMap.values()));

            return solution;

        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON: " + jsonPath, e);
        }
    }

    private static Long extractLocationId(JsonNode locationNode) {
        if (locationNode == null) {
            throw new RuntimeException("Location node is null");
        }

        if (locationNode.isTextual()) {
            return Long.parseLong(locationNode.asText());
        }

        if (locationNode.isNumber()) {
            return locationNode.asLong();
        }

        if (locationNode.isObject()) {
            JsonNode idNode = locationNode.get("id");
            if (idNode != null) {
                if (idNode.isTextual()) {
                    return Long.parseLong(idNode.asText());
                }
                return idNode.asLong();
            }
        }

        throw new RuntimeException("Cannot extract location ID from node: " + locationNode);
    }
}
