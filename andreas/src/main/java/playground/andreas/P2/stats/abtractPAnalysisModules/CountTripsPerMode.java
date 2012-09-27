/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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

package playground.andreas.P2.stats.abtractPAnalysisModules;

import java.util.HashMap;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.api.experimental.events.PersonEntersVehicleEvent;
import org.matsim.core.api.experimental.events.TransitDriverStartsEvent;
import org.matsim.core.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.core.events.handler.TransitDriverStartsEventHandler;


/**
 * Count the number of trips per ptModes specified.
 * 
 * @author aneumann
 *
 */
public class CountTripsPerMode extends AbstractPAnalyisModule implements TransitDriverStartsEventHandler, PersonEntersVehicleEventHandler{
	
	private final static Logger log = Logger.getLogger(CountTripsPerMode.class);
	
	private HashMap<Id, String> vehId2ptModeMap;
	private HashMap<String, Integer> ptMode2CountMap;
	
	public CountTripsPerMode(){
		super(CountTripsPerMode.class.getSimpleName());
		log.info("enabled");
	}

	@Override
	public String getResult() {
		StringBuffer strB = new StringBuffer();
		for (String ptMode : this.ptModes) {
			strB.append(", " + this.ptMode2CountMap.get(ptMode));
		}
		return strB.toString();
	}
	
	@Override
	public void reset(int iteration) {
		super.reset(iteration);
		this.vehId2ptModeMap = new HashMap<Id, String>();
		this.ptMode2CountMap = new HashMap<String, Integer>();
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if(!super.ptDriverIds.contains(event.getPersonId())){
			String ptMode = this.vehId2ptModeMap.get(event.getVehicleId());
			if (ptMode == null) {
				ptMode = "nonPtMode";
			}
			if (ptMode2CountMap.get(ptMode) == null) {
				ptMode2CountMap.put(ptMode, new Integer(0));
			}

			ptMode2CountMap.put(ptMode, new Integer(ptMode2CountMap.get(ptMode) + 1));
		}
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		super.handleEvent(event);
		String ptMode = this.lineIds2ptModeMap.get(event.getTransitLineId());
		if (ptMode == null) {
			log.warn("Should not happen");
			ptMode = "no valid pt mode found";
		}
		this.vehId2ptModeMap.put(event.getVehicleId(), ptMode);
	}
}
