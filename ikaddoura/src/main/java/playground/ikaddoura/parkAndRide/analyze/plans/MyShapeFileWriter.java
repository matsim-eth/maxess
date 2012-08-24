/* *********************************************************************** *
 * project: org.matsim.*
 * MyShapeFileWriter.java
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

/**
 * 
 */
package playground.ikaddoura.parkAndRide.analyze.plans;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.Map.Entry;

import org.geotools.factory.FactoryRegistryException;
import org.geotools.feature.AttributeType;
import org.geotools.feature.AttributeTypeFactory;
import org.geotools.feature.DefaultAttributeTypeFactory;
import org.geotools.feature.Feature;
import org.geotools.feature.FeatureType;
import org.geotools.feature.FeatureTypeBuilder;
import org.geotools.feature.IllegalAttributeException;
import org.geotools.feature.SchemaException;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileWriter;

import playground.ikaddoura.parkAndRide.pR.ParkAndRideFacility;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

/**
 * @author Ihab
 *
 */
public class MyShapeFileWriter {
	
	private GeometryFactory geometryFactory = new GeometryFactory();
	ArrayList<Feature> FeatureList = new ArrayList<Feature>();
	private FeatureType featureType;
	
	public void writeShapeFileLines(Scenario scenario, String outputFile) {
		initFeatureType1();
		Collection<Feature> features = createFeatures1(scenario);
		ShapeFileWriter.writeGeometries(features, outputFile);
		System.out.println("ShapeFile " + outputFile + " written.");	
	}
	
	public void writeShapeFilePoints(Scenario scenario, SortedMap<Id,Coord> koordinaten, String outputFile) {
		if (koordinaten.isEmpty() == true){
			System.out.println("Map is empty, shapeFile " + outputFile + " not written!");
		}
		else {
			initFeatureType2();
			Collection<Feature> features = createFeatures2(scenario, koordinaten);
			ShapeFileWriter.writeGeometries(features,  outputFile);
			System.out.println("ShapeFile " + outputFile + " written.");	
		}
	}
	
	public void writeShapeFileGeometry(Map<Integer, Geometry> nr2geometry, Map<Integer, Double> nr2PRUsersHomeShare, String outputFile) {
		initFeatureType4();
		Collection<Feature> features = createFeatures4(nr2geometry, nr2PRUsersHomeShare);
		ShapeFileWriter.writeGeometries(features, outputFile);
		System.out.println("ShapeFile " + outputFile + " written.");	
	}
	
	public void writeShapeFilePRUsage(Scenario scenario, Map<Id, ParkAndRideFacility> id2prFacilities, Map<Id, Integer> prLinkId2prActs, String outputFile) {
		if (prLinkId2prActs.isEmpty() == true){
			System.out.println("Map is empty, shapeFile " + outputFile + " not written!");
		}
		
		initFeatureType3();
		Collection<Feature> features = createFeatures3(scenario, id2prFacilities, prLinkId2prActs);
		ShapeFileWriter.writeGeometries(features, outputFile);
		System.out.println("ShapeFile " + outputFile + " written.");	
	}

	private void initFeatureType1() {
		AttributeType [] attribs = new AttributeType[2];
		attribs[0] = DefaultAttributeTypeFactory.newAttributeType("LineString",LineString.class, true, null, null, MGC.getCRS(TransformationFactory.WGS84_UTM35S));
		attribs[1] = AttributeTypeFactory.newAttributeType("ID", String.class);

		try {
		this.featureType = FeatureTypeBuilder.newFeatureType(attribs, "link");
		} catch (FactoryRegistryException e) {
		e.printStackTrace();
		} catch (SchemaException e) {
		e.printStackTrace();
		}		
	}
	
	private void initFeatureType2() {
		AttributeType [] attribs = new AttributeType[2];
		attribs[0] = DefaultAttributeTypeFactory.newAttributeType("Point",Point.class, true, null, null, MGC.getCRS(TransformationFactory.WGS84_UTM35S));
		attribs[1] = AttributeTypeFactory.newAttributeType("PersonID", String.class);
		
		try {
		this.featureType = FeatureTypeBuilder.newFeatureType(attribs, "point");
		} catch (FactoryRegistryException e) {
		e.printStackTrace();
		} catch (SchemaException e) {
		e.printStackTrace();
		}		
	}
	
	private void initFeatureType3() {
		AttributeType [] attribs = new AttributeType[4];
		attribs[0] = DefaultAttributeTypeFactory.newAttributeType("LineString",LineString.class, true, null, null, MGC.getCRS(TransformationFactory.WGS84_UTM35S));
		attribs[1] = AttributeTypeFactory.newAttributeType("ID", String.class);
		attribs[2] = AttributeTypeFactory.newAttributeType("Stop", String.class);
		attribs[3] = AttributeTypeFactory.newAttributeType("Users", Integer.class);


		try {
		this.featureType = FeatureTypeBuilder.newFeatureType(attribs, "link");
		} catch (FactoryRegistryException e) {
		e.printStackTrace();
		} catch (SchemaException e) {
		e.printStackTrace();
		}	
	}
	
	private void initFeatureType4() {
		AttributeType [] attribs = new AttributeType[3];
		
		attribs[0] = DefaultAttributeTypeFactory.newAttributeType("Geometry", Geometry.class, true, null, null, MGC.getCRS(TransformationFactory.WGS84_UTM35S));
		attribs[1] = AttributeTypeFactory.newAttributeType("NR", String.class);
		attribs[2] = AttributeTypeFactory.newAttributeType("PRUsersHomeShare", Double.class);

		try {
		this.featureType = FeatureTypeBuilder.newFeatureType(attribs, "geometry");
		} catch (FactoryRegistryException e) {
		e.printStackTrace();
		} catch (SchemaException e) {
		e.printStackTrace();
		}		
	}
	
	private Collection<Feature> createFeatures1(Scenario scenario) {
		ArrayList<Feature> liste = new ArrayList<Feature>();
		for (Link link : scenario.getNetwork().getLinks().values()){
			liste.add(getFeature1(link));
		}
		return liste;
	}
	
	private Collection<Feature> createFeatures2(Scenario scenario, SortedMap<Id,Coord> Koordinaten) {
		ArrayList<Feature> liste = new ArrayList<Feature>();
		for (Entry<Id,Coord> entry : Koordinaten.entrySet()){
			liste.add(getFeature2((Coord)entry.getValue(), (Id)entry.getKey()));
		}
		return liste;
	}
	
	private Collection<Feature> createFeatures3(Scenario scenario, Map<Id, ParkAndRideFacility> id2prFacilities, Map<Id, Integer> prLinkId2prActs) {
		ArrayList<Feature> liste = new ArrayList<Feature>();
		for (Id linkId : prLinkId2prActs.keySet()){
			
			String name = "";
			for (ParkAndRideFacility pr : id2prFacilities.values()){
				if (pr.getPrLink3in().equals(linkId)){
					name = pr.getStopFacilityName();
				}
			}
			
			liste.add(getFeature3(scenario.getNetwork().getLinks().get(linkId), name, prLinkId2prActs.get(linkId)));
		}
		return liste;
	}
	
	private Collection<Feature> createFeatures4(Map<Integer, Geometry> nr2geometry, Map<Integer, Double> nr2PRUsersHomeShare) {
		ArrayList<Feature> liste = new ArrayList<Feature>();
		for (Integer nr : nr2geometry.keySet()){
			liste.add(getFeature4(nr, nr2geometry.get(nr), nr2PRUsersHomeShare));
		}
		return liste;
	}

	private Feature getFeature1(Link link) {
		LineString ls = this.geometryFactory.createLineString(new Coordinate[] {MGC.coord2Coordinate(link.getFromNode().getCoord()), MGC.coord2Coordinate(link.getToNode().getCoord())});
		Object [] attribs = new Object[2];
		attribs[0] = ls;
		attribs[1] = link.getId().toString();
		
		try {
		return this.featureType.create(attribs);
		} catch (IllegalAttributeException e) {
		throw new RuntimeException(e);
		}
	}
	
	private Feature getFeature4(Integer nr, Geometry geometry, Map<Integer, Double> nr2PRUsersHomeShare) {
		Geometry g = this.geometryFactory.createGeometry(geometry);
		Object [] attribs = new Object[3];
		attribs[0] = g;
		attribs[1] = String.valueOf(nr);
		if (nr2PRUsersHomeShare.containsKey(nr)){
			attribs[2] = nr2PRUsersHomeShare.get(nr).toString();
		} else {
			
		}

		try {
		return this.featureType.create(attribs);
		} catch (IllegalAttributeException e) {
		throw new RuntimeException(e);
		}
	}
	
	private Feature getFeature2(Coord coord, Id id) {
		Coordinate homeCoordinate = new Coordinate(coord.getX(), coord.getY());
		Point p = this.geometryFactory.createPoint(homeCoordinate);
		Object [] attribs = new Object[2];
		attribs[0] = p;
		attribs[1] = id;
		
		try {
		return this.featureType.create(attribs);
		} catch (IllegalAttributeException e) {
		throw new RuntimeException(e);
		}
	}
	
	private Feature getFeature3(Link link, String transitStopName, Integer prUsers) {
		LineString ls = this.geometryFactory.createLineString(new Coordinate[] {MGC.coord2Coordinate(link.getFromNode().getCoord()), MGC.coord2Coordinate(link.getToNode().getCoord())});
		Object [] attribs = new Object[4];
		attribs[0] = ls;
		attribs[1] = link.getId().toString();
		attribs[2] = transitStopName;
		attribs[3] = prUsers;
		
		try {
			return this.featureType.create(attribs);
		} catch (IllegalAttributeException e) {
			throw new RuntimeException(e);
		}
	}
}
