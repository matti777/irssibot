/*
 * $Id: Logger.java,v 1.1.1.1 2002/11/08 10:51:59 dreami Exp $
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
package irssibot.modules;

import irssibot.protocol.*;
import irssibot.core.*;
import irssibot.user.*;
import irssibot.util.*;
import irssibot.util.log.Log;

import java.util.*;
import java.text.SimpleDateFormat;
import java.sql.*;

/**
 * MySQL logging module.<P>
 *
 * MySQL clauses to initialize the database is as follows. The tables need to be named
 * like this:<P>
 *
 * quote table: networkname_channelname_quote<br>
 * nick table: networkname_nick<br>
 * alias table: networkname_alias<br><br>
 *
 * Networkname is the symbolic name given to a network in the bot's configuration
 * file. It is the name returned by ServerConnection.getInstanceData().getNetwork()<p>
 *
 * Therefore you need one quote table per channel, and one nick table and alias table
 * per network.<p>
 * 
 * <b>NOTE:</b> you do not need to create any of the tables, this module will
 * automatically create them for you. The SQL code is just supplied here to show
 * the structure of the tables to help the development of 3rd party applications
 * on top of the logging system.<p>
 *
 * <pre>
 *
 * DROP DATABASE IF EXISTS ircstats;
 * 
 * CREATE DATABASE ircstats;
 * 
 * CONNECT ircstats;
 * 
 * DROP TABLE IF EXISTS networkname_alias;
 * 
 * CREATE TABLE networkname_alias
 * (
 *     alias_id INT( 11 )     NOT NULL AUTO_INCREMENT,
 *     name     VARCHAR( 32 ) NOT NULL,
 *     PRIMARY KEY ( alias_id ),
 *     UNIQUE      ( name ),
 *     INDEX       ( name )
 * );
 * 
 * DROP TABLE IF EXISTS networkname_nick;
 * 
 * CREATE TABLE networkname_nick
 * (
 *     nick_id  INT( 11 ) NOT NULL AUTO_INCREMENT,
 *     name     VARCHAR( 32 ) NOT NULL,
 *     alias_id INT( 11 ) NOT NULL,
 *     status   enum(' ','D','U'),
 *     PRIMARY KEY ( nick_id ),
 *     UNIQUE      ( name ),
 *     INDEX       ( name ),
 *     INDEX ( alias_id )
 * );
 * 
 * DROP TABLE IF EXISTS networkname_channelname_quote;
 * 
 * CREATE TABLE networkname_channelname_quote
 * (
 *     nick_id   INT( 11 )  NOT NULL,
 *     quoteline MEDIUMTEXT NOT NULL,
 *     created   DATETIME   NOT NULL,
 *     INDEX ( nick_id )
 * );
 *
 * </pre>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.1.1.1 $
 */
public class Logger extends AbstractModule
{
  /**
   * Module info
   */
  private static final String moduleInfo = "MySQL Logger $Revision: 1.1.1.1 $ for IrssiBot";
  /**
   * Default dateformat
   */
  private static final SimpleDateFormat defaultDateFormat = 
    new SimpleDateFormat("dd-MM-yyyy HH:mm");
  /**
   * Dateformat to use for displaying quotes
   */
  private SimpleDateFormat sdf = defaultDateFormat;
  /**
   * Name of database descriptor to use.<P>
   *
   * @see irssibot.core.Core#getDatabaseConnection(String)
   */
  private String dbName = null;
  /**
   * Indicates whether to automatically quote people on
   * on channel JOINs
   */
  private boolean autoQuote = true;
  /**
   * Indicates whether to automatically connect nicks on
   * NICK change.
   */
  private boolean autoNickJoin = false;
  /**
   * Whether the state of the module has changed and needs saving.
   */
  private boolean changed = false;

  // per-request temp data 
  private Host host = null;
  private String source = null;
  private ServerConnection caller = null;

  /**
   * Database connection
   */
  private Connection connection = null;
  /**
   * Reference to bot core
   */
  private Core core = null;

  /**
   * Default constructor
   *
   */
  public Logger() {
    super(Logger.class.getName());
  }

  /**
   * Returns module state.<P>
   *
   * @return module state or null if state has not changed.
   */
  public Properties getModuleState() {
    Properties props = null;
    
    if ( changed ) {
      props = new Properties();
      props.setProperty("dbname", dbName);
      if( autoQuote ) {
	props.setProperty("autoquote", "true");
      } else {
	props.setProperty("autoquote", "false");
      }

      if ( autoNickJoin ) {
	props.setProperty("autonickjoin", "true");
      } else {
	props.setProperty("autonickjoin", "false");
      }

      changed = false;
    }
    return props;
  }

  /**
   * Returns a module info string.<P>
   */
  public String getModuleInfo() {
    return moduleInfo;
  }

  /**
   * Disconnects from the database.<P>
   *
   */
  private void closeConnection() {
    try {
      if ( (connection != null) && !connection.isClosed() ) { 
	connection.close(); 
      }
    } catch ( SQLException e ) {
      Log.error(this, "closeConnection(): error closing database connection: " + e.getMessage());
    }
    connection = null;
  }

  /**
   * Called upon loading the module. Inits the module and connects to the configured database.
   *
   * @param state initial state for the module
   * @param core refenrece to bot core
   */
  public boolean onLoad(Properties state, Core core) { 
    this.core = core;

    // read initial state
    if( state != null ) { 
      dbName = state.getProperty("dbname");
      String tmp = state.getProperty("autoquote");
      if( tmp != null) {
	autoQuote = tmp.equalsIgnoreCase("true") ? true : false;
      }
      tmp = state.getProperty("autonickjoin");
      if( tmp != null ) {
	autoNickJoin = tmp.equalsIgnoreCase("true") ? true : false;
      }
    } 

    //##TODO## fetch dateformat from core

    return true;
  }

  /**
   * Called upon unloading the module.<P>
   *
   */
  public void onUnload() {
    Log.debug(this, "unUnload()");

    closeConnection();
  }

  /**
     * Returns a string with non-alphanumerics removed
     * from the input.
     *
     * @param input the input String
     * @return String with non-alphanumerics removed
     */
  private String strip(String input)
  {
    String tmp = input.trim().toLowerCase();
    String ret = "";

    for( int i = 0; i < tmp.length(); i++ ) {
      if( ((tmp.charAt(i) >= '0') && (tmp.charAt(i) <= '9')) || 
	  ((tmp.charAt(i) >= 'a') && (tmp.charAt(i) <= 'z')) ) {
	ret += tmp.charAt(i);
      }
    }
    if( ret.equals("") )
      return null;
    else
      return ret;
  }

  private boolean createQuoteTable(String tableName)
  {
    if( connection == null ) {
      return false;
    }

    String sql = "CREATE TABLE "+tableName+" ("+
      "nick_id  INT ( 11 ) DEFAULT '0'                    NOT NULL,"+
      "quoteline MEDIUMTEXT DEFAULT ''                    NOT NULL,"+
      "created   DATETIME   DEFAULT '0000-00-00 00:00:00' NOT NULL,"+
      "KEY nick_id ( nick_id ) )";
    try {
      PreparedStatement pstmt = connection.prepareStatement(sql);
      pstmt.execute();
    } catch( SQLException e ) {
      e.printStackTrace();
      return false;
    }     
    return true;
  }

  private boolean createNickTable(String tableName) 
  {
    if( connection == null ) {
      return false;
    }

    String sql = "CREATE TABLE "+tableName+" ("+
      "nick_id  INT     ( 11 )             NOT NULL auto_increment,"+
      "name     VARCHAR ( 32 ) DEFAULT ''  NOT NULL,"+
      "alias_id INT     ( 11 ) DEFAULT '0' NOT NULL,"+
      "status   enum(' ','D','U'),"+
      "PRIMARY KEY     ( nick_id),"+
      "UNIQUE name     ( name),"+
      "KEY    name_2   ( name),"+
      "KEY    alias_id ( alias_id ) )";
    try {
      PreparedStatement pstmt = connection.prepareStatement(sql);
      pstmt.execute();
    } catch( SQLException e ) {
      e.printStackTrace();
      return false;
    }     
    return true;
  }

  private boolean createAliasTable(String tableName)
  {
    if( connection == null ) {
      return false;
    }

    String sql = "CREATE TABLE "+tableName+" ("+
      "alias_id INT     ( 11 ) NOT NULL auto_increment,"+
      "name     VARCHAR ( 32)  NOT NULL DEFAULT '',"+
      "PRIMARY KEY ( alias_id ),"+
      "UNIQUE name ( name ),"+
      "KEY name_2  ( name ) )";
    try {
      PreparedStatement pstmt = connection.prepareStatement(sql);
      pstmt.execute();
    } catch( SQLException e ) {
      e.printStackTrace();
      return false;
    }     
    return true;
  }

  /**
     * Get number of alias nicks for an alias_id
     *
     * @param aliasId 
     * @param nickTableName per-network name of nick table
     * @return number of alias nicks
     */
  private int getNumAliases(int aliasId,String nickTableName) 
  {
    if( connection == null ) {
      throw new IllegalStateException("Logger.getNumAliases(): connection == null!");
    }

    String sql = 
      "SELECT count(*) FROM " + nickTableName + " " +
      "WHERE alias_id = ?";
    int count = -1;
	
    boolean cont = true;
    boolean retry = false;
		
    while( cont ) {
      try {
	PreparedStatement pstmt = connection.prepareStatement(sql);
	pstmt.setInt(1,aliasId);
	ResultSet rs = pstmt.executeQuery();
	    
	if( rs.next() ) {
	  count = rs.getInt(1);
	}
	cont = false;
      } catch( SQLException e ) {
	if( retry ) {
	  cont = false;
	} else {
	  /* if failed/nick table missing, attempt to create it */
	  createNickTable(nickTableName);
	  retry = true;
	}
      }	 
    }
    return count;
  }

  private int getNickId(String nick,String nickTableName)
  {
    if( connection == null ) {
      return -1;
    }

    String sql = "SELECT nick_id FROM " + nickTableName + " WHERE name = ?";
    int nickId = -1;
	
    boolean cont = true;
    boolean retry = false;
		
    while( cont ) {
      try {
	PreparedStatement pstmt = connection.prepareStatement(sql);
	pstmt.setString(1,nick);
	ResultSet rs = pstmt.executeQuery();
	    
	if( rs.next() ) {
	  nickId = rs.getInt(1);
	}
	cont = false;
      } catch( SQLException e ) {
	if( retry ) {
	  cont = false;
	} else {
	  /* if failed/nick table missing, attempt to create it */
	  createNickTable(nickTableName);
	  retry = true;
	}
      }	 
    }
    return nickId;
  }

  /**
     * Get alias_id corresponding to a certain name in alias table.
     *
     * @param nick Nick whose alias id to look for
     * @param aliasTableName per-network name for alias table
     * @return alias_id for nick, or -1 if not found.
     */
  private int getAliasIdFromAliasTable(String nick,String aliasTableName)
  {
    if( connection == null ) {
      return -1;
    }

    String sql = 
      "SELECT alias_id FROM " + aliasTableName + 
      " WHERE name = ?";
    int aliasId = -1;

    boolean cont = true;
    boolean retry = false;
		
    while( cont ) {
      try {
	PreparedStatement pstmt = connection.prepareStatement(sql);
	pstmt.setString(1,nick);
	ResultSet rs = pstmt.executeQuery();
		
	if( rs.next() ) { 
	  aliasId = rs.getInt(1);
	}
	cont = false;
      } catch( SQLException e ) {
	if( retry ) {
	  cont = false;
	} else {
	  /* if failed/alias table missing, attempt to create it */
	  createAliasTable(aliasTableName);
	  retry = true;
	}
      }
    }
    return aliasId; 
  }

  /**
     * Inserts a new entry in nick table
     *
     * @param aliasId alias_id to assign to nick entry
     * @param nick nick (name) to insert
     * @param nickTableName per-network name of nick table
     */
  private void insertIntoNickTable(int aliasId,String nick,String nickTableName)
  {
    PreparedStatement pstmt = null;
	
    String sql = 
      "INSERT INTO " + nickTableName + " (nick_id,name,alias_id,status) "+
      "values (null,?,?,null)";
	
    try {
      pstmt = connection.prepareStatement(sql);
      pstmt.setString(1,nick);
      pstmt.setInt(2,aliasId); 
      pstmt.execute();
    } catch( SQLException e ) {
      e.printStackTrace();//##TODO## do what here?
      return;
    }
  }		

  /**
     * Get alias_id corresponding to a certain name in nick table.
     *
     * @param nick Nick whose alias id to look for
     * @param nickTableName per-network name for nick table
     * @return alias_id for nick, or -1 if not found.
     */
  private int getAliasIdFromNickTable(String nick,String nickTableName)
  {
    if( connection == null ) {
      return -1;
    }

    String sql = 
      "SELECT alias_id FROM " + nickTableName + 
      " WHERE name = ?";
    int aliasId = -1;

    boolean cont = true;
    boolean retry = false;
		
    while( cont ) {
      try {
	PreparedStatement pstmt = connection.prepareStatement(sql);
	pstmt.setString(1,nick);
	ResultSet rs = pstmt.executeQuery();
		
	if( rs.next() ) { 
	  aliasId = rs.getInt(1);
	}
	cont = false;
      } catch( SQLException e ) {
	if( retry ) {
	  cont = false;
	} else {
	  /* if failed/alias table missing, attempt to create it */
	  createNickTable(nickTableName);
	  retry = true;
	}
      }
    }
    return aliasId; 
  }

  /** 
     * Insert quote into log
     *
     * @param host Host of nick saying the message
     * @param channel channel on which the message was said
     * @param msg the said message
     */
  private void log(Host host,Channel channel,String msg)
  {
    String network = strip(caller.getInstanceData().getNetwork());
    String quoteTableName = network + "_" + strip(channel.getChannelName()) + "_quote";
    String nickTableName = network+"_nick";
    String aliasTableName =  network+"_alias";
    String nick = host.getNick();
    String sql = null;
    PreparedStatement pstmt = null;
    boolean cont = true;
    boolean retry = false; 
	
    if( connection == null ) {
      return;
    }

    /* ctcp actions and empty messages not logged */
    if( (msg == null) || (msg.length() < 1) || msg.startsWith("ACTION") ) {
      return;
    }

    /* look for nick in network_nick table */
    int nickId = getNickId(nick,nickTableName);

    /* if nick not found, insert it into network_alias and network_nick */
    if( nickId == -1 ) {
      sql = 
	"INSERT INTO " + aliasTableName+" (alias_id,name) "+
	"VALUES (null,?)";
	    
      cont = true;
      retry = false;

      while( cont ) {    
	/* insert to alias table */
	try {
	  pstmt = connection.prepareStatement(sql);
	  pstmt.setString(1,nick);
	  pstmt.execute();
	  cont = false;
	} catch( SQLException e ) {
	  if( retry ) {
	    cont = false;
	  } else {
	    /* if failed/alias table missing, attempt to create it */
	    if( !createAliasTable(aliasTableName) ) {
	      System.out.println("log(): could not create alias table!");
	      return;
	    }
	    retry = true;
	  }
	}
      }

      int aliasId = getAliasIdFromAliasTable(nick,aliasTableName);
      if( aliasId == -1 ) {
	System.out.println("log(): panic! alias not created");
	return;
      } else {

	//##TODO## start using insertIntoNickTable()
		
	sql = 
	  "INSERT INTO " + nickTableName + " (nick_id,name,alias_id,status) "+
	  "values (null,?,?,null)";

	/* insert to nick table */
	try {
	  pstmt = connection.prepareStatement(sql);
	  pstmt.setString(1,nick);
	  pstmt.setInt(2,aliasId); 
	  pstmt.execute();
	} catch( SQLException e ) {
	  e.printStackTrace();
	  return;
	}
		
	/* re-fetch the nick id */
	nickId = getNickId(nick,nickTableName);
      }
    }	    

    /* insert into quote table */
    sql = 
      "INSERT INTO " + quoteTableName + " (nick_id,quoteline,created) " +
      "VALUES (?,?,?)"; 
    cont = true;
    retry = false;
		
    while( cont ) {
      try {
	pstmt = connection.prepareStatement(sql);
	pstmt.setInt(1,nickId);
	pstmt.setString(2,msg);
	pstmt.setTimestamp(3,new Timestamp(new java.util.Date().getTime()));
	pstmt.execute();
	cont = false;
      } catch( SQLException e ) {
	e.printStackTrace();
	if( retry ) {
	  cont = false;
	} else {
	  /* if failed/quote table missing, attempt to create it */
	  createQuoteTable(quoteTableName);
	  retry = true;
	}
      }
    } 
  }
    
  /**
     * Counts number of quotelines for given nickId, or if none given 
     * (nick == null), get number of all quotes.
     *
     * @param nick nick in nick table or null if getting count
     * for all quotelines in database.
     * @param nickTableName name of nick table
     * @param aliasTableName name alias table
     * @param quoteTableName name of quote table
     * @return number of quotes for given nick or all nicks or -1 on error
     */
  private int getNumQuotes(String nick,String nickTableName,
			   String aliasTableName,String quoteTableName)
  {
    if( connection == null ) {
      return -1;
    }

    int numQuotes = -1;

    try {
      String sql = null;

      if( nick != null ) {
	sql = 
	  "SELECT COUNT( quote.quoteline ) FROM " + 
	  quoteTableName + " AS quote," + 
	  nickTableName + " AS nick," + 
	  aliasTableName + " AS alias " +
	  "WHERE " + 
	  "nick.nick_id = quote.nick_id AND " + 
	  "alias.alias_id = nick.alias_id AND " + 
	  "alias.alias_id = ?";
      } else {
	sql = 
	  "SELECT COUNT( quoteline ) FROM " + quoteTableName;
      }

      PreparedStatement pstmt = connection.prepareStatement(sql);

      if( nick != null ) {
	int aliasId = getAliasIdFromNickTable(nick,nickTableName);
	pstmt.setInt(1,aliasId);
      }

      ResultSet rs = pstmt.executeQuery();
	    
      if( rs.next() ) {
	numQuotes = rs.getInt(1);
      }
    } catch( SQLException e ) {
      e.printStackTrace();
    }
    return numQuotes; 
  }

  /**
     * Sets the alias_id for given entry in nick table
     *
     * @param nick name entry in nick table
     * @param aliasId new value for alias_id
     * @param nickTableName per-network name for nick table
     * @return true on success. false if no entry for given nick.
     */
  private boolean setAliasId(String nick, int aliasId, String nickTableName)
  {
    if( connection == null ) {
      throw new IllegalStateException("Logger.setAliasId(): connection == null!");
    }

    boolean ret = true;

    try {
      String sql = 
	"UPDATE " + nickTableName + " " + 
	"SET alias_id = ? " + 
	"WHERE name = ?";

      PreparedStatement pstmt = connection.prepareStatement(sql);
	    
      pstmt.setInt(1,aliasId);
      pstmt.setString(2,nick);

      pstmt.execute();
    } catch( SQLException e ) {
      ret = false;
    }

    return ret;
  }

  /**
     * Deletes an entry from alias table.
     *
     * @param nick indicates the entry to be deleted.
     * @param aliasTableName per-network name of alias table
     */
  private void deleteFromAliasTable(String nick,String aliasTableName)
  {
    if( connection == null ) {
      throw new IllegalStateException("Logger.deleteFromAliasTable(): connection == null!");
    }

    try {
      String sql = 
	"DELETE FROM " + aliasTableName + " " + 
	"WHERE name = ?";

      PreparedStatement pstmt = connection.prepareStatement(sql);
      pstmt.setString(1,nick);

      pstmt.execute();
    } catch( SQLException e ) {
      e.printStackTrace(); 
    }
  }

  /**
     * Return the number of nicks in nick table
     *
     * @param nicktableName per-network name of nick table
     */
  private int getNumNicks(String nickTableName)
  {
    if( connection == null ) {
      return -1;
    }

    int numNicks = -1;

    try {
      String sql = "SELECT COUNT( * ) FROM " + nickTableName;

      PreparedStatement pstmt = connection.prepareStatement(sql);
      ResultSet rs = pstmt.executeQuery();
		
      if( rs.next() ) {
	numNicks = rs.getInt(1);
      }
    } catch( SQLException e ) {
      e.printStackTrace();
    }
    return numNicks;
  }

  /**
     * Joins two nicks (nick1 to nick2) together by their alias_id's:
     * <ul>
     *   <li>Gets alias_id from nick2 and sets nick1's alias_id to the value.
     *   <li>Removes nick1 from alias table (if it exists there)
     * </ul>
     *
     * @param nick1 nick to assign new alias_id to
     * @param nick2 nick to acquire the alias_id from
     * @param nickTableName per-network name of nick table
     * @param aliasTableName per-network name of alias table
     * @param insert indicates whether to INSERT nick1 into nick table if 
     * it does not exists there. this is used with autoNickJoining on NICK changes.
     */
  private void joinNicks(String nick1, String nick2, String nickTableName,
			 String aliasTableName, boolean insert)
  {
    int aliasId = getAliasIdFromNickTable(nick2, nickTableName);
    if ( aliasId == -1 ) {
      write("No such nick " + nick2);
    } else {
      if( getNickId(nick1, nickTableName) == -1 ) {
	if ( insert ) {
	  insertIntoNickTable(aliasId, nick1, nickTableName);
	} else {
	  write("No such nick " + nick1);
	  return;
	}
      }
		
      if ( setAliasId(nick1, aliasId, nickTableName) ) {
	write("Joined " + nick1 + " to " + nick2);
      } else {
	write("No such nick " + nick1);
      }
    }
  }

  /**
     * Quote command. Displays a random quote from log.<P>
     *
     * @param host host of invoker
     * @param invoker invoking User
     * @param args arguments of command
     * @param channel target channel
     */
  private void commandQuote(Host host,User invoker,String args[],Channel channel)
  {
    if( connection == null ) {
      return;
    }

    if( (args == null) || (args.length != 1) ) {
      return;
    }

    String nick = args[0];

    // don't quote self 
    if( nick.equals(caller.getHost().getNick()) ) {
      return;
    }

    String network = strip(caller.getInstanceData().getNetwork()); 
    String quoteTableName = network+"_"+strip(channel.getChannelName())+"_quote";
    String nickTableName = network+"_nick";
    String aliasTableName = network+"_alias";

    if( (args != null) && (args.length == 1) ) {
      if( (invoker == null) || invoker.isChanAdmin(channel.getChannelName()) || 
	  invoker.isGlobalAdmin() || invoker.isOp(channel.getChannelName()) ) {

	int aliasId = getAliasIdFromNickTable(nick,nickTableName);
	int numQuotes = getNumQuotes(nick,nickTableName,
				     aliasTableName,quoteTableName);
	if( numQuotes > 0 ) {
	  int randomN = (int)(Math.random() * (numQuotes - 1));

	  String sql = 
	    "SELECT quoteline, created FROM " + 
	    quoteTableName + " AS quote," + 
	    nickTableName + " AS nick," + 
	    aliasTableName + " AS alias " + 
	    "WHERE " + 
	    "nick.nick_id = quote.nick_id AND " + 
	    "alias.alias_id = nick.alias_id AND " + 
	    "alias.alias_id = ? " + 
	    "LIMIT " + randomN + ",1";

	  try {
	    PreparedStatement pstmt = connection.prepareStatement(sql);
	    pstmt.setInt(1,aliasId);
	    ResultSet rs = pstmt.executeQuery();
		    
	    if( rs.next() ) {
	      String quote = rs.getString(1);
	      java.util.Date created = rs.getTimestamp(2);

	      write("\"" + quote + "\" (c) (" + sdf.format(created) + ") " + nick);
	    }
	  } catch( SQLException e ) { 
	    e.printStackTrace(); 
	  }
	}
      }
    }
  }

  /**
     * Last command. Displays N last quotelines said by given nick.
     *
     * @param host host of invoker
     * @param invoker invoking User
     * @param args arguments of command
     * @param channel target channel
     */
  private void commandLast(Host host,User invoker,String args[],Channel channel)
  {
    if( connection == null ) {
      throw new IllegalStateException("Logger.commandLast(): connection == null!");
    }

    String network = strip(caller.getInstanceData().getNetwork());
    String quoteTableName = network+"_"+strip(channel.getChannelName())+"_quote";
    String nickTableName = network+"_nick";
    String aliasTableName = network+"_alias";
	
    int limit = 5; //##TODO## remove hardcoding

    if( (args != null) && (args.length >= 1) ) {
      if( invoker.isGlobalAdmin() || invoker.isChanAdmin(channel.getChannelName()) || 
	  invoker.isOp(channel.getChannelName()) ) { 

	/* fetch N last rows for nick */
	String nick = args[0]; 

	int aliasId = getAliasIdFromNickTable(nick,nickTableName);
	int numQuotes = getNumQuotes(nick,nickTableName,
				     aliasTableName,quoteTableName);
	if( numQuotes > 0 ) {
	  int offset = 0;
		    
	  if( numQuotes > limit ) {
	    offset = (numQuotes - limit);
	  }
		    
	  String sql = 
	    "SELECT quote.quoteline,quote.created,nick.name FROM " + 
	    quoteTableName + " AS quote," + 
	    nickTableName + " AS nick," + 
	    aliasTableName + " AS alias " + 
	    "WHERE " + 
	    "nick.nick_id = quote.nick_id AND " + 
	    "alias.alias_id = nick.alias_id AND " + 
	    "alias.alias_id = ? " + 
	    "ORDER BY created " + 
	    "LIMIT " + offset + "," + limit;

	  try {
	    PreparedStatement pstmt = connection.prepareStatement(sql);
	    pstmt.setInt(1,aliasId);
	    ResultSet rs = pstmt.executeQuery();
			
	    while( rs.next() ) {
	      String quote = rs.getString(1);
	      java.util.Date created = rs.getTimestamp(2);
	      String name = rs.getString(3);

	      write("\"" + quote + "\" (" + sdf.format(created) + ") (c) " + name);  
	    }
	  } catch( SQLException e ) { 
	    e.printStackTrace(); 
	  }
	}
      }
    }
  }

  /**
     * Edit command
     *
     * @param host host of invoker
     * @param invoker invoking User
     * @param args arguments of command
     * @param channel target channel
     */
  private void commandEdit(Host host,User invoker,String args[],Channel channel)
  {
    if( (args != null) && (args.length >= 1) ) {
      if( args[0].equals("logvars") ) {
	/* requires global admin */
	if( invoker.isGlobalAdmin() && (args.length == 4) ) {
	  dbName = args[1];
	  try {
	    autoQuote = (Integer.parseInt(args[2]) == 1);
	    autoNickJoin = (Integer.parseInt(args[3]) == 1);
	  } catch( NumberFormatException e ) {
	    write("Bad logvar values.");
	    return;
	  }
	  write("Edited logvars for " + channel.getChannelName() + ".");
	  changed = true;

	  // close existing connection and reconnect to new db 
	  closeConnection();
	  connection = core.getDatabaseConnection(dbName);
	}
      }
    }
  }

  /**
     * Add command
     *
     * @param host host of invoker
     * @param invoker invoking User
     * @param args arguments of command
     * @param channel target channel
     */
  private void commandAdd(Host host,User invoker,String args[],Channel channel)
  {
    String network = strip(caller.getInstanceData().getNetwork());
    String nickTableName = network + "_nick";
    String aliasTableName = network + "_alias";

    if( (args != null) && (args.length >= 1) ) {
      if( args[0].equals("nickjoin") ) {
	if( invoker.isGlobalAdmin() || 
	    invoker.isChanAdmin(channel.getChannelName()) ) {
	  if( args.length == 3 ) {
	    joinNicks(args[1], args[2], nickTableName, 
		      aliasTableName, false);
	  }
	}
      }
    }
  }

  /**
     * List command
     *
     * @param host host of invoker
     * @param invoker invoking User
     * @param args arguments of command
     * @param channel target channel
     */
  private void commandList(Host host,User invoker,String args[],Channel channel)
  {
    String network = strip(caller.getInstanceData().getNetwork());
    String quoteTableName = network+"_"+strip(channel.getChannelName())+"_quote";
    String nickTableName = network+"_nick";
    String aliasTableName =  network+"_alias";

    if( (args != null) && (args.length >= 1) ) {
      if( args[0].equals("logvars") ) {
	/* requires global admin */
	if( invoker.isGlobalAdmin() ) {
	  String var1 = dbName;
	  if( (dbName == null) || dbName.equals("") ) {
	    var1 = "-";
	  }
	  String var2 = (autoQuote) ? "1" : "0";
	  String var3 = (autoNickJoin) ? "1" : "0";
	  write("global logvars are: " + 
		"db-name=" + var1 + " auto-quote=" + var2 + 
		" auto-nick-join=" + var3);
	}
      } else if( args[0].equals("logstats") ) {
	if( invoker.isGlobalAdmin() || invoker.isChanAdmin(channel.getChannelName()) 
	    || invoker.isOp(channel.getChannelName()) ) {

	  if( args.length == 2 ) {
	    /* show stats for a nick */
	    String nick = args[1];
	    //	int nickId = getNickId(nick,nickTableName);
	    int numQuotes = getNumQuotes(nick,nickTableName,
					 aliasTableName,quoteTableName);
	    int aliasId = getAliasIdFromNickTable(nick,nickTableName);
	    int numAliases = getNumAliases(aliasId,nickTableName);
	    if( numQuotes > 0 ) {
	      write("logstats for " + nick + ": " + numQuotes + 
		    " quotes, " + numAliases + " alias nicks");
	    } else {
	      write("no such nick " + nick);
	    }
	  } else if( args.length == 1 ) {
	    /* show general stats */
	    int numNicks = getNumNicks(nickTableName);
	    int numQuotes = getNumQuotes(null,nickTableName,
					 aliasTableName,quoteTableName);
			
	    write("logstats: "+numNicks+" nicks, "+numQuotes+" quotes total.");
	  }
	}
      }
    }
  }
		
  /**
     * Process command message. assuming valid channel argument.
     *
     * @param msg command msg string
     * @param channel valid channel name
     */
  private void processCmdMsg(Host host,String cmd,Channel channel,String args[]) 
  {
    User user = caller.findUser(host);  

    /* all commands require user in bot */
    if( user != null ) {
      /* bot needs to be ON channel */
      if( channel.isJoined() ) {
	/* select command */
	if( cmd.equals("quote") ) {
	  commandQuote(host,user,args,channel);
	} else if( cmd.equals("last") ) {
	  commandLast(host,user,args,channel);
	} else if( cmd.equals("edit") ) {
	  commandEdit(host,user,args,channel);
	} else if( cmd.equals("list") ) {  
	  commandList(host,user,args,channel);
	} else if( cmd.equals("add") ) {  
	  commandAdd(host,user,args,channel);
	}
      }
    }
  }

  /**
     * Handles NICK changes
     * :porkkanaz!^asd@dreamland.tky.hut.fi NICK :testink
     *
     * @param message NICK IrcMessage to process
     */
  private void doNick(IrcMessage message) 
  {
    // indicate we dont want output 
    source = null;

	// do automatic nick joining?
    if( autoNickJoin ) {
      String network = strip(caller.getInstanceData().getNetwork());
      String nickTableName = network + "_nick";
      String aliasTableName = network + "_alias";

      Host host = new Host(message.getPrefix());
      String newNick = message.getTrailing();

      // new nick must not be found in nick table
      if ( getNickId(newNick, nickTableName) == -1 ) { 
	joinNicks(newNick, host.getNick(), nickTableName, aliasTableName, true);
      } 
    }
  }

  /**
   * Handles PRIVMSGs.<P>
   *
   * @param message PRIVMSG IrcMessage to process
   */
  private void doPrivmsg(IrcMessage message) {
    String prefix = message.getPrefix();

    if ( prefix == null ) {
      Log.error(this, "doPrivmsg(): prefix: null, command: " + message.getCommand() + " in message: " + message);
      return;
    }

    Host host = new Host(prefix);
    Channel channel = null;
    String args[] = null;
    String cmd = null;

    String trailing = message.getTrailing();
    if ( (trailing == null) || (trailing.length() < 1) ) {
      Log.error(this, "doPrivmsg(): null or empty trailing: " + trailing + ", message: " +
		message.toString());
      return;
    }	    

    String arguments[] = message.getArguments();
    if ( arguments == null ) {
      Log.error(this, "doPrivmsg(): arguments == null! message: " + message);
    }

    if( arguments[0].equalsIgnoreCase(caller.getHost().getNick()) ) {
      // PRIVMSG to bot 
      this.source = host.getNick();
      args = StringUtil.separate(trailing,' ');
      if( (args != null) && (args.length >= 2) ) {
	channel = caller.findChannel(args[1]);
	cmd = args[0];
	args = StringUtil.range(args,2);
      }
    } else {
      // PRIVMSG to channel 
      channel = caller.findChannel(arguments[0]);
      this.source = arguments[0];
      if ( trailing.charAt(0) == '!' ) {
	if ( trailing.length() > 1 ) {
	  args = StringUtil.separate(trailing.substring(1),' ');
	  if( args != null ) {
	    cmd = args[0];
	    args = StringUtil.range(args,1);
	  }
	}
      } else {
	// message is a normal channel message 
	log(host, channel, trailing);
      }
    }

    if( (channel != null) && (cmd != null) ) {
      processCmdMsg(host, cmd, channel, args);
    }
  }

  /**
   * Handles JOINs
   *
   * @param message the JOIN IrcMessage
   */
  public void doJoin(IrcMessage message) 
  {
    if( autoQuote ) {
      Channel channel = caller.findChannel(message.getTrailing());
      Host host = new Host(message.getPrefix());

      if( channel != null ) {
	source = channel.getChannelName();
	String args[] = new String[1];
	args[0] = host.getNick();
	commandQuote(host, null, args, channel);
      }
    }
  } 

  /**
   * Processes incoming IrcMessages from a ServerConnection.<P>
   *
   * @param message IrcMessage to process
   * @param serverConnection invoking ServerConnection
   */
  protected void processMessage(IrcMessage message, ServerConnection serverConnection) {
    this.caller = serverConnection;

    String command = message.getCommand();
    String trailing = message.getTrailing();

    // validate db connection 
    try {
      if ( (connection == null) || connection.isClosed() ) {
	connection = core.getDatabaseConnection(dbName);
      }
    } catch ( SQLException e ) {
      throw new IllegalStateException("Logger.processMessage(): " +
				      e.getMessage());
    }

    if ( command.equals("PRIVMSG") ) {
      if ( (trailing != null) && 
	   (trailing.length() > 0) ) {
	doPrivmsg(message);
      }
    } else if( command.equals("JOIN") ) {
      doJoin(message);
    } else if( command.equals("NICK") ) {
      doNick(message);
    }

    // set per-request vars to null 
    this.caller = null;
    this.source = null;
  }

  /**
   * Sends message to source (channel/user)
   *
   * @param message message to send
   */
  private void write(String message)
  {
    if( source != null ) {
      caller.write("PRIVMSG "+source+" :"+message+"\n");
    } 
  }
}










