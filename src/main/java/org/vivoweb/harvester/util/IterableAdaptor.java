/*******************************************************************************
 * Copyright (c) 2010-2011 VIVO Harvester Team. For full list of contributors, please see the AUTHORS file provided.
 * All rights reserved.
 * This program and the accompanying materials are made available under the terms of the new BSD license which accompanies this distribution, and is available at http://www.opensource.org/licenses/bsd-license.html
 ******************************************************************************/
package org.vivoweb.harvester.util;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Adapts Enumerations and Iterators to be Iterable
 * @param <T> type
 */
public class IterableAdaptor<T> {
	/**
	 * sometimes you have an Enumeration and you want an Iterable but want to cast the elements to a subtype
	 * @param <T> return subtype
	 * @param <E> original type
	 * @param enin enumeration to adapt
	 * @param returnClass the subtype to cast each element
	 * @return an iterable adapter for the enumeration with each element casted
	 */
	public static <E, T extends E> Iterable<T> adapt(final Enumeration<E> enin, @SuppressWarnings("unused") Class<T> returnClass) {
		return enin==null?Collections.<T>emptyList():new Iterable<T>() {
			@Override
		    public Iterator<T> iterator() {
				return new Iterator<T>() {
					@Override
					public boolean hasNext() {
						return enin.hasMoreElements();
					}
					
					@SuppressWarnings("unchecked")
					@Override
					public T next() {
						return (T)enin.nextElement();
					}
					
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	/**
	 * sometimes you have an Enumeration and you want an Iterable
	 * @param <T> type
	 * @param enin enumeration to adapt
	 * @return an iterable adapter for the enumeration
	 */
	public static <T> Iterable<T> adapt(final Enumeration<T> enin) {
		return enin==null?Collections.<T>emptyList():new Iterable<T>() {
			@Override
		    public Iterator<T> iterator() {
				return new Iterator<T>() {
					@Override
					public boolean hasNext() {
						return enin.hasMoreElements();
					}
					
					@Override
					public T next() {
						return enin.nextElement();
					}
					
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	/**
	 * sometimes you have an Iterator but you want to use a for
	 * @param <T> type
	 * @param itin iterator to adapt
	 * @return an iterable adapter for the iterator
	 */
	public static <T> Iterable<T> adapt(final Iterator<T> itin) {
		return itin==null?Collections.<T>emptyList():new Iterable<T>() {
			@Override
		    public Iterator<T> iterator() {
				return itin;
			}
		};
	}
	
	/**
	 * sometimes you have an Iterator and you want an Iterable but want to cast the elements to a subtype
	 * @param <T> return subtype
	 * @param <E> original type
	 * @param itin iterator to adapt
	 * @param returnClass the subtype to cast each element
	 * @return an iterable adapter for the iterator with each element casted
	 */
	public static <E, T extends E> Iterable<T> adapt(final Iterator<E> itin, @SuppressWarnings("unused") Class<T> returnClass) {
		return itin==null?Collections.<T>emptyList():new Iterable<T>() {
			@Override
		    public Iterator<T> iterator() {
				return new Iterator<T>() {
					@Override
					public boolean hasNext() {
						return itin.hasNext();
					}
					
					@SuppressWarnings("unchecked")
					@Override
					public T next() {
						return (T)itin.next();
					}
					
					@Override
					public void remove() {
						itin.remove();
					}
				};
			}
		};
	}
	
	/**
	 * sometimes you have a NodeList but you want to use a for
	 * @param nlin NodeList to adapt
	 * @param returnClass cast each Node to this class
	 * @return an iterable adapter for the NodeList
	 */
	public static <T extends Node> Iterable<T> adapt(final NodeList nlin, @SuppressWarnings("unused") Class<T> returnClass) {
		return nlin==null?Collections.<T>emptyList():new Iterable<T>() {
			@Override
		    public Iterator<T> iterator() {
				return new Iterator<T>() {
					int index = 0;
					
					@Override
					public boolean hasNext() {
						return this.index < nlin.getLength();
					}
					
					@SuppressWarnings("unchecked")
					@Override
					public T next() {
						if(hasNext()) {
							return (T)nlin.item(this.index++);
						}
						throw new NoSuchElementException();
					}
					
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	/**
	 * sometimes you have a NodeList but you want to use a for
	 * @param nlin NodeList to adapt
	 * @return an iterable adapter for the NodeList
	 */
	public static Iterable<Node> adapt(final NodeList nlin) {
		return adapt(nlin, Node.class);
	}
	
	/**
	 * sometimes you have a NamedNodeMap but you want to use a for
	 * @param nnmin NamedNodeMap to adapt
	 * @param returnClass cast each Node to this class
	 * @return an iterable adapter for the NodeList
	 */
	public static <T extends Node> Iterable<T> adapt(final NamedNodeMap nnmin, @SuppressWarnings("unused") Class<T> returnClass) {
		return nnmin==null?Collections.<T>emptyList():new Iterable<T>() {
			@Override
		    public Iterator<T> iterator() {
				return new Iterator<T>() {
					int index = 0;
					
					@Override
					public boolean hasNext() {
						return this.index < nnmin.getLength();
					}
					
					@SuppressWarnings("unchecked")
					@Override
					public T next() {
						if(hasNext()) {
							return (T)nnmin.item(this.index++);
						}
						throw new NoSuchElementException();
					}
					
					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	/**
	 * sometimes you have a NamedNodeMap but you want to use a for
	 * @param nnmin NamedNodeMap to adapt
	 * @return an iterable adapter for the NodeList
	 */
	public static Iterable<Node> adapt(final NamedNodeMap nnmin) {
		return adapt(nnmin, Node.class);
	}
}
