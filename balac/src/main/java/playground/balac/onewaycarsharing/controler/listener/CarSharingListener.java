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

package playground.balac.onewaycarsharing.controler.listener;

import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.PersonImpl;

import playground.balac.onewaycarsharing.IO.CarSharingSummaryWriter;
import playground.balac.onewaycarsharing.IO.PersonsSummaryWriter;
import playground.balac.onewaycarsharing.config.OneWayCSConfigGroup;
import playground.balac.onewaycarsharing.data.MyTransportMode;
import playground.balac.onewaycarsharing.router.CarSharingStation;
import playground.balac.onewaycarsharing.router.PlansCalcRouteFtInfo;


public class CarSharingListener implements StartupListener, IterationEndsListener {
	  private static final Logger log = Logger.getLogger(CarSharingListener.class);

  private Controler controler;
  private OneWayReservationHandler oneWayReservationHandler;
  //private CarSharingSummaryWriter csw = new CarSharingSummaryWriter("/data/matsim/ciarif/output/zurich_10pc/CarSharing/CarSharingSummary");
  //private PersonsSummaryWriter psw = new PersonsSummaryWriter("/data/matsim/ciarif/output/zurich_10pc/CarSharing/PersonsSummary");
  private CarSharingSummaryWriter csw;
  private PersonsSummaryWriter psw;
  private OneWayCSConfigGroup configGroup;
  private PlansCalcRouteFtInfo plansCalcRouteFtInfo;

  public CarSharingListener(OneWayCSConfigGroup configGroup, PlansCalcRouteFtInfo plansCalcRouteFtInfo)
  {
    this.configGroup = configGroup;
    this.plansCalcRouteFtInfo = plansCalcRouteFtInfo;
    this.csw = new CarSharingSummaryWriter(this.configGroup.getCsSummaryWriterFilename());
    this.psw = new PersonsSummaryWriter(this.configGroup.getPersonSummaryWriterFilename());
  }

  public void notifyIterationEnds(IterationEndsEvent event)
  {
	    log.info("Number of rerouted legs is: " + oneWayReservationHandler.getNumberOfReroutedLegs());
    this.controler = event.getControler();
    if (event.getIteration() != this.controler.getLastIteration())
      return;
    Network network = this.controler.getNetwork();
    this.plansCalcRouteFtInfo.prepare(network);
    for (Person person : this.controler.getPopulation().getPersons().values()) {
      Plan plan = person.getSelectedPlan();
      
    
      PersonImpl p = (PersonImpl)person;
      for (PlanElement pe : plan.getPlanElements()) {
        if (pe instanceof LegImpl) {
          LegImpl leg = (LegImpl)pe;
          ActivityImpl actBefore = (ActivityImpl)plan.getPlanElements().get(plan.getPlanElements().indexOf(leg) - 1);
          ActivityImpl actAfter = (ActivityImpl)plan.getPlanElements().get(plan.getPlanElements().indexOf(leg) + 1);
          if (leg.getMode().equals(MyTransportMode.onewaycarsharing)) {
            LinkImpl startLink = (LinkImpl)network.getLinks().get(leg.getRoute().getStartLinkId());
            CarSharingStation fromStation = this.plansCalcRouteFtInfo.getCarStations().getClosestLocation(startLink.getCoord());
            LinkImpl endLink = (LinkImpl)network.getLinks().get(leg.getRoute().getEndLinkId());
            CarSharingStation toStation = this.plansCalcRouteFtInfo.getCarStations().getClosestLocation(endLink.getCoord());
            this.csw.write(p, startLink, fromStation, toStation, endLink, leg.getDepartureTime(), leg.getArrivalTime(), actBefore, actAfter);
          }
        }
      }

      this.psw.write(p);
    }
    this.psw.close();
    this.csw.close();
  }

@Override
public void notifyStartup(StartupEvent event) {
	// create an instance of the NoCarHandler
	
	oneWayReservationHandler = new OneWayReservationHandler(plansCalcRouteFtInfo, event.getControler());

    // register the TripCounter at the EventsManager
    event.getControler().getEvents().addHandler(oneWayReservationHandler);
	
}
}