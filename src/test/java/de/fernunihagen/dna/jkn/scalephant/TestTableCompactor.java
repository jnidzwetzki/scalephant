package de.fernunihagen.dna.jkn.scalephant;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import de.fernunihagen.dna.jkn.scalephant.storage.BoundingBox;
import de.fernunihagen.dna.jkn.scalephant.storage.StorageConfiguration;
import de.fernunihagen.dna.jkn.scalephant.storage.StorageInterface;
import de.fernunihagen.dna.jkn.scalephant.storage.StorageManager;
import de.fernunihagen.dna.jkn.scalephant.storage.StorageManagerException;
import de.fernunihagen.dna.jkn.scalephant.storage.Tuple;
import de.fernunihagen.dna.jkn.scalephant.storage.sstable.SSTableCompactor;
import de.fernunihagen.dna.jkn.scalephant.storage.sstable.SSTableIndexReader;
import de.fernunihagen.dna.jkn.scalephant.storage.sstable.SSTableReader;
import de.fernunihagen.dna.jkn.scalephant.storage.sstable.SSTableWriter;

public class TestTableCompactor {
	
	protected final static String TEST_RELATION = "1_testgroup1_relation1";

	protected final StorageConfiguration storageConfiguration = new StorageConfiguration();

	@Before
	public void clearData() throws StorageManagerException {
		final StorageManager storageManager = StorageInterface.getStorageManager(TEST_RELATION);
		storageManager.clear();
		storageManager.shutdown();
	}

	@Test
	public void testCompactTestFileCreation() throws StorageManagerException {
		final List<Tuple> tupleList1 = new ArrayList<Tuple>();
		tupleList1.add(new Tuple("1", BoundingBox.EMPTY_BOX, "abc".getBytes()));
		final SSTableIndexReader reader1 = addTuplesToFile(tupleList1, 1);
		
		final List<Tuple> tupleList2 = new ArrayList<Tuple>();
		tupleList2.add(new Tuple("2", BoundingBox.EMPTY_BOX, "def".getBytes()));
		final SSTableIndexReader reader2 = addTuplesToFile(tupleList2, 2);
		
		final SSTableWriter writer = new SSTableWriter(TEST_RELATION, storageConfiguration.getDataDir(), 3);
		
		final SSTableCompactor compactor = new SSTableCompactor(Arrays.asList(reader1, reader2), writer);
		boolean compactResult = compactor.executeCompactation();
		
		Assert.assertTrue(compactResult);
		Assert.assertTrue(writer.getSstableFile().exists());
		Assert.assertTrue(writer.getSstableIndexFile().exists());
		
		writer.close();
	}
	
	@Test
	public void testCompactTestMerge() throws StorageManagerException {
		final List<Tuple> tupleList1 = new ArrayList<Tuple>();
		tupleList1.add(new Tuple("1", BoundingBox.EMPTY_BOX, "abc".getBytes()));
		final SSTableIndexReader reader1 = addTuplesToFile(tupleList1, 1);
		
		final List<Tuple> tupleList2 = new ArrayList<Tuple>();
		tupleList2.add(new Tuple("2", BoundingBox.EMPTY_BOX, "def".getBytes()));
		final SSTableIndexReader reader2 = addTuplesToFile(tupleList2, 2);
		
		final SSTableWriter writer = new SSTableWriter(TEST_RELATION, storageConfiguration.getDataDir(), 3);
		
		final SSTableCompactor compactor = new SSTableCompactor(Arrays.asList(reader1, reader2), writer);
		compactor.executeCompactation();
		writer.close();
		
		final SSTableReader reader = new SSTableReader(TEST_RELATION, storageConfiguration.getDataDir(), 
				writer.getSstableFile());
		reader.init();
		
		final SSTableIndexReader ssTableIndexReader = new SSTableIndexReader(reader);
		ssTableIndexReader.init();
		int counter = 0;
		
		for(@SuppressWarnings("unused") final Tuple tuple : ssTableIndexReader) {
			counter++;
		}
		
		Assert.assertEquals(tupleList1.size() + tupleList2.size(), counter);
	}
	
	@Test
	public void testCompactTestMergeBig() throws StorageManagerException {
		
		SSTableIndexReader reader1 = null;
		SSTableIndexReader reader2 = null;
		final List<Tuple> tupleList = new ArrayList<Tuple>();

		for(int i = 0; i < 500; i=i+2) {
			tupleList.add(new Tuple(Integer.toString(i), BoundingBox.EMPTY_BOX, "abc".getBytes()));
		}
		reader1 = addTuplesToFile(tupleList, 5);

		tupleList.clear();
	
		for(int i = 1; i < 500; i=i+2) {
			tupleList.add(new Tuple(Integer.toString(i), BoundingBox.EMPTY_BOX, "def".getBytes()));
		}
		reader2 = addTuplesToFile(tupleList, 2);

		
		final SSTableWriter writer = new SSTableWriter(TEST_RELATION, storageConfiguration.getDataDir(), 3);
		
		final SSTableCompactor compactor = new SSTableCompactor(Arrays.asList(reader1, reader2), writer);
		compactor.executeCompactation();
		writer.close();
		
		final SSTableReader reader = new SSTableReader(TEST_RELATION, storageConfiguration.getDataDir(), 
				writer.getSstableFile());
		reader.init();
		
		final SSTableIndexReader ssTableIndexReader = new SSTableIndexReader(reader);
		ssTableIndexReader.init();
		
		// Check the amount of tuples
		int counter = 0;
		for(final Tuple tuple : ssTableIndexReader) {
			counter++;
		}
		Assert.assertEquals(500, counter);
		
		// Check the consistency of the index
		for(int i = 1; i < 500; i++) {
			int pos = ssTableIndexReader.getPositionForTuple(Integer.toString(i));
			Assert.assertTrue(pos != -1);
		}
	}
	
	
	@Test
	public void testCompactTestFileOneEmptyfile1() throws StorageManagerException {
		final List<Tuple> tupleList1 = new ArrayList<Tuple>();
		tupleList1.add(new Tuple("1", BoundingBox.EMPTY_BOX, "abc".getBytes()));
		final SSTableIndexReader reader1 = addTuplesToFile(tupleList1, 1);
		
		final List<Tuple> tupleList2 = new ArrayList<Tuple>();
		final SSTableIndexReader reader2 = addTuplesToFile(tupleList2, 2);
		
		final SSTableWriter writer = new SSTableWriter(TEST_RELATION, storageConfiguration.getDataDir(), 3);
		
		final SSTableCompactor compactor = new SSTableCompactor(Arrays.asList(reader1, reader2), writer);
		compactor.executeCompactation();
		writer.close();
		
		final SSTableReader reader = new SSTableReader(TEST_RELATION, storageConfiguration.getDataDir(), 
				writer.getSstableFile());
		reader.init();
		
		final SSTableIndexReader ssTableIndexReader = new SSTableIndexReader(reader);
		ssTableIndexReader.init();
		int counter = 0;
		for(final Tuple tuple : ssTableIndexReader) {
			counter++;
		}
		
		Assert.assertEquals(tupleList1.size() + tupleList2.size(), counter);
	}
	
	@Test
	public void testCompactTestFileOneEmptyfile2() throws StorageManagerException {
		final List<Tuple> tupleList1 = new ArrayList<Tuple>();
		final SSTableIndexReader reader1 = addTuplesToFile(tupleList1, 1);
		
		final List<Tuple> tupleList2 = new ArrayList<Tuple>();
		tupleList2.add(new Tuple("2", BoundingBox.EMPTY_BOX, "def".getBytes()));
		final SSTableIndexReader reader2 = addTuplesToFile(tupleList2, 2);
		
		final SSTableWriter writer = new SSTableWriter(TEST_RELATION, storageConfiguration.getDataDir(), 3);
		
		final SSTableCompactor compactor = new SSTableCompactor(Arrays.asList(reader1, reader2), writer);
		compactor.executeCompactation();
		writer.close();
		
		final SSTableReader reader = new SSTableReader(TEST_RELATION, storageConfiguration.getDataDir(), 
				writer.getSstableFile());
		reader.init();
		
		final SSTableIndexReader ssTableIndexReader = new SSTableIndexReader(reader);
		ssTableIndexReader.init();
		int counter = 0;
		for(final Tuple tuple : ssTableIndexReader) {
			counter++;
		}
		
		Assert.assertEquals(tupleList1.size() + tupleList2.size(), counter);
	}
	
	@Test
	public void testCompactTestSameKey() throws StorageManagerException {
		final List<Tuple> tupleList1 = new ArrayList<Tuple>();
		tupleList1.add(new Tuple("1", BoundingBox.EMPTY_BOX, "abc".getBytes()));
		final SSTableIndexReader reader1 = addTuplesToFile(tupleList1, 1);
		
		final List<Tuple> tupleList2 = new ArrayList<Tuple>();
		tupleList2.add(new Tuple("1", BoundingBox.EMPTY_BOX, "def".getBytes()));
		final SSTableIndexReader reader2 = addTuplesToFile(tupleList2, 2);
		
		final SSTableWriter writer = new SSTableWriter(TEST_RELATION, storageConfiguration.getDataDir(), 3);
		
		final SSTableCompactor compactor = new SSTableCompactor(Arrays.asList(reader1, reader2), writer);
		compactor.executeCompactation();
		writer.close();
		
		final SSTableReader reader = new SSTableReader(TEST_RELATION, storageConfiguration.getDataDir(), 
				writer.getSstableFile());
		reader.init();
		
		final SSTableIndexReader ssTableIndexReader = new SSTableIndexReader(reader);
		ssTableIndexReader.init();
		
		int counter = 0;
		for(final Tuple tuple : ssTableIndexReader) {
			counter++;
			Assert.assertEquals("def", new String(tuple.getDataBytes()));
		}		
				
		Assert.assertEquals(1, counter);
	}
	
	
	protected SSTableIndexReader addTuplesToFile(final List<Tuple> tupleList, int number)
			throws StorageManagerException {

		Collections.sort(tupleList);
		
		final SSTableWriter ssTableWriter = new SSTableWriter(TEST_RELATION, storageConfiguration.getDataDir(), number);
		ssTableWriter.open();
		ssTableWriter.addData(tupleList);
		final File sstableFile = ssTableWriter.getSstableFile();
		ssTableWriter.close();
		
		final SSTableReader sstableReader = new SSTableReader(TEST_RELATION, storageConfiguration.getDataDir(), sstableFile);
		sstableReader.init();
		final SSTableIndexReader ssTableIndexReader = new SSTableIndexReader(sstableReader);
		ssTableIndexReader.init();
		
		return ssTableIndexReader;
	}
	
}
