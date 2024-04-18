package VCPExplorer;

import java.*;
import java.rmi.RemoteException;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import javax.swing.BorderFactory.*;


import visad.*;
import visad.util.*;
import visad.java3d.*;


public class Zprofile extends JFrame implements ActionListener
{
  private JPanel panel;

  protected JButton submit;
  private JButton oReset;
  private JToolBar tBar;
  private DisplayImplJ3D display;
  private DirectManipulationRendererJ3D[] pointsRend;
  private DataReferenceImpl[] pointsRef, linesRef;
  private DirectManipulationRendererJ3D zeroCRend, mTwentyCRend;
  private DataReferenceImpl zeroCRef, mTwentyCRef, zeroLineRef, mTwentyLineRef;
  private Irregular2DSet[] lines;
  private RealType reflectivity, height, ref;
  private RealTupleType refHt_tt, rHt_tt;
  private Unit kilometer, km, nMile, kFt, DBz;
  private ScalarMap refMap, htMap, rgbRefMap, rMap;
  private FlatField zProfile_ff;
  private Real[] fzLevels = new Real[2];

  private ConstantMap[] zeroCLineMap, zeroCPtMap, mTwentyCLineMap, mTwentyCPtMap;

  private RealTuple nextDot[];
  private double[] projMatrix;


  public Zprofile() throws VisADException, RemoteException
  {
    tBar = new JToolBar();
    tBar.setBackground(Color.lightGray);
    tBar.setBorder(new EtchedBorder());
    tBar.setFloatable(false);

    oReset = (JButton) addToolbarButton("O Reset", "Reset Orientation", new JButton(), tBar);
    submit = (JButton) addToolbarButton("Submit", "Set Current Data for Algs", new JButton(), tBar);


    try
    {
      kilometer = visad.data.units.Parser.parse("kilometer").clone("km");
      nMile = visad.data.units.Parser.parse("nautical_mile").clone("nm");
      kFt = visad.data.units.Parser.parse("kilofeet").clone("kFt");
//      DBz = visad.data.units.Parser.parse("DBz");
    }
    catch (visad.data.units.ParseException P)
    { System.out.println("Parse exception: " + P); }



//    reflectivity = RealType.getRealType("reflectivity", DBz, null);
    reflectivity = RealType.getRealType("reflectivity");
    ref = RealType.getRealType("ref");
    height = RealType.getRealType("Height", kilometer, null);
    refHt_tt = new RealTupleType(reflectivity, height);
    rHt_tt = new RealTupleType(ref, height);

    display = new DisplayImplJ3D("display", new TwoDDisplayRendererJ3D());

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


    htMap = new ScalarMap(height, Display.YAxis);
    refMap = new ScalarMap(reflectivity, Display.XAxis);
    rgbRefMap = new ScalarMap(reflectivity, Display.RGB);
    rMap = new ScalarMap(ref, Display.XAxis);

    display.addMap(htMap);
    display.addMap(refMap);
    display.addMap(rgbRefMap);
    display.addMap(rMap);

    GraphicsModeControl dispGMC =
                   (GraphicsModeControl)display.getGraphicsModeControl();
    dispGMC.setScaleEnable(true);

    ProjectionControl initPC = display.getProjectionControl();
    projMatrix = initPC.getMatrix();

    htMap.setOverrideUnit(kFt);
    htMap.setRange(0.0f, 80.0f);
    refMap.setRange(-10.0f, 80.0f);
    rgbRefMap.setRange(-10.0f, 80.0f);
    rMap.setRange(-10.0f, 80.0f);
    rMap.setScaleEnable(false);

    AxisScale xAxis = refMap.getAxisScale();
    xAxis.setTitle("Reflectivity (DBz)");
    AxisScale yAxis = htMap.getAxisScale();
    yAxis.setTitle("Height ARL (kFt)");



    /** Set up 0C and -20C height lines */
    setTempLines();

    createProfile(10);

    /** Set up JFrame */
    panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    Component comp = display.getComponent();
    ((JComponent)comp).setPreferredSize( new Dimension(500,500) );
    panel.add(comp);
    panel.add(tBar);
    

    setTitle("Reflectivity Profile");
//    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    getContentPane().add(panel);
    pack();
    setVisible(true);


  }

  public void createProfile(int nPoints) throws VisADException, RemoteException
  {

    double[] refRange = refMap.getRange();
    double[] htRange = htMap.getRange();
    double htDiff = htRange[1] - htRange[0];
    double htStep = htDiff/nPoints;
    double startRef = 50.0;

    final double[] constantHt = new double[nPoints + 1];


    
    pointsRef = new DataReferenceImpl[nPoints + 1];
    pointsRend = new DirectManipulationRendererJ3D[nPoints + 1];
    linesRef = new DataReferenceImpl[nPoints];

    // ### an array of RealTuple used to compare dot movement to its
    // ### previous position.  This is needed to limit motion of the dot
    // ### to only the X-Axis.
    nextDot = new RealTuple[nPoints + 1];

    // ### We have an array of dots that we want to instantiate and 
    // ### listen to their movements
    for (int i = 0; i < nPoints; ++i)
    {
      pointsRef[i] = new DataReferenceImpl("pointsRef" + i);
      pointsRend[i] = new DirectManipulationRendererJ3D();
      linesRef[i] = new DataReferenceImpl("linesRef" + i);

      double[] pointLocvals = new double[] { startRef, (((i)*htStep)/3.3) };
      nextDot[i] = new RealTuple(refHt_tt, pointLocvals);

      constantHt[i] = (i)*htStep/3.3;

      pointsRef[i].setData(nextDot[i]);

      // ### Odd little formula needed since the Display.YAxis goes 
      // ### from -1 to 1.  Display.YAxis range is obviously 2, so we
      // ### take the percentage of the current point / number of points
      // ### and multiply it by 2 and add it to -1
      float pointHtPercentage = (float) (-1 + (2*((float)i/(float)nPoints)));

      display.addReferences(pointsRend[i], pointsRef[i], 
       new ConstantMap[] { 
            new ConstantMap( 3.50f, Display.PointSize) });
      display.addReference(linesRef[i]);
    } 

    // ### this last step is needed to get the top point, since the 
    // ### loop stops at nPoints-1
    pointsRef[nPoints] = new DataReferenceImpl("pointsRef" + nPoints);
    pointsRend[nPoints] = new DirectManipulationRendererJ3D();

    double[] pointLocvals = new double[] { startRef, ((nPoints)*htStep/3.3) };
    nextDot[nPoints] = new RealTuple(refHt_tt, pointLocvals);
    constantHt[nPoints] = (nPoints)*htStep/3.3;

    pointsRef[nPoints].setData(nextDot[nPoints]);

    display.addReferences(pointsRend[nPoints], pointsRef[nPoints],
       new ConstantMap[] { new ConstantMap( 3.50f, Display.PointSize) });


    // ### This isection is where we listen to the movements of the dots
    CellImpl[] pointImpls = new CellImpl[pointsRef.length];
    
    for (int j = 0; j < pointsRef.length; ++j)
    {
      final int jCell = j;
      pointImpls[jCell] = new CellImpl()
      {
        public void doAction()
        {
          // get EndPoint Coords
          RealTuple tuple = (RealTuple) pointsRef[jCell].getData();
          if (tuple == null) return;

//System.out.println(pointsRef[jCell].getName());

          for (int k = 0; k < pointsRef.length; ++k)
          {
            final int kCell = k;
            if ( pointsRef[jCell].equals(pointsRef[kCell]) )
            {
//System.out.println(k + ": " + pointsRef[jCell].getName());

              if (k > 0 && k < pointsRef.length-1)
              {
                double[] lowerVals = 
                  ((RealTuple)pointsRef[jCell-1].getData()).getValues();
                double[] currentVals = 
                  ((RealTuple)pointsRef[jCell].getData()).getValues();
                double[] upperVals = 
                  ((RealTuple)pointsRef[jCell+1].getData()).getValues();
                try
                {
                  linesRef[jCell-1].setData( 
                     (Gridded2DSet)doLine(lowerVals, currentVals) );
                  linesRef[jCell].setData( 
                     (Gridded2DSet)doLine(currentVals, upperVals) );
                }
                catch (Exception exc) { exc.printStackTrace(); }
              }

              if (k == 0 )
              {
                double[] currentVals =
                  ((RealTuple)pointsRef[jCell].getData()).getValues();
                double[] upperVals =
                  ((RealTuple)pointsRef[jCell+1].getData()).getValues();
                try
                {
                  linesRef[jCell].setData(
                     (Gridded2DSet)doLine(currentVals, upperVals) );
                }
                catch (Exception exc) { exc.printStackTrace(); }

              }
              if (k ==  pointsRef.length-1)
              {
                double[] lowerVals =
                  ((RealTuple)pointsRef[jCell-1].getData()).getValues();
                double[] currentVals =
                  ((RealTuple)pointsRef[jCell].getData()).getValues();
                try
                {
                  linesRef[jCell-1].setData(
                     (Gridded2DSet)doLine(lowerVals, currentVals) );
                }
                catch (Exception exc) { exc.printStackTrace(); }

              }
            }
          }

          double[] ptVals = tuple.getValues();
          double[] oldPtVals = nextDot[jCell].getValues();


          // ### We want to limit motion of the point to the X-Axis only
          // ### This section says that only when the point has moved
          // ### are we going to redo the point, and give the Ht term a const
          if (!Util.isApproximatelyEqual(ptVals[0], oldPtVals[0]) ||
              !Util.isApproximatelyEqual(ptVals[1], oldPtVals[1]))
          {
  
            try 
            { 
              doPoint(
                refHt_tt, ptVals[0], constantHt[jCell], pointsRef[jCell]);
              nextDot[jCell] = new RealTuple(
                refHt_tt, new double[] {ptVals[0], constantHt[jCell]});
            }
            catch (Exception exc) { exc.printStackTrace(); }
            return;
          }

        }
      };
      pointImpls[jCell].addReference(pointsRef[jCell]);

    }

  }

  public int setTempLines() throws VisADException, RemoteException
  {

    /** Set up 0C and -20C height lines */
    zeroCLineMap = new ConstantMap[] { 
                           new ConstantMap( 0.0f, Display.Red ),
                           new ConstantMap( 0.75f, Display.Green ),
                           new ConstantMap( 1.0f, Display.Blue )
                           };
                                                                               
    zeroCPtMap = new ConstantMap[] { 
                           new ConstantMap( 0.0f, Display.Red ),
                           new ConstantMap( 0.75f, Display.Green ),
                           new ConstantMap( 1.0f, Display.Blue ),
                           new ConstantMap( 1.0f, Display.XAxis ),
                           new ConstantMap( 3.50f, Display.PointSize )
                           };
                                                                               
    mTwentyCLineMap = new ConstantMap[] { 
                           new ConstantMap( 0.0f, Display.Red ),
                           new ConstantMap( 0.0f, Display.Green ),
                           new ConstantMap( 1.0f, Display.Blue )
                      };
                                                                               
    mTwentyCPtMap = new ConstantMap[] { 
                           new ConstantMap( 0.0f, Display.Red ),
                           new ConstantMap( 0.0f, Display.Green ),
                           new ConstantMap( 1.0f, Display.Blue ),
                           new ConstantMap( 1.0f, Display.XAxis ),
                           new ConstantMap( 3.50f, Display.PointSize )  
                    };
                                                                               
    zeroCRef = new DataReferenceImpl("zeroCRef");
    zeroCRef.setData(new Real(height, 10));
    mTwentyCRef = new DataReferenceImpl("mTwentyCRef");
    mTwentyCRef.setData(new Real(height, 12));
                                                                               
    zeroCRend = new DirectManipulationRendererJ3D();
    mTwentyCRend = new DirectManipulationRendererJ3D();
                                                                               
    zeroLineRef = new DataReferenceImpl("zeroLineRef");
    mTwentyLineRef = new DataReferenceImpl("mTwentyLineRef");
                                                                               
    display.addReferences(zeroCRend, zeroCRef, zeroCPtMap);
    display.addReferences(mTwentyCRend, mTwentyCRef, mTwentyCPtMap);
    display.addReference(zeroLineRef, zeroCLineMap);
    display.addReference(mTwentyLineRef, mTwentyCLineMap);
                                                                               
    CellImpl zeroCCell = new CellImpl()
    {
      public void doAction() throws RemoteException, VisADException
      {
        // ### make sure te -20C line stays above the 0C line
        Real zTuple= (Real) zeroCRef.getData();
        Real tTuple = (Real) mTwentyCRef.getData();
                                                                               
        if (zTuple == null || tTuple == null)
        { return; }
                                                                               
        // ### we know the data are in ref,ht coords
        double zVals = zTuple.getValue();
        double tVals = tTuple.getValue();
/*
        if (tVals > 25.0)
        { tVals = 25.0; }
        else if ( tVals < 0.0)
        { tVals = 0.0; }
        else
        { tVals = tTuple.getValue(); }
                                                                               
        if (zVals > 25.0)
        { zVals = 25.0; }
        else if ( zVals < 0.0)
        { zVals = 0.0; }
        else
        { zVals = tTuple.getValue(); }
                                                                               
        zeroCRef.setData(new Real(height, (zVals)));
*/
        if (zVals > tVals)
        { mTwentyCRef.setData(new Real(height, (zVals+0.5))); }
                                                                               
         Gridded2DSet zLine = createHorizontalHeightLine(zTuple);
         Gridded2DSet tLine = createHorizontalHeightLine(tTuple);
                                                                               
         zeroLineRef.setData(zLine);
         mTwentyLineRef.setData(tLine);
                                                                               
                                                                               
      }
    };
    zeroCCell.addReference(zeroCRef);
    zeroCCell.addReference(mTwentyCRef);

    return 0;

  }

  public Gridded2DSet createHorizontalHeightLine(Real ptHt) 
                            throws VisADException, RemoteException
  {
    Linear1DSet ranSet = new Linear1DSet(height, 80.0, -10.0, 2);
    float[][] ranVals = ranSet.getSamples();
    float[][] htVals = new float[1][ranSet.getLength()];
    java.util.Arrays.fill(htVals[0], (float)ptHt.getValue());
    float[][] ranHtVals = new float[][]{ ranVals[0], htVals[0] };
    Gridded2DSet horizLine = 
           new Gridded2DSet(rHt_tt, ranHtVals, ranSet.getLength());
                                                                          
    return horizLine;
  }


  private void doPoint(RealTupleType rtt, double r, double h,
    DataReferenceImpl pt_ref) throws VisADException, RemoteException
  {
    pt_ref.setData(new RealTuple(rtt, new double[] {r, h}));
  }

  private Set doLine(double[] bottomVals, double[] topVals)
                              throws VisADException, RemoteException
  {
    float[][] vals = new float[][] 
     { {(float)bottomVals[0], (float)topVals[0]}, 
       {(float)bottomVals[1], (float)topVals[1]} };
    return new Gridded2DSet(refHt_tt, vals, 2);
  }

  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == oReset)
    { resetOrientation(); }
    if (e.getSource() == submit)
    {

      try
      {
/*
      JFrame jf = new JFrame("Test");
      JPanel jp = new JPanel();

      DisplayImpl d2 = new DisplayImplJ3D("Test2", new TwoDDisplayRendererJ3D());
      ScalarMap z2Map = new ScalarMap(ref, Display.XAxis);
      ScalarMap r2Map = new ScalarMap(ref, Display.RGB);
      ScalarMap h2Map = new ScalarMap(height, Display.YAxis);
      
      d2.addMap(z2Map);
      d2.addMap(r2Map);
      d2.addMap(h2Map);

      GraphicsModeControl gmc =
              (GraphicsModeControl) d2.getGraphicsModeControl();
      gmc.setScaleEnable(true);

      AxisScale xis = z2Map.getAxisScale();
      xis.setTitle("Reflectivity (DBz)");
      AxisScale yis = h2Map.getAxisScale();
      yis.setTitle("Height (kFt)");

      h2Map.setOverrideUnit(kFt);
      h2Map.setRange(0.0f, 80.0f);
      z2Map.setRange(-10.0f, 80.0f);
      r2Map.setRange(-10.0f, 80.0f);
*/


      float[][] domainVals = new float[1][nextDot.length];
      float[][] rangeVals = new float[1][nextDot.length];
      for (int i = 0; i < nextDot.length; ++i)
      {
        // ### Tuple is ref-ht, so we get reverse values
        domainVals[0][i] = (float) nextDot[i].getValues()[1];
        rangeVals[0][i] = (float) nextDot[i].getValues()[0];
      }

      FunctionType fType = new FunctionType(height, ref);
      Irregular1DSet iSet = new Irregular1DSet(height, domainVals);
      FlatField zFF = new FlatField(fType, iSet);
      zFF.setSamples(rangeVals);
/*
      DataReferenceImpl dRef = new DataReferenceImpl("dRef");
      dRef.setData(zFF);
      d2.addReference(dRef);


      Component comp2 = d2.getComponent();
      jp.add(comp2);
      jf.getContentPane().add(jp);
      jf.pack();
      jf.setVisible(true);
*/

      Real zTuple= (Real) zeroCRef.getData();
      Real tTuple = (Real) mTwentyCRef.getData();


      setProfile(zFF);
      setFreezeLevels(new Real[] {zTuple, tTuple} );
      }
      catch (Exception zEx)
      { System.err.println("Zprofile: " + zEx); }


    }
  }

  /**
   * Adds a button to a toolbar.  From VisAD SpreadSheet
   */
  protected AbstractButton addToolbarButton(String bLabel, String tooltip,
   AbstractButton ab, JComponent parent)
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
      parent.add(b);
      return b;
    }
    else return null;
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

  public void setProfile(FlatField zp)
  { zProfile_ff = (FlatField) zp.clone(); }

  public void setFreezeLevels(Real[] fzLvls)
  {
    for (int i = 0; i < fzLvls.length; ++i)
    {
      fzLevels[i] = new Real(height, fzLvls[i].getValue());
    }
  }

  public FlatField getProfile()
  { return zProfile_ff; }

  public Real[] getFrezeLevels()
  { return fzLevels; }


  public static void main(String[] args) throws VisADException, RemoteException
  {
    Zprofile zp = new Zprofile();
  }


}


