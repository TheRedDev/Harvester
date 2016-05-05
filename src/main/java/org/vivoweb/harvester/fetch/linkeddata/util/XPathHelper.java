/* $This file is distributed under the terms of the license in /doc/license.txt$ */

package org.vivoweb.harvester.fetch.linkeddata.util;

import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.vivoweb.harvester.fetch.linkeddata.util.xml.XmlNamespaceContext;
import org.vivoweb.harvester.fetch.linkeddata.util.xml.XmlPrefix;
import org.vivoweb.harvester.util.IterableAdaptor;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList; 

/**
 * TODO
 */
public class XPathHelper {
	/**
	 * the rdf namespace
	 */
	public static final XmlPrefix RDF_PREFIX = new XmlPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");

	/**
	 * Create a helper instance with an optional list of prefixes.
	 * @param xmlPrefixes the prefixes
	 * @return the XPathHelper object
	 */
	public static XPathHelper getHelper(XmlPrefix... xmlPrefixes) {
		return new XPathHelper(xmlPrefixes);
	}

	/**
	 * the xpath object loaded with the prefixes
	 */
	private final XPath xpath;

	/**
	 * Constructor
	 * @param xmlPrefixes the prefixes
	 */
	public XPathHelper(XmlPrefix[] xmlPrefixes) {
		this.xpath = XPathFactory.newInstance().newXPath();
		this.xpath.setNamespaceContext(new XmlNamespaceContext(xmlPrefixes));
	}

	/**
	 * Search for an Xpath pattern in the context of a node, returning a handy
	 * list.
	 * @param pattern the pattern for which to search
	 * @param context the node to search within
	 * @return list of nodes
	 * @throws XpathHelperException error parsing xml/xpath
	 */
	public List<Node> findNodes(String pattern, Node context) throws XpathHelperException {
		try {
			XPathExpression xpe = this.xpath.compile(pattern);
			List<Node> list = new ArrayList<Node>();
			for(Node n : IterableAdaptor.adapt((NodeList) xpe.evaluate(context, XPathConstants.NODESET))) {
				list.add(n);
			}
			return list;
		} catch(XPathExpressionException e) {
			throw new XpathHelperException("Can't parse '" + pattern + "' in this context.", e);
		}  
	}

	/**
	 * Search for the first node in this context that matches the Xpath pattern.
	 * If not found, return null.
	 * @param pattern the pattern for which to search
	 * @param context the node in which to search
	 * @return the first matching node, null if none
	 * @throws XpathHelperException error parsing xml/xpath
	 */
	public Node findFirstNode(String pattern, Node context) throws XpathHelperException {
		try {
			XPathExpression xpe = this.xpath.compile(pattern);
			NodeList nodes = (NodeList) xpe.evaluate(context, XPathConstants.NODESET);
			if(nodes.getLength() == 0) {
				return null;
			}
			return nodes.item(0);
		} catch(XPathExpressionException e) {
			throw new XpathHelperException("Can't parse '" + pattern + "' in this context.", e);
		}
	}

	/**
	 * Search for the first node in this context that matches the Xpath pattern.
	 * If not found, throw an exception.
	 * @param pattern the pattern for which to search
	 * @param context the node in which to search
	 * @return the first matching node, exception on none
	 * @throws XpathHelperException error parsing xml/xpath
	 */
	public Node findRequiredNode(String pattern, Node context) throws XpathHelperException {
		Node result = findFirstNode(pattern, context);
		if(result != null) {
			return result;
		}
		throw new XpathHelperException("Can't find a node that matches '" + pattern + "' within this context.");
	}

	/**
	 * exception class
	 */
	public static class XpathHelperException extends Exception {
		/**
		 * 
		 */
		private static final long serialVersionUID = 6392550313989928935L;

		/**
		 * Constructor
		 * @param message the message
		 * @param cause the cause
		 */
		public XpathHelperException(String message, Throwable cause) {
			super(message, cause);
		}

		/**
		 * Constructor
		 * @param message the message
		 */
		public XpathHelperException(String message) {
			super(message);
		}
	}
}
