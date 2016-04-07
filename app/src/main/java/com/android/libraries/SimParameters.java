package com.android.libraries;

import android.location.Location;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Mario Salierno on 14/03/2016.
 */
public class SimParameters {

    private Map<String,Location> targetLocations = new HashMap<String,Location>();

    public SimParameters(){

        //test
        //this.addNewTarget("asobrero",45.0808178,7.6655203,0);
        //this.addNewTarget("csoregina",45.082234,7.666427,0);
        //this.addNewTarget("pirandello",41.128718,14.794887,174);
        this.addNewTarget("rosina",41.1254395,14.7934637,177);
        this.addNewTarget("flora",41.1287446,14.7892335,172);
        //this.addNewTarget("duomo",41.1314897,14.7742939,136);
        //this.addNewTarget("traiano",41.1325468,14.7791313,141);
        this.addNewTarget("pirandello10",41.1292366,14.7941399, 173);
        /*
        Location asobrero = new Location("asobrero");
        asobrero.setLatitude(45.0808178);
        asobrero.setLongitude(7.6655203);

        Location csoregina = new Location("csoregina");
        csoregina.setLatitude(45.082234);
        csoregina.setLongitude(7.666427);
        */

        //Location
        //distanceTo will give you the distance in meters between the two given location ej target.distanceTo(destination).
        /*
            Location location1=new Location("locationA");
            near_locations.setLatitude(17.372102);
            near_locations.setLongitude(78.484196);
            Location location2=new Location("locationA");
            near_locations.setLatitude(17.375775);
            near_locations.setLongitude(78.469218);
            double distance=selected_location.distanceTo(near_locations);
        */
        /*
            There is only one user Location, so you can iterate List of nearby places
            can call the distanceTo() function to get the distance, you can store in an array if you like.

            From what I understand, distanceBetween() is for far away places, it's output is a WGS84 ellipsoid.
        */
        //public float bearingTo (Location dest)
        //distanceBetween

    }

    public void addNewTarget(String id, double lat, double lon, double alt){
        Location loc = new Location(id);
        loc.setLatitude(lat);
        loc.setLongitude(lon);
        loc.setAltitude(alt);
        targetLocations.put(id,loc);
    }

    public Map<String,Location> getTargetLocation() {

        return targetLocations;

    }

    public void testDistance(Location yourLoc){
        /*
        String dmia,dmiahav, dTO;

        dmia = "dmia: " + LocationUtilities.getEquirectangularApproximationDistance(yourLoc.getLatitude(),yourLoc.getLongitude(),
                                                                              targetLocation.getLatitude(),targetLocation.getLongitude());


        dmiahav = "dmiahav: " + LocationUtilities.getHaversineDistance(yourLoc.getLatitude(),yourLoc.getLongitude(),
                targetLocation.getLatitude(),targetLocation.getLongitude());


        dTO = "dTO: " +yourLoc.distanceTo(targetLocation);

        Log.d("DISTANCE!",dmia + "\n "+ dTO+"\n "+ dmiahav);
        */
    }


}