/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.fetch;

import java.io.IOException;
import java.util.List;
import net.minidev.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.FileAide;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.SpecialEntities;
import org.vivoweb.harvester.util.WebAide;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.vivoweb.harvester.util.repo.RecordHandler;
import org.vivoweb.harvester.util.repo.RecordStreamOrigin;
import org.vivoweb.harvester.util.repo.XMLRecordOutputStream;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;

/**
 * Class for harvesting from JSON Data Sources
 * @author Dale Scheppler
 * @author Christopher Haines (chris@chrishaines.net)
 */
public class JSONFetch implements RecordStreamOrigin {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(JSONFetch.class);
	
	/**
	 * The website address of the JSON source without the protocol prefix (No http://)
	 */
	private String strAddress;
	
	/**
	 * The record handler to write records to
	 */
	private RecordHandler rhOutput;
	
	/**
	* Namespace for RDF made from this database
	*/
	private String uriNS;
	
	/**
	 * mapping of node descriptions
	 */
	private String nodeNames[];
	
	/**
	 * The user defined json path strings
	 */
	private String pathStrings[];
	
	/**
	 * the base for each instance's xmlRos
	 */
	private static XMLRecordOutputStream xmlRosBase = new XMLRecordOutputStream(new String[]{"record"}, "<?xml version=\"1.0\" encoding=\"UTF-8\"?><harvest>", "</harvest>", ".*?<identifier>(.*?)</identifier>.*?", null);
	
	/**
	 * Constructor
	 * @param args command line arguments
	 * @throws IOException error connecting to record handler
	 * @throws UsageException user requested usage message
	 */
	private JSONFetch(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor
	 * @param argList parsed argument list
	 * @throws IOException error connecting to record handler
	 */
	/**
	 * @param args list of arguments
	 * @throws IOException an exception
	 */
	private JSONFetch(ArgList args) throws IOException {
		
		this(
			(args.has("u") ? args.get("u") : (args.get("f"))), // URL
			RecordHandler.parseConfig(args.get("o")), // output override
			args.get("n"), // namespace
			args.getAll("d").toArray(new String[]{}), // json object name
			args.getAll("p").toArray(new String[]{}) // path string
		);
		
	}
	
	/**
	 * Constructor
	 * @param address The website address of the repository, without http://
	 * @param rhOut The recordhandler to write to
	 * @param uriNameSpace the default namespace
	 * @param nodeNames list of node descriptiosn
	 * @param pathStrings json path strings
	 */
	public JSONFetch(String address, RecordHandler rhOut, String uriNameSpace, String nodeNames[], String pathStrings[]) {
		this.strAddress = address;
		this.rhOutput = rhOut;
		this.uriNS = uriNameSpace;
		
		if(nodeNames.length > 0 && nodeNames[0] != null) {
			this.nodeNames = nodeNames;
		} else {
			this.nodeNames = new String[]{"NODE"};
		}
		
		if(pathStrings.length > 0 && pathStrings[0] != null) {
			this.pathStrings = pathStrings;
		} else {
			this.pathStrings = new String[]{"$"};
		}
		
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("JSONFetch");
		
		parser.addArgument(new ArgDef().setShortOption('o').setLongOpt("output").setDescription("RecordHandler config file path").withParameter(true, "CONFIG_FILE"));
		parser.addArgument(new ArgDef().setShortOption('O').setLongOpt("outputOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of output recordhandler using VALUE").setRequired(false));
		
		// json harvester specific arguments
		parser.addArgument(new ArgDef().setShortOption('u').setLongOpt("url").setDescription("url which produces json ").withParameter(true, "URL").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('f').setLongOpt("file").setDescription("file containing json ").withParameter(true, "FALSE").setRequired(false));
		
		parser.addArgument(new ArgDef().setShortOption('n').setLongOpt("namespaceBase").withParameter(true, "NAMESPACE_BASE").setDescription("the base namespace to use for each node created").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('d').setLongOpt("description").withParameters(true, "NAME").setDescription("a descriptive name for the json object [have multiple -d for more names]").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('p').setLongOpt("path").withParameters(true, "PATH").setDescription("a single path for the json object [have multiple -p for more json paths]").setRequired(false));
		
		return parser;
	}
	
	/**
	 * Builds a json node record namespace
	 * @param nodeName the node to build the namespace for
	 * @return the namespace
	 */
	private String buildNodeRecordNS(String nodeName) {
		return this.uriNS + nodeName;
	}
	
	/**
	 * Builds a table's field description namespace
	 * @param nodeName the node to build the namespace for
	 * @return the namespace
	 */
	private String buildNodeFieldNS(String nodeName) {
		return this.uriNS + "fields/" + nodeName + "/";
	}
	
	/**
	 * Builds a node type description namespace
	 * @param nodeName the node to build the namespace for
	 * @return the namespace
	 */
	private String buildNodeTypeNS(String nodeName) {
		return this.uriNS + "types#" + nodeName;
	}
	
	/**
	 * Executes the task
	 * @throws IOException error getting recrords
	 */
	public void execute() throws IOException {
		
		String jsonpath = new String();
		
		try {
			XMLRecordOutputStream xmlRos = xmlRosBase.clone();
			xmlRos.setRso(this);
			
			// Get json contents as String, check for url first then a file
			String jsonString = new String();
			if(this.strAddress == null) {
				System.out.println(getParser().getUsage());
				System.exit(1);
			}
			if(this.strAddress.startsWith("http:")) {
				jsonString = WebAide.getURLContents(this.strAddress);
			} else {
				jsonString = FileAide.getTextContent(this.strAddress);
			}
			//log.info(jsonString);
			
			for(int i = 0; i < this.nodeNames.length; i++) {
				String name = this.nodeNames[i];
				jsonpath = this.pathStrings[i];
				log.info("Using path: " + jsonpath);
				JsonPath path = JsonPath.compile(jsonpath);
				log.info("got jsonpath: " + path.getPath());
				List<JSONObject> nodes = path.read(jsonString);
				log.info("name: " + name);
				//log.info("id: "+ id);
				log.info("num nodes: " + nodes.size());
				int count = 0;
				
				for(JSONObject jsonObject : nodes) {
					StringBuilder sb = new StringBuilder();
					
					//log.info("fixedkey: "+ fixedkey);
					StringBuilder recID = new StringBuilder();
					recID.append("node_-_");
					recID.append(String.valueOf(count));
					
					//log.trace("Creating RDF for "+name+": "+recID);
					// Build RDF BEGIN
					// Header info
					String nodeNS = "node-" + name;
					sb = new StringBuilder();
					sb.append("<?xml version=\"1.0\"?>\n");
					sb.append("<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n");
					sb.append("         xmlns:");
					sb.append(nodeNS);
					sb.append("=\"");
					sb.append(buildNodeFieldNS(name));
					sb.append("\"\n");
					sb.append("         xml:base=\"");
					sb.append(buildNodeRecordNS(name));
					sb.append("\">\n");
					
					// Record info BEGIN
					sb.append("  <rdf:Description rdf:ID=\"");
					sb.append(recID);
					sb.append("\">\n");
					
					// insert type value
					sb.append("    <rdf:type rdf:resource=\"");
					sb.append(buildNodeTypeNS(name));
					sb.append("\"/>\n");
					
					for(String key : jsonObject.keySet()) {
						Object val = jsonObject.get(key);
						String fixedkey = key.replaceAll(" ", "_");
						
						// Field BEGIN
						
						String field = nodeNS + ":" + fixedkey;
						sb.append("    <");
						sb.append(SpecialEntities.xmlEncode(field));
						sb.append(">");
						
						// insert field value
						// insert an empty string if the val is null
						if(val == null) {
							log.error("val is null for key: " + key);
							sb.append(""); // put in an empty string
						} else {
							sb.append(SpecialEntities.xmlEncode(val.toString().trim()));
						}
						// Field END
						sb.append("</");
						sb.append(SpecialEntities.xmlEncode(field));
						sb.append(">\n");
					}
					// Record info END
					sb.append("  </rdf:Description>\n");
					
					// Footer info
					sb.append("</rdf:RDF>");
					// Build RDF END
					
					// Write RDF to RecordHandler
					//log.trace("Adding record: " + fixedkey + "_" + recID);
					//log.trace("data: "+ sb.toString());
					//log.info("rhOutput: "+ this.rhOutput);
					//log.info("recID: "+recID);
					this.rhOutput.addRecord(name + "_" + recID, sb.toString(), this.getClass());
					count++;
				}
			}
		} catch(InvalidPathException e) {
			log.error("Invalid JsonPath: " + jsonpath);
		} catch(Exception e) {
			log.error(e.getMessage());
			e.printStackTrace();
			throw new IOException(e);
		}
	}
	
	@Override
	public void writeRecord(String id, String data) throws IOException {
		log.trace("Adding record " + id);
		this.rhOutput.addRecord(id, data, getClass());
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
			new JSONFetch(args).execute();
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
				log.error(error.getMessage());
				System.exit(1);
			} else {
				System.exit(0);
			}
		}
	}
}
