/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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

package playground.balac.retailers.strategies;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.controler.Controler;
import org.matsim.core.facilities.ActivityFacilityImpl;

import playground.balac.retailers.RetailerGA.RunRetailerGA;
import playground.balac.retailers.data.LinkRetailersImpl;
import playground.balac.retailers.models.MaxProfitWithLandPrices;
import playground.balac.retailers.models.MinTravelCostNoPrice;
import playground.balac.retailers.models.MinTravelCostRoadPriceModel;
import playground.balac.retailers.utils.Utils;


public class MinTravelingCostsRetailerStrategy extends RetailerStrategyImpl
{
	private final static Logger log = Logger.getLogger(MinTravelingCostsRetailerStrategy.class);

	public static final String CONFIG_GROUP = "Retailers";
  public static final String NAME = "minTravelingCostsRetailerStrategy";
  public static final String GENERATIONS = "numberOfGenerations";
  public static final String POPULATION = "PopulationSize";
  private Map<Id, ActivityFacilityImpl> retailerFacilities;
  private Map<Id, ActivityFacilityImpl> movedFacilities = new TreeMap<Id, ActivityFacilityImpl>();

  public MinTravelingCostsRetailerStrategy(Controler controler)
  {
    super(controler);
  }

  @Override
	public Map<Id, ActivityFacilityImpl> moveFacilities(Map<Id, ActivityFacilityImpl> retailerFacilities, TreeMap<Id, LinkRetailersImpl> freeLinks)
  {
    this.retailerFacilities = retailerFacilities;
    MaxProfitWithLandPrices mam = new MaxProfitWithLandPrices(this.controler, retailerFacilities);
    TreeMap<Integer, String> first = createInitialLocationsForGA(mergeLinks(freeLinks, retailerFacilities));
    log.info("first = " + first);
    mam.init(first);
    //this.shops = mam.getScenarioShops();
    Integer populationSize = Integer.valueOf(Integer.parseInt(this.controler.getConfig().findParam("Retailers", "PopulationSize")));
    if (populationSize == null) {
    	log.warn("In config file, param = PopulationSize in module = Retailers not defined, the value '1000' will be used as default for this parameter");
    	populationSize = 1000;
    }
    Integer numberGenerations = Integer.valueOf(Integer.parseInt(this.controler.getConfig().findParam("Retailers", "numberOfGenerations")));
    if (numberGenerations == null) { 
    	log.warn("In config file, param = numberOfGenerations in module = Retailers not defined, the value '1000' will be used as default for this parameter");
    	numberGenerations = 1000;
    }
    RunRetailerGA rrGA = new RunRetailerGA(populationSize, numberGenerations);
    ArrayList<Integer> solution = rrGA.runGA(mam);
    int count = 0;
    for (ActivityFacilityImpl af : this.retailerFacilities.values()) {
      log.info("The facility on the link = " + af.getLinkId() + " will be checked");
      if (!first.get(solution.get(count)).equals( af.getLinkId().toString() )) {
        Utils.moveFacility(af, this.controler.getNetwork().getLinks().get(new IdImpl((String)first.get(solution.get(count)))));
        log.info("The facility " + af.getId() + " has been moved");
        this.movedFacilities.put(af.getId(), af);
        log.info("Link Id after = " + af.getLinkId());

        log.info("Count= " + count);
      }
      ++count;
    }
    return this.movedFacilities;
  }
}