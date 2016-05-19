/*******************************************************************************
 * Copyright (c) 2010-2015 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.util.repo;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;

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
			RecordHandler.parseConfig(argList.get("c"), argList.getValueMap("C"))
		);
	}
	
	/**
	 * Constructor
	 * @param oldRecordHandler the old records
	 * @param newRecordHandler the new records
	 * @param addRecordHandler the added records
	 * @param delRecordHandler the deleted records
	 * @param chgRecordHandler the changed records
	 */
	public RecordHandlerDiff(
		RecordHandler oldRecordHandler, 
		RecordHandler newRecordHandler, 
		RecordHandler addRecordHandler, 
		RecordHandler delRecordHandler, 
		RecordHandler chgRecordHandler
	) {
		// create record handlers
		this.oldStore = oldRecordHandler;
		this.newStore = newRecordHandler;
		this.addStore = addRecordHandler;
		this.delStore = delRecordHandler;
		this.chgStore = chgRecordHandler;
		if(this.oldStore == null) {
			throw new IllegalArgumentException("Must provide an 'old' record handler");
		}
		if(this.newStore == null) {
			throw new IllegalArgumentException("Must provide a 'new' record handler");
		}
		if(this.addStore == null) {
			log.warn("No 'added' Record Handler provided, so added records will go nowhere");
		}
		if(this.delStore == null) {
			log.warn("No 'deleted' Record Handler provided, so deleted records will go nowhere");
		}
		if(this.chgStore == null) {
			log.warn("No 'changed' Record Handler provided, so changed records will go nowhere");
		}
	}
	
	/**
	 * checks again for the necessary file and makes sure that they exist
	 * @throws IOException error processing
	 */
	public void execute() throws IOException {
		int addCount = 0;
		int chgCount = 0;
		int delCount = 0;
		int uncCount = 0;
		for(Record oldRec : this.oldStore) {
			if(this.newStore.getRecordIDs().contains(oldRec.getID())) {
				Record newRec = this.newStore.getRecord(oldRec.getID());
				if(this.oldStore.needsUpdated(newRec)) {
					if(this.chgStore != null) {
						log.trace("Processing Changed record: "+oldRec.getID());
						this.chgStore.addRecord(oldRec.getID(), oldRec.getData(), RecordHandlerDiff.class);
					} else {
						log.trace("Discarding Changed record: "+oldRec.getID());
					}
					chgCount++;
				} else {
					log.trace("Ignoring Unchanged record: "+oldRec.getID());
					uncCount++;
				}
			} else {
				if(this.delStore != null) {
					log.trace("Processing Deleted record: "+oldRec.getID());
					this.delStore.addRecord(oldRec.getID(), oldRec.getData(), RecordHandlerDiff.class);
				} else {
					log.trace("Discarding Deleted record: "+oldRec.getID());
				}
				delCount++;
			}
		}
		for(Record newRec : this.newStore) {
			if(!this.oldStore.getRecordIDs().contains(newRec.getID())) {
				if(this.addStore != null) {
					log.trace("Processing Added record: "+newRec.getID());
					this.addStore.addRecord(newRec.getID(), newRec.getData(), RecordHandlerDiff.class);
				} else {
					log.trace("Discarding Added record: "+newRec.getID());
				}
				addCount++;
			}
		}
		if(addCount > 0) {
			if(this.addStore != null) {
				log.info("Processed "+addCount+" Added Records");
			} else {
				log.info("Discarded "+addCount+" Added Records");
			}
		}
		if(chgCount > 0) {
			if(this.chgStore != null) {
				log.info("Processed "+chgCount+" Changed Records");
			} else {
				log.info("Discarded "+chgCount+" Changed Records");
			}
		}
		if(delCount > 0) {
			if(this.delStore != null) {
				log.info("Processed "+delCount+" Deleted Records");
			} else {
				log.info("Discarded "+delCount+" Deleted Records");
			}
		}
		if(uncCount > 0) {
			log.info("Ignored "+uncCount+" Unchanged Records");
		}
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("RecordHandlerDiff");
		parser.addArgument(new ArgDef().setShortOption('o').setLongOpt("old").withParameter(true, "CONFIG_FILE").setDescription("config file for old record handler").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('O').setLongOpt("oldOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of old recordhandler using VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('n').setLongOpt("new").withParameter(true, "CONFIG_FILE").setDescription("config file for new record handler").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('N').setLongOpt("newOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of new recordhandler using VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('a').setLongOpt("added").withParameter(true, "CONFIG_FILE").setDescription("config file for added record handler").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('A').setLongOpt("addedOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of added recordhandler using VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('d').setLongOpt("deleted").withParameter(true, "CONFIG_FILE").setDescription("config file for deleted record handler").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('D').setLongOpt("deletedOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of deleted recordhandler using VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('c').setLongOpt("changed").withParameter(true, "CONFIG_FILE").setDescription("config file for changed record handler").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('C').setLongOpt("changedOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of changed recordhandler using VALUE").setRequired(false));
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
