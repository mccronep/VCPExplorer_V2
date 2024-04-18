package VCPExplorer;


import java.*;
import java.awt.*;
import java.awt.event.*;
import java.rmi.RemoteException;
import java.util.Vector;

import javax.swing.*;
import javax.swing.event.*;

import visad.*;
import visad.java3d.*;
import visad.data.units.*;

public class HeightUnderEstimate// extends JPanel
{

  private RealType ran, ht, value;
  private RealTupleType ranHt_tt, htRan_tt;
  private Unit kilometer, km, nMile, kFt;
  private FlatField heightUnderestimate;
  private FlatField[][] beamPathSet = null;
  private double[] ELEVATION_ANGLES;

  final static double INDEX_OF_REFRACTION = (4.0/3.0);
  final static double EARTH_RADIUS = 6378; // km at Equator
  final static double RADIANS = (Math.PI/180);
  final static int MAX_HT = 25;
  final static int MAX_RN = 400;
  private double RADAR_ELEVATION;


  public HeightUnderEstimate(String elevationAngles, double radarEl)
                                       throws VisADException, RemoteException
  {

    try
    { 
//      kilometer = Parser.parse("kilometer").clone("km"); 
      kilometer = visad.data.units.Parser.parse("kilometer").clone("km");
      nMile = visad.data.units.Parser.parse("nautical_mile").clone("nm");
      kFt = visad.data.units.Parser.parse("kilofeet").clone("kFt");

    }
    catch (ParseException P)
    { System.out.println("ParseException: " + P); }
    catch (UnitException U)
    { System.out.println("UnitException: " + U); }

    ran = RealType.getRealType("Range", kilometer, null);
    ht = RealType.getRealType("Height", kilometer, null);
    value = RealType.getRealType("Value", kilometer, null);

    ranHt_tt = new RealTupleType(ran, ht);
    htRan_tt = new RealTupleType(ht, ran);

    RADAR_ELEVATION = radarEl;
    setElevationAngles(  getElevationAngleDoubles(elevationAngles) );


    calcHeightUnderestimateFill();


  }

  public void calcHeightUnderestimateFill() throws VisADException, RemoteException
  {
    float GRID_TOP = MAX_HT;
    float GRID_BOT = 0.0f;
    float RESOLUTION = 0.05f;  // Vertical spacing in km (50 m)
    int NCOLS = MAX_RN + 1;  // 0 and 400 inclusive
    int NROWS = (int) ( (GRID_TOP-GRID_BOT) / RESOLUTION) + 1; // GRID_TOP & GRID_BOT inclusive
    int SAMPLES = NCOLS*NROWS;
    float[][] ranHtGridVals = new float[2][SAMPLES];
    float[][] valueVals = new float[1][SAMPLES];
    java.util.Arrays.fill(valueVals[0], Float.NaN );

    double[] elevAngles = getElevationAngles();
    int NUM_BPATHS = elevAngles.length;
    FlatField[] beamPath_ffs = new FlatField[NUM_BPATHS];

    // ### Get beam path pairs (theta and theta+1)
    for (int i = 0; i < NUM_BPATHS; ++i)
    {
      beamPath_ffs[i] = makeBeamPathsFlatField(elevAngles[i], RADAR_ELEVATION, MAX_HT, MAX_RN);
    }


    // ### At each range point get the height.  This is necessary for 2 reasons
    // ### One, the beam path is not necessarily created at every kilometer, therefore we
    // ### must sample the beam path at every km using the "evaluate" function (FlatField)
    // ### Two, the reason we do not include this in the gridpoint processing is that it is
    // ### *very* slow for some reason.  If we "preprocess" the top and bottom of the wedge
    // ### then the rest goes very quickly
    float[][][] beamPathTnB = new float[NUM_BPATHS][2][NCOLS];
    for (int i = 0; i < NCOLS; i++)
    {
      Real range = new Real(ran, i);
        for (int k = 0; k < NUM_BPATHS-1; ++k)
        {
          // ### Getting the top and bottom of the "wedge" defined by theta and theta+1 elev. angles
          int TOP_BEAM = NUM_BPATHS - (k+1);
          int BOTTOM_BEAM = NUM_BPATHS - (k+2);

          Real topReal = (Real)
          beamPath_ffs[TOP_BEAM].evaluate(range, Data.WEIGHTED_AVERAGE, Data.NO_ERRORS);
          Real botomReal = (Real)
          beamPath_ffs[BOTTOM_BEAM].evaluate(range, Data.WEIGHTED_AVERAGE, Data.NO_ERRORS);

          float top = (float)topReal.getValue();
          float bottom = (float)botomReal.getValue();

          // ### store the top and bottom of each "wedge" defined as above.
          // ### note that there are NUM_BPATHS-1 wedges
          beamPathTnB[k][0][i] = top;
          beamPathTnB[k][1][i] = bottom;
        }
      }

    int index = 0;
    float heightVal = GRID_TOP;
    float rangeVal = 0.0f;

    // ### Set up the 2D grid points also getting the values of the wedges from above
    for (int i = 0; i < NCOLS; i++)
    {
      heightVal = 25.0f;
      Real range = new Real(ran, i);
      for (int j = 0; j < NROWS; j++)
      {
        Real height = new Real(ht, j);
        heightVal = (float) (GRID_TOP - j*RESOLUTION);
        rangeVal = (float)i;

        ranHtGridVals[0][index] = rangeVal;
        ranHtGridVals[1][index] = heightVal;

        for (int k = 0; k < NUM_BPATHS; ++k)
        {
          float top = beamPathTnB[k][0][i];
          float bottom = beamPathTnB[k][1][i];

          if (
             (heightVal > top) || (heightVal < bottom) ||
             (Float.compare(top, Float.NaN) == 0) ||
             (Float.compare(bottom, Float.NaN) == 0)
             )
          { continue; }
          if (heightVal <= top && heightVal >= bottom)
          {
            float htV = (float) heightVal - bottom;
            valueVals[0][index] = htV;
          }

        }

        index++;
      }
    }


    Gridded2DSet gridSet = new Gridded2DSet(ranHt_tt, ranHtGridVals, NROWS, NCOLS);
    FunctionType funcType = new FunctionType(ranHt_tt, value);
    FlatField ff = new FlatField(funcType, gridSet);
    ff.setSamples(valueVals);

    setHeightUnderestimateVals(ff);


  }


  public float[][] calcBeamPath(double elevAngle, double radarElevation, 
                                            double maxHeight, double maxRange)
                                      throws VisADException, RemoteException
  {
    double MAX_RANGE = maxRange;
    double MAX_HEIGHT = maxHeight;
    int BEAMPATHSAMPLING = 400;
    double E_ANGLE = elevAngle * RADIANS;
    double EFFECTIVE_EARTH_RADIUS = INDEX_OF_REFRACTION * EARTH_RADIUS;

    Vector h = new Vector();
    Vector r = new Vector();

    float[][] heightVals;
    float[][] rangeVals;
    Linear1DSet r_set = new Linear1DSet(0.0, maxRange+10.0, BEAMPATHSAMPLING);
    float[][] slantRange = r_set.getSamples(true);
    Irregular1DSet rangeSet;
    FlatField beamPath_ff;
    FunctionType rangeHeightFunc = new FunctionType(ran, ht);

    for (int i = 0; i < BEAMPATHSAMPLING; i++)
    {
      double inside = ((Math.pow(slantRange[0][i],2)) + (Math.pow(EFFECTIVE_EARTH_RADIUS,2))
                            + (2*slantRange[0][i]*EFFECTIVE_EARTH_RADIUS*Math.sin(E_ANGLE)));
      double outside = EFFECTIVE_EARTH_RADIUS;
      Float n = new Float( (float)( (radarElevation/1000) + ((Math.pow(inside,0.5)) - outside)) );
      h.add(n);

      double numer = (slantRange[0][i]*Math.cos(E_ANGLE));
      double denom = (EFFECTIVE_EARTH_RADIUS + n.floatValue() );
      double quo = (numer/denom);
      Float o = new Float( (float)(EFFECTIVE_EARTH_RADIUS*Math.asin(quo)) );
      r.add(o);
/*
      if (n.floatValue() > MAX_HEIGHT)
      { break; }
*/
      if (o.floatValue() > MAX_RANGE)
      { break; }

    }

    float[][] rangeAndHeight = new float[2][r.size()];

    Float[] e = (Float[]) h.toArray(new Float[h.size()]);
    Float[] f = (Float[]) r.toArray(new Float[r.size()]);
    for (int g = 0; g < h.size(); g++)
    {
      rangeAndHeight[0][g] = f[g].floatValue();
      rangeAndHeight[1][g] = e[g].floatValue();
    }

    return rangeAndHeight;
  }



  public FlatField makeBeamPathsFlatField(double elevationAngle, double radarElevation,
                                                               double maxHt, double maxRan) 
                                                  throws VisADException, RemoteException
  {
    float[][] beamPath0 = calcBeamPath(elevationAngle, radarElevation, maxHt, maxRan);
    float[][] path0_ranVals = new float[][]{ beamPath0[0] };
    float[][] path0_htVals = new float[][]{ beamPath0[1] };

    Irregular1DSet path0_ranSet = new Irregular1DSet(ran, path0_ranVals);
    FunctionType path0_ranHtFunc = new FunctionType(ran, ht);
    FlatField path0_ranHt_ff = new FlatField(path0_ranHtFunc, path0_ranSet);
    path0_ranHt_ff.setSamples(path0_htVals);

    return path0_ranHt_ff;
  }

  public FlatField[] calcBeamPathPair(double[] elevAngles, double radarElev)
                                                 throws VisADException, RemoteException
  {
    float PATH0_MAX_HT = (float) MAX_HT;
    float PATH0_MAX_RN = (float) MAX_RN;

    FlatField nextHighest = makeBeamPathsFlatField((double) elevAngles[0],
                              (double) radarElev, (double) PATH0_MAX_HT, (double) PATH0_MAX_RN);

    FlatField highest = makeBeamPathsFlatField((double) elevAngles[1],
                              (double) radarElev, (double) PATH0_MAX_HT, (double) PATH0_MAX_RN);

    return new FlatField[]{nextHighest, highest};
  }




  public double[] getElevationAngleDoubles(String elevs)
  {
    String[] stringAngles = elevs.split(",");
    double[] doubleAngles = new double[stringAngles.length];
    for (int i = 0; i < stringAngles.length; i++)
    {
      doubleAngles[i] = Double.parseDouble( stringAngles[i] );
    }
    // ### sort from lowest to highest
    java.util.Arrays.sort(doubleAngles);

    return doubleAngles;
  }


  public FlatField switchDomainAndRange(FlatField oldff) throws VisADException, RemoteException
  {
    FunctionType oldFType = (FunctionType)oldff.getType();
    MathType[] oldDomainTypes = oldFType.getDomain().getComponents();
    MathType[] oldRangeTypes = oldFType.getFlatRange().getComponents();
    RealType oldDomainType = (RealType) oldDomainTypes[0];
    RealType oldRangeType = (RealType) oldRangeTypes[0];
/*
    Set[] rangeSets = oldff.getRangeSets();
    RealType oldRangeType = (RealType) rangeSets[0].getType();
*/
    int NUM_RANGE_SETS = oldRangeTypes.length;

    // ### for now only process a one-to-one mapping
    if (NUM_RANGE_SETS > 1)
    { return null; }
    /*
    RealType[] oldRangeTypes = new RealType[NUM_RANGE_SETS];
    for (int i = 0; i < NUM_RANGE_SETS; i++)
    {
      oldRangeTypes[i] = rangeSets[i].getType
    }
    */

    float[][] oldDomainVals = oldff.getDomainSet().getSamples();
    float[][] oldRangeVals = oldff.getFloats();

    Irregular1DSet newDomainSet = new Irregular1DSet(oldRangeType, oldRangeVals);
    FunctionType newFType = new FunctionType(oldRangeType, oldDomainType);
    FlatField new_ff = new FlatField(newFType, newDomainSet);
    new_ff.setSamples(oldDomainVals);

    return new_ff;
  }

  public UnionSet createSawTooth(Real h, FlatField[] bPaths) throws VisADException, RemoteException
  {
    Real topHt = h;
    int NUM_PATHS = bPaths.length;
    Gridded2DSet connector;
    Vector sawToothPieces = new Vector();
    UnionSet sawToothSet;

    // ### (range -> height)
    FlatField[] normalBeamPaths_ff = new FlatField[NUM_PATHS];
    System.arraycopy(bPaths, 0, normalBeamPaths_ff, 0, NUM_PATHS);

    // ### (height -> range)
    FlatField[] switchedBeamPaths_ff = new FlatField[NUM_PATHS];


    for (int i = 0; i < NUM_PATHS; i++)
    {
      Real prevTopRan, currentBottomRan, currentBottomHt, currentTopRan;
      switchedBeamPaths_ff[i] = switchDomainAndRange( normalBeamPaths_ff[i] );

      currentTopRan = (Real) switchedBeamPaths_ff[i].evaluate(topHt);

      if (i == 0)
      {
        currentBottomRan = new Real(ran, 0.0);
        currentBottomHt = (Real) normalBeamPaths_ff[i].evaluate( currentBottomRan );
      }
      else
      {
        switchedBeamPaths_ff[i-1] = switchDomainAndRange( normalBeamPaths_ff[i-1] );
        prevTopRan = (Real) switchedBeamPaths_ff[i-1].evaluate(topHt);
        currentBottomRan = prevTopRan;
        currentBottomHt = (Real) normalBeamPaths_ff[i].evaluate(currentBottomRan);

        float[][] connectorVals = new float[][] { 
                        {(float)prevTopRan.getValue(), (float)currentBottomRan.getValue()},
                        {(float)topHt.getValue(), (float)currentBottomHt.getValue()}
                        };
        connector = new Gridded2DSet(ranHt_tt, connectorVals, connectorVals[0].length);
        sawToothPieces.add(connector);

      }

       int missingTopRan = Float.compare((float)currentTopRan.getValue(), Float.NaN);
       int missingBottomRan = Float.compare((float)currentBottomRan.getValue(), Float.NaN);

       if (missingBottomRan == 0 && missingTopRan == 0)
       { continue; }
       if (missingBottomRan != 0 && missingTopRan == 0)
       {
         float[][] normalBeamPathDomainVals = normalBeamPaths_ff[i].getDomainSet().getSamples(true);
         float lastRanVal = normalBeamPathDomainVals[0][normalBeamPathDomainVals[0].length-1];
         currentTopRan = new Real(ran, lastRanVal);
       }

      FlatField subsetFF = flatFieldSubset((float)currentBottomRan.getValue(), 
                                           (float)currentTopRan.getValue(), normalBeamPaths_ff[i]);

      float[][] subsetDomainVals = subsetFF.getDomainSet().getSamples();
      float[][] subsetRangeVals = subsetFF.getFloats();
      float[][] gridded2DSetVals = new float[][] { subsetDomainVals[0], subsetRangeVals[0] };
      

      Gridded2DSet alongBeamPathSubset = 
                 new Gridded2DSet(ranHt_tt, gridded2DSetVals, gridded2DSetVals[0].length);

      sawToothPieces.add(alongBeamPathSubset);

    }


    Gridded2DSet[] e = (Gridded2DSet[]) sawToothPieces.toArray(
                                     new Gridded2DSet[sawToothPieces.size()]);

    sawToothSet = new UnionSet(ranHt_tt, e);

    return sawToothSet;

  }

  public FlatField flatFieldSubset(float domainBottom, float domainTop, FlatField origFF)
                                                throws VisADException, RemoteException
  {
    FunctionType origFType = (FunctionType)origFF.getType();

    MathType[] origDomainTypes = origFType.getDomain().getComponents();
    RealType origDomainType = (RealType) origDomainTypes[0];

    MathType[] origRangeTypes = origFType.getFlatRange().getComponents();
    RealType origRangeType = (RealType) origRangeTypes[0];

    Set origDomainSet = (Irregular1DSet)origFF.getDomainSet();

    float[][] domainBottomVal = new float[][]{ {domainBottom} };
    float[][] domainTopVal = new float[][]{ {domainTop} };
    int[] domainBottomIndex = origDomainSet.valueToIndex(domainBottomVal);
    int[] domainTopIndex = origDomainSet.valueToIndex(domainTopVal);

    int domainIndexDiff = domainTopIndex[0] - domainBottomIndex[0];
    int[] domainIndexRange = new int[domainIndexDiff+1];
    for (int i = 0; i <= domainIndexDiff; i++)
    { 
      domainIndexRange[i] = domainBottomIndex[0] + i; 
    }

    float[][] domainSubsetVals = origDomainSet.indexToValue(domainIndexRange);
    Irregular1DSet newDomainSet = new Irregular1DSet(origDomainType, domainSubsetVals);

    FlatField subsetFF = (FlatField) origFF.resample(newDomainSet);

    return subsetFF;
  }

  public void setBeamPaths( FlatField[][] ff )
  { beamPathSet = ff; }

  public FlatField[][] getBeamPaths()
  { return beamPathSet; }

  public void setHeightUnderestimateVals( FlatField hu )
  { heightUnderestimate = hu; }

  public FlatField getHeightUnderestimateVals()
  { return heightUnderestimate; }

  public void setElevationAngles(double[] eA)
  { 
    int NUM = eA.length;
    ELEVATION_ANGLES = new double[NUM];
    System.arraycopy(eA, 0, ELEVATION_ANGLES, 0, NUM);
  }

  public double[] getElevationAngles()
  { return ELEVATION_ANGLES; }
    
  public void vcpUpdate(String elevationAngles) throws VisADException, RemoteException
  {
    setElevationAngles(  getElevationAngleDoubles(elevationAngles) );
    calcHeightUnderestimateFill();
  }


/*
  public static void main(String[] args) throws VisADException, RemoteException
  {
    JFrame frame = new JFrame("First VisAd Example");

    String vcpEls11 = new String("19.50,16.70,14.00,12.00,10.00,08.70,07.50,06.20,05.25,04.30,03.35,02.40,01.45,00.50");
    String vcpEls12 = new String("19.50,15.60,12.50,10.00,08.00,06.40,05.10,04.00,03.10,02.40,01.80,01.30,00.90,00.50");
    String vcpEls21 = new String("19.50,14.60,09.00,06.00,04.30,03.35,02.40,01.45,00.50");
    String vcpEls31 = new String("04.50,03.50,02.50,01.50,00.50");
    String vcpEls32 = new String("04.50,03.50,02.50,01.50,00.50");

    double re = 427.0;

    HeightUnderEstimate me = new HeightUnderEstimate(vcpEls21, re);

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.getContentPane().add(me);
    frame.pack();
    frame.setVisible(true);
  }
*/

  }


