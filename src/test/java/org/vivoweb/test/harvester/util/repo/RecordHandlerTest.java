/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.test.harvester.util.repo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import junit.framework.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.InitLog;
import org.vivoweb.harvester.util.FileAide;
import org.vivoweb.harvester.util.repo.JDBCRecordHandler;
import org.vivoweb.harvester.util.repo.JenaRecordHandler;
import org.vivoweb.harvester.util.repo.MapRecordHandler;
import org.vivoweb.harvester.util.repo.Record;
import org.vivoweb.harvester.util.repo.RecordHandler;
import org.vivoweb.harvester.util.repo.RecordMetaData;
import org.vivoweb.harvester.util.repo.SDBJenaConnect;
import org.vivoweb.harvester.util.repo.TextFileRecordHandler;

/**
 * @author Christopher Haines (chris@chrishaines.net)
 */
public class RecordHandlerTest extends TestCase {
	/**
	 * SLF4J Logger
	 */
	private static Logger log = LoggerFactory.getLogger(RecordHandlerTest.class);
	/** */
	private RecordHandler rh;
	
	@Override
	protected void setUp() throws Exception {
		InitLog.initLogger(null, null);
		this.rh = null;
	}
	
	@Override
	protected void tearDown() throws Exception {
		if(this.rh != null) {
			ArrayList<String> ids = new ArrayList<String>();
			// Get list of record ids
			for(Record r : this.rh) {
				/*
				 * Do not do this: this.rh.delRecord(r.getID()); since that will generate ConcurrentModificationException
				 */
				ids.add(r.getID());
			}
			// Delete records for all ids
			for(String id : ids) {
				this.rh.delRecord(id);
			}
			this.rh.close();
		}
	}
	
	/**
	 * Test method for
	 * {@link org.vivoweb.harvester.util.repo.RecordHandler#parseConfig(java.lang.String, java.util.Map)
	 * parseConfig(String filename, Map&lt;String,String&gt; overrideParams)}.
	 * @throws IOException error
	 */
	public void testParseNoConfigTFRH() throws IOException {
		log.info("BEGIN testParseNoConfigTFRH");
		Map<String, String> overrideParams = new HashMap<String, String>();
		overrideParams.put("rhClass", TextFileRecordHandler.class.getCanonicalName());
		overrideParams.put("fileDir", "tmp://testingNoConfRH-Text");
		this.rh = RecordHandler.parseConfig((String)null, overrideParams);
		log.info("END testParseNoConfigTFRH");
	}
	
	/**
	 * Test method for
	 * {@link org.vivoweb.harvester.util.repo.RecordHandler#parseConfig(java.lang.String, java.util.Map)
	 * parseConfig(String filename, Map&lt;String,String&gt; overrideParams)}.
	 * @throws IOException error
	 */
	public void testParseNoConfigJDBCRH() throws IOException {
		log.info("BEGIN testParseNoConfigJDBCRH");
		Map<String, String> overrideParams = new HashMap<String, String>();
		overrideParams.put("rhClass", JDBCRecordHandler.class.getCanonicalName());
		overrideParams.put("dbClass", "org.h2.Driver");
		overrideParams.put("dbUrl", "jdbc:h2:mem:TestNoConfRH-JDBC");
		overrideParams.put("dbUser", "sa");
		overrideParams.put("dbPass", "");
		overrideParams.put("dbTable", "testdb");
		overrideParams.put("dataFieldName", "data");
		this.rh = RecordHandler.parseConfig((String)null, overrideParams);
		log.info("END testParseNoConfigJDBCRH");
	}
	
	/**
	 * Test method for
	 * {@link org.vivoweb.harvester.util.repo.RecordHandler#parseConfig(java.lang.String, java.util.Map)
	 * parseConfig(String filename, Map&lt;String,String&gt; overrideParams)}.
	 * @throws IOException error
	 */
	public void testParseNoConfigJenaRH() throws IOException {
		log.info("BEGIN testParseNoConfigJenaRH");
		Map<String, String> overrideParams = new HashMap<String, String>();
		overrideParams.put("rhClass", JenaRecordHandler.class.getCanonicalName());
		overrideParams.put("dbClass", "org.h2.Driver");
		overrideParams.put("dbUrl", "jdbc:h2:mem:TestNoConfRH-Jena");
		overrideParams.put("dbUser", "sa");
		overrideParams.put("dbPass", "");
		overrideParams.put("dbType", "H2");
		overrideParams.put("type", "sdb");
		overrideParams.put("dbLayout", "layout2");
		overrideParams.put("modelName", "namedModel");
		overrideParams.put("dataFieldType", "http://localhost/jenarecordhandlerdemo#data");
		this.rh = RecordHandler.parseConfig((String)null, overrideParams);
		log.info("END testParseNoConfigJenaRH");
	}
	
	/**
	 * Test method for
	 * {@link org.vivoweb.harvester.util.repo.RecordHandler#parseConfig(java.lang.String, java.util.Map)
	 * parseConfig(String filename, Map&lt;String,String&gt; overrideParams)}.
	 * @throws IOException error
	 */
	public void testParseNoConfigNoOverFailRH() throws IOException {
		log.info("BEGIN testParseNoConfigNoOverFailRH");
		this.rh = RecordHandler.parseConfig((String)null, new HashMap<String, String>());
		assertNull(this.rh);
		log.info("END testParseNoConfigNoOverFailRH");
	}
	
	/**
	 * Test method for
	 * {@link org.vivoweb.harvester.util.repo.JDBCRecordHandler#JDBCRecordHandler(java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 * JDBCRecordHandler(String jdbcDriverClass, String connLine, String username, String password, String tableName,
	 * String dataFieldName)}.
	 * @throws IOException error
	 */
	public void testJDBCAddRecord() throws IOException {
		log.info("BEGIN testJDBCAddRecord");
		this.rh = new JDBCRecordHandler("org.h2.Driver", "jdbc:h2:mem:TestRH-JDBC", "sa", "", "testdb", "data");
		runBattery();
		log.info("END testJDBCAddRecord");
	}
	
	/**
	 * Test method for
	 * {@link org.vivoweb.harvester.util.repo.TextFileRecordHandler#TextFileRecordHandler(java.lang.String)
	 * TextFileRecordHandler(String fileDir)}.
	 * @throws IOException error
	 */
	public void testTextFileAddRecord() throws IOException {
		log.info("BEGIN testTextFileAddRecord");
		this.rh = new TextFileRecordHandler("tmp://testTFRH");
		runBattery();
		log.info("END testTextFileAddRecord");
	}
	
	/**
	 * Test method for {@link org.vivoweb.harvester.util.repo.TextFileRecordHandler#iterator()
	 * iterator()}.
	 * @throws IOException error
	 */
	public void testNoMetaTextFileIterate() throws IOException {
		log.info("BEGIN testNoMetaTextFileIterate");
		String tfrhDir = "tmp://testNoMetaTFRH";
		this.rh = new TextFileRecordHandler(tfrhDir);
		this.rh.addRecord("test123", "testing data for record 'test123'", RecordHandlerTest.class);
		this.rh.addRecord("test456", "data test on record 'test456'", RecordHandlerTest.class);
		this.rh.addRecord("funABC", "data in record 'funABC'", RecordHandlerTest.class);
		this.rh.addRecord("wooDEF", "blah data of record 'wooDEF'", RecordHandlerTest.class);
		FileAide.delete(tfrhDir+"/.metadata");
		for(Record r : this.rh) {
			log.debug("Record '" + r.getID() + "': " + r.getData());
		}
		log.info("END testNoMetaTextFileIterate");
	}
	
	/**
	 * Test method for {@link org.vivoweb.harvester.util.repo.MapRecordHandler#MapRecordHandler() MapRecordHandler()}.
	 * @throws IOException error
	 */
	public void testMapAddRecord() throws IOException {
		log.info("BEGIN testMapAddRecord");
		this.rh = new MapRecordHandler();
		runBattery();
		log.info("END testMapAddRecord");
	}
	
	/**
	 * Test method for
	 * {@link org.vivoweb.harvester.util.repo.JenaRecordHandler#JenaRecordHandler(org.vivoweb.harvester.util.repo.JenaConnect, java.lang.String)
	 * JenaRecordHandler(JenaConnect jena, String dataFieldType)}.
	 * @throws IOException error
	 */
	public void testJenaAddRecord() throws IOException {
		log.info("BEGIN testJenaAddRecord");
		this.rh = new JenaRecordHandler(new SDBJenaConnect("jdbc:h2:mem:TestRH-Jena", "sa", "", "H2", "org.h2.Driver", "layout2"), "http://localhost/jenarecordhandlerdemo#data");
		runBattery();
		log.info("END testJenaAddRecord");
	}
	
	/**
	 * @throws IOException error
	 */
	private void runBattery() throws IOException {
		runAddRecord();
		runNoModRecord();
		runModRecord();
		runDelRecord();
	}
	
	/**
	 * @throws IOException error
	 */
	private void runAddRecord() throws IOException {
		log.info("Start adding test");
		String recID = "test1";
		String recData = "MyDataIsReally Awesome";
		String recDataMD5 = RecordMetaData.md5hex(recData);
		assertTrue(this.rh.addRecord(recID, recData, this.getClass()));
		Record r = this.rh.getRecord(recID);
		log.info("Record Data: " + r.getData());
		String rDataMD5 = RecordMetaData.md5hex(r.getData());
		assertEquals(recData.trim(), r.getData().trim());
		assertEquals(recDataMD5, rDataMD5);
		log.info("End adding test");
	}
	
	/**
	 * @throws IOException error
	 */
	private void runNoModRecord() throws IOException {
		log.info("Start no mod test");
		String recID = "test1";
		String recData = "MyDataIsReally Awesome";
		assertFalse(this.rh.addRecord(recID, recData, this.getClass()));
		log.info("End no mod test");
	}
	
	/**
	 * @throws IOException error
	 */
	private void runModRecord() throws IOException {
		log.info("Start mod test");
		String recID = "test1";
		String recData = "MyDataIsReally Awesome - Again!";
		assertTrue(this.rh.addRecord(recID, recData, this.getClass()));
		log.info("End mod test");
	}
	
	/**
	 * @throws IOException error
	 */
	private void runDelRecord() throws IOException {
		log.info("Start del test");
		String recID = "test1";
		this.rh.delRecord(recID);
		try {
			this.rh.getRecord(recID);
			fail("Illegal Record ID Request Should Throw IllegalArgumentException!");
		} catch(IllegalArgumentException e) {
			// ignore since this is the expected behavior
		}
		log.info("End del test");
	}
	
}
