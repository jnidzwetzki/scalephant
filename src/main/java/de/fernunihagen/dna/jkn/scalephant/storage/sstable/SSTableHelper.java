package de.fernunihagen.dna.jkn.scalephant.storage.sstable;

import java.io.File;
import java.nio.ByteBuffer;

import de.fernunihagen.dna.jkn.scalephant.storage.StorageManagerException;

public class SSTableHelper {
	
	/**
	 * Size of a float in bytes
	 */
	public final static int FLOAT_BYTES = Float.SIZE / Byte.SIZE;
	
	/**
	 * Size of a IEEE 754 encoded float in bytes
	 */
	public final static int FLOAT_IEEE754_BYTES = Integer.SIZE / Byte.SIZE;
	
	/**
	 * Size of a long in bytes
	 */
	public final static int LONG_BYTES = Long.SIZE / Byte.SIZE;
	
	/**
	 * Size of a integer in bytes
	 */
	public final static int INT_BYTES = Integer.SIZE / Byte.SIZE;
	
	/**
	 * Size of a short in bytes
	 */
	public final static int SHORT_BYTES = Short.SIZE / Byte.SIZE;
	
	
	/** 
	 * Convert a array of long values into a byte buffer
	 * @param longValues
	 * @return
	 */
	public static ByteBuffer longArrayToByteBuffer(long longValues[]) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(LONG_BYTES * longValues.length);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		
		for(int i = 0; i < longValues.length; i++) {
			byteBuffer.putLong(longValues[i]);
		}
		
		return byteBuffer;
	}
	
	/** 
	 * Convert a array of float values into a byte buffer (in IEEE 754 notation)
	 * @param longValues
	 * @return
	 */
	public static ByteBuffer floatArrayToIEEE754ByteBuffer(float floatValues[]) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(FLOAT_IEEE754_BYTES * floatValues.length);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		
		for(int i = 0; i < floatValues.length; i++) {
			byteBuffer.putInt(Float.floatToIntBits(floatValues[i]));
		}
		
		return byteBuffer;
	}
	
	/** 
	 * Convert a array of float values into a byte buffer (in java notation)
	 * @param longValues
	 * @return
	 */
	public static ByteBuffer floatArrayToByteBuffer(float floatValues[]) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(FLOAT_BYTES * floatValues.length);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		
		for(int i = 0; i < floatValues.length; i++) {
			byteBuffer.putFloat(floatValues[i]);
		}
		
		return byteBuffer;
	}
	
	/**
	 * Encode a long into a byte buffer
	 * 
	 * @param longValue
	 * @return the long value
	 */
	public static ByteBuffer longToByteBuffer(long longValue) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(LONG_BYTES);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		byteBuffer.putLong(longValue);
		return byteBuffer;
	}
	
	/**
	 * Encode an integer into a byte buffer
	 * 
	 * @param intValue
	 * @return the int value
	 */
	public static ByteBuffer intToByteBuffer(int intValue) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(INT_BYTES);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		byteBuffer.putInt(intValue);
		return byteBuffer;		
	}
	
	/**
	 * Encode a short into a byte buffer
	 * @param shortValue
	 * @return the short value
	 */
	public static ByteBuffer shortToByteBuffer(short shortValue) {
		final ByteBuffer byteBuffer = ByteBuffer.allocate(SHORT_BYTES);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		byteBuffer.putShort(shortValue);
		return byteBuffer;		
	}
	
	/**
	 * Decode a long array from a byte buffer
	 * @param buffer
	 * @return the long value
	 */
	public static long[] readLongArrayFromByte(byte[] buffer) {
		final int totalValues = buffer.length / LONG_BYTES;
		long values[] = new long[totalValues];
		final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		
		for(int i = 0; i < totalValues; i++) {
			values[i] = byteBuffer.getLong(i * LONG_BYTES);
		}
		
		return values;
	}
	
	/**
	 * Decode a IEEE 754 encoded float array from a byte buffer
	 * @param buffer
	 * @return the float value
	 */
	public static float[] readIEEE754FloatArrayFromByte(byte[] buffer) {
		final int totalValues = buffer.length / FLOAT_IEEE754_BYTES;
		float values[] = new float[totalValues];
		final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		
		for(int i = 0; i < totalValues; i++) {
			final int value = byteBuffer.getInt(i * FLOAT_IEEE754_BYTES);
			values[i] = Float.intBitsToFloat(value);
		}
		
		return values;
	}
	
	/**
	 * Decode a java encoded float array from a byte buffer
	 * @param buffer
	 * @return the float value
	 */
	public static float[] readFloatArrayFromByte(byte[] buffer) {
		final int totalValues = buffer.length / FLOAT_BYTES;
		float values[] = new float[totalValues];
		final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		
		for(int i = 0; i < totalValues; i++) {
			values[i] = byteBuffer.getFloat(i * FLOAT_BYTES);
		}
		
		return values;
	}
	
	/**
	 * Decode a long from a byte buffer
	 * @param buffer
	 * @return the long value
	 */
	public static long readLongFromByte(byte[] buffer) {
		final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		return byteBuffer.getLong();
	}
	
	/**
	 * Decode an int from a byte buffer
	 * @param buffer
	 * @return the int value
	 */
	public static int readIntFromByte(byte[] buffer) {
		final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		return byteBuffer.getInt();
	}
	
	/**
	 * Decode a short from a byte buffer
	 * @param buffer
	 * @return the short value
	 */
	public static short readShortFromByte(byte[] buffer) {
		final ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
		byteBuffer.order(SSTableConst.SSTABLE_BYTE_ORDER);
		return byteBuffer.getShort();
	}
	
	/**
	 * Extract the sequence Number from a given filename
	 * 
	 * @param Tablename, the name of the Table. Filename, the name of the file
	 * @return the sequence number
	 * @throws StorageManagerException 
	 */
	public static int extractSequenceFromFilename(final String tablename, final String filename)
			throws StorageManagerException {
		try {
			final String sequence = filename
				.replace(SSTableConst.SST_FILE_PREFIX + tablename + "_", "")
				.replace(SSTableConst.SST_FILE_SUFFIX, "")
				.replace(SSTableConst.SST_INDEX_SUFFIX, "");
			
			return Integer.parseInt(sequence);
		
		} catch (NumberFormatException e) {
			String error = "Unable to parse sequence number: " + filename;
			throw new StorageManagerException(error, e);
		}
	}
	
	/**
	 * The full name of the SSTable directoy for a given relation
	 * 
	 * @param directory
	 * @param name
	 * 
	 * @return e.g. /tmp/scalephant/data/relation1 
	 */
	public static String getSSTableDir(final String directory, final String name) {
		return directory 
				+ File.separator 
				+ name;
	}
	
	/**
	 * The base name of the SSTable file for a given relation
	 * 
	 * @param directory
	 * @param name
	 * 
	 * @return e.g. /tmp/scalephant/data/relation1/sstable_relation1_2
	 */
	public static String getSSTableBase(final String directory, final String name, int tablebumber) {
		return getSSTableDir(directory, name)
				+ File.separator 
				+ SSTableConst.SST_FILE_PREFIX 
				+ name 
				+ "_" 
				+ tablebumber;
	}
	
	/**
	 * The full name of the SSTable file for a given relation
	 * 
	 * @param directory
	 * @param name
	 * 
	 * @return e.g. /tmp/scalephant/data/relation1/sstable_relation1_2.sst
	 */
	public static String getSSTableFilename(final String directory, final String name, int tablebumber) {
		return getSSTableBase(directory, name, tablebumber)
				+ SSTableConst.SST_FILE_SUFFIX;
	}
	
	/**
	 * The full name of the SSTable index file for a given relation
	 * 
	 * @param directory
	 * @param name
	 * 
	 * @return e.g. /tmp/scalephant/data/relation1/sstable_relation1_2.idx
	 */
	public static String getSSTableIndexFilename(final String directory, final String name, int tablebumber) {
		return getSSTableBase(directory, name, tablebumber)
				+ SSTableConst.SST_INDEX_SUFFIX;
	}
	
	/**
	 * The full name of the SSTable medatata file for a given relation
	 * 
	 * @param directory
	 * @param name
	 * 
	 * @return e.g. /tmp/scalephant/data/relation1/sstable_relation1_2.meta
	 */
	public static String getSSTableMetadataFilename(final String directory, final String name, int tablebumber) {
		return getSSTableBase(directory, name, tablebumber)
				+ SSTableConst.SST_META_SUFFIX;
	}

}
