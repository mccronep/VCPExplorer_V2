package VCPExplorer;


import java.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.rmi.RemoteException;

import visad.*;

public class TerrainBlockage
{

  static final private int NUM_RAYS = 7200;
  static final private float ANGULAR_RESOLUTION = (float) (360.0 / NUM_RAYS);
  static final private float GROUND = 0.1f;  // Min elevation angle - from WDSSII
  static final private float MIN_HT_ABOVE_TERRAIN = 150.0f; // meters

  static final private double INDEX_OF_REFRACTION = (4.0/3.0);
  static final private double EARTH_RADIUS = 6378; // km at Equator
  static final private double RADIANS = (Math.PI/180);
  static final private double EFFECTIVE_EARTH_RADIUS = INDEX_OF_REFRACTION * EARTH_RADIUS;

  private float E_ANGLE, radarLat, radarLon, radarAlt;
  final float MAX_RANGE;


  private FlatField inputTerrain_ff;
  private RealTuple inputRadarLocation;

  Rays rays;


  /**
   *  Constructor that opens a cached file containing pre-calculated elevation angles at
   *  each terrain point
   */
  public TerrainBlockage(String terrainPath) throws VisADException, RemoteException
  {

    MAX_RANGE = -999.0f;

    FieldImpl rays_ff = null;

    try
    { rays_ff = readGZipFile(terrainPath); }
    catch (Exception ioe)
    { System.err.println("TerrainBlockage (readGZipFile): " + ioe); }

/*
    visad.data.visad.VisADForm input = new visad.data.visad.VisADForm(true);

    try
    {

      rays_ff = (FieldImpl) input.open(terrainPath);
    }
    catch (Exception ioe)
    { System.err.println("TerrainBlockage (170): " + ioe); }
*/




    Integer1DSet raysDomainSet = (Integer1DSet)rays_ff.getDomainSet();
    int numRays = raysDomainSet.getLengthX();
    rays = new Rays(NUM_RAYS);

    FlatField[] rayBlockage = new FlatField[numRays];
    for (int k = 0; k < numRays; ++k)
    {
      rayBlockage[k] = (FlatField) rays_ff.getSample(k);

      if (rayBlockage[k].getDomainSet() instanceof Integer1DSet)
      {
        Integer1DSet rayBlockageDomainSet = (Integer1DSet) rayBlockage[k].getDomainSet();
        int numRayBlockers = rayBlockageDomainSet.getLengthX();

        RayBlockage new_rb = new RayBlockage();

        RealTuple[] pointBlockersTuple = new RealTuple[numRayBlockers];
        for (int m = 0; m < numRayBlockers; ++m)
        {
          pointBlockersTuple[m] = (RealTuple) rayBlockage[k].getSample(m);

          float pbAzComp = (float) ( (Real)pointBlockersTuple[m].getComponent(0) ).getValue();
          float pbRanComp = (float) ( (Real)pointBlockersTuple[m].getComponent(1) ).getValue();
          float pbElevAngleComp = (float) ( (Real)pointBlockersTuple[m].getComponent(2) ).getValue();
          int pbMinRayNumComp = (int) ( (Real)pointBlockersTuple[m].getComponent(3) ).getValue();
          int pbMaxRayNumComp = (int) ( (Real)pointBlockersTuple[m].getComponent(4) ).getValue();

          PointBlockage new_pb = new PointBlockage();
          new_pb.az = pbAzComp;
          new_pb.ran = pbRanComp;
          new_pb.elevAngle = pbElevAngleComp;
          new_pb.minRayNumber = pbMinRayNumComp;
          new_pb.maxRayNumber = pbMaxRayNumComp;

          new_rb.push_back(new_pb);
          new_pb = null;

        } // end for

        rays.add(k, new_rb);

      }  // end if
    } // end for

  }


  /**
   * Constructor for calculating elevation angles at each terrain point in terrain_ff
   */
  public TerrainBlockage(FlatField terrain_ff, RealTuple radarLoation, float maxRan)
                                                     throws VisADException, RemoteException
  {
    inputTerrain_ff = terrain_ff;
    inputRadarLocation = radarLoation;
    MAX_RANGE = maxRan;

    rays = new Rays(NUM_RAYS);
    TerrainPoints terrainPoints0 = new TerrainPoints();
    TerrainPoints terrainPoints1 =  computeTerrainPoints( inputTerrain_ff , terrainPoints0);
    computeBeamBlockage(terrainPoints1);

  }
  public void addPointBlockage(float startAzimuth, float endAzimuth, float startRange, 
                                                                      float maxElevation)
  {
    PointBlockage newPoint = new PointBlockage();
    newPoint.az = (float) ((endAzimuth + startAzimuth) / 2.0f);
    newPoint.elevAngle = maxElevation;
    newPoint.ran = startRange;
    newPoint.minRayNumber = newPoint.getRayNumber(startAzimuth);
    newPoint.maxRayNumber = newPoint.getRayNumber(endAzimuth);

    rays.setSize(newPoint.maxRayNumber - newPoint.minRayNumber);
    for (int h = newPoint.minRayNumber; h < newPoint.maxRayNumber; h++)
    { 
      RayBlockage rb = new RayBlockage();
      rb.push_back(newPoint); 
      rays.add(h, rb);
      rays.setElementAt(  pruneRayBlockage( (RayBlockage)rays.get(h)  ), h );
    }
  }

  public void computeBeamBlockage(TerrainPoints terrainPoints)
  {

    for (int h = 0; h < rays.size(); h++)
    {
      float az = (float) (h * ANGULAR_RESOLUTION);
      PointBlockage pb = new PointBlockage();
      int rayno = pb.getRayNumber( az );

      for (int g = 0; g < terrainPoints.size(); g++)
      {
        if ( ((PointBlockage)terrainPoints.get(g)).contains(rayno) )
        { ((RayBlockage) rays.get(h)).add( (PointBlockage)terrainPoints.get(g) ); }
      }

      if ( ((RayBlockage) rays.get(h)).size() > 0 )
      {  rays.setElementAt( pruneRayBlockage((RayBlockage)rays.get(h)), h); }
    }
  }

  public RayBlockage pruneRayBlockage( RayBlockage orig )
  {
    
    /** First, sort the array in ascending order */

    // ### I pieced this together from examples on the web and trial/error
    Collections.sort(orig, new Comparator()
    {
      public int compare(Object o1, Object o2)
      { 
        PointBlockage pb1 = (PointBlockage) o1;
        PointBlockage pb2 = (PointBlockage) o2;

//System.out.println("az1: " + pb1.az + ", ran1: " + pb1.ran + ", E;: " + pb1.elevAngle);

        return ( Math.round(pb1.ran) - Math.round(pb2.ran) );
      }
    });

    RayBlockage result = new RayBlockage();
    float max_so_far = -999.0f;

    for (int h = 0; h < orig.size(); h++)
    {
      if ( ((PointBlockage)orig.get(h)).elevAngle > max_so_far )
      {
//System.out.println("az1: " + pb1.az + ", ran1: " + pb1.ran + ", E;: " + pb1.elevAngle);
        max_so_far = ((PointBlockage)orig.get(h)).elevAngle; 
        result.addElement( ((PointBlockage)orig.get(h)) );
      }
    }
    /** now "swap" orig and result - OR just return result? */
    return result;
  }


  public TerrainPoints computeTerrainPoints(FlatField inTerrain_ff, TerrainPoints terrainPoints) throws VisADException, RemoteException
  {
    Real[] radarLoc = inputRadarLocation.getRealComponents();
    radarLat = (float) radarLoc[0].getValue();
    radarLon = (float) radarLoc[1].getValue();
    radarAlt = (float) radarLoc[2].getValue();

    Gridded2DSet domainSet = (Gridded2DSet) inTerrain_ff.getDomainSet();
    float[][] lonLatVals = domainSet.getSamples(true);
    float[] altVals = inTerrain_ff.getFloats(true)[0];

    float[] lonLatMaxVals = domainSet.getHi();
    float lonMaxVal = lonLatMaxVals[0];
    float latMaxVal = lonLatMaxVals[1];

    float[] lonLatMinVals = domainSet.getLow();
    float lonMinVal = lonLatMinVals[0];
    float latMinVal = lonLatMinVals[1];

    int[] lonLatLength = domainSet.getLengths();
    int lonLength = lonLatLength[0];
    int latLength = lonLatLength[1];
 

    float[][] azimuths = new float[latLength][lonLength];

    Vector block_i = new Vector();
    Vector block_j = new Vector();
    Vector etch = new Vector();
    
    int i = 0, j = 0;

    for (int h = 0; h < (lonLength*latLength); ++h)
    {

        PointBlockage block = new PointBlockage();

        double[] elangleAzRan = CoordConversion.LLHtoAzRangeElev(
                    lonLatVals[1][h], lonLatVals[0][h], altVals[h],
                    radarLat, radarLon, radarAlt
                    );

        block.elevAngle = (float) elangleAzRan[0];
        block.az = (float) elangleAzRan[1];
        block.ran = (float) elangleAzRan[2];

        azimuths[i][j] = (float) elangleAzRan[1];



//        if ( block.elevAngle > GROUND && block.ran < MAX_RANGE )
        if ( block.ran < MAX_RANGE )
        {
          terrainPoints.push_back( block );

          block_i.add( new Integer(i) );
          block_j.add( new Integer(j) );

        }

      // ### This is to simulate the grid dimensions of the WDSSII TB
      j++;
      if (h !=0)
      {
        if (lonLatVals[0][h] == lonMaxVal)
        { 
          i++;
          j = 0;
        }
      }

    }


    for (int h = 0; h < terrainPoints.size(); h++)
    {
      PointBlockage pb = (PointBlockage)terrainPoints.get(h);
      pb.setAzimuthalSpread( azimuths, 
             ((Integer)block_i.get(h)).intValue(), ((Integer)block_j.get(h)).intValue() );
      terrainPoints.setElementAt(pb, h);
    }

    return terrainPoints;
  }



  class PointBlockage
  {
    public float az;
    public float ran;
    public float elevAngle;
    public int minRayNumber;
    public int maxRayNumber;


    public boolean contains(int ray_no)
    { return ( ray_no >= minRayNumber && ray_no <= maxRayNumber ); }

    public void setAzimuthalSpread(float[][] azimuths, int x, int y)
    {
      float max_diff = 0.0f;

      for (int i=x-1; i <= (x+1); ++i) for (int j=y-1; j <= (y+1); ++j)
      {
        // consider only the 4-neighborhood
        if ( (i==x || j==y) && i >= 0 && j >= 0 && i < azimuths.length && j < azimuths[0].length) 
        {
          float diff1 = Math.abs( (float) (azimuths[i][j] - azimuths[x][y]) );      
          // this handles the case of 359 to 1 -- diff should be 2 degrees
          float diff2 = Math.abs( (float) (360.0f - diff1) );
          float diff = Math.min( diff1, diff2 );
  
          if ( diff > max_diff )
          { max_diff = diff; }
        }
      }
      float azimuthalSpread = (float)(max_diff/2.0f);
      minRayNumber = getRayNumber(azimuths[x][y] - azimuthalSpread);
      maxRayNumber = getRayNumber(azimuths[x][y] + azimuthalSpread);
    }

    public int getRayNumber(float start)
    {
      if ( start < 0.0f ) 
      { start += 360.0f; }
      if ( start >= 360.0f ) 
      { start -= 360.0f; }
      int ray_no = (int) ( 0.5f + start/ANGULAR_RESOLUTION );
      if ( ray_no < 0.0f ) 
      { return 0; }
      if ( ray_no >= (int) (NUM_RAYS) ) 
      { return (NUM_RAYS - 1); }
      return ray_no;
    }


  }

  class RayBlockage extends Vector
  {
    public RayBlockage()
    {}

    public RayBlockage(int m)
    { setSize(m); }

    void push_back( PointBlockage p )
    {
      addElement( p );
    }
  }

  class TerrainPoints extends Vector
  {

    public void push_back( PointBlockage p )
    {
      add( p );
    }
  }

  class Rays extends RayBlockage
  {
    public Rays(int num)
    {
      for (int i = 0; i < num; i++)
      { push_back( new RayBlockage() ); }
    }


    void push_back( RayBlockage rb )
    {
      addElement( rb );
    }
  }



  public float getHeightAboveTerrain(float elev, float az, float rn)
  {
    double[] llh = CoordConversion.AzRangetoLL(radarLat, radarLon, az, rn, elev);
    float ht = (float) (llh[2] + radarAlt);
    float result = ht;

    double[] lonLatVal = new double[] { radarLon, radarLat };
    float terrainHt = Float.NaN;
    try
    {
      RealTupleType lonLat_tt = new RealTupleType( RealType.Longitude, RealType.Latitude );
      SingletonSet ss = new SingletonSet(lonLat_tt, lonLatVal, null, null, null);
      terrainHt = inputTerrain_ff.resample(ss).getFloats()[0][0];
    }
    catch (VisADException ve)
    { System.out.println("::TB VisADException> " + ve + "::\n"); }
    catch (RemoteException re)
    { System.out.println("::TB RemoteException> " + re + "::\n"); }

    if ( Float.compare(terrainHt, Float.NaN) != 0 )
    { result = result - terrainHt; }

    return result;
  }



  public int computePercentBlocked(float beamWidth, float beamElevation,
                                         float beamAzimuth, float range)
  {
    float blocked = computeFractionBlocked(beamWidth,beamElevation,
                                                  beamAzimuth, range);

//System.out.println("Fblocked: " + blocked);

    int result = (int) (0.5 + blocked*100);
    if ( result < 0 ) result = 0;
    if ( result > 100 ) result = 100;
    return result;

  }


  public float computeFractionBlocked(float beamWidth, float beamElevation,
                                         float beamAzimuth, float range)
  {
    float bottom = (float) (beamElevation - 0.5f * beamWidth);
    float top = (float) (beamElevation + 0.5f * beamWidth);
/*
    float ht = getHeightAboveTerrain(bottom,beamAzimuth, range);
    if ( ht < MIN_HT_ABOVE_TERRAIN )
    { return 1.0f; }
*/
    float minAz = (float) (beamAzimuth - 0.5f * beamWidth);
    float maxAz = (float) (beamAzimuth + 0.5f * beamWidth);

    PointBlockage dummyBlock = new PointBlockage();
    int startRay = dummyBlock.getRayNumber( minAz );
    int endRay = dummyBlock.getRayNumber( maxAz );

    if ( endRay < startRay )
    { endRay += NUM_RAYS; }// unwrap

    final int RESOLUTION = (endRay - startRay) + 1;
    float[] passed = new float[RESOLUTION];
    Arrays.fill(passed, 1.0f);

    for (int ray = startRay; ray <= endRay; ray++)
    {
      int rayno = ray % NUM_RAYS; // re-wrap
      // consider all the blockers along this ray

      final RayBlockage blockers = (RayBlockage)rays.get(rayno);
      int num_blockers = blockers.size();
      for (int h = 0; h < num_blockers; h++)
      {
        final PointBlockage block = (PointBlockage) blockers.get(h);
        // then find the minimum passed through them at this range & elev

//System.out.println(beamAzimuth + "/" + range + "): Bottom: " + bottom + ", Eangle: " + block.elevAngle);

        if ( range > block.ran && block.elevAngle > bottom )
        {
          // fraction passed ...
          float vert_factor = ( top - block.elevAngle  )/beamWidth;
          if ( vert_factor < 0 )
          { vert_factor = 0; }
          int which_bin = ray - startRay;
          // minimum passed by terrain along path
          if ( vert_factor < passed[which_bin] )
          { passed[which_bin] = vert_factor; }
        }
      }
    }

    float avg_passed = findAveragePassed(passed);

    float blocked = 1 - avg_passed;
    return blocked;
  }


  public float findAveragePassed(float[] passed)
  {
    float sum_passed = 0.0f;
    float sum_wt = 0.0f;
    int N = passed.length;
    float halfN = (float) (0.5f * N);
    for (int i=0; i < N; ++i)
    {
      float dist = (float) (i + 0.5 - halfN) / N; // -1/2 to 1/2
      float wt = (float) power_density( dist );
      sum_passed += passed[i] * wt;
      sum_wt += wt;
    }
  return (sum_passed / sum_wt);
  }

  public float power_density(float dist)
  {
    // eq. 3.2a of Doviak-Zrnic gives the power density function
    //   theta -- angular distance from the beam axis = dist * beamwidth
    //   sin(theta) is approx theta = dist*beamwidth
    //   lambda (eq.3.2b) = beamwidth*D / 1.27
    //   thus, Dsin(theta)/lambda = D * dist * beamwidth / (D*beamwidth/1.27)
    //                            = 1.27 * dist
    //   and the weight is ( J_2(pi*1.27*dist)/(pi*1.27*dist)^2 )^2
    //      dist is between -1/2 and 1/2, so abs(pi*1.27*dist) < 2
    //   in this range, J_2(x) is approximately 1 - exp(-x^2/8.5)
    float x = (float) (Math.PI * 1.27 * dist);
    if ( x < 0.01f ) 
    { x = 0.01f; } // avoid divide-by-zero
    float wt = (float) ((1 - Math.exp( -x*x / 8.5 )) / (x*x));
    wt = (float) wt*wt;
    return (wt);
  }


  /** Set the elevation angle in degrees */
  public void setElevationAngle(float ea)
  { E_ANGLE = ea; }

  /** Get the elevation angle in degrees (true) or radians (false) */
  public float getElevationAngle(boolean deg)
  {
    if (deg)
    { return E_ANGLE; }
    else
    { return (float) (E_ANGLE * RADIANS); }
  }

  public static FieldImpl readGZipFile(String filename)
    throws IOException, VisADException
  {
    visad.data.visad.BinaryReader reader =  new visad.data.visad.BinaryReader(
        new GZIPInputStream(new FileInputStream(filename)));

    return (FieldImpl) reader.getData();
  }



}


