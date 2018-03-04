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
package org.bboxdb.distribution.partitioner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.bboxdb.distribution.DistributionGroupName;
import org.bboxdb.distribution.region.DistributionRegionCallback;
import org.bboxdb.distribution.region.DistributionRegionIdMapper;
import org.bboxdb.distribution.zookeeper.ZookeeperClientFactory;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.bboxdb.distribution.zookeeper.ZookeeperNodeNames;
import org.bboxdb.distribution.zookeeper.ZookeeperNotFoundException;
import org.bboxdb.misc.BBoxDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpacePartitionerCache implements Watcher {
	
	/**
	 * Mapping between the string group and the group object
	 */
	private final Map<String, SpacePartitioner> spacePartitioner;
	
	/**
	 * The versions
	 */
	private final Map<String, Long> partitionerVersions;
	
	/**
	 * The region mapper
	 */
	private final Map<String, DistributionRegionIdMapper> distributionRegionIdMapper;
	
	/**
	 * The callbacks
	 */
	private final Map<String, Set<DistributionRegionCallback>> callbacks;
	
	/**
	 * The zookeeper adapter
	 */
	private final DistributionGroupZookeeperAdapter distributionGroupAdapter;

	/**
	 * The instance
	 */
	private static SpacePartitionerCache instance;
	
	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(SpacePartitionerCache.class);



	private SpacePartitionerCache() {
		this.spacePartitioner = new HashMap<>();
		this.distributionRegionIdMapper = new HashMap<>();
		this.partitionerVersions = new HashMap<>();
		this.callbacks = new HashMap<>();
		this.distributionGroupAdapter = ZookeeperClientFactory.getDistributionGroupAdapter();
	}
	
	public synchronized static SpacePartitionerCache getInstance() {
		 if(instance == null) {
			 instance = new SpacePartitionerCache();
		 }
		 
		 return instance;
	}
	
	/**
	 * Get the distribution region for the given group name
	 * @param groupName
	 * @return
	 * @throws BBoxDBException 
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public synchronized SpacePartitioner getSpacePartitionerForGroupName(final String groupName) 
			throws BBoxDBException  {
		
		try {
			if(! spacePartitioner.containsKey(groupName)) {
				
				final List<DistributionGroupName> knownGroups = distributionGroupAdapter
						.getDistributionGroups(this);
				
				final DistributionGroupName distributionGroupName = new DistributionGroupName(groupName);
				
				if(! knownGroups.contains(distributionGroupName)) {
					throw new BBoxDBException("Distribution group " + groupName + " is not known");
				}
				
				final String path = distributionGroupAdapter.getDistributionGroupPath(groupName);
				final long version = distributionGroupAdapter.getNodeMutationVersion(path, this);
				
				// Create callback list
				final Set<DistributionRegionCallback> callback = new CopyOnWriteArraySet<>();
				callbacks.put(groupName, callback);
				
				// Create region id mapper
				final DistributionRegionIdMapper mapper = new DistributionRegionIdMapper();
				distributionRegionIdMapper.put(groupName, mapper);
				
				final SpacePartitioner adapter = distributionGroupAdapter.getSpaceparitioner(groupName, 
						callback, mapper);
				
				partitionerVersions.put(groupName, version);
				spacePartitioner.put(groupName, adapter);
			}
			
			return spacePartitioner.get(groupName);
		} catch (ZookeeperException | ZookeeperNotFoundException e) {
			throw new BBoxDBException(e);
		}
	}

	/**
	 * Process changed on the registered distribution regions
	 */
	@Override
	public void process(final WatchedEvent event) {
		// Ignore events like connected and disconnected
		if(event == null || event.getPath() == null) {
			return;
		}
		
		final String path = event.getPath();

		// Amount of distribution groups have changed
		if(path.endsWith(ZookeeperNodeNames.NAME_NODE_VERSION)) {
			logger.info("===> Got event {}", event);
			rescanGroups();
		} else {
			logger.info("===> Ignoring event for path: {}" , path);
		}
		
	}
	
	/**
	 * Rescan for newly created distribution groups
	 */
	private void rescanGroups() {
		
	}

	@Override
	protected Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException("Unable to clone a singleton");
	}
}
