package org.vivoweb.harvester.util;

import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;

/**
 * Log specified message to log files
 * @author Christopher Haines (chris@chrishaines.net)
 */
public class LogEcho {
	
	/**
	 * Log Writer
	 */
	protected static Logger log = LoggerFactory.getLogger(LogEcho.class);
	/**
	 * message to write to log files
	 */
	private String message = null;
	/**
	 * level at which to log message
	 */
	private String logLevel = null;
	
	/**
	 * Constructor
	 * @param message message to write to log files
	 * @param logLevel level at which to log message (blank value -> INFO)
	 */
	public LogEcho(String message, String logLevel) {
		this.message = message;
		this.logLevel = logLevel;
		
		if(StringUtils.isBlank(this.logLevel)) {
			this.logLevel = getParser().getDefaultValue("l");
		}
	}
	
	/**
	 * Constructor
	 * @param args commandline arguments
	 * @throws IOException error creating task
	 * @throws UsageException user requested usage message
	 */
	private LogEcho(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor
	 * @param argList arguments
	 */
	private LogEcho(ArgList argList) {
		this(argList.get("m"), argList.get("l"));
	}

	/**
	 * Execute
	 */
	private void execute() {
		if(this.logLevel.equalsIgnoreCase("info")) {
			log.info(this.message);
		} else if(this.logLevel.equalsIgnoreCase("debug")) {
			log.debug(this.message);
		} else if(this.logLevel.equalsIgnoreCase("error")) {
			log.error(this.message);
		} else if(this.logLevel.equalsIgnoreCase("warn")) {
			log.warn(this.message);
		} else if(this.logLevel.equalsIgnoreCase("trace")) {
			log.trace(this.message);
		} else {
			log.error("unknown log level: "+this.logLevel);
			log.info(this.message);
		}
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("LogEcho");
		parser.addArgument(new ArgDef().setShortOption('m').setLongOpt("message").withParameter(true, "MESSAGE").setDescription("Message to write to log file").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('l').setLongOpt("level").withParameter(true, "MESSAGE_LOG_LEVEL").setDescription("Level at which to log message").setRequired(false).setDefaultValue("INFO"));
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
			new LogEcho(args).execute();
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
			if(error != null) {
				System.exit(1);
			}
		}
	}
	
}