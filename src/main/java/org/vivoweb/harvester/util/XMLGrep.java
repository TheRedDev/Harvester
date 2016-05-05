package org.vivoweb.harvester.util;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.apache.commons.lang.StringUtils;

/**
 * @author kuppuraj, Mayank Saini
 *
 */
public class XMLGrep {
	
	/**
	 * 
	 */
	protected static Logger log = LoggerFactory.getLogger(XMLGrep.class);
	/**
	 * Input src directory
	 */
	private String src;
	/**
	 * Input dest directory
	 */
	private String dest = "";
	
	/**
	 * Input alternate destination for items that do not match the expression
	 */
	private String altDest = "";
	
	/**
	 * Destination for items with malformed XML or which generate errors.
	 */
	private String errorDest = "";
	
	/**
	 * xpath expression to filter files
	 */
	private String expression;
	
	/**
	 * Constructor
	 * @param src directory to read files from
	 * @param dest directory to move files from
	 * @param altDest Input alternate destination for items that do not match the expression
	 * @param errorDest Destination for items with malformed XML or which generate errors
	 * @param value base for xpath expression to filter files
	 * @param name name to put into xpath expression
	 */
	public XMLGrep(String src, String dest, String altDest, String errorDest, String value, String name) {
		this.src = src;
		this.dest = dest;
		this.errorDest = errorDest;
		
		if(altDest != null) {
			this.altDest = altDest;
		}
		
		if(value == null) {
			this.expression = "//" + name;
		} else {
			this.expression = "//" + MathAide.nvl(name, "*") + "[. = '" + value + "']";
		}
	}
	
	/**
	 * Constructor
	 * @param args commandline arguments
	 * @throws IOException error creating task
	 * @throws UsageException user requested usage message
	 */
	private XMLGrep(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor
	 * @param argList arguments
	 */
	private XMLGrep(ArgList argList) {
		this(argList.get("s"), argList.get("d"), argList.get("a"), argList.get("e"), argList.get("v"), argList.get("n"));
	}
	
	/**
	 * Find the pattern within a specific file
	 * @param myFile the file to search within
	 * @param exp the expression to search for
	 * @return boolean true if found, false otherwise
	 * @throws IOException error reading file
	 */
	public boolean findInFile(String myFile, String exp) throws IOException {
		try {
			return StringUtils.isNotEmpty(XMLAide.getXPathResult(myFile, exp));
		} catch(IOException e) {
			log.error("Exception in XPathTool: Malformed XML, or bad Parser Configuration", e);
			log.debug("Moving offending file: " + myFile + " to error destination: " + this.errorDest);
			FileAide.moveFile(myFile, this.errorDest);
			return false;
		} catch(IllegalArgumentException e) {
			log.error("Exception in XPathTool: Invalid XPath Expression.");
			return false;
		}
	}
	
	/**
	 * Runs the XMLGrep
	 */
	// TODO: Clean up logic and try-catch blocks. Document. Stopgap solution implemented quickly. -RPZ 08/15/2012
	public void execute() {
		try {
			if(FileAide.isFolder(this.src)) {
				for(String file : FileAide.getChildren(this.src)) {
					try {
						// If the current file is a directory, skip it
						if(!FileAide.isFolder(file)) {
							if(findInFile(file, this.expression)) {
								//If the current file matches the xpath expression then move it
								FileAide.moveFile(file, this.dest);
							} else {
								//Check for case where no altDest provided, or altDest = srcDest
								if(this.src.equals(this.altDest) || this.altDest.equals("")) {
									// Ignore the file, as we wish to leave it in place.
								} else if(this.altDest != null) {
									//If the current file does not match the xpath expression then
									//check to see if there is an alternate destination defined
									//Protect the file from trying to move if error in Parsing caused it to move already.
									if(FileAide.isFile(file)) {
										FileAide.moveFile(file, this.altDest);
									}
								}
							}
						}
					} catch(IOException e) {
						log.error("Error: ", e);
						continue;
					}
				}
			} else if(FileAide.isFile(this.src)) {
				if(findInFile(this.src, this.expression)) {
					FileAide.moveFile(this.src, this.dest);
				}
			}
		} catch(IOException e) {
			log.error("Error: ", e);
		}
		
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("XMLGrep");
		parser.addArgument(new ArgDef().setShortOption('s').setLongOpt("src-dir").withParameter(true, "SRC_DIRECTORY").setDescription("SRC directory to read files from").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('d').setLongOpt("dest-dir").withParameter(true, "DEST_DIRECTORY").setDescription("DEST directory to write files to").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('a').setLongOpt("alt-dest").withParameter(true, "ALT_DESTINATION_DIRECTORY").setDescription("Alternate destination for files that failed to match expression").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('e').setLongOpt("err-dest").withParameter(true, "ERROR_DESTINATION_DIR").setDescription("Destination for malformed or exception generating files").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('n').setLongOpt("tag-name").withParameter(true, "TAG_NAME").setDescription("TAG Name to Search for").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('v').setLongOpt("tag-value").withParameter(true, "TAG_VALUE").setDescription("TAG value to Search for").setRequired(false));
		return parser;
	}
	
	/**
	 * Main method
	 * @param args commandline arguments
	 */
	public static void main(String... args) {
		Exception error = null;
		try {
			String harvLev = System.getProperty("console-log-level");
			System.setProperty("console-log-level", "OFF");
			InitLog.initLogger(args, getParser(), "h");
			if(harvLev == null) {
				System.clearProperty("console-log-level");
			} else {
				System.setProperty("console-log-level", harvLev);
			}
			log.info(getParser().getAppName() + ": Start");
			new XMLGrep(args).execute();
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