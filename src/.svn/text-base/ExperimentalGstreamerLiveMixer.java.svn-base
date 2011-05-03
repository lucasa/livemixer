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
import org.gstreamer.Buffer;
import org.gstreamer.Bus;
import org.gstreamer.Bus.STATE_CHANGED;
import org.gstreamer.Caps;
import org.gstreamer.Element;
import org.gstreamer.ElementFactory;
import org.gstreamer.GhostPad;
import org.gstreamer.Gst;
import org.gstreamer.GstObject;
import org.gstreamer.Pad;
import org.gstreamer.Pipeline;
import org.gstreamer.State;
import org.gstreamer.Structure;
import org.gstreamer.elements.DecodeBin2;

import com.xuggle.ferry.IBuffer;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IRational;
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
public class ExperimentalGstreamerLiveMixer {
	public static boolean DEBUG = false;

	public static boolean AUDIO_OUTPUT = false;

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

	public static void main(String[] args) {
		if (args.length < 2)
			throw new IllegalArgumentException(
					"Parameters: input_file_or_url_1 [input_file_or_url_2] ... [input_file_or_url_N] output_file_or_url");

		Gst.init("StreamAdder", new String[] { "--gst-debug-level=2" });

		List<String> inputUrls = new ArrayList<String>();
		for (int i = 0; i < args.length - 1; i++) {
			if (args[i].toLowerCase().equals("false") || args[i].toLowerCase().equals("true"))
				DEBUG = Boolean.valueOf(args[i]);
			else
				inputUrls.add(args[i]);
		}

		String outputUrl = args[args.length - 1];
		// LiveMixer adder = new LiveMixer("rtmp",
		// Collections.singletonList(inputUrls.get(0)), outputUrl);
		// inputUrls.remove(0);
		final ExperimentalGstreamerLiveMixer adder = new ExperimentalGstreamerLiveMixer("rtmp", inputUrls, outputUrl);
		adder.playThread();

		for (String url : inputUrls) {
			try {
				// Thread.sleep(20000);
				// adder.addNewInputURL(url);
				// Thread.sleep(20000);
				// adder.removeInput(url);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			public void run() {
				System.err.println("System is Shutting Down...");
				adder.getFfmpegProcess().destroy();
			}
		}));

		Gst.main();
	}

	public ExperimentalGstreamerLiveMixer(String name, List<String> inputURLs, final String outputURL) {
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
			adder = ElementFactory.make("adder", "liveadder");
		} catch (Exception e) {
			adder = ElementFactory.make("adder", "adder");
		}

		Element tee = ElementFactory.make("tee", "tee");

		Element capsfilter = ElementFactory.make("capsfilter", null);
		capsfilter.setCaps(capsAudio);
		Element queueTee = ElementFactory.make("queue", null);
		queueTee.set("max-size-time", 5 * 1000 * 1000);
		queueTee.set("min-threshold-time", 1 * 1000 * 1000);
		pipe.addMany(adder, queueTee, tee, capsfilter);
		Element.linkMany(adder, capsfilter, queueTee, tee);

		if (AUDIO_OUTPUT) {
			Element audioSink = ElementFactory.make("autoaudiosink", "Audio Sink");
			pipe.addMany(audioSink);
			Element.linkMany(tee, audioSink);
		}

		// outContainer = IContainer.make();
		// IContainerFormat outContainerFormat = IContainerFormat.make();
		// outContainerFormat.setOutputFormat("flv", outputURL, null);
		// int retval = outContainer.open(outputURL, IContainer.Type.WRITE,
		// null);
		// if (retval < 0)
		// throw new RuntimeException("could not open output file");
		// IStream outStream = outContainer.addNewStream(0);
		// outStreamCoder = outStream.getStreamCoder();
		// outStreamCoder.setCodec(codec);
		// outStreamCoder.setSampleRate(OUTPUT_FREQ);
		// outStreamCoder.setBitRate(64000);
		// outStreamCoder.setChannels(OUTPUT_CHANELS);
		// int retVal = outStreamCoder.open();
		// if (retVal < 0) {
		// debug("Could not open audio encoder");
		// }
		// outContainer.writeHeader();
		// if (retVal < 0) {
		// debug("Could not write output header: ");
		// }
		// writer = ToolFactory.makeWriter(outputURL, container);
		// writer.addAudioStream(0, 0, codec, OUTPUT_CHANELS, OUTPUT_FREQ);

		// fileFifo.deleteOnExit();
		fileFifo = new File(pathFifo);
		try {
			if (!fileFifo.exists()) {
				// fileFifo.delete();
				String command = "/usr/bin/mkfifo " + fileFifo.getAbsolutePath();
				ProcessBuilder b = new ProcessBuilder("/bin/sh", "-c", command);
				Runtime.getRuntime().exec(command.split(" ")).waitFor();
			}
			// String command = "/bin/sh -c mkfifo " +
			// fileFifo.getAbsolutePath();
		} catch (Exception e) {
			e.printStackTrace();
		}

		Element codecEnc = ElementFactory.make("lamemp3enc", "MP3 Encoder");
		Element mux = ElementFactory.make("flvmux", "FLV Muxer");
		Element queue = ElementFactory.make("queue", "Fifo Queue");
		Element filesink = ElementFactory.make("filesink", "Fifo Sink");
		filesink.set("sync", false);
		filesink.set("location", fileFifo.getAbsolutePath());
		// filesink.set("location", "/dev/null");
		pipe.addMany(queue, codecEnc, mux, filesink);
		Element.linkMany(tee, queue, codecEnc, mux, filesink);

		startFFmpegProcess();

		int i = 0;
		for (String url : inputURLs) {
			i++;
			if (checkUrl(url)) {
				addInput(url);
			}
		}

		// createNetworkOutput(tee);
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

	public boolean addNewInputURL(String url) {
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
		else
			command += " 2>" + fileFifo.getAbsolutePath() + "_log.log";
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
							bserr = new byte[error.available()];
							if (bserr.length > 0) {
								error.read(bserr);
								String error = new String(bserr);
								System.out.println(error);
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
						while (true) {
							bin = new byte[in.available()];
							if (bin.length > 0) {
								in.read(bin);
								String sin = new String(bin);
								System.out.println(sin);
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

	public static void debug(String string) {
		System.out.print("\n" + string);
	}

	private void addInput(String url) {
		System.out.println("\n\n\n------------\naddInput: " + url + "\n\n-------------\n");

		int i = inputElements.size();
		/* create audio output */
		final Bin audioBin = new Bin("Audio Bin" + i);

		Element src = null;
		if (url.contains("http://")) {
			src = ElementFactory.make("gnomevfssrc", "Input" + i);
			src.set("location", url);
		} else if (url.contains("rtmp") && url.contains("://")) {
			src = ElementFactory.make("rtmpsrc", "Input" + i);
			src.set("location", url);
		} else {
			src = ElementFactory.make("filesrc", "Input" + i);
			src.set("location", url);
		}

		DecodeBin2 decodeBin = new DecodeBin2("Decode Bin" + i);
		Element decodeQueue = ElementFactory.make("queue", "Decode Queue" + i);
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
					if (audioBin.getStaticPad("sink").getPeer() == null)
						pad.link(audioBin.getStaticPad("sink"));
				} else if (struct.getName().startsWith("video/")) {
					System.out.println("Linking video pad: " + struct.getName());
				} else {
					System.out.println("Unknown pad [" + struct.getName() + "]");
				}
			}
		});

		Element conv = ElementFactory.make("audioconvert", "Audio Convert" + i);
		Element resample = ElementFactory.make("audioresample", "Audio Resample" + i);
		Element volume = ElementFactory.make("volume", "Audio Volume" + i);
		volume.set("volume", 1.0f);
		volumeElements.put(url, volume);
		audioBin.addMany(conv, resample, volume);
		Element.linkMany(conv, resample, volume);
		audioBin.addPad(new GhostPad("src", volume.getStaticPad("src")));
		audioBin.addPad(new GhostPad("sink", conv.getStaticPad("sink")));

		Bin inputBin = new Bin("Input Bin" + i);
		inputBin.addMany(src, decodeQueue, decodeBin, audioBin);
		Element.linkMany(src, decodeQueue, decodeBin, audioBin);
		inputBin.addPad(new GhostPad("sink", audioBin.getSrcPads().get(0)));

		pipe.addMany(inputBin);
		Pad adderSink = adder.getRequestPad("sink%d");
		inputBin.getSrcPads().get(0).link(adderSink);

		inputElements.put(url, inputBin);

		if (pipe.isPlaying()) {
			src.setState(State.PLAYING);
			decodeQueue.setState(State.PLAYING);
			decodeBin.setState(State.PLAYING);
			audioBin.setState(State.PLAYING);
			volume.setState(State.PLAYING);
			adder.setState(State.PLAYING);
		}
	}

	public void removeInput(String url) {
		System.out.println("\n\n\n------------\nremoveInput: " + url + "\n\n-------------\n");
		adder.setState(State.PAUSED);
		Element input = inputElements.get(url);
		input.setState(State.PAUSED);
		Pad padSrc = input.getSrcPads().get(0);
		padSrc.setBlocked(true);
		input.setState(State.NULL);
		Pad padSink = adder.getSinkPads().get(0);
		padSrc.unlink(padSink);
		adder.releaseRequestPad(padSink);
		inputElements.remove(url);
		adder.setState(State.PLAYING);
		// EVENT_PROBE probeListener = new EVENT_PROBE() {
		// // @Override
		// public boolean eventReceived(Pad pad, Event event) {
		// String type = event.getStructure().toString();
		// if (DEBUG)
		// System.out.println("eventReceived: " + type);
		// if (type.toLowerCase().indexOf("eos") >= 0) {
		// System.out.println();
		// }
		// return true;
		// }
		// };
		//
		// // force oldE to flow all his remain data
		// oldE.getPad("src").addEventProbe(probeListener);
		// oldE.sendEvent(new EOSEvent());
		// oldE.getPad("src").removeEventProbe(probeListener);
	}

	// public void createNetworkOutput(Element tee) {
	// try {
	// Element queue = ElementFactory.make("queue", "queue");
	// final AppSink appsink = (AppSink) ElementFactory.make("appsink",
	// "appsink");
	// appsink.set("emit-signals", true);
	// appsink.set("sync", false);
	// appsink.connect(new AppSink.NEW_BUFFER() {
	// @Override
	// public void newBuffer(Element element, Pointer arg1) {
	// handdle(appsink.pullBuffer());
	// }
	// });
	// pipe.addMany(queue, appsink);
	// Element.linkMany(tee, queue, appsink);
	// } catch (Exception e) {
	// Element queue = ElementFactory.make("queue", "queue");
	// final FakeSink fakesink = (FakeSink) ElementFactory.make("fakesink",
	// "fakesink");
	// fakesink.set("signal-handoffs", true);
	// fakesink.set("sync", false);
	// fakesink.connect(new HANDOFF() {
	// @Override
	// public void handoff(Element element, Buffer buffer, Pad arg2) {
	// handdle(buffer);
	// }
	// });
	// fakesink.connect(new FakeSink.PREROLL_HANDOFF() {
	// @Override
	// public void prerollHandoff(FakeSink fakesink, Buffer buffer, Pad pad,
	// Pointer arg3) {
	// handdle(buffer);
	// }
	//
	// });
	// pipe.addMany(queue, fakesink);
	// Element.linkMany(tee, queue, fakesink);
	// }
	// }

	public void handdle(Buffer buffer) {
		if (buffer != null && outputStream != null) {
			byte[] bytes = new byte[buffer.getByteBuffer().limit()];
			buffer.getByteBuffer().get(bytes);
			// if (DEBUG)
			// System.out.println("new buffer[" + buffer.hashCode() + "]: " +
			// buffer.getSize());
			// IBuffer ibuffer = IBuffer.make(null, bytes, 0, buffer.getSize());
			// IAudioSamples samples = IAudioSamples.make(ibuffer,
			// OUTPUT_CHANELS, Format.FMT_S16);
			// samples.setComplete(true, buffer.getSize(), OUTPUT_FREQ,
			// OUTPUT_CHANELS, IAudioSamples.Format.FMT_S16,
			// Global.NO_PTS);
			// // writer.encodeAudio(0, byte2short(bytes));
			// writeToOuputStream(lastPos, samples);
			try {
				outputStream.write(bytes);
				// outputStream.flush();
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(0);
			}
		}
	}

	protected int writeToOuputStream(int lastPos, IAudioSamples samples) {
		IBuffer buffer = samples.getData();
		IPacket packet_out = IPacket.make(buffer);
		packet_out.setKeyPacket(true);
		packet_out.setTimeBase(IRational.make(1, 1000));
		// packet_out.setDuration(decodedSamples.);
		// packet_out.setDts(audioFrameCnt * 20);
		// packet_out.setPts(audioFrameCnt * 20);
		// audioFrameCnt++;
		int pksz = packet_out.getSize();
		packet_out.setComplete(true, pksz);
		int samplesConsumed = 0;
		while (samplesConsumed < samples.getNumSamples()) {
			int retVal = outStreamCoder.encodeAudio(packet_out, samples, samplesConsumed);
			if (retVal <= 0)
				throw new RuntimeException("Could not encode audio");
			samplesConsumed += retVal;
			if (packet_out.isComplete()) {
				packet_out.setPosition(lastPos);
				packet_out.setStreamIndex(0);
				lastPos += packet_out.getSize();
				retVal = outContainer.writePacket(packet_out);
				// if (retVal <= 0)
				// throw new RuntimeException("Could not write audio");
			}
		}
		return lastPos;
	}

	public void prepareBus() {
		Bus bus = pipe.getBus();

		bus.connect(new STATE_CHANGED() {
			@Override
			public void stateChanged(GstObject source, State old, State current, State pend) {
				if (source == pipe) {
					System.out.println("Pipeline new STATE: " + current);
					if (old == State.PLAYING && current != State.PLAYING) {
						pipe.setState(State.NULL);
						pipe = null;
						inputElements.clear();
						closeFFmpegProcess();
						createPipeline();
						playThread();
					}
				}
			}
		});

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
