/* *********************************************************************** *
 * project: org.matsim.*
 * MoneyEventHandler.java
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

/**
 * 
 */
package playground.ikaddoura.optimization.handler;

import java.util.ArrayList;
import java.util.List;

import org.matsim.core.api.experimental.events.AgentMoneyEvent;
import org.matsim.core.api.experimental.events.handler.AgentMoneyEventHandler;

import playground.ikaddoura.optimization.analysis.FareData;

/**
 * @author Ihab
 *
 */
public class MoneyEventHandler implements AgentMoneyEventHandler {

	private double revenues;
	private List<FareData> fareDataList = new ArrayList<FareData>();
	
	@Override
	public void reset(int iteration) {
		this.revenues = 0;
		this.fareDataList.clear();
	}

	@Override
	public void handleEvent(AgentMoneyEvent event) {
		this.revenues = this.revenues + (-1 * event.getAmount());
		FareData fareData = new FareData();
		fareData.setAmount(-1 * event.getAmount());
		fareData.setTime(event.getTime());
		this.fareDataList.add(fareData);
	}

	/**
	 * @return the revenues
	 */
	public double getRevenues() {
		return revenues;
	}

	public List<FareData> getfareDataList() {
		return fareDataList;
	}
	
}
