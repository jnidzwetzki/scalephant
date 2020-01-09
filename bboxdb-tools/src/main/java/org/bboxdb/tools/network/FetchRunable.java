/*******************************************************************************
 *
 *    Copyright (C) 2015-2018 the BBoxDB project
 *  
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License. 
 *    
 *******************************************************************************/
package org.bboxdb.tools.network;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bboxdb.commons.MathUtil;
import org.bboxdb.commons.concurrent.ExceptionSafeRunnable;
import org.bboxdb.commons.math.GeoJsonPolygon;
import org.bboxdb.misc.BBoxDBException;
import org.bboxdb.network.client.BBoxDB;
import org.bboxdb.network.client.future.client.EmptyResultFuture;
import org.bboxdb.network.client.tools.FixedSizeFutureStore;
import org.bboxdb.storage.entity.Tuple;
import org.bboxdb.storage.sstable.spatialindex.SpatialIndexBuilder;
import org.bboxdb.storage.sstable.spatialindex.SpatialIndexEntry;
import org.bboxdb.storage.sstable.spatialindex.rtree.RTreeBuilder;
import org.bboxdb.tools.converter.tuple.GeoJSONTupleBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

public class FetchRunable extends ExceptionSafeRunnable {
	
	/**
	 * The url to fetch the data
	 */
	private final String urlString;
	
	/**
	 * The auth key to fetch the data
	 */
	private final String authKey;
	
	/**
	 * The bboxdb client
	 */
	private final BBoxDB bboxdbClient;
	
	/**
	 * The table to insert
	 */
	private final String table;
	
	/**
	 * The fetch delay
	 */
	private final long fetchDelay;
	
	/**
	 * The pending futures
	 */
	private final FixedSizeFutureStore pendingFutures;
	
	
	/**
	 * The logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(FetchRunable.class);
	
	
	
	public FetchRunable(final String urlString, final String authKey, final BBoxDB bboxdbClient, 
			final String table, final FixedSizeFutureStore pendingFutures, final long fetchDelay) {
				this.urlString = urlString;
				this.authKey = authKey;
				this.bboxdbClient = bboxdbClient;
				this.table = table;
				this.fetchDelay = fetchDelay;
				this.pendingFutures = pendingFutures;
	}

	/**
	 * Fetch and insert data
	 * 
	 * curl -X GET --header 'Accept: text/plain' --header 'Authorization: ' 'https://api.transport.nsw.gov.au/v1/gtfs/vehiclepos/buses'
	 * @param bboxdbClient 
	 */
	@Override
	protected void runThread() throws Exception {
		while(! Thread.currentThread().isInterrupted()) {
			final URL url = new URL(urlString);
			final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setDoInput(true);
			connection.setRequestProperty("Accept", "text/plain");
			connection.setRequestProperty("Authorization", "apikey " + authKey);
			
			final FeedMessage message = GtfsRealtime.FeedMessage.parseFrom(connection.getInputStream());
			
			final List<FeedEntity> entities = message.getEntityList();
			final List<GeoJsonPolygon> polygonList = new ArrayList<>();
			
			for(final GtfsRealtime.FeedEntity entity : entities) {
				final VehiclePosition vehicle = entity.getVehicle();
				final TripDescriptor trip = vehicle.getTrip();
				final Position position = vehicle.getPosition();

				final String idString = trip.getTripId().replace("_", "");
				final Optional<Long> id = MathUtil.tryParseLong(idString);
				
				if(! id.isPresent()) {
					logger.warn("Skipping element with invalid id: {}", idString);
					continue;
				}
				
				final GeoJsonPolygon geoJsonPolygon = new GeoJsonPolygon(id.get());
				
				geoJsonPolygon.addProperty("RouteID", trip.getRouteId());
				geoJsonPolygon.addProperty("TripID", trip.getTripId());
				geoJsonPolygon.addProperty("Speed", Float.toString(position.getSpeed()));
				geoJsonPolygon.addProperty("Bearing", Float.toString(position.getBearing()));
				geoJsonPolygon.addProperty("DirectionID", Integer.toString(trip.getDirectionId()));
				geoJsonPolygon.addPoint(position.getLongitude(), position.getLatitude());
				
				polygonList.add(geoJsonPolygon);
			}
			
			final int inserts = insertData(polygonList);
			
			System.out.format("Inserted %d elements (read %d) %n", inserts, polygonList.size());
			
			Thread.sleep(fetchDelay);
		}
	}


	/**
	 * Insert the received tuples
	 * @param bboxdbClient
	 * @param table 
	 * @param polygonList
	 * @return
	 * @throws BBoxDBException
	 */
	private int insertData(final List<GeoJsonPolygon> polygonList) throws BBoxDBException {
		
		// Sort by id
		polygonList.sort((p1, p2) -> Long.compare(p1.getId(), p2.getId()));
		
		final SpatialIndexBuilder index = new RTreeBuilder();
		final GeoJSONTupleBuilder tupleBuilder = new GeoJSONTupleBuilder();

		for(int i = 0; i < polygonList.size(); i++) {
			final GeoJsonPolygon polygon = polygonList.get(i);
			final SpatialIndexEntry spe = new SpatialIndexEntry(polygon.getBoundingBox(), i);
			index.insert(spe);
		}
					
		final Set<Integer> processedElements = new HashSet<>();
		int inserts = 0;
		
		for(int i = 0; i < polygonList.size(); i++) {
			
			if(processedElements.contains(i)) {
				continue;
			}
			
			final GeoJsonPolygon polygon = polygonList.get(i);
			final String key = Long.toString(polygon.getId());
			final Tuple tuple = tupleBuilder.buildTuple(key, polygon.toGeoJson());
			
			// Merge entries
			final List<? extends SpatialIndexEntry> entries = index.getEntriesForRegion(polygon.getBoundingBox());
			for(SpatialIndexEntry entry : entries) {
				processedElements.add(entry.getValue());
			}
			
			final EmptyResultFuture insertFuture = bboxdbClient.insertTuple(table, tuple);
			pendingFutures.put(insertFuture);
			inserts++;
		}
		return inserts;
	}

}
