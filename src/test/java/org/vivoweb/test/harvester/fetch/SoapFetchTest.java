/**
 * 
 */
package org.vivoweb.test.harvester.fetch;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.InitLog;
import junit.framework.TestCase;

/**
 * @author jrpence
 *
 */
public class SoapFetchTest extends TestCase {
	/**
	 * SLF4J Logger
	 */
	@SuppressWarnings("unused")
	private static Logger log = LoggerFactory.getLogger(SoapFetchTest.class);
	/**
	 * url
	 */
	URL url;
	/**
	 * output stream
	 */
	OutputStream output;
	/**
	 * xml file stream
	 */
	InputStream xmlFileStream;
	/**
	 * session id
	 */
	String sesID;
	
	/**
	 * Constructor
	 * @param name test name
	 */
	public SoapFetchTest(String name) {
		super(name);
	}
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		InitLog.initLogger(null, null);
		
	}
	
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#tearDown()
	 */
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}
	
	/**
	 * Test method for {@link org.vivoweb.harvester.fetch.SOAPFetch#SOAPFetch(java.net.URL, java.io.OutputStream, java.io.InputStream, java.lang.String)}.
	 */
//	public void testSOAPFetchURLOutputStreamInputStreamString() {
//		fail("Not yet implemented");
//	}
	
	/**
	 * Test method for {@link org.vivoweb.harvester.fetch.SOAPFetch#xmlFormat2(java.lang.String)}.
	 */
//	public void testXmlFormat2() {
//		fail("Not yet implemented");
//	}
	
	/**
	 * Test method for {@link org.vivoweb.harvester.fetch.SOAPFetch#execute()}.
	 */
//	public void testExecute() {
//		fail("Not yet implemented");
//	}
	
	/**
	 * Test method for Nothing
	 */
	public void testNothing() {
		assertTrue(true);
	}
	
}
