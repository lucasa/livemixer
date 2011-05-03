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

public interface IManager {

	public boolean createPipeline(String name, Object[] inUrls, String outUrl);

	public void destroyPipeline(String name);

	public boolean destroyPipeline(int id);

	public boolean setVolume(String name, String inUrl, float volume);

	public boolean setVolume(int id, int i, float volume);

	public Object[] listPipelines();

	public void addInput(int id, String url);

	public boolean removeInput(int id, int i);

}