/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.runtime.registry;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.eclipse.core.internal.registry.ExtensionRegistry;
import org.eclipse.core.runtime.ContributorFactoryOSGi;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionDelta;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IRegistryChangeEvent;
import org.eclipse.core.runtime.IRegistryChangeListener;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.pde.internal.runtime.IHelpContextIds;
import org.eclipse.pde.internal.runtime.PDERuntimeMessages;
import org.eclipse.pde.internal.runtime.PDERuntimePlugin;
import org.eclipse.pde.internal.runtime.PDERuntimePluginImages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.XMLMemento;
import org.eclipse.ui.part.DrillDownAdapter;
import org.eclipse.ui.part.ViewPart;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
public class RegistryBrowser extends ViewPart implements BundleListener, IRegistryChangeListener {
	
	public static final String SHOW_RUNNING_PLUGINS = "RegistryView.showRunning.label"; //$NON-NLS-1$
	
	private TreeViewer treeViewer;
	private IMemento memento;
	
	// menus and action items
	private Action refreshAction;
	private Action showPluginsAction;
	private Action collapseAllAction;
	private Action removeAction;
	private Action addAction;
	private DrillDownAdapter drillDownAdapter;
	
	// single-pane control
	private Composite mainView;
	
	/*
	 * customized DrillDownAdapter which modifies enabled state of showing active/inactive
	 * plug-ins action - see Bug 58467
	 */
	class RegistryDrillDownAdapter extends DrillDownAdapter{
		public RegistryDrillDownAdapter(TreeViewer tree){
			super(tree);
		}

		public void goInto() {
			super.goInto();
			showPluginsAction.setEnabled(!canGoHome());
		}

		public void goBack() {
			super.goBack();
			showPluginsAction.setEnabled(!canGoHome());
		}

		public void goHome() {
			super.goHome();
			showPluginsAction.setEnabled(!canGoHome());
		}

		public void goInto(Object newInput) {
			super.goInto(newInput);
			showPluginsAction.setEnabled(!canGoHome());
		}
	}
	public RegistryBrowser() {
		super();
	}
	
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		if (memento == null)
			this.memento = XMLMemento.createWriteRoot("REGISTRYVIEW"); //$NON-NLS-1$
		else
			this.memento = memento;
		initializeMemento();
	}
	
	private void initializeMemento() {
		// show all plug-ins by default (i.e. not just activated ones)
		if (memento.getString(SHOW_RUNNING_PLUGINS) == null)
			memento.putString(SHOW_RUNNING_PLUGINS, "false"); //$NON-NLS-1$
	}
	
	public void dispose() {
		Platform.getExtensionRegistry().removeRegistryChangeListener(this);
		PDERuntimePlugin.getDefault().getBundleContext().removeBundleListener(
				this);
		super.dispose();
	}
	
	public void createPartControl(Composite parent) {
		// create the sash form that will contain the tree viewer & text viewer
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = 0;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		makeActions();
		createTreeViewer(composite);
		fillToolBar();
		treeViewer.refresh();
		setContentDescription(((RegistryBrowserContentProvider)treeViewer.getContentProvider()).getTitleSummary());
		
		Platform.getExtensionRegistry().addRegistryChangeListener(this);
		PDERuntimePlugin.getDefault().getBundleContext().addBundleListener(this);
	}
	private void createTreeViewer(Composite parent) {
		mainView = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = 0;
		mainView.setLayout(layout);
		mainView.setLayoutData(new GridData(GridData.FILL_BOTH));	
		
		Tree tree = new Tree(mainView, SWT.FLAT | SWT.MULTI);
		GridData gd = new GridData(GridData.FILL_BOTH);
		tree.setLayoutData(gd);
		treeViewer = new TreeViewer(tree);
		boolean showRunning = memento.getString(SHOW_RUNNING_PLUGINS).equals("true") ? true : false; //$NON-NLS-1$
		treeViewer.setContentProvider(new RegistryBrowserContentProvider(treeViewer, showRunning));
		treeViewer.setLabelProvider(new RegistryBrowserLabelProvider(treeViewer));
		treeViewer.setUseHashlookup(true);
		treeViewer.setSorter(new ViewerSorter() {
			public int compare(Viewer viewer, Object e1, Object e2) {
				if (e1 instanceof PluginObjectAdapter)
					e1 = ((PluginObjectAdapter)e1).getObject();
				if (e2 instanceof PluginObjectAdapter)
					e2 = ((PluginObjectAdapter)e2).getObject();
				if (e1 instanceof IBundleFolder && e2 instanceof IBundleFolder)
					return ((IBundleFolder)e1).getFolderId() - ((IBundleFolder)e2).getFolderId();
				if (e1 instanceof Bundle && e2 instanceof Bundle) {
					e1 = ((Bundle)e1).getSymbolicName();
					e2 = ((Bundle)e2).getSymbolicName();
				}
				return super.compare(viewer, e1, e2);
			}
		});
		
		Bundle[] bundles = PDERuntimePlugin.getDefault().getBundleContext().getBundles();
		treeViewer.setInput(new PluginObjectAdapter(bundles));
		
		PlatformUI.getWorkbench().getHelpSystem().setHelp(treeViewer.getControl(),
				IHelpContextIds.REGISTRY_VIEW);
		
		getViewSite().setSelectionProvider(treeViewer);
		
		MenuManager popupMenuManager = new MenuManager();
		IMenuListener listener = new IMenuListener() {
			public void menuAboutToShow(IMenuManager mng) {
				fillContextMenu(mng);
			}
		};
		popupMenuManager.setRemoveAllWhenShown(true);
		popupMenuManager.addMenuListener(listener);
		Menu menu = popupMenuManager.createContextMenu(tree);
		tree.setMenu(menu);
	}
		
	private void fillToolBar(){
		drillDownAdapter = new RegistryDrillDownAdapter(treeViewer);
		IActionBars bars = getViewSite().getActionBars();
		IToolBarManager mng = bars.getToolBarManager();
		drillDownAdapter.addNavigationActions(mng);
		mng.add(refreshAction);
		mng.add(new Separator());
		mng.add(collapseAllAction);
		IMenuManager mgr = bars.getMenuManager();
		mgr.add(new Separator());
		mgr.add(showPluginsAction);
	}
	public void fillContextMenu(IMenuManager manager) {
		manager.add(refreshAction);
		Tree tree = getUndisposedTree();
		if (tree != null && false) { // testing purposes only
			TreeItem[] selection = tree.getSelection();
			boolean allRemoveable = true;
			boolean canAdd = true;
			for (int i = 0; i < selection.length; i++) {
				Object obj = selection[i].getData();
				if (obj instanceof PluginObjectAdapter)
					obj = ((PluginObjectAdapter)obj).getObject();
				if (!(obj instanceof Bundle) || ((Bundle)obj).getState() < Bundle.RESOLVED)
					canAdd = false;
				if (!(obj instanceof IExtensionPoint) && !(obj instanceof IExtension)) {
					allRemoveable = false;
					break;
				}
			}
			if (allRemoveable)
				manager.add(removeAction);
			if (canAdd && selection.length == 1)
				manager.add(addAction);
		}
		manager.add(new Separator());
		drillDownAdapter.addNavigationActions(manager);
		manager.add(new Separator());
		manager.add(showPluginsAction);
	}
	public TreeViewer getTreeViewer() {
		return treeViewer;
	}
	
	public void saveState(IMemento memento) {
		if (memento == null || this.memento == null || treeViewer == null)
			return;
		boolean showRunning = ((RegistryBrowserContentProvider) treeViewer
				.getContentProvider()).isShowRunning();
		if (showRunning)
			this.memento.putString(SHOW_RUNNING_PLUGINS, Boolean.toString(true));
		else
			this.memento.putString(SHOW_RUNNING_PLUGINS, Boolean.toString(false));
		memento.putMemento(this.memento);
	}
	
	public void setFocus() {
		treeViewer.getTree().setFocus();
	}
	
	/*
	 * @see org.osgi.framework.BundleListener#bundleChanged(org.osgi.framework.BundleEvent)
	 */
	public void bundleChanged(BundleEvent event) {
		Tree tree = getUndisposedTree();
		if (tree == null)
			return;
		
		final RegistryBrowserContentProvider provider = ((RegistryBrowserContentProvider) treeViewer.getContentProvider());
		final Bundle eventBundle = Platform.getBundle(event.getBundle().getSymbolicName());
		if (eventBundle == null)
			return;
		final PluginObjectAdapter adapter = new PluginObjectAdapter(eventBundle);
		tree.getDisplay().asyncExec(new Runnable() {
			public void run() {
				Tree tree = getUndisposedTree();
				if (tree == null)
					return;
				TreeItem[] items = treeViewer.getTree().getItems();
				if (items != null) {
					for (int i = 0; i < items.length; i++) {
						Object object = items[i].getData();
						if (object instanceof PluginObjectAdapter)
							object = ((PluginObjectAdapter)object).getObject(); 
						if (object != null && object instanceof Bundle) {
							Bundle bundle = (Bundle) object;
							if (bundle.equals(eventBundle)) {
								treeViewer.update(items[i].getData(), null);
								updateTitle();
								return;
							}
						}
					}
				}	
				if (provider.isShowRunning() && eventBundle.getState() != Bundle.ACTIVE)
					return;
				treeViewer.add(treeViewer.getInput(), adapter);
				updateTitle();
			}
		});
	}
	
	/*
	 * @see org.eclipse.core.runtime.IRegistryChangeListener#registryChanged(org.eclipse.core.runtime.IRegistryChangeEvent)
	 */
	public void registryChanged(IRegistryChangeEvent event) {
		Tree tree = getUndisposedTree();
		if (tree == null)
			return;
		final IExtensionDelta[] deltas = event.getExtensionDeltas();
		tree.getDisplay().syncExec(new Runnable() {
			public void run() {
				if (getUndisposedTree() == null)
					return;
				for (int i = 0; i < deltas.length; i++)
					handleDelta(deltas[i]);
			}
		});
	}
	
	private void handleDelta(IExtensionDelta delta) {
		IExtension ext = delta.getExtension();
		IExtensionPoint extPoint = delta.getExtensionPoint();
		if (delta.getKind() == IExtensionDelta.ADDED) {
			addToTree(ext);
			addToTree(extPoint);
		} else if (delta.getKind() == IExtensionDelta.REMOVED) {
			removeFromTree(ext);
			removeFromTree(extPoint);
		}
	}
	
	private void addToTree(Object object) {
		String namespace = getNamespaceIdentifier(object);
		if (namespace == null)
			return;
		TreeItem[] bundles = treeViewer.getTree().getItems();
		for (int i = 0; i < bundles.length; i++) {
			Object data = bundles[i].getData();
			Object adapted = null;
			if (data instanceof PluginObjectAdapter)
				adapted = ((PluginObjectAdapter)data).getObject();
			if (adapted instanceof Bundle && ((Bundle)adapted).getSymbolicName().equals(namespace)) {
				// TODO fix this method
				if (true) {
					treeViewer.refresh(data);
					return;
				}
				TreeItem[] folders = bundles[i].getItems();
				for (int j = 0; j < folders.length; j++) {
					IBundleFolder folder = (IBundleFolder)folders[j].getData();
					if (correctFolder(folder, object)) {
						treeViewer.add(folder, object);
						return;
					}
				}
				// folder not found - 1st extension - refresh bundle item
				treeViewer.refresh(data);
			}
		}
	}
	
	private String getNamespaceIdentifier(Object object) {
		if (object instanceof IExtensionPoint)
			return ((IExtensionPoint)object).getNamespaceIdentifier();
		if (object instanceof IExtension)
			return ((IExtension)object).getContributor().getName();
		return null;
	}
	
	private boolean correctFolder(IBundleFolder folder, Object child) {
		if (folder == null)
			return false;
		if (child instanceof IExtensionPoint)
			return folder.getFolderId() == IBundleFolder.F_EXTENSION_POINTS;
		if (child instanceof IExtension)
			return folder.getFolderId() == IBundleFolder.F_EXTENSIONS;
		return false;
	}
	
	private void removeFromTree(Object object) {
		String namespace = getNamespaceIdentifier(object);
		if (namespace == null)
			return;
		TreeItem[] bundles = treeViewer.getTree().getItems();
		for (int i = 0; i < bundles.length; i++) {
			Object data = bundles[i].getData();
			Object adapted = null;
			if (data instanceof PluginObjectAdapter)
				adapted = ((PluginObjectAdapter)data).getObject();
			if (adapted instanceof Bundle && ((Bundle)adapted).getSymbolicName().equals(namespace)) {
				TreeItem[] folders = bundles[i].getItems();
				// TODO fix this method
				if (true) {
					treeViewer.refresh(data);
					return;
				}
				for (int j = 0; j < folders.length; j++) {
					IBundleFolder folder = (IBundleFolder)folders[j].getData();
					if (correctFolder(folder, object)) {
						treeViewer.remove(object);
						return;
					}
				}
				// folder not found - 1st extension - refresh bundle item
				treeViewer.refresh(data);
			}
		}
	}
	
	/*
	 * toolbar and context menu actions
	 */
	public void makeActions() {
		refreshAction = new Action("refresh") { //$NON-NLS-1$
			public void run() {
				BusyIndicator.showWhile(treeViewer.getTree().getDisplay(),
						new Runnable() {
					public void run() {
						treeViewer.refresh();
					}
				});
			}
		};
		refreshAction.setText(PDERuntimeMessages.RegistryView_refresh_label);
		refreshAction.setToolTipText(PDERuntimeMessages.RegistryView_refresh_tooltip);
		refreshAction.setImageDescriptor(PDERuntimePluginImages.DESC_REFRESH);
		refreshAction.setDisabledImageDescriptor(PDERuntimePluginImages.DESC_REFRESH_DISABLED);
		
		showPluginsAction = new Action(PDERuntimeMessages.RegistryView_showRunning_label){
			public void run() {
				RegistryBrowserContentProvider cp = (RegistryBrowserContentProvider) treeViewer.getContentProvider();
				cp.setShowRunning(showPluginsAction.isChecked());
				treeViewer.refresh();
				updateTitle();
			}
		};
		showPluginsAction.setChecked(memento.getString(SHOW_RUNNING_PLUGINS).equals("true")); //$NON-NLS-1$
		
		removeAction = new Action("Remove") {
			public void run() {
				Tree tree = getUndisposedTree();
				if (tree == null)
					return;
				IExtensionRegistry registry = Platform.getExtensionRegistry();
				Object token = ((ExtensionRegistry)registry).getTemporaryUserToken();
				TreeItem[] selection = tree.getSelection();
				for (int i = 0; i < selection.length; i++) {
					Object obj = selection[i].getData();
					if (obj instanceof ParentAdapter)
						obj = ((ParentAdapter)obj).getObject();
					if (obj instanceof IExtensionPoint)
						registry.removeExtensionPoint((IExtensionPoint)obj, token);
					else if (obj instanceof IExtension)
						registry.removeExtension((IExtension)obj, token);
				}
				
			}
		};
		
		addAction = new Action("Add...") {
			public void run() {
				Tree tree = getUndisposedTree();
				if (tree == null)
					return;
				FileDialog dialog = new FileDialog(getSite().getShell(), SWT.OPEN);
				String input = dialog.open();
				if (input == null)
					return;
				Object selection = tree.getSelection()[0].getData();
				if (selection instanceof PluginObjectAdapter)
					selection = ((PluginObjectAdapter)selection).getObject();
				if (!(selection instanceof Bundle))
					return;
				IContributor contributor = ContributorFactoryOSGi.createContributor((Bundle)selection);
				IExtensionRegistry registry = Platform.getExtensionRegistry();
				Object token = ((ExtensionRegistry)registry).getTemporaryUserToken();
				try {
					registry.addContribution(new FileInputStream(input), contributor, false, null, null, token);
				} catch (FileNotFoundException e) {
				}
			}
		};
		
		collapseAllAction = new Action("collapseAll"){ //$NON-NLS-1$
			public void run(){
				treeViewer.collapseAll();
			}
		};
		collapseAllAction.setText(PDERuntimeMessages.RegistryView_collapseAll_label);
		collapseAllAction.setImageDescriptor(PDERuntimePluginImages.DESC_COLLAPSE_ALL);
		collapseAllAction.setToolTipText(PDERuntimeMessages.RegistryView_collapseAll_tooltip);
	}
	
	public void updateTitle(){
		if (treeViewer == null || treeViewer.getContentProvider() == null)
			return;
		setContentDescription(((RegistryBrowserContentProvider)treeViewer.getContentProvider()).getTitleSummary());
	}
	
	private Tree getUndisposedTree() {
		if (treeViewer == null || treeViewer.getTree() == null || treeViewer.getTree().isDisposed())
			return null;
		return treeViewer.getTree();
	}
}
