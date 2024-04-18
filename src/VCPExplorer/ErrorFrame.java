package VCPExplorer;

import java.*;
import java.awt.*;
import javax.swing.*;


public class ErrorFrame extends JFrame
{

  GridBagLayout gl = new GridBagLayout();
  GridBagConstraints gbc = new GridBagConstraints();
  JPanel errorPanel = new JPanel();

  public ErrorFrame(String[] args)
  {
    super("Attention!");


    int numLines = args.length;
    JLabel[] errorLabels = new JLabel[numLines];

    gbc.gridx = 0;
    for (int i = 0; i < numLines; ++i)
    {
      errorLabels[i] = new JLabel(args[i]);
      gbc.gridy = i;
      gl.setConstraints(errorLabels[i], gbc);
      errorPanel.add(errorLabels[i]);
    }

    getContentPane().add(errorPanel, BorderLayout.CENTER);
    pack();
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    int w = (screen.width - getSize().width)/2;
    int h = (screen.height - getSize().height)/2;
    setLocation(w, h);

    setResizable(false);
    setVisible(true);
  }



}


