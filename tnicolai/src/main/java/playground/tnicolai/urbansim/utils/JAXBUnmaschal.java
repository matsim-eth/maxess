/* *********************************************************************** *
 * project: org.matsim.*
 * JAXBUnmaschal.java
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

/**
 * 
 */
package playground.tnicolai.urbansim.utils;

import java.io.File;
import java.io.IOException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import playground.tnicolai.urbansim.MATSim4Urbansim;
import playground.tnicolai.urbansim.com.matsim.config.MatsimConfigType;
import playground.tnicolai.urbansim.constants.Constants;
import playground.tnicolai.urbansim.utils.io.LoadFile;

/**
 * @author thomas
 *
 */
public class JAXBUnmaschal {
	
	// logger
	private static final Logger log = Logger.getLogger(JAXBUnmaschal.class);
	
	private String matsimConfigFile = null;
	
	public JAXBUnmaschal(String configFile){
		matsimConfigFile = configFile;
	}
	
	/**
	 * unmarschal (read) matsim config
	 * @return
	 */
	public boolean unmaschalMATSimConfig(){

		log.info("Staring unmaschalling MATSim configuration from: " + matsimConfigFile );
		log.info("...");
		try{
			JAXBContext jaxbContext = JAXBContext.newInstance(playground.tnicolai.urbansim.com.matsim.config.ObjectFactory.class);
			// create an unmaschaller (write xml file)
			Unmarshaller unmarschaller = jaxbContext.createUnmarshaller();

			// crate a schema factory ...
			SchemaFactory schemaFactory = SchemaFactory.newInstance( XMLConstants.W3C_XML_SCHEMA_NS_URI );
			// ... and initialize it with an xsd (xsd lies in the urbansim project)
			
			LoadFile loadFile = new LoadFile(Constants.MATSim_4_UrbanSim_XSD, CommonUtilities.getCurrentPath(MATSim4Urbansim.class) + "tmp/", "MATSim4UrbanSimConfigSchema.xsd");
			File file2XSD = loadFile.loadMATSim4UrbanSimXSD();
			
			// for debugging
			// File file2XSD = new File( "/Users/thomas/Development/workspace/urbansim_trunk/opus_matsim/sustain_city/models/pyxb_xml_parser/MATSim4UrbanSimConfigSchema.xsd" ); 
			if(file2XSD == null || !file2XSD.exists()){
				
				log.warn(file2XSD.getCanonicalPath() + " is not available. Loading compensatory xsd instead (this could be an older xsd version and may not work correctly).");
				log.warn("Compensatory xsd file: " + CommonUtilities.getCurrentPath(MATSim4Urbansim.class) + "tmp/MATSim4UrbanSimConfigSchema.xsd");
				
				file2XSD = new File(CommonUtilities.getCurrentPath(MATSim4Urbansim.class) + "tmp/MATSim4UrbanSimConfigSchema.xsd");
				if(!file2XSD.exists()){
					log.error(file2XSD.getCanonicalPath() + " not found!!!");
					return false;
				}
			}
			log.info("Using following xsd schema: " + file2XSD.getCanonicalPath());
			// create a schema object via the given xsd to validate the MATSim xml config.
			Schema schema = schemaFactory.newSchema(file2XSD);
			// set the schema for validation while reading/importing the MATSim xml config.
			unmarschaller.setSchema(schema);
			
			File inputFile = new File( matsimConfigFile );
			if(!inputFile.exists())
				log.error(inputFile.getCanonicalPath() + " not found!!!");
			// contains the content of the MATSim config.
			Object object = unmarschaller.unmarshal(inputFile);
			
			// Java representation of the schema file.
			MatsimConfigType matsimConfig;
			
			// The structure of both objects must match.
			if(object.getClass() == MatsimConfigType.class)
				matsimConfig = (MatsimConfigType) object;
			else
				matsimConfig = (( JAXBElement<MatsimConfigType>) object).getValue();
			
			// creatin MASim config object that contains the values from the xml config file.
			if(matsimConfig != null){
				log.info("Creating new MATSim config object to store the values from the xml configuration.");
				MATSimConfigObject.initMATSimConfigObject(matsimConfig, true);
			}
		}
		catch(JAXBException je){
			je.printStackTrace();
			return false;
		}
		catch(SAXException se){
			se.printStackTrace();
			return false;
		}
		catch(IOException ioe){
			ioe.printStackTrace();
			return false;
		}
		catch(Exception e){
			e.printStackTrace();
			return false;
		}
		log.info("... finished unmarschallig");
		return true;
	}

}

