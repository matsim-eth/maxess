package playground.smeintjes;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.log4j.Logger;
import org.javatuples.Pair;
import org.javatuples.Quintet;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;

import playground.southafrica.utilities.Header;
import playground.southafrica.utilities.containers.MyZone;
import playground.southafrica.utilities.gis.MyMultiFeatureReader;


/**
 * This class reads in multiple freight population files generated by 
 * playground.smeintjes.MultipleFreightPopulationGenerator. For each
 * population, for each plan (each vehicle has one plan which is the
 * vehicle's activity chain): count the number of activities (major and
 * minor),  get the start time of the first major activity in the plan.
 * These values are then written out to a .csv file. 
 * @author sumarie
 *
 */
public class PopulationParser {
	private final static Logger LOG =  Logger.getLogger(PopulationParser.class.toString());
	private Scenario scenario;

	public static Geometry getGeometryFromShapeFile(String shapefile, int idField) {
		
		MyMultiFeatureReader mfr = new MyMultiFeatureReader();

		try {
			mfr.readMultizoneShapefile(shapefile, idField);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Cannot read shapefile from " + shapefile);
		}
		List<MyZone> list = mfr.getAllZones();
		if(list.size() > 1){
			LOG.warn("There are multiple shapefiles in " + shapefile);
			LOG.warn("Only the first will be used as the geographic area.");
		}
		MultiPolygon area = list.get(0);
		return area;
	}
	
	
	/**
	 * @param inputPath path to the folder where the populations are saved. Do not
	 * 		  include the trialing "/" - it gets added automatically when creating the 
	 *        file names
	 * @param pmin the minimum number of points used when generating the complex network 
	 * 		  for these populations
	 * @param radius radius as described above
	 * @param numberOfPopulations the number of populations in this input folder
	 */
	public static void main(String[] args) {
		Header.printHeader(PopulationParser.class.toString(), args);
		
		/* Read in arguments*/
		String inputPath = args[0];
		int pmin = Integer.parseInt(args[1]);
		int radius = Integer.parseInt(args[2]);
		int numberRuns = Integer.parseInt(args[3]);
		int numberPopulations = Integer.parseInt(args[4]);
		String saShapefile = args[5];
		String gautengShapefile = args[6];
		String ctShapefile = args[7];
		String eThekwiniShapefile = args[8];
		int numberOfThreads = Integer.parseInt(args[9]);
		
		Counter counter = new Counter("   population # ");
		
		/*Read in all four shapefiles*/
		Geometry southAfrica = getGeometryFromShapeFile(saShapefile, 1);
		Geometry gauteng = getGeometryFromShapeFile(gautengShapefile, 1);
		Geometry capeTown = getGeometryFromShapeFile(ctShapefile, 2);
		Geometry eThekwini = getGeometryFromShapeFile(eThekwiniShapefile, 1);
		/*Get envelopes*/
		Geometry saEnvelope = southAfrica.getEnvelope();
		Geometry gautengEnvelope = gauteng.getEnvelope();
		Geometry capeTownEnvelope = capeTown.getEnvelope();
		Geometry eThekwiniEnvelope = eThekwini.getEnvelope();
		
		
		/* Set up multi-threaded infrastructure */
		ExecutorService threadExecutor = Executors.newFixedThreadPool(numberOfThreads);
		List<Future<Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>>>> jobs = 
				new ArrayList<Future<Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>>>>();
		
		/* Iterate through the 100 populations and populate the populationMap */
		String outputFile = String.format("%s/populationAnalyser_%d_%d.csv", inputPath, pmin, radius);
		for (int i = 1; i < numberRuns; i++) {
			for (int j = 0; j < numberPopulations; j++) {
				int population = j*10 + i;
				
				Callable<Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>>> job = new ExtractorCallable(
						pmin, radius, i, j, population, inputPath, southAfrica, 
						gauteng, capeTown, eThekwini, counter, saEnvelope, gautengEnvelope,
						capeTownEnvelope, eThekwiniEnvelope);
				Future<Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>>> result = threadExecutor.submit(job);				
				jobs.add(result);
			}
		}
		counter.printCounter();
		LOG.info("Done processing populations...");
		
		/* Shutdown threadExecutor */
		threadExecutor.shutdown();
		while(!threadExecutor.isTerminated()){
		}
		counter.printCounter();
		
		/* Consolidate output */
		LOG.info("Consolidating output...");
		Map<Integer, List<int[]>> consolidatedMap = 
				new TreeMap<Integer, List<int[]>>();

		try{
			for(Future<Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>>> job : jobs){
				List<int[]> listArrays = new ArrayList<int[]>();
				Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>> thisJob = job.get();
				Quintet<Integer, Integer, int[], int[], int[]> quintet = thisJob.getValue0();
				List<Pair<int[], int[]>> listArrayPairs = thisJob.getValue1();
				// Add population
				int population = quintet.getValue0();
				LOG.info("Population " + population);
				int[] numberChainsNoMinorArray = new int[]{quintet.getValue1()};
				listArrays.add(numberChainsNoMinorArray);
				listArrays.add(quintet.getValue2());
				listArrays.add(quintet.getValue3());
				listArrays.add(quintet.getValue4());
				
				for (Pair<int[], int[]> pair : listArrayPairs) {
					int[] first = pair.getValue0();
					int[] second = pair.getValue1();
					listArrays.add(first);
					listArrays.add(second);
				}
				consolidatedMap.put(population, listArrays);
			}
				
				
		} catch (ExecutionException e) {
			e.printStackTrace();
			throw new RuntimeException("Couldn't get thread job result.");
		} catch (InterruptedException e) {
			e.printStackTrace();
			throw new RuntimeException("Couldn't get thread job result.");
		}

		writePopulationInfo(outputFile, consolidatedMap);
		Header.printFooter();

	}
	
	private static void writeResultToFile(
			Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>> thisResult,
			String resultOutputFile) {
		
		Map<Integer, List<int[]>> resultMap = new TreeMap<Integer, List<int[]>>();
		List<int[]> listArrays = new ArrayList<int[]>();
		
			Quintet<Integer, Integer, int[], int[], int[]> quintet = thisResult.getValue0();
			List<Pair<int[], int[]>> listArrayPairs = thisResult.getValue1();
			// Add population
			int population = quintet.getValue0();
			LOG.info("Writing population " + population + " to file.");
			//numberOfChainsNoMinor is put into an int[] and added to listArrays
			int[] numberChainsNoMinorArray = new int[]{quintet.getValue1()};
			//Add numberChainsNoMinor
			listArrays.add(numberChainsNoMinorArray);
			//Add areaArray
			listArrays.add(quintet.getValue2());
			//Add hourArray
			listArrays.add(quintet.getValue3());
			//Add activityArray
			listArrays.add(quintet.getValue4());
			
			for (Pair<int[], int[]> pair : listArrayPairs) {
				int[] first = pair.getValue0();
				int[] second = pair.getValue1();
				listArrays.add(first);
				listArrays.add(second);
			}
			resultMap.put(population, listArrays);
		
		
		writePopulationInfo(resultOutputFile, resultMap);
	}


	public PopulationParser() {
		this.scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
	}

	
	public Collection<? extends Person> readPopulation(String populationFile) {
		MatsimPopulationReader popReader = new MatsimPopulationReader(this.scenario);
		popReader.parse(populationFile);
		Collection<? extends Person> personCollection = this.scenario.getPopulation().getPersons().values();
		
		return personCollection;
	}
	
	/** Iterate through each plan of each person (vehicle) in this population. The areaArray keeps
	 *  track of the number of plans that contain activities that were performed in the combinations of locations
	 *  declared below (a1-a9). The activityArray keeps track of the number of plans that had "this" number of minor
	 *  activities. The number of minor activities can range from 0 to 71. 
	 *  Lastly, the hourArray keeps track of the number of plans that start in "this" hour.
	 *  We want to determine a number of things while iterating through all activities:
	 *  1. Determine the location of the activity. 
	 *    1.1. If it was performed in South Africa, change inSouthAfrica to true and continue
	 *    	   checking the rest of the areas. If it wasn't performed in South Africa, increment 
	 *         the first element in the areaArray by one and continue to the next person (vehicle).
	 *    1.2. Check whether activity was performed in Cape Town or eThekwini or Gauteng, and change
	 *         the boolean values respectively. Once a person has one activity in an area, stop
	 *         checking the subsequent activities for that area. Therefore we will have two loops:
	 *         while this person has more activities, and if boolean values are false.
	 *    1.3. After all activities for this person has been checked, match the combination of
	 *         boolean values to one of the area array combinations (initialised above) and increment that
	 *         value by one.
	 *  2. Keep track of the number of minor activities.
	 * 	  2.1. For every minor activity that is checked, increment the numberMinorActivities counter by one.
	 *    2.2. Once the end of the person's plan has been reached, find the element in the activityArray that
	 *         corresponds to "this" number of activities, and increment the element by one.
	 *  3. Determine in what hour this chain started.
	 * 	  3.1. For each first activity in a plan, get the endTime (this is when the major activity ended 
	 *         and the vehicle started its activities.
	 *    3.2. Find the corresponding element in the hourArray that matches "this" hour, and increment the
	 *         element by one.         
	 * @param eThekwiniEnvelope 
	 * @param ctEnvelope 
	 * @param gautengEnvelope 
	 * @param saEnvelope 
	 */
	public Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>> parsePopulations(int pmin, int radius, int run, 
			int populationNumber, int population, String inputPath, Geometry saGeometry,
			Geometry gautengGeometry, Geometry ctGeometry, Geometry eThekwiniGeometry,
			Geometry saEnvelope, Geometry gautengEnvelope, Geometry ctEnvelope, Geometry eThekwiniEnvelope) {

		/* Set up paths to input file */
		String populationFile = String.format("%sresults%d/trainingPopulation_%d_%d_%d_%d.xml.gz", inputPath, run, pmin, radius, run, populationNumber);
//		String populationFile = String.format("%s/trainingPopulation_%d_%d_%d_%d.xml.gz", inputPath, pmin, radius, run, populationNumber);
		
		/* Parse this population */
		LOG.info("Reading population " + population + " for configuration " + pmin + "_" + radius);
		Collection<? extends Person> personCollection = readPopulation(populationFile);
		
		/* Create arrays to hold values for output */
		int numberChainsNoMinor = 0;
		int hour = 24;
		int[] hourArray = new int[24];
		int[] activityArray = new int[72];
		int[] areaArray = new int[9];
		Arrays.fill(areaArray, 0);
		Arrays.fill(hourArray, 0);
		Arrays.fill(activityArray, 0);
		
		int[] hourArray1 = new int[24];
		int[] activityArray1 = new int[72];
		Arrays.fill(activityArray1, 0);
		Arrays.fill(hourArray1, 0);
		
		int[] hourArray2 = new int[24];
		int[] activityArray2 = new int[72];
		Arrays.fill(hourArray2, 0);
		Arrays.fill(activityArray2, 0);
		
		int[] hourArray3 = new int[24];
		int[] activityArray3 = new int[72];
		Arrays.fill(hourArray3, 0);
		Arrays.fill(activityArray3, 0);
		
		int[] hourArray4 = new int[24];
		int[] activityArray4 = new int[72];
		Arrays.fill(hourArray4, 0);
		Arrays.fill(activityArray4, 0);
		
		int[] hourArray5 = new int[24];
		int[] activityArray5 = new int[72];
		Arrays.fill(hourArray5, 0);
		Arrays.fill(activityArray5, 0);
		
		int[] hourArray6 = new int[24];
		int[] activityArray6 = new int[72];
		Arrays.fill(hourArray6, 0);
		Arrays.fill(activityArray6, 0);
		
		int[] hourArray7 = new int[24];
		int[] activityArray7 = new int[72];
		Arrays.fill(hourArray7, 0);
		Arrays.fill(activityArray7, 0);
		
		int[] hourArray8 = new int[24];
		int[] activityArray8 = new int[72];
		Arrays.fill(hourArray8, 0);
		Arrays.fill(activityArray8, 0);
		
		int[] hourArray9 = new int[24];
		int[] activityArray9 = new int[72];
		Arrays.fill(hourArray9, 0);
		Arrays.fill(activityArray9, 0);
		
				
		/* Set up area array combinations. These arrays show the possible combinations
		 * of locations of one activity chain (plan). The elements in these arrays correspond to
		 * [inSouthAfrica, inGauteng, inCapeTown, inEthekwini], and were identified as:
		 * 		a1. A vehicle can have no activities in South Africa.
		 * 		a2. A vehicle can have an activity in South Africa, but the activity
		 * 		    was not performed in Gauteng, eThewkini, or Cape Town.
		 * 		a3. An activity was performed in South Africa, and that activity was
		 *          performed in eThekwini.
		 *      a4. Activities were performed in South Africa, specifically in Cape Town.
		 *      a5. Activities were performed in South Africa, specifically in Gauteng.
		 *      a6. Activities were performed in South Africa, specifically in Gauteng and Cape Town.
		 *      a7. Activities were performed in South Africa, specifically in Cape Town and eThekwini.
		 *      a8. Activities were performed in South Africa, specifically in Gauteng, eThekwini and Cape Town.
		 *      a9. Activities were performed in South Africa, specifically in Gauteng and eThekwini.
		 * 
		 * The number of plans with the same combination (a1-a9) will be tallied and reported on in the output.
		 * 
		 */
		boolean[] a1 = new boolean[]{false, false, false, false};
		boolean[] a2 = new boolean[]{false, false, false, true};
		boolean[] a3 = new boolean[]{false, false, true, true};
		boolean[] a4 = new boolean[]{false, true, false, true};
		boolean[] a5 = new boolean[]{true, false, false, true};
		boolean[] a6 = new boolean[]{true, true, false, true};
		boolean[] a7 = new boolean[]{false, true, true, true};
		boolean[] a8 = new boolean[]{true, true, true, true};
		boolean[] a9 = new boolean[]{true, false, true, true};
		
		int person = 1;
		//TODO uncomment while loop before running on Hobbes!
//		for(int i = 0; i < 100; i++){
			
		
		while(personCollection.iterator().hasNext()){
//				LOG.info("Performing analysis for vehicle: " + person + " in population: " + population);
				
				boolean inSouthAfrica = false;
				boolean inGauteng = false;
				boolean inCapeTown = false;
				boolean inEthekwini = false;
				
				Person vehicle = personCollection.iterator().next();
				Plan selectedPlan = vehicle.getSelectedPlan();
				List<PlanElement> planElements = selectedPlan.getPlanElements();
		
			/* Only consider chains with minor activities. Those 
			 * without minor activities have 3 plan elements (two
			 * major activities and a leg in between).		
			 */
			if(planElements.size() > 3)	{
					int numberMinorActivities = 0;
					for(int j = 0; j < planElements.size(); j++){
						PlanElement planElement = planElements.get(j);
						if(planElement instanceof Activity){
							Activity activity = (Activity) planElement;
							if (j == 0) {
								
								/*The first major activity: get chain start time,
								 * determine the hour of the day, and increment the
								 * associated element in the hourArray.*/
								double endTime = activity.getEndTime();
								
								hour = convertSecondsToHourOfDay(endTime);
								incrementArray(hour, hourArray);
								
								
														
							} else if(j < planElements.size() - 1){
								
								/*The minor activities*/
								Coord coord = activity.getCoord();
								double x = coord.getX();
								double y = coord.getY();
								Coordinate coordinate = new Coordinate(x, y);
								
								/* Need to initialise separate area boolean variables
								 * so that the overarching boolean variables (of this plan) doesn't
								 * get overwritten.
								 */
								boolean activityInSA = false;
								boolean activityInGauteng = false;
								boolean activityInCapeTown = false;
								boolean activityInEthekwini = false;
								/*
								 * In this while loop, activity location is only checked if the overarching 
								 * boolean value for this plan is false. If this is the case, we
								 * check the location and assign it to the activity boolean value.
								 * Only if the activity boolean value is true, do we change the 
								 * overarching boolean value to true.
								 */
								activityInSA = checkActivityInArea(saGeometry, saEnvelope, coordinate, 1);
								if(activityInSA){
									inSouthAfrica = true;
									if(!inGauteng){
										activityInGauteng = checkActivityInArea(gautengGeometry, gautengEnvelope, coordinate, 1);
										if(activityInGauteng){
											inGauteng = true;
										} else if(!inCapeTown){		
											activityInCapeTown = checkActivityInArea(ctGeometry, ctEnvelope, coordinate, 2);
											if(activityInCapeTown){
												inCapeTown = true;
											} else if(!inEthekwini){
												activityInEthekwini = checkActivityInArea(eThekwiniGeometry, eThekwiniEnvelope, coordinate, 1);
												if(activityInEthekwini){
													inEthekwini = true;
												} else{
												}
											}
										}  
									} else if(!inCapeTown){
										activityInCapeTown = checkActivityInArea(ctGeometry, ctEnvelope, coordinate, 2);
										if(activityInCapeTown){
											inCapeTown = true;
										} else if(!inEthekwini){
											activityInEthekwini = checkActivityInArea(eThekwiniGeometry, eThekwiniEnvelope, coordinate, 2);
											if(activityInEthekwini){
												inEthekwini = true;
											} else{
											}
										} 
									} else if(!inEthekwini){
										activityInEthekwini = checkActivityInArea(eThekwiniGeometry, eThekwiniEnvelope, coordinate, 2);
										if(activityInEthekwini){
											inEthekwini = true;
										} else {
										}	
									} else{
									}
								} else{
								}
							numberMinorActivities++;
								
							} else {
								
								/*The last major activity*/
								
							}
						}
					}
					
						/* Each plan (vehicle) has a numberOfMinorActivities value. 
						 * Determine which element in activityArray corresponds to "this" number of activities;
						 * increment this value with one because the end of this chain/plan has been reached.
						 */
						if(numberMinorActivities > 0){
							incrementArray(numberMinorActivities, activityArray);
//							int indexOfMinorActivities = numberMinorActivities-1;
//							int valueAtIndex = activityArray[indexOfMinorActivities];
//							activityArray[indexOfMinorActivities] = valueAtIndex+1;
						
							/* Get the combination of activity locations in an array, and compare
							 * this array with the predefined possible combinations (a1-a9). Increment
							 * the corresponding element in the areaArray. Initialise to values bigger than size
							 * of array - will throw indexOutOfBoundsError if these values are not changed.
							 */
							boolean[] inAreaArray = new boolean[]{inGauteng, inCapeTown, inEthekwini, inSouthAfrica};
							if(Arrays.equals(inAreaArray, a1)){
								incrementArray(1, areaArray);
								incrementArray(hour, hourArray1);
								incrementArray(numberMinorActivities, activityArray1);
								
							} else if(Arrays.equals(inAreaArray, a2)){
								incrementArray(2, areaArray);
								incrementArray(hour, hourArray2);
								incrementArray(numberMinorActivities, activityArray2);
											
							} else if(Arrays.equals(inAreaArray, a3)){
								incrementArray(3, areaArray);
								incrementArray(hour, hourArray3);
								incrementArray(numberMinorActivities, activityArray3);
								
							} else if(Arrays.equals(inAreaArray, a4)){
								incrementArray(4, areaArray);
								incrementArray(hour, hourArray4);
								incrementArray(numberMinorActivities, activityArray4);
								
							} else if(Arrays.equals(inAreaArray, a5)){
								incrementArray(5, areaArray);
								incrementArray(hour, hourArray5);
								incrementArray(numberMinorActivities, activityArray5);
								
							} else if(Arrays.equals(inAreaArray, a6)){
								incrementArray(6, areaArray);
								incrementArray(hour, hourArray6);
								incrementArray(numberMinorActivities, activityArray6);
								
							} else if(Arrays.equals(inAreaArray, a7)){
								incrementArray(7, areaArray);
								incrementArray(hour, hourArray7);
								incrementArray(numberMinorActivities, activityArray7);
								
							} else if(Arrays.equals(inAreaArray, a8)){
								incrementArray(8, areaArray);
								incrementArray(hour, hourArray8);
								incrementArray(numberMinorActivities, activityArray8);
								
							} else if(Arrays.equals(inAreaArray, a9)){
								incrementArray(9, areaArray);
								incrementArray(hour, hourArray9);
								incrementArray(numberMinorActivities, activityArray9);								

							} 
				
						} 
			} else{
				numberChainsNoMinor++;
			}
			personCollection.remove(vehicle);
			person++;
			}
		
		/* Quartet containing the population number, the
		 * number of chains with no minor activities and
		 * the aggregate area, hour, and activity arrays.
		 */
		Quintet<Integer, Integer, int[], int[], int[]> populationQuintet = 
				new Quintet<Integer, Integer, int[], int[], int[]>(population, numberChainsNoMinor, areaArray, hourArray, activityArray);
				
		/* Set up the pairs of arrays for a1 - a9, and add
		 * it to a list.
		 */
		Pair<int[], int[]> a1Pair = new Pair<int[], int[]>(hourArray1, activityArray1);
		Pair<int[], int[]> a2Pair = new Pair<int[], int[]>(hourArray2, activityArray2);
		Pair<int[], int[]> a3Pair = new Pair<int[], int[]>(hourArray3, activityArray3);
		Pair<int[], int[]> a4Pair = new Pair<int[], int[]>(hourArray4, activityArray4);
		Pair<int[], int[]> a5Pair = new Pair<int[], int[]>(hourArray5, activityArray5);
		Pair<int[], int[]> a6Pair = new Pair<int[], int[]>(hourArray6, activityArray6);
		Pair<int[], int[]> a7Pair = new Pair<int[], int[]>(hourArray7, activityArray7);
		Pair<int[], int[]> a8Pair = new Pair<int[], int[]>(hourArray8, activityArray8);
		Pair<int[], int[]> a9Pair = new Pair<int[], int[]>(hourArray9, activityArray9);
		
		List<Pair<int[], int[]>> pairList = new ArrayList<Pair<int[], int[]>>();
		pairList.add(a1Pair);
		pairList.add(a2Pair);
		pairList.add(a3Pair);
		pairList.add(a4Pair);
		pairList.add(a5Pair);
		pairList.add(a6Pair);
		pairList.add(a7Pair);
		pairList.add(a8Pair);
		pairList.add(a9Pair);
		
		/* Create populationPair to be passed to writeOutput method. */
		Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>> populationPair = 
				new Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>>(populationQuintet, pairList);
		
		
		return populationPair;

	}
	
	/* This method takes the given value, matches it to the 
	 * index where this value occurs in the array, and increments
	 * the value at that index by one.
	 */
	private void incrementArray(int value, int[] array) {
		int indexOfValue = value-1;
		int valueAtIndex = array[indexOfValue];
		array[indexOfValue] = valueAtIndex+1;
	}

	
	private static int convertSecondsToHourOfDay(Double startTime){
		int hours = (int)Math.ceil(startTime.intValue()/3600.0);
		return hours;
	}
	
	private static boolean checkActivityInArea(Geometry area, Geometry envelope, Coordinate activity, int idField){
		boolean inArea = false;
		GeometryFactory gf = new GeometryFactory();
		Point activityPoint = gf.createPoint(activity);
//		Geometry envelope = area.getEnvelope();
		if(envelope.covers(activityPoint)){
			
			if (area.covers(activityPoint)) {
				inArea = true;
			}
		}
		return inArea;
	}


	public static void writePopulationInfo(String outputFile, Map<Integer, List<int[]>> consolidatedMap){
		LOG.info("Writing population to " + outputFile);
		BufferedWriter bw = IOUtils.getBufferedWriter(outputFile);
		
		try {
			bw.write("population, numberChainsNoMinor, " +
					"a1, a2, a3, a4, a5, a6, a7, a8, a9, " +
					"h1, h2, h3, h4, h5, h6, h7, h8, h9," +
					"h10, h11, h12, h13, h14, h15, h16, h17, h18, h19," +
					"h20, h21, h22, h23, h24," +
					"m0, m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, " +
					"m11, m12, m13, m14, m15, m16, m17, m18, m19, m20, " +
					"m21, m22, m23, m24, m25, m26, m27, m28, m29, m30, " +
					"m31, m32, m33, m34, m35, m36, m37, m38, m39, m40, " +
					"m41, m42, m43, m44, m45, m46, m47, m48, m49, m50, " +
					"m51, m52, m53, m54, m55, m56, m57, m58, m59, m60, " +
					"m61, m62, m63, m64, m65, m66, m67, m68, m69, m70, " +
					"m71, " +
					
					"a1-h1, a1-h2, a1-h3, a1-h4,a1-h5, a1-h6, a1-h7, a1-h8, a1-h9," +
					"a1-h10, a1-h11, a1-h12, a1-h13, a1-h14, a1-h15, a1-h16, a1-h17, a1-h18, a1-h19," +
					"a1-h20, a1-h21, a1-h22, a1-h23, a1-h24," +
					"a1-m0, a1-m1, a1-m2, a1-m3, a1-m4, a1-m5, a1-m6, a1-m7, a1-m8, a1-m9, a1-m10, " +
					"a1-m11, a1-m12, a1-m13, a1-m14, a1-m15, a1-m16, a1-m17, a1-m18, a1-m19, a1-m20, " +
					"a1-m21, a1-m22, a1-m23, a1-m24, a1-m25, a1-m26, a1-m27, a1-m28, a1-m29, a1-m30, " +
					"a1-m31, a1-m32, a1-m33, a1-m34, a1-m35, a1-m36, a1-m37, a1-m38, a1-m39, a1-m40, " +
					"a1-m41, a1-m42, a1-m43, a1-m44, a1-m45, a1-m46, a1-m47, a1-m48, a1-m49, a1-m50, " +
					"a1-m51, a1-m52, a1-m53, a1-m54, a1-m55, a1-m56, a1-m57, a1-m58, a1-m59, a1-m60, " +
					"a1-m61, a1-m62, a1-m63, a1-m64, a1-m65, a1-m66, a1-m67, a1-m68, a1-m69, a1-m70, " +
					"a1-m71, " +
					
					"a2-h1, a2-h2, a2-h3, a2-h4, a2-h5, a2-h6, a2-h7, a2-h8, a2-h9," +
					"a2-h10, a2-h11, a2-h12, a2-h13, a2-h14, a2-h15, a2-h16, a2-h17, a2-h18, a2-h19," +
					"a2-h20, a2-h21, a2-h22, a2-h23, a2-h24," +
					"a2-m0, a2-m1, a2-m2, a2-m3, a2-m4, a2-m5, a2-m6, a2-m7, a2-m8, a2-m9, a2-m10, " +
					"a2-m11, a2-m12, a2-m13, a2-m14, a2-m15, a2-m16, a2-m17, a2-m18, a2-m19, a2-m20, " +
					"a2-m21, a2-m22, a2-m23, a2-m24, a2-m25, a2-m26, a2-m27, a2-m28, a2-m29, a2-m30, " +
					"a2-m31, a2-m32, a2-m33, a2-m34, a2-m35, a2-m36, a2-m37, a2-m38, a2-m39, a2-m40, " +
					"a2-m41, a2-m42, a2-m43, a2-m44, a2-m45, a2-m46, a2-m47, a2-m48, a2-m49, a2-m50, " +
					"a2-m51, a2-m52, a2-m53, a2-m54, a2-m55, a2-m56, a2-m57, a2-m58, a2-m59, a2-m60, " +
					"a2-m61, a2-m62, a2-m63, a2-m64, a2-m65, a2-m66, a2-m67, a2-m68, a2-m69, a2-m70, " +
					"a2-m71, " +
					
					"a3-h1, a3-h2, a3-h3, a3-h4, a3-h5, a3-h6, a3-h7, a3-h8, a3-h9," +
					"a3-h10, a3-h11, a3-h12, a3-h13, a3-h14, a3-h15, a3-h16, a3-h17, a3-h18, a3-h19," +
					"a3-h20, a3-h21, a3-h22, a3-h23, a3-h24," +
					"a3-m0, a3-m1, a3-m2, a3-m3, a3-m4, a3-m5, a3-m6, a3-m7, a3-m8, a3-m9, a3-m10, " +
					"a3-m11, a3-m12, a3-m13, a3-m14, a3-m15, a3-m16, a3-m17, a3-m18, a3-m19, a3-m20, " +
					"a3-m21, a3-m22, a3-m23, a3-m24, a3-m25, a3-m26, a3-m27, a3-m28, a3-m29, a3-m30, " +
					"a3-m31, a3-m32, a3-m33, a3-m34, a3-m35, a3-m36, a3-m37, a3-m38, a3-m39, a3-m40, " +
					"a3-m41, a3-m42, a3-m43, a3-m44, a3-m45, a3-m46, a3-m47, a3-m48, a3-m49, a3-m50, " +
					"a3-m51, a3-m52, a3-m53, a3-m54, a3-m55, a3-m56, a3-m57, a3-m58, a3-m59, a3-m60, " +
					"a3-m61, a3-m62, a3-m63, a3-m64, a3-m65, a3-m66, a3-m67, a3-m68, a3-m69, a3-m70, " +
					"a3-m71," +
					
					"a4-h1, a4-h2, a4-h3, a4-h4, a4-h5, a4-h6, a4-h7, a4-h8, a4-h9," +
					"a4-h10, a4-h11, a4-h12, a4-h13, a4-h14, a4-h15, a4-h16, a4-h17, a4-h18, a4-h19," +
					"a4-h20, a4-h21, a4-h22, a4-h23, a4-h24," +
					"a4-m0, a4-m1, a4-m2, a4-m3, a4-m4, a4-m5, a4-m6, a4-m7, a4-m8, a4-m9, a4-m10, " +
					"a4-m11, a4-m12, a4-m13, a4-m14, a4-m15, a4-m16, a4-m17, a4-m18, a4-m19, a4-m20, " +
					"a4-m21, a4-m22, a4-m23, a4-m24, a4-m25, a4-m26, a4-m27, a4-m28, a4-m29, a4-m30, " +
					"a4-m31, a4-m32, a4-m33, a4-m34, a4-m35, a4-m36, a4-m37, a4-m38, a4-m39, a4-m40, " +
					"a4-m41, a4-m42, a4-m43, a4-m44, a4-m45, a4-m46, a4-m47, a4-m48, a4-m49, a4-m50, " +
					"a4-m51, a4-m52, a4-m53, a4-m54, a4-m55, a4-m56, a4-m57, a4-m58, a4-m59, a4-m60, " +
					"a4-m61, a4-m62, a4-m63, a4-m64, a4-m65, a4-m66, a4-m67, a4-m68, a4-m69, a4-m70, " +
					"a4-m71, " +
					
					"a5-h1, a5-h2, a5-h3, a5-h4, a5-h5, a5-h6, a5-h7, a5-h8, a5-h9," +
					"a5-h10, a5-h11, a5-h12, a5-h13, a5-h14, a5-h15, a5-h16, a5-h17, a5-h18, a5-h19," +
					"a5-h20, a5-h21, a5-h22, a5-h23, a5-h24," +
					"a5-m0, a5-m1, a5-m2, a5-m3, a5-m4, a5-m5, a5-m6, a5-m7, a5-m8, a5-m9, a5-m10, " +
					"a5-m11, a5-m12, a5-m13, a5-m14, a5-m15, a5-m16, a5-m17, a5-m18, a5-m19, a5-m20, " +
					"a5-m21, a5-m22, a5-m23, a5-m24, a5-m25, a5-m26, a5-m27, a5-m28, a5-m29, a5-m30, " +
					"a5-m31, a5-m32, a5-m33, a5-m34, a5-m35, a5-m36, a5-m37, a5-m38, a5-m39, a5-m40, " +
					"a5-m41, a5-m42, a5-m43, a5-m44, a5-m45, a5-m46, a5-m47, a5-m48, a5-m49, a5-m50, " +
					"a5-m51, a5-m52, a5-m53, a5-m54, a5-m55, a5-m56, a5-m57, a5-m58, a5-m59, a5-m60, " +
					"a5-m61, a5-m62, a5-m63, a5-m64, a5-m65, a5-m66, a5-m67, a5-m68, a5-m69, a5-m70, " +
					"a5-m71," +
					
					"a6-h1, a6-h2, a6-h3, a6-h4, a6-h5, a6-h6, a6-h7, a6-h8, a6-h9," +
					"a6-h10, a6-h11, a6-h12, a6-h13, a6-h14, a6-h15, a6-h16, a6-h17, a6-h18, a6-h19," +
					"a6-h20, a6-h21, a6-h22, a6-h23, a6-h24," +
					"a6-m0, a6-m1, a6-m2, a6-m3, a6-m4, a6-m5, a6-m6, a6-m7, a6-m8, a6-m9, a6-m10, " +
					"a6-m11, a6-m12, a6-m13, a6-m14, a6-m15, a6-m16, a6-m17, a6-m18, a6-m19, a6-m20, " +
					"a6-m21, a6-m22, a6-m23, a6-m24, a6-m25, a6-m26, a6-m27, a6-m28, a6-m29, a6-m30, " +
					"a6-m31, a6-m32, a6-m33, a6-m34, a6-m35, a6-m36, a6-m37, a6-m38, a6-m39, a6-m40, " +
					"a6-m41, a6-m42, a6-m43, a6-m44, a6-m45, a6-m46, a6-m47, a6-m48, a6-m49, a6-m50, " +
					"a6-m51, a6-m52, a6-m53, a6-m54, a6-m55, a6-m56, a6-m57, a6-m58, a6-m59, a6-m60, " +
					"a6-m61, a6-m62, a6-m63, a6-m64, a6-m65, a6-m66, a6-m67, a6-m68, a6-m69, a6-m70, " +
					"a6-m71, " +
					
					"a7-h1, a7-h2, a7-h3, a7-h4, a7-h5, a7-h6, a7-h7, a7-h8, a7-h9," +
					"a7-h10, a7-h11, a7-h12, a7-h13, a7-h14, a7-h15, a7-h16, a7-h17, a7-h18, a7-h19," +
					"a7-h20, a7-h21, a7-h22, a7-h23, a7-h24," +
					"a7-m0, a7-m1, a7-m2, a7-m3, a7-m4, a7-m5, a7-m6, a7-m7, a7-m8, a7-m9, a7-m10, " +
					"a7-m11, a7-m12, a7-m13, a7-m14, a7-m15, a7-m16, a7-m17, a7-m18, a7-m19, a7-m20, " +
					"a7-m21, a7-m22, a7-m23, a7-m24, a7-m25, a7-m26, a7-m27, a7-m28, a7-m29, a7-m30, " +
					"a7-m31, a7-m32, a7-m33, a7-m34, a7-m35, a7-m36, a7-m37, a7-m38, a7-m39, a7-m40, " +
					"a7-m41, a7-m42, a7-m43, a7-m44, a7-m45, a7-m46, a7-m47, a7-m48, a7-m49, a7-m50, " +
					"a7-m51, a7-m52, a7-m53, a7-m54, a7-m55, a7-m56, a7-m57, a7-m58, a7-m59, a7-m60, " +
					"a7-m61, a7-m62, a7-m63, a7-m64, a7-m65, a7-m66, a7-m67, a7-m68, a7-m69, a7-m70, " +
					"a7-m71, " +
					
					"a8-h1, a8-h2, a8-h3, a8-h4, a8-h5, a8-h6, a8-h7, a8-h8, a8-h9," +
					"a8-h10, a8-h11, a8-h12, a8-h13, a8-h14, a8-h15, a8-h16, a8-h17, a8-h18, a8-h19," +
					"a8-h20, a8-h21, a8-h22, a8-h23, a8-h24," +
					"a8-m0, a8-m1, a8-m2, a8-m3, a8-m4, a8-m5, a8-m6, a8-m7, a8-m8, a8-m9, a8-m10, " +
					"a8-m11, a8-m12, a8-m13, a8-m14, a8-m15, a8-m16, a8-m17, a8-m18, a8-m19, a8-m20, " +
					"a8-m21, a8-m22, a8-m23, a8-m24, a8-m25, a8-m26, a8-m27, a8-m28, a8-m29, a8-m30, " +
					"a8-m31, a8-m32, a8-m33, a8-m34, a8-m35, a8-m36, a8-m37, a8-m38, a8-m39, a8-m40, " +
					"a8-m41, a8-m42, a8-m43, a8-m44, a8-m45, a8-m46, a8-m47, a8-m48, a8-m49, a8-m50, " +
					"a8-m51, a8-m52, a8-m53, a8-m54, a8-m55, a8-m56, a8-m57, a8-m58, a8-m59, a8-m60, " +
					"a8-m61, a8-m62, a8-m63, a8-m64, a8-m65, a8-m66, a8-m67, a8-m68, a8-m69, a8-m70, " +
					"a8-m71," +
					
					"a9-h1, a9-h2, a9-h3, a9-h4, a9-h5, a9-h6, a9-h7, a9-h8, a9-h9," +
					"a9-h10, a9-h11, a9-h12, a9-h13, a9-h14, a9-h15, a9-h16, a9-h17, a9-h18, a9-h19," +
					"a9-h20, a9-h21, a9-h22, a9-h23, a9-h24," +
					"a9-m0, a9-m1, a9-m2, a9-m3, a9-m4, a9-m5, a9-m6, a9-m7, a9-m8, a9-m9, a9-m10, " +
					"a9-m11, a9-m12, a9-m13, a9-m14, a9-m15, a9-m16, a9-m17, a9-m18, a9-m19, a9-m20, " +
					"a9-m21, a9-m22, a9-m23, a9-m24, a9-m25, a9-m26, a9-m27, a9-m28, a9-m29, a9-m30, " +
					"a9-m31, a9-m32, a9-m33, a9-m34, a9-m35, a9-m36, a9-m37, a9-m38, a9-m39, a9-m40, " +
					"a9-m41, a9-m42, a9-m43, a9-m44, a9-m45, a9-m46, a9-m47, a9-m48, a9-m49, a9-m50, " +
					"a9-m51, a9-m52, a9-m53, a9-m54, a9-m55, a9-m56, a9-m57, a9-m58, a9-m59, a9-m60, " +
					"a9-m61, a9-m62, a9-m63, a9-m64, a9-m65, a9-m66, a9-m67, a9-m68, a9-m69, a9-m70, " +
					"a9-m71");
			bw.newLine();
			Set<Entry<Integer, List<int[]>>> entrySet = consolidatedMap.entrySet();
			for (Entry<Integer, List<int[]>> populationEntry : entrySet) {
				int population = populationEntry.getKey();
				bw.write(Integer.toString(population));
				bw.write(",");
				List<int[]> intArrayList = populationEntry.getValue();
				for (int[] intArray : intArrayList) {
					for(int j = 0; j < intArray.length; j++){
						bw.write(Integer.toString(intArray[j]));
						bw.write(",");
					}
				}
				bw.newLine();
			}

			
		} catch (IOException e) {
			throw new RuntimeException("Could not read from BufferedWrited " + outputFile);
		} finally{
			try {
				bw.close();
			} catch (IOException e) {
				throw new RuntimeException("Could not close BufferedWriter " + outputFile);
			}
		}
	}
	

	private static class ExtractorCallable implements Callable<Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>>>{
		
		
		public Counter counter;
		private int pmin;
		private int radius;
		private int run;
		private int populationNumber;
		private int population;
		private String inputPath;
		private Geometry saGeometry;
		private Geometry gautengGeometry;
		private Geometry ctGeometry;
		private Geometry eThekwiniGeometry;
		private Geometry saEnvelope;
		private Geometry gautengEnvelope;
		private Geometry ctEnvelope;
		private Geometry eThekwiniEnvelope;
	
		public ExtractorCallable(int pmin, int radius, int i, int j, int population, String inputPath, 
				Geometry southAfrica, Geometry gauteng, Geometry capeTown, Geometry eThekwini, Counter counter, 
				Geometry saEnvelope, Geometry gautengEnvelope, Geometry capeTownEnvelope, Geometry eThekwiniEnvelope) {
			this.counter = counter;
			this.pmin = pmin;
			this.radius = radius;
			this.run = i;
			this.populationNumber = j;
			this.population = population;
			this.inputPath = inputPath;
			this.saGeometry = southAfrica;
			this.gautengGeometry = gauteng;
			this.ctGeometry = capeTown;
			this.eThekwiniGeometry = eThekwini;
			this.saEnvelope = saEnvelope;
			this.gautengEnvelope = gautengEnvelope;
			this.ctEnvelope = capeTownEnvelope;
			this.eThekwiniEnvelope = eThekwiniEnvelope;
		}
	
	
		@Override
		public Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>> call() throws Exception {
	
			String resultOutputFile = String.format("%s/populationAnalyser_%d_%d_%d.csv", inputPath, pmin, radius, population);
			PopulationParser parser = new PopulationParser();
			Pair<Quintet<Integer, Integer, int[], int[], int[]>, List<Pair<int[], int[]>>> populationPair = parser.parsePopulations(
					pmin, radius, run, populationNumber, population, inputPath, saGeometry, gautengGeometry, ctGeometry, eThekwiniGeometry,
					saEnvelope, gautengEnvelope, ctEnvelope, eThekwiniEnvelope);
			writeResultToFile(populationPair, resultOutputFile);
			counter.incCounter();
			return populationPair;
		}
	}
}
