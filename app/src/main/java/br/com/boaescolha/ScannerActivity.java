package br.com.boaescolha;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScannerActivity extends androidx.activity.ComponentActivity {
    public static final String EXTRA_BARCODE = "br.com.boaescolha.EXTRA_BARCODE";

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean processingFrame = new AtomicBoolean(false);

    private PreviewView previewView;
    private TextView txtScannerStatus;
    private Button btnTorch;
    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private boolean resultDelivered;
    private boolean torchEnabled;
    private boolean manualEntryOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        previewView = findViewById(R.id.previewView);
        txtScannerStatus = findViewById(R.id.txtScannerStatus);
        btnTorch = findViewById(R.id.btnTorch);
        applySystemInsets(
                findViewById(R.id.scannerRoot),
                findViewById(R.id.scannerTopPanel),
                findViewById(R.id.scannerActions));
        findViewById(R.id.btnCancelScan).setOnClickListener(view -> cancelScan());
        findViewById(R.id.btnManualBarcode).setOnClickListener(view -> showManualBarcodeDialog());
        btnTorch.setOnClickListener(view -> toggleTorch());

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_CODABAR,
                        Barcode.FORMAT_ITF)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        startCamera();
    }

    private void applySystemInsets(View root, View topPanel, View actions) {
        int baseTopPadding = topPanel.getPaddingTop();
        ViewGroup.MarginLayoutParams actionParams = (ViewGroup.MarginLayoutParams) actions.getLayoutParams();
        int baseActionBottomMargin = actionParams.bottomMargin;

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int statusTop = insets.getSystemWindowInsetTop();
            int navigationBottom = insets.getSystemWindowInsetBottom();

            topPanel.setPadding(
                    topPanel.getPaddingLeft(),
                    baseTopPadding + statusTop,
                    topPanel.getPaddingRight(),
                    topPanel.getPaddingBottom());

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) actions.getLayoutParams();
            params.bottomMargin = baseActionBottomMargin + navigationBottom;
            actions.setLayoutParams(params);

            return insets;
        });
        root.requestApplyInsets();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (Exception exception) {
                showScannerMessage("Não consegui abrir a câmera. Tente digitar o código manualmente.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, this::analyzeBarcode);

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, analysis);
        boolean hasTorch = camera.getCameraInfo().hasFlashUnit();
        btnTorch.setEnabled(hasTorch);
        btnTorch.setAlpha(hasTorch ? 1f : 0.45f);
        if (!hasTorch) {
            btnTorch.setText("Sem lanterna");
        }
    }

    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) {
            showScannerMessage("Este aparelho não informou lanterna disponível.");
            return;
        }
        torchEnabled = !torchEnabled;
        camera.getCameraControl().enableTorch(torchEnabled);
        btnTorch.setText(torchEnabled ? "Desligar" : "Lanterna");
    }

    @SuppressLint("UnsafeOptInUsageError")
    @ExperimentalGetImage
    private void analyzeBarcode(@NonNull ImageProxy imageProxy) {
        if (resultDelivered || manualEntryOpen || !processingFrame.compareAndSet(false, true)) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            processingFrame.set(false);
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees());

        barcodeScanner.process(image)
                .addOnSuccessListener(this::handleBarcodes)
                .addOnFailureListener(exception -> showScannerMessage("Não consegui ler este código. Tente aproximar ou melhorar a luz."))
                .addOnCompleteListener(task -> {
                    processingFrame.set(false);
                    imageProxy.close();
                });
    }

    private void handleBarcodes(List<Barcode> barcodes) {
        if (resultDelivered || barcodes == null || barcodes.isEmpty()) {
            return;
        }

        for (Barcode barcode : barcodes) {
            String rawValue = barcode.getRawValue();
            if (rawValue != null && !rawValue.trim().isEmpty()) {
                deliverResult(rawValue.trim());
                return;
            }
        }
    }

    private void deliverResult(String barcode) {
        resultDelivered = true;
        manualEntryOpen = false;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_BARCODE, barcode);
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    private void showManualBarcodeDialog() {
        if (resultDelivered || manualEntryOpen) {
            return;
        }
        manualEntryOpen = true;

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, dp(18), padding, dp(16));
        content.setBackground(roundedDrawable(getColorCompat(R.color.one_ui_surface), dp(24)));

        TextView title = new TextView(this);
        title.setText("Digitar código de barras");
        title.setTextColor(getColorCompat(R.color.one_ui_text_primary));
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(this);
        message.setText("Digite os números que aparecem abaixo do código de barras.");
        message.setTextColor(getColorCompat(R.color.one_ui_text_secondary));
        message.setTextSize(14);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(8);
        content.addView(message, messageParams);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        input.setHint("Ex.: 7891234567890");
        input.setTextColor(getColorCompat(R.color.one_ui_text_primary));
        input.setHintTextColor(getColorCompat(R.color.one_ui_text_muted));
        input.setTextSize(16);
        input.setSelectAllOnFocus(false);
        input.setBackgroundResource(R.drawable.bg_input);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54));
        inputParams.topMargin = dp(16);
        content.addView(input, inputParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(18);

        Button cancel = new Button(this);
        cancel.setText("Cancelar");
        cancel.setAllCaps(false);
        cancel.setTextColor(getColorCompat(R.color.one_ui_text_primary));
        cancel.setBackgroundResource(R.drawable.bg_button_secondary);
        buttons.addView(cancel, new LinearLayout.LayoutParams(dp(112), dp(46)));

        Button confirm = new Button(this);
        confirm.setText("Consultar");
        confirm.setAllCaps(false);
        confirm.setTextColor(Color.WHITE);
        confirm.setBackgroundResource(R.drawable.bg_button_primary);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(dp(118), dp(46));
        confirmParams.leftMargin = dp(10);
        buttons.addView(confirm, confirmParams);

        content.addView(buttons, buttonsParams);

        cancel.setOnClickListener(view -> {
            manualEntryOpen = false;
            dialog.dismiss();
        });
        confirm.setOnClickListener(view -> {
            String barcode = input.getText().toString().replaceAll("\\D", "");
            if (barcode.length() < 8 || barcode.length() > 14) {
                input.setError("Digite de 8 a 14 números.");
                return;
            }
            manualEntryOpen = false;
            dialog.dismiss();
            deliverResult(barcode);
        });
        dialog.setOnDismissListener(dialogInterface -> {
            if (!resultDelivered) {
                manualEntryOpen = false;
            }
        });
        dialog.setView(content);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(420));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 180);
    }

    private void cancelScan() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    private void showScannerMessage(String message) {
        runOnUiThread(() -> txtScannerStatus.setText(message));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getColorCompat(int colorRes) {
        return ContextCompat.getColor(this, colorRes);
    }

    private GradientDrawable roundedDrawable(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
        cameraExecutor.shutdownNow();
    }
}
