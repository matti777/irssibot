/*
 * $Id: ConnectionHandler.java,v 1.1.1.1 2002/11/08 10:51:59 dreami Exp $
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

import java.util.StringTokenizer;
import java.net.Socket;
import java.io.*;

/**
 * Connection handler for the HTTP server.<P>
 *
 * @author Matti Dahlbom 
 * @version $Name:  $ $Revision: 1.1.1.1 $
 */
class ConnectionHandler {
  /**
   * Inbound buffer size.
   */
  private static final int INBOUND_BUFFER_SIZE = 4096;
  /**
   * Inbound buffer
   */
  private byte buffer[] = null;

  /**
   * Constructs.<P>
   *
   */
  public ConnectionHandler() {
    buffer = new byte[INBOUND_BUFFER_SIZE];
  }

  /**
   * Closes streams and socket.<P>
   *
   * @param in input stream
   * @param out output stream
   * @param socket socket
   */
  private void closeSafely(InputStream in, OutputStream out, Socket socket) {
    if ( in != null ) {
      try {
	in.close();
      } catch ( IOException e ) {
	// do nothing
      }
    }

    if ( out != null ) {
      try {
	out.close();
      } catch ( IOException e ) {
	// do nothing
      }
    }

    if ( socket != null ) {
      try {
	socket.close();
      } catch ( IOException e ) {
	// do nothing
      }
    }
  }

  /**
   * Writes the given bytes to the connection as a HTTP response.<P>
   *
   * @param data HTTP response data
   */
  private void sendResponse(OutputStream out, byte data[]) {
    if ( out == null ) {
      Log.error(this, "sendResponse(): out == null!");
      return;
    }
    
    Log.debug(this, "sendResponse(): writing " + data.length + " bytes");

    try {
      out.write(data, 0, data.length);
      out.flush();
    } catch ( IOException e ) {
      Log.log(this, e);
      close();
    }
  }

  /**
   * Creates a HTTP response.<P>
   *
   * @param response response code, for example "200 OK"
   * @param message message to use as body
   */
  private String createResponse(String response, String message) {
    String body = message;

    StringBuffer sb = new StringBuffer(4096);
    sb.append("HTTP/1.1 " + response + "\n");
    sb.append("Connection: Close\n");
    sb.append("Content-Type: text/html\n");
    sb.append("Content-Length: " + body.length() + "\n\n");
    sb.append(body);

    return sb.toString();
  }

  /**
   * Reads the whole request into the inbound buffer.<P>
   *
   * @param in input stream to read
   * @return request as a string
   */
  private String readAll() {
    int count = 0;
    int curbyte = 0;
    int numTimeouts = 0;

    while ( curbyte != -1 ) {
      try {
	curbyte = in.read();
      } catch ( InterruptedIOException e ) {
	if ( (numTimeouts++ > 20) || (count > 0) ) {
	  if ( count == null ) {
	    return null;
	  } else {
	    return new String(buffer, 0, count);
	  }
	}
      } catch ( IOException e ) {
	Log.log(this, e);
	return null;
      }

      if ( curbyte != -1 ) {
	if ( count >= INBOUND_BUFFER_SIZE ) {
	  return null;
	}
	buffer[count++] = (byte)curbyte;
      }
    }

    return null;
  }

  /**
   * Processes an inbound request.<P>
   * 
   * @param socket socket for the request
   */
  public void processRequest(Socket socket) {
    InputStream in = null;
    OutputStream out = null;

    // attempt to create streams to the socket
    try {
      in = new BufferedInputStream(socket.getInputStream());
      out = new BufferedOutputStream(socket.getOutputStream());
    } catch ( IOException e ) {
      Log.log(this, e);
      closeSafely(in, out, socket);
    }

    String request = readAll(in);

    if ( request == null ) {
      sendResponse(out, createResponse("400 Bad Request", "Bad Request"));
    }

    StringTokenizer st = new StringTokenizer(request, "\n");
    while ( st.hasMoreTokens() ) {
      String token = st.nextToken();
      
      System.err.println(token);
    }
  }
}










