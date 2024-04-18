package VCPExplorer;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.BorderFactory.*;

public class InfoPanel extends JPanel
{

//  static GridBagLayout gridbag = new GridBagLayout();
//  static GridBagConstraints c = new GridBagConstraints();
  Border raisedbevel = BorderFactory.createRaisedBevelBorder();
  Border loweredbevel = BorderFactory.createLoweredBevelBorder();
  Border loweredetched = 
             BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
  Border compound = 
             BorderFactory.createCompoundBorder(raisedbevel, loweredbevel);
  TitledBorder titled;

  JLabel id, siteName, location, lat, lon, alt;

  public InfoPanel(String[] radarInfo)
  {
    titled = 
          BorderFactory.createTitledBorder(compound, "Current Radar Info");
    setBorder(titled);

    id = new JLabel();
    id.setForeground(Color.blue);

    siteName = new JLabel();
    siteName.setForeground(Color.blue);

    location = new JLabel();
    location.setForeground(Color.blue);

    lat = new JLabel();
    lat.setForeground(Color.blue);

    lon = new JLabel();
    lon.setForeground(Color.blue);

    alt = new JLabel();
    alt.setForeground(Color.blue);



    add(new JLabel("ICAO: "));
    add(id);
    add(new JLabel(","));

    add(new JLabel("Site Name: "));
    add(siteName);
    add(new JLabel(","));

    add(new JLabel("Location: "));
    add(location);
    add(new JLabel(","));

    add(new JLabel("Latitude: "));
    add(lat);
    add(new JLabel(","));

    add(new JLabel("Longitude: "));
    add(lon);
    add(new JLabel(","));

    add(new JLabel("Feedhorn Elevation (m): "));
    add(alt);

    setLayout(new FlowLayout());

    update(radarInfo);
  }

  public void update(String[] rInfo)
  {
    id.setText(rInfo[0]);
    siteName.setText("\"" + rInfo[1] + "\"");
    location.setText(rInfo[2] + ", " + rInfo[3]);
    lat.setText(rInfo[4]);
    lon.setText(rInfo[5]);
    alt.setText(rInfo[6]);
  }
}











