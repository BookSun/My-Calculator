package com.android.calculator2.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.EditText;

/**
 * Created by chyang on 15-5-19.
 */
public class CalculatorStorage {

    private SharedPreferences sharedPreferences;
    protected static final String CALCULATOR_SP = "calculator_sp";
    protected static final String SP_ADD_KEY = "sp_add_KEY";
    public CalculatorStorage (Context context) {
        sharedPreferences = context.getSharedPreferences(CALCULATOR_SP, Context.MODE_APPEND);
    }

    public void saveFormula(String value) {
        if (!TextUtils.isEmpty(readFormula())) {
            StringBuilder mStringBuilder = new StringBuilder();
            mStringBuilder.append(readFormula()).append(value);
            value = mStringBuilder.toString();
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SP_ADD_KEY, value);
        editor.commit();
    }

    public String readFormula () {
       return sharedPreferences.getString(SP_ADD_KEY,"");
    }

    public void cleanFormula () {
        sharedPreferences.edit().clear().commit();
    }

}
