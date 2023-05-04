/*
 * $Id: ConfigParser.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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
package irssibot.config;

import irssibot.core.*;
import irssibot.util.*;
import irssibot.util.log.*;

import java.io.*;
import java.util.*;

import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.parsers.*;

/**
 * Confiduration file parser.<P>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $
 */
public class ConfigParser {
  /**
   * Logger
   */
  private CommonLog log = null;
  /**
   * Logging date format string
   */
  private String dateFormatString = null;
   /**
    * Interface IP to bind to 
    */
   private String bindIP = null;
  /**
   * List of modules to load at bot startup
   */
  private Vector initialModules = null;
  /**
   * List of server instances
   */
  private Vector serverInstances = null;
  /**
   * List of database descriptors
   */
  private ArrayList databases = null;
  /**
   * Module base dir
   */
  private String moduleBaseDir = null;
  
  /**
   * Constructs.<P>
   *
   * @param configFileName config file name
   * @exception IOException if reading the config file fails
   */
  public ConfigParser(String configFileName) throws IOException {
    serverInstances = new Vector();
    initialModules = new Vector();
    databases = new ArrayList();

    Node node = readConfigFile(configFileName);
    parse(node);
  }

  /**
   * Reads config file and generates a DOM document out of it.<P>
   *
   * @param configFileName config file name
   * @return contents of the config file
   * @exception IOException if reading the config file fails
   */
  private Node readConfigFile(String configFileName) throws IOException {
    File file = new File(configFileName);
    FileInputStream fileIn = new FileInputStream(file);
    
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setValidating(false);
    DocumentBuilder builder = null;
    
    try {
      builder = factory.newDocumentBuilder();
      InputSource source = new InputSource(fileIn);
      Document inputDocument = builder.parse(source);
      return inputDocument.getDocumentElement();
    } catch ( Exception e ) {
      throw new IOException("Unable to parse config file: caught " + e.getClass().getName() + 
			    ", message: " + e.getMessage());
    } 
  }

  /**
   * Reads database information.<P>
   * 
   * @param node database node
   */
  private void handleDatabaseNode(Node node)
  {
    String name = XMLUtil.getNodeAttribute(node, "name");
    Node child = node.getFirstChild();
    String jdbcDriver = null;
    String jdbcUrl = null;

    if ( name == null ) {
      throw new MissingValueException("Attribute name mandatory for node database");
    }

    while ( child != null ) {
      if( child.getNodeName().equals("jdbc") ) {
	jdbcDriver = XMLUtil.getNodeAttribute(child, "driver-class");
	jdbcUrl = XMLUtil.getNodeAttribute(child, "url");
      }

      child = child.getNextSibling();
    }

    if ( (jdbcDriver == null) || (jdbcUrl == null) ) {
      throw new MissingValueException("Attributes driver-class and url " +
				      "mandatory for node jdbc");
    }

    databases.add(new DatabaseDescriptor(name, jdbcDriver, jdbcUrl));
  }
  
  /**
   * Parse general information.<P>
   * 
   * @param node server-instance node
   */
  private void handleGeneralNode(Node node) {
    Node child = node.getFirstChild();
    while ( child != null ) {
       if ( child.getNodeName().equals("interface") ) {
	  bindIP = XMLUtil.getNodeAttribute(child, "ip");
       } else if ( child.getNodeName().equals("log") ) {
	String logClassName = XMLUtil.getNodeAttribute(child, "class");
	String propertyFile = XMLUtil.getNodeAttribute(child, "property-file");
	dateFormatString = XMLUtil.getNodeAttribute(child, "date-format");

	if ( (logClassName == null) || (propertyFile == null) ||
	     (dateFormatString == null) ) {
	  throw new MissingValueException("Attributes class, property-file and date-format mandatory " + 
					  "for node log");
	}

	// load log class
	try {
	  Class c = Class.forName(logClassName);
	  log = (CommonLog)c.newInstance();
	} catch ( Exception e ) {
	  throw new IllegalArgumentException("Class " + logClassName + " is not a CommonLog object");
	}

	// load the properties and init the log
	Properties p = new Properties();
	try {
	  FileInputStream fileIn = new FileInputStream(propertyFile);
	  p.load(fileIn);
	} catch ( IOException e ) {
	  throw new IllegalArgumentException("Could not read log property-file " + propertyFile +
					     ": " + e.getMessage());
	}

	// add property file path itself to the Properties
	p.setProperty(CommonLog.PROPERTY_FILE_PATH_PROPERTY_NAME, propertyFile);

	log.init(p);
      }

      child = child.getNextSibling();
    }
  }

  /**
   * Create and init a new server instance.<P>
   * 
   * @param node server-instance node
   */
  private void handleServerInstanceNode(Node node) {
    String network = null;
    String userFilePath = null;
    String nick = null;
    String altNick = null;
    String realName = null;
    String ident = null;
    Vector serverList = null;
    Hashtable channels = null;
    long interval = 0;
    int maxBytes = 0;

    network = XMLUtil.getNodeAttribute(node, "network");

    if ( network == null ) {
      throw new MissingValueException("Attribute network mandatory for " + 
				      "server-instance node");
    }

    Node child = node.getFirstChild();
    while ( child != null ) {
      String nodeName = child.getNodeName();
      if ( nodeName.equals("bot-info") ) {
	nick = XMLUtil.getNodeAttribute(child, "nick");
	altNick = XMLUtil.getNodeAttribute(child, "altnick");
	ident = XMLUtil.getNodeAttribute(child, "ident");
	realName = XMLUtil.getNodeAttribute(child, "realname");

	if ( (nick == null) || (altNick == null) ||
	     (ident == null) || (realName == null) ) {
	  throw new MissingValueException("Attributes nick, altNick, ident and " +
					  "realname mandatory for " + 
					  "bot-info node");
	}
      } else if ( nodeName.equals("user-file") ) {
	userFilePath = XMLUtil.getNodeAttribute(child, "path");
      } else if ( nodeName.equals("output") ) {
	interval = XMLUtil.getNodeIntAttribute(child, "flush-time-ms");
	maxBytes = XMLUtil.getNodeIntAttribute(child, "max-output-bytes");
      } else if ( nodeName.equals("channel-list") ) {
	channels = new Hashtable();
	Node channelNode = child.getFirstChild();

	while ( channelNode != null ) {
	  if ( channelNode.getNodeName().equals("channel") ) {
	    String channelName = XMLUtil.getNodeAttribute(channelNode, "name");
	    String channelKey = XMLUtil.getNodeAttribute(channelNode, "key");
	    String channelForcedModes = XMLUtil.getNodeAttribute(channelNode, 
								 "forced-modes");

	    if ( (channelName == null) || channelName.equals("") ) {
	      throw new MissingValueException("name attribute is mandatory for " +
					      "channel node");
	    }

	    channels.put(channelName, new Channel(channelName, channelKey,
						  channelForcedModes, null));
	  }
	  
	  channelNode = channelNode.getNextSibling();
	}
      } else if ( nodeName.equals("server-list") ) {
	serverList = XMLUtil.getListElements(child, "address");
      }

      child = child.getNextSibling();
    }

    serverInstances.add(new ServerInstanceData(network, userFilePath, nick, 
					       altNick, realName, ident, 
					       serverList, channels, 
					       interval, maxBytes));
  }
 
  /**
   * Parses the DOM document representing the config file.<P>
   *
   * @param node document root node
   */
  private void parse(Node node) {
    node = node.getFirstChild();

    while ( node != null ) {
      String nodeName = node.getNodeName();

      if ( nodeName.equals("general") ) {
	handleGeneralNode(node);
      } else if ( nodeName.equals("database") ) {
	handleDatabaseNode(node);
      } else if ( nodeName.equals("server-instance") ) {
	handleServerInstanceNode(node);
      } else if ( nodeName.equals("modules") ) {
	moduleBaseDir = XMLUtil.getNodeAttribute(node, "base-dir");
	if ( moduleBaseDir == null ) {
	  throw new MissingValueException("Attribute base-dir mandatory for node modules");
	}
	initialModules = XMLUtil.getListElements(node, "module");
      }

      node = node.getNextSibling();
    }
  }

   /**
    * Returns the interface bind IP.<p />
    */
   public final String getBindIP() {
      return bindIP;
   }
   
  /**
   * Returns database descriptors.<P>
   */
  public ArrayList getDatabases() {
    return databases;
  }

  /**
   * Return initial modules list.<P>
   */
  public Vector getInitialModules() {
    return initialModules;
  }

  /**
   * Returns server instance descriptors.<P>
   */
  public Vector getInstanceData() {
    return serverInstances;
  }
  
  /**
   * Returns logger.<P>
   */
  public CommonLog getLog() {
    return log;
  }

  /**
   * Returns date format string.<P>
   */
  public String getDateFormatString() {
    return dateFormatString;
  }
  
  /**
   * Returns the module base dir.<P>
   */
  public String getModuleBaseDir() {
    return moduleBaseDir;
  }
}














