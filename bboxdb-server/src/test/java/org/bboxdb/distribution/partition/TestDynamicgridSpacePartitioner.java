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
package org.bboxdb.distribution.partition;

import java.util.HashSet;

import org.bboxdb.commons.math.BoundingBox;
import org.bboxdb.distribution.partitioner.DistributionGroupZookeeperAdapter;
import org.bboxdb.distribution.partitioner.DistributionRegionState;
import org.bboxdb.distribution.partitioner.DynamicgridSpacePartitioner;
import org.bboxdb.distribution.region.DistributionRegion;
import org.bboxdb.distribution.region.DistributionRegionHelper;
import org.bboxdb.distribution.region.DistributionRegionIdMapper;
import org.bboxdb.distribution.zookeeper.ZookeeperClientFactory;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.bboxdb.distribution.zookeeper.ZookeeperNotFoundException;
import org.bboxdb.misc.BBoxDBException;
import org.bboxdb.storage.entity.DistributionGroupConfiguration;
import org.bboxdb.storage.entity.DistributionGroupConfigurationBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestDynamicgridSpacePartitioner {
	
	/**
	 * The name of the test region
	 */
	private static final String TEST_GROUP = "abc";
	
	/**
	 * The distribution group adapter
	 */
	private static DistributionGroupZookeeperAdapter distributionGroupZookeeperAdapter;
	
	@BeforeClass
	public static void beforeClass() throws ZookeeperException {
		distributionGroupZookeeperAdapter = ZookeeperClientFactory.getDistributionGroupAdapter();
	}
	
	@Before
	public void before() throws ZookeeperException, BBoxDBException {
		
		final String config = "[[0.0,5.0]:[0.0,6.0]];0.5"; 
		
		final DistributionGroupConfiguration configuration = DistributionGroupConfigurationBuilder
				.create(2)
				.withSpacePartitioner("org.bboxdb.distribution.partitioner.DynamicgridSpacePartitioner", config)
				.withPlacementStrategy("org.bboxdb.distribution.placement.DummyResourcePlacementStrategy", "")
				.build();

		distributionGroupZookeeperAdapter.deleteDistributionGroup(TEST_GROUP);
		distributionGroupZookeeperAdapter.createDistributionGroup(TEST_GROUP, configuration); 
	}

	@Test
	public void testRootElement() throws ZookeeperException, ZookeeperNotFoundException, BBoxDBException {
		final DynamicgridSpacePartitioner spacePartitioner = getSpacePartitioner();
		
		final DistributionRegion rootElement = spacePartitioner.getRootNode();
		Assert.assertEquals(rootElement.getState(), DistributionRegionState.SPLIT);
		
		final BoundingBox box = rootElement.getConveringBox();
		Assert.assertEquals(new BoundingBox(0.0, 5.0, 0.0, 6.0), box);
	}
	
	@Test
	public void createGridCells() throws ZookeeperException, ZookeeperNotFoundException, BBoxDBException {
		final DynamicgridSpacePartitioner spacePartitioner = getSpacePartitioner();
		final DistributionRegion rootElement = spacePartitioner.getRootNode();

		final long regions = rootElement
				.getThisAndChildRegions()
				.stream()
				.map(r -> r.getState())
				.filter(DistributionRegionHelper.PREDICATE_REGIONS_FOR_WRITE)
				.count();
				
		Assert.assertEquals(12, regions);
	}
	

	/**
	 * Get the space partitioner
	 * @return
	 * @throws ZookeeperException
	 * @throws ZookeeperNotFoundException
	 */
	private DynamicgridSpacePartitioner getSpacePartitioner() throws ZookeeperException, ZookeeperNotFoundException {
		final DynamicgridSpacePartitioner spacepartitionier = (DynamicgridSpacePartitioner) 
				distributionGroupZookeeperAdapter.getSpaceparitioner(TEST_GROUP, 
						new HashSet<>(), new DistributionRegionIdMapper());
				
		return spacepartitionier;
	}
}