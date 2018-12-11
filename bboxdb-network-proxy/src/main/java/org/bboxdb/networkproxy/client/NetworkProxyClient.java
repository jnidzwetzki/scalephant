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
package org.bboxdb.networkproxy.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.bboxdb.commons.CloseableHelper;
import org.bboxdb.commons.math.Hyperrectangle;
import org.bboxdb.storage.entity.Tuple;

public class NetworkProxyClient implements AutoCloseable {
	
	/**
	 * The client socket
	 */
	private final Socket clientSocket;
	
	/**
	 * The socket reader
	 */
	private final BufferedReader socketReader;
	
	/**
	 * The socket writer
	 */
	private final Writer socketWriter;

	public NetworkProxyClient(final String hostname, final int port) 
			throws UnknownHostException, IOException {
		
		this.clientSocket = new Socket(hostname, port);
		this.socketReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		this.socketWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
	}

	@Override
	public void close() throws IOException {
		CloseableHelper.closeWithoutException(socketReader);
		CloseableHelper.closeWithoutException(socketWriter);
		CloseableHelper.closeWithoutException(clientSocket);
	}
	
	/**
	 * Disconnect from server
	 * @throws IOException 
	 */
	public void disconnect() throws IOException {
		sendToServer("CLOSE");
	}

	/**
	 * Send data to server
	 * @throws IOException 
	 */
	private synchronized void sendToServer(final String string) throws IOException {
		socketWriter.write(string);
		socketWriter.flush();
	}
	
	/**
	 * The get call
	 * @param key
	 * @param table
	 * @return
	 * @throws IOException 
	 */
	public List<Tuple> get(final String key, final String table) throws IOException {
		sendToServer("GET " + table + " " + key);
		
		return new ArrayList<>();
	}
	
	/**
	 * The get local call
	 * @param key
	 * @param table
	 * @return
	 * @throws IOException 
	 */
	public List<Tuple> getLocal(final String key, final String table) throws IOException {
		sendToServer("GET_LOCAL " + table + " " + key);
		
		return new ArrayList<>();
	}
	
	/**
	 * The put call
	 * @param tuple
	 * @param table
	 * @throws IOException 
	 */
	public void put(final Tuple tuple, final String table) throws IOException {
		final StringBuilder sb = new StringBuilder("PUT ");
		sb.append(table);
		sb.append(" ");
		sb.append(tupleToProxyString(tuple));

		sendToServer(sb.toString());
	}

	/**
	 * Convert the tuple into a string
	 * @param tuple
	 * @param sb
	 * @return
	 */
	private String tupleToProxyString(final Tuple tuple) {
		final StringBuilder sb = new StringBuilder();
		
		sb.append(tuple.getKey().length());
		sb.append(" ");
		sb.append(tuple.getKey());
		sb.append(" ");
		sb.append(tuple.getBoundingBox().toCompactString());
		sb.append(" ");
		sb.append(tuple.getDataBytes().length);
		sb.append(" ");
		sb.append(tuple.getDataBytes());
		sb.append(" ");
		sb.append(tuple.getVersionTimestamp());
		
		return sb.toString();
	}
	
	/**
	 * The range query call
	 * @param queryRectangle
	 * @param table
	 * @return
	 * @throws IOException 
	 */
	public List<Tuple> rangeQuery(final Hyperrectangle queryRectangle, final String table) 
			throws IOException {
		
		sendToServer("GET_RANGE " + table + " " + queryRectangle.toCompactString());
		
		return new ArrayList<>();
	}
 }
