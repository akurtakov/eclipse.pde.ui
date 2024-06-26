/*******************************************************************************
 * Copyright (c) 2008, 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.correction;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.pde.core.IBaseModel;
import org.eclipse.pde.internal.core.ibundle.IBundlePluginModelBase;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.editor.plugin.ManifestEditor;
import org.eclipse.pde.internal.ui.util.SWTUtil;
import org.eclipse.pde.internal.ui.wizards.extension.NewExtensionPointWizard;
import org.eclipse.ui.IEditorPart;

public class AddNewExtensionPointResolution extends AbstractPDEMarkerResolution {

	public AddNewExtensionPointResolution(int type, IMarker marker) {
		super(type, marker);
	}

	@Override
	public String getLabel() {
		return PDEUIMessages.AddNewExtensionPointResolution_description;
	}

	@Override
	protected void createChange(IBaseModel model) {
		IEditorPart part = PDEPlugin.getActivePage().getActiveEditor();
		if (part instanceof ManifestEditor editor) {
			IBaseModel base = editor.getAggregateModel();
			if (base instanceof IBundlePluginModelBase pluginModel) {
				NewExtensionPointWizard wizard = new NewExtensionPointWizard(pluginModel.getUnderlyingResource().getProject(), pluginModel, editor) {
					@Override
					public boolean performFinish() {
						return super.performFinish();
					}
				};
				WizardDialog dialog = new WizardDialog(PDEPlugin.getActiveWorkbenchShell(), wizard);
				dialog.create();
				SWTUtil.setDialogSize(dialog, 400, 450);
				dialog.open();
			}
		}
	}
}