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

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.JToolTip;

import org.jxmapviewer.JXMapViewer;

public class MouseOverlayHandler extends MouseAdapter {
	
	/**
	 * The map viewer
	 */
	private final JXMapViewer mapViewer;
	
	/**
	 * The overlay painter
	 */
	private Collection<OverlayElement> renderedElements;
	
	/**
	 * The highlighted elements
	 */
	private final List<OverlayElement> highlightedElements = new ArrayList<>();

	/**
	 * The tooltip
	 */
	private final JToolTip toolTip;

	MouseOverlayHandler(final JXMapViewer mapViewer, final JToolTip toolTip) {
		
		this.mapViewer = mapViewer;
		this.toolTip = toolTip;
	}
	
	/**
	 * Update the rendered elements
	 * 
	 * @param renderedElements
	 */
	public void setRenderedElements(final Collection<OverlayElement> renderedElements) {
		this.renderedElements = renderedElements;
	}

	@Override
	public void mouseMoved(final MouseEvent e) {

		if(renderedElements == null) {
			return;
		}
		
		final Rectangle rect = mapViewer.getViewportBounds();
		final Point mousePosPoint = new Point((int) (e.getX() + rect.getX()), 
				(int) (e.getY() + rect.getY()));

		final Rectangle mousePos = new Rectangle(mousePosPoint);
		mousePos.setSize(1, 1);
				
		for(final OverlayElement element : renderedElements) {
			final Rectangle bbox = element.getBBoxToDrawOnGui();
			
			if(bbox.intersects(mousePos)) {
				
				if(element.isHighlighted()) {
					continue;
				}
				
				element.setHighlight(true);
				highlightedElements.add(element);
				repaintElement(rect, bbox);
			}
		}
				
		for (final Iterator<OverlayElement> iterator = highlightedElements.iterator(); iterator.hasNext();) {
			final OverlayElement element = (OverlayElement) iterator.next();
			final Rectangle bbox = element.getBBoxToDrawOnGui();

			if(! bbox.intersects(mousePos)) {
				iterator.remove();
				element.setHighlight(false);
				repaintElement(rect, bbox);
			}
		}
		
		if(! highlightedElements.isEmpty()) {
			final StringBuilder sb = new StringBuilder("<html>");
			
			for(final OverlayElement element : highlightedElements) {
				if(! element.isHighlighted()) {
					continue;
				}
				sb.append("===========================<br>");

				sb.append("<b>Table:</b> " + element.getTablename() + "<br>");
				sb.append("<b>Id: </b> " + element.getPolygon().getId() + "<br>");
				for(Map.Entry<String, String> property : element.getPolygon().getProperties().entrySet()) {
					sb.append("<b>" + property.getKey() + ":</b> " +  property.getValue() + "<br>");
				}
			}
			
			sb.append("</html>");

			toolTip.setTipText(sb.toString());
			toolTip.setLocation(new Point(e.getX(), e.getY()));
			toolTip.setVisible(true);
		} else {
			toolTip.setVisible(false);
		}
	}

	/**
	 * Repaint the given area
	 * @param rect
	 * @param bbox
	 */
	private void repaintElement(final Rectangle rect, final Rectangle bbox) {
		final Rectangle translatedBBox = new Rectangle(bbox);
		translatedBBox.translate((int) -rect.getX(), (int) -rect.getY());
		mapViewer.repaint(translatedBBox);
	}
}