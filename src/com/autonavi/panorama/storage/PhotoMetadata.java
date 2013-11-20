package com.autonavi.panorama.storage;

import android.location.Location;

public class PhotoMetadata {
	public String filePath;
	public Location location;
	public int heading;
	public long timestamp;
	
	public PhotoMetadata(String filePath, Location location, int heading,
			long timestamp) {
		this.filePath = filePath;
		this.location = location;
		this.heading = heading;
		this.timestamp = timestamp;
	};
	
}
