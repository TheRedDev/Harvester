/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.util.repo;

import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.MathAide;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.vivoweb.harvester.util.repo.RecordMetaData.RecordMetaDataType;

/**
 * Transfer records from one Record Handler to another
 * @author Christopher Haines (chris@chrishaines.net)
 */
public class RecordTransfer {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(RecordTransfer.class);
	/**
	 * input record handler
	 */
	private RecordHandler inRH;
	/**
	 * output record handler
	 */
	private RecordHandler outRH;
	/**
	 * remove rather than add
	 */
	private boolean removeMode;
	/**
	 * prefix added on the output record ids
	 */
	private String outputPrefix;
	/**
	 * prefix removed from the input record ids
	 */
	private String inputPrefix;
	/**
	 * overwrite existing records
	 */
	private boolean overwriteMode;
	
	/**
	 * Constructor
	 * @param args commandline arguments
	 * @throws IOException error parsing options
	 * @throws UsageException user requested usage message
	 */
	private RecordTransfer(String... args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor
	 * @param argList parsed argument list
	 * @throws IOException error creating task
	 */
	private RecordTransfer(ArgList argList) throws IOException {
		// load data from record handler
		this.inRH = RecordHandler.parseConfig(argList.get("i"), argList.getValueMap("I"));
		
		// load data into record handler
		this.outRH = RecordHandler.parseConfig(argList.get("o"), argList.getValueMap("O"));
		
		// remove mode
		this.removeMode = argList.has("m");
		this.overwriteMode = argList.has("f");
		this.inputPrefix = MathAide.nvl(argList.get("p"),"");
		this.outputPrefix = MathAide.nvl(argList.get("q"),"");
		
		// Require input args
		if(this.inRH == null) {
			throw new IllegalArgumentException("Must provide a valid input {-i or -I}");
		}
		
		// Require output args
		if(this.outRH == null) {
			throw new IllegalArgumentException("Must provide a valid output {-o or -O}");
		}
	}
	
	/**
	 * Get the output record id by stripping off the input prefix (if provided) and prepending the output prefix (if provided)
	 * @param inRecID the input record id
	 * @return the output record id
	 */
	private String getOutRecId(String inRecID) {
		return this.outputPrefix+StringUtils.removeStart(inRecID, this.inputPrefix);
	}
	
	/**
	 * Copy data from input to output
	 * @throws IOException error
	 */
	private void execute() throws IOException {
		if(this.removeMode) {
			log.info("Removing input record handler rdf records from output record handler");
			int processed = 0;
			for(String inRecID : this.inRH.getRecordIDs()) {
				this.outRH.delRecord(getOutRecId(inRecID));
				processed++;
			}
			log.info("Removed " + processed + " records");
		} else {
			log.info("Loading input record handler rdf records into output record handler");
			int processed = 0;
			for(Record r : this.inRH) {
				String inRecID = r.getID();
				String outRecID = getOutRecId(inRecID);
				this.outRH.addRecord(outRecID, r.getData(), RecordTransfer.class, this.overwriteMode);
				this.outRH.delMetaData(outRecID);
				Record outRec = this.outRH.getRecord(outRecID);
				for(RecordMetaData rmd : this.inRH.getRecordMetaData(inRecID)) {
					this.outRH.addMetaData(outRec, rmd);
				}
				this.outRH.addMetaData(outRec, RecordTransfer.class, RecordMetaDataType.transferred);
				processed++;
			}
			log.info("Loaded " + processed + " records");
		}
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("RecordTransfer");
		// Inputs
		parser.addArgument(new ArgDef().setShortOption('i').setLongOpt("input").withParameter(true, "CONFIG_FILE").setDescription("config file for record handler to load into output").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('I').setLongOpt("inputOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of input record handler config using VALUE").setRequired(false));
		// Outputs
		parser.addArgument(new ArgDef().setShortOption('o').setLongOpt("output").withParameter(true, "CONFIG_FILE").setDescription("config file for output record handler").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('O').setLongOpt("outputOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of output record handler config using VALUE").setRequired(false));
		// Params
		parser.addArgument(new ArgDef().setShortOption('p').setLongOpt("inputPrefix").withParameter(true, "PREFIX").setDescription("prefix added on the output record ids").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('q').setLongOpt("outputPrefix").withParameter(true, "PREFIX").setDescription("prefix removed from the input record ids").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('f').setLongOpt("forceOverwrite").setDescription("force overwrite of existing records in output").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('m').setLongOpt("modeRemove").setDescription("remove from output recordhandler rather than add").setRequired(false));
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
			new RecordTransfer(args).execute();
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
