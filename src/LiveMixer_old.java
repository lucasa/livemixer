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
import org.gstreamer.Event;
import org.gstreamer.GhostPad;
import org.gstreamer.Gst;
import org.gstreamer.GstObject;
import org.gstreamer.Pad;
import org.gstreamer.Pad.EVENT_PROBE;
import org.gstreamer.PadLinkReturn;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.Structure;
import org.gstreamer.elements.DecodeBin2;

import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IStreamCoder;

/*******************************************************************************
 * Copyright (c) 2011, Author: Lucas Alberto Souza Santos <lucasa@gmail.com>.
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
public class LiveMixer_old {
	private int INDEX = 0;

	public static boolean DEBUG = true;

	public static boolean AUDIO_OUTPUT = false;
	
	public static boolean STREAM_OUTPUT = true;
	
	private boolean FAKE_INPUT = true;

	protected boolean AUTO_RECOVERY = false;

	private boolean FFMPEG_OUPUT = false;
	// private Map<String, Float> volumes = new HashMap<String, Float>();

	private Map<String, Element> volumeElements = new HashMap<String, Element>();

	private Pipeline pipe;

	private Caps capsAudio;

	private int OUTPUT_CHANELS = 2;

	private int OUTPUT_FREQ = 44100;

	private String outputURL;

	// private IMediaWriter writer;

	private IContainer outContainer;

	private IStreamCoder outStreamCoder;

	// private int lastPos;

	private OutputStream outputStream;

	private File fileFifo;

	private String OUTPUT_FORMAT = "flv";

	private Element adder;

	private Thread ffmpegThread;

	private Map<String, Element> inputElements = new HashMap<String, Element>();

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


	public static void main(String[] args) throws InterruptedException {
		if (args.length < 2)
			throw new IllegalArgumentException(
					"Parameters: input_file_or_url_1 [input_file_or_url_2] ... [input_file_or_url_N] output_file_or_url");

		// Gst.init("StreamAdder", new String[] { "--gst-debug=liveadder:4" });
		Gst.init("StreamAdder", new String[] { "--gst-debug-level=1" });

		List<String> inputUrls = new ArrayList<String>();
		for (int i = 0; i < args.length - 1; i++) {
			if (args[i].toLowerCase().equals("false") || args[i].toLowerCase().equals("true"))
				DEBUG = Boolean.valueOf(args[i]);
			else
				inputUrls.add(args[i]);
		}

		String outputUrl = args[args.length - 1];
		final LiveMixer_old adder = new LiveMixer_old("rtmp", inputUrls, outputUrl);
		adder.playThread();

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				System.err.println("System is Shutting Down...");
				adder.getFfmpegProcess().destroy();
			}
		}));

		String url2 = "rtmp://wowza01.dyb.fm/teste/teste/media3";
		String url3 = "rtmp://wowza01.dyb.fm/dyb/runningradio/media";

//		Thread.sleep(5000);
//		adder.pipe.setState(State.NULL);
//		Thread.sleep(5000);
//		adder.pipe.setState(State.PLAYING);
		
		for (int i = 0; i < 10;) {
			Thread.sleep(20000);			
			adder.addInputURL(url3);
			Thread.sleep(20000);
			adder.removeInput(url3);
		}

		Gst.main();
	}

	public LiveMixer_old(String name, List<String> inputURLs, final String outputURL) {
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
		pathFifo = "/tmp/" + outputURL.replace('/', '_') + "_" + config;
		this.capsAudio = Caps.fromString("audio/x-raw-int," + config + ";audio/x-raw-float," + config);
		pipe = new Pipeline("Audio Mixer Pipeline");
		try {
			adder = ElementFactory.make("liveadder", "liveadder");
			//adder.set("latency", 5 * 1000);
		} catch (Exception e) {
		}
		adder.connect(new PAD_REMOVED() {
			@Override
			public void padRemoved(Element element, Pad pad) {
				if (removedInput != null) {
					pipe.remove(removedInput);
					pipe.remove(removedIdentity);
					removedInput.setState(State.NULL);
					removedIdentity.setState(State.NULL);
					System.out.println("padRemoved: " + removedInput);
					removedInput = null;
					removedInput = null;
					System.gc();
				}
			}
		});

		adder.connect(new PAD_ADDED() {
			@Override
			public void padAdded(Element element, Pad pad) {
				linkNewInputToPad(pad);
			}

		});

		Element tee = ElementFactory.make("tee", "tee");

		Element capsfilter = ElementFactory.make("capsfilter", null);
		capsfilter.setCaps(capsAudio);
		queueTee = ElementFactory.make("queue2", null);
		pipe.addMany(adder, queueTee, tee, capsfilter);
		Element.linkMany(adder, queueTee, capsfilter, tee);

		if (AUDIO_OUTPUT) {
			Element audioSink = ElementFactory.make("alsasink", "Audio Sink");
			audioSink.set("sync", false);
			pipe.addMany(audioSink);
			Element.linkMany(tee, audioSink);
		}
		
		if(STREAM_OUTPUT)
		{

			// fileFifo.deleteOnExit();
			fileFifo = new File(pathFifo);
			try {
				if (!fileFifo.exists()) {
					// fileFifo.delete();
					String command = "/usr/bin/mkfifo " + fileFifo.getAbsolutePath();
					ProcessBuilder b = new ProcessBuilder("/bin/sh", "-c", command);
					b.start().waitFor();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			Element codecEnc = ElementFactory.make("lamemp3enc", "MP3 Encoder");
			Element mux = ElementFactory.make("flvmux", "FLV Muxer");
			Element queue = ElementFactory.make("queue2", "Fifo Queue");
			Element filesink = ElementFactory.make("filesink", "Fifo Sink");
			filesink.set("sync", false);
			filesink.set("location", fileFifo.getAbsolutePath());
			pipe.addMany(queue, codecEnc, mux, filesink);
			Element.linkMany(tee, queue, codecEnc, mux, filesink);

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
	 * @param pad
	 */
	public void linkNewInputToPad(Pad pad) {
		String addedUrl = inputURLs.get(inputURLs.size() - 1);
		final Element newInput = inputElements.get(addedUrl);

		EVENT_PROBE probeListener = new EVENT_PROBE() {
			public boolean eventReceived(Pad pad, Event event) {
				String type = event.getStructure().toString();
				if (DEBUG)
					System.out.println("eventReceived: " + type);
				if (type.toLowerCase().indexOf("error") >= 0) {
					System.out.println("Error");
					return false;
				}
				return true;
			}
		};
		adder.getSrcPads().get(0).addEventProbe(probeListener);

		Element identity = ElementFactory.make("identity", "identity" + INDEX);
		identity.set("sync", true);
		identity.set("single-segment", true);
		// identity.set("silent", false);
		// Element mux = ElementFactory.make("flvmux", "mux-timestamp" + INDEX);
		// Element enc = ElementFactory.make("lamemp3enc", "enc-timestamp" +
		// INDEX);
		// Element demux = ElementFactory.make("flvdemux", "demux-timestamp" +
		// INDEX);
		// Element dec = ElementFactory.make("flump3dec", "dec-timestamp" +
		// INDEX);
		//
		// // identity.set("silent", false);
		// pipe.addMany(identity, enc, mux, demux, dec);
		//
		// identity.setState(State.PAUSED);
		// mux.setState(State.PAUSED);
		// enc.setState(State.PAUSED);
		// demux.setState(State.PAUSED);
		// dec.setState(State.PAUSED);
		//
		pipe.addMany(newInput, identity);
		PadLinkReturn linked = newInput.getSrcPads().get(0).link(identity.getSinkPads().get(0));
		System.out.println("new input linked: " + linked);
		//
		// boolean l = Element.linkMany(identity, enc, mux, demux, dec);
		// System.out.println("mux demux linked: "+l);
		//
		// PadLinkReturn linked2 = dec.getSrcPads().get(0).link(pad);
		// System.out.println("new dec linked: " + linked2);
		if (pipe.isPlaying()) {
			State state = State.READY;
			//newInput.setState(state);
			pipe.setState(state);
		}
		PadLinkReturn linked3 = identity.getSrcPads().get(0).link(pad);
		System.out.println("new identity linked: " + linked3);
	}

	/**
	 * @param outputURL
	 */
	public void startFFmpegProcess() {
		if (ffmpegThread == null && FFMPEG_OUPUT) {
			ffmpegThread = new Thread(new Runnable() {
				@Override
				public void run() {
					ffmpegProcess = executaFFmpeg(outputURL, fileFifo);
					try {
						ffmpegProcess.waitFor();
					} catch (InterruptedException e) {
						e.printStackTrace();
					} finally {
						closeFFmpegProcess();
						System.out.println("ffmpg closed!");
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

		if (new File(FFMPEG_PATH1).exists() == true)
			FFMPEG_PATH = FFMPEG_PATH1;
		else
			FFMPEG_PATH = FFMPEG_PATH2;

		String command = "/bin/cat " + fileFifo.getAbsolutePath() + " | " + FFMPEG_PATH
				+ " -i - -re -vn -y -loglevel 0 -debug 0 -ac " + OUTPUT_CHANELS + " -ar " + OUTPUT_FREQ
				+ " -acodec copy -f " + OUTPUT_FORMAT + " " + outputURL;
		if (!DEBUG)
			command += " 2>/dev/null";
//		else
//			command += " 2>" + fileFifo.getAbsolutePath() + "_log.log";
		System.out.println(command);
		// TODO: só precisa instanciar 1 vez
		ProcessBuilder b = new ProcessBuilder("/bin/sh", "-c", command);
		Process processo = null;
		try {
			processo = b.start(); // TODO: merge error and output streams
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		// outputStream = processo.getOutputStream();
		// outputStream = new BufferedOutputStream(outputStream);
		if (DEBUG) {
			final InputStream error = processo.getErrorStream();
			final InputStream in = processo.getInputStream();
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						byte[] bserr = null;
						while (true) {
							try {
								bserr = new byte[error.available()];
								if (bserr.length > 0) {
									error.read(bserr);
									String error = new String(bserr);
									System.out.println(error);
								}
								Thread.sleep(100);
							} catch (Exception e) {
								e.printStackTrace();
							}
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
						while (true) {
							try {
								bin = new byte[in.available()];
								if (bin.length > 0) {
									in.read(bin);
									String sin = new String(bin);
									System.out.println(sin);
								}
								Thread.sleep(100);
							} catch (Exception e) {
								e.printStackTrace();
							}
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

	public static void debug(String string) {
		System.out.print("\n" + string);
	}

	private void addInput(String url) {

		INDEX++;
		int i = INDEX;
		System.out.println("\n------------\naddInput["+i+"]: " + url + "\n-------------\n");

		Element input = null;
		if (!FAKE_INPUT) {
			/* create audio output */
			final Bin audioBin = new Bin("Audio Bin" + i);

			Element src = null;
			if (url.contains("http://")) {
				src = ElementFactory.make("gnomevfssrc", "Input" + i);
				src.set("location", url);
			} else if (url.contains("rtmp") && url.contains("://")) {
				src = ElementFactory.make("rtmpsrc", "Input" + i);
				// src.set("do-timestamp", true);
				src.set("location", url);
			} else {
				src = ElementFactory.make("filesrc", "Input" + i);
				src.set("location", url);
			}

			DecodeBin2 decodeBin = new DecodeBin2("Decode Bin" + i);
			Element decodeQueue = ElementFactory.make("queue2", "Decode Queue" + i);

			Element conv = ElementFactory.make("audioconvert", "Audio Convert" + i);
			Element resample = ElementFactory.make("audioresample", "Audio Resample" + i);
			Element volume = ElementFactory.make("volume", "Audio Volume" + i);
			volume.set("volume", 1.0f);
			volumeElements.put(url, volume);
			audioBin.addMany(conv, resample, volume);
			Element.linkMany(conv, resample, volume);
			audioBin.addPad(new GhostPad("src", volume.getStaticPad("src")));
			audioBin.addPad(new GhostPad("sink", conv.getStaticPad("sink")));

			input = new Bin("Input Bin" + i);
			((Bin) input).addMany(src, decodeQueue, decodeBin, audioBin);
			Element.linkMany(src, decodeQueue, decodeBin, audioBin);
			input.addPad(new GhostPad("src", audioBin.getSrcPads().get(0)));

			decodeBin.connect(new DecodeBin2.NEW_DECODED_PAD() {
				public void newDecodedPad(Element elem, Pad pad, boolean last) {
					/* only link once */
					if (pad.isLinked()) {
						return;
					}
					/* check media type */
					Caps caps = pad.getCaps();
					Structure struct = caps.getStructure(0);
					if (struct.getName().startsWith("audio/")) {
						System.out.println("Linking audio pad: " + struct.getName());
						if (audioBin.getStaticPad("sink").getPeer() == null) {
							PadLinkReturn linked = pad.link(audioBin.getStaticPad("sink"));
							System.out.println("Decodebin linked " + linked);
						}
					} else if (struct.getName().startsWith("video/")) {
						System.out.println("Linking video pad: " + struct.getName());
					} else {
						System.out.println("Unknown pad [" + struct.getName() + "]");
					}
				}
			});
		}
		else {
			input = ElementFactory.make("audiotestsrc", "Audio Fake" + i);
			int w = i;
			if(i > 1)
				w = 5;
			input.set("wave", w);
			input.set("is-live", true);
		}

		if (!inputURLs.contains(url)) {
			inputURLs.add(url);
		}
		inputElements.put(url, input);

		boolean playing = pipe.isPlaying();

		Pad adderSink = adder.getRequestPad("sink%d");
		if (playing) {
			System.out.println("Adder inputs: "+adder.getSinkPads());
			pipe.setState(State.PLAYING);
		}
	}

	public void removeInput(final String url) {

		if (!inputElements.containsKey(url))
			return;

		System.out.println("\n------------\nremoveInput: " + url + "\n-------------\n");
		removedInput = inputElements.get(url);

		boolean playing = pipe.isPlaying();
		Pad inputSrcPad = removedInput.getSrcPads().get(0);
		removedIdentity = inputSrcPad.getPeer().getParentElement();
		Pad adderSinkPad = removedIdentity.getSrcPads().get(0).getPeer();
		pipe.setState(State.READY);
		boolean removed = adder.removePad(adderSinkPad);
		System.out.println("Pad removed: " + removed);

		inputElements.remove(url);
		if (playing) {
			System.out.println("Adder inputs: "+adder.getSinkPads());
			pipe.setState(State.PLAYING);
		}
	}

	public void prepareBus() {
		Bus bus = pipe.getBus();

		STATE_CHANGED = new STATE_CHANGED() {
			@Override
			public void stateChanged(GstObject source, State old, State current, State pend) {
				if (source == pipe) {
					System.out.println("Pipeline new state: " + current);
					if (old == State.PLAYING && current == State.NULL) {
						if (AUTO_RECOVERY) {
							pipe.setState(State.NULL);
							pipe = null;
							inputElements.clear();
							closeFFmpegProcess();
							createPipeline();
							playThread();
						}
					}
				}
			}
		};
		bus.connect(STATE_CHANGED);

		bus.connect(new Bus.ERROR() {
			public void errorMessage(GstObject source, int code, String message) {
				System.out.println("Error: code=" + code + " message=" + message);
				if (code == 1) {
					pipe.setState(State.NULL);
				}
			}
		});
		bus.connect(new Bus.EOS() {
			public void endOfStream(GstObject source) {
				if (AUTO_RECOVERY) {
					pipe.setState(State.NULL);
					pipe = null;
					createPipeline();
					closeFFmpegProcess();
					startFFmpegProcess();
					playThread();
					// writer.close();
					// if (outContainer.writeTrailer() < 0)
					// throw new RuntimeException();
					// outContainer.close();
					// System.exit(0);
				}
			}

		});
	}

	public void setVolume(String url, float volume) {
		volumeElements.get(url).set("volume", volume);
	}

	/**
	 * 
	 */
	public void play() {
		pipe.setState(State.PLAYING);
	}

	/**
	 * @return
	 */
	public Thread playThread() {
		Thread play = new Thread(new Runnable() {
			@Override
			public void run() {
				pipe.play();
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
				ffmpegProcess.getInputStream().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				ffmpegProcess.getOutputStream().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			ffmpegProcess.destroy();
			ffmpegProcess = null;
			ffmpegThread = null;
		}
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
}
