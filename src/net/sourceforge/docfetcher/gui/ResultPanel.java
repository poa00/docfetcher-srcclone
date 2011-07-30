/*******************************************************************************
 * Copyright (c) 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.gui;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import net.sourceforge.docfetcher.base.Event;
import net.sourceforge.docfetcher.base.Util;
import net.sourceforge.docfetcher.base.annotations.NotNull;
import net.sourceforge.docfetcher.base.annotations.Nullable;
import net.sourceforge.docfetcher.base.gui.FileIconCache;
import net.sourceforge.docfetcher.base.gui.VirtualTableViewer;
import net.sourceforge.docfetcher.base.gui.VirtualTableViewer.Column;
import net.sourceforge.docfetcher.enums.Img;
import net.sourceforge.docfetcher.enums.SettingsConf;
import net.sourceforge.docfetcher.model.search.ResultDocument;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;

/**
 * @author Tran Nam Quang
 */
public final class ResultPanel {
	
	// TODO SWT bug: setting an image, then setting the image to null leaves an indent
	// TODO hide second sender column when there are only emails?
	// TODO show an additional icon if an email has attachments
	// TODO show some helpful overlay message if a search yielded no results
	// TODO implement page navigation
	
	public enum HeaderMode {
		FILES { protected void setLabel(VariableHeaderColumn<?> column) {
			column.setLabel(column.fileHeader);
		} },
		EMAILS { protected void setLabel(VariableHeaderColumn<?> column) {
			column.setLabel(column.emailHeader);
		} },
		FILES_AND_EMAILS { protected void setLabel(VariableHeaderColumn<?> column) {
			column.setLabel(column.combinedHeader);
		} },
		;
		
		protected abstract void setLabel(@NotNull VariableHeaderColumn<?> column);
		
		@NotNull
		public static HeaderMode getInstance(boolean filesFound, boolean emailsFound) {
			final HeaderMode mode;
			if (filesFound)
				mode = emailsFound ? HeaderMode.FILES_AND_EMAILS : HeaderMode.FILES;
			else
				mode = HeaderMode.EMAILS;
			return mode;
		}
	}
	
	private static final DateFormat dateFormat = new SimpleDateFormat();
	
	public final Event<List<ResultDocument>> evtSelection = new Event<List<ResultDocument>> ();
	
	private final VirtualTableViewer<ResultDocument> viewer;
	private final FileIconCache iconCache;
	private HeaderMode presetHeaderMode = HeaderMode.FILES; // externally suggested header mode
	private HeaderMode actualHeaderMode = HeaderMode.FILES; // header mode after examining each visible element

	public ResultPanel(@NotNull Composite parent) {
		iconCache = new FileIconCache(parent);
		
		int treeStyle = SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER;
		viewer = new VirtualTableViewer<ResultDocument> (parent, treeStyle) {
			@SuppressWarnings("unchecked")
			protected List<ResultDocument> getElements(Object rootElement) {
				return (List<ResultDocument>) rootElement;
			}
		};
		
		viewer.getControl().setHeaderVisible(true);
		
		viewer.getControl().addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				evtSelection.fire(viewer.getSelection());
			}
		});
		
		viewer.addColumn(new VariableHeaderColumn<ResultDocument>("Title", "Subject") { // TODO i18n
			protected String getLabel(ResultDocument element) {
				return element.getTitle();
			}
			protected Image getImage(ResultDocument element) {
				if (element.isEmail())
					return Img.EMAIL.get();
				return iconCache.getIcon(element.getFilename(), Img.FILE.get());
			}
		});
		
		viewer.addColumn(new Column<ResultDocument>("Score [%]", SWT.RIGHT) { // TODO i18n
			protected String getLabel(ResultDocument element) {
				return String.valueOf(element.getScore());
			}
		});
		
		viewer.addColumn(new Column<ResultDocument>("Size", SWT.RIGHT) { // TODO i18n
			protected String getLabel(ResultDocument element) {
				return String.valueOf(element.getSizeInKB()) + " KB";
			}
		});

		viewer.addColumn(new VariableHeaderColumn<ResultDocument>("Filename", "Sender") { // TODO i18n
			protected String getLabel(ResultDocument element) {
				if (element.isEmail())
					return element.getSender();
				return element.getFilename();
			}
			protected Image getImage(ResultDocument element) {
				return getEmailIconOrNull(element);
			}
		});

		viewer.addColumn(new Column<ResultDocument>("Type") { // TODO i18n
			protected String getLabel(ResultDocument element) {
				return element.getType();
			}
		});
		
		viewer.addColumn(new Column<ResultDocument>("Path") { // TODO i18n
			protected String getLabel(ResultDocument element) {
				return element.getPath();
			}
		});
		
		viewer.addColumn(new VariableHeaderColumn<ResultDocument>("Authors", "Sender") { // TODO i18n
			protected String getLabel(ResultDocument element) {
				return element.getAuthors();
			}
			protected Image getImage(ResultDocument element) {
				return getEmailIconOrNull(element);
			}
		});
		
		viewer.addColumn(new VariableHeaderColumn<ResultDocument>("Last Modified", "Send Date") { // TODO i18n
			protected String getLabel(ResultDocument element) {
				Date date;
				if (element.isEmail())
					date = element.getDate();
				else
					date = element.getLastModified();
				return dateFormat.format(date);
			}
			protected Image getImage(ResultDocument element) {
				return getEmailIconOrNull(element);
			}
		});
		
		SettingsConf.ColumnWidths.ResultPanel.bind(viewer.getControl());
		
		// TODO Adjust column headers:
		// - Title/Subject
		// - Filename/Sender
		// - Last-Modified/Sent Date
		
		// TODO make column headers movable and clickable
		
		// TODO check if navigating to previous/next page clears
		// the vertical scrolling, but not the horizontal scrolling
	}
	
	@NotNull
	public Table getControl() {
		return viewer.getControl();
	}
	
	// header mode: auto-detect for "files + emails", no auto-detect for files and emails mode
	public void setResults(	@NotNull List<ResultDocument> results,
							@NotNull HeaderMode headerMode) {
		// TODO
		Util.checkNotNull(results, headerMode);
		
		if (this.presetHeaderMode != headerMode) {
			if (headerMode != HeaderMode.FILES_AND_EMAILS)
				updateColumnHeaders(headerMode);
			this.presetHeaderMode = headerMode;
		}
		setActualHeaderMode(results); // TODO needs some refactoring
		
		viewer.setRoot(results);
	}
	
	private void setActualHeaderMode(List<ResultDocument> elements) {
		if (presetHeaderMode != HeaderMode.FILES_AND_EMAILS) {
			actualHeaderMode = presetHeaderMode;
			return;
		}
		boolean filesFound = false;
		boolean emailsFound = false;
		for (ResultDocument element : elements) {
			if (element.isEmail())
				emailsFound = true;
			else
				filesFound = true;
		}
		actualHeaderMode = HeaderMode.getInstance(filesFound, emailsFound);
		updateColumnHeaders(actualHeaderMode);
	}

	private void updateColumnHeaders(HeaderMode headerMode) {
		for (Column<ResultDocument> column : viewer.getColumns()) {
			if (! (column instanceof VariableHeaderColumn)) continue;
			headerMode.setLabel((VariableHeaderColumn<?>) column);
		}
	}
	
	@Nullable
	private Image getEmailIconOrNull(@NotNull ResultDocument element) {
		if (actualHeaderMode != HeaderMode.FILES_AND_EMAILS) return null;
		return element.isEmail() ? Img.EMAIL.get() : null;
	}
	
	private static abstract class VariableHeaderColumn<T> extends Column<T> {
		private final String fileHeader;
		private final String emailHeader;
		private final String combinedHeader;
		
		public VariableHeaderColumn(@NotNull String fileHeader,
									@NotNull String emailHeader) {
			super(fileHeader);
			Util.checkNotNull(fileHeader, emailHeader);
			this.fileHeader = fileHeader;
			this.emailHeader = emailHeader;
			combinedHeader = fileHeader + " / " + emailHeader;
		}
	}

}