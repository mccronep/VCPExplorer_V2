package VCPExplorer;

import java.*;
import java.io.*;
import java.util.*;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;


public class Soundings extends JFrame implements ActionListener
{

  private JPanel panel;
  private JButton button1;
  protected JButton submit;
  static GridBagLayout gridbag = new GridBagLayout();
  static GridBagConstraints c = new GridBagConstraints();
  final static protected Dimension dim = new Dimension(515,550);

  private JLabel title = new JLabel("Enter Sounding Data");
  private String[] soundingTypes = new String[] {
                                   new String("BUFR"), 
                                   new String("RAOB")
                                                };

  private JComboBox soundingBox = new JComboBox(soundingTypes);
  private JComboBox choiceBox = new JComboBox();
//  , stidBox, dateBox, timeBox;
  private JTextField locField = new JTextField(20);
  private JFileChooser fileChooser = new JFileChooser();

  private ParseSounding parseSnd;
//  private ParseRAOB raobSnd;

  private Vector data;
  private String sndHeadder, currSdg;
  private String[] currInfo;
  private JLabel[] currInfoLabels;

  

  public Soundings(String str) throws IOException
  {
    setCurrentSounding(str);
    setup();
  }

  public void setup() throws IOException
//  public Soundings() throws IOException
  {

    button1 = new JButton("Choose");
    button1.addActionListener(this);
    submit = new JButton("Submit");
    submit.addActionListener(this);


    panel = new JPanel();
    panel.setLayout(gridbag);

    /** #######Start Layout####### */

    c.gridy = 0;
    c.gridx = 0;
    c.weighty = 1.0;
    c.weightx = 1.0;
    c.anchor = GridBagConstraints.NORTH;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.fill = GridBagConstraints.HORIZONTAL;

    JPanel titlePanel = new JPanel();
    JLabel titleLabel = new JLabel("Enter Location of Sounding Data");
    titlePanel.add(titleLabel);
    titlePanel.setOpaque(true);
    gridbag.setConstraints(titlePanel, c);
    panel.add(titlePanel);

    addSeparator();
    c.gridy = GridBagConstraints.RELATIVE;
    c.anchor = GridBagConstraints.NORTHWEST;

    JPanel chooserPanel = new JPanel();
     GridBagLayout chGridbag = new GridBagLayout();
     GridBagConstraints chC = new GridBagConstraints();
     chooserPanel.setLayout(chGridbag);
      chC.gridy = 0;
      chC.gridx = 0;
      chC.weighty = 1.0;
      chC.weightx = 1.0;
      chC.anchor = GridBagConstraints.WEST;
      JLabel sndTypeLabel = new JLabel("Choose Sounding Type:");
      chGridbag.setConstraints(sndTypeLabel, chC);
      chooserPanel.add(sndTypeLabel);

      chC.gridx = GridBagConstraints.RELATIVE;
      Component strut0 = Box.createHorizontalStrut(50);
      chGridbag.setConstraints(strut0, chC);
      chooserPanel.add(strut0);

      JLabel locationLabel = new JLabel("Choose File Location:");
      chC.gridx = GridBagConstraints.RELATIVE;
      chGridbag.setConstraints(locationLabel, chC);
      chooserPanel.add(locationLabel);

      chC.gridy = 1;
      chC.gridx = 0;
      chGridbag.setConstraints(soundingBox, chC);
      chooserPanel.add(soundingBox);

      chC.gridx = GridBagConstraints.RELATIVE;
      Component strut1 = Box.createHorizontalStrut(50);
      chGridbag.setConstraints(strut1, chC);
      chooserPanel.add(strut1);

      chC.gridx = GridBagConstraints.RELATIVE;
      chGridbag.setConstraints(locField, chC);
      chooserPanel.add(locField);

      chGridbag.setConstraints(button1, chC);
      chooserPanel.add(button1);
    gridbag.setConstraints(chooserPanel, c);
    panel.add(chooserPanel);

    addSeparator();

    JPanel choicePanel = new JPanel();
     GridBagLayout aGridBag = new GridBagLayout();
     GridBagConstraints aC = new GridBagConstraints();
     choicePanel.setLayout(aGridBag);
      aC.gridy = 0;
      aC.gridx = 0;
      aC.weighty = 1.0;
      aC.weightx = 1.0;
      aC.anchor = GridBagConstraints.WEST;
      JLabel choiceLabel = new JLabel("STID,DATE,TIME");
      aGridBag.setConstraints(choiceLabel, aC);
      choicePanel.add(choiceLabel);

      aC.gridy = 1;
      aC.gridx = 0;
      aGridBag.setConstraints(choiceBox, aC);
      choicePanel.add(choiceBox);

      aC.gridx = GridBagConstraints.RELATIVE;
      Component strut2 = Box.createHorizontalStrut(50);
      aGridBag.setConstraints(strut2, aC);
      choicePanel.add(strut2);

      aGridBag.setConstraints(submit, aC);
      choicePanel.add(submit);
    gridbag.setConstraints(choicePanel, c);
    panel.add(choicePanel);

    addSeparator();
   
    JPanel currPanel = new JPanel();
     GridBagLayout bGridBag = new GridBagLayout();
     GridBagConstraints bC = new GridBagConstraints();
     currPanel.setLayout(bGridBag);
      bC.gridy = 0;
      bC.gridx = 0;
      bC.weighty = 1.0;
      bC.weightx = 1.0;
      bC.gridwidth = GridBagConstraints.REMAINDER;
      bC.anchor = GridBagConstraints.CENTER;
      JLabel curTitle = new JLabel("Current Sounding Is:");
      curTitle.setFont(new Font("Serif", Font.ITALIC+Font.BOLD, 12));
      curTitle.setOpaque(true);
      curTitle.setBackground(Color.blue);
      curTitle.setForeground(Color.white);
      bGridBag.setConstraints(curTitle, bC);
      currPanel.add(curTitle);

      bC.gridy = 1;
      bC.gridx = 0;

      bC.gridx = GridBagConstraints.RELATIVE;
      Component strutV = Box.createVerticalStrut(25);
      bGridBag.setConstraints(strutV, bC);
      currPanel.add(strutV);

      bC.gridy = 2;
      bC.gridx = 0;
      bC.gridwidth = 1;
      bC.anchor = GridBagConstraints.NORTHWEST;
      bC.fill = GridBagConstraints.HORIZONTAL;
      JLabel curTypeLabel = new JLabel("Type: ");
      bGridBag.setConstraints(curTypeLabel, bC);
      currPanel.add(curTypeLabel);
/*
      bC.gridx = GridBagConstraints.RELATIVE;
      JLabel curType = new JLabel(currInfo[0]);
      curType.setForeground(Color.red);
      bGridBag.setConstraints(curType, bC);
      currPanel.add(curType);
*/

      bC.gridx = GridBagConstraints.RELATIVE;
      currInfoLabels[0].setForeground(Color.red);
      bGridBag.setConstraints(currInfoLabels[0], bC);
      currPanel.add(currInfoLabels[0]);

      bC.gridx = GridBagConstraints.RELATIVE;
      Component strut3 = Box.createHorizontalStrut(50);
      bGridBag.setConstraints(strut3, bC);
      currPanel.add(strut3);

      JLabel curStidLabel = new JLabel("STID: ");
      bGridBag.setConstraints(curStidLabel, bC);
      currPanel.add(curStidLabel);
/*
      JLabel curStid = new JLabel(currInfo[1]);
      curStid.setForeground(Color.red);
      bGridBag.setConstraints(curStid, bC);
      currPanel.add(curStid);
*/
      currInfoLabels[1].setForeground(Color.red);
      bGridBag.setConstraints(currInfoLabels[1], bC);
      currPanel.add(currInfoLabels[1]);

      bC.gridx = GridBagConstraints.RELATIVE;
      Component strut4 = Box.createHorizontalStrut(50);
      bGridBag.setConstraints(strut4, bC);
      currPanel.add(strut4);

      JLabel curDateLabel = new JLabel("Date: ");
      bGridBag.setConstraints(curDateLabel, bC);
      currPanel.add(curDateLabel);
/*                                                                           
      JLabel curDate = new JLabel(currInfo[2]);
      curDate.setForeground(Color.red);
      bGridBag.setConstraints(curDate, bC);
      currPanel.add(curDate);
*/
      currInfoLabels[2].setForeground(Color.red);
      bGridBag.setConstraints(currInfoLabels[2], bC);
      currPanel.add(currInfoLabels[2]);

      bC.gridx = GridBagConstraints.RELATIVE;
      Component strut5 = Box.createHorizontalStrut(50);
      bGridBag.setConstraints(strut5, bC);
      currPanel.add(strut5);


      bC.gridwidth = GridBagConstraints.RELATIVE;
      JLabel curTimeLabel = new JLabel("Time: ");
      bGridBag.setConstraints(curTimeLabel, bC);
      currPanel.add(curTimeLabel);
/*                                                                           
      bC.gridwidth = GridBagConstraints.REMAINDER;
      JLabel curTime = new JLabel(currInfo[3]);
      curTime.setForeground(Color.red);
      bGridBag.setConstraints(curTime, bC);
      currPanel.add(curTime);
*/
      bC.gridwidth = GridBagConstraints.REMAINDER;
      currInfoLabels[3].setForeground(Color.red);
      bGridBag.setConstraints(currInfoLabels[3], bC);
      currPanel.add(currInfoLabels[3]);
    gridbag.setConstraints(currPanel, c);
    panel.add(currPanel);


    /** #######End Layout####### */

    getContentPane().add(panel);
    setTitle("VCPRPE: Enter Sounding Data");
    setSize(dim);
//    pack();
    setVisible(true);
  }

  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == button1)
    {
      String sndgType = (String)soundingBox.getSelectedItem();
      File file = null;
      choiceBox.removeAllItems();

      int returnVal = fileChooser.showOpenDialog(this);
      if (returnVal == JFileChooser.APPROVE_OPTION)
      {
          file = fileChooser.getSelectedFile();
          locField.setText( file.getAbsolutePath() );
  
        if (sndgType.equalsIgnoreCase("BUFR") && file != null)
        {
          try
          { 
            parseSnd = new ParseBUFR(file); 
          }
          catch (Exception ex)
          { 
            String err0 = new String("Error opening: ");
            String err1 = locField.getText();
            String err2 = new String(" Prehaps this is not a BUFR sounding file?");
            ErrorFrame ef0 = new ErrorFrame(new String[] { err0, err1, err2 });
          }
  
        }
  
        if (sndgType.equalsIgnoreCase("RAOB") && file != null)
        {
          try
          { 
            parseSnd = new ParseRAOB(file); 
          }
          catch (Exception ex)
          { 
            String err0 = new String("Error opening: ");
            String err1 = locField.getText();
            String err2 = new String(" Prehaps this is not a RAOB sounding file?");
            ErrorFrame ef0 = new ErrorFrame(new String[] { err0, err1, err2 });
          }
  
        }
  
        Vector headder = null;
  
        if (parseSnd != null)
        { headder = parseSnd.getHeadderVect(); }
        
        try
        {
          String[] headderString = 
             (String[])headder.toArray(new String[headder.size()]);
          for (int i = 0; i < headderString.length; ++i)
          {
            choiceBox.addItem( headderString[i]);
          }
        }
        catch (Exception ex)
        {
          String err0 = new String("Error opening: ");
          String err1 = locField.getText();
          String err2 = new String(" Do you have the correct sounding TYPE?");
          ErrorFrame ef0 = new ErrorFrame(new String[] { err0, err1, err2 });
        }

    
      } // ### end "if - APPROVE"
      else
      {}

/*
      if (sndgType.equalsIgnoreCase("RAOB") && file != null)
      {
        ParseRAOB raobSnd = new ParseRAOB(file);
      }
*/

    }

    if (e.getSource() == submit)
    {
      String choiceHeadder = (String)choiceBox.getSelectedItem();
      String sndType = (String)soundingBox.getSelectedItem();
      data = new Vector();
      if (parseSnd != null && choiceHeadder != null)
      { 
        data = (Vector)parseSnd.getSoundingData(choiceHeadder).clone();
        sndHeadder = new String(sndType + "," + choiceHeadder);
        setCurrentSounding(sndHeadder);
      }
      else
      {
        String err0 = new String("Unable to parse selected sounding data: ");
        String err1 = choiceHeadder;
        String err2 = new String(" Please choose valid sounding and/or headder");
        ErrorFrame ef = new ErrorFrame(new String[] {err0, err1, err2});
      }
    }

  }

  public Vector getSoundingData()
  { return data; }

  public String getSoundingInfo()
  { return sndHeadder; } 

  public void createStrut( GridBagLayout gridbag,
                            JPanel contentPane,
                            int gridy, int gridx,
                            boolean h, int gridwidth)	
  {
    GridBagConstraints gc = new GridBagConstraints();

    Component strut = null;

    if (h)
    { strut = Box.createHorizontalStrut(10); }
    else
    { strut = Box.createVerticalStrut(10); }

    gc.fill = GridBagConstraints.BOTH;
    gc.gridx = gridx;
    gc.gridy = gridy;
    gc.gridwidth = gridwidth;
    gridbag.setConstraints(strut, gc);
    contentPane.add(strut);
  }

  public void addSeparator()
  {
    JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
    GridBagConstraints g = new GridBagConstraints();

    g.gridx = 0;
    g.gridy = GridBagConstraints.RELATIVE;
    g.fill = GridBagConstraints.HORIZONTAL;
    gridbag.setConstraints(sep, g);
    panel.add(sep);
  }

  public void setCurrentSounding(String sd)
  {
    if (sd != null)
    { 
      // ### Statement needed if user kills Sounding window
      // ### and starts a new sounding window
      if (currInfoLabels == null)
      { setNewSounding(); }

      currSdg = new String(sd);
      currInfo = sd.split(",");
      for (int i = 0; i < currInfo.length; ++i)
      {
        currInfoLabels[i].setText(currInfo[i]);
      }
    }
    else
    {
      if (currInfo == null)
      { setNewSounding(); }
    }
  }

 public void setNewSounding()
 {
   int expectedFields = 4;
   currInfoLabels = new JLabel[expectedFields];
   currInfo = new String[expectedFields];
   for (int i = 0; i < expectedFields; ++i)
   {
     currInfo[i] = new String();
     currInfoLabels[i] = new JLabel(currInfo[i]);
   }
 } // ### end setNewSounding 

  public static void main(String[] args) throws IOException
  {
    Soundings s = new Soundings(null);
  }

}


