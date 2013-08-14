/* *********************************************************************** *
 * project: org.matsim.*
 * CurrentLegToSecureFacilityReplanner.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

package playground.christoph.evacuation.withinday.replanning.replanners;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.mobsim.framework.MobsimDriverAgent;
import org.matsim.core.mobsim.framework.PlanAgent;
import org.matsim.core.mobsim.qsim.InternalInterface;
import org.matsim.core.mobsim.qsim.agents.PlanBasedWithinDayAgent;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.router.TripRouter;
import org.matsim.core.scenario.ScenarioImpl;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplanner;
import org.matsim.withinday.utils.EditRoutes;
import org.matsim.withinday.utils.ReplacePlanElements;

import playground.christoph.evacuation.config.EvacuationConfig;

/*
 * If an Agent performs a Leg in the secure area (or at least the
 * link where the Agent is at the moment) we adapt the Route as following:
 * - If the Agents plans to stop at the current Link anyway we only
 * 	 replace the planned Activity.
 * 
 * - Otherwise: We cannot force the Agent to stop at the current Link. The QSim
 *   may already have moved him to the buffer of the QLink/QLane which means the
 *   Agent is ready to be moved over the OutNode - no further check whether
 *   an Activity should be performed at the current Link will be done.
 *   Therefore we try to find the counter Link to the current Link - that Link
 *   should be also secure. If also no counter Link is available, we check whether
 *   one of the OutLinks of the next Node is secure and use that one.
 * 
 */
public class CurrentLegToSecureFacilityReplanner extends WithinDayDuringLegReplanner {

	protected final TripRouter tripRouter;
	protected final EditRoutes editRoutes;
	
	/*package*/ CurrentLegToSecureFacilityReplanner(Id id, Scenario scenario, InternalInterface internalInterface,
			TripRouter tripRouter) {
		super(id, scenario, internalInterface);
		this.tripRouter = tripRouter;
		this.editRoutes = new EditRoutes();
	}

	@Override
	public boolean doReplanning(PlanBasedWithinDayAgent withinDayAgent) {
		
		// do Replanning only in the timestep where the Evacuation has started.
		if (this.time > EvacuationConfig.evacuationTime) return true;
		
		// If we don't have a valid WithinDayPersonAgent
		if (withinDayAgent == null) return false;

		PlanImpl executedPlan = (PlanImpl) ((PlanAgent) withinDayAgent).getSelectedPlan();

		// If we don't have an executed plan
		if (executedPlan == null) return false;

		Leg currentLeg = this.withinDayAgentUtils.getCurrentLeg(withinDayAgent);
		Activity nextActivity = executedPlan.getNextActivity(currentLeg);
		
		// If it is not a car Leg we don't replan it.
//		if (!currentLeg.getMode().equals(TransportMode.car)) return false;

		// Get the current Link
		Link currentLink = scenario.getNetwork().getLinks().get(((MobsimDriverAgent) withinDayAgent).getCurrentLinkId());
		
		Activity rescueActivity = null;
		/*
		 * If the Agents has planned an Activity at the current Link anyway
		 * we only have to replace that Activity.
		 */
		if (currentLeg.getRoute().getEndLinkId().equals(currentLink.getId())) {
			rescueActivity = nextActivityAtCurrentLink(currentLink, executedPlan, nextActivity);
		}
		
		else {
			/*
			 * Get the CounterLink. It should also be in the secure area.
			 */
			Link nextLink = getNextLink(currentLink);
			
			/*
			 * Now we add a new Activity at the rescue facility.
			 */
			rescueActivity = scenario.getPopulation().getFactory().createActivityFromLinkId("secure", nextLink.getId());
			String idString = "secureFacility" + nextLink.getId();
			((ActivityImpl)rescueActivity).setFacilityId(scenario.createId(idString));
			rescueActivity.setEndTime(Time.UNDEFINED_TIME);
			
			Coord rescueCoord = ((ScenarioImpl)scenario).getActivityFacilities().getFacilities().get(scenario.createId(idString)).getCoord();
			((ActivityImpl)rescueActivity).setCoord(rescueCoord);
			
			new ReplacePlanElements().replaceActivity(executedPlan, nextActivity, rescueActivity);
			
			int currentLinkIndex = this.withinDayAgentUtils.getCurrentRouteLinkIdIndex(withinDayAgent);

			// new Route for current Leg
			this.editRoutes.relocateCurrentLegRoute(currentLeg, executedPlan.getPerson(), currentLinkIndex, rescueActivity.getLinkId(), currentLinkIndex, scenario.getNetwork(), tripRouter);
		}
		
		// Remove all legs and activities after the next activity.
		int nextActivityIndex = executedPlan.getActLegIndex(rescueActivity);
		
		while (executedPlan.getPlanElements().size() - 1 > nextActivityIndex) {
			executedPlan.removeActivity(executedPlan.getPlanElements().size() - 1);
		}
		
		// Finally reset the cached Values of the PersonAgent - they may have changed!
		this.withinDayAgentUtils.resetCaches(withinDayAgent);
		
		return true;
	}
	
	/*
	 * We try to get a CounterLink. If none is available we choose one of
	 * the OutLinks of the next Node.
	 */
	private Link getNextLink(Link currentLink) {
		/*
		 * Get the CounterLink. It should also be in the secure area.
		 */
		for (Link link : currentLink.getToNode().getOutLinks().values()) {
			if (link.getToNode().equals(currentLink.getFromNode())) {
				return link;
			}
		}
		
		/*
		 * We found no CounterLink - therefore we use one of the OutLinks, if
		 * it is also in the secure Area.
		 */
		Coord center = EvacuationConfig.centerCoord;
		for (Link outLink : currentLink.getToNode().getOutLinks().values()) {
			double distance = CoordUtils.calcDistance(center, outLink.getToNode().getCoord());
			
			if (distance >= EvacuationConfig.innerRadius) return outLink;
		}
		
		return null;
	}
	
	private Activity nextActivityAtCurrentLink(Link currentLink, Plan selectedPlan, Activity nextActivity) {
		Activity rescueActivity = scenario.getPopulation().getFactory().createActivityFromLinkId("secure", currentLink.getId());
		String idString = "secureFacility" + currentLink.getId();
		((ActivityImpl)rescueActivity).setFacilityId(scenario.createId(idString));
		rescueActivity.setEndTime(Time.UNDEFINED_TIME);
		
		Coord rescueCoord = ((ScenarioImpl)scenario).getActivityFacilities().getFacilities().get(scenario.createId(idString)).getCoord();
		((ActivityImpl)rescueActivity).setCoord(rescueCoord);

		new ReplacePlanElements().replaceActivity(selectedPlan, nextActivity, rescueActivity);
		
		return rescueActivity;
	}
}