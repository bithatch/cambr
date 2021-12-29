package uk.co.bithatch.cambr;

import java.io.IOException;
import java.util.ResourceBundle;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Cambr {
	static CambrApp app;

	public static void main(String[] args) {
		CambrApp.main(args);
	}

	/**
	 * This silliness is because I just cannot get this to run as a modular
	 * application (due to BridJ not being able to access libraries). I can't find
	 * any way to hack this with --add-opens and friends. BridJ is a dead project,
	 * so this will never get fixed. I intend to look for an alternative to
	 * camera-capture that uses more up to date libraries. On top of this, JavaFX
	 * now makes it difficult to use with non-modular applications.
	 */
	public static class CambrApp extends Application {

		public static void main(String[] args) {
			launch(args);
		}

		public static HostServices getAppHostServices() {
			return app.getHostServices();
		}

		@Override
		public void start(final Stage primaryStage) {
			app = this; /*
						 * Ew, fix this -
						 * https://stackoverflow.com/questions/16604341/how-can-i-open-the-default-
						 * system-browser-from-a-java-fx-application
						 */

			primaryStage.setTitle("Cambr");
			try {
				Parent root = FXMLLoader.load(getClass().getResource("CambrController.fxml"),
						ResourceBundle.getBundle(CambrController.class.getName()));
				root.getStylesheets().add(Cambr.class.getResource("Cambr.css").toExternalForm());
				Scene scene = new Scene(root/* , 900, 690 */);
				primaryStage.setScene(scene);
				primaryStage.sizeToScene();
				primaryStage.centerOnScreen();
				primaryStage.show();
				primaryStage.setOnCloseRequest((evt) -> {
					System.exit(0);
				});
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to load UI.", ioe);
			}
		}
	}
}
