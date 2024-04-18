package VCPExplorer;

import java.awt.*;
import java.awt.event.*;
import java.awt.Graphics2D.*;
import java.util.Hashtable;
import java.util.Iterator;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.BorderFactory.*;
import javax.swing.ListModel.*;
import javax.swing.event.*;
import visad.*;
import visad.java2d.DisplayImplJ2D;
import java.rmi.RemoteException;


public class VCP extends JPanel
// implements ActionListener
{

  JButton up, down;
  JLabel vcpLabel, elevLabel, bwLabel, vcpTitle, vcpElevationsTitle, 
                   chooseElevationTitle, bwTitle, pctBlockageTitle, azTitle; 
  JComboBox vcpComboBox;
  protected Hashtable vcpHTable;
  protected JList vcpList;
  protected JFormattedTextField elevEntered, beamWidth, pctBlockage, azEntered;
  protected DefaultListModel dlm, vcpListModel;
  JScrollPane vcpScrollPane;
  double DEFAULT_BEAM_WIDTH = 1.0;
  int DEFAULT_BLOCKAGE = 50;

  GridBagLayout gridbag = new GridBagLayout();
  GridBagConstraints c = new GridBagConstraints();
  TitledBorder titled;
  Border loweredetched = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
  Border raisedbevel = BorderFactory.createRaisedBevelBorder();
  Border loweredbevel = BorderFactory.createLoweredBevelBorder();
  Border compound = BorderFactory.createCompoundBorder(raisedbevel, loweredbevel);


  public VCP()
  {
    titled = BorderFactory.createTitledBorder(compound, "VCP Controls");
    setLayout(gridbag);

    try { vcpHTable = readVCPList(); }
    catch (IOException i)
    { System.out.println("IOException: " + i ); }

    // Get list of VCP headders (keys) from "vcpList.dat"
    Object[] vcp = vcpHTable.keySet().toArray();

    Icon upButton = new ImageIcon("Img/up.gif");
    Icon downButton = new ImageIcon("Img/down.gif");
    up = new JButton(upButton);
    down = new JButton(downButton);

    // Create and initialize containers
    elevEntered = new JFormattedTextField(
          new java.text.DecimalFormat("00.00") );
    elevEntered.setColumns(4);
    beamWidth = new JFormattedTextField( 
          new java.text.DecimalFormat("00.00") );
    beamWidth.setText(String.valueOf(DEFAULT_BEAM_WIDTH));
    pctBlockage = new JFormattedTextField( 
          new java.text.DecimalFormat("000") );
    pctBlockage.setColumns(3);

    azEntered = new JFormattedTextField( new java.text.DecimalFormat("000.00") );
    azEntered.setColumns(5);

    pctBlockage.setText(String.valueOf(DEFAULT_BLOCKAGE) );
    vcpComboBox = new JComboBox(vcp);
    vcpComboBox.setSelectedIndex(0);

    vcpLabel = new JLabel((String)vcpComboBox.getSelectedItem());
    vcpLabel.setForeground(Color.red);
    elevLabel = new JLabel();
    elevLabel.setForeground(Color.yellow);
    vcpTitle = new JLabel("Current VCP: ");
    chooseElevationTitle = new JLabel("Elev.", SwingConstants.CENTER);
    bwTitle = new JLabel("Enter Beam Width", SwingConstants.CENTER);
    pctBlockageTitle = new JLabel("Beam Blockage (%)", SwingConstants.CENTER);
    azTitle = new JLabel("Azimuth", SwingConstants.CENTER);

    dlm = new DefaultListModel();
    dlm = stringize( (String) vcpHTable.get(vcp[0]));
    vcpList = new JList(dlm);
    vcpList.setVisibleRowCount(5);
    vcpList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    vcpList.clearSelection();

    vcpList.addListSelectionListener(new ListSelectionListener()
                             { public void valueChanged(ListSelectionEvent e)
                               {
                                 if (vcpList.getSelectedValue() != null  && !e.getValueIsAdjusting())
                                 { 
                                   String t = (String)vcpList.getSelectedValue();
                                   elevLabel.setText(t); 
                                   elevEntered.setText(t);
                                   // make user-entered elev field have focus.  Need here and in VCP
                                   elevEntered.grabFocus();
                                 }
                               }
                             }
     );

    int setPosit = (vcpList.getModel().getSize() - 1);
    vcpList.setSelectedIndex(setPosit);
    vcpList.ensureIndexIsVisible(setPosit);

    vcpScrollPane = new JScrollPane(vcpList);
    vcpScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

    vcpList.setSelectedIndex(setPosit);
    vcpList.ensureIndexIsVisible(setPosit);

    // Add action listener for Combobox
    vcpComboBox.addActionListener(new ActionListener()
               { public void actionPerformed(ActionEvent e)
                 {

                   DefaultListModel dlm = new DefaultListModel();
                   JComboBox vcpC = (JComboBox)e.getSource();
                   String VCPchoice = (String)vcpC.getSelectedItem();
                   vcpLabel.setText(VCPchoice);

                   dlm.removeAllElements();
                   dlm = stringize( (String) vcpHTable.get(VCPchoice) );
                   dlm.trimToSize();

                   vcpList.setModel(dlm);
                   int k = vcpList.getModel().getSize() - 1;
                   vcpList.setSelectedIndex(k);
                   vcpList.ensureIndexIsVisible(k);
                   vcpList.ensureIndexIsVisible(k); // this 2nd call is necessary
//                   beamWidth.setText(String.valueOf(DEFAULT_BEAM_WIDTH) );
                 }
               }
    );


    up.addActionListener(new ActionListener()
                        { public void actionPerformed(ActionEvent e)
                          {
                            if (vcpList.getSelectedIndex() != 0)
                            {
                              int i = vcpList.getSelectedIndex() - 1;
                              vcpList.ensureIndexIsVisible(i);
                              vcpList.setSelectedIndex(i);
                            }
                          }
                        }
    );

    down.addActionListener(new ActionListener()
                          { public void actionPerformed(ActionEvent e)
                            { 
                              if (vcpList.getSelectedIndex() < vcpListModel.size() - 1)
                              {
                                int i = vcpList.getSelectedIndex() + 1;
                                vcpList.ensureIndexIsVisible(i);
                                vcpList.setSelectedIndex(i);
                              }
                            }
                          }
    );
/*
    c.gridwidth = 1;
    c.gridheight = 1;
    c.ipadx = 0;
    c.gridx = 4;
    c.gridy = 0;
    gridbag.setConstraints(bwTitle,c);
    add(bwTitle);
    c.ipadx = 0;
    c.gridx = 4;
    c.gridy = 1;
    gridbag.setConstraints(beamWidth,c);
    add(beamWidth);
*/
    c.gridx = 0;
    c.gridy = 0;
    c.anchor = GridBagConstraints.LINE_START;

    gridbag.setConstraints(vcpComboBox,c); 
    add(vcpComboBox);

    c.gridx = 0;
    c.gridy = 1;
    c.weighty = 1.0;
    c.gridheight = GridBagConstraints.RELATIVE;
    gridbag.setConstraints(vcpLabel,c);
    add(vcpLabel);

    c.gridx = 0;
    c.gridy = 2;
    c.gridheight = 1;
    gridbag.setConstraints(elevLabel,c);
    add(elevLabel);

    c.gridx = GridBagConstraints.RELATIVE;
    c.gridy = 0;
    c.gridheight = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(vcpScrollPane,c);
    add(vcpScrollPane);

    c.gridx = GridBagConstraints.RELATIVE;
    c.gridy = 0;
    c.gridheight = 1;
    c.fill = GridBagConstraints.VERTICAL;
    gridbag.setConstraints(up, c);
    add(up);

//    c.gridx = 2;
    c.gridy = 1;
    gridbag.setConstraints(down, c);
    add(down);

    c.insets = new Insets(0, 20, 0, 0);
    c.gridx = GridBagConstraints.RELATIVE;
    c.gridy = 0;
    c.gridheight = 1;
//    c.gridwidth = GridBagConstraints.RELATIVE;
    gridbag.setConstraints(chooseElevationTitle,c);
    add(chooseElevationTitle);

//    c.gridx = 3;
    c.gridy = 1;
    gridbag.setConstraints(elevEntered,c);
    add(elevEntered);

    c.gridx = GridBagConstraints.RELATIVE;
    c.gridy = 0;
    //c.gridwidth = GridBagConstraints.RELATIVE;
    gridbag.setConstraints(azTitle, c);
    add(azTitle);

//    c.gridx = 4;
    c.gridy = 1;
    gridbag.setConstraints(azEntered, c);
    add(azEntered);

    c.gridx = GridBagConstraints.RELATIVE;
    c.gridy = 0;
    c.gridwidth = GridBagConstraints.REMAINDER;
    gridbag.setConstraints(pctBlockageTitle,c);
    add(pctBlockageTitle);

//    c.gridx = 5;
    c.gridy = 1;
    c.anchor = GridBagConstraints.CENTER;
    gridbag.setConstraints(pctBlockage,c);
    add(pctBlockage);
    
    setBorder(titled);
  }

  public Hashtable readVCPList() throws IOException, FileNotFoundException
  {
    Hashtable vcpHash = new Hashtable();
    BufferedReader in  = new BufferedReader(new FileReader("ConfigFiles/vcpList.dat"));

    while ( in.ready() )
    {
      String[] currentVCPLine = in.readLine().split("\t");
      vcpHash.put(currentVCPLine[0], currentVCPLine[1]);
    }
    return vcpHash;
  }

  public DefaultListModel stringize(String elevs)
  {
     vcpListModel = new DefaultListModel();
    vcpListModel.removeAllElements();
    String[] indivElAngs = elevs.split(",");
    for (int i = 0; i < indivElAngs.length; i++)
    {
      vcpListModel.addElement(indivElAngs[i]);
    }
    return vcpListModel;
  }

  public static void main(String[] args)
  {
    VCP v = new VCP();

    JFrame frame = new JFrame("VCP Controls");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.getContentPane().add(v);
    frame.pack();
    frame.setVisible(true);
  }


}




