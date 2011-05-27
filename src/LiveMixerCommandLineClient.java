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

import java.net.URL;
import java.util.Scanner;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.util.ClientFactory;

public class LiveMixerCommandLineClient {

	private static final String urlServidor = "http://localhost:" + LiveMixerManager.PORT;
	private static LiveMixerCommandLineClient remote;
	private IManager manager;

	public LiveMixerCommandLineClient(String urlPort) {
		try {
			XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
			config.setServerURL(new URL(urlPort));
			config.setEnabledForExtensions(true);
			config.setEnabledForExtensions(true);
			config.setConnectionTimeout(60 * 1000);
			config.setReplyTimeout(60 * 1000);
			XmlRpcClient client = new XmlRpcClient();
			client.setConfig(config);
			ClientFactory factory = new ClientFactory(client);
			manager = (IManager) factory.newInstance(IManager.class);
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	public IManager getManager() {
		return manager;
	}

	public static void main(String[] args) {
		String urlPort = urlServidor;
		if (args.length > 0)
			urlPort = args[0];
		remote = new LiveMixerCommandLineClient(urlPort);
		Scanner scan = new Scanner(System.in);
		int option = -1;
		while (option != 0) {
			try {
				System.out.println("\nChoose a option:");
				System.out.println("Exit - 0");
				System.out.println("Create a pipeline - 1");
				System.out.println("Destroy a pipeline - 2");
				System.out.println("List pipelines - 3");
				System.out.println("Set volume of pipeline input - 4");
				System.out.println("Add new pipeline input - 5");
				System.out.println("Remove a pipeline input - 6");
				System.out.println("Backup pipelines to file - 7");
				System.out.println("Recover pipelines from file - 8");
				System.out.println("Shutdown deamon - 99");
				System.out.print("Option: ");
				option = scan.nextInt();
				if (option == 0) {
					break;
				} else if (option == 1) {					
					System.out
							.print("Digit the parameters of the pipeline [format: url_input_1 [url_input_n] url_output]: ");
					if (scan.hasNext()) {
						scan.nextLine();
						String line = scan.nextLine();
						if (line.contains(" ")) {
							String[] params = line.split(" ");
							String[] inputs = new String[params.length - 1];
							for (int i = 0; i < inputs.length; i++) {
								inputs[i] = params[i];
							}
							String output = params[params.length - 1];
							String name = output;
							remote.getManager().createPipeline(name, inputs, output);
							System.out.println("Pipeline created.");
						}
					}
				} else if (option == 2) {
					listPipelines(remote);
					System.out.print("Digit the ID of the pipeline to destroy: ");
					int id = scan.nextInt();
					boolean done = remote.getManager().destroyPipeline(id);
					if (done)
						System.out.println("Pipeline destroyed.");
					else
						System.out.println("Pipeline not destroyed.");
				} else if (option == 3) {
					listPipelines(remote);
				} else if (option == 4) {
					listPipelines(remote);
					System.out.print("Digit the ID of the pipeline to configure: ");
					int id = scan.nextInt();
					System.out.print("Digit the ID of the input: ");
					int i = scan.nextInt();
					System.out.print("Digit the new % volume [0 - 200]: ");
					float v = scan.nextInt() / 100f;
					boolean done = remote.getManager().setVolume(id, i, v);
					if (done)
						System.out.println("Pipeline configured.");
					else
						System.out.println("Pipeline not configured.");
				} else if (option == 5) {
					listPipelines(remote);
					System.out.print("Digit the ID of the pipeline to configure: ");
					int id = scan.nextInt();
					System.out.print("Digit the URL of the input: ");
					String url = scan.nextLine();
					remote.getManager().addInput(id, url);
					System.out.println("New input added: " + url);
				} else if (option == 6) {
					listPipelines(remote);
					System.out.print("Digit the ID of the pipeline to configure: ");
					int id = scan.nextInt();
					System.out.print("Digit the ID of the input to remove: ");
					int i = scan.nextInt();
					boolean done = remote.getManager().removeInput(id, i);
					if (done)
						System.out.println("Input removed.");
					else
						System.out.println("Input not removed.");
				} else if (option == 7) {
						System.out.print("Digit the file path to save backup: ");
						String path = scan.next();
						boolean done = remote.getManager().backup(path);
						if (done)
							System.out.println("Backup saved.");
						else
							System.out.println("Backup not saved.");
				} else if (option == 8) {
					System.out.print("Digit the file path to recover backup: ");
					String path = scan.next();
					boolean done = remote.getManager().recoverBackup(path);
					if (done)
						System.out.println("Backup recovered.");
					else
						System.out.println("Backup not recovered.");
				} else if (option == 99) {
					System.out.print("Going down...");
					remote.getManager().shutdown();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		System.out.println("Exited.");
	}

	/**
	 * @param remote
	 */
	protected static void listPipelines(LiveMixerCommandLineClient remote) {
		System.out.print("\nList of active pipelines: ");
		Object[] pipelines = remote.getManager().listPipelines();
		int i = 0;
		for (Object pipe : pipelines) {
			System.out.println("\n----- Pipeline [ ID " + i + "] -------");
			System.out.println(pipe);
			System.out.println("---------------------------------");
			i++;
		}
		System.out.println();
	}
}
