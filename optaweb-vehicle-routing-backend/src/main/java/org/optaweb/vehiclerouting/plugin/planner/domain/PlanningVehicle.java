/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
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


package org.optaweb.vehiclerouting.plugin.planner.domain;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.optaweb.vehiclerouting.plugin.planner.domain.persistable.AbstractPersistable;

@XStreamAlias("VrpVehicle")
public class PlanningVehicle extends AbstractPersistable implements Standstill {

    protected int capacity;
    protected PlanningDepot depot;

    // Shadow variables
    private PlanningVisit nextVisit;

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public PlanningDepot getDepot() {
        return depot;
    }

    public void setDepot(PlanningDepot depot) {
        this.depot = depot;
    }

    public PlanningVisit getNextVisit() {
        return nextVisit;
    }

    public void setNextVisit(PlanningVisit nextVisit) {
        this.nextVisit = nextVisit;
    }


    @Override
    public PlanningVehicle getVehicle() {
        return this;
    }

    @Override
    public PlanningLocation getLocation() {
        return depot.getLocation();
    }

    @Override
    public String toString() {
        return "PlanningVehicle{" +
                "capacity=" + capacity +
                (depot == null ? "" : ",depot=" + depot.getId()) +
                (nextVisit == null ? "" : ",nextVisit=" + nextVisit.getId()) +
                ",id=" + id +
                '}';
    }
}