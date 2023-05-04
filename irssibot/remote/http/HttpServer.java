/*
 * $Id: HttpServer.java,v 1.1.1.1 2002/11/08 10:51:59 dreami Exp $
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
package irssibot.remote.http;

import irssibot.core.*;
import irssibot.util.log.Log;

import java.util.*;
import java.net.*;
import java.io.*;

/**
 * HTTP interface for remote administration of IrssiBot.<P>
 *
 * @author Matti Dahlbom 
 * @version $Name:  $ $Revision: 1.1.1.1 $
 */
public class HttpServer extends Thread {
  /**
   * Socket timeout value (in milliseconds).
   */
  private static final int SOCKET_SO_TIMEOUT = 100;
  /**
   * Module info string
   */
  private static final String info = "HttpServer";
  /**
   * Allowed hostmasks
   */
  private ArrayList hostsAllow = null;
  /**
   * Disallowed hostmasks
   */
  private ArrayList hostsDeny = null;
  /**
   * Server socket
   */
  private ServerSocket serverSocket = null;
  /**
   * Whether to keep listening to connections. When set to false, the server thread
   * will exit.
   */
  private boolean isRunning = true;

  /**
   * Initializes the server.<P>
   *
   * @param port server port to listen to
   * @param hostsAllow allowed hostmasks
   * @param hostsDeny disallowed hostmasks
   */
  public void init(int port, ArrayList hostsAllow, ArrayList hostsDeny) 
    throws IOException {
    // attempt to open server socket
    serverSocket = new ServerSocket(port);
    serverSocket.setSoTimeout(SOCKET_SO_TIMEOUT);
      
    this.hostsAllow = hostsAllow;
    this.hostsDeny = hostsDeny;
  }

  /**
   * Stops the server thread.<P>
   *
   */
  public void stopServer() {
    isRunning = false;
    interrupt();

    // close the server socket
    try {
      serverSocket.close();
    } catch ( IOException e ) {
      Log.log(this, e);
    }
  }

  /**
   * Listens to the server socket for inbound connections.<P>
   *
   */
  public void run() {
    Socket socket = null;

    Log.debug(this, "run() started");

    while ( isRunning ) {
      socket = null;

      try {
	socket = serverSocket.accept();
      } catch ( InterruptedIOException e ) {
	// do nothing
      } catch ( IOException e ) {
	Log.log(this, e);
	isRunning = false;
      }

      if ( socket != null ) {
	// for now, use single-threaded model and process the request in the server thread
	handler.processRequest(socket);
      }
    }

    Log.debug(this, "run() existing..");
  }

  /**
   * Returns textual description.<P>
   */
  public String toString() {
    return info;
  }
}










