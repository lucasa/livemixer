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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.RequestProcessorFactoryFactory;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;
import org.apache.xmlrpc.webserver.WebServer;
import org.gstreamer.Gst;

import sun.misc.RequestProcessor;

class LiveMixerFactoryFactory extends RequestProcessor implements RequestProcessorFactoryFactory {

	private final RequestProcessorFactory factory = new LiveMixerFactory();
	private final IManager manager;

	public LiveMixerFactoryFactory(IManager liveMixerManager) {
		this.manager = liveMixerManager;
	}

	@Override
	public RequestProcessorFactory getRequestProcessorFactory(Class arg0) throws XmlRpcException {
		return factory;
	}

	private class LiveMixerFactory implements RequestProcessorFactory {
		@Override
		public Object getRequestProcessor(XmlRpcRequest arg0) throws XmlRpcException {
			return manager;
		}
	}
}

public class LiveMixerManager implements IManager {

	public static final String DATE_FORMAT_NOW = "dd-MM-yyyy HH:mm:ss - ";

	private static boolean AUTO_BACKUP = true;

	public static int PORT = 8080;

	Map<String, LiveMixer> mixers = new LinkedHashMap<String, LiveMixer>();

	private boolean DEBUG = true;

	private static String DEFAULT_BACKUP_PATH = "livemixer_backup";
	private static String BACKUP_PATH = "livemixer_backup";

	private static WebServer webServer;

	public LiveMixerManager() {
		
	}
	
	public LiveMixerManager(String path) {
		recoverBackup(path);
	}
	
	@Override
	public boolean createPipeline(String name, Object[] inUrls, String outUrl) {
		System.out.println("creating a new pipeline: " + name + " " + Arrays.toString(inUrls) + " " + outUrl);
		if (!mixers.containsKey(name)) {
			List<String> inputs = new ArrayList<String>();
			for (Object in : inUrls)
				inputs.add((String) in);
			LiveMixer mixer = new LiveMixer(name, inputs, outUrl, false);
			mixers.put(name, mixer);
			mixer.playThread();
			System.out.println("pipeline playing.");
			if (AUTO_BACKUP) {
				backup(BACKUP_PATH);
				backup(DEFAULT_BACKUP_PATH);
			}
		} else {
			System.out.println("could not create the pipeline, it's already playing.");
			return false;
		}

		return true;
	}

	@Override
	public void destroyPipeline(String name) {
		System.out.println("destroying pipeline " + name + "...");
		mixers.get(name).shutdown();
		mixers.remove(name);
		if (AUTO_BACKUP) {
			backup(BACKUP_PATH);
			backup(DEFAULT_BACKUP_PATH);
		}
		System.gc();
		System.out.println("pipeline destroyed.");
	}

	@Override
	public boolean destroyPipeline(int id) {
		if (mixers.size() > id) {
			String name = (new ArrayList<String>(mixers.keySet())).get(id);
			destroyPipeline(name);
			return true;
		} else {
			return false;
		}
	}

	public static void main(String[] args) {
		try {
			System.out.println("LimeMixer Manager starting...");


			// Gst.init("StreamAdder", new String[] { "--gst-debug=liveadder:4"
			// });
			Gst.init("StreamAdder", new String[] { "--gst-debug-level=1" });
			webServer = new WebServer(PORT);
			XmlRpcServer xmlRpcServer = webServer.getXmlRpcServer();
			PropertyHandlerMapping phm = new PropertyHandlerMapping();
			
			LiveMixerManager liveMixerManager;
			if (args.length > 0) {
				DEFAULT_BACKUP_PATH = args[0];
				liveMixerManager = new LiveMixerManager(DEFAULT_BACKUP_PATH);
			} else
				liveMixerManager = new LiveMixerManager();
			
			phm.setRequestProcessorFactoryFactory(new LiveMixerFactoryFactory(liveMixerManager));
			phm.setVoidMethodEnabled(true);
			phm.addHandler(IManager.class.getName(), LiveMixerManager.class);
			xmlRpcServer.setHandlerMapping(phm);

			XmlRpcServerConfigImpl serverConfig = (XmlRpcServerConfigImpl) xmlRpcServer.getConfig();
			serverConfig.setEnabledForExtensions(true);
			serverConfig.setContentLengthOptional(false);
			webServer.start();
			System.out.println("LimeMixer Manager started!");
			String[] methods = phm.getListMethods();
			System.out.println(Arrays.toString(methods));
		} catch (Exception exception) {
			System.err.println("JavaServer: " + exception);
		}
	}

	@Override
	public boolean setVolume(String name, String inUrl, float volume) {
		mixers.get(name).setVolume(inUrl, volume);
		return true;
	}

	@Override
	public boolean setVolume(int id, int i, float volume) {
		if (mixers.size() > id) {
			String name = (new ArrayList<String>(mixers.keySet())).get(id);
			mixers.get(name).setVolume(mixers.get(name).getInputURLs().get(i), volume);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void addInput(int id, String url) {
		if (mixers.size() > id) {
			String name = (new ArrayList<String>(mixers.keySet())).get(id);
			mixers.get(name).addInputURL(url);
		}
	}

	@Override
	public boolean removeInput(int id, int i) {
		if (mixers.size() > id) {
			String name = (new ArrayList<String>(mixers.keySet())).get(id);
			mixers.get(name).removeInput(mixers.get(name).getInputURLs().get(i));
			return true;
		} else
			return false;
	}

	@Override
	public String[] listPipelines() {
		List<String> pipes = new ArrayList<String>();
		for (String name : mixers.keySet()) {
			LiveMixer livemixer = mixers.get(name);
			List<String> inUrls = livemixer.getInputURLs();
			String outUrl = livemixer.getOutputURL();
			StringBuilder str = new StringBuilder("Name: " + name + " - " + livemixer.getPipe().getState());
			for (String in : inUrls) {
				str.append("\nInput [" + inUrls.indexOf(in) + "]: " + in);
			}
			str.append("\nOutput: ").append(outUrl);
			pipes.add(str.toString());
		}
		return pipes.toArray(new String[0]);
	}

	@Override
	public boolean backup(String path) {
		StringBuilder str = new StringBuilder();
		for (String name : mixers.keySet()) {
			LiveMixer livemixer = mixers.get(name);
			List<String> inUrls = livemixer.getInputURLs();
			String outUrl = livemixer.getOutputURL();
			for (String in : inUrls) {
				str.append(in).append(" ");
			}
			str.append(outUrl);
			str.append('\n');
			System.out.println(getTime() + "Backuping pipeline: " + inUrls + " " + outUrl);
		}

		try {
			System.out.println(getTime() + "Saving backup: " + path);
			PrintWriter writer = new PrintWriter(new FileWriter(path));
			writer.print(str.toString());
			writer.close();
			System.out.println(getTime() + "Backup saved: " + path);
			BACKUP_PATH = path;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	private String getTime() {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
		return sdf.format(cal.getTime());
	}

	@Override
	public boolean recoverBackup(String path) {
		try {
			AUTO_BACKUP = false;
			destroyAllPipelines();

			System.out.println(getTime() + "Loading backup: " + path);
			BufferedReader reader = new BufferedReader(new FileReader(path));
			System.out.println(getTime() + "Backup loaded: " + path);

			String str;
			while ((str = reader.readLine()) != null) {
				if (str.length() > 1) {
					String[] params = str.split(" ");
					try {
						String[] inUrls = new String[params.length - 1];
						for (int i = 0; i < inUrls.length; i++) {
							inUrls[i] = params[i];
						}
						String outUrl = params[params.length - 1];
						System.out
								.println(getTime() + "Recovering pipeline: " + Arrays.toString(inUrls) + " " + outUrl);
						createPipeline(outUrl, inUrls, outUrl);
						System.out.println(getTime() + "Pipeline recovered: " + Arrays.toString(inUrls) + " " + outUrl);
					} catch (Exception e) {
						e.printStackTrace();
						return false;
					}
				}
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			AUTO_BACKUP = true;
		}
		return true;
	}

	/**
	 * 
	 */
	protected void destroyAllPipelines() {
		System.out.println(getTime() + "Removing all pipelines...");
		for (String name : new ArrayList<String>(mixers.keySet()))
			destroyPipeline(name);
		System.out.println(getTime() + "All pipelines removed");
	}

	@Override
	public void shutdown() {
		AUTO_BACKUP = false;
		destroyAllPipelines();
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(2000);
					// webServer.shutdown();
					System.exit(0);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}
}