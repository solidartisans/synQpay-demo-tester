package com.synqpay.demoTester;

import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.synqpay.sdk.ResponseCallback;
import com.synqpay.sdk.SynqpayAPI;
import com.synqpay.sdk.SynqpayManager;
import com.synqpay.sdk.SynqpayPAL;
import com.synqpay.sdk.SynqpayPrinter;
import com.synqpay.sdk.SynqpaySDK;
import com.synqpay.sdk.SynqpayStartupNotifier;
import com.synqpay.sdk.pal.IDocument;
import com.synqpay.sdk.pal.ILine;
import com.synqpay.demoTester.databinding.ActivityMainBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

public class MainActivity extends AppCompatActivity implements SynqpaySDK.ConnectionListener {
    private static final String TAG = "MainActivity";

    // UI Components
    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private TextView tvBindStatus;
    private TextView tvApiEnabled;
    private CheckBox cbNotifyUpdate;

    // Synqpay Components
    private SynqpayAPI api;
    private SynqpayManager manager;
    private SynqpayPrinter printer;
    private SynqpayStartupNotifier startupNotifier;
    private boolean isBound = false;
    private boolean isInitialized = false;

    // Error handling constants
    private static final String ERROR_NOT_INITIALIZED = "Synqpay is not initialized";
    private static final String ERROR_NOT_BOUND = "Synqpay is not bound";
    private static final String ERROR_API_NOT_READY = "API is not ready";
    private static final String ERROR_MANAGER_NOT_READY = "Manager is not ready";
    private static final String ERROR_PRINTER_NOT_READY = "Printer is not ready";
    private static final String ERROR_GENERIC = "An error occurred";
    private static final String ERROR_REQUEST_FAILED = "Request failed";
    private static final String ERROR_RESPONSE_PARSE = "Failed to parse response";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            setupNavigationUI();
            setupSynqpayUI();
            initializeSynqpay();
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            showErrorAndFinish("Failed to initialize application");
        }
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            try {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error showing toast", e);
            }
        });
    }

    private void showMessage(String message) {
        runOnUiThread(() -> {
            try {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error showing toast", e);
            }
        });
    }

    private void initializeSynqpay() {
        try {
            startupNotifier = new SynqpayStartupNotifier();
            SynqpaySDK.get().init(this);
            SynqpaySDK.get().setListener(this);
            isInitialized = true;
            Log.d(TAG, "Synqpay SDK initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Synqpay SDK", e);
            showError(ERROR_NOT_INITIALIZED);
            isInitialized = false;
        }
    }

    private void setupNavigationUI() {
        try {
            setSupportActionBar(binding.appBarMain.toolbar);
            binding.appBarMain.fab.setOnClickListener(view ->
                    Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                            .setAction("Action", null)
                            .setAnchorView(R.id.fab)
                            .show());

            DrawerLayout drawer = binding.drawerLayout;
            NavigationView navigationView = binding.navView;
            mAppBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                    .setOpenableLayout(drawer)
                    .build();

            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
            NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
            NavigationUI.setupWithNavController(navigationView, navController);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up navigation UI", e);
            showError("Failed to setup navigation");
        }
    }

    private void setupSynqpayUI() {
        try {
            tvBindStatus = findViewById(R.id.text_synqpay_status);
            tvApiEnabled = findViewById(R.id.text_api_enabled);
            cbNotifyUpdate = findViewById(R.id.checkbox_notify_update);

            tvBindStatus.setText("Initializing...");
            tvApiEnabled.setText("Not Connected");

            setupButtons();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Synqpay UI", e);
            showError("Failed to setup UI components");
        }
    }

    private void setupButtons() {
        try {
            findViewById(R.id.button_getTerminalStatus).setOnClickListener(v -> {
                if (!checkSynqpayReady()) return;
                handleGetTerminalStatus();
            });

            findViewById(R.id.button_settlement).setOnClickListener(v -> {
                if (!checkSynqpayReady()) return;
                handleSettlement();
            });

            findViewById(R.id.button_startTransaction).setOnClickListener(v -> {
                if (!checkSynqpayReady()) return;
                handleStartTransaction();
            });

            findViewById(R.id.button_continueTransaction).setOnClickListener(v -> {
                if (!checkSynqpayReady()) return;
                handleContinueTransaction();
            });

            findViewById(R.id.button_restart).setOnClickListener(v -> {
                if (!checkSynqpayReady()) return;
                restartSynqpay();
            });

            findViewById(R.id.button_print).setOnClickListener(v -> {
                if (!checkSynqpayReady()) return;
                print();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error setting up buttons", e);
            showError("Failed to setup buttons");
        }
    }

    private boolean checkSynqpayReady() {
        if (!isInitialized) {
            showError(ERROR_NOT_INITIALIZED);
            return false;
        }
        if (!isBound) {
            showError(ERROR_NOT_BOUND);
            return false;
        }
        if (api == null) {
            showError(ERROR_API_NOT_READY);
            return false;
        }
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isInitialized) {
            bindSynqpay();
        } else {
            showError(ERROR_NOT_INITIALIZED);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindSynqpay();
    }

    private void bindSynqpay() {
        try {
            startupNotifier.start(this, () -> {
                Log.d(TAG, "Synqpay startup complete");
                showMessage("Synqpay Started");
            });

            SynqpaySDK.get().bindService();
            Log.d(TAG, "Binding to Synqpay service initiated");
        } catch (Exception e) {
            Log.e(TAG, "Error binding to Synqpay service", e);
            showError("Error binding to Synqpay");
        }
    }

    private void unbindSynqpay() {
        try {
            if (isBound) {
                startupNotifier.stop(this);
                SynqpaySDK.get().unbindService();
                SynqpaySDK.get().setListener(null);
                isBound = false;
                api = null;
                manager = null;
                printer = null;
                Log.d(TAG, "Successfully unbound from Synqpay service");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unbinding from Synqpay service", e);
        }
    }

    private void handleGetTerminalStatus() {
        try {
            String request = getTerminalStatusRequest();
            if (request.isEmpty()) {
                showError("Failed to create terminal status request");
                return;
            }

            ResponseCallback.Stub callback = new ResponseCallback.Stub() {
                @Override
                public void onResponse(String response) {
                    Log.d(TAG, "Raw response received: " + response);
                    if (response == null || response.isEmpty()) {
                        showError("Empty response received");
                        return;
                    }
                    handleTerminalStatusResponse(response);
                }
            };

            sendRequest(request, callback);
        } catch (Exception e) {
            Log.e(TAG, "Error handling terminal status request", e);
            showError(ERROR_REQUEST_FAILED);
        }
    }

    private void handleSettlement() {
        try {
            String request = settlementRequest();
            if (request.isEmpty()) {
                showError("Failed to create settlement request");
                return;
            }

            ResponseCallback.Stub callback = new ResponseCallback.Stub() {
                @Override
                public void onResponse(String response) {
                    handleTerminalStatusResponse(response);
                }
            };

            sendRequest(request, callback);
        } catch (Exception e) {
            Log.e(TAG, "Error handling settlement request", e);
            showError(ERROR_REQUEST_FAILED);
        }
    }

    private void handleStartTransaction() {
        try {
            String request = getStartTransactionRequest();
            if (request.isEmpty()) {
                showError("Failed to create transaction request");
                return;
            }

            ResponseCallback.Stub callback = new ResponseCallback.Stub() {
                @Override
                public void onResponse(String response) {
                    handleTransactionResponse(response);
                }
            };

            sendRequest(request, callback);
        } catch (Exception e) {
            Log.e(TAG, "Error handling start transaction", e);
            showError(ERROR_REQUEST_FAILED);
        }
    }

    private void handleContinueTransaction() {
        try {
            String request = getContinueTransactionRequest();
            if (request.isEmpty()) {
                showError("Failed to create continue transaction request");
                return;
            }

            ResponseCallback.Stub callback = new ResponseCallback.Stub() {
                @Override
                public void onResponse(String response) {
                    handleTransactionResponse(response);
                }
            };

            sendRequest(request, callback);
        } catch (Exception e) {
            Log.e(TAG, "Error handling continue transaction", e);
            showError(ERROR_REQUEST_FAILED);
        }
    }

    private void sendRequest(String request, ResponseCallback.Stub callback) {
        if (!checkSynqpayReady()) return;

        try {
            Log.i(TAG, " => " + request);
            api.sendRequest(request, callback);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException when sending request", e);
            showError(ERROR_REQUEST_FAILED);
        } catch (Exception e) {
            Log.e(TAG, "Error sending request", e);
            showError(ERROR_REQUEST_FAILED);
        }
    }

    private void handleTerminalStatusResponse(String response) {
        Log.i(TAG, " <= " + response);
        try {
            JSONObject jsonResponse = new JSONObject(response);
            JSONObject jsonResult = jsonResponse.optJSONObject("result");
            if (jsonResult == null) {
                Log.w(TAG, "No result object in response");
                showError("Invalid response format");
                return;
            }

            final String terminalId = jsonResult.optString("terminalId", "");
            final String status = jsonResult.optString("status", "");

            if (terminalId.isEmpty() && status.isEmpty()) {
                showError("Empty response data");
                return;
            }

            showMessage(terminalId + " :" + status);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing status response", e);
            showError(ERROR_RESPONSE_PARSE);
        } catch (Exception e) {
            Log.e(TAG, "Error handling response", e);
            showError(ERROR_GENERIC);
        }
    }

    private void handleTransactionResponse(String response) {
        Log.i(TAG, " <= " + response);
        try {
            JSONObject jsonResponse = new JSONObject(response);
            JSONObject jsonResult = jsonResponse.optJSONObject("result");
            if (jsonResult == null) {
                Log.w(TAG, "No result object in response");
                showError("Invalid response format");
                return;
            }

            final String terminalId = jsonResult.optString("terminalId", "");
            final String result = jsonResult.optString("transactionStatus", "");

            if (terminalId.isEmpty() && result.isEmpty()) {
                showError("Empty transaction response");
                return;
            }

            showMessage(terminalId + " :" + result);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing transaction response", e);
            showError(ERROR_RESPONSE_PARSE);
        } catch (Exception e) {
            Log.e(TAG, "Error handling transaction response", e);
            showError(ERROR_GENERIC);
        }
    }

    private String getTerminalStatusRequest() {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject
                    .put("jsonrpc", "2.0")
                    .put("id", "1234")
                    .put("method", "getTerminalStatus")
                    .put("params", JSONObject.NULL);
            return jsonObject.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error creating terminal status request", e);
            return "";
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in terminal status request", e);
            return "";
        }
    }

    private String settlementRequest() {
        try {
            JSONObject params = new JSONObject()
                    .put("host", "SHVA");

            JSONObject jsonObject = new JSONObject();
            jsonObject
                    .put("jsonrpc", "2.0")
                    .put("id", "1234")
                    .put("method", "settlement")
                    .put("params", params);
            return jsonObject.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error creating settlement request", e);
            return "";
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in settlement request", e);
            return "";
        }
    }

    private static int transactionCounter = 0;
    private Random random = new Random();

    private String getStartTransactionRequest() {
        try {
            boolean notifyUpdate = cbNotifyUpdate != null && cbNotifyUpdate.isChecked();
            int amount = 100 + random.nextInt(801); // Random amount between 100-900
            String referenceId = String.format("TXN%06d", ++transactionCounter);

            JSONObject params = new JSONObject()
                    .put("paymentMethod", "CREDIT_CARD")
                    .put("tranType", "SALE")
                    .put("referenceId", referenceId)
                    .put("amount", amount)
                    .put("currency", 376)
                    .put("notifyUpdate", notifyUpdate);

            JSONObject jsonObject = new JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", "1234")
                    .put("method", "startTransaction")
                    .put("params", params);

            return jsonObject.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error creating transaction request", e);
            return "";
        }
    }

    private String getContinueTransactionRequest() {
        try {
            JSONObject params = new JSONObject()
                    .put("creditTerms", "REGULAR");

            JSONObject jsonObject = new JSONObject();
            jsonObject
                    .put("jsonrpc", "2.0")
                    .put("id", "1234")
                    .put("method", "continueTransaction")
                    .put("params", params);
            return jsonObject.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Error creating continue transaction request", e);
            return "";
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in continue transaction request", e);
            return "";
        }
    }

    private void restartSynqpay() {
        if (!checkSynqpayReady() || manager == null) {
            showError(ERROR_MANAGER_NOT_READY);
            return;
        }

        try {
            manager.restartSynqpay();
            showMessage("Restarting Synqpay...");
        } catch (RemoteException e) {
            Log.e(TAG, "Error restarting Synqpay", e);
            showError("Failed to restart Synqpay");
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error restarting Synqpay", e);
            showError(ERROR_GENERIC);
        }
    }

    private void print() {
        try {
            IDocument document = SynqpayPAL.newDocument();
            document.direction(SynqpayPAL.Direction.RTL);

            ILine header = document.addLine();
            header.addText().text("סופר שומרון").bold(true).align(SynqpayPAL.Align.END);
            document.addLine().addText().text("סניף: רמת אביב #245").align(SynqpayPAL.Align.END);
            document.addLine().addText().text("אינשטיין 007, תל אביב").align(SynqpayPAL.Align.END);

            document.addDivider(4);

            addCategory(document, "מוצרי חלב");
            addItems(document, new String[][]{
                    {"חלב תנובה 3%", "7.90"},
                    {"גבינה צהובה עמק 28%", "38.90"},
                    {"קוטג' תנובה 5%", "6.90"},
                    {"שמנת מתוקה 38%", "12.90"},
                    {"יוגורט דנונה", "5.90"},
                    {"גבינת שמנת 30%", "14.90"},
                    {"חמאה", "9.90"}
            });

            addCategory(document, "לחם ומאפים");
            addItems(document, new String[][]{
                    {"לחם אחיד פרוס", "6.50"},
                    {"חלה", "12.90"},
                    {"פיתות", "8.90"},
                    {"לחמניות המבורגר", "15.90"}
            });

            addCategory(document, "ירקות ופירות");
            addItems(document, new String[][]{
                    {"עגבניות", "8.90"},
                    {"מלפפונים", "7.90"},
                    {"פלפל אדום", "12.90"},
                    {"בצל", "4.90"},
                    {"תפוחי אדמה", "9.90"},
                    {"גזר", "5.90"},
                    {"תפוחי עץ", "14.90"},
                    {"בננות", "11.90"},
                    {"תפוזים", "8.90"}
            });

            addCategory(document, "מוצרים יבשים");
            addItems(document, new String[][]{
                    {"אורז בסמטי", "18.90"},
                    {"פסטה ברילה", "9.90"},
                    {"קמח לבן", "7.90"},
                    {"סוכר", "6.90"},
                    {"מלח שולחן", "3.90"}
            });

            addCategory(document, "חטיפים וממתקים");
            addItems(document, new String[][]{
                    {"במבה אסם", "4.90"},
                    {"ביסלי גריל", "4.90"},
                    {"תפוצ'יפס", "5.90"},
                    {"שוקולד פרה", "8.90"},
                    {"עוגיות אוראו", "12.90"}
            });

            addCategory(document, "משקאות");
            addItems(document, new String[][]{
                    {"קוקה קולה 1.5", "8.90"},
                    {"ספרייט 1.5", "8.90"},
                    {"מים מינרלים", "4.90"},
                    {"פריגת תפוזים", "12.90"},
                    {"יין תירוש", "25.90"}
            });

            addCategory(document, "מוצרי ניקיון");
            addItems(document, new String[][]{
                    {"אבקת כביסה", "39.90"},
                    {"מרכך כביסה", "24.90"},
                    {"נוזל כלים פיירי", "14.90"},
                    {"מטליות ניקוי", "12.90"},
                    {"נייר טואלט", "32.90"}
            });

            addCategory(document, "מוצרי בשר");
            addItems(document, new String[][]{
                    {"חזה עוף טרי", "39.90"},
                    {"שניצל תירס", "28.90"},
                    {"קציצות עוף", "32.90"},
                    {"כרעיים עוף", "29.90"},
                    {"המבורגר בקר", "45.90"}
            });

            addCategory(document, "שימורים ומזון יבש");
            addItems(document, new String[][]{
                    {"טונה בשמן", "8.90"},
                    {"תירס שימורים", "7.90"},
                    {"זיתים ירוקים", "12.90"},
                    {"קטשופ - HEINTZ היינץ", "14.90"},
                    {"קטשופ היינץ", "14.90"},
                    {"חומוס מוכן", "15.90"}
            });

            document.addDivider(4);
            ILine total = document.addLine();
            total.addText().text("סה״כ לתשלום:").bold(true).align(SynqpayPAL.Align.END);
            total.addText().text("₪687.30").align(SynqpayPAL.Align.START);

            document.addDivider(2);
            document.addSpace(10);
            document.addLine().addText().text("תודה ולהתראות").align(SynqpayPAL.Align.END);
            document.addLine().addText().text("תודה שקניתם בסופר שומרון").align(SynqpayPAL.Align.END);

            printer.print(document.bundle());

        } catch (RemoteException e) {
            Log.e(TAG, "Print error", e);
            showError("Print failed");
        }
    }

    private void addCategory(IDocument doc, String name) throws RemoteException {
        ILine line = doc.addLine();
        line.addText().text(name).bold(true).align(SynqpayPAL.Align.END);
        doc.addDivider(1);
    }

    private void addItems(IDocument doc, String[][] items) throws RemoteException {
        for (String[] item : items) {
            ILine line = doc.addLine();
            line.fillLast(true);
            line.addText().text(item[0]).align(SynqpayPAL.Align.START);
            line.addText().text("₪" + item[1]).align(SynqpayPAL.Align.END);
        }
    }
    @Override
    public void onSynqpayConnected() {
        try {
            api = SynqpaySDK.get().getSynqpayAPI();
            manager = SynqpaySDK.get().getSynqpayManager();
            printer = SynqpaySDK.get().getSynqpayPrinter();

            if (api == null || manager == null || printer == null) {
                Log.e(TAG, "One or more Synqpay components failed to initialize");
                showError("Failed to initialize all components");
                isBound = false;
                return;
            }

            isBound = true;

            runOnUiThread(() -> {
                try {
                    tvBindStatus.setText("Synqpay Bounded");
                    if (manager != null) {
                        tvApiEnabled.setText(manager.isApiEnabled() ?
                                "API Enabled" : "API Disabled");
                    } else {
                        tvApiEnabled.setText("Manager Not Available");
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Error checking API status", e);
                    tvApiEnabled.setText("API Status Unknown");
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error updating UI", e);
                    tvApiEnabled.setText("Error Getting Status");
                }
            });

            Log.d(TAG, "Successfully connected to Synqpay service");
            showMessage("Connected to Synqpay");
        } catch (Exception e) {
            Log.e(TAG, "Error during Synqpay connection", e);
            showError("Failed to initialize Synqpay components");
            isBound = false;
        }
    }

    @Override
    public void onSynqpayDisconnected() {
        try {
            isBound = false;
            api = null;
            manager = null;
            printer = null;

            runOnUiThread(() -> {
                try {
                    tvBindStatus.setText("Synqpay Unbounded");
                    tvApiEnabled.setText("");
                } catch (Exception e) {
                    Log.e(TAG, "Error updating UI on disconnect", e);
                }
            });

            Log.d(TAG, "Disconnected from Synqpay service");
            showMessage("Disconnected from Synqpay");
        } catch (Exception e) {
            Log.e(TAG, "Error during Synqpay disconnection", e);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (isBound) {
                unbindSynqpay();
            }
            api = null;
            manager = null;
            printer = null;
            startupNotifier = null;
            super.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "Error during activity destruction", e);
            super.onDestroy();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        try {
            super.onSaveInstanceState(outState);
            outState.putBoolean("isBound", isBound);
            outState.putBoolean("isInitialized", isInitialized);
        } catch (Exception e) {
            Log.e(TAG, "Error saving instance state", e);
            super.onSaveInstanceState(outState);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        try {
            super.onRestoreInstanceState(savedInstanceState);
            isBound = savedInstanceState.getBoolean("isBound", false);
            isInitialized = savedInstanceState.getBoolean("isInitialized", false);
        } catch (Exception e) {
            Log.e(TAG, "Error restoring instance state", e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        try {
            getMenuInflater().inflate(R.menu.main, menu);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error creating options menu", e);
            return false;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        try {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
            return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                    || super.onSupportNavigateUp();
        } catch (Exception e) {
            Log.e(TAG, "Error in navigation", e);
            return super.onSupportNavigateUp();
        }
    }
}
