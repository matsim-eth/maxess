/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,     *
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

package playground.jjoubert.projects.gautengPopulation;

import java.io.File;

import playground.southafrica.utilities.Header;

/**
 * This class perform a number of sequential tasks to generate a population for 
 * Gauteng.
 * <ol>
 * 		<li> The former car, bus, taxi and external subpopulations that were 
 * 			 used in the SANRAL 2010 project are converted: the coordinate
 * 			 reference system is updated to SA_Albers; the person Id is given
 * 			 a prefix to indicate the subpopulation, and a subpopulation
 * 			 attribute is added to the PersonAttributes file. <b><i>Note: a
 * 		 	 commercial vehicle subpopulation must already exist!</i></b>
 * 		<li> The various subpopulations are joined into a single population
 * 			 without making any changes. Both the population and person 
 * 			 attribute files are aggregated.
 * 		<li> Toll attributes are assigned. Specifically, the presence of an eTag
 * 			 is considered, and a vehicle toll category (A2, B or C) is assigned 
 * 			 according to the subpopulation.
 * </ol>
 * Finally, the class reports some summary statistics on the final population, 
 * and deletes all the temporary files that were created as a result of running
 * this class.  
 * 
 * @author jwjoubert
 */
public class PuttingGautengPopulationTogether {

	/**
	 * Make sure that there are two files for the commercial vehicles in the 
	 * output folder specified in the arguments:<br><br>
	 * <code>com.xml.gz</code> containing the desired population of commercial
	 * 		vehicles; and <br>
	 * <code>comAttr.xml.gz</code> containing the attributes of the commercial
	 * 		vehicle population.<br><br>
	 * 
	 * This means you have to first run the {@link AddGautengIntraAttribute} 
	 * class and move the output to the relevant folder.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		Header.printHeader(PuttingGautengPopulationTogether.class.toString(), args);
		
		String outputFolder = args[0];

		/* First convert all the old Sanral population components. */
		convertOldSanralSubpopulations(outputFolder);
		
		/* Now combine the subpopulations. */
		combineSubpopulations(outputFolder);
		
		/* Clean up the temporary files. */
		cleanUpTemporaryFiles(outputFolder);
		
		Header.printFooter();
	}

	/**
	 * @param outputFolder
	 */
	private static void cleanUpTemporaryFiles(String outputFolder) {
		new File(outputFolder + "carAttr.xml.gz").delete();
		new File(outputFolder + "busAttr.xml.gz").delete();
		new File(outputFolder + "taxiAttr.xml.gz").delete();
		new File(outputFolder + "extAttr.xml.gz").delete();
		new File(outputFolder + "tmp1.xml.gz").delete();
		new File(outputFolder + "tmp1Attr.xml.gz").delete();
		new File(outputFolder + "tmp2.xml.gz").delete();
		new File(outputFolder + "tmp2Attr.xml.gz").delete();
		new File(outputFolder + "tmp3.xml.gz").delete();
		new File(outputFolder + "tmp3Attr.xml.gz").delete();
		new File(outputFolder + "tmp4Attr.xml.gz").delete();
	}

	/**
	 * @param outputFolder
	 */
	private static void combineSubpopulations(String outputFolder) {
		JoinSubpopulations.Run( 
				/* Car and commercial vehicles... */
				outputFolder + "car.xml.gz", outputFolder + "carAttr.xml.gz", 
				outputFolder + "com.xml.gz", outputFolder + "comAttr.xml.gz", 
				outputFolder + "tmp1.xml.gz", outputFolder + "tmp1Attr.xml.gz");
		JoinSubpopulations.Run(
				/* ... add bus... */
				outputFolder + "tmp1.xml.gz", outputFolder + "tmp1Attr.xml.gz", 
				outputFolder + "bus.xml.gz", outputFolder + "busAttr.xml.gz", 
				outputFolder + "tmp2.xml.gz", outputFolder + "tmp2Attr.xml.gz");
		JoinSubpopulations.Run(
				/* ... add taxi... */
				outputFolder + "tmp2.xml.gz", outputFolder + "tmp2Attr.xml.gz", 
				outputFolder + "taxi.xml.gz", outputFolder + "taxiAttr.xml.gz", 
				outputFolder + "tmp3.xml.gz", outputFolder + "tmp3Attr.xml.gz");
		JoinSubpopulations.Run(
				/* ... add external traffic... */
				outputFolder + "tmp3.xml.gz", outputFolder + "tmp3Attr.xml.gz", 
				outputFolder + "ext.xml.gz", outputFolder + "extAttr.xml.gz", 
				outputFolder + "gauteng.xml.gz", outputFolder + "tmp4Attr.xml.gz");
		
		/* Finally, add the vehicle class and eTag penetration. */
		AssignTollAttributes.Run(
				outputFolder + "gauteng.xml.gz", 
				outputFolder + "tmp4Attr.xml.gz",
				outputFolder + "gautengAttr.xml.gz");
		
		/* Report some summary statistics. */
		ReportPopulationStatistics.Run(
				outputFolder + "gauteng.xml.gz", 
				outputFolder + "gautengAttr.xml.gz");
	}

	/**
	 * @param outputFolder
	 */
	private static void convertOldSanralSubpopulations(String outputFolder) {
		SanralPopulationConverter.Run("/Users/jwjoubert/Documents/workspace/data-sanral2010/plans/car_plans_2009_10pctV0.xml.gz",
				"car", "WGS84_UTM35S", "car", 0.1, 
				outputFolder + "car.xml.gz",
				outputFolder + "carAttr.xml.gz",
				"WGS84_SA_Albers");
		SanralPopulationConverter.Run("/Users/jwjoubert/Documents/workspace/data-sanral2010/plans/bus_plans_2009_10pctV0.xml.gz",
				"bus", "WGS84_UTM35S", "bus", 0.1, 
				outputFolder + "bus.xml.gz",
				outputFolder + "busAttr.xml.gz",
				"WGS84_SA_Albers");
		SanralPopulationConverter.Run("/Users/jwjoubert/Documents/workspace/data-sanral2010/plans/taxi_plans_2009_10pctV0.xml.gz",
				"taxi", "WGS84_UTM35S", "taxi", 0.1, 
				outputFolder + "taxi.xml.gz",
				outputFolder + "taxiAttr.xml.gz",
				"WGS84_SA_Albers");
		SanralPopulationConverter.Run("/Users/jwjoubert/Documents/workspace/data-sanral2010/plans/ext_plans_2011_10pctV0.xml.gz",
				"ext", "WGS84_UTM35S", "ext", 0.1, 
				outputFolder + "ext.xml.gz",
				outputFolder + "extAttr.xml.gz",
				"WGS84_SA_Albers");
	}

}
