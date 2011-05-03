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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import com.xuggle.ferry.IBuffer;
import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.IAudioResampler;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IContainerFormat;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IAudioSamples.Format;
import com.xuggle.xuggler.IContainer.Type;

/**
 * Takes multiples media files or URLs, decodes the audio streams, and then mix
 * it together.
 */
public class XugglerLiveMixer {
	private static int ALL_SOURCES_LIVE = 0;

	final static boolean DEBUG = true;

	protected static final boolean ENABLE_OUTPUT_STREAM = true;

	private static final boolean ENABLE_LOCAL_AUDIO_OUTPUT = false;

	private static final short OUTPUT_BUFFERING_SLEEP_TIME = 5000;

	private static final int OUTPUT_CHANELS = 2;

	private static final int OUTPUT_FREQ = 44100;

	private static final boolean REALTIME = true;

	private static int MAX_MIX_BUFFER = 1000;

	private static int MAX_DECODERS_BUFFER = MAX_MIX_BUFFER;

	private static int DECODE_THREAD_INITIAL_BUFFERING_SIZE = MAX_DECODERS_BUFFER;

	private static int MIN_BUFFER_MIX = 10;

	private static int MIN_DECODER_BUFFER = 10;

	/**
	 * Audio output
	 */
	static SourceDataLine mLine = null;

	/**
	 * Volume of each input media.
	 */
	private static Map<String, Float> volume = new HashMap<String, Float>();
	/**
	 * Audio decoder of each input.
	 */
	private static Map<String, IStreamCoder> audioCoders = new HashMap<String, IStreamCoder>();
	/**
	 * Buffers of decoded samples.
	 */
	private static Map<String, List<IAudioSamples>> audioSamples = new HashMap<String, List<IAudioSamples>>();
	/**
	 * Audio resamplers of each input.
	 */
	private static Map<String, IAudioResampler> audioResamplers = new HashMap<String, IAudioResampler>();
	/**
	 * Buffer of mixed samples.
	 */
	protected static List<IAudioSamples> MIXED_SAMPLES = new Vector<IAudioSamples>();

	private static List<String> INPUT_URLs = new ArrayList<String>();
	/**
	 * The path or URL to output.
	 */
	private static String OUTPUT_URL;
	/**
	 * The thread that do the mixing work.
	 */
	private static Thread mixerThread;
	/**
	 * The thread that write the mixed samples to the output.
	 */
	private static Thread outputThread;

	private static String lastDebug = "";

	@SuppressWarnings("deprecation")
	public static void main(String[] args) {
		if (args.length < 2)
			throw new IllegalArgumentException(
					"Parameters: input_file_or_url_1 [input_file_or_url_2] ... [input_file_or_url_N] output_file_or_url");

		// String filename1 =
		// "http://tv.softwarelivre.org/sites/default/files/videos/41c-24-07-2010_09-01-39.ogg";
		// filename1 = "/home/00412804581/Desktop/41b-21-07-2010_13-05-07.ogg";
		// filename1 = "/home/lucasa/reflexoes-paradoxais-VERSAO-FINAL.ogg";
		// String filename2 =
		// "http://tv.softwarelivre.org/sites/default/files/videos/41c-23-07-2010_09-05-43.ogg";
		// String filename3 =
		// "/home/00412804581/Desktop/41b-21-07-2010_13-05-07.ogg";
		// filename2 =
		// "/home/lucasa/05 Passarinho da Lagoa, Chuva Chovendo.mp3";

		for (int i = 0; i < args.length - 1; i++) {
			INPUT_URLs.add(args[i]);
			volume.put(args[i], 1.0f);
		}
		OUTPUT_URL = args[args.length - 1];

		try {
			// createOutput("rtmp://localhost/out.flv");
			createOutputStream(OUTPUT_URL);

			for (String input : INPUT_URLs) {
				Thread decoder = decodeSourceStream(input);
				decoder.setPriority(Thread.MAX_PRIORITY);
				decoder.start();
			}

			mixerThread = createMixerThread();
			mixerThread.setPriority(Thread.MIN_PRIORITY);
			mixerThread.start();

			outputThread = createOutputStreamThread();
			outputThread.setPriority(Thread.MIN_PRIORITY);
			outputThread.start();
			outputThread.join();

			closeOutputStream();

		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void closeOutputStream() {
		if (outContainer.writeTrailer() < 0)
			throw new RuntimeException();
		outContainer.close();
	}

	public static Thread createMixerThread() {
		Runnable process = new Runnable() {

			public void run() {
				Map<String, IAudioSamples> map = new HashMap<String, IAudioSamples>();
				boolean doMix = true;
				while (ALL_SOURCES_LIVE > 0) {
					int bufferSize = MIXED_SAMPLES.size();
					if (doMix) {
						if (bufferSize < MAX_MIX_BUFFER) {
							map.clear();
							/* long timestamp = */readInputSamplesBuffers(map);
							if (!map.isEmpty()) {
								try {
									IBuffer buffer = mixAudioBuffers(map);
									IAudioSamples samples = IAudioSamples.make(buffer, OUTPUT_CHANELS, Format.FMT_S16);
									samples.setComplete(true, buffer.getSize(), OUTPUT_FREQ, OUTPUT_CHANELS,
											IAudioSamples.Format.FMT_S16, Global.NO_PTS);
									// samples.setTimeStamp(timestamp);

									MIXED_SAMPLES.add(samples);
									if (MIXED_SAMPLES.size() % 10 == 0) {
										if (DEBUG)
											debug("MIXER BUFFER: " + bufferSize);
									}
									// debug(samples);
									// playJavaSound(samples);
									// if(outputThread.w != null)
									// outputThread.notify();
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						} else {
							do {
								debug("* MIXER BUFFER FULL: " + bufferSize);
								try {
									Thread.sleep(5000);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								System.gc();
								bufferSize = MIXED_SAMPLES.size();
							} while (bufferSize > MAX_MIX_BUFFER * (2 / 3)); // 66%
						}
					}

					doMix = true;
					if (bufferSize > MIN_BUFFER_MIX) {
						int aver = getAverageDecodersBuffersSize();
						if (aver < MIN_DECODER_BUFFER)
						// if (aver < MAX_DECODERS_BUFFER * 0.3)
						{
							doMix = false;
							if (DEBUG)
								debug("* LET ENCODERs DO SOME BUFFERING, IT HAS AVERAGE: " + aver);
						}
					}

				}
			}
		};
		Thread mix = new Thread(process);
		return mix;
	}

	protected static int getAverageDecodersBuffersSize() {
		int n = audioSamples.size();
		int sum = 0;
		if (n > 0) {
			synchronized (audioSamples) {
				for (List buffers : audioSamples.values()) {
					int s = buffers.size();
					sum += s;
				}
			}
			return sum / n;
		} else
			return 0;
	}

	protected static void debug(String string) {
		if (!lastDebug.equals(string)) {
			lastDebug = string;
			System.out.print("\n" + string);
		}
	}

	public static Thread createOutputStreamThread() {
		Runnable play = new Runnable() {

			public void run() {
				int lastPos = 0;
				/*
				 * try { long delay = BUFFERING_DELAY;// INPUT_URLs.size();
				 * debug("Buffering " + delay / 1000 + "s");
				 * Thread.sleep(delay); } catch (InterruptedException e1) {
				 * e1.printStackTrace(); }
				 */
				List<IAudioSamples> toPlay = new ArrayList<IAudioSamples>();
				while (ALL_SOURCES_LIVE > 0) {
					int n = MIXED_SAMPLES.size();
					if (n > MIN_BUFFER_MIX) {
						// List<IAudioSamples> toPlay = new
						// ArrayList<IAudioSamples>(MIXED_SAMPLES.subList(0,
						// n));
						toPlay.clear();
						toPlay.addAll(MIXED_SAMPLES);
						for (IAudioSamples samples : toPlay) {
							if (ENABLE_OUTPUT_STREAM)
								lastPos = writeToOuputStream(lastPos, samples);
							if (ENABLE_LOCAL_AUDIO_OUTPUT)
								playJavaSound(samples);
						}

						MIXED_SAMPLES.removeAll(toPlay);
						debug("played: " + toPlay.size());

					} else {
						do {
							// System.out.print('.');
							if (DEBUG)
								debug("Output thread going to sleep, because MIX buffer is: " + n);
							try {
								Thread.sleep(OUTPUT_BUFFERING_SLEEP_TIME);
							} catch (Exception e) {
								e.printStackTrace();
							}
							n = MIXED_SAMPLES.size();
						} while (n < MIN_BUFFER_MIX);
					}
				}
			}
		};
		Thread thread = new Thread(play);
		return thread;
	}

	/**
	 * @param url
	 * @return
	 */
	protected static Thread decodeSourceStream(final String url) {
		ALL_SOURCES_LIVE++;
		// Create a Xuggler container object
		final IContainer container = IContainer.make();

		// Open up the container
		if (container.open(url, IContainer.Type.READ, null) < 0)
			throw new IllegalArgumentException("could not open file: " + url);

		// query how many streams the call to open found
		int numStreams = container.getNumStreams();
		int audioStreamId = -1;
		IStreamCoder audioCoder = null;
		for (int i = 0; i < numStreams; i++) {
			// Find the stream object
			IStream stream = container.getStream(i);
			// Get the pre-configured decoder that can decode this stream;
			IStreamCoder coder = stream.getStreamCoder();
			if (audioStreamId == -1 && coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO) {
				audioStreamId = i;
				audioCoder = coder;
			}

		}
		if (audioStreamId == -1)
			throw new RuntimeException("could not find audio stream in container: " + url);

		Thread thread = null;
		if (audioCoder != null) {
			if (audioCoder.open() < 0)
				throw new RuntimeException("could not open audio decoder for container: " + url);

			synchronized (audioSamples) {
				audioSamples.put(url, new ArrayList<IAudioSamples>());
			}

			try {
				// only create the soundboard output once
				// set the Java Sound System to get itself ready.
				if (ENABLE_LOCAL_AUDIO_OUTPUT && mLine == null)
					openJavaSound(audioCoder);
			} catch (Exception ex) {
				ex.printStackTrace();
			}

			final int audioId = audioStreamId;
			final IStreamCoder aCoder = audioCoder;
			Runnable process = new Runnable() {
				public void run() {
					try {
						decodePackets(url, container, audioId, aCoder);
						closeAllOpenReaders(url, container, aCoder);
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						ALL_SOURCES_LIVE--;
						synchronized (audioSamples) {
							audioSamples.remove(url);
						}
						debug("Exiting decoder thread: " + url);
					}
				}
			};
			thread = new Thread(process);
		}

		return thread;
	}

	/**
	 * 
	 * @param filename
	 * @param container
	 * @param audioCoder
	 */
	protected static void closeAllOpenReaders(String filename, IContainer container, IStreamCoder audioCoder) {
		// clean up
		if (audioCoder != null) {
			audioCoder.close();
			audioCoder = null;
		}
		if (container != null) {
			container.close();
			container = null;
		}

		closeJavaSound(filename);
	}

	/**
	 * @param url
	 * @param container
	 * @param audioStreamId
	 * @param audioCoder
	 */
	protected static void decodePackets(String url, IContainer container, int audioStreamId, IStreamCoder audioCoder) {
		debug("Audio decoder buffering: " + url);
		boolean buffering = true;
		boolean enableRealtime = false;
		List<IAudioSamples> initial_buffer = new ArrayList<IAudioSamples>();
		IPacket packet = IPacket.make();
		long totalBytesReaded = 0;
		while (container.readNextPacket(packet) >= 0) {
			/* Now we have a packet, let's see if it belongs to a audio stream */
			if (packet.getStreamIndex() == audioStreamId) {
				if (REALTIME && !buffering && enableRealtime)
					delayForRealTime(packet);

				int bufferSize = audioSamples.get(url).size();

				IAudioSamples samples = IAudioSamples.make(1024, audioCoder.getChannels());
				/*
				 * A packet can actually contain multiple sets of samples (or
				 * frames of samples in audio-decoding speak). So, we may need
				 * to call decode audio multiple times at different offsets in
				 * the packet's data. We capture that here.
				 */
				int offset = 0;

				/*
				 * Keep going until we've processed all data
				 */
				while (offset < packet.getSize()) {
					int bytesDecoded = audioCoder.decodeAudio(samples, packet, offset);
					if (bytesDecoded < 0)
						throw new RuntimeException("got error decoding audio in: " + url);
					offset += bytesDecoded;
					/*
					 * Some decoder will consume data in a packet, but will not
					 * be able to construct a full set of samples yet. Therefore
					 * you should always check if you got a complete set of
					 * samples from the decoder
					 */
					if (samples.isComplete()) {
						// debug("Total samples of " + url +
						// ": " + audioSamples.get(url).size());
						if (buffering) {
							initial_buffer.add(samples);
						} else {
							// if (DEBUG && bufferSize % 50 == 0)
							// debug("SAMPLES_BUFFER: " +
							// bufferSize);// +" -> ");
							synchronized (audioSamples) {
								audioSamples.get(url).add(samples);
							}
						}
					}
				}
				totalBytesReaded += offset;

				if (buffering && initial_buffer.size() >= DECODE_THREAD_INITIAL_BUFFERING_SIZE) {
					synchronized (audioSamples) {
						audioSamples.get(url).addAll(initial_buffer);
					}
					buffering = false;
					initial_buffer = null;
					System.gc();
					debug("Decoding thread initial buffer is full :" + url);
				}

				while (bufferSize > MAX_DECODERS_BUFFER) {
					debug("* DECODER BUFFER FULL: " + bufferSize + " - " + url);
					System.gc();
					enableRealtime = true;
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					bufferSize = audioSamples.get(url).size();
				}

				if (bufferSize > 0 && bufferSize < MIN_DECODER_BUFFER) {
					if (DEBUG)
						debug("* DECODER BUFFER EMPTY: " + bufferSize);
					enableRealtime = false;
				} else
					enableRealtime = true;
			}
		}

		debug("Total bytes of container : " + container.getFileSize() + " - " + url);
		debug("Total bytes readed: " + totalBytesReaded + " - " + url);
	}

	private static void openJavaSound(IStreamCoder aAudioCoder) throws LineUnavailableException {
		AudioFormat audioFormat = new AudioFormat(OUTPUT_FREQ, (int) IAudioSamples.findSampleBitDepth(Format.FMT_S16),
				OUTPUT_CHANELS, true, false);
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
		SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
		mLine = line;
		line.open(audioFormat);
		line.start();
	}

	private static void playJavaSound(IAudioSamples aSamples) {
		if (mLine != null) {
			byte[] rawBytes = aSamples.getData().getByteArray(0, aSamples.getSize());
			if (rawBytes.length > 0)
				mLine.write(rawBytes, 0, rawBytes.length);
		}
	}

	private static void closeJavaSound(String url) {
		if (mLine != null) {
			mLine.drain();
			mLine.close();
			mLine = null;
		}
	}

	protected static IBuffer mixAudioBuffers(Map<String, IAudioSamples> samplesList) {
		int n = samplesList.size();
		IBuffer ibuffer = null;
		if (n > 1) {
			int min = Integer.MAX_VALUE;
			for (IAudioSamples samples : samplesList.values()) {
				// Format format = samples.getFormat();
				// int rate = samples.getSampleRate();
				// long depth = samples.getSampleBitDepth();
				// int ns = (int) samples.getNumSamples();
				// int s = samples.getSize();
				// long ss = samples.getSampleSize();
				// int l = samples.getByteBuffer().limit();
				int d = samples.getByteBuffer().limit();
				if (d < min)
					min = d;
			}

			short[] mix = new short[min >> 1];
			byte[] bufferb = new byte[min];
			boolean first = true;
			// Arrays.fill(mix, (short) 0);
			for (String url : samplesList.keySet()) {
				IAudioSamples samples = samplesList.get(url);
				// get the raw audio bytes and adjust it's value
				// bufferb = new byte[samples.getData().getSize()];
				// bytes = new byte[samples.getSize()];
				// samples.getByteBuffer().get(bytes);
				try {
					samples.getByteBuffer().get(bufferb, 0, min);
					short[] buffer = byte2short(bufferb);
					for (int i = 0; i < buffer.length; ++i) {
						// short vv = (short) (buffer[i] * volume.get(url));
						short vv = buffer[i];
						if (vv > Short.MAX_VALUE)
							vv = Short.MAX_VALUE;
						if (vv < Short.MIN_VALUE)
							vv = Short.MIN_VALUE;
						if (first) {
							mix[i] = (short) (vv / n);
						} else {
							mix[i] = (short) (mix[i] + (vv / n));
						}
						// debug(v + " * " + mVolume + " = " + s);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				first = false;
			}

			bufferb = short2byte(mix);
			// IAudioSamples sample = samplesList.values().iterator().next();
			// IBuffer buffer = IBuffer.make(null, sample.getByteBuffer(), 0,
			// sample.getSize());
			ibuffer = IBuffer.make(null, bufferb, 0, min);
		} else {
			ibuffer = samplesList.values().iterator().next().getData();
		}

		ibuffer.setType(IBuffer.Type.IBUFFER_SINT16);
		return ibuffer;
	}

	private static IContainer outContainer;

	private static IContainerFormat outContainerFormat;

	private static IStream outAudioStream;

	private static IStreamCoder outAudioEncoder;

	private static Long mStartStreamTime = null;

	private static Long mStartClockTime = null;

	private static String OUTPUT_FORMAT = "mp3"; //ffmpeg's short name

	private static boolean createOutputStream(String urlOut) {
		outContainer = IContainer.make();
		outContainerFormat = IContainerFormat.make();
		outContainerFormat.setOutputFormat(OUTPUT_FORMAT, urlOut, null);
		int retVal = outContainer.open(urlOut, Type.WRITE, outContainerFormat);
		if (retVal < 0) {
			debug("Could not open output container");
			return false;
		}

		outAudioStream = outContainer.addNewStream(0); // add only audio
		outAudioEncoder = outAudioStream.getStreamCoder();
		outAudioEncoder.setCodec(ICodec.ID.CODEC_ID_MP3);
		outAudioEncoder.setSampleRate(OUTPUT_FREQ);
		outAudioEncoder.setChannels(OUTPUT_CHANELS);
		retVal = outAudioEncoder.open();
		if (retVal < 0) {
			debug("Could not open audio encoder");
			return false;
		}

		retVal = outContainer.writeHeader();
		if (retVal < 0) {
			debug("Could not write output header: ");
			return false;
		}
		return true;
	}

	public static byte[] short2byte(short[] shortArray) {
		if (shortArray == null) {
			return null;
		} else {
			byte[] byteArray = new byte[shortArray.length * 2];
			for (int i = 0; i < shortArray.length; i++) {
				short anChar = shortArray[i];
				byteArray[i * 2] = (byte) (anChar & 0xFF);
				byteArray[i * 2 + 1] = (byte) ((anChar >>> 8) & 0xFF);
			}
			return byteArray;
		}
	}

	public static short[] byte2short(byte[] byteArray) {
		if (byteArray == null) {
			return null;
		} else {
			short[] shortArray = new short[byteArray.length / 2];
			for (int i = 0; i < byteArray.length; i = i + 2) {
				shortArray[i / 2] = (short) ((byteArray[i] & 0xFF) + ((((short) byteArray[i + 1]) << 8) & 0xFF00));
			}
			return shortArray;
		}
	}

	/**
	 * Force a realtime processing based on the timebase of the media.
	 * 
	 * @param oPacket
	 *            the packet that has timing information.
	 */
	public static void delayForRealTime(IPacket oPacket) {
		// convert packet timestamp to microseconds
		final IRational timeBase = oPacket.getTimeBase();
		if (timeBase == null || timeBase.getNumerator() == 0 || timeBase.getDenominator() == 0)
			return;
		long dts = oPacket.getDts();
		if (dts == Global.NO_PTS)
			return;

		final long currStreamTime = IRational.rescale(dts, 1, 1000000, timeBase.getNumerator(), timeBase
				.getDenominator(), IRational.Rounding.ROUND_NEAR_INF);

		// convert now to microseconds
		final long currClockTime = System.nanoTime() / 1000;

		if (mStartStreamTime == null)
			mStartStreamTime = currStreamTime;
		if (mStartClockTime == null)
			mStartClockTime = currClockTime;

		final long currClockDelta = currClockTime - mStartClockTime;
		if (currClockDelta < 0)
			return;
		final long currStreamDelta = currStreamTime - mStartStreamTime;
		if (currStreamDelta < 0)
			return;
		final long streamToClockDeltaMilliseconds = (currStreamDelta - currClockDelta) / 1000;
		if (streamToClockDeltaMilliseconds <= 0)
			return;
		try {
			Thread.sleep(streamToClockDeltaMilliseconds);
		} catch (InterruptedException e) {
		}
	}

	public static long readInputSamplesBuffers(Map<String, IAudioSamples> map) {
		long timestamp = 0;
		IAudioSamples decodedSamples = null;
		synchronized (audioSamples) {
			for (String url : audioSamples.keySet()) {
				decodedSamples = null;
				if (!audioSamples.get(url).isEmpty()) {
					// debug("URLs: "+audioSamples.keySet().size());
					synchronized (audioSamples) {
						decodedSamples = audioSamples.get(url).remove(0);
					}
					if (decodedSamples != null) {
						timestamp = decodedSamples.getTimeStamp();
						if (!audioResamplers.containsKey(url))
							audioResamplers.put(url, IAudioResampler.make(OUTPUT_CHANELS, decodedSamples.getChannels(),
									OUTPUT_FREQ, decodedSamples.getSampleRate()));
						IAudioSamples resampled = IAudioSamples.make(decodedSamples.getNumSamples(), OUTPUT_CHANELS);
						audioResamplers.get(url).resample(resampled, decodedSamples, decodedSamples.getNumSamples());
						map.put(url, resampled);
						// playJavaSound(resampled);
					}
				}

				// if had problem when populating the map, get out
				if (decodedSamples == null) {
					// if(DEBUG)
					// debug("Could not decode all sources at same time, try later.");
					map.clear();
					return timestamp;
				}
			}
		}
		return timestamp;
	}

	public static int writeToOuputStream(int lastPos, IAudioSamples samples) {
		IBuffer buffer = samples.getData();
		IPacket packet_out = IPacket.make(buffer);
		packet_out.setKeyPacket(true);
		packet_out.setTimeBase(IRational.make(1, 1000));
		// packet_out.setDuration(decodedSamples.);
		// packet_out.setDts(audioFrameCnt * 20);
		// packet_out.setPts(audioFrameCnt * 20);
		// audioFrameCnt++;
		// int pksz = packet.getSize();
		// packet.setComplete(true, pksz);

		int samplesConsumed = 0;
		while (samplesConsumed < samples.getNumSamples()) {
			int retVal = outAudioEncoder.encodeAudio(packet_out, samples, samplesConsumed);
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

	/**
	 * Print information about a media container.
	 * @param container
	 * @param coder
	 */
	private static void printMediaInfo(IContainer container, IStreamCoder coder) {
		debug("*** Start of Stream Info ***");
		System.out.printf("type: %s; ", coder.getCodecType());
		System.out.printf("codec: %s; ", coder.getCodecID());
		System.out.printf("duration: %s; ", container.getDuration());
		System.out.printf("start time: %s; ", container.getStartTime());
		System.out
				.printf("coder tb: %d/%d; ", coder.getTimeBase().getNumerator(), coder.getTimeBase().getDenominator());
		debug("");
		System.out.printf("sample rate: %d; ", coder.getSampleRate());
		System.out.printf("channels: %d; ", coder.getChannels());
		System.out.printf("format: %s", coder.getSampleFormat());

	}
}
