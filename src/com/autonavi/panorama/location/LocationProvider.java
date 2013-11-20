package com.autonavi.panorama.location;

import java.security.Provider;

import com.autonavi.panorama.util.Log;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

public class LocationProvider {
	private static final String TAG = LocationProvider.class.getSimpleName();
	
	private static final String GPS = "gps";
	private static final String NETWORK = "network";
	
	private Location currentLocation;
	private LocationListener gpsListener;
	private LocationListener networkListener;
	private final LocationManager locationManager;
	
	public LocationProvider(LocationManager lm) {
		locationManager = lm;
	}
	
	private boolean isBetterLocation(Location location, Location curLoc) {
		if (curLoc == null) {
			return true;
		}
		
		long timeDiff = location.getTime() - curLoc.getTime();
		boolean v6 = false;
		if (timeDiff > 12000) {
			v6 = true;
		}
		
		boolean v7 = false;
		if (timeDiff < -12000) {
			v7 = true;
		}
		
		boolean v4 = false;
		if (timeDiff > 0) {
			v4 = true;
		}
		
		if (v6) {
			return true;
		}
		
		if (v7) {
			return false;
		}
		
		float accuDiff = location.getAccuracy() - curLoc.getAccuracy();
		boolean v2 = false;
		if (accuDiff > 0) {
			v2 = true;
		}
		
		boolean v3 = false;
		if (accuDiff < 0) {
			v3 = true;
		}
		
		boolean v5 = false;
		if (accuDiff > 200) {
			v5 = true;
		}
		
		if (v3) {
			return true;
		}
		
		if (v4 && !v2) {
			return true;
		}
		
		return v4 && !v5 && isSameProvider(location.getProvider(), curLoc.getProvider());
			
	}
	
	private boolean isRunning() {
		return (gpsListener != null) && (networkListener != null);
	}
	
	private boolean isSameProvider(String provider1, String provider2) {
		if (provider1 == null) {
			return provider2 == null;
		}
		
		return provider1.equals(provider2);
	}
	
	private void updateCurrentLocation(Location location) {
		if (isBetterLocation(location, currentLocation)) {
			currentLocation = location;
		}
	}
	
	public Location getCurrentLocation() {
		return currentLocation;
	}
	
	public void startProvider() {
		if (isRunning()) {
			Log.log(TAG + ": LocationProvider has been in running.");
		}
		
		if (locationManager.isProviderEnabled(GPS)) {
			gpsListener = new MyLocationListener();
			locationManager.requestLocationUpdates(GPS, 5000, 5.0f, gpsListener);
		}
		
		if (locationManager.isProviderEnabled(NETWORK)) {
			networkListener = new MyLocationListener();
			locationManager.requestLocationUpdates(NETWORK, 5000, 5.0f, networkListener);
		}
	}
	
	public void stopProvider() {
		currentLocation = null;

		if (gpsListener != null) {
			locationManager.removeUpdates(gpsListener);
			gpsListener = null;
		}
		
		if (networkListener != null) {
			locationManager.removeUpdates(networkListener);
			networkListener = null;
		}
	}
	
	private class MyLocationListener implements LocationListener {

		@Override
		public void onLocationChanged(Location location) {
			updateCurrentLocation(location);
		}

		@Override
		public void onProviderDisabled(String provider) {}

		@Override
		public void onProviderEnabled(String provider) {}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {}
		
	}
}
