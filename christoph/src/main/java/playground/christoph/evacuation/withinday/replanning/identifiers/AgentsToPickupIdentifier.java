/* *********************************************************************** *
 * project: org.matsim.*
 * AgentsToPickupIdentifier.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.christoph.evacuation.withinday.replanning.identifiers;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.AgentArrivalEvent;
import org.matsim.core.api.experimental.events.AgentDepartureEvent;
import org.matsim.core.api.experimental.events.AgentStuckEvent;
import org.matsim.core.api.experimental.events.LinkEnterEvent;
import org.matsim.core.api.experimental.events.LinkLeaveEvent;
import org.matsim.core.api.experimental.events.handler.AgentArrivalEventHandler;
import org.matsim.core.api.experimental.events.handler.AgentDepartureEventHandler;
import org.matsim.core.api.experimental.events.handler.AgentStuckEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkEnterEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkLeaveEventHandler;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.events.SimulationInitializedEvent;
import org.matsim.core.mobsim.framework.listeners.SimulationInitializedListener;
import org.matsim.core.router.util.PersonalizableTravelTime;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.ptproject.qsim.QSim;
import org.matsim.ptproject.qsim.agents.ExperimentalBasicWithindayAgent;
import org.matsim.ptproject.qsim.agents.PersonDriverAgentImpl;
import org.matsim.ptproject.qsim.agents.PlanBasedWithinDayAgent;
import org.matsim.ptproject.qsim.comparators.PersonAgentComparator;
import org.matsim.vehicles.Vehicles;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringLegIdentifier;

import playground.christoph.evacuation.analysis.CoordAnalyzer;
import playground.christoph.evacuation.mobsim.VehiclesTracker;
import playground.christoph.evacuation.withinday.replanning.replanners.JoinedHouseholdsReplanner;

/**
 * Identifies agents that perform a walk leg in the insecure area. They might be
 * picked up by a vehicle coming by.
 * 
 * Use a PriorityQueue that contains all AgentLeaveLink times. Whenever an agent
 * is going to leave a link it is checked whether there is a vehicle on the same
 * link available that has the same destination and available capacity.
 * 
 * @author cdobler
 */
public class AgentsToPickupIdentifier extends DuringLegIdentifier implements LinkEnterEventHandler, LinkLeaveEventHandler,
		AgentDepartureEventHandler, AgentArrivalEventHandler, AgentStuckEventHandler, SimulationInitializedListener {

	private final Scenario scenario;
	private final CoordAnalyzer coordAnalyzer;
	private final VehiclesTracker vehiclesTracker;
	private final PersonalizableTravelTime walkTravelTime;

	private final Map<Id, MobsimAgent> agents;
	private final Map<Id, Double> earliestLinkLeaveTime;
	private final Set<Id> carLegPerformingAgents;
	private final Set<Id> walkLegPerformingAgents;
	private final Set<Id> insecureWalkLegPerformingAgents;
	
	/*
	 * Queue that contains information on when agents are going to leave one
	 * link and enter another one.
	 */
	private final Queue<Tuple<Double, MobsimAgent>> agentsLeaveLinkQueue = new PriorityQueue<Tuple<Double, MobsimAgent>>(30, new TravelTimeComparator());

	/* package */AgentsToPickupIdentifier(Scenario scenario, CoordAnalyzer coordAnalyzer, VehiclesTracker vehiclesTracker, PersonalizableTravelTime walkTravelTime) {
		this.scenario = scenario;
		this.coordAnalyzer = coordAnalyzer;
		this.vehiclesTracker = vehiclesTracker;
		this.walkTravelTime = walkTravelTime;

		this.agents = new HashMap<Id, MobsimAgent>();
		this.earliestLinkLeaveTime = new HashMap<Id, Double>();
		this.carLegPerformingAgents= new HashSet<Id>();
		this.walkLegPerformingAgents = new HashSet<Id>();
		this.insecureWalkLegPerformingAgents = new HashSet<Id>();
	}

	public Set<PlanBasedWithinDayAgent> getAgentsToReplan(double time) {
		Set<PlanBasedWithinDayAgent> insecureLegPerformingAgents = new TreeSet<PlanBasedWithinDayAgent>(new PersonAgentComparator());

		Tuple<Double, MobsimAgent> tuple = null;
		while ((tuple = agentsLeaveLinkQueue.peek()) != null) {
			if (tuple.getFirst() > time) {
				break;
			} else if (tuple.getFirst() < time) {
				agentsLeaveLinkQueue.poll();
			} else {
				agentsLeaveLinkQueue.poll();
				insecureLegPerformingAgents.add((PlanBasedWithinDayAgent) tuple.getSecond());
			}
		}

		Vehicles vehicles = ((ScenarioImpl) scenario).getVehicles();		
		Set<PlanBasedWithinDayAgent> agentsToReplan = new TreeSet<PlanBasedWithinDayAgent>(new PersonAgentComparator());
		Map<Id, AtomicInteger> reservedCapacities = new HashMap<Id, AtomicInteger>();
		
		for (MobsimAgent personAgent : insecureLegPerformingAgents) {
			
			/*
			 * The agent wants to be picked up if its leg mode is walk and if
			 * its activity is not from type rescue.
			 */
			if (personAgent.getMode().equals(TransportMode.walk)) {

				// check whether next activity types match
				Activity acivity = (Activity) ((PlanBasedWithinDayAgent) personAgent).getNextPlanElement();
				if (!acivity.getType().equals(JoinedHouseholdsReplanner.activityType)) continue;

				/*
				 * Check whether there are vehicle available on the link.
				 * If vehicles are found, check whether one of them has free capacity.
				 */
				List<Id> vehicleIds = vehiclesTracker.getEnrouteVehiclesOnLink(personAgent.getCurrentLinkId());
				for (Id vehicleId : vehicleIds) {
					Id driverId = this.vehiclesTracker.getVehicleDriverId(vehicleId);
					
					/*
					 * If the vehicle could leave the link before the agent has entered it skip 
					 * the vehicle and try the next one.
					 * Add two seconds because the walk agent has to stop and perform the pickup
					 * activity which takes some time.
					 */
					double leaveLinkTime = this.earliestLinkLeaveTime.get(driverId);
					if (time + 2 > leaveLinkTime) continue;
					
					int capacity = this.vehiclesTracker.getFreeVehicleCapacity(vehicleId);
					
					/*
					 * If already other agents have reserved a place in that vehicle, reduce
					 * the vehicle's available capacity.
					 */
					AtomicInteger reservedCapacity = reservedCapacities.get(vehicleId);			
					if (reservedCapacity != null) capacity -= reservedCapacity.get();
					
					/*
					 * Check whether the available capacity equals the total
					 * capacity. If true, the vehicle is parked and therefore not
					 * available.
					 */
					int seats = vehicles.getVehicles().get(vehicleId).getType().getCapacity().getSeats();
					if (seats == capacity) continue;
					
					/*
					 * Check whether the vehicle has the same destination as
					 * the agent has.
					 */
					Id vehicleDestinationLinkId = this.vehiclesTracker.getVehicleDestination(vehicleId);
					Id agentDestinationLinkId = ((ExperimentalBasicWithindayAgent) personAgent).getCurrentLeg().getRoute().getEndLinkId();
					if (!vehicleDestinationLinkId.equals(agentDestinationLinkId)) continue;
					
					/*
					 * mark the agent as to be replanned and add an entry in the map which 
					 * connects the agent and the vehicle that will pick him up.
					 */
					agentsToReplan.add((PlanBasedWithinDayAgent) personAgent);
					
					// inform vehiclesTracker
					this.vehiclesTracker.addPlannedPickupVehicle(personAgent.getId(), vehicleId);
					break;
				}
			}
		}
		return agentsToReplan;
	}

	@Override
	public void reset(int iteration) {
		this.agentsLeaveLinkQueue.clear();
		this.carLegPerformingAgents.clear();
		this.walkLegPerformingAgents.clear();
		this.insecureWalkLegPerformingAgents.clear();
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		if (this.walkLegPerformingAgents.contains(event.getPersonId())) {
			Link link = this.scenario.getNetwork().getLinks().get(event.getLinkId());
			boolean affected = this.coordAnalyzer.isLinkAffected(link);
			if (affected) {
				this.insecureWalkLegPerformingAgents.add(event.getPersonId());

				/*
				 * Check whether the agent ends its leg on the current link. If
				 * yes, skip the agent.
				 */
				MobsimAgent agent = this.agents.get(event.getPersonId());
				Leg leg = ((ExperimentalBasicWithindayAgent) agent).getCurrentLeg();
				Id destinationLinkId = leg.getRoute().getEndLinkId();
				if (destinationLinkId.equals(event.getLinkId())) return;

				/*
				 * Otherwise add the agent to the agentsLeaveLinkQueue.
				 */
				Person person = ((PersonDriverAgentImpl) agent).getPerson();
				this.walkTravelTime.setPerson(person);
				double travelTime = walkTravelTime.getLinkTravelTime(link, event.getTime());
				double departureTime = event.getTime() + travelTime;
				departureTime = Math.round(departureTime);
				this.agentsLeaveLinkQueue.add(new Tuple<Double, MobsimAgent>(departureTime, agent));
			}
		} else if (this.carLegPerformingAgents.contains(event.getPersonId())) {
			Link link = this.scenario.getNetwork().getLinks().get(event.getLinkId());
			double time = link.getLength() / link.getFreespeed(event.getTime());
			this.earliestLinkLeaveTime.put(event.getPersonId(), time);
		}
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {
		this.insecureWalkLegPerformingAgents.remove(event.getPersonId());
	}

	@Override
	public void handleEvent(AgentStuckEvent event) {
		this.earliestLinkLeaveTime.remove(event.getPersonId());
		this.carLegPerformingAgents.remove(event.getPersonId());
		this.walkLegPerformingAgents.remove(event.getPersonId());
		this.insecureWalkLegPerformingAgents.remove(event.getPersonId());
	}

	@Override
	public void handleEvent(AgentArrivalEvent event) {
		if (event.getLegMode().equals(TransportMode.walk)) {
			this.walkLegPerformingAgents.remove(event.getPersonId());
			this.insecureWalkLegPerformingAgents.remove(event.getPersonId());
		} else if (event.getLegMode().equals(TransportMode.car)) {
			this.carLegPerformingAgents.remove(event.getPersonId());
			this.earliestLinkLeaveTime.remove(event.getPersonId());
		}
	}

	@Override
	public void handleEvent(AgentDepartureEvent event) {
		if (event.getLegMode().equals(TransportMode.walk)) {
			this.walkLegPerformingAgents.add(event.getPersonId());

			Link link = this.scenario.getNetwork().getLinks().get(event.getLinkId());
			boolean affected = this.coordAnalyzer.isLinkAffected(link);
			if (affected) this.insecureWalkLegPerformingAgents.add(event.getPersonId());
		} else if (event.getLegMode().equals(TransportMode.car)) {
			this.carLegPerformingAgents.add(event.getPersonId());

			// the agent might leave the current link immediately
			this.earliestLinkLeaveTime.put(event.getPersonId(), event.getTime());
		}
	}

	@Override
	public void notifySimulationInitialized(SimulationInitializedEvent e) {
		QSim sim = (QSim) e.getQueueSimulation();

		agents.clear();
		for (MobsimAgent agent : (sim).getAgents()) {
			agents.put(agent.getId(), agent);
		}
	}

	private static class TravelTimeComparator implements Comparator<Tuple<Double, MobsimAgent>>, Serializable {
		private static final long serialVersionUID = 1L;

		@Override
		public int compare(final Tuple<Double, MobsimAgent> o1, final Tuple<Double, MobsimAgent> o2) {
			// first compare time information
			int ret = o1.getFirst().compareTo(o2.getFirst());
			if (ret == 0) {
				// if they're equal, compare the Ids: the one with the larger Id should be first
				ret = o2.getSecond().getId().compareTo(o1.getSecond().getId()); 
			}
			return ret;
		}
	}
}
