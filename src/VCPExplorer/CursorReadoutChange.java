
package VCPExplorer;


import java.*;
import java.io.IOException;
import java.util.Vector;
import java.awt.*;
import java.awt.event.*;
import java.rmi.RemoteException;

import javax.swing.*;
import javax.swing.event.*;

import visad.*;
import visad.java3d.DisplayImplJ3D;
import visad.java3d.TwoDDisplayRendererJ3D;
import visad.bom.Radar2DCoordinateSystem;
import visad.data.netcdf.Plain;

public class CursorReadoutChange implements DisplayListener
{

  private DisplayRenderer dr;
  float[] radarLocation;
  final static double INDEX_OF_REFRACTION = (4.0/3.0);
  final static double EARTH_RADIUS = 6378; // km at Equator
  final static double RADIANS = (Math.PI/180);
  double EFFECTIVE_EARTH_RADIUS = INDEX_OF_REFRACTION * EARTH_RADIUS;
  private float E_ANGLE;
  private Vector cursorVect, outCursorVect;
  private Unit kilometer, nMile, kFt;
  private RealType beamAzType, beamRanType, beamHtType;
  private Real beamAz, beamRan, beamHt;


  public CursorReadoutChange(DisplayImpl display, float[] radarLoc, float rElevationAngle) 
                                  throws VisADException, RemoteException
  {
    setElevationAngle( rElevationAngle );
    radarLocation = new float[radarLoc.length];
    System.arraycopy(radarLoc, 0, radarLocation, 0, radarLoc.length);
    display.enableEvent(DisplayEvent.MOUSE_DRAGGED);
    display.addDisplayListener(this);
    dr = display.getDisplayRenderer();

    try
    {
      kilometer = visad.data.units.Parser.parse("kilometer").clone("km");
      nMile = visad.data.units.Parser.parse("nautical_mile").clone("nm");
      kFt = visad.data.units.Parser.parse("kilofeet").clone("kFt");
    }
    catch (visad.data.units.ParseException P)
    { System.out.println("Parse exception: " + P); }

    beamAzType = RealType.getRealType("beamAzType", CommonUnit.degree);
    beamRanType = RealType.getRealType("beamRanType", kilometer);
    beamHtType = RealType.getRealType("beamHtType", kilometer);



  }


  public void displayChanged(DisplayEvent e) 
  {
    InputEvent ie = e.getInputEvent();
    if(
       e.getId() == DisplayEvent.MOUSE_PRESSED_CENTER // middle button click
       || (e.getId() == DisplayEvent.MOUSE_DRAGGED &&  // middle button drag
           ie.getModifiers() == InputEvent.BUTTON2_MASK) 
       || (e.getId() == DisplayEvent.MOUSE_PRESSED &&  // 2-button (emulated middle) click
           ie.getModifiersEx() == 
          (InputEvent.BUTTON1_DOWN_MASK + InputEvent.BUTTON3_DOWN_MASK) )
       || (e.getId() == DisplayEvent.MOUSE_DRAGGED &&  // 2-button (emulated middle) drag
           ie.getModifiersEx() == 
          (InputEvent.BUTTON1_DOWN_MASK + InputEvent.BUTTON3_DOWN_MASK) )
      )
    {

//    cursorVect = dr.getCursorStringVector();
    outCursorVect = new Vector(0);
      cursorVect = dr.getCursorStringVector();
      String[] cString = (String[]) cursorVect.toArray(
                             new String[cursorVect.size()]);

      if (cString != null);
      {
        // ### Note, due to the lon/lat nature of the terrain domain
        // ## as read by visad.Plain, the order is switched

        String[] lonString = cString[0].split(" ");
        String[] latString = cString[1].split(" ");

        float latVal = Float.parseFloat(latString[2]);
        float lonVal = Float.parseFloat(lonString[3]);
        
        float[][] latLonVals = new float[][] { {latVal}, {lonVal} };
        float[][] azRanEl = null;
        
        try 
        {
          azRanEl = 
          latLonToRanAz(radarLocation[0], radarLocation[1], radarLocation[2], latLonVals);

          if (azRanEl[0] != null)
          {
  
            beamAz = new Real(beamAzType, azRanEl[1][0]);
            beamRan = new Real(beamRanType, azRanEl[0][0]/1000);
            beamHt = new Real(beamHtType, rangeToHeight(azRanEl[0][0]) );
  
            String azString = new String("Az = " + (float) beamAz.getValue() + " " + beamAz.getUnit());
            String ranString = new String("Ran = " + (float) beamRan.getValue(nMile) + " " + nMile);
            String htString = new String("BmHt = " + (float) beamHt.getValue(kFt) + " " + kFt);
            outCursorVect.add(azString);
            outCursorVect.add(ranString);
            outCursorVect.add(htString);
          }
        }
        catch (VisADException exc) 
        {  System.out.println("VisADException: " + exc); }
        catch (RemoteException exc)
        { System.out.println("RemoteException: " + exc); }
/*
          String azString = new String("Az = " + azRanEl[1][0] + " deg");
          outCursorVect.add(azString);
          String ranString = new String("Ran = " + azRanEl[0][0]/1000 + " km");
          outCursorVect.add(ranString);
          float beamHt = rangeToHeight(azRanEl[0][0]);
          String htString = new String("Ht = " + beamHt + " km");
          outCursorVect.add(htString);
*/
          dr.setCursorStringVector(outCursorVect);

      }
    }

  }

  public float[][] latLonToRanAz(float radarLat, float radarLon,
                                      float radarAlt, float[][] vals)
                                                            throws VisADException, RemoteException

  {

    Radar2DCoordinateSystem r2D = new Radar2DCoordinateSystem(radarLat, radarLon);

    float[][] target = r2D.fromReference(vals);
    return target;
  }

  public float rangeToHeight(float ran)
  {
    float ground_range = ran/1000.0f;

    // ### Since we are given a ground (not slant range) range, we 
    // ### calculate the height as a function of the true range [ h(true) ]
    // ### Eq'n. source is below
    double inside = ((Math.pow(ground_range,2)) + (Math.pow(EFFECTIVE_EARTH_RADIUS,2))
                          + (2*ground_range*EFFECTIVE_EARTH_RADIUS*Math.sin(E_ANGLE)));
    double outside = EFFECTIVE_EARTH_RADIUS;
    float height_gr = (float)( (radarLocation[2]/1000) + ((Math.pow(inside,0.5)) - outside));

    // ### Using the h(true) value, calculate (backwards) to get the slant range
    float slant_range = (float) (
                        Math.sin(ground_range/EFFECTIVE_EARTH_RADIUS) *
                        ( (EFFECTIVE_EARTH_RADIUS + height_gr) / Math.cos(E_ANGLE) )
                        );

    // ### Now that we have the slant range, we can use Doviak and Zrnic's Eq. 2.28b 
    // ### (2nd Ed. - 1993) to get the true height. Note, same equation as above but
    // ### using the slan range instead of the ground range
    double inside1 = ((Math.pow(slant_range,2)) + (Math.pow(EFFECTIVE_EARTH_RADIUS,2))
                          + (2*slant_range*EFFECTIVE_EARTH_RADIUS*Math.sin(E_ANGLE)));
    double outside1 = EFFECTIVE_EARTH_RADIUS;
    float height = (float)( (radarLocation[2]/1000) + ((Math.pow(inside1,0.5)) - outside1));

    return height;
  }

  public void setElevationAngle(float ea)
  { E_ANGLE = (float)(ea * RADIANS); }
  public float getElevationAngle()
  { return E_ANGLE; }



}
