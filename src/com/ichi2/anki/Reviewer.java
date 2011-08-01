/***************************************************************************************
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.ClipboardManager;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout.LayoutParams;

import com.ichi2.utils.DiffEngine;
import com.tomgibara.android.veecheck.util.PrefSettings;
import com.zeemote.zc.Controller;
import com.zeemote.zc.event.ButtonEvent;
import com.zeemote.zc.event.IButtonListener;
import com.zeemote.zc.ui.android.ControllerAndroidUi;

import org.amr.arabic.ArabicUtilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Reviewer extends Activity implements IButtonListener{
    private static final String TAG = "AnkiReviewer";
    /**
     * Result codes that are returned when this activity finishes.
     */
    public static final int RESULT_SESSION_COMPLETED = 1;
    public static final int RESULT_NO_MORE_CARDS = 2;

    /**
     * Available options performed by other activities.
     */
    public static final int EDIT_CURRENT_CARD = 0;

    /** Constant for class attribute signaling answer */
    static final String ANSWER_CLASS = "answer";

    /** Constant for class attribute signaling question */
    static final String QUESTION_CLASS = "question";

    /** Max size of the font for dynamic calculation of font size */
    private static final int DYNAMIC_FONT_MAX_SIZE = 14;

    /** Min size of the font for dynamic calculation of font size */
    private static final int DYNAMIC_FONT_MIN_SIZE = 3;
    private static final int DYNAMIC_FONT_FACTOR = 5;

    private static final int TOTAL_WIDTH_PADDING = 10;

    /**
     * Menus
     */
    private static final int MENU_WHITEBOARD = 0;
    private static final int MENU_CLEAR_WHITEBOARD = 1;
    private static final int MENU_EDIT = 2;
    private static final int MENU_REMOVE = 3;
    private static final int MENU_REMOVE_BURY = 31;
    private static final int MENU_REMOVE_SUSPEND = 32;
    private static final int MENU_REMOVE_DELETE = 33;
    private static final int MENU_SEARCH = 4;
    private static final int MENU_MARK = 5;
    private static final int MENU_UNDO = 6;
    private static final int MENU_REDO = 7;

    /** Zeemote messages */
    private static final int MSG_ZEEMOTE_BUTTON_A = 0x110;
    private static final int MSG_ZEEMOTE_BUTTON_B = MSG_ZEEMOTE_BUTTON_A+1;
    private static final int MSG_ZEEMOTE_BUTTON_C = MSG_ZEEMOTE_BUTTON_A+2;
    private static final int MSG_ZEEMOTE_BUTTON_D = MSG_ZEEMOTE_BUTTON_A+3;

    /** Regex patterns used in identifying and fixing Hebrew words, so we can reverse them */
    private static final Pattern sHebrewPattern = Pattern.compile(
            // Two cases caught below:
            // Either a series of characters, starting from a hebrew character...
            "([[\\u0591-\\u05F4][\\uFB1D-\\uFB4F]]" +
            // ...followed by hebrew characters, punctuation, parenthesis, spaces, numbers or numerical symbols...
            "[[\\u0591-\\u05F4][\\uFB1D-\\uFB4F],.?!;:\"'\\[\\](){}+\\-*/%=0-9\\s]*" +
            // ...and ending with hebrew character, punctuation or numerical symbol
            "[[\\u0591-\\u05F4][\\uFB1D-\\uFB4F],.?!;:0-9%])|" +
            // or just a single Hebrew character
            "([[\\u0591-\\u05F4][\\uFB1D-\\uFB4F]])");
    private static final Pattern sHebrewVowelsPattern = Pattern.compile(
            "[[\\u0591-\\u05BD][\\u05BF\\u05C1\\u05C2\\u05C4\\u05C5\\u05C7]]");
    // private static final Pattern sBracketsPattern = Pattern.compile("[()\\[\\]{}]");
    // private static final Pattern sNumeralsPattern = Pattern.compile("[0-9][0-9%]+");

    /** Hide Question In Answer choices */
    private static final int HQIA_DO_HIDE = 0;
    private static final int HQIA_DO_SHOW = 1;
    private static final int HQIA_CARD_MODEL = 2;

    private static Card sEditorCard; // To be assigned as the currentCard or a new card to be sent to and from editor

    private static boolean sDisplayAnswer =  false; // Indicate if "show answer" button has been pressed

    /** The percentage of the absolute font size specified in the deck. */
    private int mDisplayFontSize = CardModel.DEFAULT_FONT_SIZE_RATIO;

    /**
     * Broadcast that informs us when the sd card is about to be unmounted
     */
    private BroadcastReceiver mUnmountReceiver = null;

    /**
     * Variables to hold preferences
     */
    private boolean mPrefTimer;
    private boolean mPrefWhiteboard;
    private boolean mPrefWriteAnswers;
    private boolean mPrefTextSelection;
    private boolean mPrefFullscreenReview;
    private boolean mshowNextReviewTime;
    private boolean mZoomEnabled;
    private boolean mZeemoteEnabled;
    private boolean mPrefUseRubySupport; // Parse for ruby annotations
    private String mDeckFilename;
    private int mPrefHideQuestionInAnswer; // Hide the question when showing the answer
    private int mRelativeButtonSize;
    private String mDictionaryAction;
    private int mDictionary;
    private boolean mGesturesEnabled;
    private boolean mShakeEnabled = false;
    private int mShakeIntensity;
    private boolean mShakeActionStarted = false;
    private boolean mPrefFixHebrew; // Apply manual RTL for hebrew text - bug in Android WebView
    private boolean mPrefFixArabic;
    private boolean mSpeakText;
    private boolean mPlaySoundsAtStart;
    private boolean mInvertedColors = false;
    private boolean mIsLastCard = false;
    private boolean mShowProgressBars;
    private boolean mPrefUseTimer;

    private boolean mIsDictionaryAvailable;
    private boolean mIsSelecting = false;

    @SuppressWarnings("unused")
    private boolean mUpdateNotifications; // TODO use Veecheck only if this is true

    private Sound mSound;
    private String mCardTemplate;

    /**
     * Searches
     */
    private static final int DICTIONARY_AEDICT = 0;
    private static final int DICTIONARY_LEO_WEB = 1;    // German web dictionary for English, French, Spanish, Italian, Chinese, Russian
    private static final int DICTIONARY_LEO_APP = 2;    // German web dictionary for English, French, Spanish, Italian, Chinese, Russian
    private static final int DICTIONARY_COLORDICT = 3;

    /**
     * Variables to hold layout objects that we need to update or handle events for
     */
    private View mMainLayout;
    private WebView mCard;
    private TextView mTextBarRed;
    private TextView mTextBarBlack;
    private TextView mTextBarBlue;
    private TextView mChosenAnswer;
    private LinearLayout mProgressBars;
    private View mDailyBar;
    private View mGlobalBar;
    private TextView mNext1;
    private TextView mNext2;
    private TextView mNext3;
    private TextView mNext4;
    private Button mFlipCard;
    private EditText mAnswerField;
    private Button mEase1;
    private Button mEase2;
    private Button mEase3;
    private Button mEase4;
    private Chronometer mCardTimer;
    private Whiteboard mWhiteboard;
    private ClipboardManager mClipboard;
    private ProgressDialog mProgressDialog;

    private Card mCurrentCard;
    private int mCurrentEase;
    private long mSessionTimeLimit;
    private int mSessionCurrReps;
    private float mScaleInPercent;
    private boolean mShowWhiteboard = false;

    private int mNextTimeTextColor;
    private int mNextTimeTextRecomColor;
    private int mForegroundColor;

    private int mButtonHeight = 0;

    private boolean mConfigurationChanged = false;
    private final int mShowChosenAnswerLength = 2000;

    private boolean mShowCongrats = false;

    private int mStatisticBarsMax;
    private int mStatisticBarsHeight;

    private boolean mClosing = false;

    private long mSavedTimer = 0;
	/**
	 * Shake Detection
	 */
	private SensorManager mSensorManager;
	private float mAccel; // acceleration apart from gravity
	private float mAccelCurrent; // current acceleration including gravity
	private float mAccelLast; // last acceleration including gravity

	/**
     * Swipe Detection
     */
 	private GestureDetector gestureDetector;
 	View.OnTouchListener gestureListener;

	/**
     * Gesture Allocation
     */
 	private int mGestureSwipeUp;
 	private int mGestureSwipeDown;
 	private int mGestureSwipeLeft;
 	private int mGestureSwipeRight;
 	private int mGestureShake;
 	private int mGestureDoubleTap;
 	private int mGestureTapLeft;
 	private int mGestureTapRight;
 	private int mGestureTapTop;
 	private int mGestureTapBottom;

 	private static final int GESTURE_NOTHING = 0;
 	private static final int GESTURE_ANSWER_EASE1 = 1;
 	private static final int GESTURE_ANSWER_EASE2 = 2;
 	private static final int GESTURE_ANSWER_EASE3 = 3;
 	private static final int GESTURE_ANSWER_EASE4 = 4;
 	private static final int GESTURE_ANSWER_RECOMMENDED = 5;
 	private static final int GESTURE_ANSWER_BETTER_THAN_RECOMMENDED = 6;
 	private static final int GESTURE_UNDO = 7;
 	private static final int GESTURE_REDO = 8;
 	private static final int GESTURE_EDIT = 9;
 	private static final int GESTURE_MARK = 10;
 	private static final int GESTURE_LOOKUP = 11;
 	private static final int GESTURE_BURY= 12;
 	private static final int GESTURE_SUSPEND = 13;
 	private static final int GESTURE_DELETE = 14;
 	private static final int GESTURE_CLEAR_WHITEBOARD = 15;
 	private static final int GESTURE_EXIT = 16;

 	/**
 	 * Zeemote controller
 	 */
 	//Controller controller = null;
 	ControllerAndroidUi controllerUi;

    private int zEase;


    public Reviewer() {
        super();
        // this.getMainLooper().setMessageLogging(new LogPrinter(Log.INFO, TAG));
    }

    // ----------------------------------------------------------------------------
    // LISTENERS
    // ----------------------------------------------------------------------------

    /**
     * From http://stackoverflow.com/questions/2317428/android-i-want-to-shake-it
     * Thilo Koehler
     */
 	private final SensorEventListener mSensorListener = new SensorEventListener() {
 	    public void onSensorChanged(SensorEvent se) {

 	      float x = se.values[0];
 	      float y = se.values[1];
 	      float z = se.values[2] / 2;
 	      mAccelLast = mAccelCurrent;
 	      mAccelCurrent = (float) Math.sqrt((x*x + y*y + z*z));
 	      float delta = mAccelCurrent - mAccelLast;
 	      mAccel = mAccel * 0.9f + delta; // perform low-cut filter
 	      if (!mShakeActionStarted && mAccel >= (mShakeIntensity / 10)) {
 	    	  mShakeActionStarted = true;
 	    	  executeCommand(mGestureShake);
 	      }
 	    }

 	    public void onAccuracyChanged(Sensor sensor, int accuracy) {
 	    }
 	  };


    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            List<URI> uris = (List<URI>) msg.obj;
            mSound.appendPlaylist(uris);
            mSound.play(uris);
        }
    };

    // Handler for the "show answer" button
    private final View.OnClickListener mFlipCardListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.i(TAG, "Flip card changed:");

            displayCardAnswer();
        }
    };

    private final View.OnClickListener mSelectEaseHandler = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.ease1:
                    answerCard(Card.EASE_FAILED);
                    break;
                case R.id.ease2:
                	answerCard(Card.EASE_HARD);
                    break;
                case R.id.ease3:
                	answerCard(Card.EASE_MID);
                    break;
                case R.id.ease4:
                	answerCard(Card.EASE_EASY);
                    break;
                default:
                	mCurrentEase = Card.EASE_NONE;
                    return;
            }
        }
    };


    private final View.OnLongClickListener mLongClickHandler = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            Log.i(TAG, "onLongClick()");
            Vibrator vibratorManager = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vibratorManager.vibrate(50);
            selectAndCopyText();
            return true;
        }
    };

    private final DeckTask.TaskListener mMarkCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
        	Resources res = getResources();
            mProgressDialog = ProgressDialog.show(Reviewer.this, "", res.getString(R.string.saving_changes), true);
        }

        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mCurrentCard = values[0].getCard();
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            mProgressDialog.dismiss();
        }
    };

    private final DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
        	Resources res = getResources();
        	mProgressDialog = ProgressDialog.show(Reviewer.this, "", res.getString(R.string.saving_changes), true);
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            mCurrentCard = values[0].getCard();
            if (mPrefWhiteboard) {
                mWhiteboard.clear();
            }

            if (mPrefTimer) {
                mCardTimer.setBase(SystemClock.elapsedRealtime());
                mCardTimer.start();
            }
            reviewNextCard();
            mProgressDialog.dismiss();
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            mShakeActionStarted = false;
        }
    };

    private final DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener() {
        private boolean mSessionComplete;
        private boolean mNoMoreCards;


        @Override
        public void onPreExecute() {
            Reviewer.this.setProgressBarIndeterminateVisibility(true);
            if (mPrefTimer) {
                mCardTimer.stop();
            }
            blockControls();
        }


        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            Resources res = getResources();
            mSessionComplete = false;
            mNoMoreCards = false;
            // Check to see if session rep or time limit has been reached
            Deck deck = AnkiDroidApp.deck();
            long sessionRepLimit = deck.getSessionRepLimit();
            long sessionTime = deck.getSessionTimeLimit();
            Toast sessionMessage = null;
            Toast leechMessage = null;
            Log.i(TAG, "reviewer leech flag: " + values[0].isPreviousCardLeech() + " "
                    + values[0].isPreviousCardSuspended());

            if (values[0].isPreviousCardLeech()) {
                if (values[0].isPreviousCardSuspended()) {
                    leechMessage = Toast.makeText(Reviewer.this, res.getString(R.string.leech_suspend_notification),
                        Toast.LENGTH_LONG);
                } else {
                    leechMessage = Toast.makeText(Reviewer.this, res.getString(R.string.leech_notification),
                            Toast.LENGTH_LONG);
                }
                leechMessage.show();
            }

            if ((sessionRepLimit > 0) && (mSessionCurrReps >= sessionRepLimit)) {
                mSessionComplete = true;
                sessionMessage = Toast.makeText(Reviewer.this, res.getString(R.string.session_question_limit_reached),
                        Toast.LENGTH_SHORT);
            } else if ((sessionTime > 0) && (System.currentTimeMillis() >= mSessionTimeLimit)) {
                // session time limit reached, flag for halt once async task has completed.
                mSessionComplete = true;
                sessionMessage = Toast.makeText(Reviewer.this, res.getString(R.string.session_time_limit_reached),
                        Toast.LENGTH_SHORT);
            } else if (mIsLastCard) {
                mNoMoreCards = true;
                mProgressDialog = ProgressDialog.show(Reviewer.this, "", getResources()
                        .getString(R.string.saving_changes), true);
            } else {
                // session limits not reached, show next card
                Card newCard = values[0].getCard();

                // If the card is null means that there are no more cards scheduled for review.
                if (newCard == null) {
                    mNoMoreCards = true;
                    mProgressDialog = ProgressDialog.show(Reviewer.this, "", getResources()
                            .getString(R.string.saving_changes), true);
                    return;
                }

                // Start reviewing next card
                mCurrentCard = newCard;
                if (mChosenAnswer.getText().equals("")) {
                    setDueMessage();
                }
                Reviewer.this.setProgressBarIndeterminateVisibility(false);
                // Reviewer.this.enableControls();
                Reviewer.this.unblockControls();
                Reviewer.this.reviewNextCard();
            }

            // Show a message to user if a session limit has been reached.
            if (sessionMessage != null) {
                sessionMessage.show();
            }
        }


        @Override
        public void onPostExecute(DeckTask.TaskData result) {
            // Check for no more cards before session complete. If they are both true,
            // no more cards will take precedence when returning to study options.
            if (mNoMoreCards) {
                Reviewer.this.setResult(RESULT_NO_MORE_CARDS);
                mShowCongrats = true;
                closeReviewer();
            } else if (mSessionComplete) {
                Reviewer.this.setResult(RESULT_SESSION_COMPLETED);
                closeReviewer();
            }
        }
    };


    DeckTask.TaskListener mSaveAndResetDeckHandler = new DeckTask.TaskListener() {
        @Override
        public void onPreExecute() {
        	if (mProgressDialog != null && mProgressDialog.isShowing()) {
        		mProgressDialog.setMessage(getResources().getString(R.string.saving_changes));
        	} else {
                mProgressDialog = ProgressDialog.show(Reviewer.this, "", getResources()
                        .getString(R.string.saving_changes), true);
        	}
        }
        @Override
        public void onPostExecute(DeckTask.TaskData result) {
        	if (mProgressDialog.isShowing()) {
                try {
                    mProgressDialog.dismiss();
                } catch (Exception e) {
                    Log.e(TAG, "onPostExecute() - Dialog dismiss Exception = " + e.getMessage());
                }
            }
        	finish();
        	if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
        		if (mShowCongrats) {
        			MyAnimation.slide(Reviewer.this, MyAnimation.FADE);
        		} else {
        			MyAnimation.slide(Reviewer.this, MyAnimation.RIGHT);
        		}
        	}
        }
        @Override
        public void onProgressUpdate(DeckTask.TaskData... values) {
            // Pass
        }
    };


    private final Handler mTimerHandler = new Handler();

    private final Runnable removeChosenAnswerText=new Runnable() {
    	public void run() {
    		mChosenAnswer.setText("");
    		mChosenAnswer.setTextColor(mForegroundColor);
    		setDueMessage();
    	}
    };

    //Zeemote handler
	Handler ZeemoteHandler = new Handler() {
		@Override
        public void handleMessage(Message msg){
			switch(msg.what){
			case MSG_ZEEMOTE_BUTTON_A:
				if (sDisplayAnswer) {
						if (mCurrentCard.isRev()) {
   						answerCard(Card.EASE_MID);
						} else {
							answerCard(Card.EASE_HARD);
						}
					} else {
						displayCardAnswer();
					}
				break;
			case MSG_ZEEMOTE_BUTTON_B:
				if (sDisplayAnswer) {
   					answerCard(Card.EASE_FAILED);
					} else {
   			        displayCardAnswer();
					}
				break;
			case MSG_ZEEMOTE_BUTTON_C:

				break;
			case MSG_ZEEMOTE_BUTTON_D:
				if (sDisplayAnswer) {
						if (mCurrentCard.isRev()) {
   						answerCard(Card.EASE_EASY);
						} else {
							answerCard(Card.EASE_MID);
						}
					} else {
						displayCardAnswer();
					}				break;
			}
			super.handleMessage(msg);
		}
	};
	private int mWaitSecond;



    // ----------------------------------------------------------------------------
    // ANDROID METHODS
    // ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "onCreate()");

        // The hardware buttons should control the music volume while reviewing.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Make sure a deck is loaded before continuing.
        Deck deck = AnkiDroidApp.deck();
        if (deck == null) {
            setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
            closeReviewer();
        } else {
            restorePreferences();

            //Zeemote controller initialization
    		if (mZeemoteEnabled){

    		 if (AnkiDroidApp.zeemoteController() == null) {
                AnkiDroidApp.setZeemoteController(new Controller(Controller.CONTROLLER_1));
            }
    		 controllerUi = new ControllerAndroidUi(this, AnkiDroidApp.zeemoteController());
    		 if (!AnkiDroidApp.zeemoteController().isConnected())
    		 {
        		 Log.d("Zeemote","starting connection in onCreate");
    			 controllerUi.startConnectionProcess();
    		 }
    		}

            deck.resetUndo();
            // Remove the status bar and title bar
            if (mPrefFullscreenReview) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FULLSCREEN);
                // Do not hide the title bar in Honeycomb, since it contains the action bar.
                if (Integer.valueOf(android.os.Build.VERSION.SDK) < 11) {
                    requestWindowFeature(Window.FEATURE_NO_TITLE);
                }
            }

            requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

            registerExternalStorageListener();

            initLayout(R.layout.flashcard);

            switch (mDictionary) {
            	case DICTIONARY_AEDICT:
            		mDictionaryAction = "sk.baka.aedict.action.ACTION_SEARCH_EDICT";
                    mIsDictionaryAvailable = Utils.isIntentAvailable(this, mDictionaryAction);
            		break;
                case DICTIONARY_LEO_WEB:
                    mDictionaryAction = "android.intent.action.VIEW";
                    mIsDictionaryAvailable = Utils.isIntentAvailable(this, mDictionaryAction);
                    break;
                case DICTIONARY_LEO_APP:
                    mDictionaryAction = "android.intent.action.SEND";
                    mIsDictionaryAvailable = Utils.isIntentAvailable(this, mDictionaryAction, new ComponentName("org.leo.android.dict", "org.leo.android.dict.LeoDict"));
                    break;
                case DICTIONARY_COLORDICT:
                    mDictionaryAction = "colordict.intent.action.SEARCH";
                    mIsDictionaryAvailable = Utils.isIntentAvailable(this, mDictionaryAction);
                    break;
                default:
                    mIsDictionaryAvailable = false;
                    break;
            }
            Log.i(TAG, "Is intent available = " + mIsDictionaryAvailable);

            // Load the template for the card and set on it the available width for images
            try {
                mCardTemplate = Utils.convertStreamToString(getAssets().open("card_template.html"));
                mCardTemplate = mCardTemplate.replaceFirst("var availableWidth = \\d*;", "var availableWidth = "
                        + getAvailableWidthInCard() + ";");
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Initialize session limits
            long timelimit = deck.getSessionTimeLimit() * 1000;
            Log.i(TAG, "SessionTimeLimit: " + timelimit + " ms.");
            mSessionTimeLimit = System.currentTimeMillis() + timelimit;
            mSessionCurrReps = 0;

            // Initialize text-to-speech. This is an asynchronous operation.
            if (mSpeakText && Integer.valueOf(android.os.Build.VERSION.SDK) > 3) {
            	ReadText.initializeTts(this, mDeckFilename);
            }

            // Get last whiteboard state
            if (mPrefWhiteboard && MetaDB.getWhiteboardState(this, mDeckFilename) == 1) {
            	mShowWhiteboard = true;
            	mWhiteboard.setVisibility(View.VISIBLE);
            }

            Log.i(TAG, "before");
            // Load the first card and start reviewing. Uses the answer card task to load a card, but since we send null
            // as the card to answer, no card will be answered.
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(0,
                    deck, null));
            Log.i(TAG, "middle");
            mSound = new Sound(getBaseContext(), deck.mediaDir().toURI());
            Log.i(TAG, "over");
        }
    }


    // Saves deck each time Reviewer activity loses focus
    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause()");

        // Stop visible timer and card timer
        if (mPrefTimer) {
            mSavedTimer = SystemClock.elapsedRealtime() - mCardTimer.getBase();
            mCardTimer.stop();
        }
        if (mCurrentCard != null) {
           mCurrentCard.stopTimer();
        }
        if (!mClosing) {
            // Save changes
            Deck deck = AnkiDroidApp.deck();
            deck.commitToDB();
        }

        if (mShakeEnabled) {
            mSensorManager.unregisterListener(mSensorListener);
        }

        mSound.stop();

        if (AnkiDroidApp.zeemoteController() != null) {
        	Log.d("Zeemote","Removing listener in onPause");
        	AnkiDroidApp.zeemoteController().removeButtonListener(this);
        }
    }

    @Override
    protected void onResume() {
      super.onResume();
      if (mShakeEnabled) {
          mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
      }
      if (mCurrentCard != null) {
          mCurrentCard.resumeTimer();
      }
      if (mPrefTimer && mSavedTimer != 0) {
          mCardTimer.setBase(SystemClock.elapsedRealtime() - mSavedTimer);
          mCardTimer.start();
      }
      if (AnkiDroidApp.zeemoteController() != null) {
    	  Log.d("Zeemote","Adding listener in onResume");
    	  AnkiDroidApp.zeemoteController().addButtonListener(this);
      }
    }

    @Override
    protected void onStop() {
      if (mShakeEnabled) {
          mSensorManager.unregisterListener(mSensorListener);
      }
      super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy()");
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
        }
        if (mSpeakText && Integer.valueOf(android.os.Build.VERSION.SDK) > 3) {
            ReadText.releaseTts();
        }
        if ((AnkiDroidApp.zeemoteController() != null) && (AnkiDroidApp.zeemoteController().isConnected())){
        	try {
        		Log.d("Zeemote","trying to disconnect in onDestroy...");
        		AnkiDroidApp.zeemoteController().disconnect();
        	}
        	catch (IOException ex){
        		Log.e("Zeemote","Error on zeemote disconnection in onDestroy: "+ex.getMessage());
        	}
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            Log.i(TAG, "onBackPressed()");
        	closeReviewer();
        	return true;
        }

        return super.onKeyDown(keyCode, event);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        Log.i(TAG, "onConfigurationChanged()");

        mConfigurationChanged = true;

        long savedTimer = mCardTimer.getBase();
        CharSequence savedAnswerField = mAnswerField.getText();

        // Reload layout
        initLayout(R.layout.flashcard);

       	if (mRelativeButtonSize != 100) {
       		mFlipCard.setHeight(mButtonHeight);
       		mEase1.setHeight(mButtonHeight);
       		mEase2.setHeight(mButtonHeight);
       		mEase3.setHeight(mButtonHeight);
       		mEase4.setHeight(mButtonHeight);
       	}

        // Modify the card template to indicate the new available width and refresh card
        mCardTemplate = mCardTemplate.replaceFirst("var availableWidth = \\d*;", "var availableWidth = "
                + getAvailableWidthInCard() + ";");

        // If the card hasn't loaded yet, don't refresh it
        // Also skipping the counts (because we don't know which one to underline)
        // They will be updated when the card loads anyway
        if (mCurrentCard != null) {
            refreshCard();
            updateScreenCounts();
        }

        if (mPrefTimer) {
            mCardTimer.setBase(savedTimer);
            mCardTimer.start();
        }
        if (mPrefWriteAnswers) {
            mAnswerField.setText(savedAnswerField);
        }
        if (mPrefWhiteboard) {
            mWhiteboard.rotate();
        }
        if (mInvertedColors) {
            invertColors();
        }
        updateStatisticBars();
        mConfigurationChanged = false;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        Resources res = getResources();
        if (mPrefWhiteboard) {
            if (mShowWhiteboard) {
                Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_WHITEBOARD, Menu.NONE,
                        R.string.hide_whiteboard, R.drawable.ic_menu_compose);
            } else {
                Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_WHITEBOARD, Menu.NONE,
                        R.string.show_whiteboard, R.drawable.ic_menu_compose);
            }
            Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_CLEAR_WHITEBOARD, Menu.NONE,
                    R.string.clear_whiteboard, R.drawable.ic_menu_clear_playlist);
        }
        Utils.addMenuItem(menu, Menu.NONE, MENU_EDIT, Menu.NONE, R.string.menu_edit_card,
                R.drawable.ic_menu_edit);

        SubMenu removeDeckSubMenu = menu.addSubMenu(Menu.NONE, MENU_REMOVE, Menu.NONE, R.string.menu_remove_card);
        removeDeckSubMenu.setIcon(R.drawable.ic_menu_stop);
        removeDeckSubMenu.add(Menu.NONE, MENU_REMOVE_BURY, Menu.NONE, R.string.menu_bury_card);
        removeDeckSubMenu.add(Menu.NONE, MENU_REMOVE_SUSPEND, Menu.NONE, R.string.menu_suspend_card);
        removeDeckSubMenu.add(Menu.NONE, MENU_REMOVE_DELETE, Menu.NONE, R.string.card_browser_delete_card);
        if (mPrefTextSelection) {
            item = menu.add(Menu.NONE, MENU_SEARCH, Menu.NONE, String.format(getString(R.string.menu_search),
            			res.getStringArray(R.array.dictionary_labels)[mDictionary]));
            item.setIcon(R.drawable.ic_menu_search);
        }
        item = menu.add(Menu.NONE, MENU_MARK, Menu.NONE, R.string.menu_mark_card);
        Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_UNDO, Menu.NONE, R.string.undo,
                R.drawable.ic_menu_revert);
        Utils.addMenuItemInActionBar(menu, Menu.NONE, MENU_REDO, Menu.NONE, R.string.redo,
                R.drawable.ic_menu_redo);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(MENU_MARK);
        if (mCurrentCard == null){
        	return false;
        }
        if (mCurrentCard.isMarked()) {
            item.setTitle(R.string.menu_marked);
            item.setIcon(R.drawable.ic_menu_star_on);
        } else {
            item.setTitle(R.string.menu_mark_card);
            item.setIcon(R.drawable.ic_menu_star_off);
        }
        if (mPrefTextSelection) {
            item = menu.findItem(MENU_SEARCH);
            Log.i(TAG, "Clipboard has text = " + mClipboard.hasText());
            boolean lookupPossible = mClipboard.hasText() && mIsDictionaryAvailable;
            item.setEnabled(lookupPossible);
        }
        if (mPrefFullscreenReview) {
            // Temporarily remove top bar to avoid annoying screen flickering
            mTextBarRed.setVisibility(View.GONE);
            mTextBarBlack.setVisibility(View.GONE);
            mTextBarBlue.setVisibility(View.GONE);
            mChosenAnswer.setVisibility(View.GONE);
            if (mPrefTimer) {
                mCardTimer.setVisibility(View.GONE);
            }
            if (mShowProgressBars) {
                mProgressBars.setVisibility(View.GONE);
            }

            getWindow().setFlags(0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        menu.findItem(MENU_UNDO).setEnabled(AnkiDroidApp.deck().undoAvailable());
        menu.findItem(MENU_REDO).setEnabled(AnkiDroidApp.deck().redoAvailable());
        return true;
    }


    @Override
    public void onOptionsMenuClosed(Menu menu) {
        if (mPrefFullscreenReview) {
            // Restore top bar
            mTextBarRed.setVisibility(View.VISIBLE);
            mTextBarBlack.setVisibility(View.VISIBLE);
            mTextBarBlue.setVisibility(View.VISIBLE);
            mChosenAnswer.setVisibility(View.VISIBLE);
            if (mPrefTimer) {
                mCardTimer.setVisibility(View.VISIBLE);
            }
            if (mShowProgressBars) {
                mProgressBars.setVisibility(View.VISIBLE);
            }

            // Restore fullscreen preference
            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }


    /** Handles item selections. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_WHITEBOARD:
                // Toggle mShowWhiteboard value
                mShowWhiteboard = !mShowWhiteboard;
                if (mShowWhiteboard) {
                    // Show whiteboard
                    mWhiteboard.setVisibility(View.VISIBLE);
                    item.setTitle(R.string.hide_whiteboard);
                    MetaDB.storeWhiteboardState(this, mDeckFilename, 1);
                } else {
                    // Hide whiteboard
                    mWhiteboard.setVisibility(View.GONE);
                    item.setTitle(R.string.show_whiteboard);
                    MetaDB.storeWhiteboardState(this, mDeckFilename, 0);
                }
                return true;

            case MENU_CLEAR_WHITEBOARD:
                mWhiteboard.clear();
                return true;

            case MENU_EDIT:
            	return editCard();

            case MENU_REMOVE_BURY:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_BURY_CARD, mAnswerCardHandler, new DeckTask.TaskData(0,
                        AnkiDroidApp.deck(), mCurrentCard));
                return true;

            case MENU_REMOVE_SUSPEND:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SUSPEND_CARD, mAnswerCardHandler, new DeckTask.TaskData(0,
                        AnkiDroidApp.deck(), mCurrentCard));
                return true;

            case MENU_REMOVE_DELETE:
                showDeleteCardDialog();
                return true;

            case MENU_SEARCH:
                return lookUp();

            case MENU_MARK:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(0,
                        AnkiDroidApp.deck(), mCurrentCard));
                return true;

            case MENU_UNDO:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUpdateCardHandler, new DeckTask.TaskData(0,
                        AnkiDroidApp.deck(), mCurrentCard.getId(), false));
                return true;

            case MENU_REDO:
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REDO, mUpdateCardHandler, new DeckTask.TaskData(0,
                        AnkiDroidApp.deck(), mCurrentCard.getId(), false));
                return true;
        }
        return false;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == EDIT_CURRENT_CARD) {
            if (resultCode == RESULT_OK) {
                Log.i(TAG, "Saving card...");
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UPDATE_FACT, mUpdateCardHandler, new DeckTask.TaskData(0,
                        AnkiDroidApp.deck(), mCurrentCard));
                // TODO: code to save the changes made to the current card.
                displayCardQuestion();
            } else if (resultCode == StudyOptions.CONTENT_NO_EXTERNAL_STORAGE) {
                finishNoStorageAvailable();
            }
        }
    }

    private boolean isCramming() {
        return (AnkiDroidApp.deck() != null) && (AnkiDroidApp.deck().name().compareTo("cram") == 0);
    }

    // ----------------------------------------------------------------------------
    // CUSTOM METHODS
    // ----------------------------------------------------------------------------

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications. The intent will call
     * closeExternalStorageFiles() if the external media is going to be ejected, so applications can clean up any files
     * they have open.
     */
    private void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        Log.i(TAG, "mUnmountReceiver - Action = Media Eject");
                        finishNoStorageAvailable();
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }


    private String getLanguage(int questionAnswer) {
    	String language = MetaDB.getLanguage(this, mDeckFilename,  Model.getModel(AnkiDroidApp.deck(), mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId(), questionAnswer);
		return language;
    }

    private void storeLanguage(String language, int questionAnswer) {
    	MetaDB.storeLanguage(this, mDeckFilename,  Model.getModel(AnkiDroidApp.deck(), mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId(), questionAnswer, language);
    }


    private void finishNoStorageAvailable() {
        setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
        closeReviewer();
    }


    private boolean editCard() {
        if (isCramming()) {
            Toast cramEditWarning =
                Toast.makeText(Reviewer.this,
                        getResources().getString(R.string.cram_edit_warning), Toast.LENGTH_SHORT);
            cramEditWarning.show();
            return false;
        } else {
            Intent editCard = new Intent(Reviewer.this, CardEditor.class);
        	sEditorCard = mCurrentCard;
            startActivityForResult(editCard, EDIT_CURRENT_CARD);
            if (Integer.valueOf(android.os.Build.VERSION.SDK) > 4) {
                MyAnimation.slide(Reviewer.this, MyAnimation.LEFT);
            }
            return true;
        }
    }


    private boolean lookUp() {
    	if (mPrefTextSelection && mClipboard.hasText() && mIsDictionaryAvailable) {
    	    mIsSelecting = false;
    		switch (mDictionary) {
            	case DICTIONARY_AEDICT:
            		Intent aedictSearchIntent = new Intent(mDictionaryAction);
            		aedictSearchIntent.putExtra("kanjis", mClipboard.getText());
            		startActivity(aedictSearchIntent);
                    mClipboard.setText("");
                    return true;
            	case DICTIONARY_LEO_WEB:
            		// localisation is needless here since leo.org translates only into or out of German
            		final CharSequence[] itemValues = {"en", "fr", "es", "it", "ch", "ru"};
            		String language = getLanguage(MetaDB.LANGUAGE_UNDEFINED);
            		for (int i = 0; i < itemValues.length; i++) {
                		if (language.equals(itemValues[i])) {
            		    	Intent leoSearchIntent = new Intent(mDictionaryAction, Uri.parse("http://pda.leo.org/?lp=" + language + "de&search=" + mClipboard.getText()));
                    		startActivity(leoSearchIntent);
                            mClipboard.setText("");
                            return true;
                		}
            		}
            		final CharSequence[] items = {"Englisch", "Franz√∂sisch", "Spanisch", "Italienisch", "Chinesisch", "Russisch"};
            		AlertDialog.Builder builder = new AlertDialog.Builder(this);
            		builder.setTitle("\"" + mClipboard.getText() + "\" nachschlagen");
            		builder.setItems(items, new DialogInterface.OnClickListener() {
            			public void onClick(DialogInterface dialog, int item) {
            				String language = itemValues[item].toString();
            				Intent leoSearchIntent = new Intent(mDictionaryAction, Uri.parse("http://pda.leo.org/?lp=" + language + "de&search=" + mClipboard.getText()));
            				startActivity(leoSearchIntent);
            				mClipboard.setText("");
            				storeLanguage(language, MetaDB.LANGUAGE_UNDEFINED);
            			}
            		});
            		AlertDialog alert = builder.create();
            		alert.show();
            		return true;
                case DICTIONARY_LEO_APP:
                    Intent leoSearchIntent = new Intent(mDictionaryAction);
                    leoSearchIntent.putExtra(Intent.EXTRA_TEXT, mClipboard.getText());
                    leoSearchIntent.setComponent(new ComponentName("org.leo.android.dict", "org.leo.android.dict.LeoDict"));
                    startActivity(leoSearchIntent);
                    mClipboard.setText("");
                    return true;
            	case DICTIONARY_COLORDICT:
            		Intent colordictSearchIntent = new Intent(mDictionaryAction);
            		colordictSearchIntent.putExtra("EXTRA_QUERY", mClipboard.getText());
            		startActivity(colordictSearchIntent);
                    mClipboard.setText("");
                    return true;
        	}
        }
        return true;
    }


    private void showDeleteCardDialog() {
        Dialog dialog;
        Resources res = getResources();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(res.getString(R.string.delete_card_title));
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(String.format(res.getString(R.string.delete_card_message), "some question", "some answer"));
        builder.setPositiveButton(res.getString(R.string.yes),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_DELETE_CARD, mAnswerCardHandler, new DeckTask.TaskData(0, AnkiDroidApp.deck(), mCurrentCard));
                    }
                });
        builder.setNegativeButton(res.getString(R.string.no), null);
        dialog = builder.create();
        dialog.show();
    }


    private void answerCard(int ease) {
        mIsSelecting = false;
        Deck deck = AnkiDroidApp.deck();
    	switch (ease) {
    		case Card.EASE_FAILED:
    		    mChosenAnswer.setText("\u2022");
    		    mChosenAnswer.setTextColor(mNext1.getTextColors());
    	    	if ((deck.getDueCount() + deck.getNewCountToday()) == 1) {
    	    		mIsLastCard = true;
                }
    			break;
    		case Card.EASE_HARD:
                mChosenAnswer.setText("\u2022\u2022");
                mChosenAnswer.setTextColor(mNext2.getTextColors());
    			break;
    		case Card.EASE_MID:
                mChosenAnswer.setText("\u2022\u2022\u2022");
                mChosenAnswer.setTextColor(mNext3.getTextColors());
    			break;
    		case Card.EASE_EASY:
                mChosenAnswer.setText("\u2022\u2022\u2022\u2022");
                mChosenAnswer.setTextColor(mNext4.getTextColors());
    			break;
    	}
    	mTimerHandler.removeCallbacks(removeChosenAnswerText);
    	mTimerHandler.postDelayed(removeChosenAnswerText, mShowChosenAnswerLength);
        mSound.stop();
    	mCurrentEase = ease;
        // Increment number reps counter
        mSessionCurrReps++;
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_ANSWER_CARD, mAnswerCardHandler, new DeckTask.TaskData(
                mCurrentEase, deck, mCurrentCard));
    }


    // Set the content view to the one provided and initialize accessors.
    private void initLayout(Integer layout) {
        setContentView(layout);

        mMainLayout = findViewById(R.id.main_layout);

        mCard = (WebView) findViewById(R.id.flashcard);
        mCard.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        if (mZoomEnabled) {
        	mCard.getSettings().setBuiltInZoomControls(true);
        }
        mCard.getSettings().setJavaScriptEnabled(true);
        mCard.setWebChromeClient(new AnkiDroidWebChromeClient());
        // anki_card, anki_sound, anki_config
        // FIXME: add Javascript interface for cardHistory: reps, interval, successive
        mCard.addJavascriptInterface(new AnkiAudio(), "anki_audio");
        if (Integer.parseInt(android.os.Build.VERSION.SDK) > 7) {
            mCard.setFocusableInTouchMode(false);
        }
        Log.i(TAG, "Focusable = " + mCard.isFocusable() + ", Focusable in touch mode = "
                + mCard.isFocusableInTouchMode());

        // Initialize swipe
        gestureDetector = new GestureDetector(new MyGestureDetector());

        // Initialize shake detection
        if (mShakeEnabled) {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
            mAccel = 0.00f;
            mAccelCurrent = SensorManager.GRAVITY_EARTH;
            mAccelLast = SensorManager.GRAVITY_EARTH;
        }

        if (mPrefTextSelection) {
			mCard.setOnLongClickListener(mLongClickHandler);
			mClipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        }
        mCard.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View v, MotionEvent event) {
				    if (gestureDetector.onTouchEvent(event)) {
                    	return true;
                	}
                	return false;
				}
           	});

        mScaleInPercent = mCard.getScale();

        mEase1 = (Button) findViewById(R.id.ease1);
        mEase1.setOnClickListener(mSelectEaseHandler);

        mEase2 = (Button) findViewById(R.id.ease2);
        mEase2.setOnClickListener(mSelectEaseHandler);

        mEase3 = (Button) findViewById(R.id.ease3);
        mEase3.setOnClickListener(mSelectEaseHandler);

        mEase4 = (Button) findViewById(R.id.ease4);
        mEase4.setOnClickListener(mSelectEaseHandler);

        mNext1 = (TextView) findViewById(R.id.nextTime1);
        mNext2 = (TextView) findViewById(R.id.nextTime2);
        mNext3 = (TextView) findViewById(R.id.nextTime3);
        mNext4 = (TextView) findViewById(R.id.nextTime4);

        mFlipCard = (Button) findViewById(R.id.flip_card);
        mFlipCard.setOnClickListener(mFlipCardListener);
        mFlipCard.setText(getResources().getString(R.string.show_answer));

        mTextBarRed = (TextView) findViewById(R.id.red_number);
        mTextBarBlack = (TextView) findViewById(R.id.black_number);
        mTextBarBlue = (TextView) findViewById(R.id.blue_number);

        if (mShowProgressBars) {
            mDailyBar = findViewById(R.id.daily_bar);
            mGlobalBar = findViewById(R.id.global_bar);
            mProgressBars = (LinearLayout) findViewById(R.id.progress_bars);
        }

        mCardTimer = (Chronometer) findViewById(R.id.card_time);
        float headTextSize = (float) (mCardTimer.getTextSize() * 0.63);
        mCardTimer.setTextSize(headTextSize);

        mChosenAnswer = (TextView) findViewById(R.id.choosen_answer);
        mChosenAnswer.setTextSize((float) (headTextSize * 1.02));

        if (mPrefWhiteboard) {
            mWhiteboard = new Whiteboard(this, null);
            FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
            mWhiteboard.setLayoutParams(lp2);
            FrameLayout fl = (FrameLayout) findViewById(R.id.whiteboard);
            fl.addView(mWhiteboard);

            mWhiteboard.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (mShowWhiteboard) {
                        return false;
                    }
                    if (gestureDetector.onTouchEvent(event)) {
                        return true;
                    }
                    return false;
                }
            });
        }
        mAnswerField = (EditText) findViewById(R.id.answer_field);

        if (mInvertedColors) {
            invertColors();
        } else {
            mNextTimeTextColor = getResources().getColor(R.color.next_time_usual_color);
            mNextTimeTextRecomColor = getResources().getColor(R.color.next_time_recommended_color);
            mForegroundColor = getResources().getColor(R.color.next_time_usual_color);
        }

        initControls();
    }


    private void invertColors() {
        Resources res = getResources();
        int bgColor = res.getColor(R.color.background_color_inv);
        int fgColor = res.getColor(R.color.foreground_color_inv);
        mMainLayout.setBackgroundColor(bgColor);
        mNextTimeTextColor = res.getColor(R.color.next_time_usual_color_inv);
        mNextTimeTextRecomColor = res.getColor(R.color.next_time_recommended_color_inv);
        mNext4.setTextColor(mNextTimeTextColor);
        mCardTimer.setTextColor(fgColor);
        mForegroundColor = fgColor;
        mTextBarBlack.setTextColor(fgColor);
        mTextBarBlue.setTextColor(res.getColor(R.color.textbar_blue_color_inv));
        mCard.setBackgroundColor(res.getColor(R.color.background_color_inv));
        if (mPrefWhiteboard) {
            mWhiteboard.setInvertedColor(true);
        }
        mFlipCard.setBackgroundDrawable(res.getDrawable(R.drawable.btn_keyboard_key_fulltrans_normal));
        mEase1.setBackgroundDrawable(res.getDrawable(R.drawable.btn_keyboard_key_fulltrans_normal));
        mEase2.setBackgroundDrawable(res.getDrawable(R.drawable.btn_keyboard_key_fulltrans_normal));
        mEase3.setBackgroundDrawable(res.getDrawable(R.drawable.btn_keyboard_key_fulltrans_normal));
        mEase4.setBackgroundDrawable(res.getDrawable(R.drawable.btn_keyboard_key_fulltrans_normal));
        mFlipCard.setTextColor(fgColor);
        mEase1.setTextColor(fgColor);
        mEase2.setTextColor(fgColor);
        mEase3.setTextColor(fgColor);
        mEase4.setTextColor(fgColor);

        fgColor = res.getColor(R.color.progressbar_border_inverted);
        bgColor = res.getColor(R.color.progressbar_background_inverted);
        findViewById(R.id.progress_bars_border1).setBackgroundColor(fgColor);
        findViewById(R.id.progress_bars_border2).setBackgroundColor(fgColor);
        findViewById(R.id.progress_bars_back1).setBackgroundColor(bgColor);
        findViewById(R.id.progress_bars_back2).setBackgroundColor(bgColor);
    }


    private void showEaseButtons() {
        Resources res = getResources();

        // Set correct label for each button
        if (mCurrentCard.isRev()) {
            mEase1.setText(res.getString(R.string.ease1_successive));
            mEase2.setText(res.getString(R.string.ease2_successive));
            mEase3.setText(res.getString(R.string.ease3_successive));
            mEase4.setText(res.getString(R.string.ease4_successive));

        } else {
            mEase1.setText(res.getString(R.string.ease1_learning));
            mEase2.setText(res.getString(R.string.ease2_learning));
            mEase3.setText(res.getString(R.string.ease3_learning));
            mEase4.setText(res.getString(R.string.ease4_learning));
        }

        // Show buttons
        mEase1.setVisibility(View.VISIBLE);
        mEase2.setVisibility(View.VISIBLE);
        mEase3.setVisibility(View.VISIBLE);
        mEase4.setVisibility(View.VISIBLE);

        // Show next review time
        if (mshowNextReviewTime) {
        mNext1.setText(nextInterval(1));
        mNext2.setText(nextInterval(2));
        mNext3.setText(nextInterval(3));
        mNext4.setText(nextInterval(4));
        mNext1.setVisibility(View.VISIBLE);
        mNext2.setVisibility(View.VISIBLE);
        mNext3.setVisibility(View.VISIBLE);
        mNext4.setVisibility(View.VISIBLE);
        }

        // Focus default button
        if (mCurrentCard.isRev()) {
            mEase3.requestFocus();
            mNext2.setTextColor(mNextTimeTextColor);
            mNext3.setTextColor(mNextTimeTextRecomColor);
        } else {
            mEase2.requestFocus();
            mNext2.setTextColor(mNextTimeTextRecomColor);
            mNext3.setTextColor(mNextTimeTextColor);
        }
    }


    private void hideEaseButtons() {
        // GONE -> It allows to write until the very bottom
        // INVISIBLE -> The transition between the question and the answer seems more smooth
        mEase1.setVisibility(View.GONE);
        mEase2.setVisibility(View.GONE);
        mEase3.setVisibility(View.GONE);
        mEase4.setVisibility(View.GONE);
        mNext1.setVisibility(View.INVISIBLE);
        mNext2.setVisibility(View.INVISIBLE);
        mNext3.setVisibility(View.INVISIBLE);
        mNext4.setVisibility(View.INVISIBLE);
    }


    private void initControls() {
        mCard.setVisibility(View.VISIBLE);
        mTextBarRed.setVisibility(View.VISIBLE);
        mTextBarBlack.setVisibility(View.VISIBLE);
        mTextBarBlue.setVisibility(View.VISIBLE);
        mChosenAnswer.setVisibility(View.VISIBLE);
        mFlipCard.setVisibility(View.VISIBLE);

        mCardTimer.setVisibility((mPrefTimer) ? View.VISIBLE : View.GONE);
        if (mShowProgressBars) {
            mProgressBars.setVisibility(View.VISIBLE);
        }
        if (mPrefWhiteboard) {
            mWhiteboard.setVisibility(mShowWhiteboard ? View.VISIBLE : View.GONE);
        }
        mAnswerField.setVisibility((mPrefWriteAnswers) ? View.VISIBLE : View.GONE);
    }


    private SharedPreferences restorePreferences() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        mPrefTimer = preferences.getBoolean("timer", true);
        mPrefWhiteboard = preferences.getBoolean("whiteboard", false);
        mPrefWriteAnswers = preferences.getBoolean("writeAnswers", false);
        mPrefTextSelection = preferences.getBoolean("textSelection", false);
        mDeckFilename = preferences.getString("deckFilename", "");
        mInvertedColors = preferences.getBoolean("invertedColors", false);
        mPrefUseRubySupport = preferences.getBoolean("useRubySupport", false);
        mPrefFullscreenReview = preferences.getBoolean("fullscreenReview", true);
        mshowNextReviewTime = preferences.getBoolean("showNextReviewTime", true);
        mZoomEnabled = preferences.getBoolean("zoom", false);
        mZeemoteEnabled = preferences.getBoolean("zeemote", false);
        mDisplayFontSize = preferences.getInt("relativeDisplayFontSize", CardModel.DEFAULT_FONT_SIZE_RATIO);
        mRelativeButtonSize = preferences.getInt("answerButtonSize", 100);
        mPrefHideQuestionInAnswer = Integer.parseInt(preferences.getString("hideQuestionInAnswer",
                Integer.toString(HQIA_DO_SHOW)));
        mDictionary = Integer.parseInt(preferences.getString("dictionary",
                Integer.toString(DICTIONARY_AEDICT)));
        mPrefFixHebrew = preferences.getBoolean("fixHebrewText", false);
        mPrefFixArabic = preferences.getBoolean("fixArabicText", false);
        mSpeakText = preferences.getBoolean("tts", false);
        mPlaySoundsAtStart = preferences.getBoolean("playSoundsAtStart", true);
        mShowProgressBars = preferences.getBoolean("progressBars", true);
        mPrefUseTimer = preferences.getBoolean("timeoutAnswer", false);
        mWaitSecond = preferences.getInt("timeoutAnswerSeconds", 20);

        mGesturesEnabled = preferences.getBoolean("swipe", false);
        if (mGesturesEnabled) {
         	mGestureShake = Integer.parseInt(preferences.getString("gestureShake", "0"));
         	if (mGestureShake != 0) {
         		mShakeEnabled = true;
         	}
            mShakeIntensity = preferences.getInt("minShakeIntensity", 70);

            mGestureSwipeUp = Integer.parseInt(preferences.getString("gestureSwipeUp", "0"));
         	mGestureSwipeDown = Integer.parseInt(preferences.getString("gestureSwipeDown", "0"));
         	mGestureSwipeLeft = Integer.parseInt(preferences.getString("gestureSwipeLeft", "13"));
         	mGestureSwipeRight = Integer.parseInt(preferences.getString("gestureSwipeRight", "0"));
         	mGestureDoubleTap = Integer.parseInt(preferences.getString("gestureDoubleTap", "0"));
         	mGestureTapLeft = Integer.parseInt(preferences.getString("gestureTapLeft", "0"));
         	mGestureTapRight = Integer.parseInt(preferences.getString("gestureTapRight", "0"));
         	mGestureTapTop = Integer.parseInt(preferences.getString("gestureTapTop", "0"));
         	mGestureTapBottom = Integer.parseInt(preferences.getString("gestureTapBottom", "0"));
        }

        return preferences;
    }


    private void refreshCard() {
        if (sDisplayAnswer) {
            displayCardAnswer();
        } else {
            displayCardQuestion();
        }
    }


    private void setDueMessage() {
		if (mCurrentCard != null && AnkiDroidApp.deck().getScheduler().equals("reviewEarly")) {
			double due = (mCurrentCard.getCombinedDue() - Utils.now()) / 86400.0;
			if (due > 0.041) {
	    		mChosenAnswer.setText(Utils.getReadableInterval(Reviewer.this, due, true));
			}
		}
    }


    private void reviewNextCard() {
    	updateScreenCounts();
    	if (mShowProgressBars) {
            updateStatisticBars();
    	}

        // Clean answer field
        if (mPrefWriteAnswers) {
            mAnswerField.setText("");
        }

        if (mPrefWhiteboard) {
            mWhiteboard.clear();
        }

        if (mPrefTimer) {
            mCardTimer.setBase(SystemClock.elapsedRealtime());
            mCardTimer.start();
        }

        displayCardQuestion();
    }


    private void updateScreenCounts() {
        Deck deck = AnkiDroidApp.deck();
        int eta = deck.getETA();
        if (deck.hasFinishScheduler() || eta < 1) {
            setTitle(deck.getDeckName());
        } else {
            setTitle(getResources().getQuantityString(R.plurals.reviewer_window_title, eta, deck.getDeckName(), eta));
        }

        int _failedSoonCount = deck.getFailedSoonCount();
        int _revCount = deck.getRevCount();
        int _newCount = deck.getNewCountToday();

        SpannableString failedSoonCount = new SpannableString(String.valueOf(_failedSoonCount));
        SpannableString revCount = new SpannableString(String.valueOf(_revCount));
        SpannableString newCount = new SpannableString(String.valueOf(_newCount));

        boolean isDue = true; // mCurrentCard.isDue();
        int type = mCurrentCard.getType();

        if (isDue && (type == Card.TYPE_NEW)) {
            newCount.setSpan(new UnderlineSpan(), 0, newCount.length(), 0);
        }
        if (isDue && (type == Card.TYPE_REV)) {
            revCount.setSpan(new UnderlineSpan(), 0, revCount.length(), 0);
        }
        if (isDue && (type == Card.TYPE_FAILED)) {
            failedSoonCount.setSpan(new UnderlineSpan(), 0, failedSoonCount.length(), 0);
        }

        mTextBarRed.setText(failedSoonCount);
        mTextBarBlack.setText(revCount);
        mTextBarBlue.setText(newCount);
    }


    private void updateStatisticBars() {
        if (mStatisticBarsMax == 0) {
            View view = findViewById(R.id.progress_bars_back1);
            mStatisticBarsMax = view.getWidth();
            mStatisticBarsHeight = view.getHeight();
        }
        Deck deck = AnkiDroidApp.deck();
        Utils.updateProgressBars(this, mDailyBar, deck.getProgress(false), mStatisticBarsMax, mStatisticBarsHeight, true);
        Utils.updateProgressBars(this, mGlobalBar, deck.getProgress(true), mStatisticBarsMax, mStatisticBarsHeight, true);
    }

    private final Handler mTimeoutHandler = new Handler();

    private final Runnable mShowAnswerTask=new Runnable() {
	public void run() {
            if (mPrefTimer) {
                mCardTimer.stop();
            }
            mFlipCard.performClick();
	}
    };

    private void displayCardQuestion() {
        sDisplayAnswer = false;

        if (mButtonHeight == 0 && mRelativeButtonSize != 100) {
        	mButtonHeight = mFlipCard.getHeight() * mRelativeButtonSize / 100;
        	mFlipCard.setHeight(mButtonHeight);
        	mEase1.setHeight(mButtonHeight);
        	mEase2.setHeight(mButtonHeight);
        	mEase3.setHeight(mButtonHeight);
        	mEase4.setHeight(mButtonHeight);
        }

        // If the user wants to write the answer
        if (mPrefWriteAnswers) {
            mAnswerField.setVisibility(View.VISIBLE);

            // Show soft keyboard
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(mAnswerField, InputMethodManager.SHOW_FORCED);
        }

        mFlipCard.setVisibility(View.VISIBLE);
        mFlipCard.requestFocus();

        // if (showQuestionOnAnswer()) ... <hr>;; that is: pass the configuration to getHtmlPage()
        String card = mCurrentCard.getHtmlPage();
        mSound.stop();
        mSound.setPlaylist(new ArrayList<URI>());

        Log.i(TAG, "card: '" + card + "'");

        if (mSpeakText && Integer.valueOf(android.os.Build.VERSION.SDK) > 3) {
            ReadText.setLanguageInformation(Model.getModel(AnkiDroidApp.deck(), mCurrentCard.getCardModelId(), false).getId(), mCurrentCard.getCardModelId());
        }

        Deck currentDeck = AnkiDroidApp.deck();
        if (mPrefFixArabic) {
            card = ArabicUtilities.reshapeSentence(card, true);
        }
        if (isHebrewFixEnabled()) {
            card = applyFixForHebrew(card);
        }

        // include AnkiAndroid CSS (global resizes?, color invert? replace("font-weight:600;", "font-weight:700;")?)
        // include deck CSS (I support a one-deck-one-model philosophy), so there is only one CSS per deck
        // Model myModel = Model.getModel(currentDeck, mCurrentCard.getCardModelId(), false);
        // writeExternalFile("model_style.css", myModel.getCSSForFontColorSize(mCurrentCard.getCardModelId(),
        // mDisplayFontSize, mInvertedColors));
        // mMainLayout.setBackgroundColor(Color.parseColor(myModel.getBackgroundColor(mCurrentCard.getCardModelId(),
        // mInvertedColors)));

        // empty at the moment
        // writeExternalFile("customFonts.css", getCustomFontsStyle());
        // writeExternalFile("defaultFont.css", getDefaultFontStyle());
        // writeExternalFile("deck_style.css", getDeckStyle(mCurrentCard.mDeck.getDeckPath()));

        String baseUrl = currentDeck.getBaseUrl();
        Log.i(TAG, "base url = " + baseUrl);
        mCard.loadDataWithBaseURL(baseUrl, card, "text/html", "utf-8", null);

        // onload() collects and passes the audio and initiates the playback once.

        hideEaseButtons();

        // If the user want to show answer automatically
        if (mPrefUseTimer) {
            mTimeoutHandler.removeCallbacks(mShowAnswerTask);
            mTimeoutHandler.postDelayed(mShowAnswerTask, mWaitSecond * 1000  );
        }
    }


    private void displayCardAnswer() {
        Log.i(TAG, "displayCardAnswer()");
        sDisplayAnswer = true;

        if (mPrefWriteAnswers) {
            mAnswerField.setVisibility(View.GONE);
            String userAnswer = mAnswerField.getText().toString();
            String correctAnswer = "not implemented"; // not the whole answer, just one field! (remove html snippets?)
            Log.i(TAG, "correct answer = " + correctAnswer);

            DiffEngine diff = new DiffEngine();

            diff.diff_prettyHtml(diff.diff_main(userAnswer, correctAnswer)); // Insert this in the answer!

            // Hide soft keyboard
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(mAnswerField.getWindowToken(), 0);
        }

        mIsSelecting = false;
        mFlipCard.setVisibility(View.GONE);
        mCard.loadUrl("javascript:onflip()");
        showEaseButtons();
    }


    private void writeExternalFile(String filename, String content) {
        Utils.writeFile(new File(getExternalFilesDir(null), filename), content);
    }


    private String getDeckStyle(String deckPath) {
      File styleFile = new File(Utils.removeExtension(deckPath) + ".css");
      if (!styleFile.exists() || !styleFile.canRead()) {
        return "";
      }
      StringBuilder style = new StringBuilder();
      try {
        BufferedReader styleReader =
          new BufferedReader(new InputStreamReader(new FileInputStream(styleFile)));
        while (true) {
          String line = styleReader.readLine();
          if (line == null) {
            break;
          }
          style.append(line);
          style.append('\n');
        }
      } catch (IOException e) {
            Log.e(TAG, "Error reading style file: " + styleFile.getAbsolutePath(), e);
        return "";
      }
      return style.toString();
    }


    /**
     * Returns the CSS used to handle custom fonts.
     * <p>
     * Custom fonts live in fonts directory in the directory used to store decks.
     * <p>
     * Each font is mapped to the font family by the same name as the name of the font fint without
     * the extension.
     */
    private String getCustomFontsStyle() {
      StringBuilder builder = new StringBuilder();
      for (File fontFile : Utils.getCustomFonts(getBaseContext())) {
        String fontFace = String.format(
            "@font-face {font-family: \"%s\"; src: url(\"file://%s\");}",
            Utils.removeExtension(fontFile.getName()), fontFile.getAbsolutePath());
            Log.d(TAG, "adding to style: " + fontFace);
        builder.append(fontFace);
        builder.append('\n');
      }
      return builder.toString();
    }


    /** Returns the CSS used to set the default font. */
    private String getDefaultFontStyle() {
        SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
        String defaultFont = preferences.getString("defaultFont", null);
        if (defaultFont == null || "".equals(defaultFont)) {
            return "";
        }
        return "BODY .question, BODY .answer { font-family: '" + defaultFont + "' }\n";
    }


    private boolean showQuestionOnAnswer() {
        switch (mPrefHideQuestionInAnswer) {
            case HQIA_DO_HIDE:
                return false;

            case HQIA_DO_SHOW:
                return true;

            case HQIA_CARD_MODEL:
                return (Model.getModel(AnkiDroidApp.deck(), mCurrentCard.getCardModelId(), false).getCardModel(
                        mCurrentCard.getCardModelId()).isQuestionInAnswer());

            default:
                return true;
        }
    }


    public static Card getEditorCard() {
        return sEditorCard;
    }

    private boolean isHebrewFixEnabled() {
        return mPrefFixHebrew;
    }


    /**
     * Calculates a dynamic font size depending on the length of the contents taking into account that the input string
     * contains html-tags, which will not be displayed and therefore should not be taken into account.
     *
     * @param htmlContents
     * @return font size respecting MIN_DYNAMIC_FONT_SIZE and MAX_DYNAMIC_FONT_SIZE
     */
    private static int calculateDynamicFontSize(String htmlContent) {
        // Replace each <br> with 15 spaces, each <hr> with 30 spaces, then
        // remove all html tags and spaces
        String realContent = htmlContent.replaceAll("\\<br.*?\\>", " ");
        realContent = realContent.replaceAll("\\<hr.*?\\>", " ");
        realContent = realContent.replaceAll("\\<.*?\\>", "");
        realContent = realContent.replaceAll("&nbsp;", " ");
        return Math.max(DYNAMIC_FONT_MIN_SIZE,
                DYNAMIC_FONT_MAX_SIZE - (realContent.length() / DYNAMIC_FONT_FACTOR));
    }


    private void unblockControls() {
        mCard.setEnabled(true);
        mFlipCard.setEnabled(true);

        switch (mCurrentEase) {
            case Card.EASE_FAILED:
                mEase1.setClickable(true);
                mEase2.setEnabled(true);
                mEase3.setEnabled(true);
                mEase4.setEnabled(true);
                break;

            case Card.EASE_HARD:
                mEase1.setEnabled(true);
                mEase2.setClickable(true);
                mEase3.setEnabled(true);
                mEase4.setEnabled(true);
                break;

            case Card.EASE_MID:
                mEase1.setEnabled(true);
                mEase2.setEnabled(true);
                mEase3.setClickable(true);
                mEase4.setEnabled(true);
                break;

            case Card.EASE_EASY:
                mEase1.setEnabled(true);
                mEase2.setEnabled(true);
                mEase3.setEnabled(true);
                mEase4.setClickable(true);
                break;

            default:
                mEase1.setEnabled(true);
                mEase2.setEnabled(true);
                mEase3.setEnabled(true);
                mEase4.setEnabled(true);
                break;
        }

        if (mPrefTimer) {
            mCardTimer.setEnabled(true);
        }

        if (mPrefWhiteboard) {
            mWhiteboard.setEnabled(true);
        }

        if (mPrefWriteAnswers) {
            mAnswerField.setEnabled(true);
        }
    }


    private void blockControls() {
        mCard.setEnabled(false);
        mFlipCard.setEnabled(false);

        switch (mCurrentEase) {
            case Card.EASE_FAILED:
                mEase1.setClickable(false);
                mEase2.setEnabled(false);
                mEase3.setEnabled(false);
                mEase4.setEnabled(false);
                break;

            case Card.EASE_HARD:
                mEase1.setEnabled(false);
                mEase2.setClickable(false);
                mEase3.setEnabled(false);
                mEase4.setEnabled(false);
                break;

            case Card.EASE_MID:
                mEase1.setEnabled(false);
                mEase2.setEnabled(false);
                mEase3.setClickable(false);
                mEase4.setEnabled(false);
                break;

            case Card.EASE_EASY:
                mEase1.setEnabled(false);
                mEase2.setEnabled(false);
                mEase3.setEnabled(false);
                mEase4.setClickable(false);
                break;

            default:
                mEase1.setEnabled(false);
                mEase2.setEnabled(false);
                mEase3.setEnabled(false);
                mEase4.setEnabled(false);
                break;
        }

        if (mPrefTimer) {
            mCardTimer.setEnabled(false);
        }

        if (mPrefWhiteboard) {
            mWhiteboard.setEnabled(false);
        }

        if (mPrefWriteAnswers) {
            mAnswerField.setEnabled(false);
        }
    }


    private int getAvailableWidthInCard() {
        // The width available is equals to
        // the screen's width divided by the default scale factor used by the WebView, because this scale factor will be
        // applied later
        // and minus the padding
        int availableWidth = (int) (AnkiDroidApp.getDisplayWidth() / mScaleInPercent) - TOTAL_WIDTH_PADDING;
        Log.i(TAG, "availableWidth = " + availableWidth);
        return availableWidth;
    }


    /**
     * Select Text in the webview and automatically sends the selected text to the clipboard.
     * From http://cosmez.blogspot.com/2010/04/webview-emulateshiftheld-on-android.html
     */
    private void selectAndCopyText() {
        try {
            KeyEvent shiftPressEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0);
            shiftPressEvent.dispatch(mCard);
            mIsSelecting = true;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }


    private String applyFixForHebrew(String text) {
        Matcher m = sHebrewPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String hebrewText = m.group();
            // Some processing before we reverse the Hebrew text
            // 1. Remove all Hebrew vowels as they cannot be displayed properly
            Matcher mv = sHebrewVowelsPattern.matcher(hebrewText);
            hebrewText = mv.replaceAll("");
            // 2. Flip open parentheses, brackets and curly brackets with closed ones and vice-versa
            // Matcher mp = sBracketsPattern.matcher(hebrewText);
            // StringBuffer sbg = new StringBuffer();
            // int bracket[] = new int[1];
            // while (mp.find()) {
            //     bracket[0] = mp.group().codePointAt(0);
            //     if ((bracket[0] & 0x28) == 0x28) {
            //         // flip open/close ( and )
            //         bracket[0] ^= 0x01;
            //     } else if (bracket[0] == 0x5B || bracket[0] == 0x5D || bracket[0] == 0x7B || bracket[0] == 0x7D) {
            //         // flip open/close [, ], { and }
            //         bracket[0] ^= 0x06;
            //     }
            //     mp.appendReplacement(sbg, new String(bracket, 0, 1));
            // }
            // mp.appendTail(sbg);
            // hebrewText = sbg.toString();
            // for (int i = 0; i < hebrewText.length(); i++) {
            // Log.i(TAG, "flipped brackets: " + hebrewText.codePointAt(i));
            // }
            // 3. Reverse all numerical groups (so when they get reversed again they show LTR)
            // Matcher mn = sNumeralsPattern.matcher(hebrewText);
            // sbg = new StringBuffer();
            // while (mn.find()) {
            //     StringBuffer sbn = new StringBuffer(m.group());
            //     mn.appendReplacement(sbg, sbn.reverse().toString());
            // }
            // mn.appendTail(sbg);

            // for (int i = 0; i < sbg.length(); i++) {
            // Log.i(TAG, "LTR numerals: " + sbg.codePointAt(i));
            // }
            // hebrewText = sbg.toString();//reverse().toString();
            m.appendReplacement(sb, hebrewText);
        }
        m.appendTail(sb);
        return sb.toString();
    }


    private void executeCommand(int which) {
    	switch(which) {
    	case GESTURE_NOTHING:
    		break;
    	case GESTURE_ANSWER_EASE1:
			if (sDisplayAnswer) {
				answerCard(Card.EASE_FAILED);
			} else {
		        displayCardAnswer();
			}
    		break;
    	case GESTURE_ANSWER_EASE2:
			if (sDisplayAnswer) {
				answerCard(Card.EASE_HARD);
			} else {
		        displayCardAnswer();
			}
    		break;
    	case GESTURE_ANSWER_EASE3:
			if (sDisplayAnswer) {
				answerCard(Card.EASE_MID);
			} else {
		        displayCardAnswer();
			}
    		break;
    	case GESTURE_ANSWER_EASE4:
			if (sDisplayAnswer) {
				answerCard(Card.EASE_EASY);
			} else {
		        displayCardAnswer();
			}
    		break;
    	case GESTURE_ANSWER_RECOMMENDED:
			if (sDisplayAnswer) {
				if (mCurrentCard.isRev()) {
					answerCard(Card.EASE_MID);
				} else {
					answerCard(Card.EASE_HARD);
				}
			} else {
				displayCardAnswer();
			}
    		break;
    	case GESTURE_ANSWER_BETTER_THAN_RECOMMENDED:
			if (sDisplayAnswer) {
				if (mCurrentCard.isRev()) {
					answerCard(Card.EASE_EASY);
				} else {
					answerCard(Card.EASE_MID);
				}
			}
    		break;
    	case GESTURE_EXIT:
       	 	closeReviewer();
    		break;
    	case GESTURE_UNDO:
    		if (AnkiDroidApp.deck().undoAvailable()) {
        		DeckTask.launchDeckTask(DeckTask.TASK_TYPE_UNDO, mUpdateCardHandler, new DeckTask.TaskData(0,
                        AnkiDroidApp.deck(), mCurrentCard));
    		}
    		break;
    	case GESTURE_REDO:
    		if (AnkiDroidApp.deck().redoAvailable()) {
                DeckTask.launchDeckTask(DeckTask.TASK_TYPE_REDO, mUpdateCardHandler, new DeckTask.TaskData(0,
                        AnkiDroidApp.deck(), mCurrentCard.getId(), false));
    		}
    		break;
    	case GESTURE_EDIT:
        	editCard();
    		break;
    	case GESTURE_MARK:
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_MARK_CARD, mMarkCardHandler, new DeckTask.TaskData(0,
                    AnkiDroidApp.deck(), mCurrentCard));
    		break;
    	case GESTURE_LOOKUP:
    		lookUp();
    		break;
    	case GESTURE_BURY:
            DeckTask.launchDeckTask(DeckTask.TASK_TYPE_BURY_CARD, mAnswerCardHandler, new DeckTask.TaskData(0,
                    AnkiDroidApp.deck(), mCurrentCard));
    		break;
    	case GESTURE_SUSPEND:
    		DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SUSPEND_CARD, mAnswerCardHandler, new DeckTask.TaskData(0,
                    AnkiDroidApp.deck(), mCurrentCard));
    		break;
    	case GESTURE_DELETE:
    		showDeleteCardDialog();
    		break;
    	case GESTURE_CLEAR_WHITEBOARD:
            if (mPrefWhiteboard) {
        		mWhiteboard.clear();
            }
    		break;
    	}
    }

    // ----------------------------------------------------------------------------
    // INNER CLASSES
    // ----------------------------------------------------------------------------

    /**
     * Provides a hook for calling "alert" from javascript. Useful for debugging your javascript.
     */
    public final class AnkiDroidWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            Log.i(TAG, message);
            result.confirm();
            return true;
        }


        @Override
        public boolean onConsoleMessage(ConsoleMessage cm) {
            Log.d(TAG, cm.message() + cm.sourceId() + ":" + cm.lineNumber());
            return true;
        }
    }

    private final class AnkiAudio {
        private ArrayList<URI> mUris;

        AnkiAudio() {
            init();
        }

        void init() {
            mUris = new ArrayList<URI>();
        }


        void register(String input) {
            URI uri;
            try {
                uri = new URI(input);
            } catch (URISyntaxException e) {
                uri = URI.create("");
            }
            mUris.add(uri);
        }


        void play() {
            Message msg = Message.obtain();
            msg.obj = mUris;
            mHandler.sendMessage(msg);
        }
    }


    private String nextInterval(int ease) {
        Resources res = getResources();

        if (ease == 1){
        	return res.getString(R.string.soon);
        } else {
        	return Utils.getReadableInterval(this, mCurrentCard.nextInterval(mCurrentCard, ease));
        }
    }


    private void closeReviewer() {
    	mClosing = true;
        DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SAVE_DECK, mSaveAndResetDeckHandler, new DeckTask.TaskData(
                AnkiDroidApp.deck(), ""));
    }

    class MyGestureDetector extends SimpleOnGestureListener {
     	private boolean mIsXScrolling = false;
     	private boolean mIsYScrolling = false;

    	@Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (mGesturesEnabled) {
        		try {
        			if (e2.getY() - e1.getY() > StudyOptions.sSwipeMinDistance && Math.abs(velocityY) > StudyOptions.sSwipeThresholdVelocity && Math.abs(e1.getX() - e2.getX()) < StudyOptions.sSwipeMaxOffPath && !mIsYScrolling) {
                        // down
        				executeCommand(mGestureSwipeDown);
       		        } else if (e1.getY() - e2.getY() > StudyOptions.sSwipeMinDistance && Math.abs(velocityY) > StudyOptions.sSwipeThresholdVelocity && Math.abs(e1.getX() - e2.getX()) < StudyOptions.sSwipeMaxOffPath && !mIsYScrolling) {
                        // up
        				executeCommand(mGestureSwipeUp);
       		        } else if (e2.getX() - e1.getX() > StudyOptions.sSwipeMinDistance && Math.abs(velocityX) > StudyOptions.sSwipeThresholdVelocity && Math.abs(e1.getY() - e2.getY()) < StudyOptions.sSwipeMaxOffPath && !mIsXScrolling && !mIsSelecting) {
                      	 // right
       		        	executeCommand(mGestureSwipeRight);
                    } else if (e1.getX() - e2.getX() > StudyOptions.sSwipeMinDistance && Math.abs(velocityX) > StudyOptions.sSwipeThresholdVelocity && Math.abs(e1.getY() - e2.getY()) < StudyOptions.sSwipeMaxOffPath && !mIsXScrolling && !mIsSelecting) {
                    	// left
                    	executeCommand(mGestureSwipeLeft);
                    }
               		mIsXScrolling = false;
               		mIsYScrolling = false;
                 }
                 catch (Exception e) {
                    Log.e(TAG, "onFling Exception = " + e.getMessage());
                 }
            }
            return false;
        }

    	@Override
    	public boolean onDoubleTap(MotionEvent e) {
    		if (mGesturesEnabled) {
        		executeCommand(mGestureDoubleTap);
			}
    		return false;
    	}

    	@Override
    	public boolean onSingleTapConfirmed(MotionEvent e) {
    		if (mGesturesEnabled && !mIsSelecting) {
    			int height = mCard.getHeight();
    			int width = mCard.getWidth();
    			float posX = e.getX();
    			float posY = e.getY();
    			if (posX > posY / height * width) {
    				if (posY > height * (1 - posX / width)) {
    		       		executeCommand(mGestureTapRight);
    				} else {
    		       		executeCommand(mGestureTapTop);
    				}
    			} else {
    				if (posY > height * (1 - posX / width)) {
    		       		executeCommand(mGestureTapBottom);
    				} else {
    		       		executeCommand(mGestureTapLeft);
    				}
    			}
 			} else {
 				mIsSelecting = false;
 			}
    		return false;
    	}

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        	if (mCard.getScrollY() != 0) {
        		mIsYScrolling = true;
        	}
        	if (mCard.getScrollX() != 0) {
        		mIsXScrolling = true;
        	}
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector.onTouchEvent(event)) {
            return true;
        } else {
            return false;
        }
    }


	@Override
	public void buttonPressed(ButtonEvent arg0) {
		Log.d("Zeemote","Button pressed, id: "+arg0.getButtonID());
	}


	@Override
	public void buttonReleased(ButtonEvent arg0) {
		Log.d("Zeemote","Button released, id: "+arg0.getButtonID());
		Message msg = Message.obtain();
		msg.what = MSG_ZEEMOTE_BUTTON_A + arg0.getButtonID(); //Button A = 0, Button B = 1...
		if ((msg.what >= MSG_ZEEMOTE_BUTTON_A) && (msg.what <= MSG_ZEEMOTE_BUTTON_D)) { //make sure messages from future buttons don't get throug
			this.ZeemoteHandler.sendMessage(msg);
		}
	}
}
