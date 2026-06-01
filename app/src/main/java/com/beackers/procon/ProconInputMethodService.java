package dev.procon.ime;

import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Minimal IME implementation that exposes a basic on-screen QWERTY keyboard.
 *
 * <p>This default input view keeps the project installable and usable while the
 * controller-driven input layer is developed.</p>
 */
public class ProconInputMethodService extends InputMethodService {
    private static final String[][] KEY_ROWS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"z", "x", "c", "v", "b", "n", "m"}
    };

    private boolean shouldCapitalize;

    @Override
    public View onCreateInputView() {
        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(8);
        keyboard.setPadding(padding, padding, padding, padding);
        keyboard.setBackgroundColor(getColor(R.color.keyboard_background));

        TextView hint = new TextView(this);
        hint.setText(R.string.ime_name);
        hint.setTextColor(getColor(R.color.keyboard_hint_text));
        hint.setGravity(Gravity.CENTER);
        keyboard.addView(hint, rowLayoutParams());

        for (String[] row : KEY_ROWS) {
            LinearLayout rowView = new LinearLayout(this);
            rowView.setGravity(Gravity.CENTER);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            for (String key : row) {
                rowView.addView(createTextKey(key), keyLayoutParams(1));
            }
            keyboard.addView(rowView, rowLayoutParams());
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(createActionKey("⌫", this::deleteBackward), keyLayoutParams(1));
        actions.addView(createActionKey("Space", () -> commitText(" ")), keyLayoutParams(3));
        actions.addView(createActionKey("Enter", this::sendEnter), keyLayoutParams(1));
        keyboard.addView(actions, rowLayoutParams());

        return keyboard;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        int inputType = attribute == null ? InputType.TYPE_NULL : attribute.inputType;
        shouldCapitalize = (inputType & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0;
    }

    private Button createTextKey(String value) {
        return createActionKey(value.toUpperCase(Locale.US), () -> {
            String output = shouldCapitalize ? value.toUpperCase(Locale.US) : value;
            commitText(output);
            shouldCapitalize = false;
        });
    }

    private Button createActionKey(String label, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(getColor(R.color.keyboard_key_text));
        button.setBackgroundColor(getColor(R.color.keyboard_key_background));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void commitText(String text) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            inputConnection.commitText(text, 1);
        }
    }

    private void deleteBackward() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            inputConnection.deleteSurroundingText(1, 0);
        }
    }

    private void sendEnter() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            inputConnection.commitText("\n", 1);
        }
        shouldCapitalize = true;
    }

    private LinearLayout.LayoutParams rowLayoutParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams keyLayoutParams(int weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(56), weight);
        int margin = dp(2);
        params.setMargins(margin, margin, margin, margin);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
