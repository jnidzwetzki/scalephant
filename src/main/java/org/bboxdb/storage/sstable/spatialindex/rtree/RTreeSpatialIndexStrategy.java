/*******************************************************************************
 *
 *    Copyright (C) 2015-2017 the BBoxDB project
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
package org.bboxdb.storage.sstable.spatialindex.rtree;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bboxdb.storage.StorageManagerException;
import org.bboxdb.storage.entity.BoundingBox;
import org.bboxdb.storage.sstable.SSTableConst;
import org.bboxdb.storage.sstable.spatialindex.SpatialIndex;
import org.bboxdb.storage.sstable.spatialindex.SpatialIndexEntry;
import org.bboxdb.util.DataEncoderHelper;

import com.google.common.io.ByteStreams;

public class RTreeSpatialIndexStrategy implements SpatialIndex {

	/**
	 * The node factory
	 */
	protected final RTreeNodeFactory nodeFactory;
	
	/**
	 * The root node of the tree
	 */
	protected RTreeDirectoryNode rootNode;
	
	/**
	 * The max size of a child node
	 */
	protected final int maxNodeSize;
	
	/**
	 * The default max node size
	 */
	public final static int DEFAULT_NODE_SIZE = 32;
	
	public RTreeSpatialIndexStrategy() {
		this(DEFAULT_NODE_SIZE);
	}

	public RTreeSpatialIndexStrategy(final int maxNodeSize) {
		this.maxNodeSize = maxNodeSize;
		this.nodeFactory = new RTreeNodeFactory();
		this.rootNode = nodeFactory.buildDirectoryNode();
	}
	
	/**
	 * Validate the magic bytes of a stream
	 * 
	 * @return a InputStream or null
	 * @throws StorageManagerException
	 * @throws IOException 
	 */
	protected void validateStream(final InputStream inputStream) throws IOException, StorageManagerException {
		
		// Validate file - read the magic from the beginning
		final byte[] magicBytes = new byte[SSTableConst.MAGIC_BYTES_SPATIAL_INDEX.length];
		ByteStreams.readFully(inputStream, magicBytes, 0, SSTableConst.MAGIC_BYTES_SPATIAL_INDEX.length);

		if(! Arrays.equals(magicBytes, SSTableConst.MAGIC_BYTES_SPATIAL_INDEX)) {
			throw new StorageManagerException("Spatial index file does not contain the magic bytes");
		}
	}

	@Override
	public void readFromStream(final InputStream inputStream) throws StorageManagerException {
		
		assert (rootNode.getDirectoryNodeChilds().isEmpty());
		assert (rootNode.getIndexEntries().isEmpty());
		
		try {
			// Validate the magic bytes
			validateStream(inputStream);
			
			final byte[] elementBytes = new byte[DataEncoderHelper.INT_BYTES];
			ByteStreams.readFully(inputStream, elementBytes, 0, elementBytes.length);
			final int elements = DataEncoderHelper.readIntFromByte(elementBytes);
		
			for(int i = 0; i < elements; i++) {
				final RTreeSpatialIndexEntry entry = RTreeSpatialIndexEntry.readFromStream(inputStream);
				insert(entry);
			}
			
		} catch (IOException e) {
			throw new StorageManagerException(e);
		}
	}

	@Override
	public void writeToStream(final OutputStream outputStream) throws StorageManagerException {
		
		try {
			// Write the magic bytes
			outputStream.write(SSTableConst.MAGIC_BYTES_SPATIAL_INDEX);
			
			// Write only index entries.
			// The index structure is reconstructed in memory
			// with a bulk insert.
			final List<RTreeSpatialIndexEntry> entries = rootNode.getEntriesForRegion(BoundingBox.EMPTY_BOX);
			
			final ByteBuffer nodes = DataEncoderHelper.intToByteBuffer(entries.size());
			outputStream.write(nodes.array());
			
			for(final RTreeSpatialIndexEntry entry : entries) {
				entry.writeToStream(outputStream);
			}
			
		} catch (IOException e) {
			throw new StorageManagerException(e);
		}
	}

	@Override
	public List<? extends SpatialIndexEntry> getEntriesForRegion(final BoundingBox boundingBox) {
		return rootNode.getEntriesForRegion(boundingBox);
	}

	@Override
	public boolean bulkInsert(final List<SpatialIndexEntry> elements) {
		boolean result = true;
		
		for(final SpatialIndexEntry entry : elements) {
			final boolean insertResult = insert(entry);
			
			if(! insertResult) {
				result = false;
			}
		}
		
		return result;
	}

	@Override
	public boolean insert(final SpatialIndexEntry entry) {
		final RTreeSpatialIndexEntry treeEntry = nodeFactory.buildRTreeIndex(entry);
		return insert(treeEntry);
	}
	
	/**
	 * Insert the given RTreeSpatialIndexEntry into the tree
	 * @param entry
	 * @return 
	 */
	protected boolean insert(final RTreeSpatialIndexEntry entry) {

		if(entry.getBoundingBox() == null || entry.getBoundingBox() == BoundingBox.EMPTY_BOX) {
			return false;
		}

		final RTreeDirectoryNode insertedNode = rootNode.insertEntryIntoIndex(entry);
		adjustTree(insertedNode);	
		
		return true;
	}

	/**
	 * Adjust the tree, beginning from the argument to the tree root
	 * @param insertedNode
	 */
	protected void adjustTree(final RTreeDirectoryNode insertedNode) {
		
		if(insertedNode == null) {
			return;
		}
		
		RTreeDirectoryNode nodeToCheck = insertedNode;
		
		// Adjust beginning from the bottom
		do {
			
			if(nodeToCheck.getSize() > maxNodeSize) {
				nodeToCheck = splitNode(insertedNode);
			}
			
			nodeToCheck = nodeToCheck.getParentNode();
		} while(nodeToCheck != null);	
	}

	/**
	 * Split the given node
	 * @param nodeToSplit
	 * @return 
	 */
	protected RTreeDirectoryNode splitNode(final RTreeDirectoryNode nodeToSplit) {
		final RTreeDirectoryNode newNode1 = nodeFactory.buildDirectoryNode();
		final RTreeDirectoryNode newNode2 = nodeFactory.buildDirectoryNode();
		RTreeDirectoryNode newParent = null;
		
		// Root node is full
		if(nodeToSplit.getParentNode() == null) {
			rootNode = nodeFactory.buildDirectoryNode();
			newParent = rootNode;
		} else {
			newParent = nodeFactory.buildDirectoryNode();
			nodeToSplit.getParentNode().addDirectoryNodeChild(newParent);
			nodeToSplit.getParentNode().removeDirectoryNodeChild(nodeToSplit);
		}
		
		// Insert new directory node
		newParent.addDirectoryNodeChild(newNode1);
		newParent.addDirectoryNodeChild(newNode2);
		newNode1.setParentNode(newParent);
		newNode2.setParentNode(newParent);
		
		// Find seeds and distribute data
		if(nodeToSplit.isLeafNode()) {
			distributeLeafData(nodeToSplit, newNode1, newNode2);
		} else {
			distributeIndexData(nodeToSplit, newNode1, newNode2);
		}

		// Recalculate the bounding boxes
		newNode1.updateBoundingBox();
		newNode2.updateBoundingBox();
		newParent.updateBoundingBox();
		
		return newNode1;
	}

	/**
	 * Distribute the leaf data
	 * @param nodeToSplit
	 * @param newNode1
	 * @param newNode2
	 */
	protected void distributeIndexData(final RTreeDirectoryNode nodeToSplit, final RTreeDirectoryNode newNode1,
			final RTreeDirectoryNode newNode2) {
		
		final List<RTreeDirectoryNode> dataToDistribute = nodeToSplit.getDirectoryNodeChilds();
		final List<RTreeDirectoryNode> seeds = new ArrayList<>();
		
		final QuadraticSeedPicker<RTreeDirectoryNode> seedPicker = new QuadraticSeedPicker<>();
		seedPicker.quadraticPickSeeds(dataToDistribute, seeds);
		
		newNode1.addDirectoryNodeChild(seeds.get(0));
		newNode2.addDirectoryNodeChild(seeds.get(1));
		
		for(int i = 0; i < dataToDistribute.size(); i++) {
			newNode1.updateBoundingBox();
			newNode2.updateBoundingBox();

			final int remainingObjects = dataToDistribute.size() - i;
			final RTreeDirectoryNode entry = dataToDistribute.get(i);
			
			if(newNode1.getDirectoryNodeChilds().size() + remainingObjects <= maxNodeSize / 2) {
				newNode1.addDirectoryNodeChild(entry);
				continue;
			}
			
			if(newNode2.getDirectoryNodeChilds().size() + remainingObjects <= maxNodeSize / 2) {
				newNode2.addDirectoryNodeChild(entry);
				continue;
			}
			
			final double node1Enlargement = newNode1.getBoundingBox().calculateEnlargement(entry.getBoundingBox());
			final double node2Enlargement = newNode2.getBoundingBox().calculateEnlargement(entry.getBoundingBox());
		
			if(node1Enlargement == node2Enlargement) {
				if(newNode1.getDirectoryNodeChilds().size() < newNode2.getDirectoryNodeChilds().size()) {
					newNode1.addDirectoryNodeChild(entry);
					continue;
				} else {
					newNode2.addDirectoryNodeChild(entry);
					continue;
				}
			}
			
			if(node1Enlargement < node2Enlargement) {
				newNode1.addDirectoryNodeChild(entry);
				continue;
			} else {
				newNode2.addDirectoryNodeChild(entry);
				continue;
			}
		}
	}

	/**
	 * Distribute the index data
	 * @param nodeToSplit
	 * @param newNode1
	 * @param newNode2
	 */
	protected void distributeLeafData(final RTreeDirectoryNode nodeToSplit, final RTreeDirectoryNode newNode1,
			final RTreeDirectoryNode newNode2) {
		
		final List<RTreeSpatialIndexEntry> dataToDistribute = nodeToSplit.getIndexEntries();
		final List<RTreeSpatialIndexEntry> seeds = new ArrayList<>();
		
		final QuadraticSeedPicker<RTreeSpatialIndexEntry> seedPicker = new QuadraticSeedPicker<>();
		seedPicker.quadraticPickSeeds(dataToDistribute, seeds);

		newNode1.insertEntryIntoIndex(seeds.get(0));
		newNode2.insertEntryIntoIndex(seeds.get(1));
		
		for(int i = 0; i < dataToDistribute.size(); i++) {
			newNode1.updateBoundingBox();
			newNode2.updateBoundingBox();

			final int remainingObjects = dataToDistribute.size() - i;
			final RTreeSpatialIndexEntry entry = dataToDistribute.get(i);
			
			if(newNode1.getIndexEntries().size() + remainingObjects <= maxNodeSize / 2) {
				newNode1.insertEntryIntoIndex(entry);
				continue;
			}
			
			if(newNode2.getIndexEntries().size() + remainingObjects <= maxNodeSize / 2) {
				newNode2.insertEntryIntoIndex(entry);
				continue;
			}
			
			final double node1Enlargement = newNode1.getBoundingBox().calculateEnlargement(entry.getBoundingBox());
			final double node2Enlargement = newNode2.getBoundingBox().calculateEnlargement(entry.getBoundingBox());
		
			if(node1Enlargement == node2Enlargement) {
				if(newNode1.getIndexEntries().size() < newNode2.getIndexEntries().size()) {
					newNode1.insertEntryIntoIndex(entry);
					continue;
				} else {
					newNode2.insertEntryIntoIndex(entry);
					continue;
				}
			}
			
			if(node1Enlargement < node2Enlargement) {
				newNode1.insertEntryIntoIndex(entry);
				continue;
			} else {
				newNode2.insertEntryIntoIndex(entry);
				continue;
			}
		}
	}
	
	/**
	 * Get the maximal node size
	 * @return
	 */
	public int getMaxNodeSize() {
		return maxNodeSize;
	}
}
