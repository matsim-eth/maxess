/* *********************************************************************** *
 * project: org.matsim.*
 * FhMain.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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
package playground.benjamin.emissions.types;

import java.util.HashMap;
import java.util.Map;

/**
 * @author benjamin
 *
 */
public class HbefaAvgWarmEmissionFactors {

	private double speed;
	private final Map<WarmPollutant, Double> emissionFactors = new HashMap<WarmPollutant, Double>();

	public HbefaAvgWarmEmissionFactors(){
	}

	public double getSpeed() {
		return speed;
	}
	
	public void setSpeed(double speed) {
		this.speed = speed;
	}
	
	public double getEmissionFactor(WarmPollutant warmPollutant) {
		return this.emissionFactors.get(warmPollutant);
	}
	
	public void setEmissionFactor(WarmPollutant warmPollutant, double emissionFactor) {
		this.emissionFactors.put(warmPollutant, emissionFactor);
	}
}