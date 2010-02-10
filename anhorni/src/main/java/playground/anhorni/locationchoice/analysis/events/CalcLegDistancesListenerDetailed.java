/* *********************************************************************** *
 * project: org.matsim.*
 * CalcLegTimesKTIListener.java
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

package playground.anhorni.locationchoice.analysis.events;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.TreeMap;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.utils.io.IOUtils;

public class CalcLegDistancesListenerDetailed implements StartupListener, AfterMobsimListener, ShutdownListener {

	final private String filename;
	private BufferedWriter out;
	private CalcLegDistances calcLegDistances;
	private String actTypes [] = {"shop", "shop_grocery", "shop_nongrocery", "leisure", "work", "education", "home"};	
	
	public CalcLegDistancesListenerDetailed(String filename) {
		super();
		this.filename = filename;
	}
	public void notifyStartup(StartupEvent event) {

		try {
			this.out = IOUtils.getBufferedWriter(event.getControler().getControlerIO().getOutputFilename(this.filename));
			this.out.write("#iteration");
			
			for (int i = 0; i < actTypes.length; i++) {
				for (TransportMode mode : TransportMode.values()) {
					this.out.write("\t" + mode.toString() + "_" + actTypes[i]);
				}
			}
			this.out.write(System.getProperty("line.separator"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}		
		this.calcLegDistances = new CalcLegDistances(event.getControler().getPopulation());
		event.getControler().getEvents().addHandler(this.calcLegDistances);

	}
	
	public void notifyAfterMobsim(AfterMobsimEvent event) {
		
		TreeMap<String, Double> avgTripDistances = this.calcLegDistances.getAverageTripDistancesByModeAndActType();
		String str;
		
		try {
			this.out.write(Integer.toString(event.getIteration()));			
			for (int i = 0; i < actTypes.length; i++) {
				for (TransportMode mode : TransportMode.values()) {
					
					String key = mode.toString() + "_" + actTypes[i];
					
					if (avgTripDistances.containsKey(key)) {
						str = avgTripDistances.get(key).toString();
					} else {
						str = "---";
					}
					this.out.write("\t" + str);
				}
			}
			this.out.write(System.getProperty("line.separator"));
			this.out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void notifyShutdown(ShutdownEvent event) {
		try {
			this.out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
