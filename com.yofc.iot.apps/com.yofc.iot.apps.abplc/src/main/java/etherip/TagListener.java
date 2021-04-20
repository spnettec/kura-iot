/*******************************************************************************
 * Copyright (c) 2012 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package etherip;

/**
 * Listener to {@link Tag}
 *
 * @author Kay Kasemir
 */
public interface TagListener {
  /**
   * Tag was read
   *
   * <p>Current value of the tag should reflect value on the device.
   *
   * @param tag Tag
   */
  void tagUpdate(Tag tag);

  /**
   * Tag could not be read or written.
   *
   * @param tag Tag
   */
  void tagError(Tag tag);
}
