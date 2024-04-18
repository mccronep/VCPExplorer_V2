package VCPExplorer;


import java.*;
import java.util.Vector;
import java.rmi.RemoteException;

import visad.*;



public class Algorithms
{
  private Unit kFt, kilometer, km, nm;
  private double zeroC = -1;
  private double mTwentyC = -1;
  private FlatField zProfile;
  private FlatField[] vcp;
  private RealType height, range, ref;
  private final Real radarHeight;
  private float[][] heightVals, dbzVals, ZeVals, capZeVals;
  private float[] shi, posh, mesh, poh, vil, vilDensity;
  private FlatField tSHI_ff, tPOSH_ff, tMESH_ff, tVIL_ff, 
                    tVILDensity_ff, tEchoTops_ff,
                    sSHI_ff, sPOSH_ff, sMESH_ff, sVIL_ff, 
                    sVILDensity_ff, sEchoTops_ff;
  private Integer1DSet radarRangeSet;

  final static int RESOLUTION = 400;
  final static float BEAMWIDTH = 0.5f;
  final static double ZE_CAP = Math.pow(10,(56/10));

/*
  public Algorithms() throws VisADException, RemoteException
  {}
*/
  public Algorithms( FlatField[] v, FlatField zProf, double[] freezeLevels, 
                     float radarHt ) 
                                    throws VisADException, RemoteException
  {
    try
    {
      km = visad.data.units.Parser.parse("kilometer").clone("km");
      nm = visad.data.units.Parser.parse("nautical_mile").clone("nm");
      kFt = visad.data.units.Parser.parse("kilofeet").clone("kFt");
    }
    catch (visad.data.units.ParseException P)
    { System.out.println("Parse exception: " + P); }

    // ### initialize vars
    height = RealType.getRealType("Height", km);
    range = RealType.getRealType("Range", km);
    ref = RealType.getRealType("ref");
    radarHeight = new Real(height, radarHt/1000, km);


    // ### Make everything local and private
    vcp = new FlatField[v.length];
    for (int i = 0; i < v.length; ++i)
    {
      vcp[i] = (FlatField) v[i].clone();
    }

    zProfile = (FlatField) zProf.clone();
    zeroC = freezeLevels[0]*1000.0f;
    mTwentyC = freezeLevels[1]*1000.0f;
    radarRangeSet = new Integer1DSet(range, RESOLUTION);

    // ### Process incoming data to get DBz values for each elevation scan
    // ### at each range point.  (range -> height -> DBz)
    // ### Situation is as follows:
    // ###  loop through range (0-400km) and evaluate (or resample)
    // ###  vcp to get heights at each range point.
    // ###  use vcp heights to get DBz values from zProfile
    // ###  use DBz values and freezing heights to plug into algs
    int NUM_ELEVATIONS = vcp.length;
    Vector heightVect;
    Vector dbzVect = new Vector();
    heightVals = new float[RESOLUTION][];
    dbzVals = new float[RESOLUTION][];
    ZeVals = new float[RESOLUTION][];
    capZeVals = new float[RESOLUTION][];


    // ### along range
    for (int i = 0; i < RESOLUTION; ++i)
    {
      heightVect = new Vector();

      // ### for each volume scan
      for (int j = NUM_ELEVATIONS-1; j >= 0; --j)
      {
        Real rnReal = new Real(range, i, km);
        Real htReal = (Real) vcp[j].evaluate(rnReal);
        float htVal_km = (float) htReal.getValue();

        if (Float.compare(htVal_km, Float.NaN) != 0)
        { 
          heightVect.add(new Float(htVal_km)) ; 
        } // ### end if
      }  // ### end j loop

        int N = heightVect.size();
        heightVals[i] = new float[N];
        dbzVals[i] = new float[N];
        ZeVals[i] = new float[N];
        capZeVals[i] = new float[N];
        Float[] tempHtFloats = (Float[])heightVect.toArray(
                                          new Float[heightVect.size()]);
        for (int k = 0; k < N; ++k)
        { 
          float tmpHtFloat = tempHtFloats[k].floatValue();
          Real tmpHtReal = new Real(height, tmpHtFloat, km);

          heightVals[i][k] = tmpHtFloat;
          dbzVals[i][k] = (float)
                            ((Real)zProfile.evaluate(tmpHtReal)).getValue();

          // ### used for VIL calc - from WDTB
          ZeVals[i][k] = (float) Math.pow(10,(dbzVals[i][k]/10)); 
/*
System.out.println("DBz: " + dbzVals[i][k]
                   + ", Ze: " + ZeVals[i][k]
                   + ", ZE_CAP: " + ZE_CAP
                  );
*/
          if (ZeVals[i][k] <= (float)ZE_CAP)
          { capZeVals[i][k] = ZeVals[i][k]; }
          else
          { capZeVals[i][k] = (float)ZE_CAP; }
          
        } // ### end k loop
    } // ### end i loop

    computeTheoreticalValues();
    computeSHI();
/*
    computeTheoreticalVIL();
    computeVIL();
*/
  }

  public float computeSHI() throws VisADException, RemoteException
  {
    float zL = 40;  // ### These values are taken from PolarHail::computeSHI
    float zU = 50;
    double wt = 57.5 * (zeroC/1000) - 121; // ### in km for some reason
    if (wt < 20)
    { wt = 20; }

    shi = new float[RESOLUTION];
    posh = new float[RESOLUTION];
    mesh = new float[RESOLUTION];
                           
    /** Need to define refValue, hCurrent and hBelow */

    // ### Start "range" loop
    for (int i = 0; i < RESOLUTION; ++i)
    {
      double wz = 0.0;
      double wh = 0.0;
      double integration = 0.0;

      // ### Start "elevationAngle" loop
      for (int j = 0; j < dbzVals[i].length; ++j)
      {

        float refValue = dbzVals[i][j];
        float hCurrent = (heightVals[i][j]*1000.0f);

        if (refValue <= zL)
        { wz = 0; }
        else if (refValue >= zU)
        { wz = 1; }
        else
        {
          wz = (refValue - zL)/(zU - zL);
        }

        double E = 5 * (Math.pow(10.0,-6.0)) * 
                       (wz * Math.pow(10.0, (0.084*refValue))) ;
    
        if (hCurrent <= zeroC)
        wh = 0;
        else if (zeroC < hCurrent && hCurrent < mTwentyC)
        wh = (hCurrent - zeroC)/
          (mTwentyC - zeroC);
        else if (hCurrent >= mTwentyC)
        wh = 1;
        else
        System.err.println(" Error!!!! No value for wh \n");
    
        double dh = 0.1;

        if (j == 0)
        { dh = hCurrent; }
        else
        { 
          double hBelow = (heightVals[i][j-1]*1000.0f);
          dh = hCurrent - hBelow;
        }
    
        integration  = integration + E*wh*dh;
      } // ### End elevationAngle loop

      // ### SHI for range i
      shi[i] = (float)(0.1*integration);

      // ### POSH for range i
      posh[i] = 0;
      double poshTmp = ((29 * Math.log( (shi[i] / wt) )) + 50);
      if (poshTmp <= 0.0)
      { poshTmp = 0.0; }
      if (poshTmp >= 100.0)
      { poshTmp = 100.0; }

      posh[i] = (float) poshTmp;

      // ### MESH (or MEHS) for range i
      mesh[i] = (float)(2.54 * Math.pow(shi[i], 0.5));

      // ### Make and set FlatFields for alg values
      float[][] shiRangeVals = new float[][] { shi };
      float[][] poshRangeVals = new float[][] { posh };
      float[][] meshRangeVals = new float[][] { mesh };

      RealType shiType = RealType.getRealType("shiType");
      RealType poshType = RealType.getRealType("poshType");
      RealType meshType = RealType.getRealType("meshType");

      FunctionType shiFunc = new FunctionType(range, shiType);
      FunctionType poshFunc = new FunctionType(range, poshType);
      FunctionType meshFunc = new FunctionType(range, meshType);

      FlatField shiFF = new FlatField(shiFunc, radarRangeSet);
      FlatField poshFF = new FlatField(poshFunc, radarRangeSet);
      FlatField meshFF = new FlatField(meshFunc, radarRangeSet);

      shiFF.setSamples(shiRangeVals);
      poshFF.setSamples(poshRangeVals);
      meshFF.setSamples(meshRangeVals);

      setSampledSHI(shiFF);
      setSampledPOSH(poshFF);
      setSampledMESH(meshFF);


    } // ### end i loop



    return -1.0f;

  }

  public void computeTheoreticalValues() 
                  throws VisADException, RemoteException
  {
    float zL = 40;  // ### These values are taken from PolarHail::computeSHI
    float zU = 50;
    double wt = 57.5 * (zeroC/1000) - 121 ; // ### in km for some reason
    if (wt < 20)
    { wt = 20; }

    float t_shi ;
    float t_posh ;
    float t_mesh ;

    float[] hiT = ((Irregular1DSet)zProfile.getDomainSet()).getHi();
    float[] lowT = ((Irregular1DSet)zProfile.getDomainSet()).getLow();

    float hi = (float)(hiT[0]*1000.0f);
    float low = (float)(lowT[0]*1000.0f);

    Linear1DSet newDomainSet = new Linear1DSet(height, lowT[0], hiT[0], 1000);

    float[][] domainVals = newDomainSet.getSamples(true);
    float[][] rangeVals = (zProfile.resample(newDomainSet)).getFloats();

    float[] t_heightVals = domainVals[0];
    float[] t_dbzVals = rangeVals[0];

      double wz = 0.0;
      double wh = 0.0;
      double integration = 0.0;

      for (int j = 0; j < t_dbzVals.length; ++j)
      {

        float refValue = t_dbzVals[j];
        float hCurrent = (t_heightVals[j]*1000.0f);


        if (refValue <= zL)
        { wz = 0; }
        else if (refValue >= zU)
        { wz = 1; }
        else
        {
          wz = (refValue - zL)/(zU - zL);
        }

        double E = 5 * (Math.pow(10.0,-6.0)) * 
                       (wz * Math.pow(10.0, (0.084*refValue))) ;
    
        if (hCurrent <= zeroC)
        wh = 0;
        else if (zeroC < hCurrent && hCurrent < mTwentyC)
        wh = (hCurrent - zeroC)/
          (mTwentyC - zeroC);
        else if (hCurrent >= mTwentyC)
        wh = 1;
        else
        System.err.println(" Error!!!! No value for wh \n");
    
        double dh = 0.1;

        if (j == 0)
        { dh = hCurrent; }
        else
        { 
          double hBelow = (t_heightVals[j-1]*1000.0f);
          dh = hCurrent - hBelow;
        }
    
        integration  = integration + E*wh*dh;
      } // ### end j loop


      // ### SHI 
      t_shi = (float)(0.1*integration);

      // ### POSH 
      t_posh = 0;
      double t_poshTmp = ((29 * Math.log( (t_shi / wt)) ) + 50);
      if (t_poshTmp <= 0.0)
      { t_poshTmp = 0.0; }
      if (t_poshTmp >= 100.0)
      { t_poshTmp = 100.0; }

      t_posh = (float) t_poshTmp;

      // ### MESH (or MEHS)
      t_mesh = (float)(2.54 * Math.pow(t_shi, 0.5));

      // ### Make and set FlatFields for alg values
      float[][] shiRangeVals = new float[1][RESOLUTION];
      float[][] poshRangeVals = new float[1][RESOLUTION];
      float[][] meshRangeVals = new float[1][RESOLUTION];

      java.util.Arrays.fill(shiRangeVals[0], t_shi);
      java.util.Arrays.fill(poshRangeVals[0], t_posh);
      java.util.Arrays.fill(meshRangeVals[0], t_mesh);

      RealType shiType = RealType.getRealType("shiType");
      RealType poshType = RealType.getRealType("poshType");
      RealType meshType = RealType.getRealType("meshType");

      FunctionType shiFunc = new FunctionType(range, shiType);
      FunctionType poshFunc = new FunctionType(range, poshType);
      FunctionType meshFunc = new FunctionType(range, meshType);

      FlatField shiFF = new FlatField(shiFunc, radarRangeSet);
      FlatField poshFF = new FlatField(poshFunc, radarRangeSet);
      FlatField meshFF = new FlatField(meshFunc, radarRangeSet);

      shiFF.setSamples(shiRangeVals);
      poshFF.setSamples(poshRangeVals);
      meshFF.setSamples(meshRangeVals);

      setTheoreticalSHI(shiFF);
      setTheoreticalPOSH(poshFF);
      setTheoreticalMESH(meshFF);


  } // ### end computeTheoreticalValues

  public void computeVIL() throws VisADException, RemoteException
  {
    vil = new float[RESOLUTION];
    vilDensity = new float[RESOLUTION];


    // ### Start "range" loop
    for (int i = 0; i < RESOLUTION; ++i)
    {
      double vilIntegration = 0.0;
      double vilDensityIntegration = 0.0;

      // ### Start "elevationAngle" loop
      for (int j = 0; j < dbzVals[i].length; ++j)
      {
        float hCurrent = (heightVals[i][j]*1000.0f);
        float ze = ZeVals[i][j];
//        float capZe = capZeVals[i][j];

        float hBelow;
        double currentVil;

        if (dbzVals[i][j] < (float) 0)
        { continue; }

        if (j == 0)
        { 
          hBelow = hCurrent; 
          currentVil = 0.0;
        }
        else
        {
          double firstTerm = ((capZeVals[i][j-1] + capZeVals[i][j])/2.0);
          double secondTerm = (4.0/7.0);
          hBelow = (heightVals[i][j-1]*1000.0f);
          currentVil = (0.00000344)*
                       (Math.pow(firstTerm, secondTerm)*
                       (hCurrent - hBelow));

          vilIntegration = vilIntegration + currentVil;
          vilDensityIntegration = //vilDensityIntegration + 
                                  (vilIntegration*1000)/hCurrent;
/*
System.out.println(i + "/" + j + ")" 
                   + "\tvil: " + currentVil
                   + ", Integ: " + vilIntegration + "\n"
                   + "\t\t Density: " + ((vilIntegration*1000)/hCurrent)
                   + ", dInteg: " + vilDensityIntegration + "\n"
                   + "\t\t capZe: " + capZeVals[i][j] 
                   + ", capZe-1: " + capZeVals[i][j-1]
                   );
*/
        }

                                                                             
      } // ### end j loop
      vil[i] = (float) vilIntegration;
      vilDensity[i] = (float) vilDensityIntegration;

    } // ### end i loop

      // ### Make and set FlatFields for alg values
      float[][] vilRangeVals = new float[][] { vil };
      float[][] vilDensityRangeVals = new float[][] { vilDensity };

      RealType vilType = RealType.getRealType("vilType");
      RealType vilDensityType = RealType.getRealType("vilDensityType");

      FunctionType vilFunc = new FunctionType(range, vilType);
      FunctionType vilDensityFunc = new FunctionType(range, vilDensityType);

      FlatField vilFF = new FlatField(vilFunc, radarRangeSet);
      FlatField vilDensityFF = new FlatField(vilDensityFunc, radarRangeSet);

      vilFF.setSamples(vilRangeVals);
      vilDensityFF.setSamples(vilDensityRangeVals);

      setSampledVIL(vilFF);
      setSampledVILDensity(vilDensityFF);


  } // end computeVIL

  public void computeTheoreticalVIL() throws VisADException, RemoteException
  {
    float t_vil ;
    float t_vilDensity ;

    float[] hiT = ((Irregular1DSet)zProfile.getDomainSet()).getHi();
    float[] lowT = ((Irregular1DSet)zProfile.getDomainSet()).getLow();

    float hi = (float)(hiT[0]*1000.0f);
    float low = (float)(lowT[0]*1000.0f);

    Linear1DSet newDomainSet = new Linear1DSet(height, lowT[0], hiT[0], 1000);

    float[][] domainVals = newDomainSet.getSamples(true);
    float[][] rangeVals = (zProfile.resample(newDomainSet)).getFloats();

    float[] t_heightVals = domainVals[0];
    float[] t_dbzVals = rangeVals[0];

    double t_curVIL = 0.0;
    double t_curVILDensity = 0.0;

      for (int j = 0; j < t_dbzVals.length; ++j)
      {

        float hCurrent = (t_heightVals[j]*1000.0f);
        float hBelow;

        if (j == 0)
        { continue; }
        else
        {
          if (t_dbzVals[j] < (float) 0)
          { break; }

          hBelow = (t_heightVals[j-1]*1000.0f);
          // ### used for VIL calc - from WDTB
          double zeVal_0 =  Math.pow(10,(t_dbzVals[j-1]/10)); 
          double zeVal_1 =  Math.pow(10,(t_dbzVals[j]/10)); 
          double capZeVal_0, capZeVal_1;

          if (zeVal_0 <= ZE_CAP)
          { capZeVal_0 = zeVal_0; }
          else
          { capZeVal_0 = ZE_CAP; }

          if (zeVal_1 <= ZE_CAP)
          { capZeVal_1 = zeVal_1; }
          else
          { capZeVal_1 = ZE_CAP; }

          double firstTerm = (capZeVal_0 + capZeVal_1)/2;
          double secondTerm = (4.0/7.0);
          t_curVIL = t_curVIL + (0.00000344)*
                            Math.pow(firstTerm, secondTerm)*
                            (hCurrent - hBelow);

          t_curVILDensity = t_curVIL*1000.0/hCurrent;
/*
System.out.println("tVIL: " + t_curVIL + "\t, tVILd: " + t_curVILDensity
                   + "\n\tHcur: " + hCurrent + ", Hbel: " + hBelow
                   + "\n\tcapZeVal_0: " + capZeVal_0 + ",capZeVal_1 : " + capZeVal_1
                   + "\n\tFirst: " + firstTerm + ", Second: " + secondTerm
//                   + "\n\tPOW: " + Math.pow(((capZeVal_0 + capZeVal_1)/2),(4/7))             
                   + "\n\tPOW: " + Math.pow(firstTerm, secondTerm)
                  );
*/
        } // ### end else
      } // ### end j loop


      // ### Make and set FlatFields for alg values
      float[][] vilRangeVals = new float[1][RESOLUTION];
      float[][] vilDensityRangeVals = new float[1][RESOLUTION];

      java.util.Arrays.fill(vilRangeVals[0], (float)t_curVIL);
      java.util.Arrays.fill(vilDensityRangeVals[0], (float)t_curVILDensity);

      RealType vilType = RealType.getRealType("vilType");
      RealType vilDensityType = RealType.getRealType("vilDensityType");

      FunctionType vilFunc = new FunctionType(range, vilType);
      FunctionType vilDensityFunc = new FunctionType(range, vilDensityType);

      FlatField vilFF = new FlatField(vilFunc, radarRangeSet);
      FlatField vilDensityFF = new FlatField(vilDensityFunc, radarRangeSet);

      vilFF.setSamples(vilRangeVals);
      vilDensityFF.setSamples(vilDensityRangeVals);

      setTheoreticalVIL(vilFF);
      setTheoreticalVILDensity(vilDensityFF);

      



  } // end computeTheoreticalVIL

/*
  public double computeMidbeamSines(double angle)
  {
    return Math.sin( (angle + 0.5 * BEAMWIDTH) );
  } // ### end computeMidbeamSines

  public double heightAboveGround(double ran, double sine_angle)
  {
   double zhgt;
   double hgt;//, val;
   static final double FACTOR = (2.0*1.21*6371.0);
   hgt = (rng * sine_angle) + ((rng*rng)/FACTOR);
   zhgt = hgt*1000.0;
   return(zhgt);

  } // ### end heightAboveGround
*/

  // ### "Setters"
  public void setTheoreticalSHI(FlatField x)
  { tSHI_ff = (FlatField)x.clone(); }

  public void setTheoreticalPOSH(FlatField x)
  { tPOSH_ff = (FlatField)x.clone(); }

  public void setTheoreticalMESH(FlatField x)
  { tMESH_ff = (FlatField)x.clone(); }

  public void setTheoreticalVIL(FlatField x)
  { tVIL_ff = (FlatField)x.clone(); }

  public void setTheoreticalVILDensity(FlatField x)
  { tVILDensity_ff = (FlatField)x.clone(); }

  public void setTheoreticalEchoTops(FlatField x)
  { tEchoTops_ff = (FlatField)x.clone(); }

  public void setSampledSHI(FlatField x)
  { sSHI_ff = (FlatField)x.clone(); }

  public void setSampledPOSH(FlatField x)
  { sPOSH_ff = (FlatField)x.clone(); }

  public void setSampledMESH(FlatField x)
  { sMESH_ff = (FlatField)x.clone(); }

  public void setSampledVIL(FlatField x)
  { sVIL_ff = (FlatField)x.clone(); }

  public void setSampledVILDensity(FlatField x)
  { sVILDensity_ff = (FlatField)x.clone(); }

  public void setSampledEchoTops(FlatField x)
  { sEchoTops_ff = (FlatField)x.clone(); }

  // ### "Getters"
  public FlatField getTheoreticalSHI()
  { return tSHI_ff; }

  public FlatField getTheoreticalPOSH()
  { return tPOSH_ff; }

  public FlatField getTheoreticalMESH()
  { return tMESH_ff; }

  public FlatField getTheoreticalVIL()
  { return tVIL_ff; }

  public FlatField getTheoreticalVILDensity()
  { return tVILDensity_ff; }

  public FlatField getTheoreticalEchoTops()
  { return tEchoTops_ff; }

  public FlatField getSampledSHI()
  { return sSHI_ff; }

  public FlatField getSampledPOSH()
  { return sPOSH_ff; }

  public FlatField getSampledMESH()
  { return sMESH_ff; }

  public FlatField getSampledVIL()
  { return sVIL_ff; }

  public FlatField getSampledVILDensity()
  { return sVILDensity_ff; }

  public FlatField getSampledEchoTops()
  { return sEchoTops_ff; }



  public static void main(String[] args) 
                   throws VisADException, RemoteException
  {
//    Algorithms algs = new Algorithms();
  }


}

