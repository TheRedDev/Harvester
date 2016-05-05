/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.util;

import java.io.IOException;
import java.util.HashSet;
import javax.activation.MimetypesFileTypeMap;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;

/**
 * This Class takes the images directory and segregates them in to two folders upload and backup
 * @author Sivananda Reddy Thummala Abbigari, 64254635
 * @author Christopher Haines (chris@chrishaines.net) - rewriten for harvester v2 using harvester tools
 */
public class TransferImages {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(TransferImages.class);
	
	/**
	 * Path to the ImageScript directory
	 */
	private String pathToImageScriptDirectory;
	
	/**
	 * Path to the EmpoyeeID's file
	 */
	private String pathToEmployeeIDsFile;
	
	/**
	 * Contains the list of Employee ID's who doesn't have images in VIVO
	 */
	private HashSet<String> employeeIDSet;
	
	/**
	 * Used for reading files the images directory
	 */
	private String folder;
	
	/**
	 * only operate on image mime types
	 */
	private boolean checkImageMIMEType;
	
	/**
	 * Command line Constructor
	 * @param args command line arguments
	 * @throws IOException error creating task
	 * @throws UsageException user requested usage message
	 */
	private TransferImages(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * ArgList Constructor
	 * @param argList option set of parsed args
	 */
	private TransferImages(ArgList argList) {
		this(argList.get("p"), argList.get("e"), argList.has("m"));
	}
	
	/**
	 * Library style Constructor
	 * @param pathToImageScriptFolder path to folder containing image scripts
	 * @param pathToEmployeeIDsFile path to file containing employee ids
	 * @param mimeTypeCheck only operate on image mime types
	 */
	public TransferImages(String pathToImageScriptFolder, String pathToEmployeeIDsFile, boolean mimeTypeCheck) {
		this.pathToImageScriptDirectory = pathToImageScriptFolder;
		this.pathToEmployeeIDsFile = pathToEmployeeIDsFile;
		this.employeeIDSet = new HashSet<String>();
		this.folder = this.pathToImageScriptDirectory + "/fullImages";
		this.checkImageMIMEType = mimeTypeCheck;
	}
	
	/**
	 * Library style Constructor
	 * @param pathToImageScriptFolder path to folder containing image scripts
	 * @param pathToEmployeeIDsFile path to file containing employee ids
	 */
	public TransferImages(String pathToImageScriptFolder, String pathToEmployeeIDsFile) {
		this(pathToImageScriptFolder, pathToEmployeeIDsFile, false);
	}
	
	/**
	 * Executes
	 * @throws IOException error reading files
	 */
	public void execute() throws IOException {
		for(String line : StringUtils.split(FileAide.getTextContent(this.pathToEmployeeIDsFile), System.lineSeparator())) {
			this.employeeIDSet.add(line.substring(0, 8));
		}
		for(String f : FileAide.getChildren(this.folder)) {
			if(this.checkImageMIMEType && new MimetypesFileTypeMap().getContentType(f).contains("image")) {
				String fileName = FileAide.getFileName(f);
				if(this.employeeIDSet.contains(fileName.substring(0, 8))) {
					FileAide.moveFile(this.pathToImageScriptDirectory + "/fullImages/" + fileName, this.pathToImageScriptDirectory + "/upload/fullImages/" + fileName);
					FileAide.moveFile(this.pathToImageScriptDirectory + "/thumbnails/" + fileName, this.pathToImageScriptDirectory + "/upload/thumbnails/" + fileName);
				} else {
					FileAide.moveFile(this.pathToImageScriptDirectory + "/fullImages/" + fileName, this.pathToImageScriptDirectory + "/backup/fullImages/" + fileName);
					FileAide.moveFile(this.pathToImageScriptDirectory + "/thumbnails/thumbnail" + fileName, this.pathToImageScriptDirectory + "/backup/thumbnails/thumbnail" + fileName);
				}
			}
		}
		log.info("Transfered images to upload and backup directories!");
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("TransferImages");
		parser.addArgument(new ArgDef().setShortOption('p').setLongOpt("pathToImageScriptDirectory").withParameter(true, "PATH").setDescription("path to the Image Script Directory").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('e').setLongOpt("pathToEmployeedIDsFile").withParameter(true, "PATH").setDescription("path to the EmployeeID's file").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('m').setLongOpt("checkImageMimeType").setDescription("only operate on image mime types").setRequired(true));
		return parser;
	}
	
	/**
	 * Main method
	 * @param args command line arguments
	 */
	public static void main(String... args) {
		Exception error = null;
		try {
			InitLog.initLogger(args, getParser());
			log.info(getParser().getAppName() + ": Start");
			new TransferImages(args).execute();
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