/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vivoweb.harvester.util.args.ArgDef;
import org.vivoweb.harvester.util.args.ArgList;
import org.vivoweb.harvester.util.args.ArgParser;
import org.vivoweb.harvester.util.args.UsageException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Export xpath selection from xml file
 * @author Christopher Haines (chris@chrishaines.net)
 */
public class XMLAide {
	/**
	 * SLF4J Logger
	 */
	protected static Logger log = LoggerFactory.getLogger(XMLAide.class);
	/**
	 * path to xml file to read data from
	 */
	private String xml;
	/**
	 * xpath expression to export
	 */
	private String exp;
	
	/**
	 * Constructor
	 * @param xmlFile path to xml file to read data from
	 * @param expression xpath expression to export
	 */
	public XMLAide(String xmlFile, String expression) {
		this.xml = xmlFile;
		this.exp = expression;
	}
	
	/**
	 * Constructor
	 * @param args commandline arguments
	 * @throws IOException error creating task
	 * @throws UsageException user requested usage message
	 */
	private XMLAide(String[] args) throws IOException, UsageException {
		this(getParser().parse(args));
	}
	
	/**
	 * Constructor
	 * @param argList arguments
	 */
	private XMLAide(ArgList argList) {
		this(argList.get("x"), argList.get("e"));
	}
	
	/**
	 * Export xpath selection from xml file
	 * @param xmlFile path to xml file to read data from
	 * @param expression xpath expression to export
	 * @return the value of the selection
	 * @throws IOException error reading xml file
	 */
	public static String getXPathResult(String xmlFile, String expression) throws IOException {
		return getXPathStreamResult(FileAide.getInputStream(xmlFile), expression);
	}
	
	/**
	 * Export xpath selection from string
	 * @param xmlString string to read data from
	 * @param expression xpath expression to export
	 * @return the value of the selection
	 * @throws IOException error reading xml file
	 */
	public static String getXPathResultFromString(String xmlString, String expression) throws IOException {
		return getXPathStreamResult(new ByteArrayInputStream(xmlString.getBytes("UTF-8")), expression);
	}

	/**
	 * @param xmlIS input stream of the xml document
	 * @param expression the expression used in xpath to find the proper location
	 * @return A string which is the resulting value of the xpath request
	 * @throws IOException thrown if there is an issue parsing the input stream
	 */
	public static String getXPathStreamResult(InputStream xmlIS, String expression) throws IOException{
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true); // never forget this!
			Document doc = factory.newDocumentBuilder().parse(new InputSource(xmlIS));
			String value = XPathFactory.newInstance().newXPath().compile(expression).evaluate(doc, XPathConstants.STRING).toString();
			return value;
		} catch(ParserConfigurationException e) {
			throw new IOException(e);
		} catch(SAXException e) {
			throw new IOException(e);
		} catch(XPathExpressionException e) {
			throw new IllegalArgumentException(e);
		}
	}
	
	/**
	 * Get an empty XML Document
	 * @return the XML Document
	 */
	public static Document getDocument() {
		return getBuilder().newDocument();
	}
	
	/**
	 * Get an XML Document from a string
	 * @param xmlStr the string
	 * @return the XML Document
	 * @throws SAXException error parsing stream contents
	 * @throws IOException error with reading document
	 */
	public static Document getDocument(String xmlStr) throws SAXException, IOException {
		return getBuilder().parse(new ByteArrayInputStream(xmlStr.getBytes("UTF-8")));
	}
	
	/**
	 * Get an XML Document from a stream
	 * @param xmlIS the stream
	 * @return the XML Document
	 * @throws IOException error reading from stream
	 * @throws SAXException error parsing stream contents
	 */
	public static Document getDocument(InputStream xmlIS) throws IOException, SAXException {
		return getBuilder().parse(xmlIS);
	}
	
	/**
	 * Get an XML Document from a stream
	 * @param xmlIS the source
	 * @return the XML Document
	 * @throws IOException error reading from stream
	 * @throws SAXException error parsing stream contents
	 */
	public static Document getDocument(InputSource xmlIS) throws IOException, SAXException {
		return getBuilder().parse(xmlIS);
	}
	
	/**
	 * Get an XML Document from a stream
	 * @return the XML Document Builder
	 */
	private static DocumentBuilder getBuilder() {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true); // never forget this!
		DocumentBuilder builder;
		try {
			builder = factory.newDocumentBuilder();
		} catch(ParserConfigurationException e) {
			throw new IllegalArgumentException(e);
		}
		return builder;
	}
	
	/**
	 * Take an XML Node and ensure there are line breaks and indentation.
	 * @param node the input XML Node
	 * @param indentSpaces number of spaces to indent each line (null gets default)
	 * @param xmlDeclaration should we have xml declaration (null gets default)
	 * @return the formatted XML string
	 * @throws TransformerException error formatting xml
	 */
	public static String formatXML(Node node, Integer indentSpaces, Boolean xmlDeclaration) throws TransformerException {
		Transformer transformer;
		try {
			transformer = TransformerFactory.newInstance().newTransformer();
		} catch(TransformerConfigurationException | TransformerFactoryConfigurationError e) {
			throw new IllegalArgumentException("XML Transformer Configuration Error", e);
		}
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, MathAide.nvl(xmlDeclaration,Boolean.TRUE).booleanValue()?"no":"yes");
		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", MathAide.nvl(indentSpaces, Integer.valueOf(4)).toString());
		
		StreamResult outputTarget = new StreamResult(new StringWriter());
		DOMSource xmlSource = new DOMSource(node);
		transformer.transform(xmlSource, outputTarget);
		
		return outputTarget.getWriter().toString();
	}
	
	/**
	 * Take an XML Node and ensure there are line breaks and indentation.
	 * @param node the input XML Node
	 * @param xmlDeclaration should we have xml declaration (null gets default)
	 * @return the formatted XML string
	 * @throws TransformerException error formatting xml
	 */
	public static String formatXML(Node node, Boolean xmlDeclaration) throws TransformerException {
		return formatXML(node, null, xmlDeclaration);
	}
	
	/**
	 * Take an XML Node and ensure there are line breaks and indentation.
	 * @param node the input XML Node
	 * @param indentSpaces number of spaces to indent each line (null gets default)
	 * @return the formatted XML string
	 * @throws TransformerException error formatting xml
	 */
	public static String formatXML(Node node, Integer indentSpaces) throws TransformerException {
		return formatXML(node, indentSpaces, null);
	}
	
	/**
	 * Take an XML Node and ensure there are line breaks and indentation.
	 * @param node the input XML Node
	 * @return the formatted XML string
	 * @throws TransformerException error formatting xml
	 */
	public static String formatXML(Node node) throws TransformerException {
		return formatXML(node, null, null);
	}
	
	/**
	 * Take an XML string and ensure there are line breaks and indentation.
	 * @param inputXml the input XML string
	 * @param indentSpaces number of spaces to indent each line (null gets default)
	 * @param xmlDeclaration should we have xml declaration (null gets default)
	 * @return the formatted XML string
	 */
	public static String formatXML(String inputXml, Integer indentSpaces, Boolean xmlDeclaration) {
		try {
			return formatXML(getDocument(inputXml), indentSpaces, xmlDeclaration);
		} catch(TransformerException | SAXException | IOException e) {
			log.error(e.getMessage(), e);
			return inputXml; //log error and then just don't format the result
		}
	}
	
	/**
	 * Take an XML string and ensure there are line breaks and indentation.
	 * @param inputXml the input XML string
	 * @param xmlDeclaration should we have xml declaration (null gets default)
	 * @return the formatted XML string
	 */
	public static String formatXML(String inputXml, Boolean xmlDeclaration) {
		return formatXML(inputXml, null, xmlDeclaration);
	}
	
	/**
	 * Take an XML string and ensure there are line breaks and indentation.
	 * @param inputXml the input XML string
	 * @param indentSpaces number of spaces to indent each line (null gets default)
	 * @return the formatted XML string
	 */
	public static String formatXML(String inputXml, Integer indentSpaces) {
		return formatXML(inputXml, indentSpaces, null);
	}
	
	/**
	 * Take an XML string and ensure there are line breaks and indentation.
	 * @param inputXml the input XML string
	 * @return the formatted XML string
	 */
	public static String formatXML(String inputXml) {
		return formatXML(inputXml, null, null);
	}
	
	/**
	 * Runs the xpath
	 * @throws IOException error executing
	 */
	public void execute() throws IOException {
		System.out.println(getXPathResult(this.xml, this.exp));
	}
	
	/**
	 * Get the ArgParser for this task
	 * @return the ArgParser
	 */
	private static ArgParser getParser() {
		ArgParser parser = new ArgParser("XPathTool");
		// Inputs
		parser.addArgument(new ArgDef().setShortOption('x').setLongOpt("xml-file").withParameter(true, "XML_FILE").setDescription("path to xml file to read data from").setRequired(true));
		// Params
		parser.addArgument(new ArgDef().setShortOption('e').setLongOpt("expression").withParameter(true, "XPATH_EXPRESSION").setDescription("xpath expression to export").setRequired(true));
		return parser;
	}
	
	/**
	 * Main method
	 * @param args commandline arguments
	 */
	public static void main(String... args) {
		Exception error = null;
		try {
			String harvLev = System.getProperty("console-log-level");
			System.setProperty("console-log-level", "OFF");
			InitLog.initLogger(args, getParser(), "h");
			if(harvLev == null) {
				System.clearProperty("console-log-level");
			} else {
				System.setProperty("console-log-level", harvLev);
			}
			log.info(getParser().getAppName() + ": Start");
			new XMLAide(args).execute();
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
