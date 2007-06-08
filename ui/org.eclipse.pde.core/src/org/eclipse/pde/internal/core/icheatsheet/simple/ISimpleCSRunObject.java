/*******************************************************************************
 * Copyright (c) 2006, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.pde.internal.core.icheatsheet.simple;

/**
 * ISimpleCSRunObject
 *
 */
public interface ISimpleCSRunObject extends ISimpleCSRunContainerObject {

	/**
	 * Attribute: confirm
	 * @return
	 */
	public boolean getConfirm();
	
	/**
	 * Attribute: confirm
	 * @param confirm
	 */
	public void setConfirm(boolean confirm);
	
	/**
	 * Attribute: when
	 * @return
	 */
	public String getWhen();
	
	/**
	 * Attribute: when
	 * @param when
	 */
	public void setWhen(String when);
	
	/**
	 * Attribute: translate
	 * @return
	 */
	public String getTranslate();
	
	/**
	 * Attribute: translate
	 * @param translate
	 */
	public void setTranslate(String translate);	
	
}
