/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.correction;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.internal.ui.PDEUIMessages;

public class RemoveBuildOrderEntries extends AbstractPDEMarkerResolution {

	public RemoveBuildOrderEntries(int type) {
		super(type);
	}

	public String getDescription() {
		return PDEUIMessages.RemoveBuildOrderEntries_desc;
	}

	public String getLabel() {
		return PDEUIMessages.RemoveBuildOrderEntries_label;
	}

	public void run(IMarker marker) {
		try {
			IProject project = marker.getResource().getProject();
			if (project == null) return;
			IProjectDescription projDesc = project.getDescription();
			if (projDesc == null) return;
			projDesc.setReferencedProjects(new IProject[0]);
			project.setDescription(projDesc, null);
		} catch (CoreException e) {
		}
	}
}