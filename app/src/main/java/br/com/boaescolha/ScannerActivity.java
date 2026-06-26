package br.com.boaescolha;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
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
import android.widget.Button;

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
        if (resultDelivered || !processingFrame.compareAndSet(false, true)) {
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
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_BARCODE, barcode);
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    private void cancelScan() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    private void showScannerMessage(String message) {
        runOnUiThread(() -> txtScannerStatus.setText(message));
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
