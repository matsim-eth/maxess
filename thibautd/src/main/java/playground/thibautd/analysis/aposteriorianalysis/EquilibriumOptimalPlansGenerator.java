/* *********************************************************************** *
 * project: org.matsim.*
 * EquilibriumOptimalPlansGenerator.java
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
package playground.thibautd.analysis.aposteriorianalysis;

import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.population.PopulationWriter;

import playground.thibautd.jointtripsoptimizer.population.Clique;
import playground.thibautd.jointtripsoptimizer.population.ScenarioWithCliques;
import playground.thibautd.jointtripsoptimizer.replanning.modules.JointPlanOptimizerModule;
import playground.thibautd.jointtripsoptimizer.replanning.selectors.PlanWithLongestTypeSelector;
import playground.thibautd.jointtripsoptimizer.run.config.JointReplanningConfigGroup;
import playground.thibautd.jointtripsoptimizer.run.JointControler;
import playground.thibautd.utils.RemoveJointTrips;

/**
 * Class which optimises the plan of all cliques of more than 2 members
 * against the traffic state at the equilibrium.
 *
 * This allows to generate plans files with the "potential optimal plans" for
 * each clique, with different parameters (with and without joint trips, for example).
 *
 * This way, different kind of plans can be generated and compared, without the conceptual
 * difficulty arising from the non-uniqueness of the equilibrium state.
 *
 * @author thibautd
 */
public class EquilibriumOptimalPlansGenerator {
	private final JointControler controler;
	private final JointReplanningConfigGroup configGroup;

	/**
	 * @param controler a controler, after it has been run. Parameters for the
	 * joint replanning module must correspond to the desired ones.
	 */
	public EquilibriumOptimalPlansGenerator(
			final JointControler controler) {
		this.controler = controler;
		this.configGroup = (JointReplanningConfigGroup)
			controler.getConfig().getModule( JointReplanningConfigGroup.GROUP_NAME );
	}

	public void writePopulations(final String directory) {
		writeUntoggledOptimalJointTrips( directory+"/plans-with-all-joint-trips.xml.gz" );
		writeToggledOptimalJointTrips( directory+"/plans-with-best-joint-trips.xml.gz" );
		writeIndividualTrips( directory+"/plans-with-no-joint-trips.xml.gz" );
	}

	private void writeUntoggledOptimalJointTrips(final String file) {
		PlanWithLongestTypeSelector selector = new PlanWithLongestTypeSelector();
		ScenarioWithCliques scenario = (ScenarioWithCliques) controler.getScenario();

		for (Clique clique : scenario.getCliques().getCliques().values()) {
			Plan plan = selector.selectPlan( clique );
			clique.setSelectedPlan( plan );

			for (Plan currentPlan : clique.getPlans()) {
				if (currentPlan != plan) {
					clique.removePlan( currentPlan );
				}
			}
		}

		configGroup.setOptimizeToggle( "false" );
		optimiseSelectedPlans();
		writePopulation( file );
	}

	private void writeToggledOptimalJointTrips(final String file) {
		PlanWithLongestTypeSelector selector = new PlanWithLongestTypeSelector();
		ScenarioWithCliques scenario = (ScenarioWithCliques) controler.getScenario();

		for (Clique clique : scenario.getCliques().getCliques().values()) {
			Plan plan = selector.selectPlan( clique );
			clique.setSelectedPlan( plan );
		}

		configGroup.setOptimizeToggle( "true" );
		optimiseSelectedPlans();
		writePopulation( file );
	}

	private void writeIndividualTrips(final String file) {
		ScenarioWithCliques scenario = (ScenarioWithCliques) controler.getScenario();

		RemoveJointTrips.removeJointTrips( scenario.getPopulation() );

		optimiseSelectedPlans();
		writePopulation( file );

	}

	private void optimiseSelectedPlans() {
		JointPlanOptimizerModule module = new JointPlanOptimizerModule( controler );
		ScenarioWithCliques scenario = (ScenarioWithCliques) controler.getScenario();

		module.prepareReplanning();
		for (Clique clique : scenario.getCliques().getCliques().values()) {
			module.handlePlan( clique.getSelectedPlan() );
		}
		module.finishReplanning();
	}

	private void writePopulation(final String file) {
		(new PopulationWriter(
				controler.getScenario().getPopulation(),
				controler.getScenario().getNetwork()) ).write( file );
	}
}

