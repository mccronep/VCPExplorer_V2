package VCPExplorer;

import java.util.Vector;
import java.util.Hashtable;
import java.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.Font;
import java.rmi.RemoteException;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import javax.swing.BorderFactory.*;
import javax.swing.text.*;

import visad.*;
import visad.data.units.*;
import visad.java3d.*;
import visad.util.*;
import visad.bom.Radar2DCoordinateSystem;
import visad.bom.RubberBandBoxRendererJ3D;

public class RHIpanel extends JPanel 
          implements ActionListener
{

// #########  Asthetics  ###########
  private GridBagLayout gridbag = new GridBagLayout();
  private GridBagConstraints c = new GridBagConstraints();
  final static private Border loweredetched = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
  final static private Border raisedbevel = BorderFactory.createRaisedBevelBorder();
  final static private Border loweredbevel = BorderFactory.createLoweredBevelBorder();
  final static private Border compound = BorderFactory.createCompoundBorder(raisedbevel, loweredbevel);
  static private TitledBorder titled;
  private JFrame parentFrame;
//  final static private Dimension dim = new Dimension(620,690);

// #########  VisAD vars  ###########
  final private RealTupleType beamEnd;
  private RealType r, h, s, lat, lon, alt, value, index, 
                   red, green, blue, alpha, z,
                   shiType, poshType, meshType, vilType, vilDensityType;
  private Real htPointCoords, xMaxRange, yMaxRange88D, yMaxRangeTDWR;
  private TextType text;
  private Unit kilometer, km, nMile, kFt, inch, meter, mm;
  private FunctionType func_h_s, rangeHeightFunc;
  private Set r_set, h_set, rangeSet, rLineSet, whiteLine;
  private FlatField  beamPath_ff, terrainData_FF, zProfile_ff;
  private UnionSet vcpUSet, vcpWidths;
  private DataReferenceImpl singleBeamRef, singleBeamWidthRef, singleBeamFillRef, singleBeamLabelRef;
  private DataReferenceImpl allBeamsRef, allBeamWidthsRef, allBeamFillsRef, allBeamLabelsRef;
  private DataReferenceImpl huFillRef, sawToothPointRef, sawToothLineRef, sawToothRef, gcRef;
  private DataReferenceImpl nsPathRef, nsWidthRef;
  private DataReferenceImpl sampledSHIRef, sampledPOSHRef, sampledMESHRef,
                            sampledVILRef, sampledVILDensityRef, 
                            sampledEchoTopsRef;
  private DataReferenceImpl theoreticalSHIRef, theoreticalPOSHRef, theoreticalMESHRef,
                            theoreticalVILRef, theoreticalVILDensityRef, 
                            theoreticalEchoTopsRef;
  private DataRenderer nsPathRend, nsWidthRend;
  private DataRenderer singleBeamRend, singleBeamWidthRend, singleBeamFillRend, singleBeamLabelRend;
  private DataRenderer allBeamsRend, allBeamWidthsRend, allBeamFillsRend, allBeamLabelsRend;
  private DataRenderer sawToothPointRend, sawToothLineRend, sawToothRend, huFillRend, gcRend;
  private DataRenderer sampledSHIRend, sampledPOSHRend, sampledMESHRend,
                            sampledVILRend, sampledVILDensityRend, 
                            sampledEchoTopsRend;
  private DataRenderer theoreticalSHIRend, theoreticalPOSHRend, theoreticalMESHRend,
                            theoreticalVILRend, theoreticalVILDensityRend, 
                            theoreticalEchoTopsRend;
  final private DataReferenceImpl pt_ref, line_ref;
  private DataReferenceImpl altLineDataRef, wLineDataRef;
  private DataReferenceImpl allBeamsLabelRef[]; 
  protected DisplayImplJ3D rhiDisplay, ppiDisplay;
  private ScalarMap xMap, yMap, xRangeMap, yRangeMap, tMap, iMap, 
                    redMap, greenMap, blueMap, indexMap, valueMap,
                    alphaMap, zMap;
  private ScalarMap shiMap, poshMap, meshMap, vilMap, vilDensityMap;
  private ConstantMap[] 
              sampledSHI_CMap, sampledPOSH_CMap, sampledMESH_CMap,
              sampledVIL_CMap,sampledVILDensity_CMap ,
              theoreticalSHI_CMap, theoreticalPOSH_CMap, theoreticalMESH_CMap,
              theoreticalVIL_CMap,theoreticalVILDensity_CMap,
              nsPath_CMap, nsWidth_CMap, gc_CMap;
  private double[] projMatrix;
  private String radarID;
  private double mTwentyC, zeroC;

  private HeightUnderEstimate hu;
  private CursorReadoutChange crc;
  private TerrainBlockage tb;
  private Algorithms algs;

  private Vector soundingData;
  private String soundingInfo, oldSoundingInfo = new String();



// #########  General vars  ###########
  final private static int BEAMPATHSAMPLING = 400;
  final private static int EARTH_RADIUS = 6378; // km at Equator
  final private static double EFFECTIVE_EARTH_RADIUS = (4*EARTH_RADIUS/3); // km at Equator
  private static float BEAM_LENGTH;
  private static double MAX_RANGE;
  private static int RING_INTERVAL;
  private int oldPercentBlocked  = 101;
  private float oldElevationAng = -999.0f;
  static private JButton reset;
  static private JToggleButton beamWidthToggle, singleBeamToggle, 
                               beamFillToggle, labelToggle,
                               htUEFillToggle, htUEToothToggle, gc,
                               sdgToggle, refToggle;
  private JToolBar Toolbar;
  private double ELEV_ANGLE;
  private float currentElevationAngle;
  private int  currentPctBlocked;
  private float END_X;
  private float END_Y;
  private float END_Z;
  private Hashtable vcpHashTable;
  private float[][] h_vals = new float[1][BEAMPATHSAMPLING];
  private float[][] s_vals = new float[1][BEAMPATHSAMPLING];
  private BeamPath beamPath;
  private JPanel huColorBar;


// #########  Constructor  ###########

  public RHIpanel (JFrame frame, final VCP vcp, final PPIpanel dpy, int ringInterval, 
                                                       double maxRange, String radar) 
                                        throws RemoteException, VisADException
  {
// #########  Set up JPanel attributes (panel size, border, etc.)  ###########
    setLayout(gridbag);
    titled = BorderFactory.createTitledBorder(compound, "VCP RayPath");
    setBorder(titled);

// #########  Set up any action buttons  ###########
    Toolbar = new JToolBar();
    Toolbar.setBackground(Color.lightGray);
    Toolbar.setBorder(new EtchedBorder());
    Toolbar.setFloatable(false);
//    Toolbar.setLayout(new BoxLayout(Toolbar, BoxLayout.X_AXIS));
    Toolbar.setLayout(new GridBagLayout());
    GridBagConstraints g = new GridBagConstraints();

    g.gridx = 0;
    g.gridy = 0;
    g.fill = GridBagConstraints.NONE;
    g.weightx = 0.0;
    g.weighty = 0.0;
    g.anchor = GridBagConstraints.WEST;

    reset = (JButton) addToolbarButton("Reset", "Reset Orientation", new JButton(), Toolbar, g);

    g.gridx = GridBagConstraints.RELATIVE;
//    g.anchor = GridBagConstraints.CENTER;

    gc = (JToggleButton) addToolbarButton("GC", "Ground Clutter", new JToggleButton(), Toolbar, g);
    Toolbar.addSeparator();
    singleBeamToggle = (JToggleButton) 
         addToolbarButton("Beams", "Single Beam/All VCP Beams", new JToggleButton(), Toolbar, g);
    labelToggle = (JToggleButton) 
        addToolbarButton("Label", "Elevation Angle On/Off", new JToggleButton(), Toolbar, g);
    beamWidthToggle = (JToggleButton) 
        addToolbarButton("BW", "BeamWidth On/Off", new JToggleButton(), Toolbar, g);
    beamFillToggle = (JToggleButton) 
        addToolbarButton("BF", "BeamFill On/Off", new JToggleButton(), Toolbar, g);
    Toolbar.addSeparator();
    htUEFillToggle = (JToggleButton) 
        addToolbarButton("HF", "Height Underestimate FILL", new JToggleButton(), Toolbar, g);
    htUEToothToggle = (JToggleButton) 
        addToolbarButton("HL", "Height Underestimate LINE", new JToggleButton(), Toolbar, g);

    Toolbar.addSeparator();
    sdgToggle = (JToggleButton) 
        addToolbarButton("Sdg", "Sounding Derived Beam Path", new JToggleButton(), Toolbar, g);
    refToggle = (JToggleButton) 
        addToolbarButton("DBz", "Reflectivity Profile for Algs.", new JToggleButton(), Toolbar, g);


// #########  Get data from incoming variables  ###########
    parentFrame = frame;
    BEAM_LENGTH = (float)maxRange;
    MAX_RANGE = maxRange;
    RING_INTERVAL = ringInterval;
    this.ppiDisplay = dpy.display;
    this.terrainData_FF = dpy.terrainData_ff;
    setLat(dpy.radarLatitude);
    setLon(dpy.radarLongitude);
    setAlt(dpy.radarAltitude);
    setVcpHashTable(vcp.vcpHTable);
    radarID = radar;

// #########  Declare ScalarTypes  ###########
    try
    { 
      kilometer = visad.data.units.Parser.parse("kilometer").clone("km"); 
      nMile = visad.data.units.Parser.parse("nautical_mile").clone("nm");
      kFt = visad.data.units.Parser.parse("kilofeet").clone("kFt");
      inch = visad.data.units.Parser.parse("inch").clone("inch");
      meter = visad.data.units.Parser.parse("meter");
      mm = visad.data.units.Parser.parse("mm").clone("mm");
    }
    catch (visad.data.units.ParseException P)
    { System.out.println("Parse exception: " + P); }

    h = RealType.getRealType("Height", kilometer, null);
    s = RealType.getRealType("Range", kilometer, null);
    lat = RealType.getRealType("Latitude");
    lon = RealType.getRealType("Longitude");
    alt = RealType.getRealType("Altitude", SI.meter, null);
    text = TextType.getTextType("Text");
    index = RealType.getRealType("index");
    red = RealType.getRealType("RED", null, null);
    green = RealType.getRealType("GREEN", null, null);
    blue = RealType.getRealType("BLUE", null, null);
    alpha = RealType.getRealType("Alpha");
    z = RealType.getRealType("z");
    value = RealType.getRealType("Value", kilometer, null);
    shiType = RealType.getRealType("shiType");
    poshType = RealType.getRealType("poshType");
    meshType = RealType.getRealType("meshType");
    RealType radarLat = RealType.getRealType("Latitude");
    RealType radarLon = RealType.getRealType("Longitude");
    RealType radarHeight = RealType.getRealType("Altitude", SI.meter, null);
    RealType endLat = RealType.getRealType("Latitude");
    RealType endLon = RealType.getRealType("Longitude");
    RealType endHeight = RealType.getRealType("Altitude", SI.meter, null);
    RealTupleType radarInfo = new RealTupleType(radarLat, radarLon);
    beamEnd = new RealTupleType(endLat, endLon);
    final RealTupleType locLatLon = new RealTupleType(lat, lon);



// #########  Declare ConstantMaps  ###########
    ConstantMap[] radarLocDot = { new ConstantMap (1.0f, Display.Red),
                                  new ConstantMap (0.0f, Display.Green),
                                  new ConstantMap (0.0f, Display.Blue),
                                  new ConstantMap(-0.7f, Display.ZAxis),
                                  new ConstantMap (4.0f, Display.PointSize) };
    ConstantMap[] beamEndDot = { new ConstantMap (1.0f, Display.Red),
                                 new ConstantMap (0.0f, Display.Green),
                                 new ConstantMap (0.0f, Display.Blue),
                                 new ConstantMap(-0.7f, Display.ZAxis),
                                 new ConstantMap (5.0f, Display.PointSize) };
    ConstantMap[] lineMap = { new ConstantMap (1.0f, Display.Red),
                              new ConstantMap (0.0f, Display.Green),
                              new ConstantMap (0.0f, Display.Blue),
                              new ConstantMap(-0.7f, Display.ZAxis) };
    ConstantMap[] fillMap = { new ConstantMap(0.0f, Display.Red),
                           new ConstantMap(0.8f, Display.Green),
                           new ConstantMap(0.0f, Display.Blue),
                           new ConstantMap(-1.0f, Display.ZAxis) };
    ConstantMap[] widthMap = { new ConstantMap(1.0f, Display.Red),
                           new ConstantMap(1.0f, Display.Green),
                           new ConstantMap(0.0f, Display.Blue),
                           new ConstantMap(GraphicsModeControl.DASH_STYLE, Display.LineStyle)
                         };
    ConstantMap[] dashLineMap = { new ConstantMap(GraphicsModeControl.DASH_STYLE, Display.LineStyle)};
    ConstantMap[] alphaCMap = { new ConstantMap(0.6f, Display.Alpha)};

    gc_CMap =  new ConstantMap[] { 
                             new ConstantMap (0.50f, Display.Red),
                             new ConstantMap (0.50f, Display.Green),
                             new ConstantMap (0.50f, Display.Blue),
                             new ConstantMap (0.75f, Display.Alpha),
                             new ConstantMap(-0.9f, Display.ZAxis) };

    ConstantMap[] huFillMap = { new ConstantMap(-1.0f, Display.ZAxis) };

// #########  Caluculate TerrainBlockage ##########
   RealTupleType radarLLA_tt = new RealTupleType(lat, lon, alt);
   double[] radarLocation = new double[] {getLat(), getLon(), getAlt()};
   RealTuple rlt = new RealTuple(radarLLA_tt, radarLocation);
//   tb = new TerrainBlockage(terrainData_FF, rlt, (float)MAX_RANGE*1000.0f);
   String blockageFile = new String("TerrainFiles/Blockage/" 
                                    + radarID.toLowerCase() + "_blockage.vad.gz");
   tb = new TerrainBlockage(blockageFile);

// #########  Declare DataReferences  ###########
    singleBeamRef = new DataReferenceImpl("singleBeamRef");
    singleBeamWidthRef = new DataReferenceImpl("singleBeamWidthRef");
    singleBeamFillRef = new DataReferenceImpl("singleBeamFillRef");
    singleBeamLabelRef = new DataReferenceImpl("singleBeamFillRef");
    singleBeamRend = new DefaultRendererJ3D();
    singleBeamWidthRend = new DefaultRendererJ3D();
    singleBeamFillRend = new DefaultRendererJ3D();
    singleBeamLabelRend = new DefaultRendererJ3D();

    allBeamsRef = new DataReferenceImpl("allBeamsRef");
    allBeamWidthsRef = new DataReferenceImpl("allBeamWidthsRef");
    allBeamFillsRef = new DataReferenceImpl("allBeamFillsRef");
    allBeamLabelsRef = new DataReferenceImpl("allBeamLabelsRef");
    allBeamsRend = new DefaultRendererJ3D();
    allBeamWidthsRend = new DefaultRendererJ3D();
    allBeamFillsRend = new DefaultRendererJ3D();
    allBeamLabelsRend = new DefaultRendererJ3D();

    sawToothPointRef = new DataReferenceImpl("sawToothPointRef");
    sawToothLineRef = new DataReferenceImpl("sawToothLineRef");
    sawToothRef = new DataReferenceImpl("sawToothRef");
    sawToothPointRend = new DirectManipulationRendererJ3D();
    sawToothLineRend = new DefaultRendererJ3D();
    sawToothRend = new DefaultRendererJ3D();

    huFillRef = new DataReferenceImpl("huFilRef");
    huFillRend = new DefaultRendererJ3D();
    gcRef = new DataReferenceImpl("gcRef");
    gcRend = new DefaultRendererJ3D();

    line_ref = new DataReferenceImpl("line");
    pt_ref = new DataReferenceImpl("point");
    altLineDataRef = new DataReferenceImpl("altLineDataRef");
    DataReferenceImpl refBeamEndTuple = new DataReferenceImpl("refBeamEndTuple");
    DataReferenceImpl refRadarLocationTuple = 
              new DataReferenceImpl("refRadarLocationTuple");

    nsPathRef = new DataReferenceImpl("nsPathRef");
    nsWidthRef = new DataReferenceImpl("nsWidthRef");
    nsPathRend = new DefaultRendererJ3D();
    nsWidthRend = new DefaultRendererJ3D();

    sampledSHIRef = new DataReferenceImpl("sampledSHIRef");
    sampledPOSHRef = new DataReferenceImpl("sampledPOSHRef");
    sampledMESHRef = new DataReferenceImpl("sampledMESHRef");
    sampledVILRef = new DataReferenceImpl("sampledVILRef");
    sampledVILDensityRef = new DataReferenceImpl("sampledVILDensityRef");
    theoreticalSHIRef = new DataReferenceImpl("theoreticalSHIRef");
    theoreticalPOSHRef = new DataReferenceImpl("theoreticalPOSHRef");
    theoreticalMESHRef = new DataReferenceImpl("theoreticalMESHRef");
    theoreticalVILRef = new DataReferenceImpl("theoreticalVILRef");
    theoreticalVILDensityRef = new DataReferenceImpl("theoreticalVILDensityRef");

    sampledSHIRend = new DefaultRendererJ3D();
    sampledPOSHRend = new DefaultRendererJ3D();
    sampledMESHRend = new DefaultRendererJ3D();
    sampledVILRend = new DefaultRendererJ3D();
    sampledVILDensityRend = new DefaultRendererJ3D();
    theoreticalSHIRend = new DefaultRendererJ3D();
    theoreticalPOSHRend = new DefaultRendererJ3D();
    theoreticalMESHRend = new DefaultRendererJ3D();
    theoreticalVILRend = new DefaultRendererJ3D();
    theoreticalVILDensityRend = new DefaultRendererJ3D();

// #########  Set up RHI display  ###########
    rhiDisplay = new DisplayImplJ3D("RHIDisplay");
    rhiDisplay.clearMaps();

    // ### Set up mouse behavior
    DisplayRendererJ3D dr = (DisplayRendererJ3D)rhiDisplay.getDisplayRenderer();
    MouseBehaviorJ3D mb = (MouseBehaviorJ3D)dr.getMouseBehavior();
    MouseHelper mh = mb.getMouseHelper();

    int[][][] mouseButtonTable = new int[][][]
    {
      { {MouseHelper.DIRECT, MouseHelper.DIRECT}, {MouseHelper.DIRECT, MouseHelper.DIRECT} },
      { {MouseHelper.CURSOR_TRANSLATE, MouseHelper.CURSOR_TRANSLATE}, {MouseHelper.CURSOR_TRANSLATE, MouseHelper.CURSOR_TRANSLATE} },
      { {MouseHelper.TRANSLATE, MouseHelper.ZOOM}, {MouseHelper.TRANSLATE, MouseHelper.ZOOM} }
    };

    mh.setFunctionMap(mouseButtonTable);

    GraphicsModeControl dispGMC = 
                   (GraphicsModeControl)rhiDisplay.getGraphicsModeControl();
    dispGMC.setScaleEnable(true);
    dispGMC.setProjectionPolicy(DisplayImplJ3D.PARALLEL_PROJECTION);


    ProjectionControl initPC = rhiDisplay.getProjectionControl();
    projMatrix = initPC.getMatrix();
    double scale = 1.4;
    double[] newMatrix = mb.make_matrix(0.0, 0.0, 0.0, scale, 0.0, 0.0, 0.0);
    newMatrix = mb.multiply_matrix(newMatrix, projMatrix);
    initPC.setMatrix(newMatrix);
    projMatrix = initPC.getMatrix();




    xMap = new ScalarMap(s, Display.XAxis);
    yMap = new ScalarMap(h, Display.YAxis);
    tMap = new ScalarMap(text, Display.Text);
    redMap = new ScalarMap(red, Display.Red);
    greenMap = new ScalarMap(green, Display.Green);
    blueMap = new ScalarMap(blue, Display.Blue);
    alphaMap = new ScalarMap(alpha, Display.Alpha);
    zMap = new ScalarMap(z, Display.ZAxis);

    shiMap = new ScalarMap(shiType, Display.YAxis);
    poshMap = new ScalarMap(poshType, Display.YAxis);
    meshMap = new ScalarMap(meshType, Display.YAxis);

    xMap.setOverrideUnit(nMile);
    yMap.setOverrideUnit(kFt);

//    indexMap = new ScalarMap(index, Display.RGB);
    valueMap = new ScalarMap(value, Display.RGB);

    yRangeMap = new ScalarMap( h, Display.SelectRange);
    xRangeMap = new ScalarMap( s, Display.SelectRange);

    rhiDisplay.addMap(xMap);
    rhiDisplay.addMap(yMap);
    rhiDisplay.addMap(tMap);
    rhiDisplay.addMap(redMap);
    rhiDisplay.addMap(greenMap);
    rhiDisplay.addMap(blueMap);
    rhiDisplay.addMap(alphaMap);
    rhiDisplay.addMap(zMap);
//    rhiDisplay.addMap(indexMap);
    rhiDisplay.addMap(yRangeMap);
    rhiDisplay.addMap(xRangeMap);
    rhiDisplay.addMap(valueMap);

    redMap.setRange( 0.0f, 1.0f );
    greenMap.setRange( 0.0f, 1.0f );
    blueMap.setRange( 0.0f, 1.0f );
    alphaMap.setRange( 0.0f, 1.0f );
    valueMap.setRange(0.0, 19.8); // kFt range for 0.0m - 6.0m
    zMap.setRange(-1.0, 1.0);
    zMap.setScaleEnable(false);

    valueMap.setOverrideUnit(kFt);

    TextControl tcontrol = (TextControl) tMap.getControl();
    tcontrol.setNumberFormat(new DecimalFormat("00.00"));
    tcontrol.setSize(0.75);
    tcontrol.setJustification(TextControl.Justification.LEFT);
    tcontrol.setRotation(-45.0);

    xMaxRange = new Real(s, MAX_RANGE);
    yMaxRange88D = new Real(h, 25.0);
    yMaxRangeTDWR = new Real(h, 10.0);

    xMap.setRange(0.0, xMaxRange.getValue(nMile));
    if (MAX_RANGE >= 300)
    { yMap.setRange(0.0, yMaxRange88D.getValue(kFt)); }
    else
    { yMap.setRange(0.0, yMaxRangeTDWR.getValue(kFt)); }


    AxisScale xAxis = xMap.getAxisScale();
    xAxis.setTitle("Range (nm)");
    AxisScale yAxis = yMap.getAxisScale();
    yAxis.setTitle("Height (kFt)");

// #########  Set Radar and beam end point data  ###########
    final double rLatVal = (double) getLat();
    final double rLonVal = (double) getLon();
    final double rHeightVal = (double) getAlt();
    double eLatVal = rLatVal + 4.0;
    double eLonVal = rLonVal;
    final double eHeightVal = rHeightVal;
    double[] radarLocVals = { rLatVal, rLonVal };
    double[] beamEndVals = { eLatVal, eLonVal };
    RealTuple radarLocationTuple = new RealTuple(radarInfo, radarLocVals);
    RealTuple beamEndTuple = new RealTuple(beamEnd, beamEndVals);

    refRadarLocationTuple.setData(radarLocationTuple);
    refBeamEndTuple.setData(beamEndTuple);

// #########  Add references to PPI display  ###########
    ppiDisplay.disableAction();
    ppiDisplay.addReference(refRadarLocationTuple, radarLocDot);
    ppiDisplay.addReferences(new DirectManipulationRendererJ3D(), pt_ref, beamEndDot);

    ppiDisplay.addReference(line_ref, lineMap);

    // Needed to create the endpoint of the radial
    doPoint(beamEnd, (float)eLatVal, (float)eLonVal, pt_ref);

    // Create the temperature line to be shown on display
    altLineDataRef.setData( makeFlatFieldAlongRadial(beamEnd, (float)eLatVal, (float)eLonVal) );

    // Create behavior for DirectManipulation Renderer
    CellImpl cell = new CellImpl()
    {
      public void doAction()
      {
        // get EndPoint Coords
        RealTuple tuple = (RealTuple) pt_ref.getData();
        if (tuple == null) return;
        double[] vals = tuple.getValues();
        float latVal = (float) vals[0];
        float lonVal = (float) vals[1];

        float beamRange = (float) 
          calcLatLonRange(rLatVal, rLonVal, (double)latVal,(double)lonVal);

        if (!Util.isApproximatelyEqual(beamRange, BEAM_LENGTH, 0.05f)) {
          double lamda = BEAM_LENGTH / beamRange;
          latVal = (float) (rLatVal + lamda * (latVal - rLatVal));
          lonVal = (float) (rLonVal + lamda * (lonVal - rLonVal));


          try { doPoint(beamEnd, latVal, lonVal, pt_ref); }
          catch (Exception exc) { exc.printStackTrace(); }
          return;
        }


        try{ 
             altLineDataRef.setData( makeFlatFieldAlongRadial(beamEnd, latVal, lonVal) );

           }
        catch (Exception exc) { exc.printStackTrace(); }

        float[][] ranAz = null;
        try{ 
             float[][] fVals = new float[][] { {latVal}, {lonVal} };
             ranAz = latLonToRanAz(fVals);
           }
        catch (Exception exc) { exc.printStackTrace(); }
        java.text.DecimalFormat format = new java.text.DecimalFormat("000.00");
        String s =  String.valueOf( format.format(ranAz[1][0]) );
        vcp.azEntered.setText( s );

      }
    };
    cell.addReference(pt_ref);


// #########  Create initial beam path for RHI  ###########
    beamPath = new BeamPath( getAlt(), MAX_RANGE );
    if (vcp.vcpList.getSelectedValue() != null  && !vcp.vcpList.getValueIsAdjusting())
    { 
      beamPath.setEnteredBeamWidth( vcp.beamWidth.getText() );
      listen( (String)vcp.elevEntered.getText() ); 
      setPercentBlocked( vcp.pctBlockage.getText() );
    }

// ### Changes cursor readout from lat/lon to polar on PPI Display
    float[] radarLatLonFloats = new float[] { (float)getLat(), (float)getLon(),
                                              (float)getAlt() };

    crc = new CursorReadoutChange(ppiDisplay, radarLatLonFloats, 
                                                             (float)beamPath.getElevationAngle() );


// #########  Create initial VCP beams  ###########
    beamPath.vcpUpdate( 
              (String)vcp.vcpHTable.get(vcp.vcpComboBox.getSelectedItem()) );
    vcpUpdate();
    hu = new HeightUnderEstimate( beamPath.getVCP(), beamPath.getRadarElevation() ); 

// #########  Add references to RHI display  ###########
    ConstantMap[] altLineCMap = { new ConstantMap(-0.8f, Display.ZAxis) };

    rhiDisplay.addReference(altLineDataRef, altLineCMap);

    ConstantMap[] beamCMap = { new ConstantMap(-0.9f, Display.ZAxis) };
    rhiDisplay.addReferences(singleBeamRend, singleBeamRef, beamCMap);

    /** ##### */
/*
    rhiDisplay.addReferences(nsPathRend, nsPathRef, new ConstantMap[]{ 
                      new ConstantMap(1.0f, Display.Red), 
                      new ConstantMap(0.76f, Display.Green), 
                      new ConstantMap(0.15f, Display.Blue) 
                                                        });

    rhiDisplay.addReferences(nsWidthRend, nsWidthRef, new ConstantMap[]{ 
                      new ConstantMap(0.80f, Display.Red), 
                      new ConstantMap(0.61f, Display.Green), 
                      new ConstantMap(0.11f, Display.Blue),
                      new ConstantMap(GraphicsModeControl.DASH_STYLE, Display.LineStyle) 
                                                        });
    nsPathRend.toggle(false);
    nsWidthRend.toggle(false);
*/
    /** ##### */

    rhiDisplay.addReferences(singleBeamWidthRend, singleBeamWidthRef, widthMap);
    rhiDisplay.addReferences(singleBeamFillRend, singleBeamFillRef, fillMap);
    rhiDisplay.addReferences(singleBeamLabelRend, singleBeamLabelRef);
    singleBeamRend.toggle(true);
    singleBeamWidthRend.toggle(false);
    singleBeamFillRend.toggle(false);
    singleBeamLabelRend.toggle(false);

    rhiDisplay.addReferences(allBeamsRend, allBeamsRef);
    rhiDisplay.addReferences(allBeamWidthsRend, allBeamWidthsRef, dashLineMap);
//    rhiDisplay.addReferences(allBeamFillsRend, allBeamFillsRef, alphaCMap);
    rhiDisplay.addReferences(allBeamFillsRend, allBeamFillsRef);
    rhiDisplay.addReferences(allBeamLabelsRend, allBeamLabelsRef);
    allBeamsRend.toggle(false);
    allBeamWidthsRend.toggle(false);
    allBeamFillsRend.toggle(false);
    allBeamLabelsRend.toggle(false);

    huFillRef.setData( hu.getHeightUnderestimateVals() );
    rhiDisplay.addReferences(huFillRend, huFillRef, huFillMap);
    huFillRend.toggle(false);
/*
//    gcRef.setData ( MakeGC() );
    ppiDisplay.addReferences(gcRend, gcRef, gcCMap);
    gcRend.toggle(false);
*/
    Component comp = rhiDisplay.getComponent();

    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1.0;
    c.weighty = 1.0;
    c.fill = GridBagConstraints.BOTH;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.anchor = GridBagConstraints.LINE_START;
    gridbag.setConstraints( comp, c);
    add(comp);

    c.gridy = 2;
    c.weightx = 0.0;
    c.weighty = 0.0;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.fill = GridBagConstraints.NONE;
    c.anchor = GridBagConstraints.LINE_START;
    gridbag.setConstraints( Toolbar, c);
    add(Toolbar);


    // Make sure RHI loads before PPI, fixes "squat-bug"
    rhiDisplay.addDisplayListener(new DisplayListener()
    { 
      public void displayChanged(DisplayEvent de)
      {
        if (de.TRANSFORM_DONE != 0)
        { 
          ppiDisplay.enableAction(); 
        }
      }
    });

    rhiDisplay.reAutoScale();

    // ### RubberBandZoom
    RealTupleType ranHt_tt = new RealTupleType(s, h);
    Gridded2DSet zoomSet = new Gridded2DSet(ranHt_tt, null, 1);
    final DataReferenceImpl zoomRef = new DataReferenceImpl("zoomRef");
    zoomRef.setData(zoomSet);
    // m = 1 needed to make rb-box DirectManipulationRenderer active on shift-rt click,
    // else affects other DMRs, like sawTooth
    int m = 1; 
    rhiDisplay.addReferences(new RubberBandBoxRendererJ3D(s, h, m, m), zoomRef);

    CellImpl zoomCell = new CellImpl()
    {
      public void doAction() throws VisADException, RemoteException
      {
        Set set = (Set) zoomRef.getData();
        float[][] zoomSamples = set.getSamples();
        if (zoomSamples != null)
        {
          java.util.Arrays.sort(zoomSamples[0]);
          java.util.Arrays.sort(zoomSamples[1]);

          Real x0 = new Real(s, zoomSamples[0][0]);
          Real x1 = new Real(s, zoomSamples[0][1]);
          Real y0 = new Real(h, zoomSamples[1][0]);
          Real y1 = new Real(h, zoomSamples[1][1]);
/*
          // ### Use this for units km, km
          xMap.setRange(x0.getValue(km), x1.getValue(km));
          yMap.setRange(y0.getValue(km), y1.getValue(km));
*/
          // ### Use this for units nm,kFt
          xMap.setRange(x0.getValue(nMile), x1.getValue(nMile));
          yMap.setRange(y0.getValue(kFt), y1.getValue(kFt));

        }
      }
    };
    zoomCell.addReference(zoomRef);


    ppiDisplay.reDisplayAll();

// #########  Make sure we listen to updates from VCP  ###########
    update(vcp);
    // make user-entered elev field have focus.  Need here and in VCP
    vcp.elevEntered.grabFocus();


  }



  public void actionPerformed(ActionEvent e)
  {

/*
    try
    { throw new RuntimeException(); }
    catch
    (RuntimeException rt)
    { rt.printStackTrace(System.out); }
*/

//  ######### Beam Fill
    if ( e.getActionCommand().startsWith("BF") )
    {
      if ( beamFillToggle.isSelected() )
      {
        if ( singleBeamToggle.isSelected() )
        {
          singleBeamFillRend.toggle(false);
          allBeamFillsRend.toggle(true);
        }
        if ( !singleBeamToggle.isSelected() )
        {
          singleBeamFillRend.toggle(true);
          allBeamFillsRend.toggle(false);
        }

      }
      if ( !beamFillToggle.isSelected() )
      {
        singleBeamFillRend.toggle(false);
        allBeamFillsRend.toggle(false);

      }
      rhiDisplay.reDisplayAll();
    }

//  ######## BeamWidths
    if ( e.getActionCommand().startsWith("BW") )
    {
      if ( beamWidthToggle.isSelected() )
      {
        if ( singleBeamToggle.isSelected() )
        {
          singleBeamWidthRend.toggle(false);
          allBeamWidthsRend.toggle(true);
        }
        if ( !singleBeamToggle.isSelected() )
        {
          singleBeamWidthRend.toggle(true);
          allBeamWidthsRend.toggle(false);
        }
      }
      if ( !beamWidthToggle.isSelected() )
      {
        singleBeamWidthRend.toggle(false);
        allBeamWidthsRend.toggle(false);

      }
      rhiDisplay.reDisplayAll();
    }


//  ######### All/Single Beam
    if ( e.getActionCommand().endsWith("Beams") )
    {
      if ( singleBeamToggle.isSelected() )
      {

        singleBeamRend.toggle(false);
        allBeamsRend.toggle(true);

        if ( beamWidthToggle.isSelected() )
        {
          singleBeamWidthRend.toggle(false);
          allBeamWidthsRend.toggle(true);
        }
        if ( !beamWidthToggle.isSelected() )
        {
          singleBeamWidthRend.toggle(false);
          allBeamWidthsRend.toggle(false);
        }
        if ( beamFillToggle.isSelected() )
        {
          singleBeamFillRend.toggle(false);
          allBeamFillsRend.toggle(true);
        }
        if ( !beamFillToggle.isSelected() )
        {
          singleBeamFillRend.toggle(false);
          allBeamFillsRend.toggle(false);
        }
        if ( labelToggle.isSelected() )
        {
          singleBeamLabelRend.toggle(false);
          allBeamLabelsRend.toggle(true);
        }
        if ( !labelToggle.isSelected() )
        {
          singleBeamLabelRend.toggle(false);
          allBeamLabelsRend.toggle(false);
        }
      }

      if ( !singleBeamToggle.isSelected() )
      {
        singleBeamRend.toggle(true);
        allBeamsRend.toggle(false);
  
        if ( beamWidthToggle.isSelected() )
        {
          singleBeamWidthRend.toggle(true);
          allBeamWidthsRend.toggle(false);
        }
        if ( !beamWidthToggle.isSelected() )
        {
          singleBeamWidthRend.toggle(false);
          allBeamWidthsRend.toggle(false);
        }
        if ( beamFillToggle.isSelected() )
        {
          singleBeamFillRend.toggle(true);
          allBeamFillsRend.toggle(false);
        }
        if ( !beamFillToggle.isSelected() )
        {
          singleBeamFillRend.toggle(false);
          allBeamFillsRend.toggle(false);
        }
        if ( labelToggle.isSelected() )
        {
          singleBeamLabelRend.toggle(true);
          allBeamLabelsRend.toggle(false);
        }
        if ( !labelToggle.isSelected() )
        {
          singleBeamLabelRend.toggle(false);
          allBeamLabelsRend.toggle(false);
        }
      }
      rhiDisplay.reDisplayAll();

    }

//  ######## Labels
    if ( e.getActionCommand().startsWith("Label") )
    {
      if ( labelToggle.isSelected() )
      {
        if ( !singleBeamToggle.isSelected() )
        {
          allBeamLabelsRend.toggle(false);
          singleBeamLabelRend.toggle(true);
        }
        if ( singleBeamToggle.isSelected() )
        {
          allBeamLabelsRend.toggle(true);
          singleBeamLabelRend.toggle(false);
        }
      }
      if ( !labelToggle.isSelected() )
      {
        allBeamLabelsRend.toggle(false);
        singleBeamLabelRend.toggle(false);
      }
      rhiDisplay.reDisplayAll();
    }

    if ( e.getActionCommand().startsWith("HF") )
    {
      if ( htUEFillToggle.isSelected() )
      {

        singleBeamRend.toggle(false);
        singleBeamWidthRend.toggle(false);
        singleBeamFillRend.toggle(false);
        singleBeamLabelRend.toggle(false);
        allBeamsRend.toggle(true);
        allBeamWidthsRend.toggle(false);
        allBeamFillsRend.toggle(false);
        allBeamLabelsRend.toggle(true);

        try
        { 
          huFillRef.setData( hu.getHeightUnderestimateVals() );
          huFillRend.toggle(true);  

          huColorBar = makeColorBar(); 

          GridBagConstraints g = new GridBagConstraints();
          g.gridx = 0;
          g.gridy = 1;
          g.weightx = 0;
          g.weighty = 0;
          g.fill = GridBagConstraints.HORIZONTAL;
          gridbag.setConstraints( huColorBar, g);
          add(huColorBar);
          revalidate();

          // ### This shrinks the displayed data a little to make room for the colorbar
          DisplayRendererJ3D dr = (DisplayRendererJ3D)rhiDisplay.getDisplayRenderer();
          MouseBehaviorJ3D mb = (MouseBehaviorJ3D)dr.getMouseBehavior();
          ProjectionControl pc = rhiDisplay.getProjectionControl();
          double[] matrix = pc.getMatrix();
          double scale = 0.9;
          double[] newMatrix = mb.make_matrix(0.0, 0.0, 0.0, scale, 0.0, 0.0, 0.0);
          newMatrix = mb.multiply_matrix(newMatrix, matrix);
          pc.setMatrix(newMatrix);
        }
        catch (VisADException ve)
        { System.out.println("::HU-Fill VisADException> " + ve + "::\n"); }
        catch (RemoteException re)
        { System.out.println("::HU-Fill RemoteException> " + re + "::\n"); }

      }

      if ( !htUEFillToggle.isSelected() )
      {
        singleBeamRend.toggle(true);
        singleBeamWidthRend.toggle(false);
        singleBeamFillRend.toggle(false);
        singleBeamLabelRend.toggle(false);
        allBeamsRend.toggle(false);
        allBeamWidthsRend.toggle(false);
        allBeamFillsRend.toggle(false);
        allBeamLabelsRend.toggle(false);

        huFillRend.toggle(false);
        remove(huColorBar);
        revalidate();
        resetOrientation();
      }

      rhiDisplay.reDisplayAll();


    }

    // ### Height Underestimate SawTooth Pattern
    if ( e.getActionCommand().startsWith("HL") )
    {
      if (htUEToothToggle.isSelected())
      {
        singleBeamRend.toggle(false);
        singleBeamWidthRend.toggle(false);
        singleBeamFillRend.toggle(false);
        singleBeamLabelRend.toggle(false);
//        allBeamsRend.toggle(false);
        allBeamWidthsRend.toggle(false);
        allBeamFillsRend.toggle(false);
        allBeamLabelsRend.toggle(false);

        if ( !singleBeamToggle.isSelected() )
         { singleBeamToggle.doClick(1); }

        try
        { makeHU_SawTooth(); }
        catch (VisADException ve)
        { System.out.println("::HU-Tooth VisADException> " + ve + "::\n"); }
        catch (RemoteException re)
        { System.out.println("::HU-Tooth RemoteException> " + re + "::\n"); }

        sawToothPointRend.toggle(true);
        sawToothLineRend.toggle(true);
        sawToothRend.toggle(true);

      }

      if ( !htUEToothToggle.isSelected() )
      {
        sawToothPointRend.toggle(false);
        sawToothLineRend.toggle(false);
        sawToothRend.toggle(false);

        if ( singleBeamToggle.isSelected() )
         { singleBeamToggle.doClick(1); }

        singleBeamRend.toggle(true);
        singleBeamWidthRend.toggle(false);
        singleBeamFillRend.toggle(false);
        singleBeamLabelRend.toggle(false);
        allBeamsRend.toggle(false);
        allBeamWidthsRend.toggle(false);
        allBeamFillsRend.toggle(false);
        allBeamLabelsRend.toggle(false);


      }
      rhiDisplay.reDisplayAll();
    }


    // ### Make the GC map
    if ( e.getActionCommand().equalsIgnoreCase("GC") )
    {
      int percentBlocked = getPercentBlocked();
      float elevationAng = getCurrentElevationAngle();

      if ( gc.isSelected() )
      {
        if ( (percentBlocked != oldPercentBlocked) || (elevationAng != oldElevationAng) )
        {

          parentFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
          try
          { 
            UnionSet gcSet = (UnionSet)MakeGC();    
            if (gcSet == null)
            {
              int percent = (int) getPercentBlocked();
              String error0 = new String("No Beam Blockage >= " + Integer.toString(percent) + "%");
              String error1 = new String("for elevation angle of " 
                                          + Float.toString(getCurrentElevationAngle()) );
              String error2 = new String("at " + radarID);
              String[] errors = new String[] {error0, error1, error2};
              ErrorFrame nullData = new ErrorFrame(errors);

            }

            gcRef.setData(gcSet);
              if ( ppiDisplay.findReference(gcRef) == null)
              {
                ppiDisplay.addReferences(
                      gcRend , gcRef, gc_CMap);
              }
          }
          catch (Exception ve)
          { System.out.println("GC Toggle: " + ve); }
        }
        gcRend.toggle(true);

      }
      if ( !gc.isSelected() )
      { gcRend.toggle(false); }

      oldPercentBlocked = percentBlocked;
      oldElevationAng = elevationAng;
      ppiDisplay.reDisplayAll();
      parentFrame.setCursor(null);
    }


    // ### Reset Orientation
    if ( e.getSource() == reset )
    { resetOrientation(); }


    // ### Sounding toggle
    if (e.getSource() == sdgToggle)
    {

      if (!sdgToggle.isSelected())
      { 
        boolean onOff = false;
        nsPathRend.toggle(onOff);
        nsWidthRend.toggle(onOff);
      }

      if (sdgToggle.isSelected())
      {
        Vector sndDataVect = getSoundingData();
        String sndInfo = getSoundingInfo();
 
        if (sndInfo != null && sndDataVect != null 
            && sndDataVect.size() > 1 
            && oldSoundingInfo.equals(sndInfo)
           )
        {
          boolean onOff = true;
          nsPathRend.toggle(onOff);
          nsWidthRend.toggle(onOff);
        }

        else
        {


          // ### Make sure we have some sounding data to work with
          if (sndInfo == null || sndDataVect == null)
          {
            String error0 = new String("No sounding data found.");
            String error1 = new String("Please select a sounding from the menu");
            ErrorFrame ef = new ErrorFrame(new String[]{ error0, error1 });
            sdgToggle.setSelected(false);
          }
          // ### If everything looks OK, plot the data
          else if (sndDataVect != null && sndDataVect.size() > 1 &&
                 !oldSoundingInfo.equals(sndInfo) )
          {
            beamPath.setSounding(sndDataVect);
            try
            { 
              if ( rhiDisplay.findReference(nsPathRef) == null)
              {
                nsPath_CMap = new ConstantMap[] {
                      new ConstantMap(1.0f, Display.Red),
                      new ConstantMap(0.76f, Display.Green),
                      new ConstantMap(0.15f, Display.Blue),
                      new ConstantMap(-0.7f, Display.ZAxis)
                                                };
                rhiDisplay.addReferences(
                      nsPathRend , nsPathRef, nsPath_CMap);
              }
              if ( rhiDisplay.findReference(nsWidthRef) == null)
              {
                nsWidth_CMap = new ConstantMap[] {
                      new ConstantMap(0.80f, Display.Red), 
                      new ConstantMap(0.61f, Display.Green), 
                      new ConstantMap(0.11f, Display.Blue),
                      new ConstantMap(GraphicsModeControl.DASH_STYLE, Display.LineStyle),
                      new ConstantMap(-0.7f, Display.ZAxis)
                                                };
                rhiDisplay.addReferences(
                      nsWidthRend , nsWidthRef, nsWidth_CMap);
              }

              nsPathRef.setData( beamPath.getNSBeamPath() );
              nsWidthRef.setData( beamPath.getNSBeamWidth() );
              nsPathRend.toggle(true);
              nsWidthRend.toggle(true);
            }
            catch (Exception ex)
            { System.err.println("Set nsPath: " + ex); }
    
          }
    
        }
        if (sndInfo == null)
        { oldSoundingInfo = new String(); }
        else
        { oldSoundingInfo = new String(sndInfo.toString()); }
      }
    }


    // ### DBz toggle
    if (e.getSource() == refToggle)
    {
      if (zProfile_ff == null)
      {

            String error0 = new String("Reflectivity profile not set.");
            String error1 = new String("Please create a DBz profile from the menu");
            ErrorFrame ef = new ErrorFrame(new String[]{ error0, error1 });            refToggle.setSelected(false);

      }

      if (refToggle.isSelected())
      {
        try
        {
/*
          algs = new Algorithms(
                                beamPath.getBeamPaths(),
                                getZProfile(),
                                new double[] { zeroC, mTwentyC},
                                getAlt()
                               );
*/

          shiType = RealType.getRealType("shiType");
          poshType = RealType.getRealType("poshType");
          meshType = RealType.getRealType("meshType");
          vilType = RealType.getRealType("vilType");
          vilDensityType = RealType.getRealType("vilDensityType");

          shiMap = new ScalarMap(shiType, Display.YAxis);
          shiMap.getAxisScale().setColor(new float[]{1.0f, 0.0f, 0.0f});
          shiMap.getAxisScale().setTitle("SHI");

          poshMap = new ScalarMap(poshType, Display.YAxis);
          poshMap.getAxisScale().setColor(new float[]{0.0f, 1.0f, 0.0f});
          poshMap.getAxisScale().setTitle("POSH (%)");

          meshMap = new ScalarMap(meshType, Display.YAxis);
          meshMap.getAxisScale().setColor(new float[]{0.0f, 0.0f, 1.0f});
          meshMap.getAxisScale().setTitle("MESH (mm)");
//          meshMap.setOverrideUnit(inch);

          vilMap = new ScalarMap(vilType, Display.YAxis);
          vilMap.getAxisScale().setColor(new float[]{1.0f, 1.0f, 0.0f});
          vilMap.getAxisScale().setTitle("VIL");

          vilDensityMap = new ScalarMap(vilDensityType, Display.YAxis);
          vilDensityMap.getAxisScale().setColor(new float[]{0.0f, 1.0f, 1.0f});
          vilDensityMap.getAxisScale().setTitle("VIL Density");

          rhiDisplay.addMap(shiMap);
          rhiDisplay.addMap(poshMap);
          rhiDisplay.addMap(meshMap);
/*
          rhiDisplay.addMap(vilMap);
          rhiDisplay.addMap(vilDensityMap);

          vilMap.setRange(0.0,100.0);
          vilDensityMap.setRange(0.0,5.0);
*/

          // ### Check and make sure all DataReferenceImples are displayed
          if ( rhiDisplay.findReference(sampledSHIRef) == null)
          { 
            sampledSHI_CMap = new ConstantMap[] {
                                  new ConstantMap (1.0f, Display.Red),
                                  new ConstantMap (0.0f, Display.Green),
                                  new ConstantMap (0.0f, Display.Blue),
                                  new ConstantMap(-0.7f, Display.ZAxis)
                                                };
            rhiDisplay.addReferences(
                   sampledSHIRend, sampledSHIRef, sampledSHI_CMap); 
          }
          if ( rhiDisplay.findReference(sampledPOSHRef) == null)
          { 
            sampledPOSH_CMap = new ConstantMap[] { 
                                  new ConstantMap (0.0f, Display.Red),
                                  new ConstantMap (1.0f, Display.Green),
                                  new ConstantMap (0.0f, Display.Blue),
                                  new ConstantMap(-0.7f, Display.ZAxis)
                                                 };
            rhiDisplay.addReferences(
                   sampledPOSHRend, sampledPOSHRef, sampledPOSH_CMap); 
          }
          if ( rhiDisplay.findReference(sampledMESHRef) == null)
          { 
            sampledMESH_CMap = new ConstantMap[] {
                                  new ConstantMap (0.0f, Display.Red),
                                  new ConstantMap (0.0f, Display.Green),
                                  new ConstantMap (1.0f, Display.Blue),
                                  new ConstantMap(-0.7f, Display.ZAxis)
                                                 };
            rhiDisplay.addReferences(
                   sampledMESHRend, sampledMESHRef, sampledMESH_CMap); 
          }
/*
          if ( rhiDisplay.findReference(sampledVILRef) == null)
          { 
            sampledVIL_CMap = new ConstantMap[] {
                                  new ConstantMap (1.0f, Display.Red),
                                  new ConstantMap (1.0f, Display.Green),
                                  new ConstantMap (0.0f, Display.Blue),
                                  new ConstantMap(-0.7f, Display.ZAxis)
                                                };
            rhiDisplay.addReferences(
                   sampledVILRend, sampledVILRef, sampledVIL_CMap); 
          }
          if ( rhiDisplay.findReference(sampledVILDensityRef) == null)
          { 
            sampledVILDensity_CMap = new ConstantMap[] {
                                  new ConstantMap (0.0f, Display.Red),
                                  new ConstantMap (1.0f, Display.Green),
                                  new ConstantMap (1.0f, Display.Blue),
                                  new ConstantMap(-0.7f, Display.ZAxis)
                                                };
            rhiDisplay.addReferences(
                   sampledVILDensityRend, sampledVILDensityRef, sampledVILDensity_CMap); 
          }
*/
          if ( rhiDisplay.findReference(theoreticalSHIRef) == null)
          { 
            theoreticalSHI_CMap = new ConstantMap[] {
                                  new ConstantMap (1.0f, Display.Red),
                                  new ConstantMap (0.5f, Display.Green),
                                  new ConstantMap (0.5f, Display.Blue),
                                  new ConstantMap(-0.7f, Display.ZAxis)
                                                    };
            rhiDisplay.addReferences(
              theoreticalSHIRend, theoreticalSHIRef, theoreticalSHI_CMap); 
          }
          if ( rhiDisplay.findReference(theoreticalPOSHRef) == null)
          { 
            theoreticalPOSH_CMap = new ConstantMap[] {
                                  new ConstantMap (0.5f, Display.Red),
                                  new ConstantMap (1.0f, Display.Green),
                                  new ConstantMap (0.5f, Display.Blue),
                                  new ConstantMap(-0.7f, Display.ZAxis)
                                                     };
            rhiDisplay.addReferences(
              theoreticalPOSHRend, theoreticalPOSHRef, theoreticalPOSH_CMap); 
          }
          if ( rhiDisplay.findReference(theoreticalMESHRef) == null)
          { 
            theoreticalMESH_CMap = new ConstantMap[] {
                                  new ConstantMap (0.5f, Display.Red),
                                  new ConstantMap (0.5f, Display.Green),
                                  new ConstantMap (1.0f, Display.Blue),
                                  new ConstantMap(-0.7f, Display.ZAxis)
                                                     };
            rhiDisplay.addReferences(
              theoreticalMESHRend, theoreticalMESHRef, theoreticalMESH_CMap); 
          }
/*
          if ( rhiDisplay.findReference(theoreticalVILRef) == null)
          { 
            theoreticalVIL_CMap = new ConstantMap[] {
                                  new ConstantMap (1.0f, Display.Red),
                                  new ConstantMap (1.0f, Display.Green),
                                  new ConstantMap (0.0f, Display.Blue),
                                  new ConstantMap(-0.7f, Display.ZAxis)
                                                     };
            rhiDisplay.addReferences(
              theoreticalVILRend, theoreticalVILRef, theoreticalVIL_CMap); 
          }
          if ( rhiDisplay.findReference(theoreticalVILDensityRef) == null)
          { 
            theoreticalVILDensity_CMap = new ConstantMap[] {
                                  new ConstantMap (0.0f, Display.Red),
                                  new ConstantMap (1.0f, Display.Green),
                                  new ConstantMap (1.0f, Display.Blue),
                                  new ConstantMap(-0.7f, Display.ZAxis)
                                                     };
            rhiDisplay.addReferences(
              theoreticalVILDensityRend, theoreticalVILDensityRef, theoreticalVILDensity_CMap); 
          }
*/
          else
          {
            sampledSHIRend.toggle(true);
            sampledPOSHRend.toggle(true);
            sampledMESHRend.toggle(true);
/*
            sampledVILRend.toggle(true);
            sampledVILDensityRend.toggle(true);
*/
            theoreticalSHIRend.toggle(true);
            theoreticalPOSHRend.toggle(true);
            theoreticalMESHRend.toggle(true);
/*
            theoreticalVILRend.toggle(true);
            theoreticalVILDensityRend.toggle(true);
*/
          }

          sampledSHIRef.setData( algs.getSampledSHI() );
          sampledPOSHRef.setData( algs.getSampledPOSH() );
          sampledMESHRef.setData( algs.getSampledMESH() );
/*
          sampledVILRef.setData( algs.getSampledVIL() );
          sampledVILDensityRef.setData( algs.getSampledVILDensity() );
*/
          theoreticalSHIRef.setData( algs.getTheoreticalSHI() );
          theoreticalPOSHRef.setData( algs.getTheoreticalPOSH() );
          theoreticalMESHRef.setData( algs.getTheoreticalMESH() );
/*
          theoreticalVILRef.setData( algs.getTheoreticalVIL() );
          theoreticalVILDensityRef.setData( algs.getTheoreticalVILDensity() );*/


        } // ### end try
        catch (Exception algEx)
        { System.err.println("Error calling Algs: " + algEx); }

      } // ### end "isSelected"


      if (!refToggle.isSelected())
      {
        sampledSHIRend.toggle(false);
        sampledPOSHRend.toggle(false);
        sampledMESHRend.toggle(false);
/*
        sampledVILRend.toggle(false);
        sampledVILDensityRend.toggle(false);
*/
        theoreticalSHIRend.toggle(false);
        theoreticalPOSHRend.toggle(false);
        theoreticalMESHRend.toggle(false);
/*
        theoreticalVILRend.toggle(false);
        theoreticalVILDensityRend.toggle(false);
*/
        try
        {
          rhiDisplay.removeMap(shiMap);
          rhiDisplay.removeMap(poshMap);
          rhiDisplay.removeMap(meshMap);
/*
          rhiDisplay.removeMap(vilMap);
          rhiDisplay.removeMap(vilDensityMap);
*/
        }
        catch (Exception ex)
        { System.err.println("Error removing maps: " + ex); }


      } // ### end "!isSelected"



    } // ### end RefToggle

  }



  /**
   * Resets the display projection to its original value.
   * Borrowed from VisAD SpreadSheet
   */
  public void resetOrientation() {
    if (rhiDisplay != null) {
      ProjectionControl pc = rhiDisplay.getProjectionControl();
      if (pc != null) {
        try
        {
          pc.setMatrix(projMatrix);

          xMaxRange = new Real(s, MAX_RANGE);
          yMaxRange88D = new Real(h, 25.0);
          yMaxRangeTDWR = new Real(h, 10.0);
      
          xMap.setRange(0.0, xMaxRange.getValue(nMile));
          if (MAX_RANGE >= 300)
          { yMap.setRange(0.0, yMaxRange88D.getValue(kFt)); }
          else
          { yMap.setRange(0.0, yMaxRangeTDWR.getValue(kFt)); }

        }
        catch (VisADException exc) {
          System.out.println("Cannot reset orientation" + exc);
        }
        catch (RemoteException exc) {
          System.out.println("Cannot reset orientation" + exc);
        }
      }
    }
  }


  public void update(final VCP vcp) throws RemoteException, VisADException
  {
    final JFormattedTextField vcpJTF = vcp.elevEntered;
    final JTextField bWidth = vcp.beamWidth;
    final JFormattedTextField blockage = vcp.pctBlockage;
    JComboBox vcpCB = vcp.vcpComboBox;
    final JList vcpJList = vcp.vcpList;
    final JLabel elLabel = vcp.elevLabel;
    final JFormattedTextField azEntered = vcp.azEntered;

    vcpJTF.addActionListener(new ActionListener()
    { public void actionPerformed(ActionEvent evt)
      { 
        // ### Formats entry to ##.##
        java.text.DecimalFormat format = new java.text.DecimalFormat("00.00");
        double d = Double.parseDouble(vcpJTF.getText());
        String s =  String.valueOf( format.format(d) );
        vcpJTF.setText( s );

        listen( vcpJTF.getText() );
        elLabel.setText( vcpJTF.getText() );
        vcpJTF.grabFocus();
        vcpJTF.selectAll();
      }
    });
    
    vcpJTF.addFocusListener( new FocusListener()
    {
      public void focusGained(FocusEvent f)
      {
        vcpJTF.selectAll();
        gc.setSelected(false);
        listen( vcpJTF.getText() );
      }
      public void focusLost(FocusEvent f) 
      {
      }
    });

    blockage.addActionListener(new ActionListener()
    { public void actionPerformed(ActionEvent evt)
      { 
        blockage.selectAll();
        gc.setSelected(false);
        setPercentBlocked( blockage.getText() );
      }
    });
    
    blockage.addFocusListener( new FocusListener()
    {
      public void focusGained(FocusEvent f)
      {
        blockage.selectAll();
      }
      public void focusLost(FocusEvent f) 
      {
        setPercentBlocked( blockage.getText() );
      }
    });

    azEntered.addActionListener(new ActionListener()
    { public void actionPerformed(ActionEvent evt)
      {
        float[][] latLon = null; 
        azEntered.selectAll();
        float azVal = Float.parseFloat( azEntered.getText() );
        if (azVal < 0.0f)
        { azVal = 360.0f - azVal; }
        if (azVal > 360.0f)
        { azVal = azVal - 360.0f; }

        try
        { latLon = ranAzToLatLon((float) MAX_RANGE, azVal); }
        catch (VisADException ve)
        { System.err.println("AzEntered: " + ve); }
        catch (RemoteException re)
        { System.err.println("AzEntered: " + re); }

        try { doPoint(beamEnd, latLon[0][0], latLon[1][0], pt_ref); }
        catch (Exception exc) { exc.printStackTrace(); }
        return;
      }
    });
    
    azEntered.addFocusListener( new FocusListener()
    {
      public void focusGained(FocusEvent f)
      {
        azEntered.selectAll();
      }
      public void focusLost(FocusEvent f) 
      {
      }
    });


/*
    bWidth.addActionListener(new ActionListener()
    { public void actionPerformed(ActionEvent evt)
      { 
        bWidth.selectAll();
        beamPath.setEnteredBeamWidth( bWidth.getText() );

        try 
        { 
          listen( vcpJTF.getText() );
          beamPath.vcpUpdate();
          vcpUpdate();
        }
        catch (RemoteException E)
        { System.out.println("Remote exception: " + E); }
        catch (VisADException E)
        { System.out.println("VisAD exception: " + E); }

        rhiDisplay.reDisplayAll();
      }
    });
    
    bWidth.addFocusListener( new FocusListener()
    {
      public void focusGained(FocusEvent f)
      {
        bWidth.selectAll();
      }
      public void focusLost(FocusEvent f) {}
    });
*/
    vcpCB.addActionListener(new ActionListener()
    { public void actionPerformed(ActionEvent cbe)
      {
        JComboBox vcpC = (JComboBox)cbe.getSource();
        String VCPchoice = (String)vcpC.getSelectedItem();
        Hashtable ht = getVcpHashTable();
        try
        { 
          listen( vcpJTF.getText() );
          beamPath.vcpUpdate( (String)ht.get(VCPchoice)); 
          vcpUpdate();
          hu.vcpUpdate( beamPath.getVCP() );
          if ( htUEFillToggle.isSelected() )
          { huFillRef.setData( hu.getHeightUnderestimateVals() ); }

        }
        catch (RemoteException E)
        { System.out.println("Remote exception: " + E); }
        catch (VisADException E)
        { System.out.println("VisAD exception: " + E); }
      }
    });

  }

  private void listen(String st)
  {
    try
    {
      setCurrentElevationAngle(st);
      beamPath.elevationUpdate( st );
      elevationUpdate();
      if (crc != null)
      { crc.setElevationAngle( (float) beamPath.getElevationAngle() ); }
    }
    catch (RemoteException E)
    { System.out.println("Remote exception: " + E); }
    catch (VisADException E)
    { System.out.println("VisAD exception: " + E); }
  }

  private void doPoint(RealTupleType rtt, double lat, double lon,
    DataReferenceImpl pt_ref) throws VisADException, RemoteException
  {
    pt_ref.setData(new RealTuple(rtt, new double[] {lat, lon}));
  }

  private Set doLine(RealTupleType rtt, double lat, double lon,
    DataReferenceImpl line_ref) throws VisADException, RemoteException
  {
    Set set = doLine(rtt, lat, lon);
    line_ref.setData(set);
    return set;
  }

//########################################################################
  private Set doLine(RealTupleType rtt, double lat, double lon) throws VisADException, RemoteException
  {
    int numPoints = BEAMPATHSAMPLING;
//    int numPoints = 1600;
    double[][] endPoints = { {getLat(), getLon()}, {lat, lon} };
    int numCoords = 2;
    double[][] samples2= new double[numCoords][numPoints];
    double[] incr = new double[numCoords];

    for (int i=0; i < numCoords; ++i)
    {
      incr[i] = (endPoints[1][i] - endPoints[0][i])/numPoints;
    }

    for (int i=0; i < numPoints; ++i)
        for (int j=0; j < numCoords; ++j)
        {
          samples2[Math.abs(j-1)][i] = (endPoints[0][j] + incr[j] * i);
        }

    RealType lo = RealType.getRealType("Longitude");
    RealType la = RealType.getRealType("Latitude");

    RealTupleType rtt2 = new RealTupleType(lo, la);

    Gridded2DDoubleSet set2 = new Gridded2DDoubleSet(rtt2, samples2, numPoints);

    return set2;
  }

//########################################################################
  private double calcLatLonRange(double lat1, double lon1, double lat2, double lon2)
  {
     double term1, term2, term3, inside, range, rLat1, rLon1, rLat2, rLon2;
     rLat1 = Math.PI*lat1/180;
     rLon1 = Math.PI*lon1/180;
     rLat2 = Math.PI*lat2/180;
     rLon2 = Math.PI*lon2/180;
     term1 = Math.cos(rLat1)*Math.cos(rLon1)*Math.cos(rLat2)*Math.cos(rLon2);
     term2 = Math.cos(rLat1)*Math.sin(rLon1)*Math.cos(rLat2)*Math.sin(rLon2);
     term3 = Math.sin(rLat1)*Math.sin(rLat2);
     inside = term1 + term2 + term3;
     range = Math.acos(inside)  * EARTH_RADIUS;
     return range;
  }

//########################################################################
  public float[][] distanceAlongLatLonLine(Set alongLine)
                               throws VisADException, RemoteException
  {
    // Take Gridded2DSet (manDim=1) of LatLon and get the length from radar
    // center to each LatLon pair along the line
    // |-D1-----D6---------D15---> where D1 = Lat1Lon1, D6 = Lat6Lon6, etc.
    float[][] addedRange = new float[1][];
    float[][] alongLineDoubles = alongLine.getSamples();

    if (alongLineDoubles[0].length != alongLineDoubles[1].length)
    {
      System.out.println("Error: trouble reading Set");
      return new float[][] { {-1.0f} };
    }
    else
    {
      addedRange[0] = new float[alongLineDoubles[0].length];
      for (int k = 0; k < alongLineDoubles[0].length; k++)
      {
        // At k = 0, this is a Lat/Lon difference of zero, so the range is zero
        if (k == 0)
        {
          addedRange[0][k] = 0.0f;
          continue;
        }
        float tempRange = (float)calcLatLonRange(alongLineDoubles[1][k-1],
            alongLineDoubles[0][k-1], alongLineDoubles[1][k], alongLineDoubles[0][k]);
        addedRange[0][k] = addedRange[0][k-1] + tempRange;
      }
    }
    return addedRange;
  }


//########################################################################
  public FlatField makeFlatFieldAlongRadial(
               RealTupleType rtt, double endPointLat, double endPointLon ) 
                        throws VisADException, RemoteException
  {

    FunctionType aLineType = new FunctionType(s, h);

    Set line  = doLine(rtt, (float)endPointLat, (float)endPointLon, line_ref);

    Set aLineSet = new Irregular1DSet(s, distanceAlongLatLonLine(line) );

    FlatField altLine_ff =  new FlatField( aLineType, aLineSet );
    float[] terrData = terrainData_FF.resample( line).getFloats(false)[0];
    float[][] range = new float[1][terrData.length];
    for (int j = 0; j < terrData.length; ++j)
    { range[0][j] =  (terrData[j]/1000.0f); }
    altLine_ff.setSamples( range, false);

    return altLine_ff;
  }

//########################################################################
  /**
   * given an array of floats, sort the array in ascending order 
   * and return the min and max values (in that order)
   */
  public float[] getMinMax(float[] array)
  {
    float max = -999.9f;
    if (array != null)
    { java.util.Arrays.sort(array); }
    for (int i = array.length-1; i >= 0; --i)
    {
      if ( !Float.isNaN(array[i]) )
      {
        max = array[i];
        break;
      }
    }
    return new float[] {array[0], max };
  }


  public void elevationUpdate() throws VisADException, RemoteException
  {
    singleBeamRef.setData ( beamPath.getBeamPath() );
    nsPathRef.setData( beamPath.getNSBeamPath() );
    nsWidthRef.setData( beamPath.getNSBeamWidth() );
    singleBeamWidthRef.setData ( beamPath.getBeamWidth() );
    singleBeamFillRef.setData ( beamPath.getBeamFill() );
    singleBeamLabelRef.setData ( beamPath.getBeamLabel() );
    beamPath_ff = beamPath.getBeamPath();
  }

  public void vcpUpdate() throws VisADException, RemoteException
  {
    int NUM_ANGLES = beamPath.getNumElevationAngles();
    FlatField[] paths = beamPath.getBeamPaths();
    UnionSet[] widths = beamPath.getBeamWidths();
    Irregular2DSet[] fills = beamPath.getBeamFills();
    Tuple[] labels = beamPath.getBeamLabels();
    Vector evenFillSetVect = new Vector();
    Vector oddFillSetVect = new Vector();
    Vector evenWidthSetVect = new Vector();
    Vector oddWidthSetVect = new Vector();
/*
    Vector fillSetVect0 = new Vector();
    Vector fillSetVect1 = new Vector();
    Vector fillSetVect2 = new Vector();
    Vector widthSetVect0 = new Vector();
    Vector widthSetVect1 = new Vector();
    Vector widthSetVect2 = new Vector();


    for (int k = 0; k < fills.length; k++)
    {
      if (k%3 == 0)
      { 
        fillSetVect0.add(fills[k]); 
        widthSetVect0.add(widths[k]);
      }
      if (k%3 == 1)
      { 
        fillSetVect1.add(fills[k]); 
        widthSetVect1.add(widths[k]);
      }
      if (k%3 == 2)
      { 
        fillSetVect2.add(fills[k]); 
        widthSetVect2.add(widths[k]);
      }

    }

    Irregular2DSet[] fillSet0 = 
        (Irregular2DSet[]) fillSetVect0.toArray(new Irregular2DSet[fillSetVect0.size()]);
    Irregular2DSet[] fillSet1 = 
        (Irregular2DSet[]) fillSetVect1.toArray(new Irregular2DSet[fillSetVect1.size()]);
    Irregular2DSet[] fillSet2 = 
        (Irregular2DSet[]) fillSetVect2.toArray(new Irregular2DSet[fillSetVect2.size()]);

    UnionSet[] widthSet0 = 
        (UnionSet[]) widthSetVect0.toArray(new UnionSet[widthSetVect0.size()]);
    UnionSet[] widthSet1 = 
        (UnionSet[]) widthSetVect1.toArray(new UnionSet[widthSetVect1.size()]);
    UnionSet[] widthSet2 = 
        (UnionSet[]) widthSetVect2.toArray(new UnionSet[widthSetVect2.size()]);

    UnionSet fillUSet = new UnionSet(fillSet);
    UnionSet fillUSet = new UnionSet(fillSet);
    UnionSet fillUSet = new UnionSet(fillSet);

    UnionSet widthUSet = new UnionSet(widthSet);
    UnionSet widthUSet = new UnionSet(widthSet);
    UnionSet widthUSet = new UnionSet(widthSet);
*/



    for (int k = 0; k < fills.length; k++)
    {
      if (k%2 == 0)
      { 
        evenFillSetVect.add(fills[k]); 
        evenWidthSetVect.add(widths[k]);
      }
      else
      { 
        oddFillSetVect.add(fills[k]); 
        oddWidthSetVect.add(widths[k]);
      }

    }

    Irregular2DSet[] evenFillSet = 
        (Irregular2DSet[]) evenFillSetVect.toArray(new Irregular2DSet[evenFillSetVect.size()]);
    Irregular2DSet[] oddFillSet = 
        (Irregular2DSet[]) oddFillSetVect.toArray(new Irregular2DSet[oddFillSetVect.size()]);

    UnionSet[] evenWidthSet = 
        (UnionSet[]) evenWidthSetVect.toArray(new UnionSet[evenWidthSetVect.size()]);
    UnionSet[] oddWidthSet = 
        (UnionSet[]) oddWidthSetVect.toArray(new UnionSet[oddWidthSetVect.size()]);

    UnionSet evenFillUSet = new UnionSet(evenFillSet);
    UnionSet oddFillUSet = new UnionSet(oddFillSet);

    UnionSet evenWidthUSet = new UnionSet(evenWidthSet);
    UnionSet oddWidthUSet = new UnionSet(oddWidthSet);

    MathType[] mPathType = new MathType[]{ red, green, blue, z, paths[0].getType()};
    MathType[] mWidthType = new MathType[]{ red, green, blue, z, widths[0].getType()};
    MathType[] mFillType = new MathType[]{ red, green, blue, alpha, z, fills[0].getType()};
    MathType[] mLabelType = new MathType[]{ red, green, blue, z, labels[0].getType()};
    MathType[] mFillUType = new MathType[]{ red, green, blue, alpha, z, evenFillUSet.getType()};
    MathType[] mWidthUType = new MathType[]{ red, green, blue, z, evenWidthUSet.getType()};

    TupleType pathTupleType = new TupleType(mPathType);
    TupleType widthTupleType = new TupleType(mWidthType);
    TupleType fillTupleType = new TupleType(mFillType);
    TupleType labelTupleType = new TupleType(mLabelType);
    TupleType fillUTupleType = new TupleType(mFillUType);
    TupleType widthUTupleType = new TupleType(mWidthUType);

    Tuple[] pathTuple = new Tuple[NUM_ANGLES];
    Tuple[] widthTuple = new Tuple[NUM_ANGLES];
    Tuple[] fillTuple = new Tuple[NUM_ANGLES];
    Tuple[] labelTuple = new Tuple[NUM_ANGLES];
    Tuple[] fillUTuple = new Tuple[2];
    Tuple[] widthUTuple = new Tuple[2];

    Data evenFillData[] = { 
                            new Real(red, 1.0f),
                            new Real(green, 1.0f),
                            new Real(blue, 0.0f),
                            new Real(alpha, 1.0f),
                            new Real(z, -1.0f),
                            evenFillUSet};
    Data oddFillData[] = { 
                            new Real(red, 0.63f),
                            new Real(green, 0.13f),
                            new Real(blue, 0.94f),
                            new Real(alpha, 0.5f),
                            new Real(z, -0.9f),
                            oddFillUSet};
    Data evenWidthData[] = { 
                            new Real(red, 1.0f),
                            new Real(green, 1.0f),
                            new Real(blue, 0.0f),
                            new Real(z, -0.9f),
                            evenWidthUSet};
    Data oddWidthData[] = { 
                            new Real(red, 0.63f),
                            new Real(green, 0.13f),
                            new Real(blue, 0.94f),
                            new Real(z, -0.9f),
                            oddWidthUSet};

    fillUTuple[0] = new Tuple(fillUTupleType, evenFillData);
    fillUTuple[1] = new Tuple(fillUTupleType, oddFillData);

    widthUTuple[0] = new Tuple(widthUTupleType, evenWidthData);
    widthUTuple[1] = new Tuple(widthUTupleType, oddWidthData);

    RealType dummy1 = RealType.getRealType("dummy1");
    Integer1DSet fillSet = new Integer1DSet(dummy1, 2);
    FunctionType fillsUFType = new FunctionType(dummy1, fillUTupleType);
    FieldImpl fillsUField = new FieldImpl(fillsUFType, fillSet);
    fillsUField.setSamples(fillUTuple, false);

    RealType dummy2 = RealType.getRealType("dummy2");
    Integer1DSet widthSet = new Integer1DSet(dummy2, 2);
    FunctionType widthsUFType = new FunctionType(dummy2, widthUTupleType);
    FieldImpl widthsUField = new FieldImpl(widthsUFType, widthSet);
    widthsUField.setSamples(widthUTuple, false);


    for (int i = 0; i < NUM_ANGLES; i++)
    {
      float depth = (float) (-1.0 + (0.1*i));
      float redChanger = 1.0f;
      float blueChanger = 1.0f;
      float greenChanger = 1.0f;

      if (i%3 == 0)
      {
        redChanger = 1.0f;
        greenChanger = 0.0f;
        blueChanger = 0.0f;
      }
      if (i%3 == 1)
      {
        redChanger = 1.0f;
        greenChanger = 1.0f;
        blueChanger = 1.0f;
      }
      if (i%3 == 2)
      {
        redChanger = 0.0f;
        greenChanger = 1.0f;
        blueChanger = 0.0f;
      }

/*
      float redChanger = (float)(i*(1.0/(NUM_ANGLES-1)));
      float blueChanger = (float)(1.0 - redChanger);
      double mid = 1.00;
      float greenChanger;
      if ((2*redChanger) >= mid )
      { greenChanger = (float)(2*mid - 2*redChanger); }
      else
      { greenChanger = 2*redChanger; }
*/

      Data pathData[] = { new Real(red, 1.0f),
                      new Real(green, 1.0f),
                      new Real(blue, 1.0f),
                      new Real(z, -0.9f),
                      paths[i] };
      Data widthData[] = { new Real(red, redChanger),
                      new Real(green, greenChanger),
                      new Real(blue, blueChanger ),
                      new Real(z, -0.9f),
                      widths[i] };
      Data fillData[] = { new Real(red, redChanger),
                      new Real(green, greenChanger),
                      new Real(blue, blueChanger ),
                      new Real(alpha, 0.5f),
//                      new Real(z, -1.0f),
                      new Real(z, depth),
                      fills[i] };
      Data labelData[] = { new Real(red, 1.0f),
                      new Real(green, 1.0f),
                      new Real(blue, 1.0f),
                      new Real(z, -1.0f),
                     labels[i] };

      pathTuple[i] = new Tuple(pathTupleType, pathData);
      widthTuple[i] = new Tuple(widthTupleType, widthData);
      fillTuple[i] = new Tuple(fillTupleType, fillData);
      labelTuple[i] = new Tuple(labelTupleType, labelData);

    }

    Integer1DSet indexSet = new Integer1DSet(index, NUM_ANGLES);
    ConstantMap[] dashLineMap = { new ConstantMap(GraphicsModeControl.DASH_STYLE, Display.LineStyle)};
    ConstantMap[] alphaCMap = { new ConstantMap(0.6f, Display.Alpha)};

    FunctionType pathsFType = new FunctionType(index, pathTupleType);
    FunctionType labelsFType = new FunctionType(index, labelTupleType);
    FunctionType widthsFType = new FunctionType(index, widthTupleType);
    FunctionType fillsFType = new FunctionType(index, fillTupleType);
/*
    FunctionType pathsFType = new FunctionType( index, paths[0].getType() );
    FunctionType labelsFType = new FunctionType( index, labels[0].getType() );
    FunctionType widthsFType = new FunctionType( index, widths[0].getType() );
    FunctionType fillsFType = new FunctionType( index, fills[0].getType() );
*/
    FieldImpl pathsField = new FieldImpl(pathsFType, indexSet);
    FieldImpl widthsField = new FieldImpl(widthsFType, indexSet);
    FieldImpl fillsField = new FieldImpl(fillsFType, indexSet);
    FieldImpl labelsField = new FieldImpl(labelsFType, indexSet);
/*
    pathsField.setSamples(paths, false);
    widthsField.setSamples(widths, false);
    fillsField.setSamples(fills, false);
    labelsField.setSamples(labels, false);
*/
    pathsField.setSamples(pathTuple, false);
    widthsField.setSamples(widthTuple, false);
    fillsField.setSamples(fillTuple, false);
    labelsField.setSamples(labelTuple, false);

    allBeamsRef.setData(pathsField);
    allBeamWidthsRef.setData(widthsField);
//    allBeamWidthsRef.setData(widthsUField);
    allBeamFillsRef.setData(fillsField);
//    allBeamFillsRef.setData(fillsUField);
    allBeamLabelsRef.setData(labelsField);

    if (zProfile_ff != null)
    { setZProfile( getZProfile() ); }

    rhiDisplay.reDisplayAll();

  }


  public void makeHU_SawTooth() throws VisADException, RemoteException
  {
    ConstantMap[] sawMap = { new ConstantMap( 1.0f, Display.Red ),
                           new ConstantMap( 1.0f, Display.Green ),
                           new ConstantMap( 0.0f, Display.Blue ),
                           new ConstantMap( 0.1f, Display.ZAxis ) };

    ConstantMap[] sawLineMap = { new ConstantMap( 0.0f, Display.Red ),
                           new ConstantMap( 1.0f, Display.Green ),
                           new ConstantMap( 0.0f, Display.Blue )};

    ConstantMap[] sawPtMap = { new ConstantMap( 0.0f, Display.Red ),
                           new ConstantMap( 1.0f, Display.Green ),
                           new ConstantMap( 0.0f, Display.Blue ),
                           new ConstantMap( 1.0f, Display.XAxis ),
                           new ConstantMap( 3.50f, Display.PointSize )  };

    htPointCoords = new Real(h, 20.0);

    Gridded2DSet initHLine = createHorizontalHeightLine(htPointCoords);
    UnionSet initSawToothSet = hu.createSawTooth(htPointCoords, beamPath.getBeamPaths());

    sawToothPointRef.setData( htPointCoords );
    sawToothLineRef.setData( initHLine );
    sawToothRef.setData( initSawToothSet );
    
      CellImpl htCell = new CellImpl() {
      public void doAction() throws RemoteException, VisADException {

        // get the data object from the reference. We know it's a Real
        Real htCoords = (Real) sawToothPointRef.getData();

        // test if cursor postion (northing) has changed significantly
        if( Util.isApproximatelyEqual(  htCoords.getValue(),
                                        htPointCoords.getValue() )){

          return; // leave method and thus don't update line
        }

        double htCoordsVal = htCoords.getValue();
        if (htCoordsVal > 25.0)
        { htCoordsVal = 25.0; }
        else if ( htCoordsVal < 0.0)
        { htCoordsVal = 0.0; }
        else
        { htCoordsVal = htCoords.getValue(); }
        Real newHtCoord = new Real(h, htCoordsVal);
        doPoint(newHtCoord, sawToothPointRef);

        Gridded2DSet hLine = createHorizontalHeightLine(newHtCoord);

        UnionSet sawToothSet = hu.createSawTooth(newHtCoord, beamPath.getBeamPaths());

        sawToothLineRef.setData(hLine);
        sawToothRef.setData( sawToothSet );

        htPointCoords = newHtCoord;
      }
    };

    htCell.addReference(sawToothPointRef);

    if ( rhiDisplay.findReference(sawToothPointRef) == null)
    { rhiDisplay.addReferences(sawToothPointRend, sawToothPointRef, sawPtMap); }

    if ( rhiDisplay.findReference(sawToothLineRef) == null)
    { rhiDisplay.addReferences(sawToothLineRend, sawToothLineRef, sawLineMap); }

    if ( rhiDisplay.findReference(sawToothRef) == null)
    { rhiDisplay.addReferences(sawToothRend, sawToothRef, sawMap); }

    sawToothPointRend.toggle(true);
    sawToothLineRend.toggle(true);
    sawToothRend.toggle(true);
  }

  public Gridded2DSet createHorizontalHeightLine(Real ptHt) throws VisADException, RemoteException
  {

    Linear1DSet ranSet = new Linear1DSet(h, 401.0, 0.0, 400);
    float[][] ranVals = ranSet.getSamples();
    float[][] htVals = new float[1][ranSet.getLength()];
    java.util.Arrays.fill(htVals[0], (float)ptHt.getValue());
    float[][] ranHtVals = new float[][]{ ranVals[0], htVals[0] };
    RealTupleType ranHtTT = new RealTupleType(s, h);
    Gridded2DSet horizLine = new Gridded2DSet(ranHtTT, ranHtVals, ranSet.getLength());

    return horizLine;
  }

  private static void doPoint(Real rtt,
    DataReferenceImpl sawToothPointRef) throws VisADException, RemoteException
  {
    sawToothPointRef.setData(rtt);
  }

  public Set MakeGC() throws VisADException, RemoteException
  {
    float beamWidth = 0.5f;
    float elevAngle = getCurrentElevationAngle();

    // ### This is a >= number; i.e. get percent blockage >= X
    int percent = (int) getPercentBlocked();

    // ### Make sure that blockage percentage is > 0
    if ( percent < 1)
    { percent = 1; }
    if (percent > 100)
    { return null; }

    CoordConversion cc = new CoordConversion();
    Vector latVect = new Vector();
    Vector lonVect = new Vector();
    Gridded2DSet blockPointsSet;
    Irregular2DSet[] allRadials;
    Vector radialVect = new Vector();


    for (float i = 0.5f; i < 360; i = i + 0.5f)
    {
      for (int j = 0; j < MAX_RANGE*1000; j = j+1000)
      {
        int block = tb.computePercentBlocked(beamWidth, elevAngle, (float)i, (float)j);

        if (block >= percent)
        {
          double[] ll = cc.AzRangetoLL( getLat(), getLon(), i, j, elevAngle);
          Irregular2DSet set = (Irregular2DSet)makeBeamFill( 
                        (float)i, beamWidth, elevAngle, (float)j, (float)MAX_RANGE*1000);
          radialVect.add(set);
          break;
        }
      }
    }

    allRadials = (Irregular2DSet[]) radialVect.toArray( new Irregular2DSet[radialVect.size()] );
    if (allRadials.length == 0)
    { return null; }

    RealTupleType latLon_tt = new RealTupleType(lat, lon);
    UnionSet beamBlockage = new UnionSet(latLon_tt, allRadials);

    return beamBlockage;
  }


  public Set makeBeamFill(float azimuth, float beamWidth, float elevAngle, float beginRan,
                                              float endRan) throws VisADException, RemoteException
  {
    // ## From radar point of view
    float leftAz = (float) (azimuth - 0.5f * beamWidth);
    float rightAz = (float) (azimuth + 0.5f * beamWidth);

    // ### return vals are lat/lon/ht
    double[] leftClose =
               CoordConversion.AzRangetoLL(getLat(), getLon(), leftAz, beginRan, elevAngle);
    double[] leftFar =
               CoordConversion.AzRangetoLL(getLat(), getLon(), leftAz, endRan, elevAngle);
    double[] rightClose =
               CoordConversion.AzRangetoLL(getLat(), getLon(), rightAz, beginRan, elevAngle);
    double[] rightFar =
               CoordConversion.AzRangetoLL(getLat(), getLon(), rightAz, endRan, elevAngle);

    float[] latVals = 
     new float[]{ (float)rightFar[0], (float)rightClose[0], (float)leftClose[0], (float)leftFar[0]};
    float[] lonVals =
     new float[]{ (float)rightFar[1], (float)rightClose[1], (float)leftClose[1], (float)leftFar[1]};

    float[][] corners = new float[][] { latVals, lonVals};


    RealTupleType latLon_tt = new RealTupleType(lat, lon);
    Gridded2DSet cornersSet = new Gridded2DSet(latLon_tt, corners, 4);
    Irregular2DSet filledWidthSet = DelaunayCustom.fill(cornersSet);
    return filledWidthSet;

  }

  public JPanel makeColorBar() throws RemoteException, VisADException
  {
    JPanel panel = new JPanel();

    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setAlignmentY(JPanel.TOP_ALIGNMENT);
    panel.setAlignmentX(JPanel.LEFT_ALIGNMENT);


    float max = Float.NaN;
    float min = Float.NaN;
//    float init = Float.NaN;
    double[] ran = valueMap.getRange();
    min = (float)ran[0];
    max = (float)ran[1];


//    float min = 0.0f;
//    float max = 6.0f;
    float init = (float)((min+max)/2.0f);
//    String name = new String("Ht Underestimate (km)");
    String name = valueMap.getOverrideUnit().toString();
    ArrowSlider slider = new ArrowSlider(min, max, init, name);
    SliderLabel label = new SliderLabel(slider);
    LabeledColorWidget lcw = new LabeledColorWidget( valueMap );
    ColorPreview preview = lcw.getPreview();

    panel.add(preview);
    panel.add(label);
    panel.add(slider);


    return panel;
  }

  /**
   * Adds a button to a toolbar.  From VisAD SpreadSheet
   */
  protected AbstractButton addToolbarButton(String bLabel, String tooltip,
   AbstractButton ab, JComponent parent, GridBagConstraints g)
  {
    if (bLabel.compareToIgnoreCase("") != 0)
    {
      AbstractButton b = null;
      if (ab instanceof JButton)
      { b = new JButton(bLabel); }
      if (ab instanceof JToggleButton)
      { b = new JToggleButton(bLabel); }
      
      b.setAlignmentY(JButton.CENTER_ALIGNMENT);
      b.setToolTipText(tooltip);
      b.addActionListener(this);
      parent.add(b, g);
      return b;
    }
    else return null;
  }

  public float[][] ranAzToLatLon(float ran, float az) throws VisADException, RemoteException
  {
    Radar2DCoordinateSystem coords = new Radar2DCoordinateSystem(getLat(), getLon());
    float[][] inCoords = { {ran}, {az} };
    float[][] latLon = coords.toReference(inCoords);
    return latLon;
  }

  public float[][] latLonToRanAz(float[][] vals) throws VisADException, RemoteException
  {
    Radar2DCoordinateSystem r2D = new Radar2DCoordinateSystem(getLat(), getLon());

    float[][] target = r2D.fromReference(vals);
    return target;
  }


//########################################################################
  public void setLat( double lat )
  { END_Y = (float) lat; }

  public void setLon( double lon )
  { END_X = (float) lon; }

  public void setAlt( double alt )
  { END_Z = (float) alt; }

  public float getLat()
  { return END_Y; }

  public float getLon()
  { return END_X; }

  public float getAlt()
  { return END_Z; }

  public void setCurrentElevationAngle(String st)
  { currentElevationAngle = Float.parseFloat(st); }

  public float getCurrentElevationAngle()
  { return currentElevationAngle; }

  public void setPercentBlocked(String st)
  { currentPctBlocked = Integer.parseInt(st); }

  public int getPercentBlocked()
  { return currentPctBlocked; }

  public void setVCPUnionSet(UnionSet vcpU)
  {  
    vcpUSet = null;
    vcpUSet = vcpU;
  }

  public UnionSet getVCPUnionSet()
  { return vcpUSet; }

  public void setVCPBeamWidths(UnionSet vcpW)
  { 
    vcpWidths = null;
    vcpWidths = vcpW;
  }

  public UnionSet getVCPBeamWidths()
  { return vcpWidths; }

  public void setVcpHashTable(Hashtable vcp)
  { vcpHashTable = vcp; }

  public Hashtable getVcpHashTable()
  { return vcpHashTable; }

  public void setSoundingData(Vector v)
  { 
    soundingData = new Vector();
    soundingData = (Vector)v.clone();
    beamPath.setSounding(soundingData);
    try 
    {
      nsPathRef.setData( beamPath.getNSBeamPath() );
      nsWidthRef.setData( beamPath.getNSBeamWidth() );
    }
    catch (Exception ex)
    {
      String err0 = new String("RHIpanel: setSounding Error> ");
      String err1 = ex.toString();
      ErrorFrame ef = new ErrorFrame(new String[]{ err0, err1});
    }

  }

  public Vector getSoundingData()
  { return soundingData; }

  public void setSoundingInfo(String si)
  {
    soundingInfo = new String();
    soundingInfo = si;
  }

  public String getSoundingInfo()
  { return soundingInfo; }


  public void setZProfile(FlatField zp)
  {
    zProfile_ff = (FlatField) zp.clone(); 

    try
    {
      algs = new Algorithms(
                          beamPath.getBeamPaths(),
                          getZProfile(),
                          new double[] { zeroC, mTwentyC},
                          getAlt()
                           );
    sampledSHIRef.setData( algs.getSampledSHI() );
    sampledPOSHRef.setData( algs.getSampledPOSH() );
    sampledMESHRef.setData( algs.getSampledMESH() );
    sampledVILRef.setData( algs.getSampledVIL() );
    sampledVILDensityRef.setData( algs.getSampledVILDensity() );
    theoreticalSHIRef.setData( algs.getTheoreticalSHI() );
    theoreticalPOSHRef.setData( algs.getTheoreticalPOSH() );
    theoreticalMESHRef.setData( algs.getTheoreticalMESH() );
    theoreticalVILRef.setData( algs.getTheoreticalVIL() );
    theoreticalVILDensityRef.setData( algs.getTheoreticalVILDensity() );

    // ### This resets the Y-Axes
/*
    rhiDisplay.removeMap(shiMap);
    rhiDisplay.addMap(shiMap);
*/
    }
    catch (Exception algEx)
    { System.err.println("Error calling Algs: " + algEx); }
  }

  public void setZProfileFreezeLevels(Real[] fz)
  {
    zeroC = fz[0].getValue();
    mTwentyC = fz[1].getValue();
  }


  public FlatField getZProfile()
  { return zProfile_ff; }

  protected void cleanUp() throws VisADException, RemoteException, Throwable
  {

    if (rhiDisplay != null)
    {
      rhiDisplay.removeAllReferences();
      rhiDisplay.removeAllSlaves();
      rhiDisplay.destroy();
      rhiDisplay.destroyUniverse();
    }

    tb = null;
    hu = null;
    gridbag = null; 
    c = null;
    gc.removeActionListener(this);
    reset.removeActionListener(this);
    singleBeamToggle.removeActionListener(this);
    beamWidthToggle.removeActionListener(this);
    beamFillToggle.removeActionListener(this);
    labelToggle.removeActionListener(this);
    htUEFillToggle.removeActionListener(this);
    htUEToothToggle.removeActionListener(this);

    finalize();

  }

}


