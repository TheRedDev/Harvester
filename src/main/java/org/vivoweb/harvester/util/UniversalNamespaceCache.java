package org.vivoweb.harvester.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Cache to store namespaces
 */
public class UniversalNamespaceCache implements NamespaceContext {
	/**
	 * The default namespace
	 */
	private static final String DEFAULT_NS = "DEFAULT";
	/**
	 * map prefixes to uris
	 */
	Map<String, String> prefix2Uri = new HashMap<String, String>();
	
	/**
	 * This constructor parses the document and stores all namespaces it can
	 * find. If toplevelOnly is true, only namespaces in the root are used.
	 * @param document source document
	 * @param toplevelOnly restriction of the search to enhance performance
	 */
	public UniversalNamespaceCache(Document document, boolean toplevelOnly) {
		examineNode(document.getFirstChild(), toplevelOnly);
	}
	
	/**
	 * A single node is read, the namespace attributes are extracted and stored.
	 * @param node the node to examine
	 * @param attributesOnly if true no recursion happens
	 */
	private void examineNode(Node node, boolean attributesOnly) {
		NamedNodeMap attributes = node.getAttributes();
		if(attributes != null) {
			for(Attr attribute : IterableAdaptor.adapt(attributes, Attr.class)) {
				storeAttribute(attribute);
			}
			if(!attributesOnly) {
				for(Node child : IterableAdaptor.adapt(node.getChildNodes())) {
					if(child.getNodeType() == Node.ELEMENT_NODE) {
						examineNode(child, false);
					}
				}
			}
		}
	}
	
	/**
	 * This method looks at an attribute and stores it, if it is a namespace
	 * attribute.
	 * @param attribute the attribute to examine
	 */
	private void storeAttribute(Attr attribute) {
		// examine the attributes in namespace xmlns
		if(attribute.getNamespaceURI() != null && attribute.getNamespaceURI().equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
			// Default namespace xmlns="uri goes here"
			if(attribute.getNodeName().equals(XMLConstants.XMLNS_ATTRIBUTE)) {
				putInCache(DEFAULT_NS, attribute.getNodeValue());
			} else {
				// The defined prefixes are stored here
				putInCache(attribute.getLocalName(), attribute.getNodeValue());
			}
		}
		
	}
	
	/**
	 * Store prefix and uri pair in the maps
	 * @param prefix the prefix to store
	 * @param uri the uri to store
	 */
	private void putInCache(String prefix, String uri) {
		this.prefix2Uri.put(prefix, uri);
	}
	
	/**
	 * Get the mapping of prefixes to uris
	 * @return the prefix2uri map
	 */
	public Map<String, String> getPrefix2UriMap() {
		return this.prefix2Uri;
	}
	
	@Override
	public String getNamespaceURI(String prefix) {
		if(prefix == null || prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
			return this.prefix2Uri.get(DEFAULT_NS);
		}
		return this.prefix2Uri.get(prefix);
	}
	
	@Override
	public String getPrefix(String namespaceURI) {
		if(namespaceURI == null) {
			throw new IllegalArgumentException("Cannot request prefix for null namespace");
		}
		for(Entry<String, String> entry : this.prefix2Uri.entrySet()) {
			if(entry.getValue().equals(namespaceURI)) {
				return entry.getKey();
			}
		}
		return null;
	}

	/**
	 * @author Christopher Haines (chris@chrishaines.net)
	 */
	@Override
	public Iterator<String> getPrefixes(final String namespaceURI) {
		return new Iterator<String>() {
			private Iterator<String> wrapped;
			{
				if(namespaceURI == null) {
					throw new IllegalArgumentException("Cannot request prefixes for null namespace");
				}
				LinkedHashSet<String> prefixes = new LinkedHashSet<>();
				for(Entry<String, String> entry : UniversalNamespaceCache.this.prefix2Uri.entrySet()) {
					if(entry.getValue().equals(namespaceURI)) {
						prefixes.add(entry.getKey());
					}
				}
				this.wrapped = prefixes.iterator();
			}
			
			@Override
			public boolean hasNext() {
				return this.wrapped.hasNext();
			}
			
			@Override
			public String next() {
				return this.wrapped.next();
			}
			
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
}
