/* *********************************************************************** *
 * project: org.matsim.*
 * newIdPlan.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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
package playground.yu.newPlans;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.scenario.ScenarioLoaderImpl;

/**
 * @author yu
 * 
 */
public class NewIdPlan extends NewPopulation {

	public NewIdPlan(final Population plans) {
		super(plans);
	}

	@Override
	public void run(final Person person) {
		if (Integer.parseInt(person.getId().toString()) <= 100)
			this.pw.writePerson(person);
	}

	public static void main(final String[] args) {
		Scenario scenario = new ScenarioLoaderImpl(args[0]).loadScenario();

		NewIdPlan nip = new NewIdPlan(scenario.getPopulation());
		nip.run(scenario.getPopulation());
		nip.writeEndPlans();
	}
}
