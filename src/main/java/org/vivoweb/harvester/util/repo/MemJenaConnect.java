/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.util.repo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.FileAide;
import org.vivoweb.harvester.util.SpecialEntities;

/**
 * Connection Helper for Memory Based Jena Models
 * @author Christopher Haines (chris@chrishaines.net)
 */
public class MemJenaConnect extends TDBJenaConnect {
	/**
	 * SLF4J Logger
	 */
	static Logger log = LoggerFactory.getLogger(MemJenaConnect.class);
	/**
	 * Map of already used memory model names to directories
	 */
	private static HashMap<String, String> usedModelNames = new HashMap<String, String>();
	/**
	 * Set of opened
	 */
	static Set<MemJenaConnect> openMemJCs;
	
	/**
	 * Constructor (Memory Default Model)
	 */
	public MemJenaConnect() {
		this(null);
	}
	
	/**
	 * Constructor (Memory Named Model)
	 * @param modelName the model name to use
	 */
	public MemJenaConnect(String modelName) {
		super(getDir(modelName), modelName);
		register(this);
	}
	
	/**
	 * Constructor (Load rdf from input stream)
	 * @param in input stream to load rdf from
	 * @param namespace the base uri to use for imported uris
	 * @param language the language the rdf is in. Predefined values for lang are "RDF/XML", "N-TRIPLE", "TURTLE" (or
	 *        "TTL") and "N3". null represents the default language, "RDF/XML". "RDF/XML-ABBREV" is a synonym for
	 *        "RDF/XML"
	 */
	public MemJenaConnect(InputStream in, String namespace, String language) {
		this(null);
		loadRdfFromStream(in, namespace, language);
		log.trace("loading data from input...");
		log.trace("model size: "+getDataset().getDefaultModel().size());
	}
	
	/**
	 * Clone Constructor
	 * @param original the MemJenaConnect to clone 
	 * @param modelName the new model name
	 */
	private MemJenaConnect(MemJenaConnect original, String modelName) {
		super(original.getDbDir(), modelName);
		register(this);
	}
	
	@Override
	public JenaConnect neighborConnectClone(String modelName) {
		return new MemJenaConnect(this, modelName);
	}
	
	/**
	 * Get the directory in which the model named is held
	 * @param modelName the model name
	 * @return the directory path
	 */
	private static String getDir(String modelName) {
		String mod = (modelName != null) ? modelName : generateUnusedModelName();
		mod = SpecialEntities.xmlEncode(mod, '/', ':');
		if(!usedModelNames.containsKey(mod)) {
			log.trace("attempting to create temp file for: " + mod);
			File f;
			try {
				f = FileAide.createTempFile(mod, ".tdb", false);
			} catch(IOException e) {
				throw new IllegalArgumentException(e);
			}
			log.trace("created: " + f.getAbsolutePath());
			f.delete();
			usedModelNames.put(mod, f.getAbsolutePath());
		}
		return usedModelNames.get(mod);
	}
	
	/**
	 * Register a MemJenaConnect to be closed at runtime shutdown
	 * @param mjc the MemJenaConnect to register
	 */
	private static synchronized void register(MemJenaConnect mjc) {
		if(openMemJCs == null) {
			openMemJCs = new LinkedHashSet<MemJenaConnect>();
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					synchronized (MemJenaConnect.openMemJCs) {
						for(MemJenaConnect omjc : MemJenaConnect.openMemJCs) {
							log.debug("closing MemJenaConnect: "+omjc.getDbDir());
							try {
								omjc.close();
							}catch(NullPointerException e) {
								log.error("Error closing MemJenaConnect: "+omjc.getDbDir(), e);
							}
						}
						for(MemJenaConnect omjc : MemJenaConnect.openMemJCs) {
							String fpath = omjc.getDbDir();
							try {
								if(!FileAide.delete(fpath) ) {
									log.warn("Failed to delete temporary file space {}, please remove manually  ",fpath);
								} else {
									log.trace("Deleted temporary file space {}  ",fpath);
								}
							} catch(IOException e) {
								log.warn("Error deleting temporary file space "+fpath+", please remove manually  ", e);
							}
						}
						MemJenaConnect.openMemJCs.clear();
						MemJenaConnect.openMemJCs = null;
					}
				}
			});
		}
		synchronized (MemJenaConnect.openMemJCs) {
			MemJenaConnect.openMemJCs.add(mjc);
		}
	}
	
	@Override
	public void close() {
		super.close();
	}
	
	/**
	 * Get an unused memory model name
	 * @return the name
	 */
	private static String generateUnusedModelName() {
		Random random = new Random();
		String name = null;
		while(name == null) {
			name = "DEFAULT" + random.nextInt(Integer.MAX_VALUE);
			if(usedModelNames.containsKey(name)) {
				name = null;
			}
		}
		return name;
	}
}
