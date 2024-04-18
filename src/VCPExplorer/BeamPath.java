
package VCPExplorer;

import java.*;
import java.io.*;
import java.util.Vector;
import java.util.Enumeration;
import visad.*;
import visad.bom.*;
import visad.data.units.*;
import java.rmi.RemoteException;


public class BeamPath
{

  final static double ZERO_K = 273.16;
  final static double INDEX_OF_REFRACTION = (4.0/3.0);
  final static double EARTH_RADIUS = 6378; // km at Equator
  final static double RADIANS = (Math.PI/180);
  static double MAX_RANGE;
  private RealType sRange, range, height;
  private TextType text;
  private RealTupleType rangeHeightType;
  private String currentVCP;
  private Unit kilometer, km;
  private UnionSet vcpBeamWidthSet, allBeamsFill;
  private Irregular2DSet beamFill;
  private double radarElevation, elevationAngle, enteredBeamWidth;
  private int numberOfElevationAnglesInCurrentVCP;

  private FlatField singlePath;
  private UnionSet singleWidth, nsWidth;
  private Irregular2DSet singleFill;
  private Tuple singleLabel;
  private Gridded2DSet nsPath;
  private FlatField[] vcpPaths;
  private UnionSet[] vcpWidths;
  private Irregular2DSet[] vcpFills;
  private Tuple[] vcpLabels;
  private Vector soundingData; // store the sounding data
  private Vector refSoundingData; // vertical refractivity profile
  private ParseSounding sounding;
  

  public BeamPath(double re, double maxRange) throws RemoteException, VisADException
  {

    MAX_RANGE = maxRange;  

    try
    { kilometer = Parser.parse("kilometer").clone("km"); }
    catch (ParseException P)
    { System.out.println("ParseException: " + P); }
    catch (UnitException U)
    { System.out.println("UnitException: " + U); }
  
    sRange = RealType.getRealType("SlantRange", kilometer, null);
    range = RealType.getRealType("Range", kilometer, null);
    height = RealType.getRealType("Height", kilometer, null);
    rangeHeightType = new RealTupleType(range, height);
    text = TextType.getTextType("Text");

    radarElevation = re;
/*
    try
    { sounding = new parseSounding("SoundingFiles/eta_oun.buf"); }
    catch (IOException ioex)
    { System.err.println("Cannot read sounding file: " + ioex); }

    setSounding( sounding.getSoundingData("OUN,040427,0000") );

    sounding = null;
*/

//System.out.println(soundingData.size());

  }

  /** Constructor that accepts a sounding */
  public BeamPath(double re, double maxRange, Vector sndg) 
                                  throws RemoteException, VisADException
  {
    soundingData = new Vector();
    soundingData = (Vector)sndg.clone();
    new BeamPath(re, maxRange);
  }


  /**
   * Calculate the radar range (and azimuth) between to sets of
   * Lat/Lon coordinates
   */
  public double calcLatLonRange(double lat1, double lon1,
                                                  double lat2, double lon2)
  {
     double term1, term2, term3, inside, distance, rLat1, rLon1, rLat2, rLon2;
     rLat1 = Math.PI*lat1/180;
     rLon1 = Math.PI*lon1/180;
     rLat2 = Math.PI*lat2/180;
     rLon2 = Math.PI*lon2/180;
     term1 = Math.cos(rLat1)*Math.cos(rLon1)*Math.cos(rLat2)*Math.cos(rLon2);
     term2 = Math.cos(rLat1)*Math.sin(rLon1)*Math.cos(rLat2)*Math.sin(rLon2);
     term3 = Math.sin(rLat1)*Math.sin(rLat2);
     inside = term1 + term2 + term3;
     distance = Math.acos(inside)  * EARTH_RADIUS;
     return distance;
  }



  /**
   * Calculate the radar range (and azimuth) between to sets of
   * Lat/Lon coordinates using visad.bom.Radar2DCoordinateSystem
   */
  public float[][] calcLatLonRange(float sLat, float sLon, 
           float eLat, float eLon) throws VisADException, RemoteException
  {
    Radar2DCoordinateSystem radarSystem = 
                                new Radar2DCoordinateSystem(sLat, sLon);

    float[][] distance = radarSystem.fromReference(new float[][] { {eLon}, {eLat} } );

    return distance;
  }



  /**
   * Calculate the beampaths for all the elevations for the current VCP
   */

/* 
  public UnionSet vcpBeamPaths()
                          throws VisADException, RemoteException
  {
    return vcpBeamPaths(currentVCP);
  }
*/

  public void vcpUpdate()
                          throws VisADException, RemoteException
  {
     vcpUpdate(currentVCP);
  }

  /**
   * Calculate the beampaths for all the elevations in a given VCP
   */ 

  public void vcpUpdate(String elevations)
                          throws VisADException, RemoteException
  {
    setVCP(elevations);
    String[] indivElevationAngles = elevations.split(",");
    int NUM_ELS = indivElevationAngles.length;
    setNumElevationAngles(NUM_ELS);

    MathType[] mtypes = {range, height, text};
    TupleType text_tuple = new TupleType(mtypes);

    FlatField[] beamPaths = new FlatField[NUM_ELS];
    UnionSet[] beamWidths = new UnionSet[NUM_ELS];
    Irregular2DSet[] beamFills = new Irregular2DSet[NUM_ELS];
    Tuple[] beamLabels = new Tuple[NUM_ELS];

    float[][][] valsTemp = new float[NUM_ELS][][];

    for (int i = 0; i < NUM_ELS; i++)
    {
      double el = Double.parseDouble( indivElevationAngles[i] );
      valsTemp[i] = calcBeamPath(el, MAX_RANGE);

      beamPaths[i] = beamPath(valsTemp[i]);
      beamWidths[i] = plotCurrentBeamWidth( el );
      beamFills[i] = fillBeamWidth( beamWidths[i] );

      Data td[] = {new Real(range, valsTemp[i][0][valsTemp[i][0].length - 1]),
                   new Real(height, valsTemp[i][1][valsTemp[i][1].length - 1]),
                   new Text(text, indivElevationAngles[i] )};
      beamLabels[i] = new Tuple(text_tuple, td);
    }

    setBeamPaths( beamPaths );
    setBeamWidths( beamWidths );
    setBeamFills( beamFills );
    setBeamLabels( beamLabels );

  }


  public void elevationUpdate(String elevation) throws VisADException, RemoteException
  {

    double el = Double.parseDouble( elevation );
    float[][] valsTemp = calcBeamPath(el, MAX_RANGE);
    MathType[] mtypes = {range, height, text};
    TupleType text_tuple = new TupleType(mtypes);
    setElevationAngle( el);

    FlatField path = beamPath();
    UnionSet width = plotCurrentBeamWidth( el );
    Irregular2DSet fill = fillBeamWidth(width);
   
    Data td[] = {new Real(range, valsTemp[0][valsTemp[0].length - 1]),
                 new Real(height, valsTemp[1][valsTemp[1].length - 1]),
                 new Text(text, elevation )};
    Tuple label = new Tuple(text_tuple, td);
 
    setBeamPath( path );
    setBeamWidth( width );
    setBeamFill( fill );
    setBeamLabel( label );

    if (soundingData != null)
    { createNSBeamPaths(); }
  }
    


  /**
   * Calculate the radar beam path relative to a GreatCircle (Earth's surface)
   * and return a FlatField of height(range), Equations are from Doviak and Zrnic (1993)
   * p21, equations 2.28b and c
   */
  public float[][] calcBeamPath(double elevAngle, double maxRange) 
                                      throws VisADException, RemoteException
  {
    double MAX_RANGE = maxRange;
    int BEAMPATHSAMPLING = 400;
    double E_ANGLE = elevAngle * RADIANS;
    double EFFECTIVE_EARTH_RADIUS = INDEX_OF_REFRACTION * EARTH_RADIUS;

    Vector ht = new Vector();
    Vector rn = new Vector();

    float[][] heightVals;
    float[][] rangeVals;

    Linear1DSet r_set = new Linear1DSet(0.0, maxRange, BEAMPATHSAMPLING);
    float[][] slantRange = r_set.getSamples(true);
    Irregular1DSet rangeSet;
    FlatField beamPath_ff;
    FunctionType rangeHeightFunc = new FunctionType(range, height);
    
    for (int i = 0; i < BEAMPATHSAMPLING; i++)
    {
      double inside = ((Math.pow(slantRange[0][i],2)) + (Math.pow(EFFECTIVE_EARTH_RADIUS,2))
                            + (2*slantRange[0][i]*EFFECTIVE_EARTH_RADIUS*Math.sin(E_ANGLE)));
      double outside = EFFECTIVE_EARTH_RADIUS;
      Float n = new Float( (float)( (radarElevation/1000) + ((Math.pow(inside,0.5)) - outside)) );
      ht.add(n);

      double numer = (slantRange[0][i]*Math.cos(E_ANGLE));
      double denom = (EFFECTIVE_EARTH_RADIUS + n.floatValue() );
      double quo = (numer/denom);
      Float o = new Float( (float)(EFFECTIVE_EARTH_RADIUS*Math.asin(quo)) );
      rn.add(o);

      if (n.floatValue() > 25.0)
      { break; }

      else if (o.floatValue() > MAX_RANGE)
      { break; }

    }

    float[][] rangeAndHeight = new float[2][rn.size()];

    Float[] e = (Float[]) ht.toArray(new Float[ht.size()]);
    Float[] f = (Float[]) rn.toArray(new Float[rn.size()]);
    for (int g = 0; g < ht.size(); g++)
    {
      rangeAndHeight[0][g] = f[g].floatValue();
      rangeAndHeight[1][g] = e[g].floatValue();
    }

    return rangeAndHeight;
  }



  /**
   * Get the calculated BeamPath and make it into a FlatField
   */
  public FlatField beamPath() throws VisADException, RemoteException
  {
    Irregular1DSet rangeSet;
    FlatField beamPath_ff;
    FunctionType rangeHeightFunc = new FunctionType(range, height);

    float[][] rangeThenHeight = calcBeamPath(elevationAngle, MAX_RANGE);
    float[][] rangeVals = new float[1][rangeThenHeight[0].length];
    float[][] heightVals = new float[1][rangeThenHeight[1].length];

    rangeVals[0] = rangeThenHeight[0];
    heightVals[0] = rangeThenHeight[1];

    rangeSet = new Irregular1DSet(range, rangeVals);
    beamPath_ff = new FlatField(rangeHeightFunc, rangeSet);
    beamPath_ff.setSamples(heightVals);

    return beamPath_ff;
  }

    
  
  /**
   * Given a 2D array for a beam path, make it into a FlatField
   */
  public FlatField beamPath(float[][] beamPathVals) throws VisADException, RemoteException
  {
    Irregular1DSet rangeSet;
    FlatField beamPath_ff;
    FunctionType rangeHeightFunc = new FunctionType(range, height);

//    float[][] rangeThenHeight = calcBeamPath(elevationAngle, MAX_RANGE);
    float[][] rangeVals = new float[1][beamPathVals[0].length];
    float[][] heightVals = new float[1][beamPathVals[1].length];

    rangeVals[0] = beamPathVals[0];
    heightVals[0] = beamPathVals[1];

    rangeSet = new Irregular1DSet(range, rangeVals);
    beamPath_ff = new FlatField(rangeHeightFunc, rangeSet);
    beamPath_ff.setSamples(heightVals);

    return beamPath_ff;
  }




  /**
   * Plot the top and bottom of the BeamWidth given the curent elevation angle
   */
  public UnionSet plotCurrentBeamWidth() throws VisADException, RemoteException
  {
    return plotCurrentBeamWidth(elevationAngle);
  }



  /**
   * Plot the top and bottom of the BeamWidth given an elevation angle and the current beamwidth
   */
  public UnionSet plotCurrentBeamWidth(double elAngle) throws VisADException, RemoteException
  {
    float[][] topRangeHeight = calcBeamPath(getBeamWidthTop(elAngle), MAX_RANGE);
    float[][] bottomRangeHeight = calcBeamPath(getBeamWidthBottom(elAngle), MAX_RANGE);

    Gridded2DSet topSet = new Gridded2DSet(new RealTupleType(range, height), topRangeHeight, 
                                                                 topRangeHeight[0].length);
    Gridded2DSet bottomSet = new Gridded2DSet(new RealTupleType(range, height), bottomRangeHeight, 
                                                                 bottomRangeHeight[0].length);
    SampledSet[] ss = new SampledSet[2];
    ss[0] = topSet;
    ss[1] = bottomSet;

    UnionSet beamWidthSet = new UnionSet(new RealTupleType(range, height), ss);

    return beamWidthSet;  
  }




  /**
   * Fill the current beam width
   */
  public Irregular2DSet fillBeamWidth(UnionSet uSet) throws VisADException, RemoteException
  {
    SampledSet[] sSets = uSet.getSets();

    Gridded2DSet topSet = (Gridded2DSet) sSets[0];
    Gridded2DSet bottomSet = (Gridded2DSet) sSets[1];

    float[][] topSetVals = topSet.getSamples();
    float[][] bottomSetVals = bottomSet.getSamples();


//  ####  Need a connector from bottom of beam to top of beam to create the 2D Fill.
//  ####  If beam width spans the upper right corner of the displap, add the corner point ("IF stmt")
    float con[][];
    if ( (topSetVals[0][topSetVals[0].length-1] < 398.0f) && 
         (bottomSetVals[0][bottomSetVals[0].length-1] >= 398.0f) &&
         (topSetVals[1][topSetVals[1].length-1] >= 24.7f) &&
         (bottomSetVals[1][bottomSetVals[1].length-1] < 24.7f) )
    {

      con = new float[][] {
      { bottomSetVals[0][bottomSetVals[0].length-1], 400.0f, topSetVals[0][topSetVals[0].length-1]+0.01f },
      { bottomSetVals[1][bottomSetVals[1].length-1]+0.01f, 25.0f, topSetVals[1][topSetVals[1].length-1]-0.01f }
         };
    }
    else
    {
      con =  new float[][]{ 
      { bottomSetVals[0][bottomSetVals[0].length-1], topSetVals[0][topSetVals[0].length-1]+0.01f },
      { bottomSetVals[1][bottomSetVals[1].length-1]+0.01f, topSetVals[1][topSetVals[1].length-1]-0.01f }
         };
    }

    float[][] tSetVals = new float[2][topSetVals[0].length];
    for (int i = 1; i < topSetVals[0].length; i++)
    {
      tSetVals[0][i-1] = topSetVals[0][topSetVals[0].length-i];
      tSetVals[1][i-1] = topSetVals[1][topSetVals[1].length-i];
    }
      tSetVals[0][tSetVals[0].length-1] = (topSetVals[0][0]+topSetVals[0][1])/2f;
      tSetVals[1][tSetVals[1].length-1] = (topSetVals[1][0]+topSetVals[1][1])/2f;
    
    int numVals = (bottomSetVals[0].length + tSetVals[0].length + con[0].length);
    float[][] allVals = new float[2][numVals];
    for (int p = 0; p < numVals; p++)
    {
      if (p < bottomSetVals[0].length)
      {
        allVals[0][p] = bottomSetVals[0][p];
        allVals[1][p] = bottomSetVals[1][p];
      }
      if (p < (bottomSetVals[0].length+con[0].length) && p >= bottomSetVals[0].length)
      {
        allVals[0][p] = con[0][p-bottomSetVals[0].length];
        allVals[1][p] = con[1][p-bottomSetVals[0].length];
      }
      if (p < numVals && p >= (bottomSetVals[0].length+con[0].length) )
      {
        allVals[0][p] = tSetVals[0][p-(bottomSetVals[0].length+con[0].length)];
        allVals[1][p] = tSetVals[1][p-(bottomSetVals[0].length+con[0].length)];

      }


    }
    
    Gridded2DSet g2s = new Gridded2DSet(rangeHeightType, allVals, numVals);
 
    Irregular2DSet filled = DelaunayCustom.fill(g2s);
 
    return filled;

  }


  /**
   * Calculate the non-standard beam path given an atmospheric sounding
   * in the form of a vector of arrays.  The arrays should contain the 
   * following: array[0] = PRES, array[1] = TEMP, array[2] = DEWP, 
   * array[3] = HT.
   * This makes use of Doviak & Zrnic (1993) eq'ns. 2.30 - 2.34 and 
   * the "Refractivity" and "Index of Refraction" of the atmosphere 
   * as calculated from the sounding.
   */
  public void calcRefractSounding()
  {

    String[] line0 = null;
    String[] line1 = null;
    Vector refrSndg = new Vector();

    for (Enumeration e = soundingData.elements() ; e.hasMoreElements() ;)
    {

        line1 = (String[])e.nextElement();

        // ### if missing data, skip
        if ( checkMissingData(line1) )
        { continue; }

        if (line0 != null)
        {

          double refract0 = refractivity(
                                         Double.parseDouble(line0[0]), 
                                         Double.parseDouble(line0[1]), 
                                         Double.parseDouble(line0[2]));
          double refract1 = refractivity(
                                         Double.parseDouble(line1[0]), 
                                         Double.parseDouble(line1[1]), 
                                         Double.parseDouble(line1[2]));

          double refIndex0 = refractiveIndex(refract0);
          double refIndex1 = refractiveIndex(refract1);

          double height0 = Double.parseDouble(line0[3]);
          double height1 = Double.parseDouble(line1[3]);

          double delta_N = refract1 - refract0;
          double delta_n = refIndex1 - refIndex0;
          double delta_h = height1 - height0;
          double dN_dh = delta_N/(delta_h/1000);  // in m^-1
//          double dn_dh = delta_n/(delta_h/1000);  // in km^-1
          double dn_dh = delta_n/delta_h;  // in m^-1

          refrSndg.add( new Double[]{  
                                     new Double(dn_dh), 
                                     new Double(height1), 
                                     new Double(height0),
                                     new Double(dN_dh) 
                                    } );

        }

        line0 = new String[] {line1[0], line1[1], line1[2], line1[3]};
      }

      setRefSounding(refrSndg);
      try 
      { createNSBeamPaths(); }
      catch (Exception e) { System.err.println("NSPath Err: " + e); }
  }



  /**
   * From Doviak & Zrnic (1993) Eq'n 2.19.  Accuracy of ~ 0.1
   * This is "N"
   * pres and vaporPres in mb, temp in K
   */
  public double refractivity(double pres, double temp, double dewTemp)
  {
    double vPres = vaporPressure(dewTemp);
    return ( (77.6/(ZERO_K+temp)) * (pres + 4810*vPres/(ZERO_K+temp)) );
  }
                                                                            
  /**
   * From Doviak & Zrnic (1993) Eq'n 2.15
   */
  public double refractiveIndex(double N)
  {
    return ( 1 + N*Math.pow(10,-6));
  }
                                                                            
  /**
   * Using Clausius-Clapeyron Eq'n
   * Note T -> Es (saturation vapor pres), Td -> E (vapor pres)
   * vars: temp0 = ZERO_K (K)
   *       Lv = 2.480 (x10^6 J/kg) (avg val)
   *       Rv = 461.5 (J/kg)
   *       Eo = 6.11 mb
   */
  public double vaporPressure(double temp)
  {
    return ( 6.11 *  Math.exp(((2.48*Math.pow(10,6))/461.5) * ((1/ZERO_K) - (1/(ZERO_K+temp)))) );
  }


  public boolean checkMissingData(String[] d)
  {
    boolean isMissing = false;
    final double missingVal = -9999.0;

    for (int k = 0; k < d.length; ++k)
    {
      double val = Double.parseDouble(d[k]);
      if (val == missingVal)
      { isMissing = true; }  
    }

    return isMissing;
  }


  public void createNSBeamPaths() throws VisADException, RemoteException
  {
    float top = (float) (getElevationAngle() + (getEnteredBeamWidth() / 2.0f));
    float bottom = (float) (getElevationAngle() - (getEnteredBeamWidth() / 2.0f));


    Gridded2DSet pathSet = (Gridded2DSet)calcNSBeamPath(getElevationAngle());
    setNSBeamPath(pathSet);

    Gridded2DSet topPathSet = (Gridded2DSet)calcNSBeamPath(top);
    Gridded2DSet bottomPathSet = (Gridded2DSet)calcNSBeamPath(bottom);

    UnionSet nsUSet = new UnionSet(
           rangeHeightType, new SampledSet[] {topPathSet, bottomPathSet} );

    setNSBeamWidth(nsUSet);



  }

//  public void calcNSBeamPath(double elAngle) throws VisADException, RemoteException
  public Set calcNSBeamPath(double elAngle) throws VisADException, RemoteException
  {

    Irregular2DSet nsPath;
    double sPrime = 0, sPrime0 = 0, hPrime0 = 0;
    double totalS = 0, totalH = 0, arlH = 0;
    boolean belowRadar = true;
    double ELEV_ANGLE_RADS = elAngle *RADIANS;
    Vector es = new Vector();
    Vector atch = new Vector();

    
    es.add(new Float(0.0));
    atch.add(new Float(getRadarElevation()));

    if (!refSoundingData.isEmpty() && refSoundingData.size() > 0)
    {

      for (Enumeration e = refSoundingData.elements() ; e.hasMoreElements() ;)
      {

        Double[] layer = (Double[])e.nextElement();
        double betaOne = layer[0].doubleValue();
        double hOne = layer[2].doubleValue();
        double hPrime = layer[1].doubleValue() - hOne;
        double rPrime = (EARTH_RADIUS*1000) + hOne;
        double thetaEPrime = -999.0;

        // ### if the top of the layer is below the radar, skip
        if (layer[1].doubleValue() < getRadarElevation())
        { continue; }

        // ### if the radar is between the top and bottom of the 
        // ### sounding layers, then we will use the current elevation angle
        if (layer[2].doubleValue() <= getRadarElevation() && 
              layer[1].doubleValue() >= getRadarElevation())
        { thetaEPrime = ELEV_ANGLE_RADS; }

        else
        { 
          // ### at this point sPrime is the previous....
          double deltaS = sPrime;
          double deltaH = hPrime0;
          thetaEPrime = Math.atan( (deltaH/deltaS) ) ;
        }
/*
System.out.println("tE: " + thetaEPrime);
System.out.println("b1: " + betaOne);
System.out.println( Math.cos(thetaEPrime));
System.out.println((1 + betaOne*rPrime));
System.out.println(Math.pow( Math.sin(thetaEPrime), 2));
System.out.println((2*rPrime*(1+betaOne*rPrime)*hPrime));
System.out.println("=====================");
*/

        double firstTerm = ( Math.cos(thetaEPrime) / (1 + betaOne*rPrime) );
        double secondTermA_inside = (
            Math.pow(rPrime, 2) * Math.pow( Math.sin(thetaEPrime), 2)
          + 2*rPrime*(1+betaOne*rPrime)*hPrime 
          );

        double secondTermA = Math.sqrt(secondTermA_inside);
        if (secondTermA_inside < 0)
        { 
          double thetaPRadical = -2*hOne*(1+betaOne*rPrime)/rPrime;
          double thetaP = Math.asin( Math.sqrt(thetaPRadical) );
          double hR = (-rPrime*Math.pow( Math.sin(thetaEPrime), 2)) 
                      / (2 * (betaOne*rPrime + 1));
          double sHr = (-rPrime*Math.cos(thetaEPrime)*Math.sin(thetaEPrime))
                      / (betaOne*rPrime + 1);
/*
          System.err.println("Negative Radical.  Exiting...");
          System.exit(0);
*/
/*
          String err0 = new String("Beam at elevation angle: ");
//          String err1 = String.valueOf(thetaEPrime);
          String err1 = String.valueOf(elAngle);
          String err2 = new String(" is DUCTING.  Stopping beam calculation...");
          ErrorFrame eFrame = new ErrorFrame(new String[]{ err0, err1, err2});
          eFrame = null;
          break;
*/
        }

        double secondTermB = rPrime*Math.sin(thetaEPrime);

        sPrime = firstTerm * (secondTermA - secondTermB);

        totalS = totalS + sPrime; 
        // ### should we subtract the radar height?
        totalH = layer[1].doubleValue();
/*
System.out.println("thetaE: " + thetaEPrime 
                   + "\nbetaOne: " + betaOne
                   + "\ndN/dh: " + layer[3].doubleValue()
                   + "\nhOne: " + hOne
                   + "\nrPrime: " + rPrime
                   + "\nhPrime: " + hPrime
                   + "\nsPrime: " + sPrime
                   + "\ntotalS: " + totalS
                   + "\ntotalH: " + totalH
                   + "\n-----------------" );
*/
//        if (totalS > MAX_RANGE*1000)
//        { break; }

        hPrime0 = hPrime;
        es.add(new Float(totalS));
        atch.add(new Float(totalH));

      }
    }

    float[][] rangeVals = new float[1][es.size()];
    float[][] heightVals = new float[1][atch.size()];
    
    Float[] esFloatVals = (Float[])es.toArray(new Float[es.size()]);
    Float[] atchFloatVals = (Float[])atch.toArray(new Float[atch.size()]);

    float[][] rangeHeightVals = new float[2][esFloatVals.length];

    for (int p = 0; p < esFloatVals.length; ++p)
    {
      rangeVals[0][p] = esFloatVals[p].floatValue()/1000.0f;
      heightVals[0][p] = atchFloatVals[p].floatValue()/1000.0f;

      rangeHeightVals[0][p] = esFloatVals[p].floatValue()/1000.0f;
      rangeHeightVals[1][p] = atchFloatVals[p].floatValue()/1000.0f;
    }
    
/*
    FunctionType rangeHeightFunc = new FunctionType(range, height);
    Irregular1DSet rangeSet = new Irregular1DSet(range, rangeVals);
    FlatField beamPath_ff = new FlatField(rangeHeightFunc, rangeSet);
    beamPath_ff.setSamples(heightVals);

    setNSBeamPath(beamPath_ff);
*/


    Gridded2DSet pathSet = new Gridded2DSet(rangeHeightType, rangeHeightVals, esFloatVals.length);


    return pathSet;

  }


  /**
   *  Setters and Getters
   */
  public void setBeamPaths(FlatField[] p)
  { vcpPaths = p; }

  public void setBeamWidths(UnionSet[] p)
  { vcpWidths = p; }

  public void setBeamFills(Irregular2DSet[] p)
  { vcpFills = p; }

  public void setBeamLabels(Tuple[] p)
  { vcpLabels = p; }

  public FlatField[] getBeamPaths()
  { return vcpPaths; }

  public UnionSet[] getBeamWidths()
  { return vcpWidths; }

  public Irregular2DSet[] getBeamFills()
  { return vcpFills; }

  public Tuple[] getBeamLabels()
  { return vcpLabels; }

  public void setBeamPath(FlatField p)
  { singlePath = p; }

  public void setBeamWidth(UnionSet p)
  { singleWidth = p; }

  public void setBeamFill(Irregular2DSet p)
  { singleFill = p; }

  public void setBeamLabel(Tuple p)
  { singleLabel = p; }

  public FlatField getBeamPath()
  { return singlePath; }

  public UnionSet getBeamWidth()
  { return singleWidth; }

  public Irregular2DSet getBeamFill()
  { return singleFill; }

  public Tuple getBeamLabel()
  { return singleLabel; }


  public double getRadarElevation()
  { return radarElevation; }

  public void setEnteredBeamWidth(String bw)
  { enteredBeamWidth = Double.parseDouble(bw); }

  public double getEnteredBeamWidth()
  { return enteredBeamWidth; }

  public double getBeamWidthTop()
  {
    double bwHalf = (getEnteredBeamWidth()/2);
    return (elevationAngle + bwHalf);
  }

  public double getBeamWidthBottom()
  {
    double bwHalf = (getEnteredBeamWidth()/2);
    return (elevationAngle - bwHalf);
  }

  public double getBeamWidthTop(double elAn)
  {
    double bwHalf = (getEnteredBeamWidth()/2);
    return (elAn + bwHalf);
  }

  public double getBeamWidthBottom(double elAn)
  {
    double bwHalf = (getEnteredBeamWidth()/2);
    return (elAn - bwHalf);
  }

  public void setElevationAngle(double ea)
  { elevationAngle = ea; }

  public double getElevationAngle()
  { return elevationAngle; }

  public void setVCP(String els)
  {
    currentVCP = null;
    currentVCP = els;
  }

  public String getVCP()
  { return currentVCP; }

  public void setNumElevationAngles(int num)
  { numberOfElevationAnglesInCurrentVCP = num; }

  public int getNumElevationAngles()
  { return numberOfElevationAnglesInCurrentVCP; }

  public void setRefSounding(Vector sndg)
  { 
    refSoundingData = new Vector();
    refSoundingData = (Vector)sndg.clone();
  }

  public void setSounding(Vector sndg)
  { 
    soundingData = new Vector();
    soundingData = (Vector)sndg.clone();
    calcRefractSounding();
  }

  public void setNSBeamPath(Gridded2DSet p)
  {
     nsPath = p;
  }

  public Gridded2DSet getNSBeamPath()
  { return nsPath; }

  public void setNSBeamWidth(UnionSet p)
  {
     nsWidth = p;
  }

  public UnionSet getNSBeamWidth()
  { return nsWidth; }


}

