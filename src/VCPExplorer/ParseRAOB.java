package VCPExplorer;


import java.*;
import java.io.*;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Date;
import java.util.Enumeration;
import java.util.Collections;
import java.util.Comparator;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

import java.rmi.RemoteException;

import visad.*;
import visad.data.netcdf.Plain;
import visad.data.visad.VisADForm;

import ucar.netcdf.*;


public class ParseRAOB extends ParseSounding
{

  private File file; 
  private Tuple netcdfData;
  private Hashtable stations;
  private Vector orderedKeys, soundingData;
  private int RECNUM;

  private RealType wmoType;
  private TextType staNameType;
  private FunctionType wmo_staName_func;

  public ParseRAOB(File f) throws IOException, VisADException, RemoteException
  {
    file = f.getAbsoluteFile();
    stations = new Hashtable();
    orderedKeys = new Vector();
    Plain plain = new Plain();
    netcdfData = (Tuple)plain.open(file.getAbsolutePath());

    wmoType = RealType.getRealType("wmoType");
    staNameType = TextType.getTextType("staNameType");
    wmo_staName_func = new FunctionType(wmoType, staNameType);

    stationInfo();

  }

  public void readFile(String keyHeadder) throws IOException, VisADException, RemoteException
  {

    soundingData = new Vector();
    FlatField manValsField = (FlatField)netcdfData.getComponent(6);


    FunctionType origFType = (FunctionType)manValsField.getType();
    RealTupleType domainTypes = origFType.getDomain();

    Vector stnInfo = new Vector();
    stnInfo = (Vector)stations.get(keyHeadder);


    if (stnInfo != null)
    {
      // ### Get the record number
      int recNumber = ((Integer)stnInfo.get(0)).intValue();
      // ### Get the wmo number
      int wmoNumber = ((Integer)stnInfo.get(1)).intValue();
      // ### get the mandatory level value
      int numManLevels = ((Integer)stnInfo.get(5)).intValue();

      for (int i = 0; i <= numManLevels; ++i)
      {
        RealTuple domainTuple = new RealTuple(domainTypes, 
                            new double[]{ i, recNumber });
  
        Tuple vals = (Tuple) manValsField.evaluate(domainTuple, Data.NEAREST_NEIGHBOR, Data.INDEPENDENT);
        double pres = ((Real)vals.getComponent(0)).getValue();
        double ht = ((Real)vals.getComponent(1)).getValue();
        double temp = ((Real)vals.getComponent(2)).getValue();
        temp = temp - 273.16;
        double dDep = ((Real)vals.getComponent(3)).getValue();
        double tDew = temp - dDep;
//        double[] params = new double[] { pres, temp, tDew, ht };
        String[] params = new String[] { Double.toString(pres), 
                                         Double.toString(temp), 
                                         Double.toString(tDew),
                                         Double.toString(ht) };

        soundingData.add(params);
/* 
System.out.println(
//                   recNumber +" {" + wmoNumber + "/" + keyHeadder + "}" + " " 
//                    + "(" + i + "/" + numManLevels + ") "
//                   + "[" + pres + "] " + temp + "/" + dDep
//                  + manValsField.evaluate(domainTuple)
                  "VALUES:: " + vals.getComponent(0) + ", "
                  +  vals.getComponent(1) + ", " + vals.getComponent(2) 
                  + ", " +  vals.getComponent(3)
                  );
*/
      }
    }

    else
    {
      String err0 = new String("ERROR> No data exist for: ");
      String err1 = keyHeadder;
      new ErrorFrame(new String[]{ err0, err1 });
    }

    Collections.sort(soundingData, new Comparator()
    {
      public int compare(Object o1, Object o2)
      {
        float db1 = Float.parseFloat( ((String[])o1)[0] );
        float db2 = Float.parseFloat( ((String[])o2)[0] );

        return ( Math.round(db2) - Math.round(db1) );
      }
    });


  }

  public void stationInfo() throws VisADException, RemoteException, IOException
  {
    FlatField stnFF = (FlatField)netcdfData.getComponent(2);
    RECNUM = stnFF.getDomainSet().getLength();

    NetcdfFile netcdfData1 = new NetcdfFile(file.getAbsolutePath(), true);
    Variable wmoNum = netcdfData1.get("wmoStaNum");
    Variable staName = netcdfData1.get("staName");

    // ### Link WMO station # to Station ID
    for (int i = 0; i < RECNUM; ++i)
    {
      String site = null;
      char[] sites = new char[4];
      for (int j = 0; j < 4; ++j)
      {
        sites[j] = staName.getChar(new int[]{i, j });
        site = new String(sites);
      }

      Tuple range = (Tuple)stnFF.evaluate(new Real(i));

      // ### Extract the sounding time and set format
      long secs = (long)((Real)range.getComponent(7)).getValue()*1000;
      Date date = new Date(secs);
      SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd,HHmm,z");
      sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
      String[] dateTime = sdf.format(date).split(",");
      String headderString = 
            new String(site + "," + dateTime[0] + "," + dateTime[1]);


      Vector stnInfoVect = new Vector();
      // ### record number
      stnInfoVect.add(new Integer(i));
      // ### wmo
      stnInfoVect.add( 
         new Integer((int)((Real)range.getComponent(3)).getValue()) );
      // ### stnLat
      stnInfoVect.add( 
         new Double(((Real)range.getComponent(4)).getValue()) );
      // ### stnLon
      stnInfoVect.add( 
         new Double(((Real)range.getComponent(5)).getValue()) );
      // ### stnAlt
      stnInfoVect.add( 
         new Double(((Real)range.getComponent(6)).getValue()) );
      // ### number of mandatory levels
      stnInfoVect.add( 
         new Integer((int)((Real)range.getComponent(8)).getValue()) );
      // ### number of sig. T levels
      stnInfoVect.add( 
         new Integer((int)((Real)range.getComponent(9)).getValue()) );
      // ### number of sig. W levels
      stnInfoVect.add( 
         new Integer((int)((Real)range.getComponent(10)).getValue()) );
      // ### number of max W levels
      stnInfoVect.add( 
         new Integer((int)((Real)range.getComponent(11)).getValue()) );

      // ### put in Hashtable with station name as key
      stations.put(headderString, stnInfoVect);
      // ### another vector to use as key to preserve order of orig. data
      orderedKeys.add(headderString);
    }
    
  }


  public Vector getHeadderVect()
  { return orderedKeys; }

  public Vector getSoundingData(String head)
  {
    // ### Should check for proper headder format
    try
    { readFile(head); }
    catch (Exception ex)
    { System.err.println("RAOB Snd> " + ex); }
/*
for (Enumeration e = soundingData.elements() ; e.hasMoreElements() ;)
{
  String[] line = (String[])e.nextElement();
  for (int i = 0; i < line.length; ++i)
  { System.out.print( line[i] + ","); }
  System.out.println();
}
System.out.println("-----------");
*/

    return soundingData;
  }
/*
  public static void main(String[] args) 
           throws IOException, VisADException, RemoteException
  {
    ParseRAOB pR = new ParseRAOB(new File("SoundingFiles/raob_oun-20040520_1200.netcdf"));
  }
*/
}


