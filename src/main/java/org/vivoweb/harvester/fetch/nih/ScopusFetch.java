/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.fetch.nih;

import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.EFetchResult;
import gov.nih.nlm.ncbi.www.soap.eutils.EFetchPubmedServiceStub.PubmedArticleSet_type0;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.TransformerException;
import org.apache.axis2.databinding.utils.writer.MTOMAwareXMLSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.FileAide;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.IterableAdaptor;
import org.vivoweb.harvester.util.XMLAide;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.vivoweb.harvester.util.repo.JenaConnect;
import org.vivoweb.harvester.util.repo.RecordHandler;
import org.vivoweb.harvester.util.repo.XMLRecordOutputStream;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

/**
 * Module for fetching publications from Scopus (query by Scopus Author ID) and 
 * PubMed (query by DOI or Pubmed ID).
 * For detailed information, go to: 
 * https://sourceforge.net/apps/mediawiki/vivo/index.php?title=Scopus
 * @author Eliza Chan (elc2013@med.cornell.edu)
 */
public class ScopusFetch extends NIHFetch {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(ScopusFetch.class);
	
	/**
	 * The name of the PubMed database
	 */
	private static String database = "pubmed";

	/**
	 * a base xmlrecordoutputstream
	 */
	protected static XMLRecordOutputStream baseXMLROS = new XMLRecordOutputStream(new String[]{"PubmedArticle","PubmedBookArticle"}, "<?xml version=\"1.0\"?>\n<!DOCTYPE PubmedArticleSet PUBLIC \"-//NLM//DTD PubMedArticle, 1st January 2011//EN\" \"http://www.ncbi.nlm.nih.gov/entrez/query/DTD/pubmed_110101.dtd\">\n<PubmedArticleSet>\n", "\n</PubmedArticleSet>", ".*?<[pP][mM][iI][dD].*?>(.*?)</[pP][mM][iI][dD]>.*?", null);
	
	/**
	 * map that contains Scopus Author ID of researchers in VIVO
	 * key: http://vivo.med.cornell.edu/individual/cwid-sea2003,6602763271
	 */
	private Map<String, String> scopusIdMap = new HashMap<String, String>();
	
	/**
	 * jena connect for vivo instance
	 */
	private JenaConnect vivoJena;

	/**
	 * SPARQ query to retrieve people with Scopus ID
	 */
	private String sparqlQuery = null;

	/**
	 * Scopus X-ELS-APIKey for connection
	 */
	private String scopusApiKey;
	
	/**
	 * Scopus X-ELS-Authtoken for connection
	 */
	private String scopusAuthtoken;
	
	/**
	 * Scopus Accept for connection
	 */
	private String scopusAccept;
	
	/**
	 * Scopus publication start year
	 */
	private String scopusPubYearS;
	
	/**
	 * Scopus publication end year
	 */
	private String scopusPubYearE;
	
	/**
	 * Scopus affiliation list
	 */
	private List<String> scopusAffilList;

	/**
	 * Scopus publications linked to affiliation list.
	 */
	private String scopusAffilLinked = null;
	
	/**
	 * List to store pubmed documents.
	 */
	private List<Document> pubmedDocList = new ArrayList<Document>();
	
	/**
	 * List to make sure there are no duplications of Scopus Doc Ids in the ScopusBean maps
	 * across all authors.
	 */
	private List<String> scopusDocIdList = new ArrayList<String>();
	
	/**
	 * Constructor: Primary method for running a PubMed Fetch. The email address of the person responsible for this
	 * install of the program is required by NIH guidelines so the person can be contacted if there is a problem, such
	 * as sending too many queries too quickly.
	 * @param emailAddress contact email address of the person responsible for this install of the VIVO Harvester
	 * @param searchTerm query to run on pubmed data
	 * @param maxRecords maximum number of records to fetch
	 * @param batchSize number of records to fetch per batch
	 * @param rh record handler to write to
	 */
	public ScopusFetch(String emailAddress, String searchTerm, String maxRecords, String batchSize, RecordHandler rh) {
		super(emailAddress, searchTerm, maxRecords, batchSize, rh, database);
	}
	
	/**
	 * Constructor
	 * @param args commandline argument
	 * @throws IOException error creating task
	 * @throws UsageException user requested usage message
	 */
	private ScopusFetch(String[] args) throws IOException, UsageException {
		this(getParser("PubmedFetch", database).parse(args));
		ArgParser parser = new ArgParser("PubmedFetchIncrement");
		parser.addArgument(new ArgDef().setShortOption('q').setLongOpt("sparql").withParameter(true, "SPARQL_QUERY_FILE").setDescription("SPARQL query filename").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('s').setLongOpt("scopus-pubyear-start").withParameter(true, "SCOPUS_PUBYEAR_START").setDescription("Scopus publication year start").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('e').setLongOpt("scopus-pubyear-end").withParameter(true, "SCOPUS_PUBYEAR_END").setDescription("Scopus publication year end").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('v').setLongOpt("vivoJena-config").withParameter(true, "CONFIG_FILE").setDescription("vivoJena JENA configuration filename").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('k').setLongOpt("scopus-apikey").withParameter(true, "SCOPUS_APIKEY").setDescription("Scopus APIKey").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('a').setLongOpt("scopus-accept").withParameter(true, "SCOPUS_ACCEPT").setDescription("Scopus accept").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('f').setLongOpt("scopus-affiliation").withParameter(true, "SCOPUS_AFFILIATION").setDescription("Scopus affiliation").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('n').setLongOpt("scopus-affiliation-linked").withParameter(true, "SCOPUS_AFFILIATION_LINKED").setDescription("Scopus affiliation linked").setRequired(false));
		ArgList opts = parser.parse(args);
		this.vivoJena = JenaConnect.parseConfig(opts.get("v"), null);
		this.scopusApiKey = opts.get("k");
		this.scopusAccept = opts.get("a");
		
		if(opts.get("f") != null) {
			this.scopusAffilList = Arrays.asList(opts.get("f").split(","));
		}
		this.scopusAffilLinked = opts.get("n");
		this.sparqlQuery = FileAide.getTextContent(opts.get("q"));
		if(opts.get("s") != null && opts.get("e") != null) {
			this.scopusPubYearS = opts.get("s");
			this.scopusPubYearE = opts.get("e");			
		}
	}
	
	/**
	 * Constructor
	 * @param argList parsed argument list
	 * @throws IOException error creating task
	 */
	private ScopusFetch(ArgList argList) throws IOException {
		super(argList, database);
	}
	
	@Override
	public void execute() throws IOException {
		// get scopus author id from vivo
		getVivoScopusId();
		
		if(this.scopusIdMap.size() > 0) {
			// connect to Scopus and get authtoken
			boolean connected = initScopusConnect();
	
			if(connected) {
				// iterate list of Scopus Author ID and get publication data
				log.info("Query Scopus by Author ID: Start");
				
				StringBuffer errMsg = new StringBuffer();
				for(String key : this.scopusIdMap.keySet()) {
					
					String[] keySplit = key.split(",");
					String scopusId = keySplit[1];
					log.info("scopusId: " + scopusId);
					
					// query Scopus by Author ID
					String scopusQueryResponse = scopusQueryByAuthorId(scopusId);
	
					// extract DOI from the query response and populate ScopusBeanMap
					Map<String, ScopusBean> sbMap = new HashMap<String, ScopusBean>();
					populateScopusBeanMap(scopusQueryResponse, sbMap, errMsg);

					
					// first round: query Pubmed and populate pubmedMap using Doi
					Map<String, String> pubmedMap = new HashMap<String, String>();
					if(errMsg.length() == 0) {
						pubmedQueryByDoi(sbMap, pubmedMap, errMsg);
					}
					
					// second round: 1) query Scopus by Doc ID to get Pubmed ID
					List<String> pmidList = new ArrayList<String>();
					if(errMsg.length() == 0) {
						scopusQueryByDocId(sbMap, pmidList);
					}
					
					// second round: 2) query Pubmed and populate pubmedMap using Pubmed ID
					if(errMsg.length() == 0) {
						pubmedQueryByPubmedId(pmidList, sbMap, pubmedMap, errMsg);
					}
	
					// finally: populate scopusMap with articles that are not found in Pubmed
					Map<String, String> scopusMap = new HashMap<String, String>();
					if(errMsg.length() == 0) {
						try {
							populateScopusMap(sbMap, scopusMap);
							
							// write to files
							writeToFiles(scopusId, pubmedMap, scopusMap, errMsg);
						} catch(TransformerException e) {
							log.error(e.getMessage(), e);
						}
					}
				}

				log.info("Query Scopus by Author ID: End");
			}
		}

		// for test purpose
		//scopusQueryByAuthorId("36078494300"); // Shi, Lei
		//scopusQueryByAuthorId("35969977300"); // Adelman, Ronald D
		//scopusQueryByAuthorId("7102989382"); // Adelman, Ronald D
		//scopusQueryByAuthorId("23019591700"); // Salemi, Arash
		//scopusQueryByAuthorId("24435990700"); // Abramson, Erika
		//scopusQueryByAuthorId("35117492000"); // Dorff, Kevin
		//scopusQueryByAuthorId("6602462776"); // Campagne, Fabien
		//scopusQueryByAuthorId("35328914300"); delete this
	}
	
	/**
	 * Run SPARQL to retrieve Scopus Author ID
	 */
	private void getVivoScopusId() {
		// run sparql
		for(QuerySolution qs : IterableAdaptor.adapt(runSparql())) {
			String uri = null;
			String scopusId = null;
			for(String key : IterableAdaptor.adapt(qs.varNames())) {
				if(qs.get(key).isResource()) { // resource URI
					uri = qs.getResource(key).getURI();
				} else if (qs.get(key).isLiteral()) { // scopusId
					scopusId = qs.getLiteral(key).getString().replace("<p>", "").replace("</p>", "");
				}
			}
			if(uri != null && scopusId != null) {
				this.scopusIdMap.put(uri + "," + scopusId, null);
			}
		}
	}

	/**
	 * Necessary step to obtain authentication token
	 * @return true if successful, false otherwise
	 */
	private boolean initScopusConnect() {
		boolean connected = true;
		try {
			URL url = new URL("http://api.elsevier.com/authenticate?platform=SCOPUS");
			URLConnection conn = url.openConnection();
			conn.setRequestProperty("X-ELS-APIKey", this.scopusApiKey);
			conn.setRequestProperty("Accept", this.scopusAccept);
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String inputLine;
			StringBuffer respBuf = new StringBuffer();
			
			while((inputLine = in.readLine()) != null) {
				respBuf.append(inputLine);
			}
			in.close();
			
			Document doc = XMLAide.getDocument(respBuf.toString());
			Node authtokenNode = doc.getElementsByTagName("authenticate-response").item(0);
			if(authtokenNode.getTextContent() != null) {
				this.scopusAuthtoken  = authtokenNode.getTextContent().trim();
			}
		} catch(SAXException e) {
			log.error("initScopusConnect SAXException: ", e);
			connected = false;
		} catch(IOException e) {
			log.error("initScopusConnect IOException: ", e);
			connected = false;
		}
		return connected;
	}
	
	/**
	 * SPARQL to retrieve people from VIVO with Scopus ID
	 * @return result set
	 */
	private ResultSet runSparql() {
		ResultSet rs = null;
		try {
			rs = this.vivoJena.executeSelectQuery(this.sparqlQuery);
			log.info(this.sparqlQuery);
		} catch(IOException e) {
			log.error(this.getClass().getName() + " execute IOException: " + e);
		}
		return rs;
	}
	
	/**
	 * Obtain Scopus metadata by querying Scopus Author ID
	 * The query String includes the publication start and end years to
	 * limit the results returned from Scopus.
	 * 
	 * @param scopusId the scopusID
	 * @return metadata
	 * @throws IOException error parsing response
	 */
	private String scopusQueryByAuthorId(String scopusId) throws IOException {
		
		int totalResults = 0;
		String pubYearStr = null;
		StringBuffer completeRespBuf = new StringBuffer();
		
		if(this.scopusPubYearS != null && this.scopusPubYearE != null) {
			pubYearStr = constructPubYearQStr(this.scopusPubYearS, this.scopusPubYearE);
		}

		try {
			String queryStr = "http://api.elsevier.com/content/search/index:SCOPUS?query=au-id(" + scopusId + ")";
			if(pubYearStr != null) {
				queryStr += "+AND+(" + pubYearStr + ")";
			}
			String respStr = urlConnect(queryStr);
			
			if(respStr.length() > 0) {
				Document doc = XMLAide.getDocument(respStr);
				NodeList resultsNodes = doc.getElementsByTagName("opensearch:totalResults");
				if(resultsNodes != null) {
					String resultsNodeVal = resultsNodes.item(0).getTextContent();
					totalResults = Integer.parseInt(resultsNodeVal);
					log.info("Total results for " + scopusId + " is " + totalResults);
				}
			}

			if(totalResults > 0) { 
				// query by counts
				int start = 0;
				int count = 200;
				int countList = 0;
				for(String queryCompleteStr: constructCompleteQStr(queryStr, count, start, totalResults)) {
					log.info("Scopus query: " + queryCompleteStr);
					String eachRespStr = urlConnect(queryCompleteStr);
					if(countList > 0) {
						int entryIndex = eachRespStr.indexOf("<entry>");
						if(entryIndex > -1) {
							eachRespStr = eachRespStr.substring(entryIndex);
						}
					}
					completeRespBuf.append(eachRespStr.replace("</feed>", ""));
					countList++;
				}
				completeRespBuf.append("</feed>");
			}
		} catch(SAXException e) {
			log.error("scopusQueryByAuthorId SAXException: ", e);
		}
		return completeRespBuf.toString();
	}
	
	/**
	 * connect
	 * @param queryStr the query string
	 * @return the response
	 */
	private String urlConnect(String queryStr) {
		StringBuffer respBuf = new StringBuffer();
		try {
			URL url = new URL(queryStr);
			URLConnection conn = url.openConnection();
			conn.setRequestProperty("X-ELS-APIKey", this.scopusApiKey);
			conn.setRequestProperty("X-ELS-Authtoken", this.scopusAuthtoken);
			conn.setRequestProperty("Accept", this.scopusAccept);
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String inputLine;
	
			while((inputLine = in.readLine()) != null) {
				respBuf.append(inputLine);
			}
			in.close();
		} catch(MalformedURLException e) {
			log.error("urlConnect MalformedURLException: ", e);
		} catch(IOException e) {
			log.error("urlConnect IOException: ", e);
		} catch(Exception e) {
			log.error("urlConnect Exception: ", e);
		}
		return respBuf.toString();
	}

	/**
	 * Constructs publication year String
	 * Method declared public for test purposes
	 * @param startYear the start year
	 * @param endYear the end year
	 * @return the query string
	 */
	public String constructPubYearQStr(String startYear, String endYear) {
		// e.g. PUBYEAR+IS+2010+OR+PUBYEAR+IS+2011
		StringBuffer qBuf = new StringBuffer();
		int s = Integer.parseInt(startYear);
		int e = Integer.parseInt(endYear);
		if(e > s) {
			for(int i = s; i < e + 1; i++) {
				if(qBuf.length() > 0) {
					qBuf.append("+OR+");
				}
				qBuf.append("PUBYEAR+IS+" + String.valueOf(i));
			}
		} else {
			qBuf.append("PUBYEAR+IS+" + String.valueOf(s));
		}
		return qBuf.toString();
	}

	/**
	 * Constructs query String list
	 * Method declared public for test purposes
	 * @param queryStr the query string
	 * @param count the number by which to increment
	 * @param start where to start
	 * @param totalResults where to end
	 * @return array list of results
	 */
	public List<String> constructCompleteQStr(String queryStr, int count, int start, int totalResults) {
		int myStart = start;
		List<String> qStrList = new ArrayList<String>();
		while(myStart <= totalResults) {
			String queryCompleteStr = queryStr + "&count=" + count + "&start=" + myStart + "&view=COMPLETE";
			qStrList.add(queryCompleteStr);
			myStart += count;
		}
		return qStrList;
	}

	/**
	 * This method retrieves Pubmed ID by querying Scopus by Scopus Document ID
	 * @param sbMap scopus mapping
	 * @param pmidList pubmed ids list
	 */
	private void scopusQueryByDocId(Map<String, ScopusBean> sbMap, List<String> pmidList) {
		try {
			for(String key : sbMap.keySet()) {
				ScopusBean sb = sbMap.get(key);
				if(sb.getPubmedId() == null) {
					// get Pubmed ID from Scopus
					StringBuffer respBuf = new StringBuffer();
					String queryStr = "http://api.elsevier.com/content/abstract/SCOPUS_ID:" + sb.getScopusDocId() + "?view=META";
					URL url = new URL(queryStr);
					URLConnection conn = url.openConnection();
					conn.setRequestProperty("X-ELS-APIKey", this.scopusApiKey);
					conn.setRequestProperty("X-ELS-Authtoken", this.scopusAuthtoken);
					conn.setRequestProperty("Accept", this.scopusAccept);
					BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
					String inputLine;

					while((inputLine = in.readLine()) != null) {
						respBuf.append(inputLine);
					}
					in.close();
					
					// log.info("query by scopus doc id: " + respBuf.toString());
					NodeList pubmedNodes = XMLAide.getDocument(respBuf.toString()).getElementsByTagName("pubmed-id");
					if(pubmedNodes != null) {
						String pubmedNodeVal = pubmedNodes.item(0).getTextContent();
						if(!"".equals(pubmedNodeVal)) {
							pmidList.add(pubmedNodeVal);
							sb.setPubmedId(pubmedNodeVal);
						}
					} else {
						log.info("Pubmed ID not found in Scopus.");
					}
				}
			}
		} catch(MalformedURLException e) {
			log.error("scopusQueryByDocId MalformedURLException: ", e);
		} catch(IOException e) {
			log.error("scopusQueryByDocId IOException: ", e);
		} catch(Exception e) {
			log.error("scopusQueryByDocId Exception: ", e);
		}
	}

	/**
	 * This method extracts metadata from a Scopus feed and then populates the ScopusBean map.
	 * @param resp xml response
	 * @param sbMap scopus bean map
	 * @param errMsg error message buffer
	 * @throws IOException error with parsing response
	 * @throws DOMException error parsing response
	 */
	private void populateScopusBeanMap(String resp, Map<String, ScopusBean> sbMap, StringBuffer errMsg) throws DOMException, IOException {
		try {
			if(resp.length() > 0) {
				// populate ScopusBean map
				for(Node entryNode : IterableAdaptor.adapt(XMLAide.getDocument(resp).getElementsByTagName("entry"))) {
					String doi = null;
					@SuppressWarnings("unused")
					String title = null;
					String scopusDocId = null;
					@SuppressWarnings("unused")
					String issn = null;
					@SuppressWarnings("unused")
					String volume = null;
					@SuppressWarnings("unused")
					String issue = null;
					@SuppressWarnings("unused")
					String pageRange = null;
					boolean withinAffil = false;
					ScopusBean sb = new ScopusBean();
					List<Node> authors = new ArrayList<Node>();
					for(Node entryChildNode : IterableAdaptor.adapt(entryNode.getChildNodes())) {
						if("dc:identifier".equals(entryChildNode.getNodeName())) {
							scopusDocId = entryChildNode.getTextContent().replace("SCOPUS_ID:", "");
						} else if("author".equals(entryChildNode.getNodeName())) {
							authors.add(entryChildNode);
						} else if("prism:doi".equals(entryChildNode.getNodeName())) {
							doi = entryChildNode.getTextContent();
						} else if("dc:title".equals(entryChildNode.getNodeName())) {
							title = entryChildNode.getTextContent();
						} else if("prism:issn".equals(entryChildNode.getNodeName())) {
							issn = entryChildNode.getTextContent();
						} else if("prism:volume".equals(entryChildNode.getNodeName())) {
							volume = entryChildNode.getTextContent();
						} else if("prism:issueIdentifier".equals(entryChildNode.getNodeName())) {
							issue = entryChildNode.getTextContent();
						} else if("prism:pageRange".equals(entryChildNode.getNodeName())) {
							pageRange = entryChildNode.getTextContent();
						} else if("affiliation".equals(entryChildNode.getNodeName())) {
							for(Node affilNode : IterableAdaptor.adapt(entryChildNode.getChildNodes())) {
								if("afid".equals(affilNode.getNodeName())) {
									String affil = affilNode.getTextContent();
									if(this.scopusAffilList != null && this.scopusAffilList.contains(affil)) {
										withinAffil = true;
										break;
									}
								}
							}
							
						}
					}
		
					sb.setScopusDocId(scopusDocId);
					sb.setDoi(doi);
					sb.setAuthors(authors);
					sb.setEntryNode(entryNode);
	
					// check affiliation
					boolean addToMap = false; // add to sbMap or not - default is false
					if(this.scopusAffilLinked != null) {
						if(("true".equals(this.scopusAffilLinked) && withinAffil) || ("false".equals(this.scopusAffilLinked) && !withinAffil)) {
							addToMap = true;
						}
					} else {
						addToMap = true; // add all publications to map, regardless of affiliated or not
					}
	
					// check if the article already exists in VIVO
					/* comment out
					boolean existsInVivo = false;
					if(sb.getDoi() != null) { // try doi
						existsInVivo = isDoiInVivo(sb.getDoi());
					}
					if(!existsInVivo) { // try Scopus Doc ID
						existsInVivo = isScopusDocIdInVivo(sb.getScopusDocId());
					}
					*/
					// add ScopusBean to map for Pubmed queries
					if(!this.scopusDocIdList.contains(sb.getScopusDocId())) {
						/*
						if(addToMap && !existsInVivo) {
							sbMap.put(sb.getScopusDocId(), sb);
						}
						*/
						if(addToMap && sb.getScopusDocId() != null) {
							sbMap.put(sb.getScopusDocId(), sb);
						}
						this.scopusDocIdList.add(sb.getScopusDocId());
					}
				}
			}
		} catch(SAXException e) {
			log.error("populateScopusBeanMap SAXException: ", e);
			errMsg.append(e + "\n");
		}
	}
	
	/**
	 * Query Pubmed by DOI
	 * @param sbMap scopus bean map
	 * @param pubmedMap pubmed map
	 * @param errMsg error message buffer
	 */
	private void pubmedQueryByDoi(Map<String, ScopusBean> sbMap, Map<String, String> pubmedMap, StringBuffer errMsg) {
		StringBuffer searchTermBuf = new StringBuffer();
		try {
			for(String key : sbMap.keySet()) {
				ScopusBean sb = sbMap.get(key);
				//if (sb.getDoi() != null && !isDoiInVivo(sb.getDoi())) {
				if(sb.getDoi() != null) {
					String searchDoi = "(" + sb.getDoi().replaceAll("[()]", "") + "[doi])";
					if(searchTermBuf.length() > 0) {
						searchTermBuf.append(" OR ");
					}
					searchTermBuf.append(searchDoi);
				}
			}
			//log.info(searchTermBuf.toString());
			populatePubmedMap(searchTermBuf.toString(), sbMap, pubmedMap, true, false, errMsg);
		} catch(Exception e) {
			log.error("pubmedQueryByDoi Exception: ", e);
			errMsg.append(e + "\n");
		}
	}

	/**
	 * Query Pubmed by Pubmed ID
	 * @param pmidList list of pubmed ids
	 * @param sbMap scopus bean map
	 * @param pubmedMap pubmed map
	 * @param errMsg error message buffer
	 */
	private void pubmedQueryByPubmedId(List<String> pmidList, Map<String, ScopusBean> sbMap, Map<String, String> pubmedMap, StringBuffer errMsg) {
		StringBuffer searchTermBuf = new StringBuffer();
		for(String pmid : pmidList) {
			if(searchTermBuf.length() > 0) {
				searchTermBuf.append(" ");
			}
			searchTermBuf.append(pmid);
		}
		if(searchTermBuf.length() > 0) {
			searchTermBuf.append("[uid]");
		}
		try {
			//log.info(searchTermBuf.toString());
			populatePubmedMap(searchTermBuf.toString(), sbMap, pubmedMap, false, true, errMsg);
		} catch(Exception e) {
			log.error("pubmedQueryByPubmedId Exception: ", e);
			errMsg.append(e + "\n");
		}
	}

	/**
	 * Populate Pubmed map with metadata from Pubmed
	 * @param searchTerm the term for which to search
	 * @param sbMap the scopus bean map
	 * @param pubmedMap the pubmed map
	 * @param lookupDoi do we lookup doi
	 * @param lookupPmid do we lookup pubmed id
	 * @param errMsg error message buffer
	 */
	private void populatePubmedMap(String searchTerm, Map<String, ScopusBean> sbMap, Map<String, String> pubmedMap, boolean lookupDoi, boolean lookupPmid, StringBuffer errMsg) {
		try {
			int recToFetch = getLatestRecord();
			@SuppressWarnings("unused")
			int intBatchSize = Integer.parseInt(this.getBatchSize());
			this.setSearchTerm(searchTerm);
			String[] env = runESearch(this.getSearchTerm());
			
			// publication found in Pubmed
			if(env != null && !"null".equals(env[2]) && Integer.parseInt(env[2]) > 0) {
				fetchRecords(env, "0", "" + recToFetch);
				for(Document pubmedDoc: this.pubmedDocList) {
					if(lookupDoi) {
						populateMapByDoi(pubmedDoc, sbMap, pubmedMap);
					} else if(lookupPmid) {
						populateMapByPubmedId(pubmedDoc, sbMap, pubmedMap);
					}
				}
			}
		} catch(IOException e) {
			log.error("populatePubmedMap IOException: ", e);
			errMsg.append(e);
		} catch(TransformerException e) {
			log.error("populatePubmedMap TransformerException: ", e);
			errMsg.append(e);
		}
	}

	/**
	 * Populate Pubmed map by looking up the DOI value from ScopusBean map
	 * @param pubmedDoc the pubmed xml document
	 * @param sbMap the scopus bean map
	 * @param pubmedMap the pubmed map
	 * @throws TransformerException error transforming xml data
	 */
	private void populateMapByDoi(Document pubmedDoc, Map<String, ScopusBean> sbMap, Map<String, String> pubmedMap) throws TransformerException {
		String doi = null;
		for(Node articleIdNode : IterableAdaptor.adapt(pubmedDoc.getElementsByTagName("ArticleId"))) {
			for(Node x : IterableAdaptor.adapt(articleIdNode.getAttributes())) {
				if("doi".equals(x.getNodeValue())) {
					doi = articleIdNode.getTextContent();
				}
			}
		}
		if(doi != null) {
			// get pmid
			String pmid = null;
			NodeList pmidNodes = pubmedDoc.getElementsByTagName("PMID");
			if(pmidNodes != null) {
				pmid = pmidNodes.item(0).getTextContent();
			}
			for(String key : sbMap.keySet()) {
				ScopusBean sb = sbMap.get(key);
				if(doi.equals(sb.getDoi())) {
					sb.setIsInPubmed(true);
					sb.setPubmedId(pmid);
					// get authors
					boolean completed = populateAuthId(pubmedDoc, sb);
					if(completed) {
						pubmedMap.put(pmid, XMLAide.formatXML(pubmedDoc));
					} else {
						log.info("--- Cannot proceed with ingesting this article " + 
							"(DOI: " + doi + ") to VIVO, " + 
							"mismatch between authors in Scopus and Pubmed");
					}
					break; // found - no need to look any further
				}
			}
		}
	}

	/**
	 * Populate Pubmed map by looking up the Pubmed ID value from ScopusBean map
	 * @param pubmedDoc the pubmed xml document
	 * @param sbMap the scopus bean map
	 * @param pubmedMap the pubmed map
	 * @throws TransformerException error transforming xml data
	 */
	private void populateMapByPubmedId(Document pubmedDoc, Map<String, ScopusBean> sbMap, Map<String, String> pubmedMap) throws TransformerException {
		String pmid = null;
		NodeList pmidNodes = pubmedDoc.getElementsByTagName("PMID");
		if(pmidNodes != null) {
			pmid = pmidNodes.item(0).getTextContent();
		}
		for(String key : sbMap.keySet()) {
			ScopusBean sb = sbMap.get(key);
			if(pmid != null) {
				if(pmid.equals(sb.getPubmedId())) {
					sb.setIsInPubmed(true);
					// get authors
					boolean completed = populateAuthId(pubmedDoc, sb);
					if(completed) {
						pubmedMap.put(pmid, XMLAide.formatXML(pubmedDoc));
					} else {
						log.info("--- Cannot proceed with ingesting this article " + 
							"(Pubmed ID: " + pmid + ") to VIVO, " + 
							"mismatch between authors in Scopus and Pubmed");
					}
					break; // found - no need to look any further
				}
			}
		}
	}

	/**
	 * Populate Scopus map with Scopus metadata
	 * @param sbMap the scopus bean map
	 * @param scopusMap the scopus map
	 * @throws TransformerException error reading scopus data
	 */
	private void populateScopusMap(Map<String, ScopusBean> sbMap, Map<String, String> scopusMap) throws TransformerException {
		for(String key : sbMap.keySet()) {
			ScopusBean sb = sbMap.get(key);
			if(!sb.isInPubmed()) {
				Node entryNode = sb.getEntryNode();
				scopusMap.put(sb.getScopusDocId(), XMLAide.formatXML(entryNode));
			}
		}
	}

	/**
	 * Add Scopus Author ID to Pubmed metadata for ingest into VIVO
	 * @param pubmedDoc the pubmed xml document
	 * @param sb the scopus bean
	 * @return true if author found, false otherwise
	 */
	private boolean populateAuthId(Document pubmedDoc, ScopusBean sb) {
		boolean completed = true;

		// get Pubmed authors
		NodeList pubmedAuthorNodes = pubmedDoc.getElementsByTagName("Author");
		
		// get Scopus authors
		List<Node> scopusAuthorNodes = sb.getAuthors();
		
		boolean sameSize = false;
		
		if (pubmedAuthorNodes.getLength() == scopusAuthorNodes.size()) {
			sameSize = true;
		}
		
		if(sameSize) {
			if(pubmedAuthorNodes.getLength() == scopusAuthorNodes.size()) {
				for(int j = 0; j < pubmedAuthorNodes.getLength(); j++) {
					Node pubmedAuthorNode = pubmedAuthorNodes.item(j);
					Node scopusAuthorNode = scopusAuthorNodes.get(j);
					String scopusAuthorId = null;
//					String scopusAuthorName = null;
					for(Node scopusAuthorChildNode : IterableAdaptor.adapt(scopusAuthorNode.getChildNodes())) {
						if("authid".equals(scopusAuthorChildNode.getNodeName())) {
							scopusAuthorId = scopusAuthorChildNode.getTextContent();
							Element scopusAuthorIdEl = pubmedDoc.createElement("authid");
							scopusAuthorIdEl.setTextContent(scopusAuthorId);
							pubmedAuthorNode.appendChild(scopusAuthorIdEl);
						}
					}
				}
			}
		} else {
			for(Node pubmedAuthorNode : IterableAdaptor.adapt(pubmedAuthorNodes)) {
				for(Node pAuthCNode : IterableAdaptor.adapt(pubmedAuthorNode.getChildNodes())) {
					if ("LastName".equals(pAuthCNode.getNodeName())) {
						String lastname = pAuthCNode.getTextContent();
						boolean foundLastname = false;
						// search lastname from Scopus nodes and get authid
						for(Node sAuthNode : scopusAuthorNodes) {
							String authid = null;
							String authname = null;
							for(Node sAuthCNode : IterableAdaptor.adapt(sAuthNode.getChildNodes())) {
								if("authid".equals(sAuthCNode.getNodeName())) {
									authid = sAuthCNode.getTextContent();
								} else if("authname".equals(sAuthCNode.getNodeName())) {
									authname = sAuthCNode.getTextContent();
								}
							}
							if(authname != null && authname.toLowerCase().indexOf(lastname.toLowerCase()) > -1) {
								Element scopusAuthorIdEl = pubmedDoc.createElement("authid");
								scopusAuthorIdEl.setTextContent(authid);
								pubmedAuthorNode.appendChild(scopusAuthorIdEl);
								foundLastname = true;
								break; // lastname match found, no need to continue
							}
						}
						if(!foundLastname) {
							completed = false;
						//	log.info("--- Cannot proceed with ingesting this article to VIVO, " + "mismatch between authors in Scopus and Pubmed - lastname: " + lastname + " and DOI: "+ doi);
						}
					}
				}
			}
		}
		return completed;
	}

	/**
	 * Write query results to individual files using either Pubmed ID or Scopus Document ID as
	 * part of the file names.
	 * @param authid the authid
	 * @param pubmedMap the pubmed map
	 * @param scopusMap the scopus map
	 * @param errMsg error message buffer
	 */
	private void writeToFiles(String authid, Map<String, String> pubmedMap, Map<String, String> scopusMap, StringBuffer errMsg) {
		String header = "<?xml version=\"1.0\"?>\n<!DOCTYPE PubmedArticleSet PUBLIC \"-//NLM//DTD PubMedArticle, 1st January 2011//EN\" \"http://www.ncbi.nlm.nih.gov/entrez/query/DTD/pubmed_110101.dtd\">\n<PubmedArticleSet>\n";
		String footer = "\n</PubmedArticleSet>";
		
		try {
			for(String pmid : pubmedMap.keySet()) {
				log.trace("Pubmed Writing to output");
				String sanitizedXml = pubmedMap.get(pmid).replaceAll("<\\?xml version=\".*?>", "");
				writeRecord(authid + "_" + pmid, header + sanitizedXml.trim() + footer);
				log.trace("Pubmed Writing complete");
			}
			for(String scopusDocId : scopusMap.keySet()) {
				String oriEntry = "<entry>";
				String modEntry = "<entry xmlns:dc=\"http://purl.org/dc/elements/1.1/\" \n" +
						"xmlns:atom=\"http://www.w3.org/2005/Atom\" \n" +
						"xmlns:opensearch=\"http://a9.com/-/spec/opensearch/1.1/\" \n" +
						"xmlns:prism=\"http://prismstandard.org/namespaces/basic/2.0/\">\n";
				log.trace("Scopus Writing to output");
				writeRecord(authid + "_" + scopusDocId, scopusMap.get(scopusDocId).replace(oriEntry, modEntry));
				log.trace("Scopus Writing complete");
			}
		} catch(IOException e) {
			log.error("writeToFiles IOException: ", e);
			errMsg.append(e);
		}
	}
	
	/**
	 * Check if publication has already been ingested.
	 * If so, there is no need to re-ingest.
	 * @param doi
	 * @return
	 */
	/*
	private boolean isDoiInVivo(String doi) throws IOException {
		String query = "PREFIX bibo: <http://purl.org/ontology/bibo/doi> ASK  { ?x bibo:doi  \"" + doi + "\" }";
		boolean doiInVivo = this.vivoJena.executeAskQuery(query);
		if (doiInVivo) {
			log.trace("Document DOI: " + doi + " already exists in VIVO.");
		}
		return doiInVivo;
	}
	*/

	/**
	 * Check if publication has already been ingested.
	 * If so, there is no need to re-ingest.
	 * @param doi
	 * @return
	 */
	/*
	private boolean isScopusDocIdInVivo(String scopusDocId) throws IOException {
		String query = "PREFIX wcmc: <http://weill.cornell.edu/vivo/ontology/wcmc#> ASK  { ?x wcmc:scopusDocId  \"" + scopusDocId + "\" }";
		boolean idInVivo = this.vivoJena.executeAskQuery(query);
		if (idInVivo) {
			log.trace("Scopus Doc ID: " + scopusDocId + " already exists in VIVO.");
		}
		return idInVivo;
	}
	*/

	/**
	 * Extract DOI from Scopus feed
	 * @param resp the response
	 * @return the doi info
	 */
	@SuppressWarnings("unused")
	private List<String> extractDoi(String resp) {
		List<String> doiList = new ArrayList<String>();
		String doiTagStart = "<prism:doi>";
		String doiTagEnd = "</prism:doi>";
		String regex = doiTagStart + ".*?" + doiTagEnd;
		Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(resp);
		int c = 0;
		while(m.find()) {
			String doi = resp.substring(m.start(), m.end()).replace(doiTagStart, "").replace(doiTagEnd, "");
			doiList.add(doi);
			c++;
		}
		return doiList;
	}
	
	@Override
	public void fetchRecords(String WebEnv, String QueryKey, String retStart, String numRecords) throws IOException {
		EFetchPubmedServiceStub.EFetchRequest req = new EFetchPubmedServiceStub.EFetchRequest();
		req.setQuery_key(QueryKey);
		req.setWebEnv(WebEnv);
		req.setEmail(getEmailAddress());
		req.setTool(getToolName());
		req.setRetstart(retStart);
		req.setRetmax(numRecords);
		int retEnd = Integer.parseInt(retStart) + Integer.parseInt(numRecords);
		log.info("Fetching " + retStart + " to " + retEnd + " records from search");
		try {
			serializeFetchRequest(req);
		} catch(RemoteException e) {
			throw new IOException("Could not run search: ", e);
		}
	}
	
	/**
	 * Runs, sanitizes, and outputs the results of a EFetch request to the xmlWriter
	 * <ol>
	 * <li>create a buffer</li>
	 * <li>connect to pubmed</li>
	 * <li>run the efetch request</li>
	 * <li>get the article set</li>
	 * <li>create XML writer</li>
	 * <li>output to buffer</li>
	 * <li>dump buffer to string</li>
	 * <li>use sanitizeXML() on string</li>
	 * </ol>
	 * @param req the request to run and output results
	 * @throws IOException Unable to write XML to record
	 */
	private void serializeFetchRequest(EFetchPubmedServiceStub.EFetchRequest req) throws IOException {
		//Create buffer for raw, pre-sanitized output
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		//Connect to pubmed
		EFetchPubmedServiceStub service = new EFetchPubmedServiceStub();
		//Run the EFetch request
		EFetchResult result = service.run_eFetch(req);
		//Get the article set
		PubmedArticleSet_type0 articleSet = result.getPubmedArticleSet();
		XMLStreamWriter writer;
		try {
			//Create a temporary xml writer to our buffer
			writer = XMLOutputFactory.newInstance().createXMLStreamWriter(buffer);
			MTOMAwareXMLSerializer serial = new MTOMAwareXMLSerializer(writer);
			log.debug("Buffering records");
			//Output data
			articleSet.serialize(new QName("RemoveMe"), null, serial);
			serial.flush();
			log.debug("Buffering complete");
			log.debug("buffer size: " + buffer.size());
			//Dump buffer to String
			String iString = buffer.toString("UTF-8");
			// Sanitize string (which writes it to xmlWriter)
			sanitizeXML(iString);
		} catch(XMLStreamException e) {
			throw new IOException("Unable to write to output: ", e);
		} catch(UnsupportedEncodingException e) {
			throw new IOException("Cannot get xml from buffer: ", e);
		}
	}
	
	/**
	 * Sanitizes XML in preparation for writing to output stream
	 * <ol>
	 * <li>Removes xml namespace attributes</li>
	 * <li>Removes XML wrapper tag</li>
	 * <li>Splits each record on a new line</li>
	 * <li>Writes to outputstream writer</li>
	 * </ol>
	 * @param strInput The XML to Sanitize.
	 * @throws IOException Unable to write XML to record
	 */
	private void sanitizeXML(String strInput) throws IOException {
		log.debug("Sanitizing Output");
		log.debug("XML File Length - Pre Sanitize: " + strInput.length());
//		log.debug("====== PRE-SANITIZE ======\n"+strInput);
		String newS = strInput.replaceAll(" xmlns=\".*?\"", "");
		newS = newS.replaceAll("</?RemoveMe>", "");
		//TODO: this seems really hacky here... revise somehow?
		newS = newS.replaceAll("</PubmedArticle>.*?<PubmedArticle", "</PubmedArticle>\n<PubmedArticle");
		newS = newS.replaceAll("</PubmedBookArticle>.*?<PubmedBookArticle", "</PubmedBookArticle>\n<PubmedBookArticle");
		newS = newS.replaceAll("</PubmedArticle>.*?<PubmedBookArticle", "</PubmedArticle>\n<PubmedBookArticle");
		newS = newS.replaceAll("</PubmedBookArticle>.*?<PubmedArticle", "</PubmedBookArticle>\n<PubmedArticle");
		log.debug("XML File Length - Post Sanitze: " + newS.length());
//		log.debug("====== POST-SANITIZE ======\n"+newS);
		log.debug("Sanitization Complete");
		
		// Eliza: instead of writing to output, need to modify pubmed by incorporating
		// some scopus author data. After that, it can be written to output.
		String closingTag = "</PubmedArticle>";
		String[] pubmedXmls = newS.split(closingTag);
		this.pubmedDocList = new ArrayList<Document>(); // reset
		try {
			for(String eachItem : pubmedXmls) {
				String pubmedXml = eachItem + closingTag + "\n";
				this.pubmedDocList.add(XMLAide.getDocument(pubmedXml));
			}
		} catch(SAXException e) {
			throw new IOException("sanitizeXML SAXException: ", e);
		}
	}
	
	@Override
	protected int getLatestRecord() throws IOException {
		return Integer.parseInt(runESearch("1:8000[dp]", false)[3]);
	}
	
	@Override
	public void writeRecord(String id, String data) throws IOException {
		log.trace("Adding Record "+id);
		@SuppressWarnings("unused")
		boolean docExists = false;
		@SuppressWarnings("unused")
		String pmid = "";
		if(id.contains("_")) {
			String[] idSplit = id.split("_");
			pmid = idSplit[1];
		}
		if(this.vivoJena != null) {
			try {
				/*
				String askQuery = "PREFIX bibo: <http://purl.org/ontology/bibo/> ASK  { ?x bibo:pmid  \"" + pmid + "\" }";
				
				// look up pmid
				docExists = this.vivoJena.executeAskQuery(askQuery);
				
				if (!docExists) {
					log.trace("Adding Record "+id);
					getRh().addRecord(id, data, getClass());
				} else {
					log.trace("Record " + id + " already exists in VIVO. No further action is needed.");
				}
				*/
				log.trace("Adding Record "+id);
				getRh().addRecord(id, data, getClass());
			} catch(IOException e) {
				throw new IOException("writeRecord IOException: " + e);
			}
		}
	}
	
	/**
	 * Main method
	 * @param args commandline arguments
	 */
	public static void main(String... args) {
		Exception error = null;
		try {
			InitLog.initLogger(args, getParser("PubmedFetch", database));
			log.info("PubmedFetch: Start");
			new ScopusFetch(args).execute();
		} catch(IllegalArgumentException e) {
			log.error(e.getMessage());
			log.debug("Stacktrace:",e);
			error = e;
		} catch(UsageException e) {
			log.info("Printing Usage:");
			error = e;
		} catch(Exception e) {
			log.error(e.getMessage());
			log.debug("Stacktrace:",e);
			error = e;
		} finally {
			log.info("PubmedFetch: End");
			if(error != null) {
				System.exit(1);
			}
		}
	}
	
	/**
	 * Class to hold lookup data for each publication retrieved from Scopus.
	 * @author elc2013
	 *
	 */
	private class ScopusBean {
		/**
		 * is this publication in pubmed
		 */
		private boolean isInPubmed = false; // default
		/**
		 * the scopus document id
		 */
		private String scopusDocId;
		/**
		 * the doi
		 */
		private String doi;
		/**
		 * the issn
		 */
		private String issn;
		/**
		 * the title
		 */
		private String title;
		/**
		 * the volume number
		 */
		private String volume;
		/**
		 * the issue number
		 */
		private String issue;
		/**
		 * the range of pages
		 */
		private String pageRange;
		/**
		 * pubmed id
		 */
		private String pmid;
		/**
		 * entry node
		 */
		private Node entryNode;
		/**
		 * authors array list
		 */
		private List<Node> authors = new ArrayList<Node>();
		
		/**
		 * Constructor
		 */
		public ScopusBean() {}

		/**
		 * Set isInPubmed
		 * @param isInPubmed new value
		 */
		public void setIsInPubmed(boolean isInPubmed) {
			this.isInPubmed = isInPubmed;
		}
		
		/**
		 * Set scopusDocId
		 * @param scopusDocId new value
		 */
		public void setScopusDocId(String scopusDocId) {
			this.scopusDocId = scopusDocId;
		}
		
		/**
		 * Set authors
		 * @param authors new value
		 */
		public void setAuthors(List<Node> authors) {
			this.authors = authors;
		}
		
		/**
		 * Set doi
		 * @param doi new value
		 */
		public void setDoi(String doi) {
			this.doi = doi;
		}
		
		/**
		 * Set issn
		 * @param issn new value
		 */
		@SuppressWarnings("unused")
		public void setIssn(String issn) {
			this.issn = issn;
		}
		
		/**
		 * Set title
		 * @param title new value
		 */
		@SuppressWarnings("unused")
		public void setTitle(String title) {
			this.title = title;
		}
		
		/**
		 * Set volume
		 * @param volume new value
		 */
		@SuppressWarnings("unused")
		public void setVolume(String volume) {
			this.volume = volume;
		}
		
		/**
		 * Set issue
		 * @param issue new value
		 */
		@SuppressWarnings("unused")
		public void setIssue(String issue) {
			this.issue = issue;
		}
		
		/**
		 * Set pageRange
		 * @param pageRange new value
		 */
		@SuppressWarnings("unused")
		public void setPageRange(String pageRange) {
			this.pageRange = pageRange;
		}

		/**
		 * Set entryNode
		 * @param entryNode new value
		 */
		public void setEntryNode(Node entryNode) {
			this.entryNode = entryNode;
		}
		
		/**
		 * Set pmid
		 * @param pmid new value
		 */
		public void setPubmedId(String pmid) {
			this.pmid = pmid;
		}
		
		/**
		 * Get isInPubmed
		 * @return isInPubmed value
		 */
		public boolean isInPubmed() {
			return this.isInPubmed;
		}
		
		/**
		 * Get scopusDocId
		 * @return scopusDocId value
		 */
		public String getScopusDocId() {
			return this.scopusDocId;
		}
		
		/**
		 * Get authors
		 * @return authors value
		 */
		public List<Node> getAuthors() {
			return this.authors;
		}
		
		/**
		 * Get doi
		 * @return doi value
		 */
		public String getDoi() {
			return this.doi;
		}
		
		/**
		 * Get issn
		 * @return issn value
		 */
		@SuppressWarnings("unused")
		public String getIssn() {
			return this.issn;
		}
		
		/**
		 * Get title
		 * @return title value
		 */
		@SuppressWarnings("unused")
		public String getTitle() {
			return this.title;
		}
		
		/**
		 * Get volume
		 * @return volume value
		 */
		@SuppressWarnings("unused")
		public String getVolume() {
			return this.volume;
		}
		
		/**
		 * Get issue
		 * @return issue value
		 */
		@SuppressWarnings("unused")
		public String getIssue() {
			return this.issue;
		}
		
		/**
		 * Get pageRange
		 * @return pageRange value
		 */
		@SuppressWarnings("unused")
		public String getPageRange() {
			return this.pageRange;
		}

		/**
		 * Get pmid
		 * @return pmid value
		 */
		public String getPubmedId() {
			return this.pmid;
		}

		/**
		 * Get entryNode
		 * @return entryNode value
		 */
		public Node getEntryNode() {
			return this.entryNode;
		}
	}
}
