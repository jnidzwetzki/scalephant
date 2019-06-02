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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.bboxdb.commons.Pair;
import org.bboxdb.tools.gui.GuiModel;
import org.bboxdb.tools.gui.util.MapViewerFactory;
import org.bboxdb.tools.gui.views.View;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;

public class QueryView implements View {

	/**
	 * The GUI model
	 */
	private final GuiModel guiModel;
	
	/**
	 * The map viewer
	 */
	private JXMapViewer mapViewer;
	
	/**
	 * The data to draw
	 */
	private final Collection<Pair<List<Point2D>, Color>> dataToDraw;


	public QueryView(final GuiModel guiModel) {
		this.guiModel = guiModel;
		this.dataToDraw = new CopyOnWriteArrayList<>();
	}
	
	@Override
	public JComponent getJPanel() {
		mapViewer = MapViewerFactory.createMapViewer();
		
        final QueryRangeSelectionAdapter selectionAdapter = new QueryRangeSelectionAdapter(dataToDraw, 
        		guiModel, mapViewer);
        
        mapViewer.addMouseListener(selectionAdapter);
        mapViewer.addMouseMotionListener(selectionAdapter);
		
		final Painter<JXMapViewer> painter = new QueryResultOverlay(dataToDraw, selectionAdapter);
		mapViewer.setOverlayPainter(painter);
		
		final JPanel mainPanel = new JPanel();
		
		mainPanel.setLayout(new BorderLayout());
		mainPanel.add(mapViewer, BorderLayout.CENTER);

		final JPanel buttonPanel = new JPanel();
		mainPanel.add(buttonPanel, BorderLayout.SOUTH);
		
		final JButton zoomInButton = MapViewerFactory.getZoomInButton(mapViewer);
		buttonPanel.add(zoomInButton);

		final JButton zoomOutButton = MapViewerFactory.getZoomOutButton(mapViewer);		
		buttonPanel.add(zoomOutButton);
		
		final JButton showWolrdButton = MapViewerFactory.getShowWorldButton(mapViewer);
		buttonPanel.add(showWolrdButton);
		
		final JButton showHagenButton = MapViewerFactory.getShowHagenButton(mapViewer);
		buttonPanel.add(showHagenButton);
		
		final JButton showBerlinButton = MapViewerFactory.getShowBerlinButton(mapViewer);
		buttonPanel.add(showBerlinButton);
		
		final JButton queryButton = getQueryButton();
		buttonPanel.add(queryButton);
		
		final JButton clearButton = getClearButton();
		buttonPanel.add(clearButton);

		return mainPanel;	
	}

	/**
	 * Get the query button
	 * @return
	 */
	private JButton getQueryButton() {
		final JButton queryButton = new JButton("Execute query");
		queryButton.addActionListener(l -> {
			final QueryWindow queryWindow = new QueryWindow(guiModel, dataToDraw, () -> {
				mapViewer.repaint();
			});

			queryWindow.show();
		});
		return queryButton;
	}

	/**
	 * Get the clear map button
	 * @return
	 */
	private JButton getClearButton() {
		final JButton clearButton = new JButton("Clear map");
		clearButton.addActionListener(l -> {
			dataToDraw.clear();
			mapViewer.repaint();
		});
		return clearButton;
	}

	@Override
	public boolean isGroupSelectionNeeded() {
		return false;
	}
}
