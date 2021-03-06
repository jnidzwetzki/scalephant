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
package org.bboxdb.network.server.query.continuous;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ContinuousQueryExecutionState {

	/**
	 * The stream tuple that are matched in the last query execution
	 */
	protected final Set<String> containedStreamKeys;
	
	/**
	 * The stream tuples and their join partners that are used in the last execution
	 */
	protected final Map<String, Set<String>> containedJoinedKeys;
	
	/**
	 * The names of the join partners for the current key
	 */
	protected final Set<String> joinPartnersForCurrentKey;
	
	public ContinuousQueryExecutionState() {
		this.containedStreamKeys = new HashSet<>();
		this.containedJoinedKeys = new HashMap<>();
		this.joinPartnersForCurrentKey = new HashSet<>();
	}
	
	/**
	 * Was the stream key contained in the last range query
	 * @param key
	 * @return
	 */
	public boolean wasStreamKeyContainedInLastRangeQuery(final String key) {
		return containedStreamKeys.contains(key);
	}
	
	/**
	 * Remove the given key from the state
	 * @param key
	 * @return
	 */
	public boolean removeStreamKeyFromRangeState(final String key) {
		return containedStreamKeys.remove(key);
	}
	
	/**
	 * Add the given key to the state
	 * @param key
	 */
	public void addStreamKeyToState(final String key) {
		containedStreamKeys.add(key);
	}
	
	/**
	 * Add the current key to the list of the join partners
	 * @param key
	 */
	public void addJoinCandidateForCurrentKey(final String key) {
		joinPartnersForCurrentKey.add(key);
	}
	
	/**
	 * Get the missing join partners for the current key and clear the state
	 * @param streamKey
	 * @return 
	 */
	public Set<String> commitStateAndGetMissingJoinpartners(final String streamKey) {
		
		final Set<String> oldJoinPartners = containedJoinedKeys.getOrDefault(streamKey, new HashSet<>());
		
		// Calculate the difference between the current join partners and the previous join partners
		oldJoinPartners.removeAll(joinPartnersForCurrentKey);
		
		final Set<String> seenJoinPartners = new HashSet<>(joinPartnersForCurrentKey);
		containedJoinedKeys.put(streamKey, seenJoinPartners);
		
		clearJoinPartnerState();
				
		return oldJoinPartners;
	}

	/**
	 * Clear the state for the current join partner
	 */
	public void clearJoinPartnerState() {
		joinPartnersForCurrentKey.clear();
	}
	
	/**
	 * Get the contained joined keys
	 * @return
	 */
	public Map<String, Set<String>> getContainedJoinedKeys() {
		return containedJoinedKeys;
	}
	
	/**
	 * Remove the stream key from join state
	 * @param streamKey
	 * @return 
	 */
	public Set<String> removeStreamKeyFromJoinState(final String streamKey) {
		return containedJoinedKeys.remove(streamKey);
	}
	
	/**
	 * Was the stream key contained in the last join query
	 * @param key
	 * @return
	 */
	public boolean wasStreamKeyContainedInLastJoinQuery(final String key) {
		return containedJoinedKeys.containsKey(key);
	}
	
	/**
	 * Get the contained range query keys
	 * @return
	 */
	public Set<String> getContainedTupleKeys() {
		return containedStreamKeys;
	}

	/**
	 * Merge the given state into the local one
	 * @param resultState
	 */
	public void merge(final Set<String> rangeQueryState, final Map<String, Set<String>> joinQueryState) {
		containedStreamKeys.addAll(rangeQueryState);
		containedJoinedKeys.putAll(joinQueryState);
	}
}
