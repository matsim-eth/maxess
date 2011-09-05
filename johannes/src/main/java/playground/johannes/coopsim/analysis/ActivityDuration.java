/* *********************************************************************** *
 * project: org.matsim.*
 * ActivityDuration.java
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
package playground.johannes.coopsim.analysis;

import gnu.trove.TObjectDoubleHashMap;

import java.util.Set;

import org.matsim.api.core.v01.population.Activity;

import playground.johannes.coopsim.pysical.Trajectory;

/**
 * @author illenberger
 *
 */
public class ActivityDuration extends AbstractTrajectoryProperty {
	
	private final String purpose;
	
	public ActivityDuration(String purpose) {
		this.purpose = purpose;
	}
	
	@Override
	public TObjectDoubleHashMap<Trajectory> values(Set<? extends Trajectory> trajectories) {
		TObjectDoubleHashMap<Trajectory> values = new TObjectDoubleHashMap<Trajectory>(trajectories.size());
		
		for(Trajectory trajectory : trajectories) {
			for(int i = 0; i < trajectory.getElements().size(); i += 2) {
				Activity act = (Activity)trajectory.getElements().get(i);
				
				if(purpose == null || act.getType().equals(purpose)) {
					double dur = trajectory.getTransitions().get(i + 1) - trajectory.getTransitions().get(i);
					
					values.put(trajectory, dur);
				}
			}
		}
		
		return values;
	}
}
