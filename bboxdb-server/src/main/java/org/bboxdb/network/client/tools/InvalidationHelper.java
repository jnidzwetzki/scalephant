/*******************************************************************************
 *
 *    Copyright (C) 2015-2021 the BBoxDB project
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
package org.bboxdb.network.client.tools;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.bboxdb.distribution.membership.BBoxDBInstance;
import org.bboxdb.distribution.membership.MembershipConnectionService;
import org.bboxdb.distribution.partitioner.SpacePartitionerHelper;
import org.bboxdb.distribution.region.DistributionRegion;
import org.bboxdb.misc.BBoxDBException;
import org.bboxdb.network.client.BBoxDBClient;
import org.bboxdb.network.client.BBoxDBCluster;
import org.bboxdb.network.client.connection.BBoxDBConnection;
import org.bboxdb.network.client.future.client.EmptyResultFuture;
import org.bboxdb.network.client.future.client.OperationFuture;
import org.bboxdb.network.routing.DistributionRegionHandlingFlag;
import org.bboxdb.network.routing.RoutingHeader;
import org.bboxdb.network.routing.RoutingHop;
import org.bboxdb.network.routing.RoutingHopHelper;
import org.bboxdb.storage.entity.InvalidationTuple;
import org.bboxdb.storage.entity.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvalidationHelper {

	/**
	 * The BBoxDB cluster connection
	 */
	private final BBoxDBCluster bboxdbCluster;
	
	/**
	 * The table for the inserts
	 */
	private final String table;

	/**
	 * Inserted keys
	 */
	private final Map<OperationFuture, String> keys;
	
	/**
	 * The affected regions
	 */
	private final Map<String, Set<Long>> affectedRegions;

	/**
	 * The distribution region
	 */
	private final DistributionRegion distributionRegion;
	
	/** 
	 * The future store
	 */
	private FixedSizeFutureStore fixedSizeFutureStore;
	
	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(InvalidationHelper.class);
	
	public InvalidationHelper(final BBoxDBCluster bboxdbCluster, 
			final String table,
			final FixedSizeFutureStore fixedSizeFutureStore) throws BBoxDBException {
		
		this.bboxdbCluster = bboxdbCluster;
		this.table = table;
		this.fixedSizeFutureStore = fixedSizeFutureStore;
		this.keys = new HashMap<>();
		this.affectedRegions = new HashMap<>();
		this.distributionRegion = SpacePartitionerHelper.getRootNode(table);

		fixedSizeFutureStore.addSuccessFutureCallback(o -> handleFutureSuccess(o));
	}
	
	/**
	 * Handle the future success message
	 */
	private void handleFutureSuccess(final OperationFuture operationFuture) {
		final String key = keys.remove(operationFuture);
		
		if(key == null) {
			return;
		}
		
		final Set<Long> oldRegions = affectedRegions.remove(key);
		
		final Set<Long> newRegions = operationFuture.getAffectedRegionIDs();
		affectedRegions.put(key, newRegions);
		
		if(oldRegions == null) {
			return;
		}
		
		if(oldRegions.equals(newRegions)) {
			return;
		}
				
		logger.debug("Change for key {} detected old {} / new {}", 
				key, oldRegions, newRegions);

		// Create diff
		oldRegions.removeAll(newRegions);

		final Predicate<DistributionRegion> predicate = (d) -> {
			return oldRegions.contains(d.getRegionId());
		};
		
		final List<DistributionRegion> regionsForInvalidation = distributionRegion.getThisAndChildRegions(predicate);

		final Map<List<DistributionRegion>, EnumSet<DistributionRegionHandlingFlag>> routings = new HashMap<>();
		routings.put(regionsForInvalidation, EnumSet.noneOf(DistributionRegionHandlingFlag.class));
		
		final Map<InetSocketAddress, RoutingHop> hops = RoutingHopHelper.getHopListForRegion(routings);
		final List<RoutingHop> routingList = new ArrayList<>(hops.values());

		logger.debug("Routing list for invalidation is {}", routingList);
		
		final RoutingHeader routingHeader = new RoutingHeader((short) 0, routingList);

		final BBoxDBInstance firstInstance = routingHeader.getRoutingHop().getDistributedInstance();
		final BBoxDBConnection bboxDbConnection = MembershipConnectionService.getInstance().getConnectionForInstance(firstInstance);

		final BBoxDBClient bboxdbClient = bboxDbConnection.getBboxDBClient();
		final InvalidationTuple tuple = new InvalidationTuple(key);
		final EmptyResultFuture insertFuture = bboxdbClient.insertTuple(table, tuple, routingHeader);

		fixedSizeFutureStore.put(insertFuture);
	}
	
	/**
	 * Put a new tuple
	 * @param tuple
	 * @throws BBoxDBException 
	 */
	public void putTuple(final Tuple tuple) throws BBoxDBException {
		final EmptyResultFuture resultFuture = bboxdbCluster.insertTuple(table, tuple);
		keys.put(resultFuture, tuple.getKey());
		fixedSizeFutureStore.put(resultFuture);
	}

}
