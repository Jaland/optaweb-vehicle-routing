/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaweb.vehiclerouting.plugin.planner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.optaplanner.examples.vehiclerouting.domain.Customer;
import org.optaplanner.examples.vehiclerouting.domain.Depot;
import org.optaplanner.examples.vehiclerouting.domain.Vehicle;
import org.optaplanner.examples.vehiclerouting.domain.VehicleRoutingSolution;
import org.optaplanner.examples.vehiclerouting.domain.location.RoadLocation;
import org.optaweb.vehiclerouting.service.route.RouteChangedEvent;
import org.optaweb.vehiclerouting.service.route.ShallowRoute;
import org.springframework.context.ApplicationEventPublisher;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.optaweb.vehiclerouting.plugin.planner.CustomerFactory.customer;
import static org.optaweb.vehiclerouting.plugin.planner.DepotFactory.depot;
import static org.optaweb.vehiclerouting.plugin.planner.SolutionFactory.solutionFromCustomers;
import static org.optaweb.vehiclerouting.plugin.planner.SolutionFactory.solutionFromLocations;
import static org.optaweb.vehiclerouting.plugin.planner.VehicleFactory.vehicle;

@ExtendWith(MockitoExtension.class)
class SolutionPublisherTest {

    @Mock
    private ApplicationEventPublisher publisher;
    @InjectMocks
    private SolutionPublisher solutionPublisher;

    @Test
    void should_covert_solution_to_event_and_publish_it() {
        solutionPublisher.publishSolution(SolutionFactory.emptySolution());
        Mockito.verify(publisher).publishEvent(Mockito.any(RouteChangedEvent.class));
    }

    @Test
    void empty_solution_should_have_zero_routes_vehicles_etc() {
        VehicleRoutingSolution solution = SolutionFactory.emptySolution();

        RouteChangedEvent event = SolutionPublisher.solutionToEvent(solution, this);

        assertThat(event.vehicleIds()).isEmpty();
        assertThat(event.depotId()).isEmpty();
        assertThat(event.visitIds()).isEmpty();
        assertThat(event.routes()).isEmpty();
        assertThat(event.distance()).isEqualTo("0h 0m 0s");
    }

    @Test
    void solution_with_vehicles_and_no_depot_should_have_zero_routes() {
        long vehicleId = 1;
        Vehicle vehicle = vehicle(vehicleId);
        VehicleRoutingSolution solution = solutionFromCustomers(singletonList(vehicle), null, emptyList());

        RouteChangedEvent event = SolutionPublisher.solutionToEvent(solution, this);

        assertThat(event.vehicleIds()).containsExactly(vehicleId);
        assertThat(event.depotId()).isEmpty();
        assertThat(event.visitIds()).isEmpty();
        assertThat(event.routes()).isEmpty();
        assertThat(event.distance()).isEqualTo("0h 0m 0s");
    }

    @Test
    void nonempty_solution_without_vehicles_should_have_zero_routes_but_contain_visits() {
        long depotId = 1;
        long visitId = 2;
        VehicleRoutingSolution solution = solutionFromLocations(
                emptyList(),
                depot(new RoadLocation(depotId, 1.0, 1.0)),
                singletonList(new RoadLocation(visitId, 2.0, 2.0))
        );

        RouteChangedEvent event = SolutionPublisher.solutionToEvent(solution, this);

        assertThat(event.vehicleIds()).isEmpty();
        assertThat(event.depotId()).contains(depotId);
        assertThat(event.visitIds()).containsExactly(visitId);
        assertThat(event.routes()).isEmpty();
        assertThat(event.distance()).isEqualTo("0h 0m 0s");
    }

    @Test
    void initialized_solution_should_have_one_route_per_vehicle() {
        // arrange
        long vehicleId1 = 1001;
        long vehicleId2 = 2001;
        Vehicle vehicle1 = vehicle(vehicleId1);
        Vehicle vehicle2 = vehicle(vehicleId2);

        long depotId = 1;
        long visitId1 = 2;
        long visitId2 = 3;
        Depot depot = depot(new RoadLocation(depotId, 1.0, 1.0));
        Customer customer1 = customer(new RoadLocation(visitId1, 2.0, 2.0));
        Customer customer2 = customer(new RoadLocation(visitId2, 0.2, 0.2));

        VehicleRoutingSolution solution = solutionFromCustomers(
                asList(vehicle1, vehicle2),
                depot,
                asList(customer1, customer2)
        );

        /* Send both vehicles to both visits
         * V1
         *   \
         *    |---> customer1 ---> customer2
         *   /
         * V2
         */
        for (Vehicle vehicle : solution.getVehicleList()) {
            vehicle.setNextCustomer(customer1);
            customer1.setPreviousStandstill(vehicle);
        }
        customer1.setNextCustomer(customer2);
        customer2.setPreviousStandstill(customer1);

        // act
        RouteChangedEvent event = SolutionPublisher.solutionToEvent(solution, this);

        // assert
        assertThat(event.routes()).hasSameSizeAs(solution.getVehicleList());
        assertThat(event.routes().stream().mapToLong(value -> value.vehicleId))
                .containsExactlyInAnyOrder(vehicleId1, vehicleId2);

        for (ShallowRoute route : event.routes()) {
            assertThat(route.depotId).isEqualTo(depot.getId());
            // visits shouldn't include the depot
            assertThat(route.visitIds).containsExactly(visitId1, visitId2);
        }

        assertThat(event.vehicleIds()).containsExactlyInAnyOrder(vehicleId1, vehicleId2);
        assertThat(event.depotId()).contains(depotId);
        assertThat(event.visitIds()).containsExactlyInAnyOrder(visitId1, visitId2);
        assertThat(event.distance()).isEqualTo("0h 0m 0s");
    }

    @Test
    void fail_fast_if_vehicles_next_customer_doesnt_exist() {
        Vehicle vehicle = vehicle(1);
        vehicle.setNextCustomer(customer(new RoadLocation(2, 2.0, 2.0)));

        VehicleRoutingSolution solution = solutionFromLocations(
                singletonList(vehicle),
                depot(new RoadLocation(1, 1.0, 1.0)),
                singletonList(new RoadLocation(3, 3.0, 3.0))
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> SolutionPublisher.solutionToEvent(solution, this))
                .withMessageContaining("Customer");
    }

    @Test
    void vehicle_without_a_depot_is_illegal_if_depot_exists() {
        Depot depot = depot(new RoadLocation(1, 1.0, 1.0));
        Vehicle vehicle = vehicle(1);
        VehicleRoutingSolution solution = solutionFromCustomers(singletonList(vehicle), depot, emptyList());
        vehicle.setDepot(null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SolutionPublisher.solutionToEvent(solution, this))
                .withMessageContaining("Vehicle");
    }
}
