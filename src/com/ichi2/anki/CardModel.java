/****************************************************************************************
 * Copyright (c) 2009 Daniel Svärd <daniel.svard@gmail.com>                             *
 * Copyright (c) 2010 Rick Gruber-Riemer <rick@vanosten.net>                            *
 * Copyright (c) 2011 Robert Siemer <Robert.Siemer-pankidroid@backsla.sh>               *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import java.io.File;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

/**
 * Card model. Card models are used to make question/answer pairs for the information you add to facts. You can display
 * any number of fields on the question side and answer side.
 *
 * @see http://ichi2.net/anki/wiki/ModelProperties#Card_Templates
 */
public class CardModel implements Comparator<CardModel> {

    // TODO: Javadoc.
    // TODO: Methods for reading/writing from/to DB.

    public static enum QA {
        QUESTION, ANSWER
    }

    public static final int DEFAULT_FONT_SIZE = 20;
    public static final int DEFAULT_FONT_SIZE_RATIO = 100;
    public static final String DEFAULT_FONT_FAMILY = "Arial";
    public static final String DEFAULT_FONT_COLOR = "#000000";
    public static final String DEFAULT_BACKGROUND_COLOR = "#FFFFFF";

    // Regex pattern for converting old style template to new
    private static final Pattern sOldStylePattern = Pattern.compile("%\\((.+?)\\)s");
    private static final Pattern sQaSeparator = Pattern.compile("^-----$", Pattern.MULTILINE);
    private static final boolean LOCAL_LOGV = true;
    private static final String TAG = "AnkiCardModel";

    // BEGIN SQL table columns
    private long mId; // Primary key
    private int mOrdinal;
    private long mModelId; // Foreign key models.id
    private String mName;
    private String mDescription = "";
    private int mActive = 1;
    // Formats: question/answer/last (not used)
    private String mQformat;
    private String mAformat;
    private String mLformat;
    // Question/answer editor format (not used yet)
    private String mQedformat;
    private String mAedformat;
    private int mQuestionInAnswer = 0;
    // Unused
    private String mQuestionFontFamily = DEFAULT_FONT_FAMILY;
    private int mQuestionFontSize = DEFAULT_FONT_SIZE;
    private String mQuestionFontColour = DEFAULT_FONT_COLOR;
    // Used for both question & answer
    private int mQuestionAlign = 0;
    // Unused
    private String mAnswerFontFamily = DEFAULT_FONT_FAMILY;
    private int mAnswerFontSize = DEFAULT_FONT_SIZE;
    private String mAnswerFontColour = DEFAULT_FONT_COLOR;
    private int mAnswerAlign = 0;
    private final String mLastFontFamily = DEFAULT_FONT_FAMILY;
    private final int mLastFontSize = DEFAULT_FONT_SIZE;
    // Used as background colour
    private String mLastFontColour = DEFAULT_BACKGROUND_COLOR;
    private final String mEditQuestionFontFamily = "";
    private final int mEditQuestionFontSize = 0;
    private final String mEditAnswerFontFamily = "";
    private final int mEditAnswerFontSize = 0;
    // Empty answer
    private final int mAllowEmptyAnswer = 1;
    private final String mTypeAnswer = "";
    // END SQL table entries

    /**
     * Backward reference
     */
    private Model mModel;


    // This constructor is used nowhere...
    //
    // public CardModel(String name, String qformat, String aformat, boolean active) {
    // mName = name;
    // mQformat = qformat;
    // mAformat = aformat;
    // mActive = active ? 1 : 0;
    // mId = Utils.genID();
    // }


    private CardModel() {
    }

    /** SELECT string with only those fields, which are used in AnkiDroid */
    private static final String SELECT_STRING = "SELECT id, ordinal, modelId, name, description, active, qformat, "
            + "aformat, questionInAnswer, questionFontFamily, questionFontSize, questionFontColour, questionAlign, "
            + "answerFontFamily, answerFontSize, answerFontColour, answerAlign, lastFontColour" + " FROM cardModels";


    /**
     * @param modelId
     * @param models will be changed by adding all found CardModels into it
     * @return unordered CardModels which are related to a given Model and eventually active put into the parameter
     *         "models"
     */
    protected static final void fromDb(Deck deck, long modelId, LinkedHashMap<Long, CardModel> models) {
        Cursor cursor = null;
        CardModel myCardModel = null;
        try {
            StringBuffer query = new StringBuffer(SELECT_STRING);
            query.append(" WHERE modelId = ");
            query.append(modelId);
            query.append(" ORDER BY ordinal");

            cursor = AnkiDatabaseManager.getDatabase(deck.getDeckPath()).getDatabase().rawQuery(query.toString(), null);

            if (cursor.moveToFirst()) {
                do {
                    myCardModel = new CardModel();

                    myCardModel.mId = cursor.getLong(0);
                    myCardModel.mOrdinal = cursor.getInt(1);
                    myCardModel.mModelId = cursor.getLong(2);
                    myCardModel.mName = cursor.getString(3);
                    myCardModel.mDescription = cursor.getString(4);
                    myCardModel.mActive = cursor.getInt(5);
                    myCardModel.mQformat = cursor.getString(6);
                    myCardModel.mAformat = cursor.getString(7);
                    myCardModel.mQuestionInAnswer = cursor.getInt(8);
                    myCardModel.mQuestionFontFamily = cursor.getString(9);
                    myCardModel.mQuestionFontSize = cursor.getInt(10);
                    myCardModel.mQuestionFontColour = cursor.getString(11);
                    myCardModel.mQuestionAlign = cursor.getInt(12);
                    myCardModel.mAnswerFontFamily = cursor.getString(13);
                    myCardModel.mAnswerFontSize = cursor.getInt(14);
                    myCardModel.mAnswerFontColour = cursor.getString(15);
                    myCardModel.mAnswerAlign = cursor.getInt(16);
                    myCardModel.mLastFontColour = cursor.getString(17);
                    File template = new File(deck.mediaDir(), "cardmodel."
                            + Utils.replaceFatSpecials(myCardModel.mName) + ".html");
                    if (LOCAL_LOGV) {
                        Log.v(TAG, template.getAbsolutePath());
                    }
                    if (template.exists()) {
                        String[] qa = sQaSeparator.split(Utils.readFile(template));
                        myCardModel.mQformat = qa[0];
                        myCardModel.mAformat = qa[1];
                    }
                    models.put(myCardModel.mId, myCardModel);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    protected void toDB(Deck deck) {
        ContentValues values = new ContentValues();
        values.put("id", mId);
        values.put("ordinal", mOrdinal);
        values.put("modelId", mModelId);
        values.put("name", mName);
        values.put("description", mDescription);
        values.put("active", mActive);
        values.put("qformat", mQformat);
        values.put("aformat", mAformat);
        values.put("questionInAnswer", mQuestionInAnswer);
        values.put("questionFontFamily", mQuestionFontFamily);
        values.put("questionFontSize", mQuestionFontSize);
        values.put("questionFontColour", mQuestionFontColour);
        values.put("questionAlign", mQuestionAlign);
        values.put("answerFontFamily", mAnswerFontFamily);
        values.put("answerFontSize", mAnswerFontSize);
        values.put("answerFontColour", mAnswerFontColour);
        values.put("answerAlign", mAnswerAlign);
        values.put("lastFontColour", mLastFontColour);
        deck.getDB().update(deck, "cardModels", values, "id = " + mId, null);
    }


    public boolean isActive() {
        return (mActive != 0);
    }


    /**
     * @param cardModelId
     * @return the modelId for a given cardModel or 0, if it cannot be found
     */
    protected static final long modelIdFromDB(Deck deck, long cardModelId) {
        Cursor cursor = null;
        long modelId = -1;
        try {
            String query = "SELECT modelId FROM cardModels WHERE id = " + cardModelId;
            cursor = AnkiDatabaseManager.getDatabase(deck.getDeckPath()).getDatabase().rawQuery(query, null);
            cursor.moveToFirst();
            modelId = cursor.getLong(0);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return modelId;
    }


    private static String replaceField(String replaceFrom, Fact fact, int replaceAt, boolean isQuestion) {
        int endIndex = replaceFrom.indexOf(")", replaceAt);
        String fieldName = replaceFrom.substring(replaceAt + 2, endIndex);
        char fieldType = replaceFrom.charAt(endIndex + 1);
        if (isQuestion) {
            String replace = "%(" + fieldName + ")" + fieldType;
            String with = "<span class=\"fm" + Long.toHexString(fact.getFieldModelId(fieldName)) + "\">"
                    + fact.getFieldValue(fieldName) + "</span>";
            replaceFrom = replaceFrom.replace(replace, with);
        } else {
            replaceFrom.replace("%(" + fieldName + ")" + fieldType, "<span class=\"fma"
                    + Long.toHexString(fact.getFieldModelId(fieldName)) + "\">" + fact.getFieldValue(fieldName)
                    + "</span");
        }
        return replaceFrom;
    }


    private static String replaceHtmlField(String replaceFrom, Fact fact, int replaceAt) {
        int endIndex = replaceFrom.indexOf(")", replaceAt);
        String fieldName = replaceFrom.substring(replaceAt + 7, endIndex);
        char fieldType = replaceFrom.charAt(endIndex + 1);
        String replace = "%(text:" + fieldName + ")" + fieldType;
        String with = fact.getFieldValue(fieldName);
        replaceFrom = replaceFrom.replace(replace, with);
        return replaceFrom;
    }


    /**
     * Implements Comparator by comparing the field "ordinal".
     *
     * @param object1
     * @param object2
     * @return
     */
    @Override
    public int compare(CardModel object1, CardModel object2) {
        return object1.mOrdinal - object2.mOrdinal;
    }


    /**
     * @return the id
     */
    public long getId() {
        return mId;
    }


    /**
     * @return the ordinal
     */
    public int getOrdinal() {
        return mOrdinal;
    }


    /**
     * @return the questionInAnswer
     */
    public boolean isQuestionInAnswer() {
        // FIXME hmmm, is that correct?
        return (mQuestionInAnswer == 0);
    }


    /**
     * @return the lastFontColour
     */
    public String getLastFontColour() {
        return mLastFontColour;
    }


    /**
     * @return the questionFontFamily
     */
    public String getQuestionFontFamily() {
        return mQuestionFontFamily;
    }


    /**
     * @return the questionFontSize
     */
    public int getQuestionFontSize() {
        return mQuestionFontSize;
    }


    /**
     * @return the questionFontColour
     */
    public String getQuestionFontColour() {
        return mQuestionFontColour;
    }


    /**
     * @return the questionAlign
     */
    public int getQuestionAlign() {
        return mQuestionAlign;
    }


    /**
     * @return the answerFontFamily
     */
    public String getAnswerFontFamily() {
        return mAnswerFontFamily;
    }


    /**
     * @return the answerFontSize
     */
    public int getAnswerFontSize() {
        return mAnswerFontSize;
    }


    /**
     * @return the answerFontColour
     */
    public String getAnswerFontColour() {
        return mAnswerFontColour;
    }


    /**
     * @return the answerAlign
     */
    public int getAnswerAlign() {
        return mAnswerAlign;
    }


    /**
     * @return the name
     */
    public String getName() {
        return mName;
    }


    /**
     * Setter for question Format
     * @param the new question format
     */
    public void setQFormat(String qFormat) {
        mQformat = qFormat;
    }


    /**
     * Setter for answer Format
     * @param the new answer format
     */
    public void setAFormat(String aFormat) {
        mAformat = aFormat;
    }


    public String getQa(QA qa) {
        return qa == QA.QUESTION ? mQformat : mAformat;
    }
}
