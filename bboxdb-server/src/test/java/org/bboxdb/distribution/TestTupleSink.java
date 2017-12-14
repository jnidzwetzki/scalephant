package org.bboxdb.distribution;

import java.util.ArrayList;
import java.util.Arrays;

import org.bboxdb.distribution.regionsplit.tuplesink.AbstractTupleSink;
import org.bboxdb.distribution.regionsplit.tuplesink.TupleRedistributor;
import org.bboxdb.storage.StorageManagerException;
import org.bboxdb.storage.entity.BoundingBox;
import org.bboxdb.storage.entity.Tuple;
import org.bboxdb.storage.entity.TupleStoreName;
import org.junit.Test;
import org.mockito.Mockito;

public class TestTupleSink {

	/**
	 * Redistribute a tuple without any registered regions
	 * @throws Exception
	 */
	@Test(expected=StorageManagerException.class)
	public void testTupleWithoutRegions() throws Exception {
		final TupleStoreName tupleStoreName = new TupleStoreName("3_region_abc");
				
		final TupleRedistributor tupleRedistributor = new TupleRedistributor(null, tupleStoreName);
		
		final Tuple tuple1 = new Tuple("abc", BoundingBox.EMPTY_BOX, "".getBytes());
		
		tupleRedistributor.redistributeTuple(tuple1);
	}
	
	/**
	 * Register region two times
	 * @throws Exception
	 */
	@Test(expected=StorageManagerException.class)
	public void testRegisterRegionDuplicate() throws StorageManagerException {
		final DistributionGroupName distributionGroupName = new DistributionGroupName("3_region");
		
		final DistributionRegion distributionRegion = new DistributionRegion(
				distributionGroupName, DistributionRegion.ROOT_NODE_ROOT_POINTER);

		final TupleRedistributor tupleRedistributor = createTupleRedistributor();
		
		tupleRedistributor.registerRegion(distributionRegion, new ArrayList<>());
		tupleRedistributor.registerRegion(distributionRegion, new ArrayList<>());
	}

	/**
	 * Get the tuple redistributor
	 * @return
	 */
	protected TupleRedistributor createTupleRedistributor() {
		final TupleStoreName tupleStoreName = new TupleStoreName("3_region_abc");

		return new TupleRedistributor(null, tupleStoreName);
	}
	
	/**
	 * Test the tuple redistribution
	 * @throws Exception 
	 */
	@Test
	public void testTupleRedistribution1() throws Exception {
		final DistributionGroupName distributionGroupName = new DistributionGroupName("3_region");
		
		final DistributionRegion distributionRegion1 = new DistributionRegion(
				distributionGroupName, DistributionRegion.ROOT_NODE_ROOT_POINTER);
		distributionRegion1.setConveringBox(new BoundingBox(0.0, 1.0, 0.0, 1.0, 0.0, 1.0));

		final TupleRedistributor tupleRedistributor = createTupleRedistributor();
		
		final AbstractTupleSink tupleSink1 = Mockito.mock(AbstractTupleSink.class);
		tupleRedistributor.registerRegion(distributionRegion1, Arrays.asList(tupleSink1));
		
		final Tuple tuple1 = new Tuple("abc", new BoundingBox(0.0, 1.0, 0.0, 1.0, 0.0, 1.0), "".getBytes());
	
		tupleRedistributor.redistributeTuple(tuple1);
		(Mockito.verify(tupleSink1, Mockito.times(1))).sinkTuple(Mockito.any(Tuple.class));
		
		tupleRedistributor.redistributeTuple(tuple1);
		(Mockito.verify(tupleSink1, Mockito.times(2))).sinkTuple(Mockito.any(Tuple.class));

		System.out.println(tupleRedistributor.getStatistics());
	}
	

	/**
	 * Test the tuple redistribution
	 * @throws Exception 
	 */
	@Test
	public void testTupleRedistribution2() throws Exception {
		final DistributionGroupName distributionGroupName = new DistributionGroupName("3_region");
		
		final DistributionRegion distributionRegion1 = new DistributionRegion(
				distributionGroupName, DistributionRegion.ROOT_NODE_ROOT_POINTER);
		distributionRegion1.setConveringBox(new BoundingBox(0.0, 1.0, 0.0, 1.0, 0.0, 1.0));

		final DistributionRegion distributionRegion2 = new DistributionRegion(
				distributionGroupName, DistributionRegion.ROOT_NODE_ROOT_POINTER);
		distributionRegion2.setConveringBox(new BoundingBox(5.0, 6.0, 5.0, 6.0, 5.0, 6.0));

		final TupleRedistributor tupleRedistributor = createTupleRedistributor();
		
		final AbstractTupleSink tupleSink1 = Mockito.mock(AbstractTupleSink.class);
		tupleRedistributor.registerRegion(distributionRegion1, Arrays.asList(tupleSink1));
		
		final AbstractTupleSink tupleSink2 = Mockito.mock(AbstractTupleSink.class);
		tupleRedistributor.registerRegion(distributionRegion2, Arrays.asList(tupleSink2));
		
		final Tuple tuple1 = new Tuple("abc", new BoundingBox(0.0, 1.0, 0.0, 1.0, 0.0, 1.0), "".getBytes());
	
		tupleRedistributor.redistributeTuple(tuple1);
		(Mockito.verify(tupleSink1, Mockito.times(1))).sinkTuple(Mockito.any(Tuple.class));
		(Mockito.verify(tupleSink2, Mockito.never())).sinkTuple(Mockito.any(Tuple.class));

		final Tuple tuple2 = new Tuple("abc", new BoundingBox(5.0, 6.0, 5.0, 6.0, 5.0, 6.0), "".getBytes());
		tupleRedistributor.redistributeTuple(tuple2);
		(Mockito.verify(tupleSink1, Mockito.times(1))).sinkTuple(Mockito.any(Tuple.class));
		(Mockito.verify(tupleSink2, Mockito.times(1))).sinkTuple(Mockito.any(Tuple.class));

		final Tuple tuple3 = new Tuple("abc", new BoundingBox(0.0, 6.0, 0.0, 6.0, 0.0, 6.0), "".getBytes());
		tupleRedistributor.redistributeTuple(tuple3);
		(Mockito.verify(tupleSink1, Mockito.atLeast(2))).sinkTuple(Mockito.any(Tuple.class));
		(Mockito.verify(tupleSink2, Mockito.atLeast(2))).sinkTuple(Mockito.any(Tuple.class));

		System.out.println(tupleRedistributor.getStatistics());
	}

}