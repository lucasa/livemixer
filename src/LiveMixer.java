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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gstreamer.Bin;
import org.gstreamer.Bus;
import org.gstreamer.Bus.STATE_CHANGED;
import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.Element.PAD_ADDED;
import org.gstreamer.Element.PAD_REMOVED;
import org.gstreamer.ElementFactory;
import org.gstreamer.GhostPad;
import org.gstreamer.Gst;
import org.gstreamer.GstObject;
import org.gstreamer.Message;
import org.gstreamer.MessageType;
import org.gstreamer.Pad;
import org.gstreamer.PadLinkReturn;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.Structure;

import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IStreamCoder;

public class LiveMixer {

	private boolean FILE_OUPUT = true;

	private int INDEX = 0;

	public boolean DEBUG = false;

	public boolean AUDIO_OUTPUT = false;

	public boolean FIFO_PIPE_OUTPUT = true;

	private boolean FAKE_INPUT = false;

	protected boolean AUTO_RECOVERY = true;

	private boolean FFMPEG_OUPUT = true;

	private Map<String, Element> volumeElements = new HashMap<String, Element>();

	private Pipeline pipe;

	private Caps capsAudio;

	private int OUTPUT_CHANELS = 2;

	private int OUTPUT_FREQ = 44100;

	private int MP3_BITRATE = 96;

	private String outputURL;

	private File fileFifo;

	private String OUTPUT_FORMAT = "flv";

	private Element adder;

	private Thread ffmpegThread;

	private Map<String, Element> inputElements = new HashMap<String, Element>();

	private String FFMPEG_PATH0 = "./ffmpeg";
	private String FFMPEG_PATH1 = "/usr/local/bin/ffmpeg";
	private String FFMPEG_PATH2 = "/usr/bin/ffmpeg";

	private Process ffmpegProcess;

	private String name;

	private List<String> inputURLs;

	private String pathFifo;

	private Element queueTee;

	private STATE_CHANGED STATE_CHANGED;

	private Element removedInput;

	private Element removedIdentity;

	protected int pidFFmpeg;

	private boolean RUN = true;

	public static void main(String[] args) throws InterruptedException {
		if (args.length < 2)
			throw new IllegalArgumentException(
					"Parameters: input_file_or_url_1 [input_file_or_url_2] ... [input_file_or_url_N] output_file_or_url");

		// Gst.init("StreamAdder", new String[] { "--gst-debug-level=1",
		// "--gst-debug=progressreport:1" });

		List<String> inputUrls = new ArrayList<String>();
		for (int i = 0; i < args.length - 1; i++) {
			// if (args[i].toLowerCase().equals("false") ||
			// args[i].toLowerCase().equals("true"))
			// DEBUG = Boolean.valueOf(args[i]);
			// else
			inputUrls.add(args[i]);
		}

		String outputUrl = args[args.length - 1];
		final LiveMixer adder = new LiveMixer("rtmp", inputUrls, outputUrl, true);
		adder.playThread();

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				System.err.println("System is Shutting Down...");
				adder.getFfmpegProcess().destroy();
			}
		}));

		adder.pipe.debugToDotFile(Bin.DEBUG_GRAPH_SHOW_STATES, "livemixer.dot");

		Gst.main();
	}

	public LiveMixer(String name, List<String> inputURLs, String outputURL, boolean debug) {
		this.DEBUG = debug;
		this.name = name;
		this.outputURL = outputURL;
		this.inputURLs = inputURLs;
		createPipeline();
	}

	public Process getFfmpegProcess() {
		return ffmpegProcess;
	}

	/**
	 * @param inputURLs
	 * @param outputURL
	 * @param pathFifo
	 */
	public void createPipeline() {
		String config = "rate=" + OUTPUT_FREQ + ",channels=" + OUTPUT_CHANELS + ",depth=16";
		pathFifo = "/tmp/" + outputURL.replace('/', '_') + "_" + config.replace(',', '_').replace('=', '_');
		this.capsAudio = Caps.fromString("audio/x-raw-int," + config);
		pipe = new Pipeline("Audio Mixer Pipeline");
		try {
			adder = ElementFactory.make("adder", "adder mixer");
		} catch (Exception e) {
		}
		adder.connect(new PAD_REMOVED() {
			@Override
			public void padRemoved(Element element, Pad pad) {
				if (removedInput != null) {
					// unlinkInput();
				}
			}
		});
		adder.connect(new PAD_ADDED() {
			@Override
			public void padAdded(Element element, Pad pad) {
				// linkInput(pad);
			}
		});

		Element tee = ElementFactory.make("tee", "tee");
		queueTee = ElementFactory.make("queue2", "queue tee");
		pipe.addMany(adder, queueTee, tee);
		Element.linkMany(adder, queueTee, tee);

		if (AUDIO_OUTPUT) {
			Element audioSink = ElementFactory.make("alsasink", "Audio Sink");
			audioSink.set("sync", false);
			pipe.addMany(audioSink);
			Element.linkMany(tee, audioSink);
		}

		if (FILE_OUPUT) {
			fileFifo = new File(pathFifo);
			try {
				if (!fileFifo.exists()) {
					String command = "/usr/bin/mkfifo " + fileFifo.getAbsolutePath();
					ProcessBuilder b = new ProcessBuilder("/bin/sh", "-c", command);
					b.start().waitFor();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			Element codecEnc = ElementFactory.make("lamemp3enc", "MP3 Encoder");
			codecEnc.set("target", 1);
			codecEnc.set("bitrate", MP3_BITRATE);
			codecEnc.set("cbr", true);
			Element mux = ElementFactory.make("flvmux", "FLV Muxer");
			mux.set("streamable", true);

			Element queue = ElementFactory.make("queue2", "Fifo Queue");
			Element filesink = ElementFactory.make("filesink", "Fifo Sink");
			filesink.set("sync", false);
			if (FIFO_PIPE_OUTPUT) {
				filesink.set("location", fileFifo.getAbsolutePath());
			} else {
				filesink.set("location", "/dev/null");
			}

			Element capsfilter = ElementFactory.make("capsfilter", null);
			capsfilter.setCaps(capsAudio);
			Element audiorate = ElementFactory.make("audiorate", null);
			Element audioconvert = ElementFactory.make("audioconvert", null);

			Element capsfilterMp3 = ElementFactory.make("capsfilter", null);
			capsfilterMp3.setCaps(Caps.fromString("audio/mpeg,rate=" + OUTPUT_FREQ + ",channels=" + OUTPUT_CHANELS));

			pipe.addMany(queue, audiorate, audioconvert, capsfilter, codecEnc, capsfilterMp3, mux, filesink);
			boolean linked = Element.linkMany(tee, audiorate, audioconvert, queue, capsfilter, codecEnc, capsfilterMp3,
					mux, filesink);

			debug("File output linked: " + linked);

			if (FFMPEG_OUPUT)
				startFFmpegProcess();
		}

		int i = 0;
		for (String url : inputURLs) {
			i++;
			if (checkUrl(url)) {
				addInput(url);
			}
		}

		prepareBus();
	}

	/**
	 * @param outputURL
	 */
	public void startFFmpegProcess() {
		if (ffmpegThread == null) {
			ffmpegThread = new Thread(new Runnable() {
				@Override
				public void run() {
					ffmpegProcess = executaFFmpeg(outputURL, fileFifo);
					try {
						pidFFmpeg = Util.getProcessId(ffmpegProcess);
						ffmpegProcess.waitFor();
					} catch (InterruptedException e) {
						e.printStackTrace();
					} finally {
						closeFFmpegProcess();
						System.err.println("ffmpg closed!");
					}
				}
			});
			ffmpegThread.start();
		}
	}

	private boolean checkUrl(String url) {
		// TODO: implement check if url input is valid
		return true;
	}

	public boolean addInputURL(String url) {
		if (checkUrl(url)) {
			addInput(url);
			return true;
		} else
			return false;
	}

	private Process executaFFmpeg(String outputURL, File fileFifo) {
		String FFMPEG_PATH = "";

		if (new File(FFMPEG_PATH0).exists() == true)
			FFMPEG_PATH = FFMPEG_PATH0;
		else if (new File(FFMPEG_PATH1).exists() == true)
			FFMPEG_PATH = FFMPEG_PATH1;
		else
			FFMPEG_PATH = FFMPEG_PATH2;

		String command = "/bin/cat " + fileFifo.getAbsolutePath() + " | " + FFMPEG_PATH
				+ " -i - -re -vn -y -loglevel 0 -debug 0 -ac " + OUTPUT_CHANELS + " -ar " + OUTPUT_FREQ
				+ " -acodec libmp3lame -f " + OUTPUT_FORMAT + " " + outputURL;
		// if (!DEBUG)
		command += " 2>/dev/null";
		// else
		// command += " 2>" + fileFifo.getAbsolutePath() + "_log.log";

		debug(command);

		// TODO: só precisa instanciar 1 vez
		ProcessBuilder b = new ProcessBuilder("/bin/sh", "-c", command);
		Process processo = null;
		try {
			processo = b.start(); // TODO: merge error and output streams
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (DEBUG) {
			final InputStream error = processo.getErrorStream();
			final InputStream in = processo.getInputStream();
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						byte[] bserr = null;
						while (RUN) {
							bserr = new byte[error.available()];
							if (bserr.length > 0) {
								error.read(bserr);
								String error = new String(bserr);
								debug(error);
							}
							Thread.sleep(100);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			thread.start();
			thread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						byte[] bin = null;
						while (RUN) {
							bin = new byte[in.available()];
							if (bin.length > 0) {
								in.read(bin);
								String sin = new String(bin);
								debug(sin);
							}
							Thread.sleep(100);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			thread.start();
		}
		return processo;
	}

	public void debug(String string) {
		if (DEBUG)
			System.out.print("\n" + string);
	}

	private void addInput(String url) {
		INDEX++;
		int i = INDEX;
		System.out.println("\n------------\nAdding Input[" + i + "]: " + url + "\n-------------\n");

		Bin input = null;

		if (url.indexOf("/home/") == 0) {
			url = "file://" + url;
		}

		Element src = null;
		if (!FAKE_INPUT) {
			src = ElementFactory.make("uridecodebin", "Input" + i);
			src.set("uri", url);
			src.set("buffer-duration", 5000);
			src.set("use-buffering", true);
			src.set("download", true);
		} else {
			src = ElementFactory.make("audiotestsrc", "Audio Fake" + i);
			int w = i;
			if (i > 1)
				w = 5;
			src.set("wave", w);
			src.set("is-live", true);
		}

		input = new Bin("Input Bin" + i);

		final Element conv = ElementFactory.make("audioconvert", "Audio Convert" + i);
		Element resample = ElementFactory.make("audioresample", "Audio Resample" + i);
		Element volume = ElementFactory.make("volume", "Audio Volume" + i);
		volume.set("volume", 1.0f);
		volumeElements.put(url, volume);

		Element capsfilter = ElementFactory.make("capsfilter", null);
		capsfilter.setCaps(capsAudio);

		Element progressreport = ElementFactory.make("progressreport", "Progress Report" + i);
		progressreport.set("update-freq", 30);

		input.addMany(src, progressreport, conv, resample, volume);
		boolean linked = Element.linkMany(conv, resample, progressreport, volume);
		debug("Input elements linked: " + linked);
		input.addPad(new GhostPad("src", volume.getSrcPads().get(0)));

		src.connect(new PAD_ADDED() {
			@Override
			public void padAdded(Element elem, Pad pad) {
				/* only link once */
				if (pad.isLinked()) {
					return;
				}
				/* check media type */
				Caps caps = pad.getCaps();
				Structure struct = caps.getStructure(0);
				if (struct.getName().startsWith("audio/")) {
					debug("Linking audio pad: " + struct.getName());
					if (conv.getStaticPad("sink").getPeer() == null) {
						PadLinkReturn linked = pad.link(conv.getStaticPad("sink"));
						debug("Uridecodebin linked " + linked);
					}
				} else if (struct.getName().startsWith("video/")) {
					debug("Linking video pad: " + struct.getName());
				} else {
					debug("Unknown pad [" + struct.getName() + "]");
				}
			}
		});

		if (!inputURLs.contains(url)) {
			inputURLs.add(url);
		}
		inputElements.put(url, input);

		boolean playing = pipe.isPlaying();

		pipe.add(input);
		linked = input.link(adder);
		debug("new Input [" + input + "] linked with adder: " + linked);

		if (playing) {
			debug("Adder inputs: " + adder.getSinkPads());
			pipe.setState(State.PLAYING);
		}

		System.out.println("\n------------\nInput[" + i + "] " + url + " added.\n-------------\n");
	}

	public boolean removeInput(final String url) {

		if (!inputElements.containsKey(url))
			return false;

		System.out.println("\n------------\nRemoving Input: " + url + "\n-------------\n");
		removedInput = inputElements.get(url);

		boolean playing = pipe.isPlaying();

		Pad inputSrcPad = removedInput.getSrcPads().get(0);
		Pad adderSinkPad = inputSrcPad.getPeer();

		inputSrcPad.setBlocked(true);
		removedInput.setState(State.NULL);
		inputSrcPad.unlink(adderSinkPad);
		debug("Input removed: " + removedInput);
		adder.releaseRequestPad(adderSinkPad);

		inputElements.remove(url);
		if (playing) {
			debug("Adder inputs: " + adder.getSinkPads());
			pipe.setState(State.PLAYING);
		}

		System.out.println("\n------------\nInput " + url + " removed.\n-------------\n");
		return true;
	}

	public void prepareBus() {

		Bus bus = pipe.getBus();

		bus.connect(new Bus.MESSAGE() {
			@Override
			public void busMessage(Bus bus, Message msg) {
				if (msg.getType() == MessageType.ELEMENT)
					if (msg.getSource().getName().contains("Progress")) {
						debug("Message progres: " + msg.getStructure());
					}
			}
		});

		STATE_CHANGED = new STATE_CHANGED() {
			@Override
			public void stateChanged(GstObject source, State old, State current, State pend) {
				debug(source + " new state: " + current);
				if (source == pipe) {
					debug("Pipeline new state: " + current);
					if (old == State.PLAYING && current != State.PLAYING) {
						if (AUTO_RECOVERY) {
							recreatePipeline();
						}
					}
				}
			}
		};
		bus.connect(STATE_CHANGED);

		bus.connect(new Bus.ERROR() {
			public void errorMessage(GstObject source, int code, String message) {
				debug("Error: code=" + code + " message=" + message);
				if (code == 1) {
					pipe.setState(State.NULL);
				}
			}
		});
		bus.connect(new Bus.EOS() {
			public void endOfStream(GstObject source) {
				debug("EOS");
				if (AUTO_RECOVERY) {
					recreatePipeline();
				}
			}

		});
	}

	public void setVolume(String url, float volume) {
		volumeElements.get(url).set("volume", volume);
		debug("Setting volume of " + volumeElements.get(url).getName() + ": " + volume);
	}

	/**
	 * 
	 */
	public void play() {
		pipe.setState(State.PLAYING);
	}

	public void stop() {
		pipe.setState(State.NULL);
		closeFFmpegProcess();
		pipe = null;
	}

	/**
	 * @return
	 */
	public Thread playThread() {
		Thread play = new Thread(new Runnable() {
			@Override
			public void run() {
				RUN = true;
				pipe.play();
				System.out.println("\n------------\nPipeline " + name + " started.\n-------------\n");
			}
		});
		play.start();
		return play;
	}

	/**
	 * 
	 */
	public void closeFFmpegProcess() {
		if (ffmpegProcess != null) {
			try {
				ffmpegProcess.destroy();
				ffmpegProcess = null;
				ffmpegThread = null;
			} finally {
				Util.kill9(pidFFmpeg);
			}
		}
	}

	/**
	 * 
	 */
	public void unlinkInput() {
		pipe.remove(removedInput);
		pipe.remove(removedIdentity);
		removedInput.setState(State.NULL);
		removedIdentity.setState(State.NULL);
		debug("padRemoved: " + removedInput);
		removedInput = null;
		removedInput = null;
		System.gc();
	}

	/**
	 * @param pad
	 * @param newInput
	 */
	protected void linkInputPad(final Pad pad, final Element newInput) {
		Element identity = ElementFactory.make("identity", "identity" + INDEX);
		identity.set("sync", true);
		identity.set("single-segment", true);
		pipe.addMany(newInput, identity);
		PadLinkReturn linked = newInput.getSrcPads().get(0).link(identity.getSinkPads().get(0));
		debug("new input linked: " + linked);
		if (pipe.isPlaying()) {
			State state = State.PAUSED;
			newInput.setState(state);
		}
		PadLinkReturn linked3 = identity.getSrcPads().get(0).link(pad);
		debug("new identity linked: " + linked3);
	}

	protected static short[] byte2short(byte[] byteArray) {
		return byte2short(byteArray, byteArray.length);
	}

	protected static short[] byte2short(byte[] byteArray, int limit) {
		if (byteArray == null) {
			return null;
		} else {
			short[] shortArray = new short[limit / 2];
			for (int i = 0; i < limit; i = i + 2) {
				shortArray[i / 2] = (short) ((byteArray[i] & 0xFF) + ((((short) byteArray[i + 1]) << 8) & 0xFF00));
			}
			return shortArray;
		}
	}

	public Map<String, Element> getVolumeElements() {
		return volumeElements;
	}

	public String getOutputURL() {
		return outputURL;
	}

	public List<String> getInputURLs() {
		return inputURLs;
	}

	public Pipeline getPipe() {
		return pipe;
	}

	/**
	 * 
	 */
	protected void recreatePipeline() {
		System.out.println("\n------------\nrecreating Pipeline: " + name + "\n-------------\n");
		try {
			pipe.setState(State.NULL);
			pipe = null;
			inputElements.clear();
			closeFFmpegProcess();
			createPipeline();
			playThread();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void shutdown() {
		AUTO_RECOVERY = false;
		RUN = false;
		try {
			closeFFmpegProcess();
			inputElements.clear();
			volumeElements.clear();
			pipe.setState(State.NULL);
			pipe = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
