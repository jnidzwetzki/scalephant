/*******************************************************************************
 *
 *    Copyright (C) 2015-2020 the BBoxDB project
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
package org.bboxdb.network.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bboxdb.commons.math.Hyperrectangle;
import org.bboxdb.distribution.partitioner.SpacePartitioner;
import org.bboxdb.distribution.partitioner.SpacePartitionerCache;
import org.bboxdb.distribution.partitioner.regionsplit.RangeQueryExecutor;
import org.bboxdb.distribution.partitioner.regionsplit.RangeQueryExecutor.ExecutionPolicy;
import org.bboxdb.distribution.region.DistributionRegionIdMapper;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.bboxdb.misc.BBoxDBException;
import org.bboxdb.network.packages.PackageEncodeException;
import org.bboxdb.network.packages.response.ErrorResponse;
import org.bboxdb.network.packages.response.MultipleTupleEndResponse;
import org.bboxdb.network.packages.response.MultipleTupleStartResponse;
import org.bboxdb.network.packages.response.PageEndResponse;
import org.bboxdb.network.query.ContinuousConstQueryPlan;
import org.bboxdb.network.query.ContinuousQueryPlan;
import org.bboxdb.network.query.ContinuousTableQueryPlan;
import org.bboxdb.network.query.entity.TupleAndBoundingBox;
import org.bboxdb.network.query.filter.UserDefinedFilter;
import org.bboxdb.network.query.filter.UserDefinedFilterDefinition;
import org.bboxdb.network.query.transformation.TupleTransformation;
import org.bboxdb.network.server.connection.ClientConnectionHandler;
import org.bboxdb.storage.StorageManagerException;
import org.bboxdb.storage.entity.JoinedTuple;
import org.bboxdb.storage.entity.Tuple;
import org.bboxdb.storage.entity.TupleStoreName;
import org.bboxdb.storage.tuplestore.manager.TupleStoreManager;
import org.bboxdb.storage.tuplestore.manager.TupleStoreManagerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContinuousClientQuery implements ClientQuery {

	/**
	 * The bounding box of the query
	 */
	private final Hyperrectangle boundingBox;

	/**
	 * The client connection handler
	 */
	private final ClientConnectionHandler clientConnectionHandler;

	/**
	 * The package sequence of the query
	 */
	private final short querySequence;

	/**
	 * The request table
	 */
	private final TupleStoreName requestTable;

	/**
	 * The total amount of send tuples
	 */
	private long totalSendTuples;
	
	/**
	 * Is the continuous query active
	 */
	private boolean queryActive = true;

	/**
	 * The tuples for the given key
	 */
	private final BlockingQueue<JoinedTuple> tupleQueue;
	
	/**
	 * The last query flush time
	 */
	private long lastFlushTime = System.currentTimeMillis();

	/**
	 * The maximal queue capacity
	 */
	private final static int MAX_QUEUE_CAPACITY = 1024;
	
	/**
	 * The number of tuples per page
	 */
	private final static long FLUSH_TIME_IN_MS = TimeUnit.SECONDS.toMillis(1);

	/**
	 * The tuple insert callback
	 */
	private final Consumer<Tuple> tupleInsertCallback;

	/**
	 * The tuple store manager
	 */
	private TupleStoreManager storageManager;
	
	/**
	 * The query plan
	 */
	private final ContinuousQueryPlan queryPlan;

	/**
	 * The dead pill for the queue
	 */
	private final static JoinedTuple RED_PILL = new JoinedTuple(new ArrayList<>(), new ArrayList<>());
	
	/**
	 * Keep alive pill
	 */
	private final static JoinedTuple KEEP_ALIVE_PILL = new JoinedTuple(new ArrayList<>(), new ArrayList<>());
	
	/**
	 * The Logger
	 */
	private final static Logger logger = LoggerFactory.getLogger(ContinuousClientQuery.class);
	

	public ContinuousClientQuery(final ContinuousQueryPlan queryPlan,
			final ClientConnectionHandler clientConnectionHandler,
			final short querySequence) {

			this.queryPlan = queryPlan;
			this.boundingBox = queryPlan.getQueryRange();
			this.requestTable = new TupleStoreName(queryPlan.getStreamTable());
			
			this.clientConnectionHandler = clientConnectionHandler;
			this.querySequence = querySequence;
			this.tupleQueue = new ArrayBlockingQueue<>(MAX_QUEUE_CAPACITY);

			this.totalSendTuples = 0;

			// Add each tuple to our tuple queue
			if(queryPlan instanceof ContinuousConstQueryPlan) {
				final ContinuousConstQueryPlan qp = (ContinuousConstQueryPlan) queryPlan;
				this.tupleInsertCallback = getCallbackForConstQuery(qp);
			} else if(queryPlan instanceof ContinuousTableQueryPlan) {
				final ContinuousTableQueryPlan qp = (ContinuousTableQueryPlan) queryPlan;
				this.tupleInsertCallback = getCallbackForTableQuery(qp);
			} else { 
				this.tupleInsertCallback = null;
				logger.error("Unknown query type: " + queryPlan);
				queryActive = false;
				return;
			}

			try {
				init();
			} catch (BBoxDBException e) {
				logger.error("Got exception on init", e);
				queryActive = false;
			}
	}

	/**
	 * Get the callback for a table query
	 * @param qp 
	 * @return
	 */
	private Consumer<Tuple> getCallbackForTableQuery(final ContinuousTableQueryPlan qp) {
		
		final List<TupleTransformation> streamTransformations = qp.getStreamTransformation(); 
		final Map<UserDefinedFilter, byte[]> filters = getUserDefinedFilter(qp);
				
		return (streamTuple) -> {
			
			final TupleAndBoundingBox transformedStreamTuple 
				= applyStreamTupleTransformations(streamTransformations, streamTuple);
			
			// Tuple was removed during transformation
			if(transformedStreamTuple == null) {
				return;
			}
			
			// Ignore stream elements outside of our query box
			if(! transformedStreamTuple.getBoundingBox().intersects(qp.getQueryRange())) {
				return;
			}
			
			// Callback for the stored tuple
			final Consumer<Tuple> tupleConsumer = (storedTuple) -> {
				final List<TupleTransformation> tableTransformations 
					= qp.getTableTransformation(); 
				
				final TupleAndBoundingBox transformedStoredTuple 
					= applyStreamTupleTransformations(tableTransformations, storedTuple);
				
				if(transformedStoredTuple == null) {
					logger.error("Transformed tuple is null, please check filter");
					return;
				}
				
				final boolean insertection = transformedStreamTuple.getBoundingBox()
						.intersects(transformedStoredTuple.getBoundingBox());
				
				// Is the tuple important for the query?
				if(insertection && queryPlan.isReportPositive()) {
				
					// Perform expensive UDF
					final boolean udfMatches = doUserDefinedFilterMatch(streamTuple, 
							storedTuple, filters);

					if(udfMatches == true) {
						final JoinedTuple joinedTuple = buildJoinedTuple(qp, streamTuple, storedTuple);
						queueTupleForClientProcessing(joinedTuple);
					}
					
				} else if(! insertection && ! queryPlan.isReportPositive()) {
					
					// Perform expensive UDF
					final boolean udfMatches = doUserDefinedFilterMatch(streamTuple, 
							storedTuple, filters);
					
					if(udfMatches != true) {
						final JoinedTuple joinedTuple = buildJoinedTuple(qp, streamTuple, storedTuple);
						queueTupleForClientProcessing(joinedTuple);
					}
				}
			};
			
			try {
				final TupleStoreName tupleStoreName = new TupleStoreName(qp.getJoinTable());
				
				final RangeQueryExecutor rangeQueryExecutor = new RangeQueryExecutor(tupleStoreName, 
						transformedStreamTuple.getBoundingBox(), 
						tupleConsumer, clientConnectionHandler.getStorageRegistry(),
						ExecutionPolicy.LOCAL_ONLY);
				
				rangeQueryExecutor.performDataRead();
			} catch (BBoxDBException e) {
				logger.error("Got an exeeption while quering tuples", e);
				queryActive = false;
				return;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				queryActive = false;
				return;
			}	
		};
	}

	/**
	 * Build a joined tuple 
	 * @param qp
	 * @param streamTuple
	 * @param storedTuple
	 * @return
	 */
	private JoinedTuple buildJoinedTuple(final ContinuousTableQueryPlan qp, 
			final Tuple streamTuple, final Tuple storedTuple) {
		
		return new JoinedTuple(
				Arrays.asList(streamTuple, storedTuple), 
				Arrays.asList(qp.getStreamTable(), qp.getJoinTable()));
	}

	/**
	 * Perform the user defined filters on the given stream and stored tuple
	 * @param streamTuple
	 * @param storedTuple
	 * @param filters
	 * @return
	 */
	private boolean doUserDefinedFilterMatch(final Tuple streamTuple, final Tuple storedTuple,
			final Map<UserDefinedFilter, byte[]> filters) {
		
		boolean matches = true;
		
		for(final Entry<UserDefinedFilter, byte[]> entry : filters.entrySet()) {
			
			final UserDefinedFilter operator = entry.getKey();
			final byte[] value = entry.getValue();
			
			final boolean result 
				= operator.filterJoinCandidate(streamTuple, storedTuple, value);
			
			if(! result) {
				matches = false;
			}
		}
		return matches;
	}

	/**
	 * Get the user defined operators
	 * @param tableQueryPlan
	 * @return 
	 */
	private Map<UserDefinedFilter, byte[]> getUserDefinedFilter(
			final ContinuousTableQueryPlan tableQueryPlan) {
		
		final Map<UserDefinedFilter, byte[]> operators = new HashMap<>();
		
		for(final UserDefinedFilterDefinition filter : tableQueryPlan.getAfterJoinFilter()) {
			try {
				final Class<?> filterClass = Class.forName(filter.getUserDefinedFilterClass());
				final UserDefinedFilter operator = 
						(UserDefinedFilter) filterClass.newInstance();
				operators.put(operator, filter.getUserDefinedFilterValue().getBytes());
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
				throw new IllegalArgumentException("Unable to find user defined filter class", e);
			}
		}
		
		return operators;
	}

	/**
	 * Get the consumer for the const query
	 * @param qp 
	 * @return
	 */
	private Consumer<Tuple> getCallbackForConstQuery(final ContinuousConstQueryPlan qp) {
		
		final ContinuousConstQueryPlan constQueryPlan = (ContinuousConstQueryPlan) queryPlan;
		
		return (t) -> {
			final List<TupleTransformation> transformations = constQueryPlan.getStreamTransformation(); 
			final TupleAndBoundingBox tuple = applyStreamTupleTransformations(transformations, t);
			
			// Tuple was removed during transformation
			if(tuple == null) {
				return;
			}
			
			// Is the tuple important for the query?
			if(tuple.getBoundingBox().intersects(constQueryPlan.getCompareRectangle())) {
				if(queryPlan.isReportPositive()) {
					final JoinedTuple joinedTuple = new JoinedTuple(t, requestTable.getFullname());
					queueTupleForClientProcessing(joinedTuple);
				}
			} else {
				if(! queryPlan.isReportPositive()) {
					final JoinedTuple joinedTuple = new JoinedTuple(t, requestTable.getFullname());
					queueTupleForClientProcessing(joinedTuple);
				}
			}

		};
	}

	/**
	 * Apply the stream transformations
	 * @param constQueryPlan
	 * @param inputTuple
	 * @return
	 */
	private TupleAndBoundingBox applyStreamTupleTransformations(final List<TupleTransformation> transformations,
			final Tuple inputTuple) {
				
		TupleAndBoundingBox tuple = new TupleAndBoundingBox(inputTuple, inputTuple.getBoundingBox());
		for(final TupleTransformation transformation : transformations) {
			tuple = transformation.apply(tuple);
			
			if(tuple == null) {
				break;
			}
		}
		
		return tuple;
	}

	/**
	 * Queue the tuple for client processing
	 * @param t
	 */
	private void queueTupleForClientProcessing(final JoinedTuple t) {
		final boolean insertResult = tupleQueue.offer(t);

		if(! insertResult) {
			logger.error("Unable to add tuple to continuous query, queue is full");
		}
	}

	/**
	 * Init the query
	 * @param tupleStoreManagerRegistry
	 * @throws BBoxDBException
	 */
	private void init() throws BBoxDBException {

		try {
			final TupleStoreManagerRegistry storageRegistry
				= clientConnectionHandler.getStorageRegistry();

			final String fullname = requestTable.getDistributionGroup();
			final SpacePartitioner spacePartitioner = SpacePartitionerCache.getInstance()
					.getSpacePartitionerForGroupName(fullname);

			final DistributionRegionIdMapper regionIdMapper = spacePartitioner
					.getDistributionRegionIdMapper();

			final List<TupleStoreName> localTables
				= regionIdMapper.getLocalTablesForRegion(boundingBox, requestTable);

			if(localTables.size() != 1) {
				logger.error("Got more than one table for the continuous query {}", localTables);
				close();
				return;
			}

			final TupleStoreName tupleStoreName = localTables.get(0);

			storageManager = QueryHelper.getTupleStoreManager(storageRegistry, tupleStoreName);

			storageManager.registerInsertCallback(tupleInsertCallback);

			// Remove tuple store insert listener on connection close
			clientConnectionHandler.addConnectionClosedHandler((c) -> close());
		} catch (StorageManagerException | ZookeeperException e) {
			logger.error("Got an exception during query init", e);
			close();
		}
	}

	@Override
	public void fetchAndSendNextTuples(final short packageSequence) throws IOException, PackageEncodeException {

		clientConnectionHandler.writeResultPackage(new MultipleTupleStartResponse(packageSequence));

		while(queryActive) {
			try {
				
				// Finish query _PAGE_ after at least FLUSH_TIME_IN_MS
				if(System.currentTimeMillis() >= lastFlushTime + FLUSH_TIME_IN_MS) {
					clientConnectionHandler.writeResultPackage(new PageEndResponse(packageSequence));
					clientConnectionHandler.flushPendingCompressionPackages();
					lastFlushTime = System.currentTimeMillis();
					return;
				}
				
				// Send next tuple or wait
				final JoinedTuple tuple = tupleQueue.take();
				
				if(tuple == RED_PILL) {
					logger.info("Got the red pill from the queue, cancel query");
					clientConnectionHandler.writeResultPackage(new MultipleTupleEndResponse(packageSequence));
					clientConnectionHandler.flushPendingCompressionPackages();
					this.queryActive = false;
					return;
				}
				
				// Test for query finish
				if(tuple == KEEP_ALIVE_PILL) {
					continue;
				}
				
				clientConnectionHandler.writeResultTuple(packageSequence, tuple, true);
				totalSendTuples++;
			} catch (InterruptedException e) {
				logger.info("Thread was interrupted while waiting for new tuples");
				this.queryActive = false;
				clientConnectionHandler.writeResultPackage(new ErrorResponse(packageSequence));
				clientConnectionHandler.flushPendingCompressionPackages();
				return;
			}
		}

		// All tuples are send
		clientConnectionHandler.writeResultPackage(new MultipleTupleEndResponse(packageSequence));
		clientConnectionHandler.flushPendingCompressionPackages();	
	}
	

	@Override
	public void maintenanceCallback() {
		// Release waiting query processors and 
		// let them finish the query page if needed
		
		if(tupleQueue.isEmpty()) {
			tupleQueue.offer(KEEP_ALIVE_PILL);
		}
	}

	@Override
	public boolean isQueryDone() {
		return (! queryActive);
	}

	@Override
	public void close() {
		logger.debug("Closing query {} (send {} result tuples)", querySequence, totalSendTuples);

		if(storageManager != null) {
			storageManager.removeInsertCallback(tupleInsertCallback);
		}

		queryActive = false;
		
		// Cancel next page request
		tupleQueue.offer(RED_PILL);
	}

	@Override
	public long getTotalSendTuples() {
		return totalSendTuples;
	}
}
