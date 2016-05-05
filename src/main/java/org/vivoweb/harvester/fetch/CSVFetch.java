/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 * -- several methods in this class are borrowed from the VIVO Csv2Rdf.java file written by Cornell University
 ******************************************************************************/
package org.vivoweb.harvester.fetch;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.qualify.ChangeNamespace;
import org.vivoweb.harvester.util.FileAide;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.vivoweb.harvester.util.repo.JenaConnect;
import java.io.InputStream;
import org.skife.csv.CSVReader;
import org.skife.csv.SimpleReader;
import org.apache.jena.ontology.DatatypeProperty;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * Fetches data from a CSV file and uses the VIVO CSV import parameters to load RDF Data
 * into a triple store model.
 * @author Stephen V. Williams (svwilliams@gmail.com), Christopher Haines (chris@chrishaines.net)
 */
public class CSVFetch {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(CSVFetch.class);
	
	/**
	 * output jena model
	 */
	private JenaConnect ouputJC;
	
	/**
	 * csv file to be processed
	 */
	private String file;
	
	/**
	 * the namespace for all individuals created
	 */
	private String namespace;
	
	/**
	 * the namespace for all properties created
	 */
	private String propertyNamespace;
	
	/**
	 * the class to assign each row to
	 */
	private String typeName;
	
	/**
	 * the namespace of the uri for each subject (may be different due to computer value)
	 */
	private String individualNameBase;
	
	/**
	 * the pattern to assign before (such as fis) the unique identifier
	 */
	private String propertyNameBase;
	
	/**
	 * the field (if used) to draw the unique number from
	 */
	private String uriProperty;
	
	/**
	 * the value used to seperate columns (usually a ',')
	 */
	private char separatorChar;
	
	/**
	 * index for the uri property
	 */
	private int indexOfURIProp = -1;
	
	/**
	 * @param filename the csv file to process
	 * @param namespace the namespace for all properties and 
	 * @param output the output model
	 */
	public CSVFetch(String filename, String namespace, JenaConnect output) {
		this(filename, ',', namespace, "http://www.w3.org/2002/07/owl#Thing", namespace, null, null, output);
	}
	
	/**
	 * Command line Constructor
	 * @param args commandline arguments
	 * @throws IOException error creating task
	 * @throws UsageException user requested usage message
	 */
	private CSVFetch(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Arglist Constructor
	 * @param args option set of parsed args
	 * @throws IOException io error with jena connect
	 */
	private CSVFetch(ArgList args) throws IOException {
		this(
			args.get("f"),
			args.getChar("s"),
			args.get("n"),
			args.get("t"),
			args.get("u"),
			args.get("b"),
			args.get("p"),
			JenaConnect.parseConfig(args.get("o"), args.getValueMap("O"))
		);
	}
	
	/**
	 * Library style Constructor
	 * @param fileName the file path to the CSV file
	 * @param separatorValue the value used to seperate columns (usually a ',')
	 * @param namespace the namespace for all of the properties created
	 * @param localClass the class to assign each row to
	 * @param uriPrefix the namespace of the uri for each subject (may be different due to computer value)
	 * @param uriPattern the pattern to assign before (such as fis) the unique identifier
	 * @param uriField  the field (if used) to draw the unique number from
	 * @param output the jennaconnect to a model to output the new RDF
	 */
	public CSVFetch(String fileName, char separatorValue, String namespace, String localClass, String uriPrefix, String uriPattern, String uriField, JenaConnect output) {
		this.ouputJC = output;
		this.separatorChar = separatorValue;
		this.namespace = namespace;
		this.propertyNamespace = namespace;
		this.file = fileName;
		this.typeName = localClass;
		if(uriPrefix == null) {
			this.individualNameBase = "";
		} else {
			this.individualNameBase = uriPrefix;
		}
		this.propertyNameBase = uriPattern;
		this.uriProperty = uriField;
	}
	
	/**
	 * Execute
	 * @throws IOException error reading/parsing input file or writing to record handler
	 */
	public void execute() throws IOException {
		InputStream fis = FileAide.getInputStream(this.file);
		Model destination = this.ouputJC.getJenaModel();
		
		OntModel ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
		OntModel tboxOntModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
		ontModel.addSubModel(tboxOntModel);
		OntClass theClass = tboxOntModel.createClass(this.propertyNamespace + this.typeName);
		
		CSVReader cReader = new SimpleReader();
		cReader.setSeperator(this.separatorChar);
		//cReader.setQuoteCharacters(this.quoteChars);	
		/*
		URIGenerator uriGen = (wadf != null && destination != null) 
				? new RandomURIGenerator(wadf, destination)
		       : new SequentialURIGenerator();
		*/
		@SuppressWarnings("unchecked")
		List<String[]> fileRows = cReader.parse(fis);
		
		String[] columnHeaders = fileRows.get(0);
		
		DatatypeProperty[] dpArray = new DatatypeProperty[columnHeaders.length];
		
		for(int i = 0; i < columnHeaders.length; i++) {
			dpArray[i] = tboxOntModel.createDatatypeProperty(this.propertyNamespace + this.propertyNameBase + columnHeaders[i].replaceAll("\\W", ""));
			
			//setting the column id to generate URI
			if(this.uriProperty != null && this.uriProperty.equals(columnHeaders[i].toString())) {
				this.indexOfURIProp = i;
			}
			System.out.println(dpArray[i].toString());
		}
		
		Individual ind = null;
		for(int row = 1; row < fileRows.size(); row++) {
			
			String[] cols = fileRows.get(row);
			if(this.indexOfURIProp != -1) {
				String uri = this.namespace + this.individualNameBase + cols[this.indexOfURIProp].trim();
				ind = ontModel.createIndividual(uri, theClass);
			} else {
				String uri = ChangeNamespace.getUnusedURI(this.namespace + "/individual/n", ontModel);
				ind = ontModel.createIndividual(uri, theClass);
			}
			for(int col = 0; col < cols.length; col++) {
				String value = cols[col].trim();
				if(value.length() > 0) {
					ind.addProperty(dpArray[col], value); // no longer using: , XSDDatatype.XSDstring);
					// TODO: specification of datatypes for columns
				}
			}
		}
		
		ontModel.removeSubModel(tboxOntModel);
		
		//Model[] resultModels = new Model[2];
		//resultModels[0] = ontModel;
		//resultModels[1] = tboxOntModel;
		destination.add(ontModel);
		destination.add(tboxOntModel);
		System.out.println(this.ouputJC.exportRdfToString());
		//return resultModels;
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("CSVFetch");
		parser.addArgument(new ArgDef().setShortOption('f').setLongOpt("file").withParameter(true, "CSV_FILE").setDescription("csv file to import").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('s').setLongOpt("seperated").withParameter(true, "SEP_VARIABLE").setDescription("seperation method, automatically set to ',' ").setDefaultValue(",").setRequired(false));
		
		parser.addArgument(new ArgDef().setShortOption('n').setLongOpt("namespace").withParameter(true, "NAMESPACE_BASE").setDescription("the base namespace to use for each node created").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('t').setLongOpt("type").withParameter(true, "LOCAL_CLASS").setDescription("local name of the class that all rows will be set as").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('u').setLongOpt("uriPrefix").withParameter(true, "URI_PREFIX").setDescription("the prefix to add infront of the uri").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('b').setLongOpt("propertyPrefix").withParameter(true, "URI_PATTERN_BASE").setDescription("the field to base the URI on").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('p').setLongOpt("uriParameter").withParameter(true, "URI_PATTERN_BASE").setDescription("the ur pattern base").setRequired(false));
		
		parser.addArgument(new ArgDef().setShortOption('o').setLongOpt("output").withParameter(true, "CONFIG_FILE").setDescription("RecordHandler config file path").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('O').setLongOpt("outputOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of output recordhandler using VALUE").setRequired(false));
		
		return parser;
	}
	
	/**
	 * Main method
	 * @param args commandline arguments
	 */
	public static void main(String... args) {
		Exception error = null;
		try {
			InitLog.initLogger(args, getParser());
			log.info(getParser().getAppName() + ": Start");
			new CSVFetch(args).execute();
		} catch(IllegalArgumentException e) {
			log.error(e.getMessage());
			log.debug("Stacktrace:", e);
			System.out.println(getParser().getUsage());
			error = e;
		} catch(UsageException e) {
			log.info("Printing Usage:");
			System.out.println(getParser().getUsage());
			error = e;
		} catch(Exception e) {
			log.error(e.getMessage());
			log.debug("Stacktrace:", e);
			error = e;
		} finally {
			log.info(getParser().getAppName() + ": End");
			if(error != null) {
				System.exit(1);
			}
		}
	}
}
