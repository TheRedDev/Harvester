/*******************************************************************************
 * Copyright (c) 2010-2015 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.diff;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.vivoweb.harvester.util.repo.Record;
import org.vivoweb.harvester.util.repo.RecordHandler;

/**
 * Two RecordHandlers and creates Three RecordHandlers.
 * One for 'Added Records', another for 'Deleted Records', and a final for 'Changed Records'.
 * Unchanged, matching records don't go into any of the three output RecordHandlers.
 * @author Christopher Haines chris@chrishaines.net
 */
public class RecordHandlerDiff {
	/**
	 * the log property for logging errors, information, debugging
	 */
	private static Logger log = LoggerFactory.getLogger(RecordHandlerDiff.class);
	/**
	 * record handler for old records
	 */
	protected RecordHandler oldStore;
	/**
	 * record handler for new records
	 */
	protected RecordHandler newStore;
	/**
	 * record handler for added records
	 */
	protected RecordHandler addStore;
	/**
	 * record handler for deleted records
	 */
	protected RecordHandler delStore;
	/**
	 * record handler for changed records
	 */
	protected RecordHandler chgStore;
	/**
	 * force process records, clearing the three output RecordHandlers
	 */
	private boolean force;
	
	/**
	 * Constructor
	 * @param args commandline arguments
	 * @throws IOException error creating task
	 * @throws UsageException user requested usage message
	 */
	private RecordHandlerDiff(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor
	 * @param argList <ul>
	 *        <li>oldRecordHandler the old records</li>
	 *        <li>newRecordHandler the new records</li>
	 *        <li>addRecordHandler the added records</li>
	 *        <li>delRecordHandler the deletd records</li>
	 *        <li>chgRecordHandler the changed records</li>
	 *        <li>force process all records, clearing the three output records</li>
	 *        </ul>
	 * @throws IOException error reading files
	 */
	private RecordHandlerDiff(ArgList argList) throws IOException {
		this(
			RecordHandler.parseConfig(argList.get("o"), argList.getValueMap("O")), 
			RecordHandler.parseConfig(argList.get("n"), argList.getValueMap("N")), 
			RecordHandler.parseConfig(argList.get("a"), argList.getValueMap("A")), 
			RecordHandler.parseConfig(argList.get("d"), argList.getValueMap("D")), 
			RecordHandler.parseConfig(argList.get("c"), argList.getValueMap("C")), 
			argList.has("f")
		);
	}
	
	/**
	 * Constructor
	 * @param oldRecordHandler the old records
	 * @param newRecordHandler the new records
	 * @param addRecordHandler the added records
	 * @param delRecordHandler the deletd records
	 * @param chgRecordHandler the changed records
	 * @param force process all records, clearing the three output records
	 */
	public RecordHandlerDiff(
		RecordHandler oldRecordHandler, 
		RecordHandler newRecordHandler, 
		RecordHandler addRecordHandler, 
		RecordHandler delRecordHandler, 
		RecordHandler chgRecordHandler, 
		boolean force
	) {
		// create record handlers
		this.oldStore = oldRecordHandler;
		this.newStore = newRecordHandler;
		this.addStore = addRecordHandler;
		this.delStore = delRecordHandler;
		this.chgStore = chgRecordHandler;
		this.force = force;
		if(this.oldStore == null) {
			throw new IllegalArgumentException("Must provide an 'old' record handler");
		}
		if(this.newStore == null) {
			throw new IllegalArgumentException("Must provide a 'new' record handler");
		}
		if(this.addStore == null) {
			throw new IllegalArgumentException("Must provide an 'added' record handler");
		}
		if(this.delStore == null) {
			throw new IllegalArgumentException("Must provide a 'deleted' record handler");
		}
		if(this.chgStore == null) {
			throw new IllegalArgumentException("Must provide a 'changed' record handler");
		}
	}
	
	/**
	 * checks again for the necessary file and makes sure that they exist
	 * @throws IOException error processing
	 */
	public void execute() throws IOException {
		// get from the in record and translate
//		int oldRecordCount = 0;
//		int newRecordCount = 0;
//		int matchedRecordCount = 0;
//		int addedRecordCount = 0;
//		int deletedRecordCount = 0;
//		int changedRecordCount = 0;
//		int alreadyProcessedCount = 0;
//		String recordData;
		if(this.force) {
			this.addStore.truncate();
			this.delStore.truncate();
			this.chgStore.truncate();
		}
		for(Record r : this.oldStore) {
			if(this.force || r.needsProcessed(this.getClass())) {
				log.trace("Processing Old Record " + r.getID());
				
				r.setProcessed(this.getClass());
//				oldRecordCount++;
			} else {
				log.trace("No Processing Needed: " + r.getID());
//				alreadyProcessedCount++;
			}
		}
//		log.info(Integer.toString(translated) + " records translated.");
//		log.info(Integer.toString(passed) + " records did not need translation");
	}
	
	/**
	 * using the javax xml transform factory this method uses the xsl to translate XML into the desired format
	 * designated in the xsl.
	 * @param inStream the input stream
	 * @param outStream the output stream
	 * @param translationStream the stream for the xsl
	 * @throws IOException error translating
	 */
	public static void xmlTranslate(InputStream inStream, OutputStream outStream, InputStream translationStream) throws IOException {
		StreamResult outputResult = new StreamResult(outStream);
		// JAXP reads data using the Source interface
		Source xmlSource = new StreamSource(inStream);
		Source xslSource = new StreamSource(translationStream);
		try {
			// the factory pattern supports different XSLT processors
			// this outputs to outStream (through outputResult)
			TransformerFactory.newInstance("net.sf.saxon.TransformerFactoryImpl", null).newTransformer(xslSource).transform(xmlSource, outputResult);
		} catch(TransformerConfigurationException e) {
			throw new IOException(e);
		} catch(TransformerException e) {
			throw new IOException(e);
		}
		outStream.flush();
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("XSLTranslator");
		parser.addArgument(new ArgDef().setShortOption('i').setLongOpt("input").withParameter(true, "CONFIG_FILE").setDescription("config file for input record handler").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('I').setLongOpt("inputOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of input recordhandler using VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('o').setLongOpt("output").withParameter(true, "CONFIG_FILE").setDescription("config file for output record handler").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('O').setLongOpt("outputOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of output recordhandler using VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('x').setLongOpt("xslFile").withParameter(true, "XSL_FILE").setDescription("xsl file").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('f').setLongOpt("force").setDescription("force translation of all input records, even if previously processed").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('c').setLongOpt("cleanXML").setDescription("Decode and sanitize XML").setRequired(false));
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
			new RecordHandlerDiff(args).execute();
		} catch(IllegalArgumentException e) {
			log.error(e.getMessage());
			log.debug("Stacktrace:",e);
			System.out.println(getParser().getUsage());
			error = e;
		} catch(UsageException e) {
			log.info("Printing Usage:");
			System.out.println(getParser().getUsage());
			error = e;
		} catch(Exception e) {
			log.error(e.getMessage());
			log.debug("Stacktrace:",e);
			error = e;
		} finally {
			log.info(getParser().getAppName() + ": End");
			if(error != null) {
				System.exit(1);
			}
		}
	}
	
}
