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
package org.bboxdb.network.packages.request;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;

import org.bboxdb.commons.math.Hyperrectangle;
import org.bboxdb.misc.Const;
import org.bboxdb.network.NetworkConst;
import org.bboxdb.network.NetworkPackageDecoder;
import org.bboxdb.network.packages.NetworkQueryRequestPackage;
import org.bboxdb.network.packages.PackageEncodeException;
import org.bboxdb.network.packages.request.helper.RequestEncoderHelper;
import org.bboxdb.network.routing.RoutingHeader;
import org.bboxdb.query.filter.UserDefinedFilterDefinition;
import org.bboxdb.storage.entity.TupleStoreName;

public class QueryHyperrectangleRequest extends NetworkQueryRequestPackage {

	/**
	 * The name of the table
	 */
	private final TupleStoreName table;

	/**
	 * The the query bounding box
	 */
	private final Hyperrectangle box;
	
	/**
	 * Paging enables
	 */
	private final boolean pagingEnabled;
	
	/**
	 * The max tuples per page
	 */
	private final short tuplesPerPage;
	
	/**
	 * The custom filter name
	 */
	final List<UserDefinedFilterDefinition> udfs;

	public QueryHyperrectangleRequest(final short sequenceNumber, final RoutingHeader routingHeader,  
			final String table,  final Hyperrectangle box, final List<UserDefinedFilterDefinition> udfs, 
			final boolean pagingEnabled, final short tuplesPerPage) {
		
		super(sequenceNumber, routingHeader);
		
		this.table = new TupleStoreName(table);
		this.box = box;
		this.pagingEnabled = pagingEnabled;
		this.tuplesPerPage = tuplesPerPage;
		this.udfs = udfs;
	}

	@Override
	public long writeToOutputStream(final OutputStream outputStream) throws PackageEncodeException {

		try {
			final byte[] tableBytes = table.getFullnameBytes();
			final byte[] bboxBytes = box.toByteArray();
			
			final ByteBuffer bb = ByteBuffer.allocate(12);
			bb.order(Const.APPLICATION_BYTE_ORDER);
			
			bb.put(getQueryType());
			
			if(pagingEnabled) {
				bb.put((byte) 1);
			} else {
				bb.put((byte) 0);
			}
			
			bb.putShort(tuplesPerPage);
			
			final byte[] udfsBytes = RequestEncoderHelper.encodeUDFs(udfs);
			
			bb.putShort((short) tableBytes.length);
			bb.put(NetworkConst.UNUSED_BYTE);
			bb.put(NetworkConst.UNUSED_BYTE);
			bb.putInt((int) bboxBytes.length);

			final long bodyLength = bb.capacity() + tableBytes.length + bboxBytes.length 
					+ udfsBytes.length;
			
			final long headerLength = appendRequestPackageHeader(bodyLength, outputStream);

			// Write body
			outputStream.write(bb.array());
			outputStream.write(tableBytes);
			outputStream.write(bboxBytes);
			outputStream.write(udfsBytes);
			
			return headerLength + bodyLength;
		} catch (IOException e) {
			throw new PackageEncodeException("Got exception while converting package into bytes", e);
		}	
	}
	
	/**
	 * Decode the encoded package into a object
	 * 
	 * @param encodedPackage
	 * @return
	 * @throws PackageEncodeException 
	 * @throws IOException 
	 */
	public static QueryHyperrectangleRequest decodeTuple(final ByteBuffer encodedPackage) 
			throws PackageEncodeException, IOException {
		
		final short sequenceNumber = NetworkPackageDecoder.getRequestIDFromRequestPackage(encodedPackage);
		
		final boolean decodeResult = NetworkPackageDecoder.validateRequestPackageHeader(
				encodedPackage, NetworkConst.REQUEST_TYPE_QUERY);
		
		if(decodeResult == false) {
			throw new PackageEncodeException("Unable to decode package");
		}
		
	    final byte queryType = encodedPackage.get();
	    
	    if(queryType != NetworkConst.REQUEST_QUERY_BBOX) {
	    	throw new PackageEncodeException("Wrong query type: " + queryType 
	    			+ " required type is: " + NetworkConst.REQUEST_QUERY_BBOX);
	    }
	    
	    boolean pagingEnabled = false;
	    if(encodedPackage.get() != 0) {
	    	pagingEnabled = true;
	    }
	    
	    final short tuplesPerPage = encodedPackage.getShort();
		final short tableLength = encodedPackage.getShort();
		
	    // 2 unused bytes
	    encodedPackage.get();
	    encodedPackage.get();
		
	    final int bboxLength = encodedPackage.getInt();

		final byte[] tableBytes = new byte[tableLength];
		encodedPackage.get(tableBytes, 0, tableBytes.length);
		final String table = new String(tableBytes);
		
		final byte[] bboxBytes = new byte[bboxLength];
		encodedPackage.get(bboxBytes, 0, bboxBytes.length);
		final Hyperrectangle boundingBox = Hyperrectangle.fromByteArray(bboxBytes);
		
		final List<UserDefinedFilterDefinition> udfs = RequestEncoderHelper.decodeUDFs(encodedPackage);
		
		if(encodedPackage.remaining() != 0) {
			throw new PackageEncodeException("Some bytes are left after decoding: " 
					+ encodedPackage.remaining());
		}
		
		final RoutingHeader routingHeader = NetworkPackageDecoder.getRoutingHeaderFromRequestPackage(encodedPackage);

		return new QueryHyperrectangleRequest(sequenceNumber, routingHeader, table, boundingBox, 
				udfs, pagingEnabled, tuplesPerPage);
	}

	@Override
	public byte getPackageType() {
		return NetworkConst.REQUEST_TYPE_QUERY;
	}

	@Override
	public byte getQueryType() {
		return NetworkConst.REQUEST_QUERY_BBOX;
	}
	
	public TupleStoreName getTable() {
		return table;
	}

	public Hyperrectangle getBoundingBox() {
		return box;
	}
	
	public short getTuplesPerPage() {
		return tuplesPerPage;
	}

	public boolean isPagingEnabled() {
		return pagingEnabled;
	}

	public List<UserDefinedFilterDefinition> getUdfs() {
		return udfs;
	}

	@Override
	public String toString() {
		return "QueryHyperrectangleRequest [table=" + table + ", box=" + box + ", pagingEnabled=" + pagingEnabled
				+ ", tuplesPerPage=" + tuplesPerPage + ", udfs=" + udfs + "]";
	}

}
