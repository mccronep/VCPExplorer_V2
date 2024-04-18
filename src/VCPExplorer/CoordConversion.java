package VCPExplorer;


public class CoordConversion
{

	final static	double Index_of_Refraction=4./3;
	final static    double EarthRadius=6371000;
	final static    double RAD =  0.017453293;


	//=========================================================================
	// Delivered by Mike Magsig 8/18/97
	//
	// Purpose: to calculate lattitude and longitude of a radar observation
	// that has been projected to the earth's surface.
	//
	// Sources: Radar Theory:
	//           Doviak and Zrnic, Doppler Radar and Weather Observations, page 13
	//          Oblique Trigonometry:
	//           Beyer, CRC Standard Math Tables, 18th edition, page 148
	//
	// Author: Mike Magsig OSF/OTB
	// email address: mmagsig@osf.noaa.gov
	//
	// Last Modified: June. 22, 1999
	//    Rewritten to bring up to date -John M. Krause
	//
	// Assumptions: Spherically stratified fluid with vertical refractivity
	//              gradient approximated by the 4/3 earth model
	//              the radar beam is nearly parallel to the earth's surface
	//              (dh/ds)**2 << 1 where dh is the height differential and ds
	//              is the ray path differential height of the radar beam
	//              << earth's radius  radius of the earth + the height of the
	//             observation ~ = the radius of the earth refractivity (n) of the
	//              beam in the atmosphere is approximately 1 *Doviak and Zrnic
	//              p.12 suggests these conditions are met in the lowest 10-20 km
	//              of the atmosphere
	//

	public static double[] AzRangetoLL(
	 double station_latitude,         // Station Latitude in degrees
	 double station_longitude,        // Station Long in degrees
	 double Azimuth,                  // Target Azimuth in degrees
	 double Range,                    // Target Range in meters
	 double elev_angle)               // Elevation angle in degrees
	{
		double height = Math.pow ( Math.pow(Range,2.) +
		Math.pow(Index_of_Refraction * EarthRadius, 2.) +
		2.* Range * Index_of_Refraction*EarthRadius*
		Math.sin(RAD*elev_angle) , 0.5)-Index_of_Refraction*EarthRadius;

		double great_circle_distance = Index_of_Refraction * EarthRadius *
			Math.asin( ( Range * Math.cos(elev_angle*RAD) ) /
			( (Index_of_Refraction*EarthRadius)+ height) );
		double target_latitude = 90. - (Math.acos( (Math.cos(great_circle_distance/EarthRadius))*
			(Math.cos(RAD*(90.-station_latitude))) +
			( (Math.sin (great_circle_distance/EarthRadius))*
			(Math.sin (RAD*(90.-station_latitude)))*(Math.cos(RAD* Azimuth) ))))/RAD;

		//   there is a problem when delta longitude is > 90 degrees
		//   this likely does not pose a problem for the Jackson, MI mosaic
		//   the great_circle check will correctly determine this value
		//   code in progress as of August 5, 1998
		//    great_circle_check =  EarthRadius*atan( (1. - (cos(RAD*(90.-*target_latitu de)))*(cos(RAD*(90.-*target_latitude))) ) /( (cos(RAD*(90.-station_latitude)))*( cos(RAD*Azimuth))*(sin(RAD*(90.-station_latitude)) ) ));

		double delta_longitude =  Math.asin( (Math.sin(great_circle_distance/EarthRadius)) *
			(Math.sin(RAD* Azimuth)) / (Math.sin(RAD*(90.- target_latitude)) ) )/RAD;
		double target_longitude = station_longitude + delta_longitude;
		return(new double[]{target_latitude,target_longitude,height});
	}


	//=========================================================================
	//
	// Delivered by Mike Magsig 8/18/97
	// Modifications:
	//  9/18/98 - Modifed to account for delta_latitude delta_longitude = 0; -jmk 
	//
	//
	// Purpose: to calculate lattitude and longitude of a radar observation
	// that has been projected to the earth's surface.
	//
	// Sources: Radar Theory:
	//           Doviak and Zrnic, Doppler Radar and Weather Observations, page 13
	//          Oblique Trigonometry:
	//            Beyer, CRC Standard Math Tables, 18th edition, page 148
	//
	// Author: Mike Magsig OSF/OTB
	// email address: mmagsig@osf.noaa.gov
	//
	// Last Modified: June 23, 1999
	//    -6/23/99  modified calls to update and clean up  -jmk
	//
	// Assumptions: Spherically stratified fluid with vertical refractivity
	//              gradient approximated by the 4/3 earth model
	//              the radar beam is nearly parallel to the earth's surface
	//              (dh/ds)**2 << 1 where dh is the height differential and ds
	//              is the ray path differential height of the radar beam
	//              << earth's radius  radius of the earth + the height of the
	//             observation ~ = the radius of the earth refractivity (n) of the
	//              beam in the atmosphere is approximately 1 *Doviak and Zrnic
	//              p.12 suggests these conditions are met in the lowest 10-20 km
	//              of the atmosphere
	//
	public static double[] LLtoAzRange(
		double target_latitude,          // Target Latitude in degrees
		double target_longitude,         // Target Longitude in degrees
		double station_latitude,         // Station Latitude in degrees
		double station_longitude,        // Station Long in degrees
		double elev_angle)               // Elevation angle in degrees
	{
		double delta_longitude = target_longitude - station_longitude;
		double delta_latitude = target_latitude - station_latitude;

		double great_circle_distance =
			EarthRadius*Math.acos( Math.cos(RAD*(90. - station_latitude))*
			Math.cos(RAD*(90. - target_latitude)) +
			Math.sin(RAD*(90. - station_latitude))*
			Math.sin(RAD*(90. - target_latitude))*Math.cos(RAD*delta_longitude) );

		double Azimuth = ( Math.asin( (Math.sin(RAD*(90. - target_latitude))) *
			(Math.sin(RAD*delta_longitude)) /
			(Math.sin(great_circle_distance/EarthRadius)) ) ) /RAD;

		if(delta_latitude < 0. ) Azimuth=180.- Azimuth;
		if(delta_longitude < 0. && delta_latitude > 0. ) Azimuth=360. + Azimuth;
		//
		// check for delta == 0 conditions -jmk
		//
		if(delta_latitude==0 && delta_longitude == 0)
			Azimuth=0.;

		if(delta_latitude==0 && delta_longitude < 0 )
			Azimuth =  270.;
		else if(delta_latitude==0 && delta_longitude > 0 )
			Azimuth = 90.;

		if(delta_longitude ==0 && delta_latitude > 0 )
			Azimuth =  0.;
		else if(delta_longitude ==0 && delta_latitude < 0 )
			Azimuth = 180.;

		double height = Index_of_Refraction*EarthRadius *
			( ((Math.cos(RAD*elev_angle))/
			(Math.cos(elev_angle*RAD + great_circle_distance/
			(Index_of_Refraction*EarthRadius)))) - 1.);

		double Range = ( Math.sin(great_circle_distance/(Index_of_Refraction*EarthRadius)) )*
			(Index_of_Refraction*EarthRadius + height) /Math.cos(RAD*elev_angle);
		return(new double[]{Azimuth,Range,height});
	}

	//=========================================================================
	//
	// V Lakshmanan
	// Purpose: to calculate radar range, azimuth and elevation of beam
	// that would observe a point.
	//
	// Sources: Radar Theory:
	//           Doviak and Zrnic, Doppler Radar and Weather Observations, page 13
	// Author: V Lakshmanan
	// email address: lakshman@nssl.noaa.gov
	//
	// Last Modified: Nov. 11, 2002
	//
	// Assumptions: Spherically stratified fluid with vertical refractivity
	//              gradient approximated by the 4/3 earth model
	//              the radar beam is nearly parallel to the earth's surface
	//              (dh/ds)**2 << 1 where dh is the height differential and ds
	//              is the ray path differential height of the radar beam
	//              << earth's radius  radius of the earth + the height of the
	//             observation ~ = the radius of the earth refractivity (n) of the
	//              beam in the atmosphere is approximately 1 *Doviak and Zrnic
	//              p.12 suggests these conditions are met in the lowest 10-20 km
	//              of the atmosphere
	//

        public static double[] LLHtoAzRangeElev(
		double target_latitude,          // Target Latitude in degrees
  		double target_longitude,         // Target Longitude in degrees
  		double target_height,           // Target ht above MSL in meters
  		double station_latitude,         // Station Latitude in degrees
  		double station_longitude,        // Station Long in degrees
  		double station_height           // Station ht above MSL in meters
		)
	{
		double delta_longitude = target_longitude - station_longitude;
		double delta_latitude = target_latitude - station_latitude;

		double great_circle_distance =
			EarthRadius*Math.acos( Math.cos(RAD*(90. - station_latitude))*
			Math.cos(RAD*(90. - target_latitude)) +
			Math.sin(RAD*(90. - station_latitude))*
			Math.sin(RAD*(90. - target_latitude))*Math.cos(RAD*delta_longitude) );
		double Azimuth = ( Math.asin( (Math.sin(RAD*(90. - target_latitude))) *
			(Math.sin(RAD*delta_longitude)) /
			(Math.sin(great_circle_distance/EarthRadius)) ) ) /RAD;

		if(delta_latitude < 0. ) Azimuth=180.- Azimuth;
		if(delta_longitude < 0. && delta_latitude > 0. ) Azimuth=360. + Azimuth;
		//
		// check for delta == 0 conditions -jmk
		//
		if(delta_latitude==0 && delta_longitude == 0)
			Azimuth=0.;
		if(delta_latitude==0 && delta_longitude < 0 )
			Azimuth =  270.;
		else if(delta_latitude==0 && delta_longitude > 0 )
			Azimuth = 90.;

		if(delta_longitude ==0 && delta_latitude > 0 )
			Azimuth =  0.;
		else if(delta_longitude ==0 && delta_latitude < 0 )
			Azimuth = 180.;

		// reverse calculate elev_angle from height difference
		double height = target_height - station_height;
		double IR = Index_of_Refraction*EarthRadius;
		double elev_angle = Math.atan( (Math.cos(great_circle_distance/IR) - (IR/(IR+height)))/
			Math.sin(great_circle_distance/IR) );
		double Range = ( Math.sin(great_circle_distance/IR) )* (IR + height) /Math.cos(elev_angle);
		elev_angle /= RAD;
		return new double[]{elev_angle,Azimuth,Range};
 	}

}
