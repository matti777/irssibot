/*
 * $Id: Core.java,v 1.1 2002/11/08 11:14:31 dreami Exp $
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
package irssibot.core;

import irssibot.config.*;
import irssibot.util.*;
import irssibot.util.log.*;
import irssibot.modules.AbstractModule;
import irssibot.user.*;
import irssibot.protocol.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * IrssiBot core.<P>
 *
 * @author Matti Dahlbom 
 * @version $Name:  $ $Revision: 1.1 $
 */
public class Core 
{
  public static final String botVersion = 
    "IrssiBot 1.0.7 ($Revision: 1.1 $) for Java Copyright " + 
    "(C) 2000-2007 Matti Dahlbom";
  /**
   * Module info.<P>
   */
  private static final String info = "Core";
  /**
   * Modules base dir.
   */
  private String moduleBaseDir = null;
  /**
   * General date format string.
   */
  private String dateFormatString = null;
  /**
   * Name of configuration file
   */
  private String configFileName = null;
  /**
   * Contains ServerConnection objects
   */
  private Vector serverInstances = null;
  /**
   * Database descriptors.
   */
  private ArrayList databases = null;
   /**
    * Interface IP to bind to 
    */
   private String bindIP = null;
  /**
   * Handles modules
   */
  private ModuleHandler moduleHandler = null;
  /**
   * Time (in milliseconds, see System.currentTimeMillis()) when the bot 
   * was started.
   */
  private long startedTime = 0;
  /**
   * Time (in milliseconds, see System.currentTimeMillis()) when the module states
   * were last saved.
   */
  private long moduleStateSaved = 0;

  /**
   * Constructs.<P>
   *
   * @param configFileName path to configuration file
   */
  public Core(String configFileName) {
    this.configFileName = configFileName;
    startedTime = System.currentTimeMillis();
    moduleStateSaved = startedTime;
    
    serverInstances = new Vector();
  }

  /**
   * Returns module info.<P>
   */
  public String toString() {
    return info;
  }

  /**
   * Returns server instances.<P>
   */
  public Vector getServerInstances() { 
    return serverInstances; 
  }

  /**
   * Returns the module handler.<P>
   */
  public ModuleHandler getModuleHandler() { 
    return moduleHandler; 
  }

   /**
    * Returns the interface bind IP.<p />
    */
   public final String getBindIP() {
      return bindIP;
   }
   
  /**
   * Returns the general date format string.<P>
   *
   * @return date format string
   * @see java.text.SimpleDateFormat
   */
  public String getDateFormatString() {
    return dateFormatString;
  }

  /**
   * Returns the module base dir.<P>
   *
   * The returned path does not include trailing path separator.<P>
   */
  public String getModuleBaseDir() {
    return moduleBaseDir;
  }

  /**
   * Broadcasts a message to all channels of all server instances.<P>
   *
   * @param message message to broadcast
   */
  public void globalChannelBroadcast(String message) {
    Enumeration connections = serverInstances.elements();
    while( connections.hasMoreElements() ) {
      ServerConnection connection = (ServerConnection)connections.nextElement();
      connection.channelBroadcast(message);
    }
  }
    
  /**
   * Tells module handler to save all modules' state.<P>
   *
   * @param force if true, disregard the 5min timelimit 
   * and save anyhow. If false, don't save unless 5 minutes 
   * has passed from last save.
   */
  public void saveModuleStates(boolean force) {
    long now = System.currentTimeMillis();
    
    // if last module state save time older than 5 minutes, save 
    if( ((now - moduleStateSaved) > 30000) || force ) {
      moduleStateSaved = now;
      AbstractModule modules[] = moduleHandler.getModuleTable();
      for( int i = 0; i < modules.length; i++ ) {
	moduleHandler.saveModuleState(modules[i]);
      }
    }
  }

  /**
   * Returns a database connection for requested database.<P>
   *
   * @param name database descriptor name
   * @return database connection, or null if could not estabilish one
   */
  public Connection getDatabaseConnection(String name) {
    Iterator iter = databases.iterator();
    while ( iter.hasNext() ) {
      DatabaseDescriptor desc = (DatabaseDescriptor)iter.next();
      if ( desc.getName().equals(name) ) {
	Driver d = desc.getJdbcDriver();
	try {
	   Log.debug(this, "getDatabaseConnection(): connecting with JDBC URL " + 
		     desc.getJdbcURL());
	   return d.connect(desc.getJdbcURL(), null);
	} catch ( SQLException e ) {
	  Log.log(this, e);
	  Log.error(this, "getDatabaseConnection(): error getting database connection. " + 
		    "driver: " + d.getClass().getName() + ", url: " + desc.getJdbcURL());
	  return null;
	}
      }
    }

    Log.error(this, "getDatabaseConnection(): database descriptor with name " + name +
	      " not found!");
    return null;
  }

  /**
   * Requests XML configuration from each server instance and
   * writes the config file.<P>
   *
   */
  private void saveConfigFile() {
    String xml = "";
    
    xml += "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n\n";
    xml += "<!-- main configuration file for IrssiBot / created " + 
      new Date().toString() + " -->\n";
    xml += "<irssibot-config>\n";

    // construct XML for general info 
    CommonLog logger = Log.getLogger();
    xml += "  <general>\n";
     xml += "    <interface ip=\"" + bindIP + "\" />\n";
    xml += "    <log class=\"" + logger.getClass().getName() + "\"\n" + 
      "         property-file=\"" + logger.getPropertyFilePath() + "\"\n" + 
      "         date-format=\"" + Log.getDateFormatString() + "\" />\n";
    xml += "  </general>\n\n";

    // construct XML for module list 
    AbstractModule modules[] = moduleHandler.getModuleTable();
    xml += "  <modules base-dir=\"" + moduleBaseDir + "\">\n";

    for ( int i = 0; i < modules.length; i++ ) {
      String name = modules[i].getClass().getName();
      xml += "    <module>" + name + "</module>\n";
    }

    xml += "  </modules>\n\n";

    // database descriptors
    Iterator iter = databases.iterator();
    while ( iter.hasNext() ) {
      DatabaseDescriptor desc = (DatabaseDescriptor)iter.next();
      String jdbcURL = desc.getJdbcURL();
      
      // replace all '&'s by '&#38;'
      jdbcURL = StringUtil.replace(jdbcURL, "&", "&#38;");

      xml += "  <database name=\"" + desc.getName() + "\">\n";
      xml += "    <jdbc driver-class=\"" + desc.getJdbcDriver().getClass().getName() + "\"\n";
      xml += "          url=\"" + jdbcURL + "\" />\n";
      xml += "  </database>\n\n";
    }

    // construct XML for server instance list 
    for ( int i = 0; i < serverInstances.size(); i++ ) {
      ServerConnection connection = (ServerConnection)serverInstances.elementAt(i);
      xml += connection.getXML() + "\n";
    }
    xml += "</irssibot-config>\n";

    try {
      FileOutputStream out = new FileOutputStream(configFileName);
      out.write(xml.getBytes());
      out.flush();
      out.close();
    } catch( IOException e ) { 
      Log.log(this, e);
    }
  }

  /**
   * Loads a module.<P>
   *
   * @param params array of String parameters
   * @param caller calling server instance
   * @param source nick or channel where call originated from
   */
  private String loadModule(String params[], ServerConnection caller, String source) {
    AbstractModule loadedModule = null;
    Class loadedClass = null;
    
    if ( (params == null) || (params.length != 1) ) {
      return new String("loadModule(): incorrect number of arguments");
    }

    String className = params[0];

    try {
      ModuleLoader loader = new ModuleLoader(moduleBaseDir);
      loadedClass = loader.loadClass(className);
    } catch ( ClassNotFoundException e ) {
      return new String("loadModule(): could not find module " + className);
    }
    
    if ( loadedClass == null ) {
      return null;
    }
    
    try {
      loadedModule = (AbstractModule)loadedClass.newInstance();
    } catch( Exception e ) {
      Log.error(this, "loadModule() " + className + ": caught " + 
		e.getClass().getName() + ": " + e.getMessage());
      return new String("loadModule(): " + className + ": caught " + 
			e.getClass().getName() + ": " + e.getMessage());
    } 

    // add to module handling 
    try {
      if ( moduleHandler.addModule(className,loadedModule) )
	return new String("loadModule(): module " + className + " loaded.");
      else 
	return new String("loadModule(): error loading module " + className);
    } catch ( IllegalStateException e ) {
      return new String("loadModule(): module already loaded");
    }
  }

  /**
   * Lists info about loaded modules
   *
   * @param params array of String parameters
   * @param caller calling server instance
   * @param source nick or channel where call originated from
   */
  private String listLoadedModules(String params[],ServerConnection caller,String source) {
    if ( params != null )
      return new String("listLoadedModules(): incorrect number of arguments");
    
    AbstractModule modules[] = moduleHandler.getModuleTable();
    if( (modules != null) && modules.length > 0 ) {
      caller.write("PRIVMSG "+source+" :Modules loaded:\n");
      for( int i = 0; i < modules.length; i++ ) {
	String msg = modules[i].getModuleInfo()+" ("+modules[i].getClass().getName()+")";
	caller.write("PRIVMSG "+source+" :  "+msg+"\n");
      }
    } else {
      caller.write("PRIVMSG "+source+" :No modules loaded.\n");
    }
    
    return null;
  }

  /**
   * Unload a module.<P>
   *
   * @param params array of String parameters
   * @param caller calling server instance
   * @param source nick or channel where call originated from
   */
  private String unloadModule(String params[],ServerConnection caller,String source) {
    if( (params == null) || (params.length != 1) ) 
      return new String("unloadModule(): incorrect number of arguments");
    
    String className = params[0];
    String ret = null;
    
    if ( moduleHandler.removeModule(className) ) {
      ret = new String("unloadModule(): module " + params[0] + " unloaded.");
    } else {
      ret = new String("unloadModule(): module " + params[0] + " not found.");
    }
    
    return ret;
  }
  
    /**
     * Lists all callers channel connects
     *
     * @param params array of String parameters
     * @param caller calling server instance
     * @param source nick or channel where call originated from
     */
    private String listChannelConnects(String params[],ServerConnection caller,String source)
    {
	String connects = "";
	int numConnects = 0;

	if( params != null )
	    return new String("listChannelConnects(): incorrect number of arguments");

	/* list channel connects for all server instances */
	for( int i = 0; i < serverInstances.size(); i++ ) {
	    ServerConnection connection = (ServerConnection)serverInstances.elementAt(i);
	    Vector v = connection.getChannelConnects();

	    if( v != null ) {
		for( int j = 0; j < v.size(); j++ ) {
		    ChannelConnect connect = (ChannelConnect)v.elementAt(j);
		    String msg = 
			connection.getInstanceData().getNetwork() +
			connect.getSourceChannel();
		    ServerInstanceData data = connect.getDestinationNetwork().getInstanceData();
		    msg += " -> " + data.getNetwork() + connect.getDestinationChannel();
		    connects += msg + "|";
		    numConnects++;
		}
	    }
	}

	if( numConnects == 0 ) {
	    caller.write("PRIVMSG " + source + " :no channel connects.\n");
	} else {
	    caller.write("PRIVMSG " + source + " :registered channel connects:\n");

	    String list[] = StringUtil.separate(connects,'|');
	    for( int i = 0; i < list.length; i++ ) {
		caller.write("PRIVMSG "+source+" :  "+list[i]+"\n");
	    }
	}

	return null;
    }

    /**
     * Removes all callers channel connects.<P>
     *
     * @param params array of String parameters
     * @param caller calling server instance
     * @param source nick or channel where call originated from
     */
    private String removeChannelConnects(String params[],ServerConnection caller,String source)
    {
	if( (params == null) || (params.length != 1) ) 
	    return new String("removeChannelConnect(): incorrect number of arguments");

	for( int i = 0; i < serverInstances.size(); i++ ) {
	    ServerConnection connection = (ServerConnection)serverInstances.elementAt(i);
	    /* look for fromNetwork */
	    if( connection.getInstanceData().getNetwork().equalsIgnoreCase(params[0]) ) {
		Vector v = connection.getChannelConnects();
		if( v != null ) {
		    if( connection.getChannelConnects().size() > 0 ) {
			connection.removeChannelConnects();
			return new String(connection.getInstanceData().getNetwork() +
					  ": channel connects removed.\n");
		    } else {
			return new String(connection.getInstanceData().getNetwork() +
					  ": there are no channel connects\n");
		    }
		} else {
		    return new String(connection.getInstanceData().getNetwork() +
				      ": bad channel connect vector\n");
		}
	    }
	}
	return new String("no such server instance");
    }

    /**
     * Adds a channel connect.<P>
     *
     * @param params array of String parameters
     * @param caller calling server instance
     * @param source nick or channel where call originated from
     */
    private String addChannelConnect(String params[],ServerConnection caller,String source)
    {
	String ret = null;
	
	int index1 = 0;
	int index2 = 0;

	if( (params == null) || (params.length != 2) ) 
	    return new String("addChannelConnect(): incorrect number of arguments");

	index1 = params[0].indexOf('#');
	index2 = params[1].indexOf('#');

	if( (index1 == -1) || (index2 == -1 ) )
	    return new String("addChannelConnect(): invalid arguments");

	String fromNetwork = params[0].substring(0,index1);
	String fromChannel = params[0].substring(index1);
	String toNetwork = params[1].substring(0,index2);
	String toChannel = params[1].substring(index2);

	ServerConnection fromConnection = null;
	ServerConnection toConnection = null;

	for( int i = 0; i < serverInstances.size(); i++ ) {
	    ServerConnection connection = (ServerConnection)serverInstances.elementAt(i);
	    /* look for fromNetwork */
	    if( connection.getInstanceData().getNetwork().equalsIgnoreCase(fromNetwork) ) 
		fromConnection = connection;
	    /* look for toNetwork */
	    if( connection.getInstanceData().getNetwork().equalsIgnoreCase(toNetwork) ) 
		toConnection = connection;
	}

	if( (fromConnection == null) || (toConnection == null) )
	    return new String("addChannelConnect(): invalid network");

	ret = fromConnection.addChannelConnect(fromChannel,toConnection,toChannel);

	if( ret == null )
	    return new String("addChannelConnect(): added new channel connect "+params[0]+" -> "+params[1]);
	else
	    return ret;
    }

    /**
     * Forces the bot to write out its userfile and the state of all loaded modules.
     *
     * @param params array of String parameters
     * @param caller calling server instance
     * @param source nick or channel where call originated from
     */
    private String save(String params[],ServerConnection caller,String source)
    {
	if( params != null )
	    return new String("save(): incorrect number of arguments");

	for( int i = 0; i < serverInstances.size(); i++ ) {
	    ServerConnection connection = (ServerConnection)serverInstances.elementAt(i);
	    connection.writeUserFile();
	}
	
	/* save module states */
	saveModuleStates(true);

	/* save config file */
	saveConfigFile();

	return new String("save(): saved.");
    }

  /**
   * Sends NOTICE to all users on bot's channels.<P>
   *
   * @param params array of String parameters
   * @param caller calling server instance
   * @param source nick or channel where call originated from
   */
  private String notice(String params[], ServerConnection caller, String source)
  {
    int totalNicks = 0;

    if( (params == null) || (params.length < 1) ) {
      return new String("notice(): incorrect number of arguments");
    }

    String msg = StringUtil.join(params);

    // go through all server connections 
    Enumeration en = serverInstances.elements();
    while( en.hasMoreElements() ) {
      ServerConnection connection = (ServerConnection)en.nextElement();

      Vector v = new Vector();

      /* go through all channels */
      Enumeration channels = connection.getChannels().elements();
      while( channels.hasMoreElements() ) {
	  Channel channel = (Channel)channels.nextElement();
	  Vector nickList = channel.getNickList();
	  if( nickList != null ) {
	      for( int i = 0; i < nickList.size(); i++ ) {
		  Nick nick = (Nick)nickList.elementAt(i);
		  Host host = nick.getHost();
		  
		  if( !v.contains(host.getNick()) ) {
		      v.add(host.getNick());
		  }
	      }
	  }
      }
      
      // sent notice to all (unique) nicks 
      for ( int i = 0; i < v.size(); i++ ) {
	  String nick = (String)v.elementAt(i);
	  totalNicks++;
	 
	  connection.write("NOTICE " + nick + " :" + msg + "\n");
      }
    }

    return new String("notice(): sent notice to " + totalNicks + " nicks.");
  }

    /**
     * Quits the bot.<P>
     *
     * @param params array of String parameters
     * @param caller calling server instance
     * @param source nick or channel where call originated from
     */
    private String quit(String params[],ServerConnection caller,String source)
    {
	String msg = null;

	if( params == null )
	    msg = "Leaving";
	else {
	    if( params.length > 1 ) {
		return new String("quit(): incorrect number of arguments");
	    } else {
		msg = params[0]; 
	    }
	}
	
	// kill all server instances
	for( int i = 0; i < serverInstances.size(); i++ ) {
	    ServerConnection connection = (ServerConnection)serverInstances.elementAt(i);
	    connection.quit(msg);
	}

	// kill all modules
	AbstractModule table[] = moduleHandler.getModuleTable();
	if ( table != null ) {
	  for ( int i = 0; i < table.length; i++ ) {
	    table[i].killModule();
	  }
	}

	return new String("quit(): all instances Shows..");
    }

    /**
     * quitted info about bot.
     *
     * @param params array of String parameters
     * @param caller calling server instance
     * @param source nick or channel where call originated from
     */
    private String info(String params[], ServerConnection caller, String source)
    {
	caller.write("PRIVMSG " + source + " :I am "+botVersion + ".\n");

	/* calculate uptime */
	long secs = (System.currentTimeMillis() - startedTime) / 1000;
	int days = (int)(secs / 86400);
	secs = secs % 86400;
	int hrs = (int)(secs / 3600);
	secs = secs % 3600;
	int mins = (int)(secs / 60);
	secs = secs % 60;
	String uptime = days + " days, " + hrs + " hours, " + mins + 
	    " minutes and " + secs + " seconds. ";

	caller.write("PRIVMSG " + source + " :I have been running " + uptime + 
		     caller.getTrafficInfo() + "\n");
	caller.write("PRIVMSG " + source + " :Java VM version: " + 
		     System.getProperty("java.version") + ", "+
		     "Operating System: " + System.getProperty("os.name") + 
		     " (version " + 
		     System.getProperty("os.version") + ")\n");
		     
	return null;
    }

    /**
     * Parse and execute a core call.<P>
     *
     * @param message message defining the call
     * @return null on success; error description otherwise
     */
    public String processCoreCall(IrcMessage message, String trailing, 
				  ServerConnection caller, String source)
    {
	String call = null;
	int index1;
	int index2;
	String params[] = null;
	String method = null;

	call = trailing.substring(new String("core->").length());
	
	// isolate method name and parameters 
	index1 = call.indexOf('(');
	index2 = call.indexOf(')');

	if( (index1 == -1) || (index2 == -1 ) || (index2 <= index1) ) {
	    return new String("bad call");
	}

	method = call.substring(0,index1);
	params = StringUtil.separate(call.substring(index1+1,index2),',');

	// handle call 
	if( method.equals("addChannelConnect") ) {
	    return addChannelConnect(params,caller,source);
	} else if( method.equals("listChannelConnects") ) {
	    return listChannelConnects(params,caller,source);
	} else if( method.equals("removeChannelConnects") ) {
	    return removeChannelConnects(params,caller,source);
	} else if( method.equals("loadModule") ) {
	    return loadModule(params,caller,source);
	} else if( method.equals("unloadModule") ) {
	    return unloadModule(params,caller,source);
	} else if( method.equals("listLoadedModules") ) {
	    return listLoadedModules(params,caller,source);
	} else if( method.equals("quit") ) {
	    return quit(params,caller,source);
	} else if( method.equals("save") ) { 
 	    return save(params,caller,source); 
	} else if( method.equals("info") ) { 
	    return info(params,caller,source);  
	} else if( method.equals("notice") ) { 
	    return notice(params, caller, source);
	} else { 
	    // default 
	    return new String(method + ": no such method");
	}
    }

  /**
   * Starts the bot.<P>
   *
   */
  private void launch() {
    ConfigParser parser = null;
    String arg[] = new String[1];

    // read configuration 
    try {
      parser = new ConfigParser(configFileName);
    } catch ( Throwable t ) {
      t.printStackTrace();
      System.exit(1);
    }

    dateFormatString = parser.getDateFormatString();
    databases = parser.getDatabases();
     bindIP = parser.getBindIP();

    // init logging system with given CommonLog logger
    CommonLog log = parser.getLog();
    try {
      Log.init(log, dateFormatString);
    } catch ( Exception e ) {
      Log.init(new StdoutLog(), dateFormatString);

      Log.error(this, "launch(): error initializing log " + log.getClass().getName() +
		", using StdougLog");
    }

    // get module base dir make sure it is not in classpath
    moduleBaseDir = parser.getModuleBaseDir();
    if ( moduleBaseDir.endsWith(File.separator) ) {
      moduleBaseDir = moduleBaseDir.substring(0, moduleBaseDir.length() - 1);
    }

    String cp = System.getProperty("java.class.path");
    StringTokenizer st = new StringTokenizer(cp, File.pathSeparator);

    while ( st.hasMoreTokens() ) {
      String token = st.nextToken();
      
      if ( token.endsWith(File.separator) ) {
	token = token.substring(0, token.length() - 1);
      }

      if ( token.equals(moduleBaseDir) ) {
	Log.error(this, "launch(): module base directory MUST NOT be included in classpath!");
	Log.stop();
	System.exit(1);
      }
    }

    // launch server instances 
    Vector instanceData = parser.getInstanceData();
    for ( int i = 0; i < instanceData.size(); i++ ) {
      Log.debug(this, "launch(): launching server instance #" + i);
      ServerInstanceData instance = 
	(ServerInstanceData)instanceData.elementAt(i);
      ServerConnection connection = 
	new ServerConnection(instance, this);
      connection.start();
      serverInstances.add(connection);
    }

    // load initial modules from config file. launch module handler 
    moduleHandler = new ModuleHandler(this);
    Vector initialModules = parser.getInitialModules();
    if( initialModules != null ) {
      for( int i = 0; i < initialModules.size(); i++ ) {
	arg[0] = (String)initialModules.elementAt(i);
	Log.debug(this, "launch(): " + loadModule(arg,null,null));
      }
    }
    
    Log.debug(this, "launch(): initialization done. Thread " + 
	      Thread.currentThread().getName() + " exiting..");
  }

  /**
   * Runs the bot.<P>
   *
   * @param args the command line arguments
   */
  public static void main(String args[]) {
    // look for config file
    if ( args.length != 1 ) {
      System.err.println("Usage: java irssibot.core.Core config-file.xml");
      System.exit(1);
    }

    System.out.println("\n" + botVersion + "\n" + 
		       "IrssiBot comes with ABSOLUTELY NO WARRANTY.\n" + 
		       "This is free software, and you are welcome to redistribute it\n" +
		       "under certain conditions; see README.GPL distributed with\n" + 
		       "this software for details.\n\n");

    File file = new File(args[0]);
    if ( !file.exists() || !file.isFile() ) {
      System.err.println("Config file " + args[0] + " not found!");
      System.exit(1);
    }

    // init logging system with null logging
    Log.init(new NullLog(), null);
    
    Core core = new Core(args[0]);
    core.launch();
  }
}




