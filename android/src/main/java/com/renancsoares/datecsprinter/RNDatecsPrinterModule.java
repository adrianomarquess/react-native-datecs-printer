package com.renancsoares.datecsprinter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.datecs.api.printer.Printer;
import com.datecs.api.printer.ProtocolAdapter;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RNDatecsPrinterModule extends ReactContextBaseJavaModule implements LifecycleEventListener{

	// Debugging
	private static final Boolean DEBUG = true;
	private static final String LOG_TAG = "RNDatecsPrinterModule";

	//Members
	private Printer mPrinter;
	private ProtocolAdapter mProtocolAdapter;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothSocket mmSocket;
	private BluetoothDevice mmDevice;
	private OutputStream mmOutputStream;
	private InputStream mmInputStream;

	private final ProtocolAdapter.ChannelListener mChannelListener = new ProtocolAdapter.ChannelListener(){
		@Override
		public void onReadEncryptedCard() {
            // TODO: onReadEncryptedCard
		}

		@Override
		public void onReadCard() {
            // TODO: onReadCard
		}

		@Override
		public void onReadBarcode() {
            // TODO: onReadBarcode
		}

		@Override
		public void onPaperReady(boolean state) {
			if (state) {
				showToast("Papel Ok");
			} else {
				disconnect(null);
				showToast("Sem Papel");
			}
		}

		@Override
		public void onOverHeated(boolean state) {
			if (state) {
				showToast("Superaquecimento");
			}
		}
		// 6ca1a08e05c9439bbb6c2825ae7fdec4
		@Override
		public void onLowBattery(boolean state) {
			if (state) {
				showToast("Pouca Bateria");
			}
		}
	};

	public RNDatecsPrinterModule(ReactApplicationContext reactContext) {
		super(reactContext);

		reactContext.addLifecycleEventListener(this);
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	@Override
	public void onHostResume() {
	    // Activity `onResume`
	}

	@Override
	public void onHostPause() {
		disconnect(null);
	}

	@Override
	public void onHostDestroy() {
		disconnect(null);
	}

	@Override
	public String getName() {
		return "DatecsPrinter";
	}

	/**
	 * Get list of paired devices
	 *
	 * @param promise
	 */
	@ReactMethod
	public void getPairedDevices(Promise promise){
		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
		// it might need to be an react.bridge.WritableArray
		ArrayList list = new ArrayList();
		for(BluetoothDevice device : pairedDevices){
			list.add(device);
		}

		if(list.size() > 0){
			promise.resolve(list);
		}else{
			promise.reject("Nenhum dispositivo pareado.");
		}
	}

	/**
	 * Get list of unpaired devices
	 *
	 * @param promise
	 */
	@ReactMethod
	public void getUnpairedDevices(Promise promise){

	}

	/**
     * Start bluetooth and print connection
     *
     * @param device
     * @param promise
     */
	@ReactMethod
	public void connect(Promise promise) throws IOException {
		try {
			Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
			ArrayList list = new ArrayList();
			for(BluetoothDevice device : pairedDevices){
				list.add(device);
			}

			//need to return list of paired devices
			if(list.size() > 0){
				mmDevice = (BluetoothDevice) list.get(0);
			}else{
				showToast("Nenhum dispositivo pareado.");
				return;
			}

			// Standard SerialPortService ID
			UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
			mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
			mmSocket.connect();
			mmOutputStream = mmSocket.getOutputStream();
			mmInputStream = mmSocket.getInputStream();

			try{
				initializePrinter(mmInputStream, mmOutputStream, promise);
			}catch(Exception e){
				promise.reject("Erro: " + e.getMessage());
				return;
			}

			// promise.resolve("BLUETOOTH CONNECTED");
		}catch(Exception e){
			promise.reject("Erro: " + e.getMessage());
		}
	}

	/**
     * Connect printer
     *
     * @param promise
     */
	protected void initializePrinter(InputStream inputStream, OutputStream outputStream, Promise promise) throws IOException {
		mProtocolAdapter = new ProtocolAdapter(inputStream, outputStream);
		if (mProtocolAdapter.isProtocolEnabled()) {
			final ProtocolAdapter.Channel channel = mProtocolAdapter.getChannel(ProtocolAdapter.CHANNEL_PRINTER);

			// it was causing errors, need to be reviewed
            // channel.setListener(mChannelListener);

            // Create new event pulling thread
			new Thread(new Runnable() {
				@Override
				public void run() {
					while (true) {
						try {
							Thread.sleep(50);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						try {
							channel.pullEvent();
						} catch (IOException e) {
							e.printStackTrace();
							break;
						}
					}
				}
			}).start();
			mPrinter = new Printer(channel.getInputStream(), channel.getOutputStream());
		} else {
			mPrinter = new Printer(mProtocolAdapter.getRawInputStream(), mProtocolAdapter.getRawOutputStream());
		}
		promise.resolve("PRINTER_INITIALIZED");
	}

	/**
     * Get Printer status
     *
     * @param promise
     */
	@ReactMethod
	public void getStatus(Promise promise) {
		try {
			int status = mPrinter.getStatus();
			promise.resolve(status);
		} catch (Exception e) {
			promise.reject("Erro: " + e.getMessage());
		}
	}

	/**
     * Feed paper to the printer (roll out blank paper)
     *
     * @param linesQuantity
     * @param promise
     */
	@ReactMethod
	public void feedPaper(int linesQuantity, Promise promise) {
		if (linesQuantity < 0 || linesQuantity > 255) {
			promise.reject("AMOUNT_LINES_0_255");
			return;
		}
		try {
			mPrinter.feedPaper(linesQuantity);
			mPrinter.flush();

			promise.resolve("PAPER_FED");
		} catch (Exception e) {
			promise.reject("Erro: " + e.getMessage());
		}
	}

	/**
     * Print self test
     *
     * @param promise
     */
	@ReactMethod
	public void printSelfTest(Promise promise) {
		try {
			mPrinter.printSelfTest();
			mPrinter.flush();

			promise.resolve("SELF_TEST_PRINTED");
		} catch (Exception e) {
			promise.reject("Erro: " + e.getMessage());
		}
	}

	/**
     * Print custom text
     *
     * @param text
     * @param promise
     */
	@ReactMethod
	public void printText(String text, Promise promise) {
		String charset = "ISO-8859-1";
		try {
			mPrinter.printTaggedText(text, charset);
			mPrinter.flush();

			promise.resolve("PRINTED");
		} catch (Exception e) {
			promise.reject(e);
		}
	}

  @ReactMethod
  public void printTemplate(String template, Promise promise) throws IOException {
    try {
      String imageRegex = "\\{image\\=([0-9a-zA-Z\\+/=]{20,})\\}";

      String[] templatesText = template.split(imageRegex);

      List<String> tagsImagens = getImagesTags(template, imageRegex);
      String[] imagesSource = getImagesSources(tagsImagens);

      int i=0;
      int imagesArraySize = imagesSource.length;

      for(String templateTexto: templatesText) {
        if(!templateTexto.isEmpty()) {
          mPrinter.printTaggedText(templateTexto,"ISO-8859-1");
          mPrinter.flush();
        }
        if(i<imagesArraySize) {
          printImage(imagesSource[i]);
          mPrinter.flush();
        }
        i++;
      }

      promise.resolve("PRINTED");

    } catch (Exception e) {
      promise.reject(e);
    }
  }

  private String[] getImagesSources(List<String> tagsImagens) {

    List<String> images = new ArrayList<>();

    for (String tagImage : tagsImagens) {
      images.add(tagImage.substring(tagImage.indexOf("=")+1,tagImage.indexOf("}")));
    }

    String[] imagesArray = new String[images.size()];
    return images.toArray(imagesArray);
  }


  private List<String> getImagesTags(String template, String imageRegex) {
    List<String> tagsImagens = new ArrayList<>();
    Matcher m = Pattern.compile(imageRegex).matcher(template);
    while (m.find()) {
      tagsImagens.add(m.group());
    }
    return tagsImagens;
  }

  private void printImage(String imagePath) throws IOException {

    byte[] decodedString = Base64.decode(imagePath, Base64.DEFAULT);
    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

    Bitmap scaledBitmap = Bitmap.createScaledBitmap(decodedByte, 200, 84, false);

    final int width = scaledBitmap.getWidth();
    final int height = scaledBitmap.getHeight();
    final int[] argb = new int[width * height];
    scaledBitmap.getPixels(argb, 0, width, 0, 0, width, height);
    scaledBitmap.recycle();

    mPrinter.printImage(argb, width, height, Printer.ALIGN_CENTER, true);
  }

	/**
     * Disconnect printer
     *
     * @param promise
     */
	@ReactMethod
	public void disconnect(Promise promise){
		try {
			mmSocket.close();

			if (mPrinter != null) {
				mPrinter.release();
			}

			if (mProtocolAdapter != null) {
				mProtocolAdapter.release();
			}

			if(promise != null) promise.resolve("DISCONNECTED");
		} catch (Exception e) {
			if(promise != null) promise.reject("Erro: " + e.getMessage());
		}
	}

	private void showToast(final String text) {
		Toast.makeText(getReactApplicationContext(), text, Toast.LENGTH_SHORT).show();
	}

}
