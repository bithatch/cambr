package uk.co.bithatch.cambr;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.jetbrains.annotations.NotNull;

import com.github.sarxos.webcam.Webcam;

import io.activej.bytebuf.ByteBuf;
import io.activej.csp.ChannelSupplier;
import io.activej.promise.Promise;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.StringConverter;
import uk.co.bithatch.cambr.Cambr.CambrApp;

public class CambrController implements Initializable, ChannelSupplier<ByteBuf> {

	private static final byte[] DELIM = "\r\n".getBytes();

	private static final int DEFAULT_PORT = 8080;

	public static final String BOUNDARY = UUID.randomUUID().toString();

	@FXML
	private Button stream;

	@FXML
	private ComboBox<Webcam> device;

	@FXML
	private ComboBox<Dimension> resolution;

	@FXML
	private Button stop;

	@FXML
	private Spinner<Integer> port;

	@FXML
	private CheckBox localOnly;

	@FXML
	private CheckBox flipX;

	@FXML
	private CheckBox flipY;

	@FXML
	private CheckBox monitor;

	@FXML
	private ImageView imageView;

	@FXML
	private Hyperlink link;

	private Task<Void> captureThread;
	private SimpleBooleanProperty streaming = new SimpleBooleanProperty();
	private Task<HttpStreamer> streamThread;
	private BufferedImage offlineImage;
	private BufferedImage capturedImage;

	@Override
	public void initialize(URL arg0, ResourceBundle bundle) {
		stop.disableProperty().bind(Bindings.not(streaming));
		link.disableProperty().bind(Bindings.not(streaming));
		stream.disableProperty().bind(streaming);
		port.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 65535, 8080));
		try {
			offlineImage = ImageIO.read(CambrController.class.getResource("off.png"));
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load image.", e);
		}

		// Resolution
		resolution.setConverter(new StringConverter<Dimension>() {
			@Override
			public String toString(Dimension dim) {
				return dim == null ? bundle.getString("noResolution") : dim.width + " x " + dim.height;
			}

			@Override
			public Dimension fromString(String string) {
				throw new UnsupportedOperationException();
			}
		});
		resolution.getSelectionModel().selectedItemProperty().addListener((c, o, n) -> resolutionChanged());

		// Device
		device.getItems().setAll(Webcam.getWebcams());
		device.setConverter(new StringConverter<Webcam>() {
			@Override
			public String toString(Webcam cam) {
				return cam == null ? bundle.getString("noCamera") : cam.getName();
			}

			@Override
			public Webcam fromString(String string) {
				throw new UnsupportedOperationException();
			}
		});
		device.getSelectionModel().selectedItemProperty().addListener((c, o, n) -> deviceChanged());
		device.getSelectionModel().select(getWebcamForDeviceName(BoundPreferences.PREFS.get("device", "")));

		BoundPreferences.bind("flipX", false, flipX);
		BoundPreferences.bind("flipY", false, flipY);
		BoundPreferences.bind("localOnly", true, localOnly);
		BoundPreferences.bind("monitor", true, monitor);
		BoundPreferences.bind("port", DEFAULT_PORT, port);

		link.setOnAction((e) -> CambrApp.getAppHostServices().showDocument(link.getText()));
		monitor.selectedProperty().addListener((c, o, n) -> checkState());
		flipX.selectedProperty().addListener((c, o, n) -> reloadCapture());
		flipY.selectedProperty().addListener((c, o, n) -> reloadCapture());
		stream.setOnAction((e) -> streaming.set(true));
		stop.setOnAction((e) -> streaming.set(false));
		streaming.addListener((c, o, n) -> checkState());
		port.valueProperty().addListener((c, o, n) -> reloadStream());
		localOnly.selectedProperty().addListener((c, o, n) -> reloadStream());

		checkState();
	}

	@Override
	public void closeEx(@NotNull Exception e) {
		// TODO Auto-generated method stub

	}

	@Override
	public @NotNull Promise<ByteBuf> get() {
		if(!streaming.get())
			throw new IllegalStateException("Ended.");
		
		/*
		 * TODO this is a bit crap, there is probably a better way in ActiveJ to do
		 * multipart/x-mixed-replace, and a better way of building the content. I'm only
		 * an hour into using it!
		 * 
		 * TODO Different encoding options and techniques should be explored. For now, I
		 * know MJPEG works for what I wrote this app for (Animaze on Linux), but if
		 * people find other uses for it, fine!
		 */
		try {
			ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
			BufferedImage img = capturedImage;
			if (img == null)
				img = offlineImage;
			ImageIO.write(img, "JPEG", jpeg);
			byte[] data = jpeg.toByteArray();

			ByteArrayOutputStream res = new ByteArrayOutputStream();

			res.write("--".getBytes());
			res.write(BOUNDARY.getBytes());
			res.write(DELIM);
			res.write("Content-Type: image/jpeg".getBytes());
			res.write(DELIM);
			res.write("Content-Length: ".getBytes());
			res.write(String.valueOf(data.length).getBytes());
			res.write(DELIM);
			res.write(DELIM);
			res.write(data);
			res.write(DELIM);
			res.write(DELIM);

			return Promise.of(ByteBuf.wrapForReading(res.toByteArray()));
		} catch (IOException ioe) {
			throw new IllegalStateException("Failed.", ioe);
		}
	}

	Webcam getWebcamForDeviceName(String name) {
		for (Webcam cam : device.getItems()) {
			if (cam.getDevice().getName().equals(name))
				return cam;
		}
		if (device.getItems().isEmpty())
			return null;
		else
			return device.getItems().get(0);
	}

	void reloadCapture() {
		stopCapture();
		checkState();
	}

	void reloadStream() {
		stopStream();
		checkState();
	}

	void resolutionChanged() {
		var selected = resolution.getSelectionModel().getSelectedItem();
		BoundPreferences.PREFS.put("resolution", selected == null ? "" : selected.width + "x" + selected.height);
		checkState();
	}

	void deviceChanged() {
		var items = resolution.getItems();
		var selected = device.getSelectionModel().getSelectedItem();
		BoundPreferences.PREFS.put("device", selected == null ? "" : selected.getName());

		items.setAll(Arrays.asList(selected.getDevice().getResolutions()));

		/*
		 * Select either the last resolution, the biggest, or the first depending on
		 * state
		 */
		var lastResolutionEls = BoundPreferences.PREFS.get("resolution", "").split("x");
		Dimension last = lastResolutionEls.length < 2 ? null
				: new Dimension(Integer.parseInt(lastResolutionEls[0]), Integer.parseInt(lastResolutionEls[1]));
		Dimension biggest = null;
		for (Dimension sz : items) {
			if (sz.equals(last)) {
				biggest = sz;
				break;
			} else if (biggest == null || sz.width * sz.height > biggest.width * biggest.height)
				biggest = sz;
		}
		if (biggest != null)
			resolution.getSelectionModel().select(biggest);

		checkState();
	}

	void checkState() {
		var selectedDevice = device.getSelectionModel().getSelectedItem();
		var selectedResolution = resolution.getSelectionModel().getSelectedItem();
		boolean ready = selectedDevice != null && selectedResolution != null;
		boolean shouldBeStreaming = ready && streaming.get();
		boolean shouldBeCapturing = ready && (monitor.isSelected() || shouldBeStreaming);
		boolean isCapturing = captureThread != null;
		boolean isStreaming = streamThread != null;

		/* Transform */
		if (selectedDevice != null) {
			AffineTransform transform = createTransform(selectedResolution);
			selectedDevice.setImageTransformer(i -> transformImage(i, transform));
		}

		/* Start or stop capturing? */
		if (shouldBeCapturing != isCapturing) {
			if (shouldBeCapturing) {
				captureThread = new Task<Void>() {
					@Override
					protected Void call() throws Exception {
						selectedDevice.setViewSize(selectedResolution);
						selectedDevice.open();

						BufferedImage img = null;
						while (!isCancelled()) {
							if ((img = selectedDevice.getImage()) != null) {
								capturedImage = img;
								if (monitor.isSelected()) {
									var fimg = img;
									Platform.runLater(() -> imageView.imageProperty()
											.set(SwingFXUtils.toFXImage(fimg, null)));
								}
							}
						}
						capturedImage = null;
						return null;
					}

					@Override
					protected void failed() {
						selectedDevice.close();
					}

					@Override
					protected void cancelled() {
						selectedDevice.close();
					}
				};

				new Thread(captureThread, "Capture") {
					{
						setDaemon(true);
					}
				}.start();
			} else {
				stopCapture();
			}
		}

		/* Start or stop capturing? */
		if (shouldBeStreaming != isStreaming) {
			if (shouldBeStreaming) {
				streamThread = new Task<HttpStreamer>() {

					@Override
					protected HttpStreamer call() throws Exception {
						HttpStreamer streamer = new HttpStreamer(CambrController.this, port.getValue(),
								localOnly.isSelected());
						updateValue(streamer);
						streamer.launch(new String[0]);
						return streamer;
					}

					@Override
					protected void failed() {
						getException().printStackTrace();
					}

					@Override
					protected void cancelled() {
						getValue().shutdown();
					}
				};
				new Thread(streamThread, "Stream") {
					{
						setDaemon(true);
					}
				}.start();
			} else {
				stopStream();
			}
		}

		if (!monitor.isSelected()) {
			if (streaming.get())
				imageView.imageProperty().set(new Image(CambrController.class.getResource("streaming.png").toString()));
			else
				imageView.imageProperty().set(new Image(CambrController.class.getResource("off.png").toString()));
		}
		
		link.setText("http://localhost:" + port.getValue());
	}

	void stopStream() {
		if (streamThread != null) {
			try {
				streamThread.cancel(true);
			} finally {
				streamThread = null;
			}
		}
	}

	void stopCapture() {
		if (captureThread != null) {
			try {
				captureThread.cancel(true);
			} finally {
				captureThread = null;
				capturedImage = null;
			}
		}
	}

	AffineTransform createTransform(Dimension size) {
		AffineTransform at = new AffineTransform();
		at.concatenate(AffineTransform.getScaleInstance(flipX.isSelected() ? -1 : 1, flipY.isSelected() ? -1 : 1));
		at.concatenate(AffineTransform.getTranslateInstance(flipX.isSelected() ? -size.width : 0,
				flipY.isSelected() ? -size.height : 0));
		return at;
	}
	
	@FXML
	void evtRefresh(Event event) {
		device.getItems().setAll(Webcam.getWebcams());
	}

	public final class WebcamCell extends ListCell<Webcam> {

		@Override
		protected void updateItem(Webcam item, boolean empty) {
			super.updateItem(item, empty);
			if (item != null)
				setText(item.getName());
		}
	}

	private static BufferedImage transformImage(BufferedImage image, AffineTransform at) {

		BufferedImage newImage;
		if (image.getType() == BufferedImage.TYPE_CUSTOM) {
			ColorModel cm = image.getColorModel();
			boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
			WritableRaster raster = image.copyData(null);
			newImage = new BufferedImage(cm, raster, isAlphaPremultiplied, null);
		} else {
			newImage = new BufferedImage(image.getWidth(), image.getWidth(), image.getType());
		}

		Graphics2D g = newImage.createGraphics();
		g.transform(at);
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return newImage;
	}
}
