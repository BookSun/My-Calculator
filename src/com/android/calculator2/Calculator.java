/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calculator2;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.ViewAnimationUtils;
import android.view.ViewGroupOverlay;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.calculator2.util.CalculatorExpressionBuilder;
import com.android.calculator2.util.CalculatorExpressionEvaluator;
import com.android.calculator2.util.CalculatorExpressionEvaluator.EvaluateCallback;
import com.android.calculator2.util.CalculatorExpressionTokenizer;
import com.android.calculator2.util.CalculatorStorage;
import com.android.calculator2.view.DropDownLayout;
import com.android.calculator2.view.CalculatorEditText;
import com.android.calculator2.view.CalculatorEditText.OnTextSizeChangeListener;
import com.android.calculator2.view.CalculatorPadLayout;


public class Calculator extends Activity
        implements OnTextSizeChangeListener, EvaluateCallback, OnLongClickListener {

    private static final String NAME = Calculator.class.getName();

    // instance state keys
    private static final String KEY_CURRENT_STATE = NAME + "_currentState";
    private static final String KEY_CURRENT_EXPRESSION = NAME + "_currentExpression";

    private long lastClick;

    /**
     * Constant for an invalid resource id.
     */
    public static final int INVALID_RES_ID = -1;


    private enum CalculatorState {
        INPUT, EVALUATE, RESULT, ERROR
    }

    private enum DisplayState {
        CURRENT, HISTORY
    }

    private final TextWatcher mFormulaTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            setState(CalculatorState.INPUT);
            mEvaluator.evaluate(editable, Calculator.this);
        }
    };

    private final OnKeyListener mFormulaOnKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        mCurrentButton = mEqualButton;
                        onEquals();
                    }
                    // ignore all other actions
                    return true;
            }
            return false;
        }
    };

    private final Editable.Factory mFormulaEditableFactory = new Editable.Factory() {
        @Override
        public Editable newEditable(CharSequence source) {
            final boolean isEdited = mCurrentState == CalculatorState.INPUT
                    || mCurrentState == CalculatorState.ERROR;
            return new CalculatorExpressionBuilder(source, mTokenizer, isEdited);
        }
    };

    private CalculatorState mCurrentState;
    private CalculatorState mLastState;
    private CalculatorExpressionTokenizer mTokenizer;
    private CalculatorExpressionEvaluator mEvaluator;

    private CalculatorEditText mFormulaEditText;
    private CalculatorEditText mResultEditText;
    private View mDeleteButton;
    private View mEqualButton;

    private View mCurrentButton;
    private Animator mCurrentAnimator;

    private CalculatorPadLayout mPadNumeric;
    private CalculatorPadLayout mPadHoistroyButton;
    private DropDownLayout mDropDownLayout;

    private CalculatorStorage mCalculatorStorage;
    private DisplayState mDisplayState = DisplayState.CURRENT;
    private ScrollView historyDisplay;
    private RelativeLayout currentDisplay;
    private CalculatorEditText historyEdText;
    private LinearLayout historyButtonLatout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
        savedInstanceState = savedInstanceState == null ? Bundle.EMPTY : savedInstanceState;
        setState(CalculatorState.values()[savedInstanceState.getInt(KEY_CURRENT_STATE, CalculatorState.INPUT.ordinal())]);
        mFormulaEditText.setText(mTokenizer.getLocalizedExpression(savedInstanceState.getString(KEY_CURRENT_EXPRESSION, "")));
    }

    private void initView() {
        setContentView(R.layout.activity_calculator);
        mFormulaEditText = (CalculatorEditText) findViewById(R.id.formula);
        mResultEditText = (CalculatorEditText) findViewById(R.id.result);
        mDeleteButton = findViewById(R.id.del);
        mPadNumeric = (CalculatorPadLayout) findViewById(R.id.pad_numeric);
        mPadHoistroyButton = (CalculatorPadLayout) findViewById(R.id.pad_hoistory_button);
        mDropDownLayout = (DropDownLayout) findViewById(R.id.drop_down);
        historyButtonLatout = (LinearLayout)findViewById(R.id.pad_history_button_layout);

        historyDisplay = (ScrollView) findViewById(R.id.history_display);
        currentDisplay = (RelativeLayout) findViewById(R.id.display);
        historyEdText = (CalculatorEditText) findViewById(R.id.history_edText);
        mFormulaEditText.setOnClickListener(pasteClick);
        mFormulaEditText.setOnLongClickListener(copyClick);
        mResultEditText.setOnClickListener(pasteClick);
        mResultEditText.setOnLongClickListener(copyClick);
        changeDisplay(mDisplayState, 0);
        updateFonts();
        groupButtonAnimation();

        mEqualButton = findViewById(R.id.pad_numeric).findViewById(R.id.eq);
        if (mEqualButton == null || mEqualButton.getVisibility() != View.VISIBLE) {
            mEqualButton = findViewById(R.id.pad_operator).findViewById(R.id.eq);
        }

        mTokenizer = new CalculatorExpressionTokenizer(this);
        mEvaluator = new CalculatorExpressionEvaluator(mTokenizer);
        mCalculatorStorage = new CalculatorStorage(this);


        mEvaluator.evaluate(mFormulaEditText.getText(), this);

        mFormulaEditText.setEditableFactory(mFormulaEditableFactory);
        mFormulaEditText.addTextChangedListener(mFormulaTextWatcher);
        mFormulaEditText.setOnKeyListener(mFormulaOnKeyListener);
        mFormulaEditText.setOnTextSizeChangeListener(this);
        mDeleteButton.setOnLongClickListener(this);
    }

    private void updateFonts() {
        Typeface face = Typeface.createFromAsset(getAssets(), "fonts/LEWA_LightV2.4.otf");
        for (int numdericIndex = 0; numdericIndex < mPadNumeric.getChildCount(); numdericIndex++) {
            Button numBut = (Button) mPadNumeric.getChildAt(numdericIndex);
            numBut.setTypeface(face);
        }
        for (int displayIndex = 0; displayIndex < currentDisplay.getChildCount(); displayIndex++) {
            CalculatorEditText displayEd = (CalculatorEditText) currentDisplay.getChildAt(displayIndex);
            displayEd.setTypeface(face);
        }
        for (int hisDisplayIndex = 0; hisDisplayIndex < historyDisplay.getChildCount(); hisDisplayIndex++) {
            CalculatorEditText hisdiEd = (CalculatorEditText) historyDisplay.getChildAt(hisDisplayIndex);
            hisdiEd.setTypeface(face);
        }
        for (int hoistoryButtonIndex = 0; hoistoryButtonIndex < mPadHoistroyButton.getChildCount(); hoistoryButtonIndex++) {
            Button hisdibu = (Button) mPadHoistroyButton.getChildAt(hoistoryButtonIndex);
            hisdibu.setTypeface(face);
        }
    }

    public void groupButtonAnimation() {
        if (mPadNumeric.getChildCount() == 20) {
            rowButtonAnimation(600, 19, 18, 17, 16);
            rowButtonAnimation(800, 15, 14, 13, 12);
            rowButtonAnimation(1000, 11, 10, 9, 8);
            rowButtonAnimation(1200, 7, 6, 5, 4);
            rowButtonAnimation(1300, 3, 2, 1, 0);
            ObjectAnimator.ofFloat(mPadNumeric, View.ALPHA, 0.0f, 1.0f).setDuration(1000).start();

        }
        if (mPadNumeric.getChildCount() == 32) {
            rowButtonAnimation(600, 31, 30, 29, 28, 27, 26, 25, 24);
            rowButtonAnimation(900, 23, 22, 21, 20, 19, 18, 17, 16);
            rowButtonAnimation(1200, 15, 14, 13, 12, 11, 10, 9, 8);
            rowButtonAnimation(1500, 7, 6, 5, 4, 3, 2, 1, 0);
            ObjectAnimator.ofFloat(mPadNumeric, View.ALPHA, 0.0f, 1.0f).setDuration(1000).start();
        }

    }

    private void rowButtonAnimation(int duration, int... value) {
        if (value.length == 4) {
            AnimatorSet animatorCount = new AnimatorSet();
            animatorCount.playTogether(ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[0]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[0]), View.SCALE_Y, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[1]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[1]), View.SCALE_Y, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[2]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[2]), View.SCALE_Y, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[3]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[3]), View.SCALE_Y, 0.7f, 1.0f));
            animatorCount.setDuration(duration);
            animatorCount.start();
        }
        if (value.length == 8) {
            AnimatorSet animatorCount = new AnimatorSet();
            animatorCount.playTogether(ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[0]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[0]), View.SCALE_Y, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[1]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[1]), View.SCALE_Y, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[2]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[2]), View.SCALE_Y, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[3]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[3]), View.SCALE_Y, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[4]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[4]), View.SCALE_Y, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[5]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[5]), View.SCALE_Y, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[6]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[6]), View.SCALE_Y, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[7]), View.SCALE_X, 0.7f, 1.0f),
                    ObjectAnimator.ofFloat(mPadNumeric.getChildAt(value[7]), View.SCALE_Y, 0.7f, 1.0f));
            animatorCount.setDuration(duration);
            animatorCount.start();
        }
    }

    private View.OnClickListener pasteClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (System.currentTimeMillis() - lastClick <= 500) {
                pasteContent();
                lastClick = 0;
                return;
            }
            lastClick = System.currentTimeMillis();
        }
    };

    private OnLongClickListener copyClick = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            copyContent();
            return false;
        }
    };

    private void copyContent() {
        if (!TextUtils.isEmpty(mFormulaEditText.getText().toString())) {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, mFormulaEditText.getText()));
            if (clipboardManager.hasPrimaryClip()) {
                clipboardManager.getPrimaryClip().getItemAt(0).getText();
                Toast.makeText(Calculator.this, R.string.copy_tost, Toast.LENGTH_SHORT).show();

            }
        }
    }

    private void pasteContent() {
        ClipData clipData = ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).getPrimaryClip();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                CharSequence paste = clipData.getItemAt(i).coerceToText(this);
                if (isCanPaste(paste)) {
                    mFormulaEditText.getText().insert(mFormulaEditText.getSelectionEnd(), paste);
                }
            }
        }
    }

    private boolean isCanPaste(CharSequence paste) {
        boolean canPaste = true;
        try {
            Float.parseFloat(paste.toString());
        } catch (Exception e) {
            canPaste = false;
        }
        return canPaste;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        String saveFormula = mResultEditText.getEditableText().toString();
        String saveResult = mFormulaEditText.getEditableText().toString();
        initView();
        setState(mLastState);
        mResultEditText.setText(saveFormula);
        mFormulaEditText.setText(saveResult);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // If there's an animation in progress, cancel it first to ensure our state is up-to-date.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }

        super.onSaveInstanceState(outState);

        outState.putInt(KEY_CURRENT_STATE, mCurrentState.ordinal());
        outState.putString(KEY_CURRENT_EXPRESSION,
                mTokenizer.getNormalizedExpression(mFormulaEditText.getText().toString()));
    }

    private void setState(CalculatorState state) {
        if (mCurrentState != state) {
            mCurrentState = state;
            mFormulaEditText.setTextColor(
                    getResources().getColor(R.color.display_formula_text_color));
            mResultEditText.setTextColor(
                    getResources().getColor(R.color.display_result_text_color));
            getWindow().setStatusBarColor(
                    getResources().getColor(R.color.calculator_accent_color));
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();

        // If there's an animation in progress, cancel it so the user interaction can be handled
        // immediately.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }
    }

    public void onButtonClick(View view) {
        mCurrentButton = view;

        switch (view.getId()) {
            case R.id.eq:
                onEquals();
                break;
            case R.id.del:
                onDelete();
                break;
            case R.id.clr:
                onClear();
                break;
            case R.id.change_land:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case R.id.change_port:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case R.id.donw:
                mDisplayState = DisplayState.HISTORY;
                changeDisplay(mDisplayState, 1000);
                break;
            case R.id.up:
                mDisplayState = DisplayState.CURRENT;
                changeDisplay(mDisplayState, 1000);
                break;
            case R.id.clr_history:
                onHistoryClear();
                break;
            case R.id.fun_cos:
            case R.id.fun_ln:
            case R.id.fun_log:
            case R.id.fun_sin:
            case R.id.fun_tan:
                // Add left parenthesis after functions.
                mFormulaEditText.append(((Button) view).getText() + "(");
                mLastState = CalculatorState.INPUT;
                break;
            default:
                mFormulaEditText.append(((Button) view).getText());
                mLastState = CalculatorState.INPUT;
                break;
        }
    }

    @Override
    public boolean onLongClick(View view) {
        mCurrentButton = view;

        if (view.getId() == R.id.del) {
            onClear();
            return true;
        }
        return false;
    }

    @Override
    public void onEvaluate(String expr, String result, int errorResourceId) {
        if (errorResourceId != INVALID_RES_ID) {
            onError(errorResourceId);
        } else if (!TextUtils.isEmpty(result) && mCurrentState == CalculatorState.EVALUATE) {
            StringBuilder mStringBuilder = new StringBuilder();
            mStringBuilder.append("<br><br>").append(mFormulaEditText.getEditableText().toString()+"=")
                    .append("<br><br>")
                    .append("<font><big><big>" + result + "</big></big></font>");
            mCalculatorStorage.saveFormula(mStringBuilder.toString());
            onResult(result);
        } else if (mCurrentState == CalculatorState.EVALUATE) {
            // The current expression cannot be evaluated -> return to the input state.
            setState(CalculatorState.INPUT);
        }
        mFormulaEditText.requestFocus();
    }

    @Override
    public void onTextSizeChanged(final TextView textView, float oldSize) {
        if (mCurrentState != CalculatorState.INPUT) {
            // Only animate text changes that occur from user input.
            return;
        }

        // Calculate the values needed to perform the scale and translation animations,
        // maintaining the same apparent baseline for the displayed text.
        final float textScale = oldSize / textView.getTextSize();
        final float translationX = (1.0f - textScale) *
                (textView.getWidth() / 2.0f - textView.getPaddingEnd());
        final float translationY = (1.0f - textScale) *
                (textView.getHeight() / 2.0f - textView.getPaddingBottom());

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(textView, View.SCALE_X, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.SCALE_Y, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, translationX, 0.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, translationY, 0.0f));
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }

    private void changeDisplay(DisplayState displayState, int drution) {
        if (displayState == DisplayState.CURRENT) {
            mDropDownLayout.showView();
            historyButtonLatout.setVisibility(View.GONE);
            ObjectAnimator.ofFloat(mPadHoistroyButton, View.ALPHA, 1.0f, 0.0f).setDuration(drution).start();
            currentDisplay.setVisibility(View.VISIBLE);
            mPadHoistroyButton.getChildAt(0).setClickable(false);
            mPadHoistroyButton.getChildAt(1).setClickable(false);
            mPadNumeric.setClickable(true);
        }
        if (displayState == DisplayState.HISTORY) {
            mDropDownLayout.hiedView();
            historyButtonLatout.setVisibility(View.VISIBLE);
            currentDisplay.setVisibility(View.GONE);
            scrooToBottom();
            ObjectAnimator.ofFloat(mPadHoistroyButton, View.ALPHA, 0.0f, 1.0f).setDuration(500).start();
            historyEdText.setText(Html.fromHtml(mCalculatorStorage.readFormula()));
            mPadHoistroyButton.getChildAt(0).setClickable(true);
            mPadHoistroyButton.getChildAt(1).setClickable(true);
            mPadNumeric.setClickable(false);
        }
    }

    private void scrooToBottom() {
        historyDisplay.post(new Runnable() {
            @Override
            public void run() {
                historyDisplay.scrollTo(0,historyEdText.getHeight());
            }
        });
    }

    private void onEquals() {
        if (mCurrentState == CalculatorState.INPUT) {
            setState(CalculatorState.EVALUATE);
            mEvaluator.evaluate(mFormulaEditText.getText(), this);
        }
    }

    private void onDelete() {
        // Delete works like backspace; remove the last character from the expression.
        final Editable formulaText = mFormulaEditText.getEditableText();
        final int formulaLength = formulaText.length();
        if (formulaLength > 0) {
            formulaText.delete(formulaLength - 1, formulaLength);
        }
    }

    private void reveal(View sourceView, View layout, int colorRes, AnimatorListener listener) {
        final ViewGroupOverlay groupOverlay =
                (ViewGroupOverlay) getWindow().getDecorView().getOverlay();

        final Rect displayRect = new Rect();
        layout.getGlobalVisibleRect(displayRect);

        // Make reveal cover the display and status bar.
        final View revealView = new View(this);
        revealView.setBottom(displayRect.bottom);
        revealView.setLeft(displayRect.left);
        revealView.setRight(displayRect.right);
        revealView.setBackgroundColor(getResources().getColor(colorRes));
        groupOverlay.add(revealView);

        final int[] clearLocation = new int[2];
        sourceView.getLocationInWindow(clearLocation);
        clearLocation[0] += sourceView.getWidth() / 2;
        clearLocation[1] += sourceView.getHeight() / 2;

        final int revealCenterX = clearLocation[0] - revealView.getLeft();
        final int revealCenterY = clearLocation[1] - revealView.getTop();

        final double x1_2 = Math.pow(revealView.getLeft() - revealCenterX, 2);
        final double x2_2 = Math.pow(revealView.getRight() - revealCenterX, 2);
        final double y_2 = Math.pow(revealView.getTop() - revealCenterY, 2);
        final float revealRadius = (float) Math.max(Math.sqrt(x1_2 + y_2), Math.sqrt(x2_2 + y_2));


        final Animator revealAnimator =
                ViewAnimationUtils.createCircularReveal(revealView,
                        revealCenterX, revealCenterY, 0.0f, revealRadius);
        revealAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_longAnimTime));
        revealAnimator.addListener(listener);
        final Animator alphaAnimator = ObjectAnimator.ofFloat(revealView, View.ALPHA, 0.0f);
        alphaAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_mediumAnimTime));

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(revealAnimator).before(alphaAnimator);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                groupOverlay.remove(revealView);
                mCurrentAnimator = null;
            }
        });

        mCurrentAnimator = animatorSet;
        animatorSet.start();
    }

    private void onClear() {
        if (TextUtils.isEmpty(mFormulaEditText.getText()) && TextUtils.isEmpty(mResultEditText.getText())) {
            return;
        }

        reveal(mCurrentButton, currentDisplay, R.color.calculator_accent_colors, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mFormulaEditText.getEditableText().clear();
                mResultEditText.getEditableText().clear();
            }
        });
    }

    private void onHistoryClear() {
        if (TextUtils.isEmpty(historyEdText.getText())) {
            return;
        }
        reveal(mCurrentButton, historyDisplay, R.color.calculator_accent_colors, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mCalculatorStorage.cleanFormula();
                historyEdText.getEditableText().clear();
            }
        });

    }

    private void onError(final int errorResourceId) {
        if (mCurrentState != CalculatorState.EVALUATE) {
            // Only animate error on evaluate.
            return;
        }

        reveal(mCurrentButton, currentDisplay, R.color.calculator_accent_colors, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setState(CalculatorState.ERROR);
                mResultEditText.setText(errorResourceId);
                mLastState = CalculatorState.ERROR;
            }
        });
    }

    private void onResult(final String result) {
        // Calculate the values needed to perform the scale and translation animations,
        // accounting for how the scale will affect the final position of the text.
        final float resultScale =
                mResultEditText.getVariableTextSize(result) / mFormulaEditText.getTextSize();
        final float resultTranslationX = (1.0f - resultScale) *
                (mFormulaEditText.getWidth() / 2.0f - mFormulaEditText.getPaddingEnd());
        final float resultTranslationY = (1.0f - resultScale) *
                (mFormulaEditText.getHeight() / 2.0f - mFormulaEditText.getPaddingBottom()) +
                (mResultEditText.getBottom() - mFormulaEditText.getBottom()) +
                (mFormulaEditText.getPaddingBottom() - mResultEditText.getPaddingBottom());
        final float resultEditTextY = -mResultEditText.getBottom();

        // Use a value animator to fade to the final text color over the course of the animation.
        final int resultTextColor = mResultEditText.getCurrentTextColor();
        final int formulaTextColor = mFormulaEditText.getCurrentTextColor();
        final ValueAnimator textColorAnimator =
                ValueAnimator.ofObject(new ArgbEvaluator(), resultTextColor, formulaTextColor);
        textColorAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mResultEditText.setTextColor((int) valueAnimator.getAnimatedValue());
            }
        });


        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                textColorAnimator,
                ObjectAnimator.ofFloat(mResultEditText, View.SCALE_X, resultScale),
                ObjectAnimator.ofFloat(mResultEditText, View.SCALE_Y, resultScale),
                ObjectAnimator.ofFloat(mFormulaEditText, View.SCALE_X, resultScale),
                ObjectAnimator.ofFloat(mFormulaEditText, View.SCALE_Y, resultScale),
                ObjectAnimator.ofFloat(mResultEditText, View.TRANSLATION_X, resultTranslationX),
                ObjectAnimator.ofFloat(mFormulaEditText, View.TRANSLATION_X, resultTranslationX),
                ObjectAnimator.ofFloat(mFormulaEditText, View.TRANSLATION_Y, resultTranslationY - 13),
                ObjectAnimator.ofFloat(mResultEditText, View.TRANSLATION_Y, resultEditTextY)

        );
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_longAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            String formula = null;

            @Override
            public void onAnimationStart(Animator animation) {
                formula = mFormulaEditText.getEditableText().toString();

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // Reset all of the values modified during the animation.
                mResultEditText.setTextColor(resultTextColor);
                mResultEditText.setScaleX(1.0f);
                mResultEditText.setScaleY(1.0f);
                mFormulaEditText.setScaleX(1.0f);
                mFormulaEditText.setScaleY(1.0f);
                mResultEditText.setTranslationX(0.0f);
                mResultEditText.setTranslationY(0.0f);
                mFormulaEditText.setTranslationY(0.0f);
                mFormulaEditText.setTranslationX(0.0f);
                ObjectAnimator.ofFloat(mFormulaEditText, View.ALPHA, 0.0f, 1.0f).start();
                mResultEditText.setText(formula+"=");
                mFormulaEditText.setText(result);
                setState(CalculatorState.RESULT);
                mLastState = CalculatorState.RESULT;
                mCurrentAnimator = null;
            }
        });

        mCurrentAnimator = animatorSet;
        animatorSet.start();
    }
}
