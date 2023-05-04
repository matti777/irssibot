/*
 * $Id: XMLUtil.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
 *
 * IrssiBot - An advanced IRC automation ("bot")
 * Copyright (C) 2000 Matti Dahlbom
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * mdahlbom@cc.hut.fi
 */
package irssibot.util;

import java.util.Vector;

import org.w3c.dom.*;
import org.apache.xerces.parsers.DOMParser;

/**
 * Collection of util functions for manipulating and parsing XML documents.<P>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $
 */
public class XMLUtil {
  /**
   * Used to parse out elements of a list such as this:<P>
   *
   * <pre>
   * <node>
   *    <element>value1</element>
   *    <element>value2</element>
   *    <element>value3</element>
   * </node>
   * </pre><P>
   *
   * For this example, called with Node pointing to <node> element and "element" 
   * in listElementName.<P>
   *
   * @param listNode pointer to list's "root" node
   * @param listElementName name of list element
   * @return Vector of list elements 
   */
  public static Vector getListElements(Node listNode,String listElementName){
    String tmp = "";
    Vector ret = null;
    
    NodeList children = listNode.getChildNodes();
    if ( children != null ) {
      ret = new Vector();
      
      for ( int i = 0; i < children.getLength(); i++ ) {
	Node child = children.item(i);
	
	if ( child.getNodeName().equalsIgnoreCase(listElementName) ) {
	  String value = child.getChildNodes().item(0).getNodeValue();
	  if ( (value != null) && !value.equals("") ) {
	    ret.add(value);
	  }
	}
      }
    } 
    return ret;
  }

  /**
   * Returns a text node contents below given node:<P>
   *
   * <pre>
   * <node>text to be returned</node>
   * </pre>
   * <p>
   *
   * @param node node whose text child to return
   * @return text node contents or null if no such child
   */
  public static String getTextChildValue(Node node) {
    Node child = node.getFirstChild();
    if ( child != null ) {
      if ( child.getNodeType() != Node.TEXT_NODE ) {
	throw new IllegalArgumentException("Child is not a text node!");
      }

      return child.getNodeValue().trim();
    }

    return null;
  }
  
  /**
   * Returns the value of a named node's attribute.<P>
   *
   * @param node node whose attribute to return
   * @param name name of attribute to return
   * @return attribute value or null if not found
   */
  public static String getNodeAttribute(Node node, String name) {
    NamedNodeMap map = node.getAttributes();
    if ( map != null ) {
      Node attr = map.getNamedItem(name);
      if ( attr != null ) {
	return attr.getNodeValue();
      }
    }

    return null;
  }
  
  /**
   * Returns the value of a named node's attribute as an integer.<P>
   *
   * @param node node whose attribute to return
   * @param name name of attribute to return
   * @return attribute value or null if not found
   * @exception MissingValueException if the value is missing
   */
  public static int getNodeIntAttribute(Node node, String name) {
    NamedNodeMap map = node.getAttributes();
    if ( map != null ) {
      Node attr = map.getNamedItem(name);
      if ( attr != null ) {
	try {
	  return Integer.parseInt(attr.getNodeValue());
	} catch ( NumberFormatException e ) {
	  throw new MissingValueException("Bad value for attribute " + name +
					  ": " + attr.getNodeValue());
	}
      } 
    }

    throw new MissingValueException("Missing value for attribute " + name);
  }
}








