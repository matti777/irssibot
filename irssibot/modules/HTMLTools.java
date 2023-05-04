/*
 * IrssiBot - An advanced IRC automation ("bot")
 * Copyright (C) 2000-2007 Matti Dahlbom
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

import irssibot.util.log.Log;
import irssibot.protocol.*;
import irssibot.core.*;
import irssibot.user.*;
import irssibot.util.StringUtil;

import java.util.*;
import java.net.*;
import java.io.*;
import java.util.regex.*;

  // full command set:
  // - !saa
  // - !list saa
  // - !lampo
  // - !wo 
  // - !w
  // - !hex
  // - !stock
  // - !tv
  // - !nday
  // - !tld

/**
 * This module has basic functionality for getting and parsing 
 * the content of pre-defined URL:s.<P>
 *
 * @author Jussi Ropo
 * @author Matti Dahlbom
 * @version $Name:  $, $Revision: 1.1.1.1 $
 */
public class HTMLTools extends AbstractModule {
    /* statics */
    private static String moduleInfo = 
      "HTML Tools $Revision: 1.1.1.1 $ for IrssiBot";
    
  /**
   * Property name for tinyurlization threshold.
   */
  private static final String PROPERTY_TINYURL_THRESHOLD = "tinyurl.threshold";
  /**
   * Threshold value for tinyurlization as number of characters
   * in an URL.
   */
  private int tinyurlThreshold = 0;

  // per-request temp data 
  private Host host = null;
  private String source = null;
  private ServerConnection caller = null;
  
  private HashMap languageKey = new HashMap();
  private HashMap stockKey = new HashMap();

  /**
   * Indicates whether the state of the module has changed.
   */
  private boolean changed = false;

  /**
   * Returns module properties.<P>
   *
   * @return state of module as a Properties object
   */
  public Properties getModuleState() {
    if ( !changed ) {
      return null;
    } else {
      Properties p = new Properties();
      p.setProperty(PROPERTY_TINYURL_THRESHOLD, 
		    String.valueOf(tinyurlThreshold));

      changed = false;

      return p;
    }
  }

  /**
   * Called upon loading the module.<P>
   *
   * @param state the initial state of module as an Properties object, or
   * null if no state was saved for module.
   * @param core a Core instance. this can be used to initialize module if 
   * no state was retrieved.
   * @return true if ok, false if error. modules returning false from onLoad()
   *  will be unloaded immediately.
   * @see irssibot.core.Core
   */
  public boolean onLoad(Properties state, Core core) {
    languageKey.put("ita","Italian");
    languageKey.put("spa","Spanish");
    languageKey.put("fre","French");
    
    //##TODO## remove after 1.0.8 released
    if ( state == null ) {
      return true;
    }

    String s = state.getProperty(PROPERTY_TINYURL_THRESHOLD);
    if ( s != null ) {
      try {
	tinyurlThreshold = Integer.parseInt(s);
      } catch ( NumberFormatException e ) {
	Log.info(this, "onLoad(): bad value for property " + 
		 PROPERTY_TINYURL_THRESHOLD + ": " + s + ", using 0");
      }
    }

    return true;
  }

    /**
     * Returns a module info string 
     *
     * @return module info string
     */
    public String getModuleInfo(){
	return moduleInfo;
    }

    /**
     * Default constructor
     *
     */
    public HTMLTools() {
	super(HTMLTools.class.getName());
    }

    /**
     * class representing weather data fetched from tiehallinto...
     */
    class WeatherItem {

	/*
	  String[0] = time;
	  String[1] = airTemp;
	  String[2] = roadTemp;
	  String[3] = rain;
	  String[4] = roadCond;
	*/
	private String[] data;
	private String location;
	
	private WeatherItem(String location, String[] data){
	    
	    this.location = location;
	    this.data = data;

	    //System.out.println("new WeatherItem = "+toString());

	    for(int i=0;i<data.length;i++){		
		if(data[i].indexOf("&nbsp") >= 0){
		    data[i] = "";
		} 
	    }
	}
	
	private String getLocation(){
	    return location;
	}
	
	private String[] getData(){
	    return data;
	}
	
	public String toString(){
	    
	    String ret = location+" @ "+data[0]+
		" : ilma "+data[1]+
		", tie "+data[2]+", ";
	    
	    if(!data[3].equals("Pouta")) {
		ret += " sade: "+data[3];
	    } else {
		ret += " Poutaa";
	    }
	    
	    ret += ", keli: "+data[4];
	    return ret;
	}
    }
    
    private Vector fetchWeather(String arg, int count, boolean all) throws IOException, ArrayIndexOutOfBoundsException {
	
	String location = "";
	Vector weather = new Vector();

	BufferedReader in = URLReader("http://www.tiehallinto.fi/alk/tiesaa/tiesaa_kokomaa.html");
	    
	if(in!=null){
		
	    String line;
	    int k = 0;

	    while( ((line = in.readLine()) !=null) ){
		//System.out.println("read line in while = "+line);
		if(!line.equals("null")) {
		    int idx = line.indexOf("Tie ");
		    if(idx>0){
			location = (line.substring(idx, line.indexOf("</FONT>",idx))).trim();

			StringTokenizer st = new StringTokenizer(location, ",");
			String s = "";

			int tokenCount = st.countTokens();
			if( tokenCount == 2){
			    s = st.nextToken();
			} else if (tokenCount == 1){
			    s = location;
			} else {
			    System.out.println("error while parsing...");
			    break;
			}
			
			int idxStart = 0;
			int idxEnd = 0;
			String[] data = {"","","","",""};

			for(int i=0;i<5;i++) {
			    line = in.readLine();			    
			    //System.out.println("read line in for = "+line);
			    idxStart = line.indexOf("SIZE=2>");
			    
			    //location contains no weather data, break
			    if(idxStart == -1){
				break;
			    }
			    idxEnd = line.indexOf("</FONT>",idxStart);
			    data[i] = (line.substring(idxStart+7,idxEnd)).trim();
			}

			if( all || ((s.toLowerCase()).indexOf(arg.toLowerCase()) != -1) ){
			    WeatherItem wi = new WeatherItem(location, data);
			    weather.add(wi);
			    //System.out.println("added: "+wi.toString());
			    if(weather.size() > 0){
				k++;
			    }
			    if(!all && (k==count)){
				break;
			    }
			}
		    }
		}
	    }
	}
	in.close();
	/*
	System.out.println("############# debug");
	for(int i=0; i<weather.size();i++){
	    System.out.println("wi # "+i+" : "+((WeatherItem)weather.elementAt(i)).toString());
	}
	System.out.println("############# debug end");
	*/

	return weather;
    }
    
  private void commandIxo(Host host, User invoker, 
                          String args[], Channel channel) {
    Log.debug(this, "commandIxo()");

    BufferedReader in = 
      URLReader("http://www.kauppalehti.fi/5/i/porssi/porssikurssit/" + 
                "osake/index.jsp?klid=1197");

    String pattern1 = "<span class=\"stock_number\">(.+?)</span>.*";
    String pattern2 = 
      "<span class=\"stock_number\">.*</span>.*" + 
      "<span class=\".*\">(.+)%</span>";
    String pattern3 = "<span class=\"time\">(.+)</span>";

    Pattern p1 = 
      Pattern.compile(pattern1);
    Pattern p2 = 
      Pattern.compile(pattern2);
    Pattern p3 = 
      Pattern.compile(pattern3);

    String rate = null;
    String time = null;
    String change = null;
    
    try {
      String line = in.readLine();

      while ( (line != null) && (change == null) ) {
        line = line.trim();
  
        Matcher m1 = p1.matcher(line);
        while ( m1.find() ) {
          if ( rate == null ) {
            rate = m1.group(1);
            break;
          }
        }

        Matcher m2 = p2.matcher(line);
        while ( m2.find() ) {
          if ( change == null ) {
            change = m2.group(1);
            break;
          }
        }

        Matcher m3 = p3.matcher(line);
        while ( m3.find() ) {
          if ( time == null ) {
            time = m3.group(1);
            break;
          }
        }

        line = in.readLine();
      }
    } catch ( IOException e ) {
      write("Failed: " + e.getMessage());
	    System.out.println("Error fetching !ixo: " + e.getMessage());
      return;
    }

    if ( change == null ) {
      change = "0";
    }

    Log.debug(this, "commandIxo(): rate=" + rate + ", change=" + 
              change + ", time=" + time);

    String output = String.format("Ixonos @ %s: %s (%s%%)", 
                                  time, rate, change.trim());
    write(output);
  }
    
    /**
     * Retrieves and parses weather information
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments, first argument determines location,
     *             second how many matches are printed (if 0, all)
     * @param channel Channel where command takes place
     */
	private void commandSaa(Host host,User invoker,String args[],Channel channel) {

	int count = 3;

	if(args == null){
	    args = new String[1];
	    args[0] = "Kerava";
	} else if(args.length == 2){
	    count = Integer.parseInt(args[1]);
	    if(count > 3){
		count = 3;
	    }
	}

	try {

	    Vector weather = fetchWeather(args[0], count, false);

	    for(int i=0; i<weather.size();i++){
		WeatherItem wi = (WeatherItem)weather.elementAt(i);
		write(wi.toString());
	    }

	} catch (IOException e) {
	    System.out.println(getClass().getName()+" .commandSaa(): "+
			       "error while retrieving page content, "+e);
	} catch (StringIndexOutOfBoundsException e) {
	    System.out.println(getClass().getName()+" .commandSaa(): "+
			       "error while retrieving page content, "+e);
	}
    }
    
   /**
     * Displays highest/lowest temperature and
     * location
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandTempLimit(Host host,User invoker,String args[],Channel channel) {

	double maxTemp = -100;
	double minTemp = 100;
	double curTemp = 0;
	String maxLoc = "";
	String minLoc = "";
	String rep = "";
	
	try {

	    Vector weather = fetchWeather(null, 0, true);
	    Enumeration e = weather.elements();
	    while(e.hasMoreElements()){

		WeatherItem wi = (WeatherItem)e.nextElement();

		double tmp = 0;
		try{
		    tmp = Double.parseDouble(wi.getData()[1].trim());
		} catch (NumberFormatException ex){
		    continue;
		}

		if (maxTemp<tmp) {
		    maxTemp = tmp;
		    maxLoc = wi.getLocation();
		}
		if (minTemp>tmp) {
		    minTemp = tmp;
		    minLoc = wi.getLocation();
		}
		
	    }

	    if(!maxLoc.equals("") && !minLoc.equals("") ) {
		write("Max: "+maxLoc+", "+maxTemp+" astetta, Min: "+minLoc+", "+minTemp+" astetta");
	    }

	} catch (IOException e) {
	    System.out.println(getClass().getName()+".commandTempLimit():\n"+
			       "error while retrieving page content\n"+e);
	} catch (StringIndexOutOfBoundsException e) {
	    System.out.println(getClass().getName()+".commandTempLimit(): "+
			       "error while prosessing page content\n"+e);
	}
    }
    
    /**
     * Retrieves and parses stock exchange information
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandStock(Host host,User invoker,String args[],Channel channel) {

	String[] data = new String[8];
	String header = "";
	boolean found = false;
	String updated = "";

	if(args==null){
	    args = new String[1];
	    args[0] = "ELIAV";
	}

	args[0] = args[0].toUpperCase();

	try {

	    BufferedReader in = URLReader("http://fi.soneraplaza.net/rahapuu/paaomamarkkinat/porssikurssit/");		

	    if(in!=null){
		String line;
	    
		while( (line = in.readLine()) !=null) {
		    if(!line.equals("null")) {
			
			if (line.indexOf("P‰ivitetty :")>0) {
			    int i = line.indexOf("P‰ivitetty")+20;
			    updated = (line.substring(i,line.indexOf("</i>",i))).trim();
			}
			
			int idx = line.indexOf(args[0]);
			if(idx>0){
			    int idxStart = 0;
			    int idxEnd = 0;
			    for(int i=0;i<6;i++) {
				line = in.readLine();
				idxStart = line.indexOf("size=\"1\">");
				idxEnd = line.indexOf("</font>",idxStart);
				data[i] = (line.substring(idxStart+9,idxEnd)).trim();
			    }
			    line = in.readLine();
			    idxStart = line.indexOf("%")-6;
			    idxEnd = line.indexOf("</font",idxStart);
			    data[6] = (line.substring(idxStart,idxEnd)).trim();
			    line = in.readLine();
			    idxStart = line.indexOf("size=\"1\">")+9;
			    idxEnd = line.indexOf("</font",idxStart);
			    data[7] = (line.substring(idxStart,idxEnd)).trim();
			    found = true;
			    break;
			}
		    }
		}
		in.close();
		
		if ( found ) {
		    write(updated+": "+
			  data[0]+", osto "+data[1]+
			  ", myynti "+data[2]+
			  ", min "   +data[3]+
			  ", max "   +data[4]+
			  ", viim "  +data[5]+
			  ", muut "  +data[6]+
			  ", vaih "  +data[7]);
		}
		
	    }
	} catch (IOException e) {
	    System.out.println(getClass().getName()+".commandStock(): "+
			       "error while retrieving page content, "+e);
	} catch (StringIndexOutOfBoundsException e) {
	    System.out.println(getClass().getName()+".commandStock(): "+
			       "error while retrieving page content, "+e);
	}
    }


    /**
     * Retrieves and parses stock exchange information
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandListStock(Host host,User invoker,String args[],Channel channel) {

	HashMap stock = new HashMap();
	String header = "";
	boolean found = false;

	try {

	    BufferedReader in = URLReader("http://fi.soneraplaza.net/rahapuu/paaomamarkkinat/porssikurssit/");		
	    String line;

	    if(in!=null){
	    
		while( (line = in.readLine()) !=null) {
		    
		    if(!line.equals("null")) {
			
			if (line.indexOf("Pankit ja rahoitus")>0) {
			    //System.out.println(line);
			    in.readLine();
			    while ( (line = in.readLine()) !=null) {
				if(line.indexOf("size=\"1\">")>0){
				    //System.out.println(line);
				    int idxStart = 0;
				    int idxEnd = 0;
				    idxStart = line.indexOf("size=\"1\">");
				    idxEnd = line.indexOf("</font>",idxStart);
				    stock.put((line.substring(idxStart+9,idxEnd)).trim(),"");
				    for(int i=0;i<8;i++) {
					line = in.readLine();
				    }
				}
			    }
			}
		    }
		}
		in.close();
		
		StringBuffer sb = new StringBuffer();
		if(!stock.isEmpty()) {
		    Object[] key = new Object[stockKey.size()];
		    key = (stock.keySet()).toArray();
		    sb.append((String)key[0]);
		    if(key.length>1){
			int tmp = 0;
			for(int i=1;i<key.length;i++) {
			    sb.append(", "+(String)key[i]);
			    if (tmp==40) {
				write(sb.toString());
				sb = new StringBuffer();
				tmp = 0;
			    }
			    tmp++;
			}
		    }
		}
	    }
	} catch (IOException e) {
	    System.out.println(getClass().getName()+".commandStock(): "+
			       "error while retrieving page content, "+e);
	} catch (StringIndexOutOfBoundsException e) {
	    System.out.println(getClass().getName()+".commandStock(): "+
			       "error while prosessing page content,"+e);
	}
    }

  /**
   * Displays list of possible 
   * weather information locations
   *
   * @param host Host of invoker
   * @param invoker User object of invoker
   * @param args command arguments
   * @param channel Channel where command takes place
   */
  private void commandTLD(Host host, User invoker, String args[], Channel channel) {
    String listURL = "http://www.ics.uci.edu/pub/websoft/wwwstat/country-codes.txt";
    BufferedReader in = URLReader(listURL);
    String line = null;

    if ( (args == null) || (args.length != 1) ) {
      return;
    }

    String tld = args[0].toUpperCase();
    

    if ( tld.startsWith(".") ) {
      if ( tld.length() > 1 ) {
	tld = tld.substring(1);
      } else {
	return;
      }
    }

    if ( in == null ) {
      write("Error accessing URL " + listURL);
      return;
    }

    do {
      try {
	line = in.readLine();
      } catch ( IOException e ) {
	write("Error reading URL " + listURL + ": " + e.getMessage());
	return;
      }

      if ( line != null ) {
	line = line.trim();
	
	if ( line.startsWith(tld) ) {
	  String location = line.substring(line.indexOf(' ')).trim();
	  write("Location for Top-level domain '" + tld + "' is " + location);
	  return;
	}
      }
    } while ( line != null );

    write("Location for  Top-level domain '" + tld + "' not found.");
  }
    
   /**
     * Displays list of possible 
     * weather information locations
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandListSaa(Host host,User invoker,String args[],Channel channel) {

	String tmp = "";
	try {

	    Vector weather = fetchWeather(null, 0, true);

	    Enumeration e = weather.elements();
	    while(e.hasMoreElements()){
		
		WeatherItem wi = (WeatherItem)e.nextElement();
		tmp+= wi.getLocation() +", ";
	    }

	    write("Weather information for the following locations:");
	    write(tmp);
	    write("");
	    write("Also try '!lampo'");

	} catch (IOException e) {
	    System.out.println(getClass().getName()+" .commandSaa(): "+
			       "error while retrieving page content, "+e);
	} catch (StringIndexOutOfBoundsException e) {
	    System.out.println(getClass().getName()+" .commandSaa(): "+
			       "error while retrieving page content, "+e);
	}
    }

    /**
     * Lists possible translantion languages and commands
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandListLang(Host host,User invoker,String args[],Channel channel) {

	String tmp = "";
        if(!languageKey.isEmpty()) {
	    Object[] key = new Object[languageKey.size()];
	    key = (languageKey.keySet()).toArray();
	    tmp+= (String)key[0];
	    if(key.length>1) {
		for(int i=1;i<key.length;i++) {
		    tmp+= ", "+(String)key[i];
		}
	    }
	}
	write("Translate en->fin->en with '!w <word>' or");
	write("en->"+tmp+" with '!wo <lang> <word>'");
    }


    /**
     * creates URLConnection for specified url
     *
     * @param address url as a String, "http://777-team.org/index.html"
     *
     */
    private URLConnection getUrlCon(String address) {

	URLConnection uc = null;

	try {
	    URL url = new URL(address);
	    uc = url.openConnection();
	} catch (MalformedURLException e) {
	    System.out.println(getClass().getName()+".getUrlCon(): "+
			       "error while retrieving page content"+e);
	} catch (IOException e) {
	    System.out.println(getClass().getName()+".getUrlCon(): "+
			       "error while retrieving page content"+e);
	}

	return uc;

    }

    /**
     * creates BufferedReader for specified url
     *
     * @param address url as a String, "http://777-team.org/index.html"
     *
     */
    private BufferedReader URLReader (String address) {

	BufferedReader in = null;
	try {
	    URL url = new URL(address);
	    URLConnection uc = url.openConnection();
	    in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
	} catch (MalformedURLException e) {
	    System.out.println(getClass().getName()+".getUrlCon(): "+
			       "error while retrieving page content"+e);
    } catch (IOException e) {
	System.out.println(getClass().getName()+".getUrlCon(): "+
			   "error while retrieving page content"+e);
    }
	return in;
    }

    /**
     * translates words from english->other language.
     * possible languages are listed in HashMap languageKey
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandWordEnToOther(Host host,User invoker,String args[],Channel channel) {
	
	if( languageKey.containsKey(args[0]) && (args.length==2) ) {
	    try {
		
		StringBuffer sb = new StringBuffer();
		URLConnection uc = null;
		String param[] = new String[2];
		
		if (args.length==2) {
		    param[0] = args[0];
		    param[1] = args[1];
		}
		
		if (param.length==2) {
		    String lang = (String)languageKey.get(param[0]);
		    sb.append("search="+URLEncoder.encode(param[1]));
		    sb.append("&to="+URLEncoder.encode(lang));
		    sb.append("&from="+URLEncoder.encode("English"));
		    sb.append("&fname="+URLEncoder.encode("eng_"+param[0]+".txt"));
		    sb.append("&back="+URLEncoder.encode(param[0]+".html"));
		    sb.append("&exact="+URLEncoder.encode("on"));
		    sb.append("&max="+URLEncoder.encode("1"));
		    
		    if(param[0].equals("ita")) {
			uc = getUrlCon("http://www.screensaverhome.com/cgi-bin/onldicita.cgi");
		    } else if(param[0].equals("spa")) {
			uc = getUrlCon("http://www.histopia.nl/cgi-bin/onldicspa.cgi");
		    } else if (param[0].equals("fre")) {
			uc = getUrlCon("http://www.freewaresite.com/cgi-bin/onldicfre.cgi");			
		    }
		    
		}
	
		if(uc!=null){
		    //set properties
		    uc.setDoOutput(true);
		    uc.setUseCaches(false);
		    uc.setAllowUserInteraction(false);
		    uc.setRequestProperty("content-type","application/x-www-form-urlencoded");
		    
		    //send parameters
		    PrintWriter out = new PrintWriter(uc.getOutputStream());
		    out.print(sb.toString());
		    out.close();
		    
		    BufferedReader in = new BufferedReader(
					    new InputStreamReader(
						uc.getInputStream()));
		
		    sb = new StringBuffer();
		    String line;
		
		    if(in!=null){
			while ((line = in.readLine()) != null){
			    if(line.indexOf("bgcolor=\"#ffffff\">"+param[1]+"</td>")>0){
				line = in.readLine();
				if (param[0].equals("ita") || param[0].equals("spa") || param[0].equals("fre") ) {
				    int idx = line.indexOf("bgcolor=\"#ffffff\"><b>");
				    sb.append((line.substring(idx+21,line.indexOf("</b>",idx))).trim());
				}
			    }
			}
			in.close();

			if(sb.length()<1){
			    write(languageKey.get(param[0])+": no matching words found");
			} else {
			    write(languageKey.get(param[0])+": "+sb.toString());
			}
		    }
		}
	    } catch (IOException e) {
		System.out.println("Message from HTMLTools.commandWordEnToOther():\n"+
				   "error while retrieving page content\n"+
				   e.getMessage());
	    }
	}
    }

    /**
     * translates words from english->finnish->english
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandWordEnFi(Host host,User invoker,String args[],Channel channel) {

	if ( args.length>0 ) {
	    try {
	    

		URLConnection uc = null;
		
		if (args.length>0) {
		    uc = getUrlCon("http://efe.scape.net/trans.cgi?Keyword="+args[0]);		
		}

		if(uc!=null){
		    BufferedReader in = new BufferedReader(
				            new InputStreamReader(
					        uc.getInputStream()));

		    StringBuffer sb = new StringBuffer();		
		    String line;
		    boolean isFirst = true;
		
		    if(in!=null){
			while ((line = in.readLine()) != null){
			    if(line.indexOf("<table border=0")>-1){
				line = in.readLine();
				while(line.indexOf("<tr><td><li>")>-1) {
				    if (isFirst) {
					sb.append((line.substring(12,line.indexOf("</td>",12))).trim());
					isFirst = false;
				    } else {
					sb.append((", "+line.substring(12,line.indexOf("</td>",12))).trim());
				    }
				    line = in.readLine();
				}
			    }
			}
			in.close();
			
			if(sb.length()<1){
			    write("no matching words found");
			} else {
			    write("Matched: "+sb.toString());
			}
		    }
		}
	    } catch (IOException e) {
		System.out.println(getClass().getName()+".commandWordEnToOther(): "+
				   "error while retrieving page content "+e);
	    }
	}
    }

    /**
     * retrieves hex index
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandHex(Host host,User invoker,String args[],Channel channel) {

	try {
	    
	    BufferedReader in = URLReader("http://fi.soneraplaza.net/rahapuu/paaomamarkkinat/porssikurssit/");
	    
	    StringBuffer sb = new StringBuffer();		
	    String line;
	    int idx = -1;
	    
	    if(in!=null){

		while ((line = in.readLine()) != null){		    
		    idx = line.indexOf("Hex yleisindeksi");
		    
		    if(idx>-1){
			int idxFont = line.indexOf("<font", idx);
			sb.append((line.substring(idx,idxFont)).trim());
			idx = line.indexOf("\">",idxFont);
			idxFont = line.indexOf("</font",idx);
			sb.append(" "+(line.substring(idx+2,idxFont)).trim());
		    }
		}
		in.close();
	    }	    
	    if(sb.length()<1){
		write("error while executing...");
	    } else {
		write(sb.toString());
	    }
	} catch (IOException e) {
	    System.out.println(getClass().getName()+".commandHex(): "+
			       "error while retrieving page content, "+e);
	}
    }

    /**
     * retrieves namedays
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandNameDay(Host host,User invoker,String args[],Channel channel) {

	try {
	    
	    BufferedReader in = URLReader("http://aristoday.com/tanaan/");
	    
	    StringBuffer sb = new StringBuffer();		
	    String line;
	    int idx = -1;
	    
	    if(in!=null){
		while ((line = in.readLine()) != null){		    
		    idx = line.indexOf("Nimip‰iv‰ t‰n‰‰n");
		    
		    if(idx>1){
			idx = line.indexOf(":", idx)+1;
			sb.append("Nimip‰iv‰ t‰n‰‰n: ");
			sb.append((line.substring(idx,line.length())).trim());
		    }
		}
		in.close();
	    }
	    if(sb.length()<1){
		write("error while executing...");
	    } else {
		write(sb.toString());
	    }
	    
	} catch (IOException e) {
	    System.out.println(getClass().getName()+".commandNameDay(): "+
			       "error while retrieving page content, "+e);
	}
    }
    

    /**
     * retrieves current tv-programs
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandTv(Host host,User invoker,String args[],Channel channel) {

	try {
	    
	    BufferedReader in = URLReader("http://www.apu.fi/cgi/tv/");
	    
	    StringBuffer sb = new StringBuffer();		
	    String line;
	    int idx = -1;

	    String curProgram[] = { "","","","" };
	    String nextProgram[] = { "","","","" };

	    String time = "";
	    int chan = 0;
	    String prog = "";

	    int curCounter = 0;
	    int nextCounter = 0;

	    if(args==null){
		args = new String[1];
		args[0] = "now";
	    } else if (!args[0].equals("next")) {
		args[0] = "now";
	    }

	    if(in!=null){
		while ((line = in.readLine()) != null){		    
		    
		    idx = line.indexOf("<dt>");
		    
		    if(idx>-1){
			//System.out.println(line);
			
			idx = line.indexOf("<b>")+3;
			time = (line.substring(idx,idx+5)).trim();
			
			idx = line.indexOf("#006600",idx)+9;
			chan = (new Integer((line.substring(idx,idx+1)).trim())).intValue();
			
			idx = idx+8;
			prog = (line.substring(idx,line.length())).trim();
			
			
			if(prog.indexOf("font")>-1){
			    idx = prog.indexOf(">")+1;
			    prog = (prog.substring(idx,prog.indexOf("<"))).trim();
			}
			
			
			if(curCounter<4){
			    curProgram[chan-1] = "tv"+chan+" @ "+time+": "+prog;
			    curCounter++;
			} else {		
			    if(nextProgram[chan-1].length()==0) {
				nextProgram[chan-1] = "tv"+chan+" @ "+time+": "+prog;
				nextCounter++;
			    }
			}
		    }
		    if(nextCounter==4){
			break;
		    }
		}
		in.close();
	    }
	    if(args[0].equals("now")){
		
		for(int i=0;i<4;i++){
		    if(i==0){
			write("Nyt: ");
		    }
		    
		    if(curProgram[i].length()>0){
			write(curProgram[i]);
		    } else if (curProgram[i].indexOf("<b>K</b>")>0) {
			write("error while fetching program info for channel "+(i+1));
		    } else {
			write("error while fetching program info for channel "+(i+1));
		    }		    
		}
	    } else if (args[0].equals("next")){
		
		for(int i=0;i<4;i++){
		    if(i==0){
			write("Seuraavaksi: ");
		    }
		    
		    if(nextProgram[i].length()>0){
			write(nextProgram[i]);
		    } else if (nextProgram[i].indexOf("<b>K</b>")>0) {
			write("error while fetching program info for channel "+(i+1));
		    } else {
			write("error while fetching program info for channel "+(i+1));
		    }
		}
	    }
	    
	} catch (IOException e) {
	    System.out.println(getClass().getName()+".commandTv(): "+
			       "error while retrieving page content, "+e);
	}
    }

    /**
     * Lists possible tv commands
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandListTv(Host host,User invoker,String args[],Channel channel) {

	write("Current program '!tv [now]'");
	write("Next    program '!tv next'");

    }

    /**
     * Lists possible nameday commands
     *
     * @param host Host of invoker
     * @param invoker User object of invoker
     * @param args command arguments
     * @param channel Channel where command takes place
     */
    private void commandListNameDay(Host host,User invoker,String args[],Channel channel) {
	write("Nameday today '!nday'");
    }

  /**
   * Processes list-commands for this module.<P>
   *
   * @param host Host of invoker
   * @param invoker User object of invoker
   * @param args command arguments
   * @param channel Channel where command takes place
   */
  private void commandList(Host host,User invoker,String args[],
			   Channel channel) {
    if (args[0].equals("saa")) {
      commandListSaa(host,invoker,args,channel);
    } else if (args[0].equals("lang")) {
      commandListLang(host,invoker,args,channel);
    } else if (args[0].equals("stock")) {
      commandListStock(host,invoker,args,channel);
    } else if (args[0].equals("tv")) {
      commandListTv(host,invoker,args,channel);
    } else if (args[0].equals("nday")) {
      commandListNameDay(host,invoker,args,channel);
    } else if (args[0].equals("tinyurl")) {
      if ( invoker.isOp(channel.getChannelName()) ||
	   invoker.isChanAdmin(channel.getChannelName()) ||
	   invoker.isGlobalAdmin() ) {
	if ( tinyurlThreshold == 0 ) {
	  write("tinyurl-threshold=0 (disabled)");
	} else {
	  write("tinyurl-threshold=" + tinyurlThreshold + 
		" (0 to disable)");
	}
      }
    }
  }

  /**
   * Processes edit-commands for this module.<P>
   *
   * @param host Host of invoker
   * @param invoker User object of invoker
   * @param args command arguments
   * @param channel Channel where command takes place
   */
  private void commandEdit(Host host, User invoker, String args[],
			   Channel channel) {
    if (args[0].equals("tinyurl")) {
      if ( invoker.isOp(channel.getChannelName()) ||
	   invoker.isChanAdmin(channel.getChannelName()) ||
	   invoker.isGlobalAdmin() ) {
	if ( args.length == 2 ) {
	  try {
	    tinyurlThreshold = Integer.parseInt(args[1]);
	  } catch ( NumberFormatException e ) {
	    write("Bad value for tinyurl-threshold: " + args[1]);
	    return;
	  }

	  if ( tinyurlThreshold < 0 ) {
	    tinyurlThreshold = 0;
	  }
	  
	  write("Set tinyurl-threshold to " + tinyurlThreshold);
	  changed = true;
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
    private void processCmdMsg(Host host, String cmd, Channel channel,
			       String args[]) {
	
	User user = caller.findUser(host);

	//System.out.println("cmd="+cmd);

	/* all commands require user in bot */
	if( (user != null) && channel.isJoined() ) {
	    if( cmd.equals("saa") ) {
		commandSaa(host,user,args,channel);
	    } else if ( cmd.equals("list") ) {
		commandList(host,user,args,channel);
	    } else if ( cmd.equals("edit") ) {
		commandEdit(host,user,args,channel);
	    } else if ( cmd.equals("lampo") ) {
		commandTempLimit(host,user,args,channel);
	    } else if ( cmd.equals("wo") ) {
		commandWordEnToOther(host,user,args,channel);
	    } else if ( cmd.equals("w") ) {
		commandWordEnFi(host,user,args,channel);
	    } else if ( cmd.equals("hex") ) {
		commandHex(host,user,args,channel);
	    } else if ( cmd.equals("stock") ) {
		commandStock(host,user,args,channel);
	    } else if ( cmd.equals("tv") ) {
		commandTv(host,user,args,channel);
	    } else if ( cmd.equals("nday") ) {
		commandNameDay(host,user,args,channel);
	    } else if ( cmd.equals("tld") ) {
        commandTLD(host, user, args, channel);
	    } else if ( cmd.equals("ixo") ) {
        commandIxo(host, user, args, channel);
      }
	    
	}
    }

  /**
   * Fetches a tinyurl (tinyurl.com) version for a long http-URL.<P>
   */
  private void doTinyurl(Host host, Channel channel, String msg) {
    // look for an url in the line
    int index1 = msg.indexOf("http://");
    if ( index1 == -1 ) {
      index1 = msg.indexOf("https://");
      if ( index1 == -1 ) {
	return;
      }
    }

    int index2 = msg.indexOf(" ", index1);
    String oldURL = null;

    if ( index2 == -1 ) {
      // check url length; must exceed threshold
      if ( (msg.length() - index1) < tinyurlThreshold ) {
	return;
      }

      // the line ends with the url
      oldURL = msg.substring(index1);
    } else {
      // check url length; must exceed threshold
      if ( (index2 - index1) < tinyurlThreshold ) {
	return;
      }

      // there is still something after the url; skip it
      oldURL = msg.substring(index1, index2);
    }

    // disregard any tinyurl.com -urls
    if ( oldURL.toLowerCase().startsWith("http://tinyurl.com") ) {
      return;
    }

    try {
      URL url = new URL("http://tinyurl.com/create.php");
      HttpURLConnection con = (HttpURLConnection)url.openConnection();
      con.setRequestMethod("POST");
      con.setRequestProperty("Content-Type", 
			     "application/x-www-form-urlencoded");
      con.setDoOutput(true);
      con.connect();
      OutputStream out = con.getOutputStream();
      String param = "url=" + URLEncoder.encode(oldURL, "ISO-8859-1");
      out.write(param.getBytes());
      out.close();
      
      InputStream in = con.getInputStream();

      // read all of the reply in the buffer
      byte buf[] = new byte[4 * 4096];
      int cur = 0;
      int num = 0;

      while ( cur != -1 ) {
	cur = in.read();

	if ( cur != -1 ) {
	  buf[num++] = (byte)cur;
	}
      }
      in.close();

      //      Log.debug(this, "doTinyurl(): " + new String(buf, 0, num));
      String s = new String(buf, 0, num);
      StringTokenizer st = new StringTokenizer(s, "\n");
      while ( st.hasMoreTokens() ) {
	String line = st.nextToken().trim();
	if ( line.startsWith("<blockquote><b>http://tinyurl.com/") ) {
	  index1 = line.indexOf("http://");
	  index2 = line.indexOf("</b><br>");
	  
	  if ( (index1 != -1) && (index2 != -1) ) {
	    String newURL = line.substring(index1, index2);

	    // display the fetched tinyurl
	    write(host.getNick() + "'s URL tinyurled: " + newURL + "");
	  }

	  break;
	}
      }

      con.disconnect();
    } catch ( Exception e ) {
      // nicely handled, yeah..
      Log.info(this, "doTinyurl(): caught " + e.getClass().getName() + 
	       ": " + e.getMessage());
    }
  }

    /**
     * Processes incoming IrcMessages from a ServerConnection
     *
     * @param message IrcMessage to process
     * @param serverConnection invoking ServerConnection
     */
    protected void processMessage(IrcMessage message, 
				  ServerConnection serverConnection) {

	//System.out.println("HTMLTools.IrcMessage:"+message.toString());
	String trailing = message.getTrailing();

	try{
	    if(trailing!=null){
		if(trailing.length()>1){
		    this.caller = serverConnection;
		    
		    if( message.getCommand().equals("PRIVMSG")) {
			doPrivmsg(message);
		    }
		    
		    /* set per-request vars to null */
		    this.caller = null;
		    this.source = null;
		}
	    }
	} catch (Exception e){
	    Log.log(this, e);
	}
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
	    } else {
	      if ( tinyurlThreshold > 0 ) {
		doTinyurl(host, channel, trailing);
	      }
	    }
	}
	
	if( (channel != null) && (cmd != null) ) {
	    processCmdMsg(host, cmd, channel, args);
	}
    }
}













