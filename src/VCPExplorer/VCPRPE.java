package VCPExplorer;

import java.lang.*;
import java.awt.*;
import java.awt.Graphics2D.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;
import javax.swing.event.*;
import visad.*;
import visad.java2d.*;
import visad.java3d.*;
import visad.util.DataUtility;
import visad.util.Util;
import java.rmi.RemoteException;



public class VCPRPE extends JFrame implements ActionListener
{

  final static protected Dimension dim = new Dimension(600,700);
  final static protected String[] RADAR_FILES = { "ConfigFiles/88D_siteList.dat", "ConfigFiles/TDWR_siteList.dat" };
  static GridBagLayout gridbag = new GridBagLayout();
  static GridBagConstraints c = new GridBagConstraints();
  private String[] currentRadar;
  private String[][] WSR_RadarFields;
  private String[][] TDWR_RadarFields;
  private String[][] currentRadarFields;
  private double RING_INTERVAL;
  private double MAX_RANGE;
  PPIpanel ppiPanel;
  RHIpanel rhiPanel;
  VCP vcpSelections;
  JPanel radarInfoPanel;
  private Soundings sndgs;
  private Zprofile zp;

  public VCPRPE() throws RemoteException, VisADException,
                                IOException, FileNotFoundException
  {
    setTitle("VCPRPE");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    JPopupMenu.setDefaultLightWeightPopupEnabled(false);
    ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);

    String initLoc = null;
    String[] init = null;

    initLoc = getRadarInfo(1, "ConfigFiles/saveLocation.dat")[0];

    if (!initLoc.equalsIgnoreCase(""))
    {
      init = initLoc.split("\t");
    }
    else
    { 
      init = new String[]
             {"KIWA","PHOENIX","PHOENIX","AZ","33.289","-111.670","427.0"};
    }

    setCurrentRadar( init );

    setRadarRings(4, 400);

    init();

  }

  
  // Get number of lines (1 radar/line) in config file "88D_siteList.dat"
  public int getRadarConfigFileData(String rFile) throws IOException, FileNotFoundException
  {
    int i = 0;
    BufferedReader in  = new BufferedReader(new FileReader(rFile));

    while ( in.ready() )
    { 
      in.readLine();
      i++;
    }
    in.close();
    return i;
  }

  // Read each line from "88D_siteList.dat" into a String array
  public String[] getRadarInfo(int numLines, String rFile) throws IOException, FileNotFoundException
  {
    int i = 0;
    String[] radarLines = new String[numLines];

    File newFile = new File(rFile);

    if (!newFile.exists())
    {
      return new String[]{""};
    }

    BufferedReader in  = new BufferedReader(new FileReader(rFile));

    while ( in.ready() )
    { 
      radarLines[i] = in.readLine();
      i++;
    }
    in.close();

    return radarLines;
  }

  // Parse each line from "88D_siteList.dat" and store in a 2D String array
  public void setRadarData(String[] radarLine, int rFile)
  {
    int lenRadarLines = radarLine.length;
    int lenRadarFields = radarLine[lenRadarLines - 1].split("\t").length;

    String[][] radarFields = new String[lenRadarLines][lenRadarFields];

    for (int i = 0; i < lenRadarLines; i++)
    {
      radarFields[i] = radarLine[i].split("\t");
    }

    if (rFile == 0)
    { WSR_RadarFields = radarFields; }
    if (rFile == 1)
    { TDWR_RadarFields = radarFields; }

  }

  public String[][] getRadarData(int rFiles)
  {

    if (rFiles == 0)
    { return WSR_RadarFields; }
    else
    { return TDWR_RadarFields; }

  }

  public JMenuBar makeJMenuBar() throws IOException, FileNotFoundException
  {
    JMenuBar menuBar;
    JMenu menu, radarMenu; 
    RadarMenu tdwrMenu, wsrMenu;
    JMenuItem exitMenuItem;
    JRadioButtonMenuItem radarRadioButtonMI;

    // Create the MenuBar
    menuBar = new JMenuBar();

    // Create a Menu
    menu = new JMenu("File");
    menu.getAccessibleContext().setAccessibleDescription( "Controls Radar Selection and Exiting" );
    menuBar.add(menu);

    JMenu compMenu = new JMenu("Compare");
    compMenu.getAccessibleContext().setAccessibleDescription( 
              "Set sounding or DBz profiles" );
    menuBar.add(compMenu);
      JMenuItem sndMenuItem = new JMenuItem("Sounding");
      JMenuItem refMenuItem = new JMenuItem("DBz");
      sndMenuItem.addActionListener(this);
      refMenuItem.addActionListener(this);
      compMenu.add(sndMenuItem);
      compMenu.add(refMenuItem);

    JMenu hMenu = new JMenu("Help");
    hMenu.getAccessibleContext().setAccessibleDescription( "Help" );
    menuBar.add(hMenu);
      JMenuItem hMenuItem = new JMenuItem("Online Help");
      JMenuItem aMenuItem = new JMenuItem("About");
      hMenuItem.addActionListener(this);
      aMenuItem.addActionListener(this);
      hMenu.add(hMenuItem);
      hMenu.add(aMenuItem);


    exitMenuItem = new JMenuItem("Exit");
    exitMenuItem.getAccessibleContext().setAccessibleDescription( "Exits Program" );
    exitMenuItem.addActionListener(this);

    // ### Saves the current site as the startup site the next time the program starts (04.21.04)
    JMenuItem saveMenuItem = new JMenuItem("Save Location");
    saveMenuItem.getAccessibleContext().setAccessibleDescription( 
                     "Save Current Location as Startup Location" );
    saveMenuItem.addActionListener(this);

    menu.add(exitMenuItem);
    menu.add(saveMenuItem);

    radarMenu = new JMenu("Choose Radar");

    // First dimension is for radar group (WSR-88D or TDWR)
    String[][][] radarData = new String[2][][];
    String[][] radars = new String[2][];
    for (int i = 0; i < RADAR_FILES.length; i++)
    {
      setRadarData ( getRadarInfo( getRadarConfigFileData(RADAR_FILES[i]), RADAR_FILES[i] ), i );
      radarData[i] = getRadarData(i);
      radars[i] = new String[radarData[i].length-1];
  
      // Note the "i+1" in radarData.  The first line is a headder which we don't want
      for (int j = 0; j < radarData[i].length-1; j++)
      { radars[i][j] = radarData[i][j+1][0]; }
  
      if (i == 0)
      {
        ButtonGroup wsrGroup = new ButtonGroup();
        wsrMenu = createRadarSubMenu(radars[i], "WSR-88D", wsrGroup);
        radarMenu.add(wsrMenu);
      }
      if (i == 1)
      {
        ButtonGroup tdwrGroup = new ButtonGroup();
        tdwrMenu = createRadarSubMenu(radars[i], "TDWR", tdwrGroup);
        radarMenu.add(tdwrMenu);
      }


    }
    menu.add(radarMenu);



    return menuBar;
  }

  public RadarMenu createRadarSubMenu(String[] radarSites, String radarType, ButtonGroup radarBG)
  {
    RadarMenu rMenu = new RadarMenu(radarType);
    JRadioButtonMenuItem radarRadioButtonMI;
    int p = 0;
    int q = 0;

    for (int i = 0; i < radarSites.length; i++)
    {

      radarRadioButtonMI  = new JRadioButtonMenuItem(radarSites[i]);
      radarRadioButtonMI.addActionListener(this);


      if ( radarSites[i].equalsIgnoreCase(getCurrentRadar()[0] ) )
      {
        radarRadioButtonMI.setSelected(true);
      }
      radarBG.add(radarRadioButtonMI);

      p = i%15;

      if (p == 0 & i > 0)
      { q++; }

      rMenu.setConst(radarRadioButtonMI, q, p);
    }

    return rMenu;
  }

  class RadarMenu extends JMenu
  {
    JPopupMenu pm = getPopupMenu();
    GridBagLayout gridbag = new GridBagLayout();
    GridBagConstraints c = new GridBagConstraints();

    RadarMenu(String label)
    {
      super(label);
      pm.setLabel(label);
      pm.setLayout(gridbag);
    }

    public void setConst(JRadioButtonMenuItem comp, int x, int y)
    { 
      c.gridx = 0;
      c.gridy = 0;
      c.gridx = x;
      c.gridy = y;
      gridbag.setConstraints( comp, c );
      pm.add(comp);
    }

  }

  public void actionPerformed(ActionEvent e)
  {
    if ( e.getActionCommand().equalsIgnoreCase("exit") )
    { 
      System.runFinalization();
      System.exit(0); 
    }

    // ### Save the current radar location in a file to use a startup location
    if ( e.getActionCommand().equalsIgnoreCase("Save Location") )
    {
      String dummyString = new String();
      for (int k = 0; k < getCurrentRadar().length; ++k)
      {
        dummyString = dummyString.concat( getCurrentRadar()[k] );
        if (k < (getCurrentRadar().length - 1) )
        { dummyString = dummyString.concat( new String("\t") ); }
      }

      try
      {
        File saveLocFile = new File("ConfigFiles/saveLocation.dat");
        PrintWriter out = new PrintWriter(new FileWriter(saveLocFile));
        out.println(dummyString);
        out.close();
      }
      catch (IOException ioe) 
      { System.err.println("Error writing save file: " + ioe); }
    }

    String[][] radarData;

    if ( e.getActionCommand().equalsIgnoreCase("Online Help") )
    {
      JFrame helpFrame = new JFrame("Help");
      String labelMsg = new String("VCP Explorer Help");
      java.net.URL helpURL = null;

        JPanel helpPanel = new JPanel();
        helpPanel.setLayout(new BoxLayout(helpPanel, BoxLayout.Y_AXIS));
       

          JEditorPane editorPane = new JEditorPane();
          editorPane.setEditable(false);
           helpURL = VCPRPE.class.getResource("../ConfigFiles/VCPExplorer_Help.html"); 
          if (helpURL != null) {
              try {
                  editorPane.setPage(helpURL);
              } catch (IOException ioe) {
                  System.err.println("Attempted to read a bad URL: " + helpURL);
              }
          } else {
              System.err.println("Couldn't find file: VCPExplorer_Help.html");
          }

          //Put the editor pane in a scroll pane.
          JScrollPane editorScrollPane = new JScrollPane(editorPane);
          editorScrollPane.setVerticalScrollBarPolicy(
                          JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
          editorScrollPane.setPreferredSize(new Dimension(250, 145));
          editorScrollPane.setMinimumSize(new Dimension(10, 10));



        helpPanel.add(editorScrollPane);

      helpFrame.getContentPane().add(helpPanel);
      helpFrame.setSize(750,400);

      Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
      helpFrame.setLocation((screen.width - helpFrame.getSize().width)/2,
                  (screen.height - helpFrame.getSize().height)/2);

      helpFrame.setVisible(true);

    }

    if ( e.getActionCommand().equalsIgnoreCase("About") )
    {
      JFrame aboutFrame = new JFrame("About");
      String labelMsg = new String("VCP Explorer");
      Icon vcpIcon = new ImageIcon("Img/VCPRPE_HU_Fill_small.gif");

        JPanel aboutPanel = new JPanel();
        GridBagLayout gl = new GridBagLayout();
        GridBagConstraints gbc = new GridBagConstraints();
        aboutPanel.setLayout(gl);

          JLabel aboutLabel = new JLabel(labelMsg);
          aboutLabel.setFont(new Font("Serif", Font.PLAIN, 24));
          JLabel aboutIcon = new JLabel(vcpIcon);
          JLabel aboutField0 = new JLabel("VCP Explorer for");
          JLabel aboutField1 = new JLabel("Weather Decision Training Branch");
          JLabel aboutField2 = new JLabel("Written by Kevin L. Manross");
          JLabel aboutField3 = new JLabel("(NSSL/CIMMS) <kevin.manross@noaa.gov>");
          JLabel aboutField4 = new JLabel("Version 1.1 (03.01.04)");

        gbc.gridx = 0;
        gbc.gridy = 0;
        gl.setConstraints(aboutLabel, gbc);
        aboutPanel.add(aboutLabel);
        gbc.gridy = 1;
        gl.setConstraints(aboutIcon, gbc);
        aboutPanel.add(aboutIcon);
        gbc.gridy = 2;
        gl.setConstraints(aboutField0, gbc);
        aboutPanel.add(aboutField0);
        gbc.gridy = 3;
        gl.setConstraints(aboutField1, gbc);
        aboutPanel.add(aboutField1);
        gbc.gridy = 4;
        gl.setConstraints(aboutField2, gbc);
        aboutPanel.add(aboutField2);
        gbc.gridy = 5;
        gl.setConstraints(aboutField3, gbc);
        aboutPanel.add(aboutField3);
        gbc.gridy = 6;
        gl.setConstraints(aboutField4, gbc);
        aboutPanel.add(aboutField4);

      aboutFrame.getContentPane().add(aboutPanel);
      aboutFrame.setSize(300, 350);
      aboutFrame.setResizable(false);

      Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
      aboutFrame.setLocation((screen.width - aboutFrame.getSize().width)/2,
                  (screen.height - aboutFrame.getSize().height)/2);

      aboutFrame.setVisible(true);

    }

    if (e.getActionCommand().equalsIgnoreCase("Sounding"))
    {
      try
      {

          sndgs = new Soundings(rhiPanel.getSoundingInfo());
//        final Soundings sndgs = new Soundings(rhiPanel.getSoundingInfo());

        // ### Using MouseListener in order to ensure sounding data
        // ### get processed before we send it to rhiPanel
        sndgs.submit.addMouseListener(new MouseListener()
        { 
          public void mouseClicked(MouseEvent ae)
          {}
          public void mouseEntered(MouseEvent ae)
          {}
          public void mouseExited(MouseEvent ae)
          {}
          public void mousePressed(MouseEvent ae)
          {}
          public void mouseReleased(MouseEvent ae)
          { 
            rhiPanel.setSoundingData( sndgs.getSoundingData() ); 
            rhiPanel.setSoundingInfo( sndgs.getSoundingInfo() ); 
          }
        });

      }
      catch (Exception ex)
      { System.err.println("Soundings: " + ex); }
    }

    if (e.getActionCommand().equalsIgnoreCase("DBz") )
    {
      try
      { 
          zp = new Zprofile(); 
//        final Zprofile zp = new Zprofile(); 

        zp.submit.addMouseListener(new MouseListener()
        {
          public void mouseClicked(MouseEvent ae)
          {}
          public void mouseEntered(MouseEvent ae)
          {}
          public void mouseExited(MouseEvent ae)
          {}
          public void mousePressed(MouseEvent ae)
          {}
          public void mouseReleased(MouseEvent ae)
          {
            rhiPanel.setZProfileFreezeLevels( zp.getFrezeLevels() );
            rhiPanel.setZProfile( zp.getProfile() );
          }
        });

      }
      catch (Exception ex)
      { System.err.println("ZProfile: " + ex); }

    }



    // Use the label of the parent (RadarType) menu to
    // determine which radar fields to grab (TDWR or WSR-88D)
    JMenuItem item = (JMenuItem) e.getSource();
    JPopupMenu men = (JPopupMenu)item.getParent();
    String radarType = men.getLabel();

    if (radarType != null)
    {
      if ( radarType.equalsIgnoreCase("WSR-88D") )
      { 
        radarData = getRadarData(0); 
        setRadarRings(4, 400);
      }
      else
      { 
        radarData = getRadarData(1); 
        setRadarRings(3, 150);
      }
  
      String[] desiredRadar = new String[radarData[1].length];
  
      for (int i = 0; i < radarData.length; i++)
      {
        if ( radarData[i][0].equalsIgnoreCase( e.getActionCommand() ) )
        { 
          desiredRadar = radarData[i];
          break;
        }
      }
  
      setCurrentRadar( desiredRadar );
  
      try    
      { 
        ppiPanel.cleanUp(); 
        rhiPanel.cleanUp();
        System.runFinalization();
        System.gc();
      }
      catch (RemoteException r)
      { System.out.println("RemoteException: " + r ); }
      catch (VisADException v)
      { System.out.println("VisADException: " + v ); }
      catch (IOException i)
      { System.out.println("IOException: " + i ); }
      catch (Throwable t)
      { System.out.println("Throwable: " + t ); }
  
      try 
      { init(); }
      catch (RemoteException r)
      { System.out.println("RemoteException: " + r ); }
      catch (VisADException v)
      { System.out.println("VisADException: " + v ); }
      catch (IOException i)
      { System.out.println("IOException: " + i ); }
 
    } 
  }

  public void setRadarRings(double interval, double maxRange)
  {
    RING_INTERVAL = interval;
    MAX_RANGE = maxRange;
  }

  public double[] getRadarRings()
  { 
    double[] vals = {RING_INTERVAL, MAX_RANGE};
    return vals; 
  }

  public void setCurrentRadar(String[] cr)
  { currentRadar = cr; }

  public String[] getCurrentRadar()
  { return currentRadar; }

  public void makePPIPanel( String[] radar ) throws RemoteException, VisADException
//  { ppiPanel = new PPIpanel(radar); }
  { ppiPanel = new PPIpanel(this, radar, getRadarRings()[1]); }

  public PPIpanel getPPIPanel()
  { return ppiPanel; }

  public void makeRHIPanel() throws RemoteException, VisADException
//  { rhiPanel = new RHIpanel(getVCP(), getPPIPanel(), (int)getRadarRings()[0], getRadarRings()[1] ); }
  { rhiPanel = new RHIpanel(this, getVCP(), getPPIPanel(), (int)getRadarRings()[0], getRadarRings()[1], getCurrentRadar()[0]); }

  public RHIpanel getRHIPanel()
  { return rhiPanel; }

  public void makeVCP()
  { vcpSelections =  new VCP(); }

  public VCP getVCP()
  { return vcpSelections; }

  public void makeRadarInfoPanel(String[] radar)
  { radarInfoPanel = new InfoPanel(radar); }

  public JPanel getRadarInfoPanel()
  { return radarInfoPanel; }

  public void init() throws RemoteException, VisADException,
                                IOException, FileNotFoundException
  {


    setVisible(false);

    setSize(1024, 768);

    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    setLocation((screen.width - getSize().width)/2,
                (screen.height - getSize().height)/2);

    setResizable(true);
    setVisible(true);
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));


    makeVCP();
    setCurrentRadar( getCurrentRadar() );
    makePPIPanel( getCurrentRadar() );
    makeRHIPanel();
    makeRadarInfoPanel( getCurrentRadar() );

    setJMenuBar( makeJMenuBar() );
    getContentPane().removeAll();
    getContentPane().setLayout(gridbag);

    c.gridx = 0;
    c.gridy = 0;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.gridheight = 1;
    c.gridwidth = 3;
    c.weightx = 1.0;
    c.weighty = 1.0;
    c.fill = GridBagConstraints.NONE;
    gridbag.setConstraints( getRadarInfoPanel(), c );
    getContentPane().add( getRadarInfoPanel() );

    c.gridheight = 1;
    c.gridwidth = 3;
    c.gridx = 1;
    c.gridy = 3;
    c.weightx = 1.0;
    c.weighty = 1.0;
//    c.fill = GridBagConstraints.BOTH;
    //c.anchor = GridBagConstraints.LINE_END;
    c.anchor = GridBagConstraints.SOUTHEAST;
    gridbag.setConstraints(getVCP(), c);
    getContentPane().add(getVCP());

    c.gridheight = 1;
    c.gridwidth = 1;
    c.gridx = 0;
    c.gridy = 2;
    c.weightx = 5.0;
    c.weighty = 20.0;
    c.fill = GridBagConstraints.BOTH;
    gridbag.setConstraints(getPPIPanel(), c);
    getContentPane().add(getPPIPanel());

    c.gridheight = 1;
    c.gridwidth = 1;
    c.gridx = 1;
    c.gridy = 2;
    c.weightx = 2.0;
    c.weighty = 20.0;
    c.fill = GridBagConstraints.BOTH;
    gridbag.setConstraints(getRHIPanel(), c);
    getContentPane().add(getRHIPanel());

/*
    setSize(1024, 768);

    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    setLocation((screen.width - getSize().width)/2,
                (screen.height - getSize().height)/2);

    setResizable(true);
*/
    setVisible(true);
    setCursor(null);

  }

  public static void main(String[] args) throws RemoteException, VisADException,
                                IOException, FileNotFoundException
  {
    VCPRPE rpe = new VCPRPE(); 
  }

}


