package uk.co.bithatch.cambr;

import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;

public class BoundPreferences {

	public final static Preferences PREFS = Preferences.userNodeForPackage(BoundPreferences.class);

	public static void bind(String key, boolean defaultValue, CheckBox checkBox) {
		checkBox.setSelected(PREFS.getBoolean(key, defaultValue));
		PREFS.addPreferenceChangeListener(e -> {
			if (e.getKey().equals(key))
				maybeRunLater(() -> checkBox.setSelected(Boolean.valueOf(e.getNewValue())));
		});
		checkBox.selectedProperty().addListener((c, o, n) -> PREFS.putBoolean(key, n));
	}

	public static void bind(String key, int defaultValue, Spinner<Integer> spinner) {
		spinner.getValueFactory().setValue(PREFS.getInt(key, defaultValue));
		PREFS.addPreferenceChangeListener(e -> {
			if (e.getKey().equals(key))
				maybeRunLater(() -> spinner.getValueFactory().setValue(Integer.valueOf(e.getNewValue())));
		});
		spinner.valueProperty().addListener((c, o, n) -> PREFS.putInt(key, n));
	}

	static void maybeRunLater(Runnable r) {
		if (Platform.isFxApplicationThread())
			r.run();
		else
			Platform.runLater(r);
	}

}
