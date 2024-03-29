/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.services;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.FileAide;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;

/**
 * Execute Sparql update in Jena model in an instance of VIVO
 * using SparqlUpdate web service
 * @author John Fereira (jaf30@cornell.edu) 
 */
public class SparqlUpdate {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(SparqlUpdate.class);
	
	/**
	 * Model to write to
	 */
	private String model;
	
	/**
	 * input rdf file
	 */
	private String inRDF;
	
	/**
	 * VIVO admin user name
	 */
	private String username;
	
	/**
	 * VIVO admin password
	 */
	private String password;
	
	/**
	 * Sparql update URL
	 */
	private String url;
	
	/**
	 * update type: add or delete
	 */
	private String type;
	
	/**
	 * Constructor
	 * @param args commandline arguments
	 * @throws IOException error parsing options
	 * @throws UsageException user requested usage message
	 */
	private SparqlUpdate(String... args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor
	 * @param argList parsed argument list
	 */
	private SparqlUpdate(ArgList argList) {
		this(
			argList.get("m"),
			argList.get("r"),
			argList.get("u"),
			argList.get("p"),
			argList.get("U"),
			argList.get("t")
		);
	}
	
	/**
	 * Library Constructor
	 * @param model Model to write to
	 * @param inRDF input rdf file
	 * @param username VIVO admin user name
	 * @param password VIVO admin password
	 * @param url Sparql update URL
	 * @param type update type: add or delete
	 */
	private SparqlUpdate(String model, String inRDF, String username, String password, String url, String type) {
		
		// setup output
		this.model = model;
		
		// load any specified rdf file data
		this.inRDF = inRDF;
		
		// output to file, if requested
		this.username = username;
		
		// get password
		this.password = password;
		
		// get url
		this.url = url;
		
		// get update type
		this.type = type;
		
		// Require model args
		if(this.model == null) {
			throw new IllegalArgumentException("Must provide an output model");
		}
		
		// Require input rdf 
		if(this.inRDF == null) {
			throw new IllegalArgumentException("Must provide an input rdf file name");
		}
		
		// Require user name 
		if(this.username == null) {
			throw new IllegalArgumentException("Must provide a VIVO admin username");
		}
		
		// Require password
		if(this.password == null) {
			throw new IllegalArgumentException("Must provide a VIVO admin password");
		}
		
		// Require sparql update url
		if(this.url == null) {
			throw new IllegalArgumentException("Must provide a Sparql Update URL");
		}
		
		if(this.type == null) {
			throw new IllegalArgumentException("Must provide an update type: add or delete");
		}
		
		if(this.type.equalsIgnoreCase("add") || (this.type.equalsIgnoreCase("delete"))) {
			// the type was specified as add or delete, that's good				
		} else {
			throw new IllegalArgumentException("The update type must be add or delete");
		}
	}
	
	/**
	 * Copy data from input to output
	 * @throws IOException error
	 */
	private void execute() throws IOException {
		StringBuffer updateBuffer = new StringBuffer();
		if(this.type.equals("add")) {
			updateBuffer.append("INSERT DATA {");
		} else {
			updateBuffer.append("DELETE DATA {");
		}
		updateBuffer.append("GRAPH <" + this.model + "> {");
		
		String rdfString = FileAide.getTextContent(this.inRDF);
		updateBuffer.append(rdfString);
		updateBuffer.append("  }");
		updateBuffer.append("}");
		System.out.println(updateBuffer.toString());
		
		CloseableHttpClient httpclient = HttpClients.createDefault();
		try {
			HttpPost httpPost = new HttpPost(this.url);
			
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("email", this.username));
			nvps.add(new BasicNameValuePair("password", this.password));
			nvps.add(new BasicNameValuePair("update", updateBuffer.toString()));
			httpPost.setEntity(new UrlEncodedFormEntity(nvps));
			CloseableHttpResponse response = httpclient.execute(httpPost);
			try {
				System.out.println(response.getStatusLine());
				HttpEntity entity = response.getEntity();
				InputStream is = entity.getContent();
				try {
					IOUtils.copy(is, System.out);
				} finally {
					is.close();
				}
			} finally {
				response.close();
			}
		} finally {
			httpclient.close();
		}
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("SparqlUpdate");
		// Inputs
		parser.addArgument(new ArgDef().setShortOption('r').setLongOpt("rdf").withParameter(true, "RDF_FILE").setDescription("rdf filename to load into output model").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('u').setLongOpt("username").withParameter(true, "USERNAME").setDescription("vivo admin user name").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('p').setLongOpt("password").withParameter(true, "PASSWORD").setDescription("vivo admin password").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('U').setLongOpt("url").withParameter(true, "URL").setDescription("sparql update url").setRequired(true));
		parser.addArgument(new ArgDef().setShortOption('t').setLongOpt("type").withParameter(true, "UPDATE TYPE").setDescription("type of update: add or delete").setRequired(true));
		// Outputs
		parser.addArgument(new ArgDef().setShortOption('m').setLongOpt("model").withParameter(true, "MODEL").setDescription("name of jena model").setRequired(true));
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
			new SparqlUpdate(args).execute();
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
