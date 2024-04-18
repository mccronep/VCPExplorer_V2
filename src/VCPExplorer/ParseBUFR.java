package VCPExplorer;


import java.*;
import java.util.*;
import java.util.regex.*;
import java.io.*;

public class ParseBUFR extends ParseSounding
{

//  private File bFile;
  private String headder;
//  private File sndgFile;
  private Vector soundingData;
  private Vector orderedKeys;
  private Hashtable sections;

  public ParseBUFR(String sndgFile) throws IOException
  { 
    File bFile = new File(sndgFile);
    new ParseBUFR(bFile);
  }
/*
  public ParseBUFR(File sndgFile) throws IOException
  {
    File bFile = sndgFile;
    new ParseBUFR();
  }
*/
  public ParseBUFR(File bFile) throws IOException
  {
    sections = new Hashtable(30);
    BufferedReader bFileReader = new BufferedReader(new FileReader(bFile));
    readFile(bFileReader);

  }


  public void readFile(BufferedReader br) throws IOException
  {

    String thisLine = new String();
    String thisLineMinus1 = new String();
    String thisLineMinus2 = new String();
    Vector stationHeadder = new Vector();
    String stid = new String();
    String dateTime = new String();
    String date = new String();
    String time = new String();
    soundingData = new Vector();
    orderedKeys = new Vector();
    boolean rec = false;

    while (br.ready())
    {
      /**
       * For now assuming that the format - and params - are static
       */

      thisLine = br.readLine();

      String[] thisLineFields = thisLine.split(" ");
      String[] thisLineMinus1Fields = thisLineMinus1.split(" ");
      String[] thisLineMinus2Fields = thisLineMinus2.split(" ");
      String[] params = new String[4]; // 1-PRES, 2-T, 3-Td, 4-Ht


      /**
       * If we find this pattern, then we are at the end of the 
       * basic sounding data and we can break out of the loop
       */
      if (
          thisLineFields[0].equalsIgnoreCase("STN") &&
          (isANumber(thisLineMinus1Fields[0]) || 
           thisLineMinus1Fields[0].length() <= 1)
         )
      { break; }

      /** Get station headder info, 
       *  this is run each time there is a headder 
       */
      if (thisLineFields[0].equalsIgnoreCase("STID"))
      { 
        String[] headderString = thisLine.split(" ");
        stationHeadder.add(headderString);
      }

      /** parse station headder info and store */
      if (!stationHeadder.isEmpty())
      {

        /** 
         * If we get a new headder, then we must be starting a new
         * section.  If we are starting a new section and soundingData
         * is empty, then this must be the first section.  Otherwise
         * if we are starting a new section and soundingData isn't empty
         * we want to pair the headder with the sounding data, then empty
         * the soundingData vector to begin a new section
         */
        if ( !soundingData.isEmpty() )
        {
          // ### since the keys of a hashtable don't retain their order
          orderedKeys.add(headder);
          sections.put(headder, soundingData);
          soundingData = new Vector();
        }

        stid = ((String[])stationHeadder.firstElement())[2];
        dateTime = ((String[])stationHeadder.firstElement())[8];
        String[] dtTemp = dateTime.split("/");
        date = dtTemp[0];
        time = dtTemp[1];

        headder = new String( stid + "," + date + "," + time);

          stationHeadder = new Vector();
      }

      /** 
       * Current format has params taking 2 lines with \n terminating
       * each line. E.g.,
       * PRES TMPC TMWC DWPC THTE DRCT SKNT OMEG
       * CFRL HGHT
       * Get the pair of lines.
       */
      if ( thisLineFields.length < thisLineMinus1Fields.length
          && isANumber(thisLineFields[0])
          && isANumber(thisLineMinus1Fields[0]) )
      { rec = true; }

      /**
       * Current format starts the data after the "CFRL HGHT" line.
       * Check for thisLine to be numbers while thisLineMinus1Fields
       * is not a number.  This is the only place in the current format
       * that this occurs and is a good flag to tell us when to parse
       * and record the actual sounding data
       */
      if ( isANumber(thisLineFields[0]) && 
           !isANumber(thisLineMinus1Fields[0]) )
      { rec = true; }

      if (rec && isANumber(thisLineFields[0]) 
          && isANumber(thisLineMinus1Fields[0]) )
      {
        /** 
         * Current format: 
         * PRES;TMPC;TMWC;DWPC;THTE;DRCT;SKNT;OMEG;
         * CFRL;HGHT 
         */
        params[0] = thisLineMinus1Fields[0];
        params[1] = thisLineMinus1Fields[1];
        params[2] = thisLineMinus1Fields[3];
        params[3] = thisLineFields[1];
        soundingData.add(params);

        /** 
         * Remember that we want two lines of data at a time, so we need
         * to "reset" the rec flag and use the if statement above
         */
        rec = false;
      }

      /** Shift the lines */
      thisLineMinus2 = thisLineMinus1;
      thisLineMinus1 = thisLine;
    }


    /** This catches the last section.  Based on the alg we're using
     *  inside the while loop, the last section will not be added to 
     *  the hashtable, so we must do it here.
     */
    if ( !soundingData.isEmpty() )
    {
      // ### since the keys of a hashtable don't retain their order
      orderedKeys.add(headder);
      sections.put(headder, soundingData);
    }
  }

  public boolean isANumber(String field)
  {
    boolean number = false;
    double temp = 0;

    try
    {
      // ### If this executes, it means that "field" is a number
      // ### If it fails, we will catch it
      temp = Double.valueOf(field).doubleValue();
      number = true;
    }
    catch (java.lang.NumberFormatException e)
    {
      number = false;
    }

    return number;
  }


  /** 
   * Given the "key" (as a String), get the sounding data for that
   * period.
   */
  public Vector getSoundingData(String head)
  {
    // ### Should check for proper headder format

    Vector vals = (Vector)sections.get(head);
/*
    for (Enumeration e = vals.elements(); e.hasMoreElements(); )
    {
      System.out.println( ((String[])e.nextElement())[1] );
    }
*/
    return vals;
  }

  public Vector getHeadderVect()
  { return orderedKeys; }

/*
  public static void main(String[] args) throws IOException
  {
    ParseBUFR sndgFile = new ParseBUFR(args[0]);
  }
*/
}

