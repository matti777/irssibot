/*
 * $Id: DatabaseDescriptor.java,v 1.1 2002/11/08 11:14:31 dreami Exp $
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

import java.sql.Driver;

/**
 * Describes a configured JDBC database.<P>
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.1 $
 */
public class DatabaseDescriptor {
  /**
   * Name for this descriptor.
   */
  private String name = null;
  /**
   * JDBC driver.
   */
  private Driver jdbcDriver = null;
  /**
   * JDBC URL
   */
  private String jdbcURL = null;

  /**
   * Constructs.<P>
   *
   * @param name name for this descriptor
   * @param jdbcDriverName JDBC driver class name
   * @param jdbcURL JDBC URL
   */
  public DatabaseDescriptor(String name, String jdbcDriverName, String jdbcURL) {
    this.name = name;
    this.jdbcURL = jdbcURL;

    // instantiate driver
    try {
      Class c = Class.forName(jdbcDriverName);
      jdbcDriver = (Driver)c.newInstance();
    } catch ( Exception e ) {
      throw new IllegalArgumentException("Could not load database driver " + 
					 jdbcDriverName  + ": caught " + 
					 e.getClass().getName() + 
					 ", message: " + e.getMessage());
    }
  }

  /**
   * Returns name for this descriptor.<P>
   */
  public String getName() {
    return name;
  }

  /**
   * Returns JDBC driver.<P>
   */
  public Driver getJdbcDriver() {
    return jdbcDriver;
  }

  /**
   * Returns JDBC URL.<P>
   */
  public String getJdbcURL() {
    return jdbcURL;
  }
}
