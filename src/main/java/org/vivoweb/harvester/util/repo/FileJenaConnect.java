/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.util.repo;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a file an RDF File as a JenaConnect
 * @author Christopher Haines (chris@chrishaines.net)
 */
public class FileJenaConnect extends MemJenaConnect {
	/**
	 * SLF4J Logger
	 */
	@SuppressWarnings("hiding")
	private static Logger log = LoggerFactory.getLogger(FileJenaConnect.class);
	
	/**
	 * the rdf file we are wrapping around
	 */
	private final String filepath;
	
	/**
	 * the serialization language
	 */
	private final String language;
	
	/**
	 * Constructor
	 * @param filepath path to the file
	 * @throws IOException error reading file
	 */
	public FileJenaConnect(String filepath) throws IOException {
		this(filepath, null, null);
	}
	
	/**
	 * Constructor
	 * @param filepath path to the file
	 * @param language the language the rdf is in. Predefined values for lang are "RDF/XML", "N-TRIPLE", "TURTLE" (or
	 *        "TTL") and "N3". null represents the default language, "RDF/XML". "RDF/XML-ABBREV" is a synonym for
	 *        "RDF/XML"
	 * @throws IOException error reading file
	 */
	public FileJenaConnect(String filepath, String language) throws IOException {
		this(filepath, null, language);
	}
	
	/**
	 * Constructor
	 * @param filepath path to the file
	 * @param namespace the base uri to use for imported uris
	 * @param language the language the rdf is in. Predefined values for lang are "RDF/XML", "N-TRIPLE", "TURTLE" (or
	 *        "TTL") and "N3". null represents the default language, "RDF/XML". "RDF/XML-ABBREV" is a synonym for
	 *        "RDF/XML"
	 * @throws IOException error reading file
	 */
	public FileJenaConnect(String filepath, String namespace, String language) throws IOException {
		super(null);
		log.trace("loading data from input...");
		loadRdfFromFile(filepath, namespace, language);
		log.trace("model size: "+getDataset().getDefaultModel().size());
		this.filepath = filepath;
		this.language = language;
	}
	
	@Override
	public void sync() {
		if(this.filepath != null) {
			log.trace("Syncronizing the model..."+this.filepath);
			try {
				exportRdfToFile(this.filepath, this.language);
				log.trace("Syncronization of model complete");
			} catch(IOException e) {
				log.error("Failed to syncronize the model!");
				log.debug("Stacktrace:",e);
			}
		}
		super.sync();
	}
}
