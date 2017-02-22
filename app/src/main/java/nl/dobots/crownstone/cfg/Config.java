package nl.dobots.crownstone.cfg;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 7-12-15
 *
 * @author Dominik Egger
 */
public class Config {

	public static final boolean DEBUG = true;

	public static final int MAX_DISPLAY_RESULTS = 50;

	public static final long MIN_FREE_SPACE = 10 * 1024 * 1024; // 10 MB
	public static final long MAX_BACKUP_FILE_SIZE = 500 * 1024 * 1024; // 500 MB

	public static final boolean OFFLINE = false;

}
