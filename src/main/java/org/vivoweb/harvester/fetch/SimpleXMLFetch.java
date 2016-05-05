/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.fetch;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.FileAide;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.IterableAdaptor;
import org.vivoweb.harvester.util.UniversalNamespaceCache;
import org.vivoweb.harvester.util.WebAide;
import org.vivoweb.harvester.util.XMLAide;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.vivoweb.harvester.util.repo.RecordHandler;
import org.vivoweb.harvester.util.repo.RecordStreamOrigin;
import org.vivoweb.harvester.util.repo.XMLRecordOutputStream;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

/** Class for harvesting from XML Data Sources
* @author jaf30
*/
public class SimpleXMLFetch implements RecordStreamOrigin {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(SimpleXMLFetch.class);
	
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
	 * mapping of tagnames
	 */
	private String tagNames[];
	
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
	private SimpleXMLFetch(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor
	 * @param args parsed argument list
	 * @throws IOException error connecting to record handler
	 */
	private SimpleXMLFetch(ArgList args) throws IOException {
		this(
			(args.has("u") ? args.get("u") : (args.get("f"))), // URL
			RecordHandler.parseConfig(args.get("o")), // output override
			args.get("n"), // namespace
			args.getAll("t").toArray(new String[]{}) // tag name
		);
	}
	
	/**
	 * Constructor
	 * @param address The website address of the repository, without http://
	 * @param rhOut The recordhandler to write to
	 * @param uriNameSpace the default namespace
	 * @param tagNames list of node descriptions
	 */
	public SimpleXMLFetch(String address, RecordHandler rhOut, String uriNameSpace, String[] tagNames) {
		this.strAddress = address;
		this.rhOutput = rhOut;
		this.uriNS = uriNameSpace;
		if(tagNames.length > 0 && tagNames[0] != null) {
			this.tagNames = tagNames;
		} else {
			this.tagNames = new String[]{"NODE"};
		}
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("SimpleXMLFetch");
		
		parser.addArgument(new ArgDef().setShortOption('o').setLongOpt("output").setDescription("RecordHandler config file path").withParameter(true, "CONFIG_FILE"));
		parser.addArgument(new ArgDef().setShortOption('O').setLongOpt("outputOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of output recordhandler using VALUE").setRequired(false));
		
		// xml harvester specific arguments
		parser.addArgument(new ArgDef().setShortOption('u').setLongOpt("url").setDescription("url which produces xml ").withParameter(true, "URL").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('f').setLongOpt("file").setDescription("file containing xml ").withParameter(true, "FALSE").setRequired(false));
		
		parser.addArgument(new ArgDef().setShortOption('n').setLongOpt("namespaceBase").withParameter(true, "NAMESPACE_BASE").setDescription("the base namespace to use for each node created").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('t').setLongOpt("tagname").withParameters(true, "TAGNAME").setDescription("an tagname [have multiple -e for more names]").setRequired(false));
		
		return parser;
	}
	
	/**
	 * Builds a  node record namespace
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
		try {
			XMLRecordOutputStream xmlRos = xmlRosBase.clone();
			xmlRos.setRso(this);
			
			// Get xml contents as String, check for url first then a file
			if(this.strAddress == null) {
				System.out.println(getParser().getUsage());
				System.exit(1);
			}
			
			InputStream stream = null;
			if(this.strAddress.startsWith("http:") || this.strAddress.startsWith("https:")) {
				stream = WebAide.getInputStream(this.strAddress);
			} else {
				stream = FileAide.getInputStream(this.strAddress);
			}
			Document doc = XMLAide.getDocument(stream);
			
			for(String tagname : this.tagNames) {
				NodeList nodes = doc.getElementsByTagName(tagname);
				log.info("Matched this many nodes: " + nodes.getLength());
				int count = 0;
				for(Node node : IterableAdaptor.adapt(nodes)) {
					StringBuilder sb = new StringBuilder();
					
					StringBuilder recID = new StringBuilder();
					recID.append("node_-_");
					recID.append(String.valueOf(count));
					
					//log.trace("Creating RDF for "+name+": "+recID);
					// Build RDF BEGIN
					// Header info
					sb = new StringBuilder();
					sb.append("<?xml version=\"1.0\"?>\n");
					sb.append("<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n");
					sb.append("         xmlns:");
					sb.append("node-" + tagname);
					sb.append("=\"");
					sb.append(buildNodeFieldNS(tagname));
					sb.append("\"\n");
					sb.append("         xml:base=\"");
					sb.append(buildNodeRecordNS(tagname));
					sb.append("\"\n");
					// add namespaces from the namespaces in the root element
					for(Entry<String, String> nsEntry : new UniversalNamespaceCache(doc, true).getPrefix2UriMap().entrySet()) {
						String nsprefix = nsEntry.getKey();
						String nsuri = nsEntry.getValue();
						if(!nsprefix.equals("DEFAULT")) {
							sb.append("\nxmlns:" + nsprefix + "=\"" + nsuri + "\"");
						}
					}
					// and close
					sb.append(">\n\n");
					
					// Record info BEGIN
					sb.append("  <rdf:Description rdf:ID=\"");
					sb.append(recID);
					sb.append("\">\n");
					
					// insert type value
					sb.append("    <rdf:type rdf:resource=\"");
					sb.append(buildNodeTypeNS(tagname));
					sb.append("\"/>\n");
					//
					// Now parse the element matched
					//
					
					for(Node childNode : IterableAdaptor.adapt(node.getChildNodes())) {
						if(childNode.getNodeType() != Node.TEXT_NODE) {
							String nodeXml = serializeNode(childNode);
							sb.append(nodeXml);
						}
					}
					
					// Record info END
					sb.append("\n  </rdf:Description>\n");
					
					// Footer info
					sb.append("</rdf:RDF>");
					// Build RDF END
					
					// Write RDF to RecordHandler
					//log.trace("Adding record: " + fixedkey + "_" + recID);
					//log.trace("data: "+ sb.toString());
					//log.info("rhOutput: "+ this.rhOutput);
					//log.info("recID: "+recID);
					this.rhOutput.addRecord(tagname + "_" + recID, sb.toString(), this.getClass());
					count++;
				}
			}
		} catch(Exception e) {
			log.error(e.getMessage());
			e.printStackTrace();
			throw new IOException(e);
		}
	}
	
	/**
	 * Serialize node
	 * @param node the node
	 * @return the serialized output
	 */
	private String serializeNode(Node node) {
		LSSerializer writer;
		try {
			writer = ((DOMImplementationLS)DOMImplementationRegistry.newInstance().getDOMImplementation("LS")).createLSSerializer();
		} catch(ClassNotFoundException | InstantiationException | IllegalAccessException | ClassCastException e) {
			throw new IllegalStateException("Unable to Instantiate LSSerializer", e);
		}
		DOMConfiguration domConfig = writer.getDomConfig();
		domConfig.setParameter("namespaces", Boolean.TRUE);
		domConfig.setParameter("namespace-declarations", Boolean.TRUE);
		
		if(domConfig.canSetParameter("xml-declaration", Boolean.FALSE)) {
			domConfig.setParameter("xml-declaration", Boolean.FALSE);
		}
		return writer.writeToString(node);
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
			new SimpleXMLFetch(args).execute();
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
