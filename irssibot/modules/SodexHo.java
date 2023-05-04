/*
 * $Id: SodexHo.java,v 1.1.1.1 2002/11/08 10:51:59 dreami Exp $
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
import irssibot.user.Host;
import irssibot.user.User;
import irssibot.util.StringUtil;
import irssibot.core.ServerConnection;
import irssibot.core.Core;
import irssibot.core.Channel;

import java.net.URLConnection;
import java.net.URL;
import java.net.MalformedURLException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.Vector;
import java.util.Properties;
import java.util.Enumeration;
import java.util.Date;
import java.text.SimpleDateFormat;

/**
 * Includes methods for retirieving information about Sodex Ho restaurants
 * from the WWW.
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.1.1.1 $
 */
public class SodexHo extends AbstractModule
{
  private static String moduleInfo = "SodexHo $Revision: 1.1.1.1 $";

  /* per-request temp data */
  private Host host = null;
  private String source = null;
  private ServerConnection caller = null;
  
  public Properties getModuleState()
  {
    return null;
  }
  
    /**
     * Default constructor
     *
     */
    public SodexHo() {
	super(SodexHo.class.getName());
    }

  /**
   * returns a module info string 
   */
  public String getModuleInfo()
  {
    return moduleInfo;
  }

  public boolean onLoad(Properties state,Core core)
  {
    return true;
  }
  
  public void onUnload()
  {
  }
  
  /** 
   * Displays menu for given Sodex Ho restaurant 
   *
   * @param location <code>LocationDescriptor</code> for the location
   * @param date desired date in the form of DD.MM.
   * @return true if menu found and shown, false if not found
   */
  private boolean showMenu(LocationDescriptor location, String date) 
    throws MalformedURLException, IOException
  {
    String locationURL = 
      "http://www.sodexho.fi/cgi-bin/lounaslista.cfm?id=" + 
      location.id + "&kieli=1";
    URL url = new URL(locationURL);
    URLConnection connection = url.openConnection();
    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

    /* look for correct date */
    while( true ) {
      String line = in.readLine();

      /* end of page content */
      if( line == null ) {
	return false;
      }
     
      int index =  line.indexOf(date);

      if( index != -1 ) {
	/* strip date and &nbsp; off the line */
	int semicolonIndex = line.indexOf(';', index);
	String menu = line.substring(semicolonIndex + 1);

	String formatted = StringUtil.replace(menu, "<BR>", "");
	formatted = StringUtil.replace(formatted, "<B>", "");
	formatted = StringUtil.replace(formatted, "</B>", "");
	formatted = StringUtil.replace(formatted, "&nbsp;", " ");
	formatted = StringUtil.replace(formatted, "</FONT>", "");
	
	write("Menu on " + date + " for " + location.name + ": " + formatted);

	return true;
      }      
    }
  }

  /**
   * Handles command 'sodexho'
   *
   * @param host Host of invoker
   * @param invoker User object of invoker
   * @param args command arguments
   * @param channel Channel where command takes place   *
   */
  private void commandSodexho(Host host, User user, String args[], Channel channel) 
  {
    if( (args == null) || (args.length < 1) ) {
      return;
    }

    String location = args[0].toLowerCase();
    Vector list = null;
    try {
      list = getLocations();
    } catch ( Exception e ) {
      //##TODO## log this!
      write("Error retrieving locations: " + e.getMessage());
      return;
    }

    SimpleDateFormat sdf = new SimpleDateFormat("d'.'M'.'");
    String date = sdf.format(new Date());

    if( args.length == 2 ) {
	date = args[1];
	if( !date.endsWith(".") ) {
	    date += ".";
	}
    }

    Enumeration en = list.elements();
    while( en.hasMoreElements() ) {
      LocationDescriptor loc = (LocationDescriptor)en.nextElement();
      String name = loc.name.toLowerCase();
      if( name.indexOf(location) != -1 ) {
	try {
	  if( !showMenu(loc, date) ) {
	    write("Could not find menu for " + loc.name);
	    return;
	  }
	} catch ( Exception e ) {
	  write("Error retrieving menu for " + loc.name + ": " + e.getMessage());
	  return;
	}
	break;
      }
    }
  }

  /** 
   * Retrieves list of all SodexHo restaurants
   *
   * @return list of locations as a <code>Vector</code>. The
   * elements are of type <code>LocationDescriptor</code>.
   */
  private Vector getLocations()
    throws MalformedURLException, IOException
  {
    String listURL = "http://www.sodexho.fi/cgi-bin/toimipaikat.cfm";
    URL url = new URL(listURL);
    URLConnection connection = url.openConnection();
    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
    Vector v = new Vector();

    String line = in.readLine();
    while( line != null ) {
      /* if all locations scanned, stop */
      if( line.indexOf("onclick=\"siirry_toimipaikkaan();\"") != -1 ) {
	break;
      }

      line = line.trim();

      if( line.startsWith("<OPTION value=") ) {
	/* extract the location id from the line */
	int indexFirst = line.indexOf("\"");
	int indexSecond = line.indexOf("\"", indexFirst + 1);
	
	int id = -1;
	try {
	  id = Integer.parseInt(line.substring(indexFirst + 1, indexSecond));
	} catch ( NumberFormatException e ) {
	  /* just skip this entry */
	}

	if( id != -1 ) {
	  int gtIndex = line.indexOf('>');
	  v.add(new LocationDescriptor(id, line.substring(gtIndex + 1)));
	}
      }
      line = in.readLine();
    }
    return v;
  }

  /**
   * Handles command 'list'
   *
   * @param host Host of invoker
   * @param invoker User object of invoker
   * @param args command arguments
   * @param channel Channel where command takes place   *
   */
  private void commandList(Host host, User user, String args[], Channel channel) 
  {
    if( (args != null) && (args.length >= 1) && (args[0].equals("sodexho")) ) {
      
      Vector list = null;
      try {
	list = getLocations();
      } catch ( Exception e ) {
	//##TODO## log this!
	write("Error retrieving locations: " + e.getMessage());
	return;
      }

      String locList = "";
      String delim = "";

      int num = 20;
      int total = 0;
      int from = 0;

      /* see if from -argument supplied */
      if( args.length == 2 ) {
	try {
	  from = Integer.parseInt(args[1]);
	} catch( NumberFormatException e ) {
	  /* do nothing */
	}
      }

      Enumeration en = list.elements();
      while( en.hasMoreElements() && ((total - from) < num) ) {
	LocationDescriptor loc = (LocationDescriptor)en.nextElement();
	if( total >= from ) {
	  locList += delim + loc.name;
	  delim = ", ";
	}
	total++;
      }

      write("possible locations for !sodexho <location>: (" + from + " to " + 
	    (from + num) + ")");
      write(locList);
    }
  }
  
  /**
   * Process command message. assuming valid channel argument.
   *
   * @param msg command msg string
   * @param channel valid channel name
   */
  
  private void processCmdMsg(Host host, String cmd, Channel channel, String args[]) {
    User user = caller.findUser(host);
    
     /* all commands require user in bot */
     if( (user != null) && channel.isJoined() ) {
       if( cmd.equals("sodexho") ) {
	 commandSodexho(host, user, args, channel);
       } else if( cmd.equals("list") ) {
	 commandList(host, user, args, channel);
       }
     }
   }

  /**
   * Handles PRIVMSGs 
   *
   * @param message PRIVMSG IrcMessage to process
   */
  private void doPrivmsg(IrcMessage message) {
    Host host = new Host(message.getPrefix());
    Channel channel = null;
    String args[] = null;
    String cmd = null;
    
    String trailing = message.getTrailing();
    String arguments[] = message.getArguments();
	
    if( arguments[0].equalsIgnoreCase(caller.getHost().getNick()) ) {
      /* PRIVMSG to bot */
      this.source = host.getNick();
      args = StringUtil.separate(trailing, ' ');
      if( (args != null) && (args.length >= 2) ) {
	channel = caller.findChannel(args[1]);
	cmd = args[0];
	args = StringUtil.range(args,2);
      }
    } else {
      /* PRIVMSG to channel */
      channel = caller.findChannel(arguments[0]);
      this.source = arguments[0];
      if( (trailing.charAt(0) == '!') &&
	  (trailing.length() > 1) ) {
	  args = StringUtil.separate(trailing.substring(1),' ');
	  if( args != null ) {
	    cmd = args[0];
	    args = StringUtil.range(args,1);
	  }
      }
    }
    
      if( (channel != null) && (cmd != null) ) {
	processCmdMsg(host, cmd, channel, args);
      }      
  }
  
    /**
     * Processes incoming IrcMessages from a ServerConnection
     *
     * @param message IrcMessage to process
     * @param serverConnection invoking ServerConnection
     */
    protected void processMessage(IrcMessage message,ServerConnection serverConnection)
    {
	this.caller = serverConnection;
	
	if( message.getCommand().equals("PRIVMSG") ) {
	    String trailing = message.getTrailing();
	    if( (trailing != null) && 
		(trailing.length() > 0) ) {
		doPrivmsg(message);
	    }
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
    caller.write("PRIVMSG " + source + " :" + message + "\n");
  }
}

/**
 * Represents an entry in a list of locations of SodexHo restaurants
 *
 */
class LocationDescriptor 
{
  /**
   * Id number of the location
   */
  public int id = -1;
  /**
   * Name of the location
   */
  public String name = null;
  
  public LocationDescriptor(int id, String name) 
  {
    this.id = id;
    this.name = name;
  }
}











