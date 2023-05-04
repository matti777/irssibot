/*
 * $Id: LogException.java,v 1.2 2002/11/08 11:14:31 dreami Exp $
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
package irssibot.util.log;


/**
 * Common log exception for delivering error messages from <code>CommonLog</code>
 * objects to <code>Log</code> system.
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.2 $
 */
class LogException extends Exception {
    /**
     * The cause of this exception
     */
    private Throwable rootCause = null;

    /**
     * Constructs with message
     *
     * @param message error message
     */
    LogException(String message) {
	super(message);
    }

    /**
     * Constructs with root cause
     *
     * @param cause the Throwable that caused this LogException
     */
    LogException(Throwable cause) {
	this.rootCause = cause;
    }

    /**
     * Returns the root cause
     *
     * @return the Throwable that caused this LogException
     */
    Throwable getRootCause() {
	return rootCause;
    }
}
