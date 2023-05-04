/*
 * $Id: Alko.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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

import irssibot.util.*;
import irssibot.util.log.Log;
import irssibot.core.*;
import irssibot.user.*;
import irssibot.protocol.*;

import java.util.*;
import java.io.*;
import java.net.Socket;

/**
 * Includes methods for retrieving infomation from www.alko.fi.<P>
 * 
 * @author Matti Dahlbom 
 * @version $Name:  $ $Revision: 1.2 $
 */ 
public class Alko extends AbstractModule {
  /**
   * Module info string.
   */
  private static final String moduleInfo = "Alko module ($Revision: 1.2 $)";

  /**
   * Returns a module info string.<P>
   *
   * @return module info string
   */
  public String getModuleInfo() {
    return moduleInfo;
  }

  /**
   * Constructs.<P>
   *
   */
  public Alko() {
    super("Alko");
  }

  /**
   * Called upon loading the module.<P>
   *
   * @param state initial module state
   * @param core reference to core
   */
  public boolean onLoad(Properties state, Core core) {
    return true;
  }

  /**
   * Called upon unloading the module.<P>
   */
  public void onUnload() {
    // do nothing
  }
  /**
   * Returns module properties.<P>
   *
   */
  public Properties getProperties() {
    return null;
  }

  /**
   * Reads contents of an URL as a string.<P>
   *
   * ##TODO## move to an util class!
   *
   * @param url url of the form http://host/querystring
   * @return url contents
   * @exception IOException if problems reading url
   */
  public static String readURL(String url) throws IOException {
    String host = null;
    String query = null;
    int port = 80;

    int index = url.indexOf("//");
    if ( index == -1 ) {
      throw new IllegalArgumentException("Malformed url: " + url);
    }

    host = url.substring(index + 2);
    index = host.indexOf("/");
    if ( index == -1 ) {
      throw new IllegalArgumentException("Malformed url: " + url);
    }

    query = host.substring(index);
    host = host.substring(0, index);
    
    index = host.indexOf(":");
    if ( index != -1 ) {
      port = Integer.parseInt(host.substring(index + 1));
      host = host.substring(0, index);
    }

    Socket socket = new Socket(host, port);
    InputStream in = new BufferedInputStream(socket.getInputStream());
    OutputStream out = new BufferedOutputStream(socket.getOutputStream());

    String s = "GET " + query + " HTTP/1.0\n\n";
    out.write(s.getBytes());
    out.flush();

    int curbyte = 0;
    int count = 0;
    int bufLen = 10 * 65536;
    byte buffer[] = new byte[bufLen];

    while ( curbyte != -1 ) {
      curbyte = in.read();

      if ( curbyte != -1 ) {
	if ( count >= bufLen ) {
	  //##TODO## write a closeSafely() and move there
	  out.close();
	  in.close();
	  socket.close();
	  throw new IOException("URL content too long! count: " + count);
	}

	buffer[count++] = (byte)curbyte;
      }
    }

    String content = new String(buffer, 0, count);
    buffer = null;

    //##TODO## write a closeSafely() and move there
    out.close();
    in.close();
    socket.close();

    return content;
  }

  /**
   * Displays info about a red wine.<P>
   *
   * @param name wine name or part of name 
   * @param url url to the wine
   * @param source source (nick or channel) or the message 
   * @param serverConnection calling server connection 
   */ 
  private void displayPunkkuInfo(String name, String url, 
				 String source, ServerConnection serverConnection) {
    String content = null;
    try {
      content = readURL("http://www.alko.fi" + url);
    } catch ( IOException e ) {
      Log.log(this, e);
      return;
    }

    String s = "<em><font face=\"Arial,Lucida,Helvetica\" color=\"#400040\"><font size=\"2\"><b>";
    int startIndex = content.indexOf(s);
    if ( startIndex == -1 ) {
      serverConnection.write("PRIVMSG " + source + " :Invalid HTML on info page.\n");
      return;
    }

    // parse country
    content = content.substring(startIndex + s.length());
    int indexEnd = content.indexOf("</b>");
    String country = content.substring(0, indexEnd);
    
    // parse price
    s = "<td width=\"75\" align=\"RIGHT\" valign=\"TOP\">";
    content = content.substring(content.indexOf(s));
    content = content.substring(content.indexOf("<b>") + 3);
    indexEnd = content.indexOf("&");
    String price = content.substring(0, indexEnd);

    serverConnection.write("PRIVMSG " + source + " :" + name + ", country: " + 
			   country + ", price: " + price + "e, info: " + 
			   "http://www.alko.fi" + url + "\n");
  }

  /**
   * Looks through the red wine list for matches.<P>
   *
   * @param search name of wine to look for
   * @param list string containing the wine list html
   * @param source source (nick or channel) or the message
   * @param serverConnection calling server connection
   */
  private void searchPunkkuList(String search, String list, String source, 
				ServerConnection serverConnection) {
    int count = 0;
    String fonttag = "<FONT FACE=Arial color=\"#80000\" SIZE=\"-2\">";
    String fontCloseTag = "</FONT>";
    String ahreftag = "<a href=\"";
    String sep = "";
    String matches = "";
    String correctName = null;
    String correctURL = null;

    while ( true ) {
      int indexStart = list.indexOf("<tr>");
      if ( indexStart == -1 ) {
	break;
      }

      int indexEnd = list.indexOf("</tr>");
      if ( indexEnd == -1 ) {
	break;
      }

      if ( indexEnd < indexStart ) {
	serverConnection.write("PRIVMSG " + source + " :Invalid HTML on list page.\n");
	return;
      }

      String token = list.substring(indexStart + 4, indexEnd);
      list = list.substring(indexEnd + 4);

      // parse url
      indexStart = token.indexOf(ahreftag);
      if ( indexStart == -1 ) {
	continue;
      }
      token = token.substring(indexStart + ahreftag.length());

      indexEnd = token.indexOf("\"");
      if ( indexEnd == -1 ) {
	continue;
      }

      String url = token.substring(0, indexEnd);

      // parse name
      indexStart = token.indexOf(fonttag);
      if ( indexStart == -1 ) {
	continue;
      }

      token = token.substring(indexStart + fonttag.length());
      
      indexEnd = token.indexOf(fontCloseTag);
      if ( indexEnd == -1 ) {
	continue;
      }

      String name = token.substring(0, indexEnd);
      if ( name == null ) {
	continue;
      }

      name = name.trim();
      
      // see if matches
      if ( StringUtil.wildmatch("*" + search + "*", name) ) {
	correctName = name;
	correctURL = url;

	// see if exact match
	if ( search.equalsIgnoreCase(name) ) {
	  count = 1;
	  break;
	} 

      	matches += sep + name;
      	sep = ", ";

	count++;
      }
    }

    if ( count == 0 ) {
      serverConnection.write("PRIVMSG " + source + " :No matches.\n");
    } else if ( count == 1 ) {
      // display details about the wine
      displayPunkkuInfo(correctName, correctURL, source, serverConnection);
    } else {
      serverConnection.write("PRIVMSG " + source + " :Be more specific! matches: " + 
			     matches + "\n");
    }
  }

  /**
   * Retrieves info about a named red wine.<P>
   *
   * @param name wine name or part of name
   * @param source source (nick or channel) or the message
   * @param serverConnection calling server connection
   */
  private void doPunkku(String name, String source, ServerConnection serverConnection) {
    String listURL = "http://www.alko.fi/wwwalkofi/wwwBachus.nsf/viewPunaviinitByAll?openview&count=900";
    
    String listContent = null;
    try {
      listContent = readURL(listURL);
    } catch ( IOException e ) {
      Log.log(this, e);

      //##TODO## error msg to source

      return;
    }
    
    searchPunkkuList(name, listContent, source, serverConnection);
  }

  /**
   * Handles PRIVMSGs.<P>
   *
   * @param message PRIVMSG IrcMessage to process
   * @param serverConnection calling server connection
   */
  private void doPrivmsg(IrcMessage message, ServerConnection serverConnection) {
    Host host = new Host(message.getPrefix());
    Channel channel = null;

    String trailing = message.getTrailing();
    String arguments[] = message.getArguments();

    if ( (arguments == null) || (arguments.length < 1) ) {
      Log.error(this, "doPrivmsg(): arguments == null or of zero length");
      return;
    }
    
    String source = null;
    String args[] = null;
    String cmd = null;

    if ( arguments[0].equalsIgnoreCase(serverConnection.getHost().getNick()) ) {
      // PRIVMSG to bot 
      source = host.getNick();
      args = StringUtil.separate(trailing, ' ');
      if ( (args != null) && (args.length >= 2) ) {
	channel = serverConnection.findChannel(args[1]);
	cmd = args[0];
	args = StringUtil.range(args, 2);
      }
    } else {
      // PRIVMSG to channel 
      channel = serverConnection.findChannel(arguments[0]);
      source = arguments[0];

      if ( (trailing.charAt(0) == '!') &&
	   (trailing.length() > 1) ) {
	args = StringUtil.separate(trailing.substring(1), ' ');
	if ( args != null ) {
	  cmd = args[0];
	  args = StringUtil.range(args, 1);
	}
      }
    }

    if ( cmd != null ) {
      if ( cmd.equals("punkku") ) {
	if ( args != null ) {
	  doPunkku(StringUtil.join(args), source, serverConnection);
	}
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
    String command = message.getCommand();
    String trailing = message.getTrailing();

    if ( command.equals("PRIVMSG") ) {
      if ( (trailing != null) && 
	   (trailing.length() > 0) ) {
	doPrivmsg(message, serverConnection);
      }
    }
  }
}
