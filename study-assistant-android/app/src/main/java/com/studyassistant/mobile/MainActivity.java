package com.studyassistant.mobile;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Lightweight phone companion for the local Study Assistant.
 *
 * It deliberately uses only Android platform APIs: no network permission,
 * no cloud account, and no dependency on the existing Jarvis application.
 */
public class MainActivity extends Activity {
    private static final int PICK_TEXT_FILE = 4101;
    private static final String PREFS = "study_assistant_local";
    private static final String MATERIAL = "material";
    private static final String CARDS = "cards";
    private static final int BG = Color.rgb(16, 19, 26);
    private static final int PANEL = Color.rgb(28, 33, 43);
    private static final int TEXT = Color.rgb(237, 241, 248);
    private static final int MUTED = Color.rgb(165, 177, 198);
    private static final int BLUE = Color.rgb(138, 180, 248);

    private SharedPreferences preferences;
    private EditText materialEditor;
    private TextView output;
    private TextView status;
    private LinearLayout quizArea;
    private TextToSpeech textToSpeech;
    private String currentQuizStatement;
    private boolean currentQuizAnswer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        textToSpeech = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.getDefault());
            }
        });
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(page);

        TextView title = label("STUDY ASSISTANT", 24, TEXT);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(title);
        TextView subtitle = label("Local memory • no account • built for phone study", 13, MUTED);
        page.addView(subtitle, marginParams(0, 3, 0, 14));

        status = label("Ready. Your material stays on this phone.", 13, BLUE);
        page.addView(status, marginParams(0, 0, 0, 12));

        TextView materialLabel = label("Study material", 16, TEXT);
        materialLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(materialLabel);
        materialEditor = new EditText(this);
        materialEditor.setText(preferences.getString(MATERIAL, ""));
        materialEditor.setHint("Paste a lesson, definition, or notes here…");
        materialEditor.setHintTextColor(MUTED);
        materialEditor.setTextColor(TEXT);
        materialEditor.setTextSize(16);
        materialEditor.setGravity(Gravity.TOP | Gravity.START);
        materialEditor.setPadding(dp(12), dp(12), dp(12), dp(12));
        materialEditor.setMinHeight(dp(190));
        materialEditor.setBackgroundColor(PANEL);
        page.addView(materialEditor, marginParams(0, 7, 0, 8));

        LinearLayout sourceRow = row();
        sourceRow.addView(actionButton("Import .txt / .md", v -> chooseTextFile()), weightParams(1, 0, 5));
        sourceRow.addView(actionButton("Save", v -> saveMaterial()), weightParams(1, 0, 5));
        page.addView(sourceRow);

        LinearLayout actionRow = row();
        actionRow.addView(actionButton("Summarize", v -> summarize()), weightParams(1, 0, 5));
        actionRow.addView(actionButton("Flashcard", v -> createFlashcard()), weightParams(1, 0, 5));
        actionRow.addView(actionButton("Quiz me", v -> createQuiz()), weightParams(1, 0, 5));
        page.addView(actionRow, marginParams(0, 9, 0, 0));

        quizArea = new LinearLayout(this);
        quizArea.setOrientation(LinearLayout.VERTICAL);
        page.addView(quizArea, marginParams(0, 12, 0, 0));

        output = label("Your summary, cards, and quiz prompts will appear here.", 16, TEXT);
        output.setPadding(dp(13), dp(13), dp(13), dp(13));
        output.setBackgroundColor(PANEL);
        output.setTextIsSelectable(true);
        page.addView(output, marginParams(0, 14, 0, 8));

        LinearLayout utilityRow = row();
        utilityRow.addView(actionButton("Read aloud", v -> speakOutput()), weightParams(1, 0, 5));
        utilityRow.addView(actionButton("Share", v -> shareOutput()), weightParams(1, 0, 5));
        page.addView(utilityRow);
        setContentView(scroll);
    }

    private void saveMaterial() {
        String text = materialEditor.getText().toString().trim();
        if (text.isEmpty()) {
            toast("Add some study material first.");
            return;
        }
        preferences.edit().putString(MATERIAL, text).apply();
        status.setText("Saved locally • " + text.length() + " characters");
        hideKeyboard();
    }

    private void summarize() {
        String text = material();
        if (text.isEmpty()) return;
        ArrayList<String> sentences = sentences(text);
        StringBuilder summary = new StringBuilder("SUMMARY\n\n");
        int count = Math.min(5, sentences.size());
        for (int i = 0; i < count; i++) {
            summary.append("• ").append(sentences.get(i)).append("\n\n");
        }
        if (sentences.size() > count) summary.append("More detail is available in your saved material.");
        output.setText(summary.toString().trim());
        status.setText("Summary generated locally");
    }

    private void createFlashcard() {
        String text = material();
        if (text.isEmpty()) return;
        String back = sentences(text).get(0);
        String front = "What is the key idea in this material?";
        try {
            JSONArray cards = new JSONArray(preferences.getString(CARDS, "[]"));
            JSONObject card = new JSONObject();
            card.put("front", front);
            card.put("back", back);
            cards.put(card);
            preferences.edit().putString(CARDS, cards.toString()).apply();
            output.setText("FLASHCARD SAVED\n\nFront\n" + front + "\n\nBack\n" + back);
            status.setText("Saved " + cards.length() + " local flashcard(s)");
        } catch (Exception error) {
            toast("Could not save the flashcard.");
        }
    }

    private void createQuiz() {
        String text = material();
        if (text.isEmpty()) return;
        ArrayList<String> items = sentences(text);
        currentQuizStatement = items.get(0);
        currentQuizAnswer = true;
        quizArea.removeAllViews();
        TextView question = label("TRUE OR FALSE\n\n" + currentQuizStatement, 16, TEXT);
        question.setPadding(dp(13), dp(13), dp(13), dp(8));
        quizArea.addView(question);
        LinearLayout answers = row();
        answers.addView(actionButton("TRUE", v -> answerQuiz(true)), weightParams(1, 0, 5));
        answers.addView(actionButton("FALSE", v -> answerQuiz(false)), weightParams(1, 0, 5));
        quizArea.addView(answers);
        output.setText("Quiz question ready. Choose an answer above.");
        status.setText("Local quiz generated");
    }

    private void answerQuiz(boolean answer) {
        boolean correct = answer == currentQuizAnswer;
        output.setText((correct ? "CORRECT ✓" : "NOT QUITE") + "\n\nThe statement came directly from your saved material:\n" + currentQuizStatement);
        status.setText(correct ? "Correct answer" : "Review this section once more");
    }

    private String material() {
        String text = materialEditor.getText().toString().trim();
        if (!text.isEmpty()) preferences.edit().putString(MATERIAL, text).apply();
        if (text.isEmpty()) {
            toast("Add or import study material first.");
        }
        return text;
    }

    private ArrayList<String> sentences(String text) {
        ArrayList<String> result = new ArrayList<>();
        String normalized = text.replace('\r', '\n').replaceAll("[\\t ]+", " ").trim();
        for (String piece : normalized.split("(?<=[.!?])\\s+|\\n+")) {
            String clean = piece.trim();
            if (!clean.isEmpty()) result.add(clean);
        }
        if (result.isEmpty()) result.add(normalized);
        return result;
    }

    private void chooseTextFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, PICK_TEXT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_TEXT_FILE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (InputStream stream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
            materialEditor.setText(text.toString().trim());
            saveMaterial();
            toast("Material imported locally.");
        } catch (Exception error) {
            toast("Could not read that text file.");
        }
    }

    private void speakOutput() {
        String text = output.getText().toString().trim();
        if (!text.isEmpty() && textToSpeech != null) textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "study-output");
    }

    private void shareOutput() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, output.getText().toString());
        startActivity(Intent.createChooser(share, "Share study output"));
    }

    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button actionButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(TEXT);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams marginParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams weightParams(int width, int height, int rightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, 1f);
        params.setMargins(0, 0, dp(rightMargin), 0);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(materialEditor.getWindowToken(), 0);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) textToSpeech.shutdown();
        super.onDestroy();
    }
}
