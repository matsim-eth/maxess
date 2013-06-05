package playground.pieter.pseudosim.trafficinfo;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.AgentDepartureEvent;
import org.matsim.core.api.experimental.events.AgentStuckEvent;
import org.matsim.core.api.experimental.events.PersonEntersVehicleEvent;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import playground.pieter.pseudosim.controler.listeners.MobSimSwitcher;
import playground.sergioo.singapore2012.transitRouterVariable.WaitTimeStuckCalculator;

public class PseudoSimWaitTimeCalculator2 extends WaitTimeStuckCalculator {

	public PseudoSimWaitTimeCalculator2(Population population,
			TransitSchedule transitSchedule, int timeSlot, int totalTime) {
		super(population, transitSchedule, timeSlot, totalTime);

	}

	@Override
	public void reset(int iteration) {
		if (MobSimSwitcher.isMobSimIteration) {
			Logger.getLogger(this.getClass()).error(
					"Calling reset on traveltimecalc");
			super.reset(iteration);
		}
	}

	@Override
	public void handleEvent(AgentDepartureEvent event) {
		if (MobSimSwitcher.isMobSimIteration)
			super.handleEvent(event);
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (MobSimSwitcher.isMobSimIteration)
			super.handleEvent(event);
	}

	@Override
	public void handleEvent(AgentStuckEvent event) {
		if (MobSimSwitcher.isMobSimIteration)
			super.handleEvent(event);
	}

}
