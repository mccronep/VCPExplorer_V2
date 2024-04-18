package VCPExplorer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

class SplashScreen extends JWindow implements ActionListener {
    
    private Timer timer;
    private JFrame owner;
    JLabel label = new JLabel("My Splash Screen", SwingConstants.CENTER);
    
    public SplashScreen(JFrame owner) {
        super(owner);
        this.owner = owner;
        timer = new Timer(1000, this);
        getContentPane().add(label, BorderLayout.CENTER, -1);
        setSize(200, 100);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getSize().width)/2,
                    (screen.height - getSize().height)/2);
    }
   

    public void display()
    {
      getContentPane().add(label, BorderLayout.CENTER, -1);
      show();
      setVisible(true);

//      timer = new Timer(1000, this);
//      timer.start();
    }

    public void end()
    {
      setVisible(false);
      dispose();
//      timer.stop();
    }

 
    /**
     * Displays the splash screen for the specified 
     * minimum duration in milliseconds
     */
    public void display(int millisec) {
//System.out.println(owner.isVisible());
        if (millisec > 0) {
            setVisible(true);
            timer.setInitialDelay(millisec);
            timer.start();
        }
    }
    
    /**
     * Invoked when the timer fires
     */
    public void actionPerformed(ActionEvent e) {}//System.out.println(e);}
/*
    public void actionPerformed(ActionEvent e) {
System.out.println(e);
        if (owner.isVisible()) {
            timer.stop();
            timer.removeActionListener(this);
            setVisible(false);
            dispose();
        }
    }
*/
    public void destroy() throws Throwable
    {
      timer.removeActionListener(this);
      timer = null;
      owner = null;
      label = null;
      finalize();
    }

}

