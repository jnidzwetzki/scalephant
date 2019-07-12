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
package org.bboxdb.tools.gui.views.query;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.bboxdb.commons.math.GeoJsonPolygon;
import org.bboxdb.commons.math.Hyperrectangle;
import org.bboxdb.storage.entity.JoinedTuple;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.GeoPosition;

public class OverlayElement {
	
	/**
	 * The tablename where the element is read from
	 */
	private final String tablename;
	
	/**
	 * The polygon to draw
	 */
	private final GeoJsonPolygon polygon;
	
	/**
	 * The reference to the query tuple
	 */
	private final JoinedTuple joinedTuple;
	
	/**
	 * The color to draw
	 */
	private final Color color;
	
	/**
	 * The pixel polygon points
	 */
	private List<Point2D> polygonPointsPixel;
	
	/**
	 * The pixel bounding box points
	 */
	private final Rectangle boundingBoxPixel;
	
	/**
	 * Highlight the element
	 */
	private boolean highlight;

	public OverlayElement(final String tablename, final JoinedTuple joinedTuple, 
			final GeoJsonPolygon polygon, final Color color) {
		
		this.tablename = tablename;
		this.joinedTuple = joinedTuple;
		this.polygon = polygon;
		this.color = color;
		this.boundingBoxPixel = new Rectangle();
		this.highlight = false;
	}

	/**
	 * Get the polygon to draw
	 * @return
	 */
	public GeoJsonPolygon getPolygon() {
		return polygon;
	}

	/**
	 * Get the color to use
	 * @return
	 */
	public Color getColor() {
		return color;
	}
	
	/** 
	 * Update the position based on the map position
	 * @param map
	 */
	public void updatePosition(final JXMapViewer map) {
		polygonPointsPixel = convertPointCoordinatesToGUICoordinates(map, polygon.getPointList());
		
		final Hyperrectangle bbox = polygon.getBoundingBox();
		final Point2D startPos = new Point2D.Double (bbox.getCoordinateLow(0), bbox.getCoordinateLow(1));
		final Point2D stopPos = new Point2D.Double (bbox.getCoordinateHigh(0), bbox.getCoordinateHigh(1));
		
		final Point2D bboxPixelStart = convertPointToPixel(map, startPos);
		final Point2D bboxPixelStop = convertPointToPixel(map, stopPos);

		final int width = (int) (bboxPixelStop.getX() - bboxPixelStart.getX() + 0.5);
		final int elementWidth = Math.abs(width) + 1;
		
		final int height = (int) (bboxPixelStop.getY() - bboxPixelStart.getY() + 0.5);
		final int elementHeight = Math.abs(height) + 1;
		
		boundingBoxPixel.setBounds((int) (bboxPixelStart.getX() - 0.5), (int) (bboxPixelStop.getY() - 0.5), 
				elementWidth, elementHeight);
	}
	
	/**
	 * Get the points to draw on the GUI
	 * @param map
	 * @return
	 */
	public List<Point2D> getPointsToDrawOnGui() {
		return polygonPointsPixel;
	}
	
	/**
	 * Get the bounding box points to draw on the GUI
	 * @param map
	 * @return
	 */
	public Rectangle getBBoxToDrawOnGui() {
		return boundingBoxPixel;
	}
	
	/**
	 * Convert a list with coordinate points to gui points
	 * @param map
	 * @param polygonPoints
	 * @return
	 */
	private List<Point2D> convertPointCoordinatesToGUICoordinates(final JXMapViewer map,
			final List<Point2D> polygonPoints) {
		
		final List<Point2D> elementPoints = new ArrayList<>();

		for(final Point2D point : polygonPoints) {
			final Point2D convertedPoint = convertPointToPixel(map, point);
			elementPoints.add(convertedPoint);
		}
		
		return elementPoints;
	}

	/**
	 * Convert a given point to pixel pos
	 * @param map
	 * @param point
	 * @return
	 */
	private Point2D convertPointToPixel(final JXMapViewer map, final Point2D point) {
		final GeoPosition geoPosition = new GeoPosition(point.getX(), point.getY());
		return map.getTileFactory().geoToPixel(geoPosition, map.getZoom());
	}

	/**
	 * Is the element highlighted?
	 * @return
	 */
	public boolean isHighlighted() {
		return highlight;
	}

	/**
	 * Set the element as highlighted
	 * @param highlight
	 */
	public void setHighlight(final boolean highlight) {
		this.highlight = highlight;
	}
	
	/**
	 * Get the tablename
	 * @return
	 */
	public String getTablename() {
		return tablename;
	}
	
	/**
	 * Get the joined tuple reference
	 * @return
	 */
	public JoinedTuple getJoinedTuple() {
		return joinedTuple;
	}
}