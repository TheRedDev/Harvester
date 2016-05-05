/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.qualify;

import java.io.IOException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.IterableAdaptor;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.vivoweb.harvester.util.repo.JenaConnect;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Statement;

/**
 * Qualify data using SPARQL queries
 * @author Christopher Haines (chris@chrishaines.net)
 * @author Nicholas Skaggs (nskaggs@ctrip.ufl.edu)
 */
public class Qualify {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(Qualify.class);
	/**
	 * Jena Model we are working in
	 */
	private final JenaConnect model;
	/**
	 * The data predicate
	 */
	private final String dataPredicate;
	/**
	 * The string to match
	 */
	private final String matchTerm;
	/**
	 * The value to replace it with
	 */
	private final String newVal;
	/**
	 * Is this to use Regex to match the string
	 */
	private final boolean regex;
	/**
	 * the namespace you want removed
	 */
	private final String namespace;
	/**
	 * remove all statements where the predicate is from the given namespace
	 */
	private final boolean cleanPredicates;
	/**
	 * remove all statements where the subject or object is from the given namespace
	 */
	private final boolean cleanResources;
	
	/**
	 * Constructor
	 * @param args commandline arguments
	 * @throws IOException error creating task
	 * @throws UsageException user requested usage message
	 */
	private Qualify(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor
	 * @param argList parsed argument list
	 * @throws IOException error creating task
	 */
	private Qualify(ArgList argList) throws IOException {
		this(
			JenaConnect.parseConfig(argList.get("i"), argList.getValueMap("I")), 
			argList.get("d"), 
			(argList.has("r") ? argList.get("r") : argList.get("t")), 
			argList.get("v"), 
			argList.has("r"), 
			argList.get("n"), 
			argList.has("p"), 
			argList.has("c")
		);
		if(argList.has("r") && argList.has("t")) {
			log.warn("Both text and regex matchTerm provided, using only regex");
		}
	}
	
	/**
	 * Constructor
	 * @param jenaModel the JENA model to run qualifications on
	 * @param dataType the data predicate
	 * @param matchString the string to match
	 * @param newValue the value to replace it with
	 * @param isRegex is this to use Regex to match the string
	 * @param removeNameSpace remove statements with predicates in this namespace
	 * @param cleanPredicates remove all statements where the predicate is from the given namespace
	 * @param cleanResources remove all statements where the subject or object is from the given namespace
	 */
	public Qualify(JenaConnect jenaModel, String dataType, String matchString, String newValue, boolean isRegex, String removeNameSpace, boolean cleanPredicates, boolean cleanResources) {
		this.model = jenaModel;
		if(this.model == null) {
			throw new IllegalArgumentException("Must provide a jena model");
		}
		this.dataPredicate = dataType;
		this.matchTerm = matchString;
		this.newVal = newValue;
		this.regex = isRegex;
		this.namespace = removeNameSpace;
		this.cleanPredicates = cleanPredicates;
		this.cleanResources = cleanResources;
		if(StringUtils.isBlank(this.namespace)) {
			if(StringUtils.isBlank(this.dataPredicate)) {
				throw new IllegalArgumentException("Must specify either a dataPredicate or a removeNamespace");
			}
			if(this.cleanPredicates && this.cleanResources) {
				throw new IllegalArgumentException("Cannot specify cleanPredicates and cleanResources when removeNamepsace is empty");
			}
			if(this.cleanPredicates) {
				throw new IllegalArgumentException("Cannot specify cleanPredicates when removeNamepsace is empty");
			}
			if(this.cleanResources) {
				throw new IllegalArgumentException("Cannot specify cleanResources when removeNamepsace is empty");
			}
		}
	}
	
	/**
	 * Replace records exactly matching uri & datatype & oldValue with newValue
	 */
	private void strReplace() {
		for(Statement stmt : this.model.getJenaModel().listStatements(null, this.model.getJenaModel().createProperty(this.dataPredicate), this.matchTerm).toList()) {
			if(this.namespace == null || stmt.getSubject().getURI().startsWith(this.namespace)) {
				if(this.newVal != null) {
					log.trace("Replacing record: "+stmt);
				} else {
					log.trace("Removing record: "+stmt);
				}
				if(this.newVal != null) {
					log.debug("newValue: " + this.newVal);
					stmt.changeObject(this.newVal);
				} else {
					stmt.remove();
				}
			}
		}
	}
	
	/**
	 * Replace records matching predicate & the regexMatch with newValue
	 * @throws IOException error connecting
	 */
	private void regexReplace() throws IOException {
		StringBuffer sb = new StringBuffer();
		sb.append("SELECT ?s ?o \n");
		sb.append("WHERE {\n");
		sb.append("  ?s <").append(this.dataPredicate).append("> ?o .\n");
		sb.append("  FILTER (regex(str(?o), \"").append(this.matchTerm).append("\", \"s\")) .\n");
		if(this.namespace != null) {
			sb.append("  FILTER (regex(str(?s), \"^" + this.namespace + "\")) .\n");
		}
		sb.append("}");
		String query = sb.toString();
		log.debug(query);
		StringBuilder insertQ = new StringBuilder("INSERT DATA {\n");
		StringBuilder deleteQ = new StringBuilder("DELETE DATA {\n");
		int modifyCounter = 0;
		for(QuerySolution s : IterableAdaptor.adapt(this.model.executeSelectQuery(query))) {
			modifyCounter++;
			Literal obj = s.getLiteral("o");
			RDFDatatype datatype = obj.getDatatype();
			if(datatype.getURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#langString")) {
				datatype = null;
			}
			String lang = obj.getLanguage();
			String objStr = obj.getValue().toString();
			String oldStr = encodeString(objStr, datatype, lang);
			if(this.newVal != null) {
				log.trace("Replacing record");
			} else {
				log.trace("Removing record");
			}
			log.debug("oldValue: " + oldStr);
			String sUri = s.getResource("s").getURI();
			deleteQ.append("  <" + sUri + "> <" + this.dataPredicate + "> " + oldStr + " . \n");
			if(this.newVal != null) {
				String newStr = encodeString(objStr.replaceAll(this.matchTerm, this.newVal), datatype, lang);
				log.debug("newValue: " + newStr);
				insertQ.append("  <" + sUri + "> <" + this.dataPredicate + "> " + newStr + " . \n");
			}
		}
		log.debug("Modifying " + Integer.toString(modifyCounter) + " Records.");
		insertQ.append("} \n");
		deleteQ.append("} \n");
		log.debug("Removing old data:\n" + deleteQ);
		this.model.executeUpdateQuery(deleteQ.toString());
		if(this.newVal != null) {
			log.debug("Inserting updated data:\n" + insertQ);
			this.model.executeUpdateQuery(insertQ.toString());
		}
	}
	
	/**
	 * Encode a string with its rdfdatatype or lang
	 * @param str the string
	 * @param datatype the datatype
	 * @param lang the language
	 * @return the encoded string
	 */
	private String encodeString(String str, RDFDatatype datatype, String lang) {
		String encStr = "\"" + str + "\"";
		if(datatype != null) {
			encStr += "^^<" + datatype.getURI().trim() + ">";
		} else if(StringUtils.isNotBlank(lang)) {
			encStr += "@" + lang.trim();
		}
		return encStr;
	}
	
	/**
	 * Remove all subjects and objects in a given namespace
	 * @param ns the namespace to remove all resources from
	 * @throws IOException error connecting
	 */
	private void cleanResources(String ns) throws IOException {
		String query = "" + "DELETE { ?s ?p ?o } " 
		+ "WHERE { " + "?s ?p ?o .  " 
		+ "FILTER (regex(str(?s), \"^" + ns + "\" ) || regex(str(?o), \"^" + ns + "\" ))" + "}";
		log.debug(query);
		this.model.executeUpdateQuery(query);
	}
	
	/**
	 * Remove all predicates in a given namespace
	 * @param ns the namespace to remove all predicates from
	 * @throws IOException error connecting
	 */
	private void cleanPredicates(String ns) throws IOException {
		String predicateQuery = "" + "DELETE { ?s ?p ?o } " 
		+ "WHERE { " + "?s ?p ?o .  " 
		+ "FILTER regex(str(?p), \"^" + ns + "\" ) " + "}";
		log.debug(predicateQuery);
		this.model.executeUpdateQuery(predicateQuery);
	}
	
	/**
	 * Executes the task
	 * @throws IOException error connecting
	 */
	public void execute() throws IOException {
		if(StringUtils.isNotBlank(this.namespace)) {
			if(this.cleanPredicates) {
				log.info("Running clean predicates for " + this.namespace);
				cleanPredicates(this.namespace);
			}
			if(this.cleanResources) {
				log.info("Running clean resources for " + this.namespace);
				cleanResources(this.namespace);
			}
		}
		if(StringUtils.isNotBlank(this.dataPredicate)) {
			String ns = null;
			if(this.namespace != null) {
				ns = "Limiting to resources with URI beginning with \"" + this.namespace + "\"";
			}
			if(this.regex && this.matchTerm != null) {
				if(this.newVal != null) {
					log.info("Running regex match replace '" + this.dataPredicate + "': '" + this.matchTerm + "' with '" + this.newVal + "'");
				} else {
					log.info("Running regex match remove '" + this.dataPredicate + "': '" + this.matchTerm + "'");
				}
				if(ns != null) {
					log.info(ns);
				}
				regexReplace();
			} else {
				if(this.matchTerm != null) {
					if(this.newVal != null) {
						log.info("Running text match replace '" + this.dataPredicate + "': '" + this.matchTerm + "' with '" + this.newVal + "'");
					} else {
						log.info("Running text match remove '" + this.dataPredicate + "': '" + this.matchTerm + "'");
					}
				} else {
					if(this.newVal != null) {
						log.info("Running replace all '" + this.dataPredicate + "' with '" + this.newVal + "'");
					} else {
						log.info("Running remove all '" + this.dataPredicate + "'");
					}
				}
				if(ns != null) {
					log.info(ns);
				}
				strReplace();
			}
		}
		this.model.sync();
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("Qualify");
		parser.addArgument(new ArgDef().setShortOption('i').setLongOpt("jenaConfig").setDescription("config file for jena model").withParameter(true, "CONFIG_FILE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('I').setLongOpt("jenaOverride").setDescription("override the JENA_PARAM of jena model config using VALUE").withParameterValueMap("JENA_PARAM", "VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('d').setLongOpt("dataType").setDescription("remove statements with predicate of specified data type (rdf predicate) optionally limited to resource uris starting with -n/--remove-namespace").withParameter(true, "RDF_PREDICATE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('r').setLongOpt("regexMatch").setDescription("filter for -d/--datatype where object matches this regex expression").withParameter(true, "REGEX").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('t').setLongOpt("textMatch").setDescription("filter for -d/--datatype where object matches this exact text string").withParameter(true, "MATCH_STRING").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('v').setLongOpt("value").setDescription("option for -d/--datatype where matching values of -r/--regexMatch or -t/--textMatch are replaced with this value").withParameter(true, "REPLACE_VALUE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('n').setLongOpt("remove-namespace").setDescription("specify namespace for -p/--predicate-clean and -c/--clean-resources flag or optional namespace filter for -d/--dataType flag").withParameter(true, "RDF_NAMESPACE").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('p').setLongOpt("predicate-clean").setDescription("remove all statements where the predicate is from the given -n/--remove-namespace").setRequired(false));
		parser.addArgument(new ArgDef().setShortOption('c').setLongOpt("clean-resources").setDescription("remove all statements where the subject or object is from the given -n/--remove-namespace").setRequired(false));
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
			new Qualify(args).execute();
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
