package playground.artemc.transitRouterEventsBased;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.population.routes.GenericRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scoring.ScoringFunctionAccumulator.LegScoring;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class SingaporeFareScoring implements LegScoring {

	public static double DISTANCE_FARE_RATE = -7.0/100000;
	protected double score;
	private TransitSchedule transitSchedule;
	private Network network;
	private Plan plan;
	private int numPtLegsScored = 0;
	
	public SingaporeFareScoring(Plan plan, TransitSchedule transitSchedule, Network network) {
		this.plan = plan;
		this.transitSchedule = transitSchedule;
		this.network = network;
	}

	@Override
	public void finish() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public double getScore() {
		return score;
	}

	@Override
	public void reset() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startLeg(double time, Leg leg) {
		if(leg.getMode().equals(TransportMode.pt))
			score += getDistance()*DISTANCE_FARE_RATE;
	}
	
	protected double getDistance() {
		int num = numPtLegsScored++;
		for(PlanElement planElement:plan.getPlanElements())
			if(planElement instanceof Leg && ((Leg)planElement).getMode().equals(TransportMode.pt))
				if(num==0) {
					String[] parts = ((GenericRoute)((Leg)planElement).getRoute()).getRouteDescription().split("===");
					Id<Link> fromLinkId = transitSchedule.getFacilities().get(Id.create(parts[1], TransitStopFacility.class)).getLinkId();
					Id<Link> toLinkId = transitSchedule.getFacilities().get(Id.create(parts[4], TransitStopFacility.class)).getLinkId();
					return RouteUtils.calcDistance(transitSchedule.getTransitLines().get(Id.create(parts[2], TransitLine.class)).getRoutes().get(Id.create(parts[3], TransitRoute.class)).getRoute().getSubRoute(fromLinkId, toLinkId), network);
				}
				else
					num--;
		return 0;
	}

	@Override
	public void endLeg(double time) {
		
	}

}
