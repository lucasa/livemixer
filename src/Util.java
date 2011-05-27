/*******************************************************************************
 * Copyright (c) 2011, Author: Lucas Alberto Souza Santos <lucasa at gmail dot com>.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA. See
 * <http://www.gnu.org/licenses/>.
 *******************************************************************************/

import java.io.IOException;
import java.lang.reflect.Field;

public class Util {
	/**
	 * retrieves the pid
	 */
	public static int getProcessId(Process p) {
		try {
			Field f = p.getClass().getDeclaredField("pid");
			f.setAccessible(true);
			Integer pid = (Integer) f.get(p);
			return pid;
		} catch (Exception e) {
			throw new RuntimeException("Couldn't detect pid", e);
		}
	}

	/**
	 * runs "kill -9" on the specified pid
	 */
	public static void kill9(Integer pid) {
		try {
			ProcessBuilder pb = new ProcessBuilder("kill", "-9", pid.toString());
			pb.redirectErrorStream();
			Process p = pb.start();
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * runs "kill -9" on the specified process
	 */
	public static void kill9(Process p) throws IOException, InterruptedException {
		kill9(getProcessId(p));
	}
}
