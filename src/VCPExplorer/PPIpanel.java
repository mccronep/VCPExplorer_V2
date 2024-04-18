package VCPExplorer;

import java.*;
import java.text.*;
import java.util.zip.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.rmi.RemoteException;
import java.io.IOException;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.border.*;
import javax.swing.BorderFactory.*;

import visad.*;
import visad.java3d.*;
import visad.util.*;
import visad.data.netcdf.Plain;
import visad.data.BadFormException;
import visad.bom.Radar2DCoordinateSystem;
import visad.bom.RubberBandBoxRendererJ3D;

public class PPIpanel extends JPanel implements ActionListener
{

  static GridBagLayout gridbag = new GridBagLayout();
  static GridBagConstraints c = new GridBagConstraints();
  private JFrame parentFrame;

  Border loweredetched = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
  Border raisedbevel = BorderFactory.createRaisedBevelBorder();
  Border loweredbevel = BorderFactory.createLoweredBevelBorder();
  Border compound = BorderFactory.createCompoundBorder(raisedbevel, loweredbevel);
  TitledBorder titled;

  private VCPRPE rpe;
  private Plain ncReader;
  private Data plainData;
  private RealType lat, lon, alt, value;
  private TextType text;
  private ScalarMap yMap, xMap, rMap, vMap, tMap;
  protected FlatField terrainData_ff;
  protected DisplayImplJ3D display;
  protected Radar2DCoordinateSystem radarCoords;
  private String fileExtension, dir;
  private String[] curRadar;
//  protected double Latitude, Longitude, Altitude;
  protected double radarLatitude, radarLongitude, radarAltitude;
  private double[] projMatrix, initLatRange, initLonRange;
  protected DataReferenceImpl refTerrainData, ringRef, ringLabelRef;
  private DataRenderer ringRend, ringLabelRend;
  private Unit kilometer, km, nMile, kFt;
  private static double MAX_RANGE;

  private JToolBar Toolbar;
  private JButton reset;
  private JToggleButton ringsToggle;

  public PPIpanel(JFrame frame, String[] currentRadar, double maxRange) 
                                                 throws VisADException, RemoteException
  {

    setLayout(gridbag);
    titled = BorderFactory.createTitledBorder(compound, "PPI Panel");
    setBorder(titled);
    parentFrame = frame;

    Toolbar = new JToolBar();
    Toolbar.setBackground(Color.lightGray);
    Toolbar.setBorder(new EtchedBorder());
    Toolbar.setFloatable(false);

    reset = (JButton) addToolbarButton("Reset", "Reset Orientation", new JButton(), Toolbar);
    ringsToggle = (JToggleButton)
         addToolbarButton("Rings", "Toggle Range Rings On/Off", new JToggleButton(), Toolbar);

    MAX_RANGE = maxRange;

    try
    {
      kilometer = visad.data.units.Parser.parse("kilometer").clone("km");
      nMile = visad.data.units.Parser.parse("nautical_mile").clone("nm");
      kFt = visad.data.units.Parser.parse("kilofeet").clone("kFt");
    }
    catch (visad.data.units.ParseException P)
    { System.out.println("Parse exception: " + P); }


    dir = new String("TerrainFiles/Terrain/");
    fileExtension = new String("_terrain.vad.gz");
    curRadar = currentRadar;

    radarLatitude = Double.parseDouble( currentRadar[4] );
    radarLongitude = Double.parseDouble( currentRadar[5] );
    radarAltitude = Double.parseDouble( currentRadar[6] );

    refTerrainData = new DataReferenceImpl("TerrainData");
/*
    ncReader = new Plain();

    // read the netCDF file into a data object
    try 
    { terrainData_ff = (FlatField) ncReader.open( getTerrain(curRadar) ); }
    catch (IOException e)
    { System.out.println("IOException: " + e); }
*/

    terrainData_ff = null;
    try
    {terrainData_ff = readGZipFile( getTerrain(curRadar) ); }
    catch (Exception ioe)
    { System.err.println("PPIpanel (readGZipFile): " + ioe); }


    refTerrainData.setData(terrainData_ff);

    lat = RealType.getRealType("Latitude");
    lon = RealType.getRealType("Longitude");
    alt = RealType.getRealType("Altitude");
    value = RealType.getRealType("value");
    text = TextType.getTextType("Text");

    // Create and set ScalarMaps to add data to the display
    yMap = new ScalarMap(lat, Display.YAxis);
    xMap = new ScalarMap(lon, Display.XAxis);
    rMap = new ScalarMap(alt, Display.RGB);
    vMap = new ScalarMap(value, Display.IsoContour);
    tMap = new ScalarMap(text, Display.Text);

    // create a simple display for viewing the data
//    display = new DisplayImplJ3D("TerrainDisplay", new TwoDDisplayRendererJ3D());
    display = new DisplayImplJ3D("TerrainDisplay");

    // Show axis labels
    DisplayRendererJ3D dr = (DisplayRendererJ3D)display.getDisplayRenderer();
    MouseBehaviorJ3D mb = (MouseBehaviorJ3D)dr.getMouseBehavior();

    MouseHelper mh = mb.getMouseHelper();

    int[][][] mouseButtonTable = new int[][][]
    { 
      { {MouseHelper.DIRECT, MouseHelper.DIRECT}, {MouseHelper.DIRECT, MouseHelper.DIRECT} },
      { {MouseHelper.CURSOR_TRANSLATE, MouseHelper.CURSOR_TRANSLATE}, {MouseHelper.CURSOR_TRANSLATE, MouseHelper.CURSOR_TRANSLATE} },
      { {MouseHelper.TRANSLATE, MouseHelper.ZOOM}, {MouseHelper.TRANSLATE, MouseHelper.ZOOM} }
    };

    mh.setFunctionMap(mouseButtonTable);

    GraphicsModeControl dispGMC = (GraphicsModeControl)display.getGraphicsModeControl();
    dispGMC.setScaleEnable(true);
    dispGMC.setProjectionPolicy(DisplayImplJ3D.PARALLEL_PROJECTION);
    ProjectionControl initPC = display.getProjectionControl();
    projMatrix = initPC.getMatrix();
    double scale = 1.3;
    double[] newMatrix = mb.make_matrix(0.0, 0.0, 0.0, scale, 0.0, 0.0, 0.0);
    newMatrix = mb.multiply_matrix(newMatrix, projMatrix);
    initPC.setMatrix(newMatrix);
    projMatrix = initPC.getMatrix();

    display.addMap(xMap);
    display.addMap(yMap);
    display.addMap(rMap);
    display.addMap(vMap);
    display.addMap(tMap);

    AxisScale xAxis = xMap.getAxisScale();
    xAxis.setTitle("Longitude (deg)");
    AxisScale yAyis = yMap.getAxisScale();
    yAyis.setTitle("Latitude(deg)");
    rMap.setOverrideUnit(kFt);



    // ### Next 5 lines grab the lat/lon range values - to be used in reset()
    Gridded2DSet terrainDomainSet = (Gridded2DSet) terrainData_ff.getDomainSet();
    float[] terrainDomainHiVals = terrainDomainSet.getHi();
    float[] terrainDomainLowVals = terrainDomainSet.getLow();
    initLonRange = new double[] { terrainDomainLowVals[0], terrainDomainHiVals[0] };
    initLatRange = new double[] { terrainDomainLowVals[1], terrainDomainHiVals[1] };

    ContourControl temp_cc = (ContourControl)vMap.getControl();
    temp_cc.setContourInterval(1.0f, 1.0f, 1.0f, 1.0f);

    TextControl tcontrol = (TextControl) tMap.getControl();
    tcontrol.setSize(0.50);
    tcontrol.setJustification(TextControl.Justification.CENTER);


    float[] hiVals = ((Gridded2DSet)terrainData_ff.getDomainSet()).getHi();
    float[] lowVals = ((Gridded2DSet)terrainData_ff.getDomainSet()).getLow();

    double latLow = lowVals[1];
    double lonLow = lowVals[0];
    double latHi = hiVals[1];
    double lonHi = hiVals[0];

    yMap.setRange(latLow, latHi);
    xMap.setRange(lonLow, lonHi);


    ConstantMap[] zlMap = { new ConstantMap (-1.0f, Display.ZAxis) };
    ConstantMap[] ringCMap = { new ConstantMap (-0.8f, Display.ZAxis) };
    ConstantMap[] ringLabelCMap = { new ConstantMap (-0.8f, Display.ZAxis) };

    ringRef = new DataReferenceImpl("ringRef");
    ringRend = new DefaultRendererJ3D();
    ringLabelRef = new DataReferenceImpl("ringLabelRef");
    ringLabelRend = new DefaultRendererJ3D();

    display.addReference(refTerrainData, zlMap);
    display.addReferences(ringRend, ringRef, ringCMap);
    display.addReferences(ringLabelRend, ringLabelRef, ringLabelCMap);

    // ### RubberBandZoom
    RealTupleType lonLat_tt = new RealTupleType(lon, lat);
    Gridded2DSet zoomSet = new Gridded2DSet(lonLat_tt, null, 1);
    final DataReferenceImpl zoomRef = new DataReferenceImpl("zoomRef");
    zoomRef.setData(zoomSet);
    // m = 1 needed to make rb-box DirectManipulationRenderer active on shift-rt click,
    // else affects other DMRs, like sawTooth
    int m = 1;
    int v = 1;
    display.addReferences(new RubberBandBoxRendererJ3D(lon, lat, m, v), zoomRef);

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
          xMap.setRange(zoomSamples[0][0], zoomSamples[0][1]);
          yMap.setRange(zoomSamples[1][0], zoomSamples[1][1]);
        }
      }
    };
    zoomCell.addReference(zoomRef);

    makeRings();

    Component comp = display.getComponent();

    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1.0;
    c.weighty = 1.0;
    c.fill = GridBagConstraints.BOTH;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.anchor = GridBagConstraints.LINE_START;
    gridbag.setConstraints( comp, c);
    add(comp);
  
    c.gridx = GridBagConstraints.REMAINDER;
    c.gridy = 1;
    c.weightx = 0.0;
    c.weighty = 0.0;
    c.fill = GridBagConstraints.HORIZONTAL;
    JPanel colorBar = makeColorBar();
    gridbag.setConstraints(colorBar, c);
    add( colorBar );

    c.gridx = 0;
    c.gridy = 2;
    c.fill = GridBagConstraints.NONE;
    c.anchor = GridBagConstraints.LINE_START;
    gridbag.setConstraints( Toolbar, c);
    add(Toolbar);

    display.reAutoScale();
  }

  public void actionPerformed(ActionEvent e)
  { 
    if ( e.getActionCommand().equalsIgnoreCase("Reset") )
    { resetOrientation(); }
    if ( e.getActionCommand().equalsIgnoreCase("Rings") )
    {
      if (ringsToggle.isSelected())
      {
        ringRend.toggle(false);
        ringLabelRend.toggle(false);
      }
      if (!ringsToggle.isSelected())
      {
        ringRend.toggle(true);
        ringLabelRend.toggle(true);
      }
    }
  }


  /**
   * Resets the display projection to its original value.
   * Borrowed from VisAD SpreadSheet
   */
  public void resetOrientation() {
    if (display != null) {
      ProjectionControl pc = display.getProjectionControl();
      if (pc != null) {
        try 
        {
          pc.setMatrix(projMatrix);
          xMap.setRange(initLonRange[0], initLonRange[1]);
          yMap.setRange(initLatRange[0], initLatRange[1]);
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

  public void makeRings() throws VisADException, RemoteException
  {
    UnionSet uSet;

    int NUM_RINGS = 4;
    int RING_RANGE_INTVL = 0;
    Radar2DCoordinateSystem radarRings = new Radar2DCoordinateSystem((float) radarLatitude, 
                                                                         (float) radarLongitude);
    float[][] ringDomain = new float[2][720];
    float[][] latLon;

    RealType index = RealType.getRealType("index");
    MathType[] mtypes = {lat, lon, text};
    TupleType text_tuple = new TupleType(mtypes);
    FunctionType text_function = new FunctionType(index, text_tuple);
    Integer1DSet indexSet = new Integer1DSet(index, NUM_RINGS);
    FieldImpl text_ff = new FieldImpl(text_function, indexSet);

    Real distKm = new Real(RealType.getRealType("ran", kilometer), MAX_RANGE);

    if (MAX_RANGE > 300) // must be 88D
    {
      RING_RANGE_INTVL = 50;
    }
    else
    {
      RING_RANGE_INTVL = 20;
    }

    Gridded2DSet[] ringSet = new Gridded2DSet[NUM_RINGS];

    for (int ring = 0; ring < NUM_RINGS; ring++)
    {
      float dist = (ring+1)*RING_RANGE_INTVL*1000.0f;

      // ### If units are km instead of nm, this will change
      Real distNm = new Real(RealType.getRealType("ringDist", nMile), dist);

      Integer iGer = new Integer((ring+1) * RING_RANGE_INTVL);
      String unit = new String(" nm");
      String label = new String( iGer.toString() + unit);

      float[][] latLonLabel;
      if (RING_RANGE_INTVL > 20)
      { latLonLabel = ranAzToLatLon((float) distNm.getValue(kilometer)-15000.0f, 0.0f); }
      else
      { latLonLabel = ranAzToLatLon((float) distNm.getValue(kilometer)-5000.0f, 0.0f); }

      Text textLabel = new Text(text, label);
      Data[] td = {new Real(lat, latLonLabel[0][0]),
                   new Real(lon, latLonLabel[1][0]),
                   new Text(text,label)};
      Tuple tt = new Tuple(text_tuple, td);
      text_ff.setSample(ring, tt);


      for (int i = 0; i < 720; i++)
      {
        float[][] rPoints = { { (float) distNm.getValue(kilometer)}, {(float) (i / 2)} };
        latLon = radarRings.toReference(rPoints);

        ringDomain[0][i] =  latLon[0][latLon[0].length-1];
        ringDomain[1][i] =  latLon[1][latLon[1].length-1];
      }
      ringSet[ring] = new Gridded2DSet(new RealTupleType(lat, lon), ringDomain, 720);
    }

    uSet = new UnionSet(ringSet);
    ringRef.setData(uSet);
    ringLabelRef.setData(text_ff);
  }

  public float[][] ranAzToLatLon(float ran, float az) throws VisADException, RemoteException
  {
    Radar2DCoordinateSystem coords = new Radar2DCoordinateSystem((float) radarLatitude,
                                                                         (float) radarLongitude);
    float[][] inCoords = { {ran}, {az} };
    float[][] latLon = coords.toReference(inCoords);
    return latLon;
  }


  public JPanel makeColorBar() throws RemoteException, VisADException
  {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setAlignmentY(JPanel.TOP_ALIGNMENT);
    panel.setAlignmentX(JPanel.LEFT_ALIGNMENT);


    float max = Float.NaN;
    float min = Float.NaN;
    float init = Float.NaN;
    double[] ran = new double[] {-1.0, -1.0};
    String name = rMap.getOverrideUnit().toString();

    do
    {
      ran = rMap.getRange();
      min = (float)ran[0];
      max = (float)ran[1];
      init = (float)((min+max)/2.0f);
    }
    while (Float.compare(max, Float.NaN) == 0);

    ArrowSlider slider = new ArrowSlider(min, max, (min + max) / 2, name);
    SliderLabel label = new SliderLabel(slider);
    LabeledColorWidget lcw = new LabeledColorWidget( rMap );
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
   AbstractButton ab, JComponent parent)
  {
/*
    URL url = SpreadSheet.class.getResource(file + ".gif");
    ImageIcon icon = new ImageIcon(url);
*/
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
      parent.add(b);
      return b;
    }
    else return null;
  }



  public void makePPI(String[] curRadar)  throws RemoteException, VisADException
  {

  }

  public String getTerrain(String[] curRadar)
  {
    String terrainFile = curRadar[0].toLowerCase().concat(fileExtension);
    String terrainLoc = dir.concat(terrainFile);

    return terrainLoc;
  }

  public static FlatField readGZipFile(String filename)
    throws IOException, VisADException
  {
    visad.data.visad.BinaryReader reader =  new visad.data.visad.BinaryReader(
        new GZIPInputStream(new FileInputStream(filename)));

    return (FlatField) reader.getData();
  }



  protected void cleanUp() throws VisADException, RemoteException, Throwable
  {
    reset.removeActionListener(this);
    if (display != null)
    {
      display.removeAllReferences();
      display.removeAllSlaves();
      display.destroy();
      display.destroyUniverse();
    }
    finalize();
  }

}

