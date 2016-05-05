package org.vivoweb.test.harvester.util;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import junit.framework.TestCase;
import org.apache.jena.ext.com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.FileAide;
import org.vivoweb.harvester.util.HarvestLogFormatter;

/**
 * @author Rene Ziede (rziede@ufl.edu)
 *
 */
public class HarvestLogFormatterTest extends TestCase {
	
	/**
	 * Logger for debug and trace output to console.
	 */
	protected static Logger log = LoggerFactory.getLogger(HarvestLogFormatterTest.class);
	
	/**
	 * Temp directory
	 */
	private String tempDir;
	
	/**
	 * Temporary input N-Triple file.
	 */
	private File tempInputFile;
	
	/**
	 * Sample nTriple file for reformatting.
	 */
	private String nTripleFileContents =
		"<http://vivo.ufl.edu/harvested/thumbDirDownload/ufid> <http://vitro.mannlib.cornell.edu/ns/vitro/0.7#modTime> \"2012-05-04T17:15:23-04:00\"^^<http://www.w3.org/2001/XMLSchema#dateTime> ." + '\n' +
		"<http://vivo.ufl.edu/harvested/fullDirDownload/ufid> <http://vitro.mannlib.cornell.edu/ns/vitro/0.7#modTime> \"2012-05-04T17:15:23-04:00\"^^<http://www.w3.org/2001/XMLSchema#dateTime> .";
	
	/**
	 */
	@Override
	public void setUp() {
		this.tempDir = Files.createTempDir().getAbsolutePath();
	}
	
	/**
	 * @throws java.lang.Exception FileAide
	 */
	@Override
	public void tearDown() throws Exception {
		
		if(	FileAide.exists(this.tempDir) )
		{
			FileAide.delete(this.tempDir);
		}
	}
	
	/**
	 * Test method for {@link org.vivoweb.harvester.util.HarvestLogFormatter#execute()}.
	 * @throws IOException VFS
	 */
	public void testExecute() throws IOException {

		//Create a temp file and give it nTriple contents, use temp path for init on HarvestLogFormatter
		this.tempInputFile = FileAide.createTempFile("vivo-ntriple-additions", "xml");
		FileAide.setTextContent(this.tempInputFile.getAbsolutePath(), this.nTripleFileContents, true);
		
		//Parameters for HarvestLogFormatter
		Map<String, String> inFiles = new Hashtable<String, String>();
		inFiles.put("ADD", this.tempInputFile.getAbsolutePath());
		String targetHarvest = "test-harvest";
		
		String destRootDir = this.tempDir + targetHarvest + "/";
	
		HarvestLogFormatter hlf = new HarvestLogFormatter(inFiles, destRootDir, targetHarvest);

		hlf.execute();
		assertTrue(FileAide.exists(destRootDir));
		
	}
	
}
