/*******************************************************************************
 *
 *    Copyright (C) 2015-2016
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
package de.fernunihagen.dna.scalephant;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import de.fernunihagen.dna.scalephant.distribution.membership.DistributedInstance;
import de.fernunihagen.dna.scalephant.distribution.membership.event.DistributedInstanceState;
import de.fernunihagen.dna.scalephant.distribution.placement.ResourceAllocationException;
import de.fernunihagen.dna.scalephant.distribution.placement.ResourcePlacementStrategy;
import de.fernunihagen.dna.scalephant.distribution.placement.RoundRobinResourcePlacementStrategy;

public class TestResoucePlacement {

	/**
	 * Test round robin placement 1 (empty list)
	 * @throws ResourceAllocationException
	 */
	@Test(expected=ResourceAllocationException.class)
	public void testRoundRobinPlacement0() throws ResourceAllocationException {
		final ResourcePlacementStrategy resourcePlacementStrategy = new RoundRobinResourcePlacementStrategy();
		final List<DistributedInstance> systems = new ArrayList<DistributedInstance>();
		resourcePlacementStrategy.getInstancesForNewRessource(systems);
	}
	
	/**
	 * Test round robin placement 2
	 * @throws ResourceAllocationException
	 */
	@Test
	public void testRoundRobinPlacement2() throws ResourceAllocationException {
		final ResourcePlacementStrategy resourcePlacementStrategy = new RoundRobinResourcePlacementStrategy();
		final List<DistributedInstance> systems = new ArrayList<DistributedInstance>();
		systems.add(new DistributedInstance("node1:123", "0.1", DistributedInstanceState.READWRITE));
		systems.add(new DistributedInstance("node2:123", "0.1", DistributedInstanceState.READWRITE));
		systems.add(new DistributedInstance("node3:123", "0.1", DistributedInstanceState.READWRITE));
		systems.add(new DistributedInstance("node4:123", "0.1", DistributedInstanceState.READWRITE));
		
		Assert.assertEquals(systems.get(0), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		Assert.assertEquals(systems.get(1), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		Assert.assertEquals(systems.get(2), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		Assert.assertEquals(systems.get(3), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		Assert.assertEquals(systems.get(0), resourcePlacementStrategy.getInstancesForNewRessource(systems));
	}
	
	/**
	 * Test round robin placement 3 (all systems are blacklisted)
	 * @throws ResourceAllocationException
	 */
	@Test(expected=ResourceAllocationException.class)
	public void testRoundRobinPlacement3() throws ResourceAllocationException {
		final ResourcePlacementStrategy resourcePlacementStrategy = new RoundRobinResourcePlacementStrategy();
		final List<DistributedInstance> systems = new ArrayList<DistributedInstance>();
		systems.add(new DistributedInstance("node1:123", "0.1", DistributedInstanceState.READWRITE));
		systems.add(new DistributedInstance("node2:123", "0.1", DistributedInstanceState.READWRITE));
		systems.add(new DistributedInstance("node3:123", "0.1", DistributedInstanceState.READWRITE));
		systems.add(new DistributedInstance("node4:123", "0.1", DistributedInstanceState.READWRITE));
		
		Assert.assertEquals(systems.get(0), resourcePlacementStrategy.getInstancesForNewRessource(systems, systems));
	}
	
	/**
	 * Test round robin placement 4 (removed system)
	 * @throws ResourceAllocationException
	 */
	@Test
	public void testRoundRobinPlacement4() throws ResourceAllocationException {
		final ResourcePlacementStrategy resourcePlacementStrategy = new RoundRobinResourcePlacementStrategy();
		final List<DistributedInstance> systems = new ArrayList<DistributedInstance>();
		systems.add(new DistributedInstance("node1:123", "0.1", DistributedInstanceState.READWRITE));
		systems.add(new DistributedInstance("node2:123", "0.1", DistributedInstanceState.READWRITE));
		systems.add(new DistributedInstance("node3:123", "0.1", DistributedInstanceState.READWRITE));
		systems.add(new DistributedInstance("node4:123", "0.1", DistributedInstanceState.READWRITE));
		
		Assert.assertEquals(systems.get(0), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		Assert.assertEquals(systems.get(1), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		systems.remove(1);
		Assert.assertEquals(systems.get(0), resourcePlacementStrategy.getInstancesForNewRessource(systems));
	}
	
	/**
	 * Test round robin placement 1 (only one system)
	 * @throws ResourceAllocationException
	 */
	@Test
	public void testRoundRobinPlacement5() throws ResourceAllocationException {
		final ResourcePlacementStrategy resourcePlacementStrategy = new RoundRobinResourcePlacementStrategy();
		final List<DistributedInstance> systems = new ArrayList<DistributedInstance>();
		systems.add(new DistributedInstance("node1:123", "0.1", DistributedInstanceState.READWRITE));

		Assert.assertEquals(systems.get(0), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		Assert.assertEquals(systems.get(0), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		Assert.assertEquals(systems.get(0), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		Assert.assertEquals(systems.get(0), resourcePlacementStrategy.getInstancesForNewRessource(systems));
	}

	/**
	 * No system is ready
	 * @throws ResourceAllocationException 
	 */
	@Test(expected=ResourceAllocationException.class)
	public void testNonReadySystems1() throws ResourceAllocationException {
		final ResourcePlacementStrategy resourcePlacementStrategy = new RoundRobinResourcePlacementStrategy();
		final List<DistributedInstance> systems = new ArrayList<DistributedInstance>();
		systems.add(new DistributedInstance("node1:123", "0.1", DistributedInstanceState.READONLY));
		systems.add(new DistributedInstance("node2:123", "0.1", DistributedInstanceState.UNKNOWN));
		systems.add(new DistributedInstance("node3:123", "0.1", DistributedInstanceState.UNKNOWN));
		systems.add(new DistributedInstance("node4:123", "0.1", DistributedInstanceState.UNKNOWN));
		
		resourcePlacementStrategy.getInstancesForNewRessource(systems);
	}
	
	/**
	 * Only ready systems should be returned
	 * @throws ResourceAllocationException 
	 */
	@Test
	public void testNonReadySystems2() throws ResourceAllocationException {
		final ResourcePlacementStrategy resourcePlacementStrategy = new RoundRobinResourcePlacementStrategy();
		final List<DistributedInstance> systems = new ArrayList<DistributedInstance>();
		systems.add(new DistributedInstance("node1:123", "0.1", DistributedInstanceState.READONLY));
		systems.add(new DistributedInstance("node2:123", "0.1", DistributedInstanceState.UNKNOWN));
		systems.add(new DistributedInstance("node3:123", "0.1", DistributedInstanceState.UNKNOWN));
		systems.add(new DistributedInstance("node4:123", "0.1", DistributedInstanceState.READWRITE));
		
		Assert.assertEquals(systems.get(3), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		Assert.assertEquals(systems.get(3), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		Assert.assertEquals(systems.get(3), resourcePlacementStrategy.getInstancesForNewRessource(systems));
		Assert.assertEquals(systems.get(3), resourcePlacementStrategy.getInstancesForNewRessource(systems));
	}
}
