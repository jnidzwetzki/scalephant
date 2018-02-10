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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.zookeeper.Watcher;
import org.bboxdb.commons.InputParseException;
import org.bboxdb.commons.MathUtil;
import org.bboxdb.distribution.DistributionGroupName;
import org.bboxdb.distribution.DistributionRegion;
import org.bboxdb.distribution.membership.BBoxDBInstance;
import org.bboxdb.distribution.zookeeper.ZookeeperClient;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.bboxdb.distribution.zookeeper.ZookeeperNodeNames;
import org.bboxdb.distribution.zookeeper.ZookeeperNotFoundException;
import org.bboxdb.storage.entity.DistributionGroupConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributionGroupZookeeperAdapter {

	/**
	 * The zookeeper client
	 */
	protected final ZookeeperClient zookeeperClient;
	
	/**
	 * The logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(DistributionGroupZookeeperAdapter.class);


	public DistributionGroupZookeeperAdapter(ZookeeperClient zookeeperClient) {
		this.zookeeperClient = zookeeperClient;
	}
	
	/**
	 * Get the next table id for a given distribution group
	 * @return
	 * @throws ZookeeperException 
	 */
	public int getNextTableIdForDistributionGroup(final String distributionGroup) throws ZookeeperException {
		
		final String distributionGroupIdQueuePath = getDistributionGroupIdQueuePath(distributionGroup);
		
		zookeeperClient.createDirectoryStructureRecursive(distributionGroupIdQueuePath);
	
		final String nodePath = distributionGroupIdQueuePath + "/" 
				+ ZookeeperNodeNames.SEQUENCE_QUEUE_PREFIX;
		
		final String nodename = zookeeperClient.createPersistentSequencialNode(
				nodePath, "".getBytes());
		
		// Delete the created node
		logger.debug("Got new table id; deleting node: {}", nodename);
		
		zookeeperClient.deleteNodesRecursive(nodename);
		
		// id-0000000063
		// Element 0: id-
		// Element 1: The number of the node
		final String[] splittedName = nodename.split(ZookeeperNodeNames.SEQUENCE_QUEUE_PREFIX);
		try {
			return Integer.parseInt(splittedName[1]);
		} catch(NumberFormatException e) {
			logger.warn("Unable to parse number: " + splittedName[1], e);
			throw new ZookeeperException(e);
		}
	}
	
	/**
	 * Read the structure of a distribution group
	 * @return
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public SpacePartitioner getSpaceparitioner(final String distributionGroup) 
			throws ZookeeperException {
		
		final String path = getDistributionGroupPath(distributionGroup);

		if(! zookeeperClient.exists(path)) {
			final String exceptionMessage = MessageFormat.format("Unable to read {0}. Path {1} does not exist", distributionGroup, path);
			throw new ZookeeperException(exceptionMessage);
		}
		
		return SpacePartitionerFactory.getSpacePartitionerForDistributionGroup(zookeeperClient, 
				this, distributionGroup);
	}
	
	/**
	 * Get the split position for a given path
	 * @param path
	 * @return
	 * @throws ZookeeperException
	 * @throws ZookeeperNotFoundException 
	 */
	protected double getSplitPositionForPath(final String path) throws ZookeeperException, ZookeeperNotFoundException  {
		
		final String splitPathName = path + "/" + ZookeeperNodeNames.NAME_SPLIT;
		String splitString = null;
		
		try {			
			splitString = zookeeperClient.readPathAndReturnString(splitPathName, false, null);
			return Double.parseDouble(splitString);
		} catch (NumberFormatException e) {
			throw new ZookeeperException("Unable to parse split pos '" + splitString + "' for " + splitPathName);
		}		
	}
	
	/**
	 * Set the split position for the given path
	 * @param path
	 * @param position
	 * @throws ZookeeperException 
	 */
	public void setSplitPositionForPath(final String path, final double position) throws ZookeeperException {
		final String splitPosString = Double.toString(position);
		zookeeperClient.createPersistentNode(path + "/" + ZookeeperNodeNames.NAME_SPLIT, 
				splitPosString.getBytes());
	}

	/**
	 * Test weather the group path is split or not
	 * @param path
	 * @return
	 * @throws ZookeeperException 
	 */
	protected boolean isGroupSplitted(final String path) throws ZookeeperException {

		final String splitPathName = path + "/" + ZookeeperNodeNames.NAME_SPLIT;
		
		if(! zookeeperClient.exists(splitPathName)) {
			return false;
		} else {
			return true;
		}
	}
	
	/**
	 * Get the state for a given path - version without a watcher
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public DistributionRegionState getStateForDistributionRegion(final String path) 
			throws ZookeeperException, ZookeeperNotFoundException {
		
		return getStateForDistributionRegion(path, null);
	}
	
	/**
	 * Get the state for a given path
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public DistributionRegionState getStateForDistributionRegion(final String path, 
			final Watcher callback) throws ZookeeperException, ZookeeperNotFoundException {
		
		final String statePath = path + "/" + ZookeeperNodeNames.NAME_SYSTEMS_STATE;
		final String state = zookeeperClient.readPathAndReturnString(statePath, false, callback);
		return DistributionRegionState.fromString(state);
	}
	
	
	/**
	 * Set the given region to full (if possible)
	 * @param region
	 * @return
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public boolean setToFull(final DistributionRegion region) 
			throws ZookeeperException, ZookeeperNotFoundException {
		
		logger.debug("Set state for {} to full", region.getIdentifier());
		
		final String zookeeperPath = getZookeeperPathForDistributionRegionState(region);
				
		final DistributionRegionState oldState = getStateForDistributionRegion(region);
		
		if(oldState != DistributionRegionState.ACTIVE) {
			logger.debug("Old state is not active (old value {})" , oldState);
			return false;
		}
		
		return zookeeperClient.testAndReplaceValue(zookeeperPath, 
				DistributionRegionState.ACTIVE.getStringValue(), 
				DistributionRegionState.ACTIVE_FULL.getStringValue());
	}
	
	/**
	 * Set the given region to full (if possible)
	 * @param region
	 * @return
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public boolean setToSplitMerging(final DistributionRegion region) 
			throws ZookeeperException, ZookeeperNotFoundException {
		
		logger.debug("Set state for {} to merging", region.getIdentifier());
		
		final String zookeeperPath = getZookeeperPathForDistributionRegionState(region);
				
		final DistributionRegionState oldState = getStateForDistributionRegion(region);
		
		if(oldState != DistributionRegionState.SPLIT) {
			logger.debug("Old state is not active (old value {})" , oldState);
			return false;
		}
		
		return zookeeperClient.testAndReplaceValue(zookeeperPath, 
				DistributionRegionState.SPLIT.getStringValue(), 
				DistributionRegionState.SPLIT_MERGING.getStringValue());
	}
	
	/**
	 * Get the state for a given path - without watcher
	 * @return 
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public DistributionRegionState getStateForDistributionRegion(final DistributionRegion region) 
			throws ZookeeperException, ZookeeperNotFoundException  {
		
		return getStateForDistributionRegion(region, null);
	}

	/**
	 * Get the state for a given path
	 * @return 
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public DistributionRegionState getStateForDistributionRegion(final DistributionRegion region, 
			final Watcher callback) throws ZookeeperException, ZookeeperNotFoundException  {
		
		final String path = getZookeeperPathForDistributionRegion(region);
		return getStateForDistributionRegion(path, callback);
	}
	
	/**
	 * Set the state for a given path
	 * @param path
	 * @param state
	 * @throws ZookeeperException 
	 */
	public void setStateForDistributionGroup(final String path, final DistributionRegionState state) throws ZookeeperException  {
		final String statePath = path + "/" + ZookeeperNodeNames.NAME_SYSTEMS_STATE;
		zookeeperClient.setData(statePath, state.getStringValue());
	}
	
	/**
	 * Set the state for a given distribution region
	 * @param region
	 * @param state
	 * @throws ZookeeperException
	 */
	public void setStateForDistributionGroup(final DistributionRegion region, final DistributionRegionState state) throws ZookeeperException  {
		final String path = getZookeeperPathForDistributionRegion(region);
		setStateForDistributionGroup(path, state);
	}
	
	/**
	 * Get the path for the distribution region state
	 * @param region
	 * @return
	 */
	protected String getZookeeperPathForDistributionRegionState(final DistributionRegion region) {
		
		return getZookeeperPathForDistributionRegion(region) 
				+ "/" + ZookeeperNodeNames.NAME_SYSTEMS_STATE;
	}

	/**
	 * Create a new distribution group
	 * @param distributionGroup
	 * @param replicationFactor
	 * @throws ZookeeperException 
	 */
	public void createDistributionGroup(final String distributionGroup, 
			final DistributionGroupConfiguration configuration) throws ZookeeperException {
		
		final String path = getDistributionGroupPath(distributionGroup);
		
		zookeeperClient.createPersistentNode(path, "".getBytes());
		
		final int nameprefix = getNextTableIdForDistributionGroup(distributionGroup);
					
		zookeeperClient.createPersistentNode(path + "/" + ZookeeperNodeNames.NAME_NAMEPREFIX, 
				Integer.toString(nameprefix).getBytes());
		
		zookeeperClient.createPersistentNode(path + "/" + ZookeeperNodeNames.NAME_SYSTEMS, 
				"".getBytes());

		zookeeperClient.createPersistentNode(path + "/" + ZookeeperNodeNames.NAME_SYSTEMS_VERSION, 
				Long.toString(System.currentTimeMillis()).getBytes());
		
		zookeeperClient.createPersistentNode(path + "/" + ZookeeperNodeNames.NAME_SYSTEMS_STATE, 
				DistributionRegionState.ACTIVE.getStringValue().getBytes());
		
		setDistributionGroupConfiguration(distributionGroup, configuration);
	}

	/**
	 * @param distributionGroup
	 * @param configuration
	 * @param path
	 * @throws ZookeeperException
	 */
	private void setDistributionGroupConfiguration(final String distributionGroup,
			final DistributionGroupConfiguration configuration) throws ZookeeperException {
		
		final String path = getDistributionGroupPath(distributionGroup);
		
		zookeeperClient.createPersistentNode(path + "/" + ZookeeperNodeNames.NAME_DIMENSIONS, 
				Integer.toString(configuration.getDimensions()).getBytes());
		
		zookeeperClient.createPersistentNode(path + "/" + ZookeeperNodeNames.NAME_REPLICATION, 
				Short.toString(configuration.getReplicationFactor()).getBytes());
		
		setRegionSizeForDistributionGroup(distributionGroup, configuration.getMaximumRegionSize(), 
				configuration.getMaximumRegionSize());
		
		// Placement
		final String placementPath = path + "/" + ZookeeperNodeNames.NAME_PLACEMENT_STRATEGY;
		zookeeperClient.replacePersistentNode(placementPath, configuration.getPlacementStrategy().getBytes());
		final String placementConfigPath = path + "/" + ZookeeperNodeNames.NAME_PLACEMENT_CONFIG;
		zookeeperClient.replacePersistentNode(placementConfigPath, configuration.getPlacementStrategyConfig().getBytes());
		
		// Space partitioner
		final String spacePartitionerPath = path + "/" + ZookeeperNodeNames.NAME_SPACEPARTITIONER;
		zookeeperClient.replacePersistentNode(spacePartitionerPath, configuration.getSpacePartitioner().getBytes());
		final String spacePartitionerConfigPath = path + "/" + ZookeeperNodeNames.NAME_SPACEPARTITIONER_CONFIG;
		zookeeperClient.replacePersistentNode(spacePartitionerConfigPath, configuration.getSpacePartitionerConfig().getBytes());
	}
	
	/**
	 * Get the zookeeper path for a distribution region
	 * @param distributionRegion
	 * @return
	 */
	public String getZookeeperPathForDistributionRegion(
			final DistributionRegion distributionRegion) {
		
		final StringBuilder sb = new StringBuilder();
		
		DistributionRegion tmpRegion = distributionRegion;
		
		if(tmpRegion != null) {
			while(tmpRegion.getParent() != DistributionRegion.ROOT_NODE_ROOT_POINTER) {
				if(tmpRegion.isLeftChild()) {
					sb.insert(0, "/" + ZookeeperNodeNames.NAME_LEFT);
				} else {
					sb.insert(0, "/" + ZookeeperNodeNames.NAME_RIGHT);
				}
				
				tmpRegion = tmpRegion.getParent();
			}
		}
		
		final String name = distributionRegion.getDistributionGroupName().getFullname();
		sb.insert(0, getDistributionGroupPath(name));
		return sb.toString();
	}
	
	/**
	 * Get the node for the given zookeeper path
	 * @param distributionRegion
	 * @param path
	 * @return
	 */
	public DistributionRegion getNodeForPath(final DistributionRegion distributionRegion, 
			final String path) {
		
		final String name = distributionRegion.getDistributionGroupName().getFullname();
		final String distributionGroupPath = getDistributionGroupPath(name);
		
		if(! path.startsWith(distributionGroupPath)) {
			throw new IllegalArgumentException("Path " + path + " does not start with " + distributionGroupPath);
		}
		
		final StringBuilder sb = new StringBuilder(path);
		sb.delete(0, distributionGroupPath.length());
		
		DistributionRegion resultElement = distributionRegion;
		
		while(sb.length() > 0) {
			// Remove '/'
			if(sb.length() > 0) {
				sb.delete(0, 1);
			}
			
			// Element is removed
			if(resultElement == null) {
				return null;
			}

			if(sb.indexOf(ZookeeperNodeNames.NAME_LEFT) == 0) {
				resultElement = resultElement.getLeftChild();
				sb.delete(0, ZookeeperNodeNames.NAME_LEFT.length());
			} else if(sb.indexOf(ZookeeperNodeNames.NAME_RIGHT) == 0) {
				resultElement = resultElement.getRightChild();
				sb.delete(0, ZookeeperNodeNames.NAME_RIGHT.length());
			} else {
				throw new IllegalArgumentException("Unable to decode " + sb);
			}
		}
		
		return resultElement;
	}
	
	/**
	 * Get the systems for the distribution region
	 * @param region
	 * @param callback 
	 * @return
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public Collection<BBoxDBInstance> getSystemsForDistributionRegion(final DistributionRegion region, 
			final Watcher callback) throws ZookeeperException, ZookeeperNotFoundException {
	
		final Set<BBoxDBInstance> result = new HashSet<BBoxDBInstance>();
		
		final String path = getZookeeperPathForDistributionRegion(region) 
				+ "/" + ZookeeperNodeNames.NAME_SYSTEMS;
		
		// Does the requested node exists?
		if(! zookeeperClient.exists(path)) {
			return null;
		}
		
		final List<String> childs = zookeeperClient.getChildren(path, callback);
		
		if(childs != null && !childs.isEmpty()) {
			for(final String childName : childs) {
				result.add(new BBoxDBInstance(childName));
			}
		}
		
		return result;
	}
	
	/**
	 * Add a system to a distribution region
	 * @param region
	 * @param system
	 * @throws ZookeeperException 
	 */
	public void addSystemToDistributionRegion(final DistributionRegion region, 
			final BBoxDBInstance system) throws ZookeeperException {
		
		if(system == null) {
			throw new IllegalArgumentException("Unable to add system with value null");
		}
	
		final String path = getZookeeperPathForDistributionRegion(region) 
				+ "/" + ZookeeperNodeNames.NAME_SYSTEMS;
		
		logger.debug("Register system under systems node: {}", path);
		
		zookeeperClient.createPersistentNode(path + "/" + system.getStringValue(), "".getBytes());
	}
	
	/**
	 * Set the checkpoint for the distribution region and system
	 * @param region
	 * @param system
	 * @throws ZookeeperException
	 * @throws InterruptedException 
	 */
	public void setCheckpointForDistributionRegion(final DistributionRegion region, final BBoxDBInstance system, final long checkpoint) throws ZookeeperException, InterruptedException {
		if(system == null) {
			throw new IllegalArgumentException("Unable to add system with value null");
		}
		
		final String path = getZookeeperPathForDistributionRegion(region) 
				+ "/" + ZookeeperNodeNames.NAME_SYSTEMS + "/" + system.getStringValue();
		
		logger.debug("Set checkpoint for: {} to {}", path, checkpoint);
		
		if(! zookeeperClient.exists(path)) {
			throw new ZookeeperException("Path " + path + " does not exists");
		}
		
		zookeeperClient.setData(path, Long.toString(checkpoint));
	}
	
	/**
	 * Get the checkpoint for the distribution region and system
	 * @param region
	 * @param system
	 * @return 
	 * @throws ZookeeperException
	 */
	public long getCheckpointForDistributionRegion(final DistributionRegion region, final BBoxDBInstance system) throws ZookeeperException {
		if(system == null) {
			throw new IllegalArgumentException("Unable to add system with value null");
		}
		
		try {
			final String path = getZookeeperPathForDistributionRegion(region) 
					+ "/" + ZookeeperNodeNames.NAME_SYSTEMS + "/" + system.getStringValue();
		
			if(! zookeeperClient.exists(path)) {
				throw new ZookeeperException("Path " + path + " does not exists");
			}

			final String checkpointString = zookeeperClient.getData(path);
			
			if("".equals(checkpointString)) {
				return -1;
			}
			
			return Long.parseLong(checkpointString);
		} catch (NumberFormatException e) {
			throw new ZookeeperException(e);
		}
	}
			
	/**
	 * Delete a system to a distribution region
	 * @param region
	 * @param system
	 * @return 
	 * @throws ZookeeperException 
	 */
	public boolean deleteSystemFromDistributionRegion(final DistributionRegion region, final BBoxDBInstance system) throws ZookeeperException {
		
		if(system == null) {
			throw new IllegalArgumentException("Unable to delete system with value null");
		}

		final String path = getZookeeperPathForDistributionRegion(region) + "/" + ZookeeperNodeNames.NAME_SYSTEMS + "/" + system.getStringValue();
		
		if(! zookeeperClient.exists(path)) {
			return false;
		}
		
		zookeeperClient.deleteNodesRecursive(path);
	
		return true;
	}
	

	/**
	 * Get the path for the distribution group id queue
	 * @param distributionGroup
	 * @return
	 */
	public String getDistributionGroupIdQueuePath(final String distributionGroup) {
		 return getDistributionGroupPath(distributionGroup) 
				 + "/" + ZookeeperNodeNames.NAME_PREFIXQUEUE;
	}
	
	/**
	 * Get the path for the distribution group
	 * @param distributionGroup
	 * @return
	 */
	public String getDistributionGroupPath(final String distributionGroup) {
		return zookeeperClient.getClusterPath() + "/" + distributionGroup;
	}
	
	/**
	 * Return the path for the cluster
	 * @return
	 */
	public String getClusterPath() {
		return zookeeperClient.getClusterPath();
	}

	/**
	 * Delete an existing distribution group
	 * @param distributionGroup
	 * @throws ZookeeperException 
	 */
	public void deleteDistributionGroup(final String distributionGroup) throws ZookeeperException {
		
		// Does the path not exist? We are done!
		if(! isDistributionGroupRegistered(distributionGroup)) {
			return;
		}
		
		final String path = getDistributionGroupPath(distributionGroup);			
		zookeeperClient.deleteNodesRecursive(path);
		
		// Wait for event settling
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
	/**
	 * Does the distribution group exists?
	 * @param distributionGroup
	 * @return 
	 * @throws ZookeeperException
	 */
	public boolean isDistributionGroupRegistered(final String distributionGroup) throws ZookeeperException {
		final String path = getDistributionGroupPath(distributionGroup);

		if(! zookeeperClient.exists(path)) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * List all existing distribution groups
	 * @return
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public List<DistributionGroupName> getDistributionGroups() 
			throws ZookeeperException, ZookeeperNotFoundException {
		
		return getDistributionGroups(null);
	}

	/**
	 * List all existing distribution groups
	 * @return
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public List<DistributionGroupName> getDistributionGroups(final Watcher watcher) 
			throws ZookeeperException, ZookeeperNotFoundException {
		
		final List<DistributionGroupName> groups = new ArrayList<DistributionGroupName>();
		final String clusterPath = zookeeperClient.getClusterPath();
		final List<String> nodes = zookeeperClient.getChildren(clusterPath, watcher);
		
		if(nodes == null) {
			return groups;
		}
		
		for(final String node : nodes) {
			
			// Ignore systems
			if(ZookeeperNodeNames.NAME_SYSTEMS.equals(node)) {
				continue;
			}
			
			final DistributionGroupName groupName = new DistributionGroupName(node);
			if(groupName.isValid()) {
				groups.add(groupName);
			} else {
				logger.debug("Got invalid distribution group name from zookeeper: {}", groupName);
			}
		}
		
		return groups;
	}
	
	/**
	 * Get the version number of the distribution group
	 * @param distributionGroup
	 * @return
	 * @throws ZookeeperException
	 * @throws ZookeeperNotFoundException 
	 */
	public String getVersionForDistributionGroup(final String distributionGroup, 
			final Watcher callback) throws ZookeeperException, ZookeeperNotFoundException {
		
		final String path = getDistributionGroupPath(distributionGroup);
		final String fullPath = path + "/" + ZookeeperNodeNames.NAME_SYSTEMS_VERSION;
		return zookeeperClient.readPathAndReturnString(fullPath, false, callback);	 
	}
	
	/**
	 * Get the name prefix for a given path
	 * @param path
	 * @return
	 * @throws ZookeeperException 
	 * @throws ZookeeperNotFoundException 
	 */
	public int getRegionIdForPath(final String path) throws ZookeeperException, ZookeeperNotFoundException {
		
		final String namePrefixPath = path + "/" + ZookeeperNodeNames.NAME_NAMEPREFIX;
		String namePrefix = null;
		
		try {
			namePrefix = zookeeperClient.readPathAndReturnString(namePrefixPath, false, null);
			return Integer.parseInt(namePrefix);
		} catch (NumberFormatException e) {
			throw new ZookeeperException("Unable to parse name prefix '" + namePrefix + "' for " + namePrefixPath);
		}		
	}
	
	/**
	 * Get the distribution group confoiguration
	 * @param distributionGroup
	 * @return
	 * @throws ZookeeperException
	 * @throws ZookeeperNotFoundException
	 * @throws InputParseException
	 */
	public DistributionGroupConfiguration getDistributionGroupConfiguration(
			final String distributionGroup) throws ZookeeperException, ZookeeperNotFoundException, 
			InputParseException {
		
		final String path = getDistributionGroupPath(distributionGroup);
		final String placementConfigPath = path + "/" + ZookeeperNodeNames.NAME_PLACEMENT_CONFIG;
		final String placementConfig = zookeeperClient.readPathAndReturnString(placementConfigPath, false, null);
	
		final String placementPath = path + "/" + ZookeeperNodeNames.NAME_PLACEMENT_STRATEGY;
		final String placementStrategy = zookeeperClient.readPathAndReturnString(placementPath, false, null);

		final String spacePartitionerConfigPath = path + "/" + ZookeeperNodeNames.NAME_SPACEPARTITIONER_CONFIG;
		final String spacePartitionerConfig = zookeeperClient.readPathAndReturnString(spacePartitionerConfigPath, false, null);
	
		final String spacePartitionerPath = path + "/" + ZookeeperNodeNames.NAME_SPACEPARTITIONER;
		final String spacePartitoner = zookeeperClient.readPathAndReturnString(spacePartitionerPath, false, null);
		
		final String replicationFactorPath = path + "/" + ZookeeperNodeNames.NAME_REPLICATION;
		final String replicationFactorString = zookeeperClient.getData(replicationFactorPath);
		final short replicationFactor = (short) MathUtil.tryParseInt(replicationFactorString, () -> "Unable to parse: " + replicationFactorString);
		
		final String dimensionsPath = path + "/" + ZookeeperNodeNames.NAME_DIMENSIONS;
		final String dimensionsString = zookeeperClient.getData(dimensionsPath);
		final int dimensions = MathUtil.tryParseInt(dimensionsString, () -> "Unable to parse: " + dimensionsString);
		
		final String regionMinSizePath = path + "/" + ZookeeperNodeNames.NAME_MIN_REGION_SIZE;
		final String sizeStrinMin = zookeeperClient.readPathAndReturnString(regionMinSizePath);
		final int minRegionSize =  MathUtil.tryParseInt(sizeStrinMin, () -> "Unable to parse: " + sizeStrinMin);
		
		final String regionMaxSizePath = path + "/" + ZookeeperNodeNames.NAME_MAX_REGION_SIZE;
		final String sizeStringMax = zookeeperClient.readPathAndReturnString(regionMaxSizePath);
		final int maxRegionSize =  MathUtil.tryParseInt(sizeStringMax, () -> "Unable to parse: " + sizeStringMax);

		final DistributionGroupConfiguration configuration = new DistributionGroupConfiguration();
		configuration.setPlacementStrategyConfig(placementConfig);
		configuration.setPlacementStrategy(placementStrategy);
		configuration.setSpacePartitionerConfig(spacePartitionerConfig);
		configuration.setSpacePartitioner(spacePartitoner);
		configuration.setReplicationFactor(replicationFactor);
		configuration.setMaximumRegionSize(maxRegionSize);
		configuration.setMinimumRegionSize(minRegionSize);
		configuration.setDimensions(dimensions);
		
		return configuration;
	}
	
	/**
	 * Set the region size
	 * @param maxRegionSize
	 * @throws ZookeeperException 
	 */
	public void setRegionSizeForDistributionGroup(final String distributionGroup, 
			final int maxRegionSize, final int minRegionSize) 
			throws ZookeeperException {
		
		final String path = getDistributionGroupPath(distributionGroup);
		
		// Max region size
		final String maxRegionSizePath = path + "/" + ZookeeperNodeNames.NAME_MAX_REGION_SIZE;
		zookeeperClient.replacePersistentNode(maxRegionSizePath, Integer.toString(maxRegionSize).getBytes());
		
		// Min region size
		final String minRegionSizePath = path + "/" + ZookeeperNodeNames.NAME_MIN_REGION_SIZE;
		zookeeperClient.replacePersistentNode(minRegionSizePath, Integer.toString(minRegionSize).getBytes());
	}

	/**
	 * Update the region statistics
	 * @param region
	 * @param system
	 * @param size
	 * @param tuple
	 * @return
	 * @throws ZookeeperException
	 */
	public void updateRegionStatistics(final DistributionRegion region, 
			final BBoxDBInstance system, final long size, final long tuple) throws ZookeeperException {
		
		if(system == null) {
			throw new IllegalArgumentException("Unable to add system with value null");
		}
		
		logger.debug("Update region statistics for {} / {}", region.getDistributionGroupName().getFullname(), system);
	
		final String path = getZookeeperPathForDistributionRegion(region) 
				+ "/" + ZookeeperNodeNames.NAME_STATISTICS + "/" + system.getStringValue();
		
		zookeeperClient.createDirectoryStructureRecursive(path);
		
		final String sizePath = path + "/" + ZookeeperNodeNames.NAME_STATISTICS_TOTAL_SIZE;
		zookeeperClient.createPersistentNode(sizePath, Long.toString(size).getBytes());
		
		final String tuplePath = path + "/" + ZookeeperNodeNames.NAME_STATISTICS_TOTAL_TUPLES;
		zookeeperClient.createPersistentNode(tuplePath, Long.toString(tuple).getBytes());
	}
	
	/**
	 * Get the statistics for a given region
	 * @param region
	 * @return
	 * @throws ZookeeperNotFoundException 
	 * @throws ZookeeperException 
	 */
	public Map<BBoxDBInstance, Map<String, Long>> getRegionStatistics(final DistributionRegion region) 
			throws ZookeeperException, ZookeeperNotFoundException {
		
		final  Map<BBoxDBInstance, Map<String, Long>> result = new HashMap<>();
		
		logger.debug("Get statistics for {}", region.getDistributionGroupName().getFullname());
				
		final String statisticsPath = getZookeeperPathForDistributionRegion(region) 
				+ "/" + ZookeeperNodeNames.NAME_STATISTICS;
		
		final List<String> childs = zookeeperClient.getChildren(statisticsPath, null);
		
		// No statistics found
		if(childs == null) {
			return result;
		}
		
		for(final String system : childs) {
			final String path = statisticsPath + "/" + system;
		
			final Map<String, Long> systemMap = new HashMap<>();
			
			try {
				final String sizePath = path + "/" + ZookeeperNodeNames.NAME_STATISTICS_TOTAL_SIZE;
				if(zookeeperClient.exists(sizePath)) {
					final String sizeString = zookeeperClient.readPathAndReturnString(sizePath);
					final long size = MathUtil.tryParseLong(sizeString, () -> "Unable to parse " + sizeString);
					systemMap.put(ZookeeperNodeNames.NAME_STATISTICS_TOTAL_SIZE, size);
				}
				
				final String tuplePath = path + "/" + ZookeeperNodeNames.NAME_STATISTICS_TOTAL_TUPLES;
				if(zookeeperClient.exists(tuplePath)) {
					final String tuplesString = zookeeperClient.readPathAndReturnString(tuplePath);
					final long tuples = MathUtil.tryParseLong(tuplesString, () -> "Unable to parse " + tuplesString);
					systemMap.put(ZookeeperNodeNames.NAME_STATISTICS_TOTAL_TUPLES, tuples);
				}
				
				result.put(new BBoxDBInstance(system), systemMap);
			} catch (InputParseException e) {
				logger.error("Unable to read statistics", e);
			}
		}		
		
		return result;
	}

}