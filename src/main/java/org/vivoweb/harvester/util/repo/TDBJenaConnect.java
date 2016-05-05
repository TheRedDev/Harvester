/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.util.repo;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.FileAide;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.tdb.TDB;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.tdb.base.block.FileMode;
import org.apache.jena.tdb.sys.SystemTDB;

/**
 * Connection Helper for TDB Jena Models
 * @author Christopher Haines (chris@chrishaines.net)
 */
public class TDBJenaConnect extends JenaConnect {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(TDBJenaConnect.class);
	/**
	 * Mapping of directory to Dataset
	 */
	private static HashMap<String, Dataset> dirDatasets = new HashMap<String, Dataset>();
	/**
	 * Map of references to each Dataset
	 */
	private static HashMap<Dataset, HashSet<TDBJenaConnect>> dsRefs = new HashMap<Dataset, HashSet<TDBJenaConnect>>();
	/**
	 * the TDB directory name
	 */
	private final String dbDir;
	
	/**
	 * Constructor (Default Model)
	 * @param dbDir tdb directory name
	 */
	public TDBJenaConnect(String dbDir) {
		this(dbDir, null);
	}
	
	/**
	 * Constructor (TDB Named Model)
	 * @param dbDir tdb directory name
	 * @param modelName the model to connect to
	 */
	public TDBJenaConnect(String dbDir, String modelName) {
		SystemTDB.setFileMode(FileMode.direct);
		this.dbDir = dbDir;
		try {
			FileAide.createFolder(this.dbDir);
		} catch(IOException e) {
			throw new IllegalArgumentException("Invalid Directory", e);
		}
		
		if (modelName != null) {
			setModelName(modelName);
		    Model m = getDataset().getNamedModel(getModelName());
		    log.trace("model "+ modelName +" size: "+m.size());
			setJenaModel(m);
		} else {
			//setModelName(Quad.defaultGraphIRI.getURI());
			Model m = getDataset().getDefaultModel();
			Iterator<String> iter = getDataset().listNames();
			while (iter.hasNext()) {
				log.trace("ds: "+iter.next());
			}
			log.trace("model size: "+m.size());
			setJenaModel(m);
		}
		sync(); 
	}
	
	@Override
	public Dataset getDataset() {
		if(!dirDatasets.containsKey(this.dbDir)) {
			dirDatasets.put(this.dbDir, TDBFactory.createDataset(this.dbDir));
		}
		Dataset ds = dirDatasets.get(this.dbDir);
		if(!dsRefs.containsKey(ds)) {
			dsRefs.put(ds, new HashSet<TDBJenaConnect>());
		}
		HashSet<TDBJenaConnect> dsRef = dsRefs.get(ds);
		if(!dsRef.contains(this)) {
			dsRef.add(this);
		}
		return ds;
	}
	
	@Override
	public JenaConnect neighborConnectClone(String modelName) {
		return new TDBJenaConnect(this.dbDir, modelName);
	}
	
	@Override
	public void close() {
		super.close();
		try {
			getJenaModel().close();
		} catch(NullPointerException e) {
			// Do Nothing
		}
		Dataset ds = getDataset();
		HashSet<TDBJenaConnect> dsRef = dsRefs.get(ds);
		dsRef.remove(this);
		if(dsRef.isEmpty()) {
			dsRefs.remove(ds);
			dirDatasets.remove(this.dbDir);
			ds.close();
		}
	}
	
	/**
	 * Get the dbDir
	 * @return dbDir
	 */
	protected String getDbDir() {
		return this.dbDir;
	}

	@Override
	public void printParameters() {
		super.printParameters();
		log.trace("type: 'tdb'");
		log.trace("dbDir: '" + this.dbDir + "'");
	}
	
	@Override
	public void sync() {
		log.trace("sync tdb");
		TDB.sync(getDataset());	
	}
}
