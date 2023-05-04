/*
 * $Id: ModuleLoader.java,v 1.1 2002/11/08 11:14:31 dreami Exp $
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

import java.io.*;
import irssibot.util.log.Log;

/**
 * A custom ClassLoader to load and unload modules.
 *
 * @author Matti Dahlbom
 * @version $Name:  $ $Revision: 1.1 $
 */
class ModuleLoader extends ClassLoader
{
  /**
   * Component info string to be returned by toString()
   */
  private static String info = "ModuleLoader";
  /**
   * Module base dir.<P>
   */
  private String moduleBaseDir = null;

  /**
   * Constructs.<P>
   *
   * @param moduleBaseDir module base dir
   */
  public ModuleLoader(String moduleBaseDir) {
    this.moduleBaseDir = moduleBaseDir;
  }

  public String toString() { return info; }

  /**
   * Loads an IrssiBot module from disk.<P>
   *
   * @param name module class name to load
   * @return module class
   */
  public Class loadClass(String name) throws ClassNotFoundException {
    Class loadedClass = findLoadedClass(name);
    if ( loadedClass == null ) {
      try {
	loadedClass = findSystemClass(name);
      } catch ( Exception e ) {
	// do nothing
      }
	    
      if ( loadedClass == null ) {
	String fileName = moduleBaseDir + File.separator + 
	  name.replace('.', File.separatorChar) + ".class";
	byte data[] = null;

	Log.debug(this, "loadClass(): attempting to load module from file " + fileName);

	try {
	  data = loadClassData(fileName);
	} catch( IOException e ) {
	  Log.log(this, e);
	  throw new ClassNotFoundException(e.getMessage());
	}

	loadedClass = defineClass(name,data, 0, data.length);
	
	if ( loadedClass == null ) {
	  Log.error(this, "loadClass(): loadedClass == null!");
	  throw new ClassNotFoundException("Could not load " + name);
	} else {
	  resolveClass(loadedClass);
	}
      } else {
	// found loaded system class
      }
    } else {
      // found loaded class
    }
    
    return loadedClass;
  }

  /**
   * Loads binary class data from disk.<P>
   *
   * @param fileName file name
   * @return bytecodes
   */
  private byte[] loadClassData(String fileName) throws IOException {
    File file = new File(fileName);
    byte buffer[] = new byte[(int)file.length()];

    FileInputStream in = new FileInputStream(file);
    DataInputStream dataIn = new DataInputStream(in);
    
    dataIn.readFully(buffer);
    dataIn.close();
    
    return buffer;
  }
}












