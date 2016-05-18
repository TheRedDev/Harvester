/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.fetch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.transform.TransformerException;
import org.vivoweb.harvester.util.FileAide;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.IterableAdaptor;
import org.vivoweb.harvester.util.MathAide;
import org.vivoweb.harvester.util.SOAPMessenger;
import org.vivoweb.harvester.util.XMLAide;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.vivoweb.harvester.util.repo.RecordHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches SOAP-XML data from a SOAP compatible site placing the data in the supplied file.
 */
public class WOSFetch {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(WOSFetch.class);
	
	/**
	 * RecordHandler to put data in.
	 */
	private RecordHandler outputRH;
	
	/**
	 * URL to send Authorization message to
	 */
	private URL authUrl;
	
	/**
	 * URL to send Authorization message to
	 */
	private URL searchUrl;
	
	/**
	 * URL to send Authorization message to
	 */
	private URL lamrUrl;

	/**
	 * SOAP style XML message to get authorization
	 */
	private String authMessage;

	/**
	 * Inputstream with SOAP style XML message to close session
	 */
	private String closeMessage;

	/**
	 * SOAP style XML message to perform the search
	 */
	private String searchString;

	/**
	 * SOAP style XML message to perform the search
	 */
	private String lamrMessage;
	
	/**
	 * the set of identifiers which are then requested from the LAMR site.
	 */
	private Set<String> lamrSet;
	
	/**
	 * This string is where the base64 encoded user name and password combination is to be stored
	 */
	private String usernamePassword;
	
	/**
	 * The tag that wraps each record in the search response
	 */
	private String recordTag;
	
	/**
	 * Session authCode
	 */
	private String authCode = null;

	/**
	 * the file path to session file
	 */
	private String sessionPath = null;

	/**
	 * do we end this session or not
	 */
	private boolean terminateSession = true;
	
	/**
	 * Constructor
	 * @param authUrl The location of the authorization site
	 * @param searchUrl The location of the search site
	 * @param lamrUrl The location of the links article match retrieval site
	 * @param outputRH The record handler used for storing the harvested records
	 * @param xmlSearchFile the file path to the search query message
	 * @param xmlLamrFile the file path to the links article match retrieval message
	 * @param userPass the user name password string to be base 64 encoded
	 * @throws IOException error talking with database
	 */
	public WOSFetch(URL authUrl, URL searchUrl, URL lamrUrl, RecordHandler outputRH, String xmlSearchFile, String xmlLamrFile, String userPass) throws IOException {
		init(authUrl, searchUrl, lamrUrl, outputRH, null, FileAide.getTextContent(xmlSearchFile), FileAide.getTextContent(xmlLamrFile), userPass );
		this.recordTag = getParser().getDefaultValue("r");
	}
	
	/**
	 * Constructor
	 * @param authUrl The location of the authorization site
	 * @param searchUrl The location of the search site
	 * @param lamrUrl The location of the links article match retrieval site
	 * @param authFilePath the file path to the auth message
	 * @param xmlSearchFilePath the file path to the search query message
	 * @param xmlLamrFilePath the file path to the links article match retrieval message
	 * @param userPass the user name password string to be base 64 encoded
	 * @param outputRH The record handler used for storing the harvested records
	 * @throws IOException error talking with database
	 */
	public WOSFetch(URL authUrl, URL searchUrl, URL lamrUrl, String authFilePath, String xmlSearchFilePath, String xmlLamrFilePath, String userPass, RecordHandler outputRH) throws IOException {
		init(authUrl, searchUrl, lamrUrl, outputRH, MathAide.nvl2(authFilePath, FileAide.getTextContent(authFilePath)), FileAide.getTextContent(xmlSearchFilePath), FileAide.getTextContent(xmlLamrFilePath), userPass);
		this.recordTag = getParser().getDefaultValue("r");
	}
	
	/**
	 * Command line Constructor
	 * @param args commandline arguments
	 * @throws IOException error creating task
	 * @throws UsageException user requested usage message
	 */
	private WOSFetch(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Arglist Constructor
	 * @param args option set of parsed args
	 * @throws IOException error creating task
	 */
	private WOSFetch(ArgList args) throws IOException {
		if(args.has("z")) {
			setSessionPath(args.get("z"));
		}
		if(args.has("t")) {
			this.terminateSession = true;
		}
		if(this.terminateSession && this.sessionPath != null) {
			// ignore the rest, we have all we care about
		} else {
			init(
				new URL(args.get("u")), 
				new URL(args.get("c")), 
				new URL(args.get("l")), 
				RecordHandler.parseConfig(args.get("o"), args.getValueMap("O")),
				(args.has("a")?FileAide.getTextContent(args.get("a")):null),
				FileAide.getTextContent(args.get("s")),
				FileAide.getTextContent(args.get("m")),
				args.get("p")
			);
			this.recordTag = args.get("r");
		}
	}
	
	/**
	 * Set the path in which to save the session path and turns off session termination
	 * @param sessionPath the file path to session file
	 */
	public void setSessionPath(String sessionPath) {
		this.sessionPath = sessionPath;
		this.terminateSession = false;
	}
	
	/**
	 * Set the session termination flag (defaults to true)
	 * @param terminateSession do we end this session or not
	 */
	public void setTerminateSession(boolean terminateSession) {
		this.terminateSession = terminateSession;
	}

	/**
	 * Library style Constructor
	 * @param authorizationUrl The location of the authorization site
	 * @param searchUrl The location of the search site
	 * @param lamrhUrl The location of the links article match retrieval site
	 * @param output The record handler used for storing the harvested records
	 * @param xmlAuthString the authorization message
	 * @param xmlSearchString the search query message
	 * @param xmlLamrString the links article match retrieval message
	 * @param usernamePassword the user name password string to be base 64 encoded
	 */
	public WOSFetch(URL authorizationUrl, URL searchUrl, URL lamrhUrl, RecordHandler output, String xmlAuthString, String xmlSearchString, String xmlLamrString, String usernamePassword) {
		this(authorizationUrl, searchUrl, lamrhUrl, output, xmlAuthString, xmlSearchString, xmlLamrString, usernamePassword, getParser().getDefaultValue("r"));
	}
	
	/**
	 * Library style Constructor
	 * @param authorizationUrl The location of the authorization site
	 * @param searchUrl The location of the search site
	 * @param lamrhUrl The location of the links article match retrieval site
	 * @param output The record handler used for storing the harvested records
	 * @param xmlAuthString the authorization message
	 * @param xmlSearchString the search query message
	 * @param xmlLamrString the links article match retrieval message
	 * @param usernamePassword the user name password string to be base 64 encoded
	 * @param recordTag the tag that wraps each record in the search response
	 */
	public WOSFetch(URL authorizationUrl, URL searchUrl, URL lamrhUrl, RecordHandler output, String xmlAuthString, String xmlSearchString, String xmlLamrString, String usernamePassword, String recordTag) {
		init(authorizationUrl, searchUrl, lamrhUrl, output, xmlAuthString, xmlSearchString, xmlLamrString, usernamePassword);
		this.recordTag = recordTag;
	}
	
	/**
	 * The initializing method called on via the constructors.
	 * @param authorizationUrl The location of the authorization site
	 * @param theSearchUrl The location of the search site
	 * @param thelamrUrl The location of the links article match retrieval site
	 * @param output The record handler used for storing the harvested records
	 * @param xmlAuthString the authorization message
	 * @param xmlSearchString the search query message
	 * @param xmlLamrString the links article match retrieval message
	 * @param userPass the user name password string to be base 64 encoded
	 */
	private void init(URL authorizationUrl, URL theSearchUrl, URL thelamrUrl, RecordHandler output, String xmlAuthString, String xmlSearchString, String xmlLamrString, String userPass) {
		String defaultAuthString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
			"<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" "+
			"xmlns:ns2=\"http://auth.cxf.wokmws.thomsonreuters.com\">"+
			"<soap:Body><ns2:authenticate/></soap:Body></soap:Envelope>";
		
		String closeString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
			"<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" "+
		    "xmlns:ns2=\"http://auth.cxf.wokmws.thomsonreuters.com\">"+
			"<soap:Body><ns2:closeSession/></soap:Body></soap:Envelope>";
		
		this.outputRH = output;
		this.authUrl = authorizationUrl;
		this.usernamePassword = MathAide.nvl2(userPass, Base64.encodeBase64URLSafeString(userPass.getBytes()), null);
		this.authMessage = MathAide.nvl(xmlAuthString, defaultAuthString);
		this.closeMessage = closeString;
		this.searchUrl = theSearchUrl;
		this.searchString = xmlSearchString;

		this.lamrUrl = thelamrUrl;
		this.lamrMessage = xmlLamrString;
		this.lamrSet = new TreeSet<String>();
		
		log.debug("Checking for NULL values");
		if(this.outputRH == null) {
			log.debug("Outputfile = null");
			log.error("Must provide output file!");
//		} else {
//			log.debug("Outputfile = " + this.outputRH.toString());
		}
		
		if(this.searchString == null) {
			log.debug("Search = null");
			log.error("Must provide Search message file!");
//		} else {
//			log.debug("Search = " + this.searchString);
		}
		
		if(this.authUrl == null) {
			log.debug("URL = null");
			log.error("Must provide authorization site url!");
//		} else {
//			log.debug("URL = "+ this.authUrl.toString());
		}
		
		if(this.searchUrl == null) {
			log.debug("URL = null");
			log.error("Must provide Search site url!");
//		} else {
//			log.debug("URL = "+ this.searchUrl.toString());
		}
	}
	
	/**
	 * @param previousQuery a WOS soap query xml message
	 * @return the string with the altered first node in the 
	 * @throws IOException thrown if there is an issue parsing the previousQuery string
	 */
	private String getnextQuery(String previousQuery) throws IOException{
		String nextQuery = "";
		try {
			Document searchDoc = XMLAide.getDocument(previousQuery);
			
			NodeList firstrecordNodes = searchDoc.getElementsByTagName("firstRecord");
			Node firstnode = firstrecordNodes.item(0);
			int firstrecord = Integer.parseInt(firstnode.getTextContent() );
//			log.debug("firstrecord = " + firstrecord);
			
			NodeList countNodes = searchDoc.getElementsByTagName("count");
			int count = Integer.parseInt(countNodes.item(0).getTextContent() );
//			log.debug("count= " + count);
			int newFirst = firstrecord + count;
			firstnode.setTextContent(Integer.toString(newFirst));
//			log.debug("new First Record= " + newFirst);
			
	
			nextQuery = XMLAide.formatXML(searchDoc);
		} catch(SAXException | TransformerException e) {
			throw new IOException(e);
		}
		return nextQuery;
	}
	
	/**
	 * @param responseXML String containing the results from the WOS soap query
	 * @return the number of records found in the string
	 * @throws IOException error reading and writing to recordhandlers or web service
	 */
	private Map<String,String> extractSearchRecords(String responseXML) throws IOException {
		HashMap<String,String> recordMap = new HashMap<String, String>();
		int numRecords = 0;
		Document responseDoc;
		try {
			responseDoc = XMLAide.getDocument(responseXML);
		} catch(SAXException e) {
			throw new IllegalArgumentException("Malformed XML", e);
		}
		log.debug("splitting on recordTag: <"+this.recordTag+">");
		for(Element currentRecord : IterableAdaptor.adapt(responseDoc.getElementsByTagName(this.recordTag), Element.class)) {
			log.debug("currentRecord: "+currentRecord);
//			try {
//				log.debug(XMLAide.formatXML(currentRecord));
//			} catch(TransformerException e) {
//				log.warn("Error formatting xml", e);
//			}
			Node utTagsZero = currentRecord.getElementsByTagName("UT").item(0);
			Node uidTagsZero = currentRecord.getElementsByTagName("UID").item(0);
			String identifier = MathAide.nvl(utTagsZero,uidTagsZero).getTextContent();
			String id = "id_-_" + identifier;
			compileLamrList(identifier);
			Document doc = XMLAide.getDocument();
			Element recordRoot = doc.createElement("Description");
			recordRoot.setAttribute("ID", identifier);
			Node node = doc.importNode(currentRecord, true);
			recordRoot.appendChild(node);
			String data;
			try {
				data = XMLAide.formatXML(recordRoot);
			} catch(TransformerException e) {
				throw new IllegalArgumentException("Malformed XML", e);
			}
			recordMap.put(id, data);
//			writeRecord(id, data);
			numRecords++;
		}
		log.debug("Extracted "+ numRecords +" records from search");
		return recordMap;
	}
	
	/**
	 * Execute LAMR Query
	 * @throws IOException error reading and writing to recordhandlers or lamr service
	 */
	private void executeLamrQuery() throws IOException {
		try {
			if(this.lamrSet.isEmpty()){
				log.debug("No LAMR query sent, empty LAMR set.");
				return;
			}
			//compile lamrquery with lamrSet
			
			Document lamrDoc;
			try {
				lamrDoc = XMLAide.getDocument(this.lamrMessage);
			} catch(SAXException e) {
				throw new IllegalArgumentException("Malformed XML", e);
			}
	
	//			log.debug("LAMR Message :\n"+XMLAide.formatXML(lamrDoc));
			Element lookUp = null;
			for(Element currentmap : IterableAdaptor.adapt(lamrDoc.getElementsByTagName("map"), Element.class)) {
				try {
					log.debug("Element :\n" + XMLAide.formatXML(currentmap));
				} catch(TransformerException e) {
					log.warn("Error formatting xml", e);
				}
	//			log.debug("Element name = \"" + currentmap.getAttribute("id")+ "\"");
				if(currentmap.getAttribute("id").contentEquals("lookup")){
					log.debug("Found element lookup");
	//				lookUp = (Element)currentmap.getParentNode();
					lookUp = currentmap;
					break;
				}
			}
			if(lookUp == null){
				log.error("No \"lookup\" node in LAMR query message");
			} else {
	//			log.debug("prelookUp = " + XMLAide.formatXML(lookUp));
				for(String currentUT : this.lamrSet){
					Element val = lamrDoc.createElement("val");
					val.setAttribute("name", "ut");
					val.setTextContent(currentUT);
					Element docMap = lamrDoc.createElement("map");
					docMap.setAttribute("name", "doc-"+currentUT);
					docMap.appendChild(val);
					lookUp.appendChild(docMap);
				}
			}
	//		log.debug("LAMR Message :\n"+XMLAide.formatXML(lamrDoc));
			
			//send lamrquery
			Document lamrRespDoc = null;
	
			ByteArrayOutputStream lamrResponse = new ByteArrayOutputStream();
			
			try {
				new SOAPMessenger(this.lamrUrl, lamrResponse, XMLAide.formatXML(lamrDoc), "", null).execute();
			} catch(TransformerException e) {
				throw new IllegalArgumentException("Malformed XML", e);
			}
			String lamrRespStr = lamrResponse.toString();
			log.debug("LAMR Response: ", lamrRespStr);
			try {
				lamrRespDoc = XMLAide.getDocument(lamrRespStr);
			} catch(SAXException e) {
				throw new IllegalArgumentException("Malformed XML", e);
			}
	
	//			log.debug("LAMR Response :\n"+nodeToString(lamrRespDoc));
	//			extract records - A little hacky - message specifics sensitive
	//			To ensure no erroneous name spaces rebuilding structure from existing data.
			log.debug("Extracting LAMR Records");
	//		records are in map elements.
	
			int recordsFound = 0;
			for(Element currentNode : IterableAdaptor.adapt(lamrRespDoc.getElementsByTagName("map"), Element.class)) {
	//				what we are looking for is found in maps named "WOS"
				if(currentNode.getAttribute("name").contentEquals("WOS")) {
	//					for output similarity  have the root node be Description
					Element recordRoot = lamrRespDoc.createElement("Description");
					String ut = "";
	//					each WOS node has the result formatted as named val nodes.
					for(Element currentVal : IterableAdaptor.adapt(currentNode.getElementsByTagName("val"), Element.class)) {
	//					Getting Record ID
						if(currentVal.getAttribute("name").contentEquals("ut")){
							ut = currentVal.getTextContent();
							break;
						}
					}
					if(ut != "") {
						recordsFound++;
						recordRoot.setAttribute( "ID",  ut);
	
						Element currentDup = lamrRespDoc.createElement("map");
						currentDup.setAttribute("name", "WOS");
						for(Element cur : IterableAdaptor.adapt(currentNode.getElementsByTagName("val"), Element.class)) {
							Element childNode = lamrRespDoc.createElement(cur.getTagName());
							childNode.setAttribute("name", cur.getAttribute("name"));
							childNode.setTextContent(cur.getTextContent());
							currentDup.appendChild( childNode );
						}
						recordRoot.appendChild(currentDup);
						
						try {
							writeRecord("id_-_LAMR_-_" + ut, XMLAide.formatXML(recordRoot));
						} catch(TransformerException e) {
							throw new IllegalArgumentException("Malformed XML", e);
						}
					}
				}
			}
			log.debug("Found " + recordsFound + " LAMR Records");
			this.lamrSet.clear();
		} catch(UnknownHostException e) {
			log.error(e.getMessage());
		}
	}
	
	/**
	 * @param id The identifying name for the record. (used as a filename in the text file record handler)
	 * @param data A string representing the information to place within the record.
	 * @throws IOException Thrown if there is an issue with the recordhandler back-end
	 */
	public void writeRecord(String id, String data) throws IOException {
		log.trace("Adding Record " + id);
		this.outputRH.addRecord(id, data, this.getClass());
	}
	
	/**
	 * @param id The identifying name for the record. (<UT> attribute)
	 * @throws IOException error reading and writing to recordhandlers or lamr service
	 */
	public void compileLamrList(String id) throws IOException {
		log.trace("Adding LAMR UT = " + id);
		this.lamrSet.add(id);
		if(this.lamrSet.size() == 50){
			executeLamrQuery();
		}
	}
	
	/**
	 * Executes the task
	 * @throws IOException error processing record handler or jdbc connection
	 */
	public void execute() throws IOException {
		if(this.sessionPath != null) {
			// try to load session authCode from file
			try {
				this.authCode = FileAide.getTextContent(this.sessionPath);
				if(StringUtils.isNotBlank(this.authCode)) {
					log.info("Loaded Saved Session: "+this.authCode);
				} else {
					log.debug("Saved Session File Empty");
					this.authCode = null;
				}
			} catch(IOException e) {
				log.info("Unable to Load Saved Session");
//				log.debug("Error Stacktrace", e);
			}
		}
		// check if all we care about is closing the session
		if(this.sessionPath != null && this.terminateSession) {
//			log.info("Skipping to Session Close");
		} else {
			// if we don't yet have an authCode, start a session
			if(StringUtils.isBlank(this.authCode)) {
				ByteArrayOutputStream authResponse = new ByteArrayOutputStream();
				
				HashMap<String,String> reqProp = new HashMap<String, String>();
				if(this.usernamePassword != null){
					reqProp.put("Authorization", "Basic " + this.usernamePassword);
				}
				new SOAPMessenger(this.authUrl, authResponse, this.authMessage, "", reqProp).execute();
				String authResp = authResponse.toString("UTF-8");
				this.authCode = XMLAide.getXPathResultFromString(authResp, "//return");
				if(StringUtils.isBlank(this.authCode)) {
					log.error(authResp);
				}
			}
			log.debug("auth code: "+this.authCode);
			
			// save the session authCode to our session file if we have one
			if(this.sessionPath != null) {
				FileAide.setTextContent(this.sessionPath, this.authCode);
			}
			
			// fetch records
			int recordsFound,lastRec,count;
			do {
				ByteArrayOutputStream searchResponse = new ByteArrayOutputStream();
				new SOAPMessenger(this.searchUrl, searchResponse, this.searchString, this.authCode, null).execute();
				String searchResp = searchResponse.toString("UTF-8");
				
				String recFound = XMLAide.getXPathResultFromString(searchResp, "//recordsFound");
				log.debug("Records Found = \"" + recFound + "\"");
				if(StringUtils.isBlank(recFound)) {
					log.error(searchResp);
				}
				String searchCount = XMLAide.getXPathResultFromString(this.searchString, "//retrieveParameters/count");
				log.debug("Search count = \"" + searchCount + "\"");
				String firstrecord = XMLAide.getXPathResultFromString(this.searchString, "//retrieveParameters/firstRecord");
				log.debug("first record = \"" + firstrecord + "\"");
	
				for(Entry<String, String> x : extractSearchRecords(searchResp).entrySet()) {
					writeRecord(x.getKey(), x.getValue());
				}
				count = Integer.parseInt(searchCount);
				recordsFound = Integer.parseInt(recFound);
				lastRec = Math.min(count, recordsFound) + Integer.parseInt(firstrecord) - 1;
				int left = recordsFound - lastRec;
				log.debug("Records left = " + left);
				this.searchString = getnextQuery(this.searchString);
				try {
					Thread.sleep(600); // do nothing for 600 miliseconds (1000 miliseconds = 1 second)
				} catch(InterruptedException e) {
					log.error(e.getMessage(), e);
				} 
			} while(lastRec < recordsFound);
			
			executeLamrQuery();
		}
		// close the session if we need to
		if(this.terminateSession && StringUtils.isNotBlank(this.authCode)) {
			closeSession();
		}
	}
	
	/**
	 * Close the session
	 * @throws IOException error communicating with service or deleting file
	 */
	private void closeSession() throws IOException {
		log.info("Closing Session");
		new SOAPMessenger(this.authUrl, new ByteArrayOutputStream(), this.closeMessage, this.authCode, null).execute();
		if(this.sessionPath != null) {
			log.info("Deleting Saved Session File");
			FileAide.delete(this.sessionPath);
		}
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("WOSFetch");
		parser.addArgument(new ArgDef().setShortOption('r').setLongOpt("recordtag").withParameter(true, "XMLTAG").setDescription("The XML tag which surrounds each record.").setDefaultValue("records"));
		parser.addArgument(new ArgDef().setShortOption('u').setLongOpt("authurl").withParameter(true, "URL").setDescription("The URL which will receive the AUTHMESSAGE.").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('c').setLongOpt("searchconnection").withParameter(true, "URL").setDescription("The URL which will receive the SEARCHMESSAGE.").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('l').setLongOpt("lamrconnection").withParameter(true, "URL").setDescription("The URL which will receive the LAMRMESSAGE.").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('s').setLongOpt("searchmessage").withParameter(true, "SEARCHMESSAGE").setDescription("The SEARCHMESSAGE file path.").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('a').setLongOpt("authmessage").withParameter(true, "AUTHMESSAGE").setDescription("The AUTHMESSAGE file path.").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('m').setLongOpt("lamrmessage").withParameter(true, "LAMRMESSAGE").setDescription("The LAMRMESSAGE file path.").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('p').setLongOpt("usernamepassword").withParameter(true, "USERNAMEPASSWORD").setDescription("The username and password string to be encoded using base64").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('o').setLongOpt("output").withParameter(true, "OUTPUT_FILE").setDescription("XML result file path").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('O').setLongOpt("outputOverride").withParameterValueMap("RH_PARAM", "VALUE").setDescription("override the RH_PARAM of output recordhandler using VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('z').setLongOpt("reuseSession").withParameter(true, "SESSIONSAVEFILE").setDescription("Save the session authCode in this file path, reusing session if already existing").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('t').setLongOpt("terminateSession").setDescription("Terminate the reused session, do nothing else").setRequired(false));
		return parser;
	}
	
	/**
	 * Main method
	 * @param args commandline arguments
	 */
	public static void main(String... args) {
		Exception error = null;
		WOSFetch wos = null;
		try {
			InitLog.initLogger(args, getParser());
			log.info(getParser().getAppName() + ": Start");
			wos = new WOSFetch(args);
			wos.execute();
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
				if(wos != null) {
					try {
						log.trace("Closing Session Due To Error");
						wos.closeSession();
					} catch(IOException e) {
						log.error("Error Closing Session", e);
					}
				}
				System.exit(1);
			}
		}
	}
}
