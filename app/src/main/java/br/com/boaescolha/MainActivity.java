package br.com.boaescolha;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String API_URL = "https://world.openfoodfacts.org/api/v2/product/";
    private static final String USER_AGENT = "BoaEscolhaAndroid/1.0 (Android; contato-local)";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int REQUEST_BARCODE_SCAN = 1002;
    private static final int REQUEST_GOOGLE_ACCOUNT = 1003;
    private static final String PREFS_NAME = "boa_escolha";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_SAVED_PRODUCTS = "saved_products";
    private static final String KEY_PROFILE_NAME = "profile_name";
    private static final String KEY_PROFILE_EMAIL = "profile_email";
    private static final int MAX_LOCAL_ITEMS = 20;
    private static final int TAB_HISTORY = 1;
    private static final int TAB_SEARCH = 2;
    private static final int TAB_LISTS = 3;
    private static final int TAB_PROFILE = 4;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private GoogleSignInClient googleSignInClient;

    private View rootScroll;
    private View topHeader;
    private View sectionHeader;
    private TextView txtTitle;
    private View searchContainer;
    private View filtersScroll;
    private View btnSort;
    private LinearLayout dynamicContent;
    private TextView txtSectionTitle;
    private TextView txtSectionMeta;
    private EditText editBarcode;
    private ProgressBar progress;
    private View cardStatus;
    private TextView txtStatus;
    private View cardResult;
    private ImageView imgProduct;
    private TextView txtProductName;
    private TextView txtBrand;
    private TextView txtScore;
    private TextView txtClassification;
    private TextView txtNutriScore;
    private TextView txtExplanation;
    private Button btnSaveProduct;
    private View indicatorHistory;
    private View indicatorSearch;
    private View indicatorLists;
    private View indicatorProfile;
    private ImageView iconHistory;
    private ImageView iconSearch;
    private ImageView iconLists;
    private ImageView iconProfile;
    private TextView textHistory;
    private TextView textSearch;
    private TextView textLists;
    private TextView textProfile;
    private ProductInfo currentProduct;
    private String currentCode = "";
    private boolean sortHighestFirst = true;
    private String currentListTitle = "";
    private String currentListSectionTitle = "";
    private String currentListKey = "";
    private String currentListEmptyMessage = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootScroll = findViewById(R.id.rootScroll);
        topHeader = findViewById(R.id.topHeader);
        sectionHeader = findViewById(R.id.sectionHeader);
        txtTitle = findViewById(R.id.txtTitle);
        searchContainer = findViewById(R.id.searchContainer);
        filtersScroll = findViewById(R.id.filtersScroll);
        btnSort = findViewById(R.id.btnSort);
        dynamicContent = findViewById(R.id.dynamicContent);
        txtSectionTitle = findViewById(R.id.txtSectionTitle);
        txtSectionMeta = findViewById(R.id.txtSectionMeta);
        editBarcode = findViewById(R.id.editBarcode);
        progress = findViewById(R.id.progress);
        cardStatus = findViewById(R.id.cardStatus);
        txtStatus = findViewById(R.id.txtStatus);
        cardResult = findViewById(R.id.cardResult);
        imgProduct = findViewById(R.id.imgProduct);
        txtProductName = findViewById(R.id.txtProductName);
        txtBrand = findViewById(R.id.txtBrand);
        txtScore = findViewById(R.id.txtScore);
        txtClassification = findViewById(R.id.txtClassification);
        txtNutriScore = findViewById(R.id.txtNutriScore);
        txtExplanation = findViewById(R.id.txtExplanation);

        View btnScan = findViewById(R.id.btnScan);
        View btnManual = findViewById(R.id.btnManual);
        btnSaveProduct = findViewById(R.id.btnSaveProduct);
        View bottomNav = findViewById(R.id.bottomNav);
        indicatorHistory = findViewById(R.id.indicatorHistory);
        indicatorSearch = findViewById(R.id.indicatorSearch);
        indicatorLists = findViewById(R.id.indicatorLists);
        indicatorProfile = findViewById(R.id.indicatorProfile);
        iconHistory = findViewById(R.id.iconHistory);
        iconSearch = findViewById(R.id.iconSearch);
        iconLists = findViewById(R.id.iconLists);
        iconProfile = findViewById(R.id.iconProfile);
        textHistory = findViewById(R.id.textHistory);
        textSearch = findViewById(R.id.textSearch);
        textLists = findViewById(R.id.textLists);
        textProfile = findViewById(R.id.textProfile);

        applySystemNavigationInsets(bottomNav);

        btnScan.setOnClickListener(view -> startScanner());
        btnManual.setOnClickListener(view -> searchManualCode());
        btnSaveProduct.setOnClickListener(view -> saveCurrentProduct());
        btnSort.setOnClickListener(view -> showSortMenu());
        findViewById(R.id.navHistory).setOnClickListener(view -> showHistory());
        findViewById(R.id.navSearch).setOnClickListener(view -> showSearch());
        findViewById(R.id.navLists).setOnClickListener(view -> showSavedProducts());
        findViewById(R.id.navProfile).setOnClickListener(view -> showProfile());
        editBarcode.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                searchManualCode();
                return true;
            }
            return false;
        });
        showSearch();
        setupFirebase();
    }

    private void updateNavigationState(int activeTab) {
        setNavIndicator(indicatorHistory, activeTab == TAB_HISTORY);
        setNavIndicator(indicatorSearch, activeTab == TAB_SEARCH);
        setNavIndicator(indicatorLists, activeTab == TAB_LISTS);
        setNavIndicator(indicatorProfile, activeTab == TAB_PROFILE);
        setNavItemState(iconHistory, textHistory, activeTab == TAB_HISTORY);
        setNavItemState(iconSearch, textSearch, activeTab == TAB_SEARCH);
        setNavItemState(iconLists, textLists, activeTab == TAB_LISTS);
        setNavItemState(iconProfile, textProfile, activeTab == TAB_PROFILE);
    }

    private void setNavIndicator(View indicator, boolean active) {
        indicator.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
    }

    private void setNavItemState(ImageView icon, TextView text, boolean active) {
        int color = getColor(active ? R.color.one_ui_accent : R.color.one_ui_text_secondary);
        icon.setColorFilter(color);
        text.setTextColor(color);
    }

    private void setupFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        String webClientId = getDefaultWebClientId();
        if (TextUtils.isEmpty(webClientId)) {
            googleSignInClient = null;
        } else {
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, signInOptions);
        }

        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
            saveProfileFromFirebaseUser(user);
            uploadLocalItemsToCloud();
            syncCloudToLocal();
        }
    }

    private String getDefaultWebClientId() {
        int resourceId = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (resourceId == 0) {
            return "";
        }
        return getString(resourceId);
    }

    private void applySystemNavigationInsets(View bottomNav) {
        int baseScrollBottom = rootScroll.getPaddingBottom();
        int baseNavBottom = bottomNav.getPaddingBottom();
        int baseNavHeight = bottomNav.getLayoutParams().height;

        bottomNav.setOnApplyWindowInsetsListener((view, insets) -> {
            int navigationBottom = insets.getSystemWindowInsetBottom();

            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = baseNavHeight + navigationBottom;
            view.setLayoutParams(params);

            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    baseNavBottom + navigationBottom);

            rootScroll.setPadding(
                    rootScroll.getPaddingLeft(),
                    rootScroll.getPaddingTop(),
                    rootScroll.getPaddingRight(),
                    baseScrollBottom + navigationBottom);

            return insets;
        });
        bottomNav.requestApplyInsets();
    }

    private void startScanner() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        openScanner();
    }

    private void openScanner() {
        Intent intent = new Intent(this, ScannerActivity.class);
        startActivityForResult(intent, REQUEST_BARCODE_SCAN);
    }

    private void searchManualCode() {
        String code = editBarcode.getText().toString().trim();
        if (TextUtils.isEmpty(code)) {
            showMessage("Digite um código de barras para consultar.");
            return;
        }
        showSearch();
        loadProduct(code);
    }

    private void loadProduct(String code) {
        currentCode = code;
        setLoading(true);
        cardResult.setVisibility(View.GONE);
        cardStatus.setVisibility(View.GONE);

        executor.execute(() -> {
            ProductResult result = fetchProduct(code);
            mainHandler.post(() -> {
                if (!code.equals(currentCode)) {
                    return;
                }
                setLoading(false);
                if (result.errorMessage != null) {
                    showMessage(result.errorMessage);
                } else if (result.product == null) {
                    showMessage("Produto não encontrado. Confira o código ou tente escanear outro item.");
                } else {
                    addLocalItem(KEY_HISTORY, result.product);
                    renderSearchRecentItems();
                    showProduct(result.product);
                }
            });
        });
    }

    private ProductResult fetchProduct(String code) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_URL + code);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);

            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readText(stream);
            if (responseCode < 200 || responseCode >= 300) {
                return ProductResult.error("Não foi possível consultar o produto agora. Tente novamente em instantes.");
            }

            JSONObject root = new JSONObject(body);
            if (root.optInt("status", 0) != 1) {
                return ProductResult.notFound();
            }

            JSONObject jsonProduct = root.optJSONObject("product");
            if (jsonProduct == null) {
                return ProductResult.notFound();
            }

            ProductInfo product = new ProductInfo();
            product.name = firstNonEmpty(
                    jsonProduct.optString("product_name_pt"),
                    jsonProduct.optString("product_name"),
                    jsonProduct.optString("generic_name_pt"),
                    jsonProduct.optString("generic_name"),
                    "Produto sem nome informado");
            product.brand = firstNonEmpty(jsonProduct.optString("brands"), "");
            product.imageUrl = firstNonEmpty(
                    jsonProduct.optString("image_front_url"),
                    jsonProduct.optString("image_url"),
                    "");
            product.nutriScore = readNutriScore(jsonProduct);
            product.score = calculateScore(product.nutriScore, jsonProduct);
            product.code = code;
            return ProductResult.success(product);
        } catch (Exception exception) {
            return ProductResult.error("Não consegui buscar esse produto. Verifique a conexão e tente novamente.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readText(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String readNutriScore(JSONObject product) {
        String direct = firstNonEmpty(
                product.optString("nutriscore_grade"),
                product.optString("nutrition_grades"),
                "");
        if (!TextUtils.isEmpty(direct)) {
            return normalizeNutriScore(direct);
        }

        JSONArray tags = product.optJSONArray("nutriscore_2023_tags");
        if (tags != null && tags.length() > 0) {
            return normalizeNutriScore(tags.optString(0));
        }
        return "";
    }

    private ScoreInfo calculateScore(String nutriScore, JSONObject product) {
        switch (nutriScore) {
            case "a":
                return ScoreInfo.withScore(
                        100,
                        classificationForScore(100),
                        "Nutri-Score",
                        nutriScoreExplanation("A", 100));
            case "b":
                return ScoreInfo.withScore(
                        80,
                        classificationForScore(80),
                        "Nutri-Score",
                        nutriScoreExplanation("B", 80));
            case "c":
                return ScoreInfo.withScore(
                        60,
                        classificationForScore(60),
                        "Nutri-Score",
                        nutriScoreExplanation("C", 60));
            case "d":
                return ScoreInfo.withScore(
                        40,
                        classificationForScore(40),
                        "Nutri-Score",
                        nutriScoreExplanation("D", 40));
            case "e":
                return ScoreInfo.withScore(
                        20,
                        classificationForScore(20),
                        "Nutri-Score",
                        nutriScoreExplanation("E", 20));
            default:
                return estimateScoreFromNutrition(product);
        }
    }

    private ScoreInfo estimateScoreFromNutrition(JSONObject product) {
        JSONObject nutriments = product.optJSONObject("nutriments");
        if (nutriments == null) {
            nutriments = new JSONObject();
        }
        JSONObject nutrientLevels = product.optJSONObject("nutrient_levels");

        int signals = 0;
        int score = 100;
        ArrayList<String> usedSignals = new ArrayList<>();

        double sugar = readFirstNutriment(nutriments, "sugars_100g", "sugars_value", "sugars");
        if (sugar >= 0) {
            signals++;
            usedSignals.add("açúcares");
            if (sugar > 45) {
                score -= 25;
            } else if (sugar > 22.5) {
                score -= 18;
            } else if (sugar > 10) {
                score -= 10;
            } else if (sugar > 5) {
                score -= 4;
            }
        } else {
            int levelPenalty = nutrientLevelPenalty(nutrientLevels, product, "sugars", 18, 9, 0);
            if (levelPenalty >= 0) {
                signals++;
                usedSignals.add("nível de açúcares");
                score -= levelPenalty;
            }
        }

        double saturatedFat = readFirstNutriment(nutriments, "saturated-fat_100g", "saturated-fat_value", "saturated-fat");
        if (saturatedFat >= 0) {
            signals++;
            usedSignals.add("gordura saturada");
            if (saturatedFat > 10) {
                score -= 18;
            } else if (saturatedFat > 5) {
                score -= 12;
            } else if (saturatedFat > 2) {
                score -= 6;
            }
        } else {
            int levelPenalty = nutrientLevelPenalty(nutrientLevels, product, "saturated-fat", 16, 8, 0);
            if (levelPenalty >= 0) {
                signals++;
                usedSignals.add("nível de gordura saturada");
                score -= levelPenalty;
            }
        }

        double salt = readFirstNutriment(nutriments, "salt_100g", "salt_value", "salt");
        if (salt < 0) {
            double sodium = readFirstNutriment(nutriments, "sodium_100g", "sodium_value", "sodium");
            salt = sodium >= 0 ? sodium * 2.5 : -1;
        }
        if (salt >= 0) {
            signals++;
            usedSignals.add("sal");
            if (salt > 1.5) {
                score -= 18;
            } else if (salt > 0.75) {
                score -= 10;
            } else if (salt > 0.3) {
                score -= 4;
            }
        } else {
            int levelPenalty = nutrientLevelPenalty(nutrientLevels, product, "salt", 16, 8, 0);
            if (levelPenalty >= 0) {
                signals++;
                usedSignals.add("nível de sal");
                score -= levelPenalty;
            }
        }

        double energyKcal = readFirstNutriment(nutriments, "energy-kcal_100g", "energy-kcal_value", "energy-kcal");
        if (energyKcal < 0) {
            double energyKj = readFirstNutriment(nutriments, "energy-kj_100g", "energy_100g", "energy-kj_value", "energy-kj");
            energyKcal = energyKj >= 0 ? energyKj / 4.184 : -1;
        }
        if (energyKcal >= 0) {
            signals++;
            usedSignals.add("energia");
            if (energyKcal > 550) {
                score -= 12;
            } else if (energyKcal > 400) {
                score -= 8;
            } else if (energyKcal > 250) {
                score -= 4;
            }
        }

        double fiber = readFirstNutriment(nutriments, "fiber_100g", "fiber_value", "fiber");
        if (fiber >= 0) {
            signals++;
            usedSignals.add("fibras");
            if (fiber >= 6) {
                score += 8;
            } else if (fiber >= 3) {
                score += 4;
            }
        }

        double proteins = readFirstNutriment(nutriments, "proteins_100g", "proteins_value", "proteins");
        if (proteins >= 0) {
            signals++;
            usedSignals.add("proteínas");
            if (proteins >= 10) {
                score += 4;
            }
        }

        int novaGroup = readNovaGroup(product, nutriments);
        if (novaGroup > 0) {
            signals++;
            usedSignals.add("processamento NOVA");
            if (novaGroup >= 4) {
                score -= 18;
            } else if (novaGroup == 3) {
                score -= 10;
            } else if (novaGroup == 2) {
                score -= 4;
            } else {
                score += 4;
            }
        }

        if (signals == 0) {
            return ScoreInfo.withoutScore();
        }

        int finalScore = clamp(score, 0, 100);
        String confidence = signals >= 3 ? "estimada" : "estimada com poucos dados";
        return ScoreInfo.withScore(
                finalScore,
                classificationForScore(finalScore),
                signals >= 3 ? "Estimativa nutricional" : "Estimativa parcial",
                String.format(
                        Locale.getDefault(),
                        "Este produto não trouxe Nutri-Score. A nota foi %s com os dados disponíveis no Open Food Facts: %s. Use como triagem rápida, não como avaliação oficial.",
                        confidence,
                        joinSignals(usedSignals)));
    }

    private String nutriScoreExplanation(String grade, int score) {
        return String.format(
                Locale.getDefault(),
                "Nota oficial baseada no Nutri-Score %s: %d de 100. O Boa Escolha usa essa escala para destacar rapidamente opções mais favoráveis no mercado. É uma orientação simples, não uma recomendação médica.",
                grade,
                score);
    }

    private double readFirstNutriment(JSONObject nutriments, String... keys) {
        for (String key : keys) {
            if (!nutriments.has(key)) {
                continue;
            }
            double value = nutriments.optDouble(key, -1);
            if (!Double.isNaN(value) && value >= 0) {
                return value;
            }
        }
        return -1;
    }

    private int nutrientLevelPenalty(JSONObject nutrientLevels, JSONObject product, String nutrient, int high, int moderate, int low) {
        String level = "";
        if (nutrientLevels != null) {
            level = nutrientLevels.optString(nutrient);
        }
        if (TextUtils.isEmpty(level)) {
            level = readNutrientLevelTag(product, nutrient);
        }
        if (TextUtils.isEmpty(level)) {
            return -1;
        }
        if (level.contains("high")) {
            return high;
        }
        if (level.contains("moderate")) {
            return moderate;
        }
        if (level.contains("low")) {
            return low;
        }
        return -1;
    }

    private String readNutrientLevelTag(JSONObject product, String nutrient) {
        JSONArray tags = product.optJSONArray("nutrient_levels_tags");
        if (tags == null) {
            return "";
        }
        for (int index = 0; index < tags.length(); index++) {
            String tag = tags.optString(index).toLowerCase(Locale.ROOT);
            if (tag.contains(nutrient)) {
                return tag;
            }
        }
        return "";
    }

    private int readNovaGroup(JSONObject product, JSONObject nutriments) {
        int direct = parsePositiveInt(firstNonEmpty(
                product.optString("nova_group"),
                product.optString("nova_groups"),
                nutriments.optString("nova-group_100g"),
                ""));
        if (direct > 0) {
            return direct;
        }

        JSONArray tags = product.optJSONArray("nova_groups_tags");
        if (tags == null) {
            return 0;
        }
        for (int index = 0; index < tags.length(); index++) {
            String tag = tags.optString(index);
            int parsed = parsePositiveInt(tag);
            if (parsed > 0) {
                return parsed;
            }
        }
        return 0;
    }

    private int parsePositiveInt(String value) {
        if (TextUtils.isEmpty(value)) {
            return 0;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (TextUtils.isEmpty(digits)) {
            return 0;
        }
        try {
            return Integer.parseInt(digits.substring(0, 1));
        } catch (Exception exception) {
            return 0;
        }
    }

    private String joinSignals(ArrayList<String> signals) {
        if (signals.isEmpty()) {
            return "dados limitados";
        }
        if (signals.size() == 1) {
            return signals.get(0);
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < signals.size(); index++) {
            if (index > 0) {
                builder.append(index == signals.size() - 1 ? " e " : ", ");
            }
            builder.append(signals.get(index));
        }
        return builder.toString();
    }

    private void showProduct(ProductInfo product) {
        currentProduct = product;
        sectionHeader.setVisibility(View.GONE);
        dynamicContent.setVisibility(View.GONE);
        txtProductName.setText(product.name);
        if (TextUtils.isEmpty(product.brand)) {
            txtBrand.setVisibility(View.GONE);
        } else {
            txtBrand.setVisibility(View.VISIBLE);
            txtBrand.setText(product.brand);
        }

        if (product.score.hasScore) {
            txtScore.setText(String.valueOf(product.score.value));
            txtClassification.setText(product.score.classification);
            if (!TextUtils.isEmpty(product.nutriScore)) {
                txtNutriScore.setText(String.format(Locale.getDefault(), "Nutri-Score %s", product.nutriScore.toUpperCase(Locale.ROOT)));
            } else {
                txtNutriScore.setText(product.score.source);
            }
            txtExplanation.setText(product.score.explanation);
            updateScoreColor(product.score.value);
        } else {
            txtScore.setText("-");
            txtClassification.setText("Sem nota suficiente");
            txtNutriScore.setText("Nutri-Score não informado");
            txtExplanation.setText("O Open Food Facts não trouxe Nutri-Score nem dados nutricionais suficientes para estimar uma nota com segurança.");
            updateScoreColor(0);
        }

        updateSaveButtonState();
        cardResult.setVisibility(View.VISIBLE);
        if (TextUtils.isEmpty(product.imageUrl)) {
            imgProduct.setImageDrawable(null);
            imgProduct.setVisibility(View.GONE);
        } else {
            imgProduct.setImageDrawable(null);
            imgProduct.setVisibility(View.VISIBLE);
            loadImage(product.imageUrl, currentCode);
        }
    }

    private void loadImage(String imageUrl, String code) {
        executor.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(imageUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                try (InputStream stream = new BufferedInputStream(connection.getInputStream())) {
                    bitmap = BitmapFactory.decodeStream(stream);
                }
            } catch (Exception ignored) {
                bitmap = null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            Bitmap finalBitmap = bitmap;
            mainHandler.post(() -> {
                if (!code.equals(currentCode)) {
                    return;
                }
                if (finalBitmap != null) {
                    imgProduct.setImageBitmap(finalBitmap);
                    imgProduct.setVisibility(View.VISIBLE);
                } else {
                    imgProduct.setVisibility(View.GONE);
                }
            });
        });
    }

    private void resetResult() {
        currentCode = "";
        currentProduct = null;
        editBarcode.setText("");
        cardResult.setVisibility(View.GONE);
        cardStatus.setVisibility(View.GONE);
        setLoading(false);
        editBarcode.requestFocus();
    }

    private void scanAnotherProduct() {
        resetResult();
        showSearch();
        startScanner();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showMessage(String message) {
        if (txtStatus == null || cardStatus == null) {
            return;
        }
        txtStatus.setText(message);
        cardStatus.setVisibility(View.VISIBLE);
    }

    private void showSearch() {
        updateNavigationState(TAB_SEARCH);
        topHeader.setVisibility(View.VISIBLE);
        sectionHeader.setVisibility(View.VISIBLE);
        txtTitle.setText("Busca");
        searchContainer.setVisibility(View.VISIBLE);
        filtersScroll.setVisibility(View.GONE);
        btnSort.setVisibility(View.GONE);
        dynamicContent.setVisibility(View.VISIBLE);
        cardStatus.setVisibility(View.GONE);
        renderSearchRecentItems();
    }

    private void renderSearchRecentItems() {
        txtSectionTitle.setText("Recentes");
        JSONArray items = readLocalItems(KEY_HISTORY);
        txtSectionMeta.setText(items.length() + " itens");
        dynamicContent.removeAllViews();
        if (items.length() == 0) {
            addInfoCard("Recentes", "Os produtos consultados aparecem aqui.");
            return;
        }
        int limit = Math.min(items.length(), 6);
        for (int index = 0; index < limit; index++) {
            JSONObject item = items.optJSONObject(index);
            if (item != null) {
                addHistoryRow(item);
            }
        }
    }

    private void showHistory() {
        updateNavigationState(TAB_HISTORY);
        showLocalList("Histórico", "Vistos recentemente", KEY_HISTORY, "Nenhum produto consultado ainda.");
    }

    private void showSavedProducts() {
        updateNavigationState(TAB_LISTS);
        showLocalList("Listas", "Minha lista", KEY_SAVED_PRODUCTS, "Nenhum produto salvo ainda. Consulte um produto e toque em Salvar.");
    }

    private void showProfile() {
        updateNavigationState(TAB_PROFILE);
        topHeader.setVisibility(View.GONE);
        sectionHeader.setVisibility(View.GONE);
        searchContainer.setVisibility(View.GONE);
        filtersScroll.setVisibility(View.GONE);
        btnSort.setVisibility(View.GONE);
        cardStatus.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
        dynamicContent.removeAllViews();
        dynamicContent.setVisibility(View.VISIBLE);

        String name = getPrefs().getString(KEY_PROFILE_NAME, "");
        String email = getPrefs().getString(KEY_PROFILE_EMAIL, "");
        if (TextUtils.isEmpty(email)) {
            addLoginProfileScreen();
        } else {
            addConnectedProfileScreen(name, email);
        }
    }

    private void showLocalList(String title, String sectionTitle, String key, String emptyMessage) {
        currentListTitle = title;
        currentListSectionTitle = sectionTitle;
        currentListKey = key;
        currentListEmptyMessage = emptyMessage;
        topHeader.setVisibility(View.VISIBLE);
        sectionHeader.setVisibility(View.VISIBLE);
        txtTitle.setText(title);
        searchContainer.setVisibility(View.GONE);
        filtersScroll.setVisibility(View.GONE);
        btnSort.setVisibility(View.VISIBLE);
        cardStatus.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
        dynamicContent.setVisibility(View.VISIBLE);

        renderLocalList(title, sectionTitle, key, emptyMessage, readLocalItems(key));
        loadCloudListForScreen(title, sectionTitle, key, emptyMessage);
    }

    private void showSortMenu() {
        PopupMenu menu = new PopupMenu(this, btnSort);
        menu.getMenu().add(0, 1, 0, "Maior nota");
        menu.getMenu().add(0, 2, 1, "Menor nota");
        menu.setOnMenuItemClickListener(item -> {
            sortHighestFirst = item.getItemId() == 1;
            if (!TextUtils.isEmpty(currentListKey)) {
                renderLocalList(
                        currentListTitle,
                        currentListSectionTitle,
                        currentListKey,
                        currentListEmptyMessage,
                        readLocalItems(currentListKey));
            }
            return true;
        });
        menu.show();
    }

    private void renderLocalList(String title, String sectionTitle, String key, String emptyMessage, JSONArray rawItems) {
        txtTitle.setText(title);
        txtSectionTitle.setText(sectionTitle);
        JSONArray items = sortItemsByScore(rawItems, sortHighestFirst);
        txtSectionMeta.setText(items.length() + " itens" + (KEY_HISTORY.equals(key) ? "" : " · " + (sortHighestFirst ? "maior nota" : "menor nota")));
        dynamicContent.removeAllViews();
        if (items.length() == 0) {
            addInfoCard(sectionTitle, emptyMessage);
            return;
        }
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item != null) {
                if (KEY_HISTORY.equals(key)) {
                    addHistoryRow(item);
                } else {
                    addProductRow(item);
                }
            }
        }
    }

    private void addHistoryRow(JSONObject item) {
        String code = item.optString("code");
        String name = firstNonEmpty(item.optString("name"), "Produto sem nome");
        String brand = firstNonEmpty(item.optString("brand"), "Marca não informada");
        String classification = firstNonEmpty(item.optString("classification"), "Sem nota suficiente");
        String score = item.optString("score");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_result_row);
        row.setPadding(dp(6), dp(6), dp(6), dp(6));
        row.setOnClickListener(view -> {
            showSearch();
            editBarcode.setText(code);
            loadProduct(code);
        });

        TextView scoreView = new TextView(this);
        scoreView.setGravity(android.view.Gravity.CENTER);
        scoreView.setText(TextUtils.isEmpty(score) ? "-" : score);
        scoreView.setTextColor(getColor(android.R.color.white));
        scoreView.setTextSize(11);
        scoreView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        scoreView.setBackgroundResource(R.drawable.bg_score_circle);
        if (scoreView.getBackground() instanceof GradientDrawable) {
            GradientDrawable background = (GradientDrawable) scoreView.getBackground().mutate();
            background.setColor(getColor(scoreColorRes(parseScore(score))));
        }
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        row.addView(scoreView, scoreParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(getColor(R.color.one_ui_text_primary));
        title.setTextSize(14);
        title.setMaxLines(1);
        texts.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(classification + " · " + brand);
        subtitle.setTextColor(getColor(R.color.one_ui_text_secondary));
        subtitle.setTextSize(12);
        subtitle.setMaxLines(1);
        texts.addView(subtitle);

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(1));
        dynamicContent.addView(row, rowParams);
    }

    private void addProductRow(JSONObject item) {
        String code = item.optString("code");
        String name = firstNonEmpty(item.optString("name"), "Produto sem nome");
        String brand = firstNonEmpty(item.optString("brand"), "Marca não informada");
        String classification = firstNonEmpty(item.optString("classification"), "Sem nota suficiente");
        String score = item.optString("score");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_result_row);
        row.setPadding(dp(8), dp(7), dp(8), dp(7));
        row.setOnClickListener(view -> {
            showSearch();
            editBarcode.setText(code);
            loadProduct(code);
        });

        TextView scoreView = new TextView(this);
        scoreView.setGravity(android.view.Gravity.CENTER);
        scoreView.setText(TextUtils.isEmpty(score) ? "-" : score);
        scoreView.setTextColor(getColor(android.R.color.white));
        scoreView.setTextSize(13);
        scoreView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        scoreView.setBackgroundResource(R.drawable.bg_score_circle);
        if (scoreView.getBackground() instanceof GradientDrawable) {
            GradientDrawable background = (GradientDrawable) scoreView.getBackground().mutate();
            background.setColor(getColor(scoreColorRes(parseScore(score))));
        }
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        row.addView(scoreView, scoreParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(getColor(R.color.one_ui_text_primary));
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        texts.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(brand + " · " + classification);
        subtitle.setTextColor(getColor(R.color.one_ui_text_secondary));
        subtitle.setTextSize(12);
        subtitle.setMaxLines(1);
        texts.addView(subtitle);

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (KEY_SAVED_PRODUCTS.equals(currentListKey)) {
            ImageButton removeButton = new ImageButton(this);
            removeButton.setImageResource(R.drawable.ic_delete);
            removeButton.setColorFilter(getColor(R.color.one_ui_danger));
            removeButton.setBackgroundResource(R.drawable.bg_icon_action);
            removeButton.setContentDescription("Remover da lista");
            removeButton.setPadding(dp(8), dp(8), dp(8), dp(8));
            removeButton.setOnClickListener(view -> {
                removeProductFromList(KEY_SAVED_PRODUCTS, code, true);
                renderLocalList(
                        currentListTitle,
                        currentListSectionTitle,
                        currentListKey,
                        currentListEmptyMessage,
                        readLocalItems(currentListKey));
                if (currentProduct != null && code.equals(currentProduct.code)) {
                    updateSaveButtonState();
                }
                showMessage("Produto removido da lista.");
            });
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(40), dp(40));
            removeParams.setMargins(dp(6), 0, 0, 0);
            row.addView(removeButton, removeParams);
        }

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(6));
        dynamicContent.addView(row, rowParams);
    }

    private void addInfoCard(String titleText, String bodyText) {
        TextView card = new TextView(this);
        card.setBackgroundResource(R.drawable.bg_status_card);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setText(titleText + "\n" + bodyText);
        card.setTextColor(getColor(R.color.one_ui_text_secondary));
        card.setTextSize(14);
        card.setLineSpacing(dp(2), 1f);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        dynamicContent.addView(card, params);
    }

    private void addLoginProfileScreen() {
        addCenteredTitle("Boa Escolha", 28, dp(22), true);
        addCenteredTitle("Compras mais saudáveis\nno supermercado", 24, dp(26), true);
        addCenteredSubtitle("Encontre alimentos mais saudáveis que combinam\ncom seu perfil alimentar, em poucos segundos!");

        TextView label = new TextView(this);
        label.setText("Acesse com seu e-mail");
        label.setTextColor(getColor(R.color.one_ui_text_secondary));
        label.setTextSize(16);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(30), 0, dp(8));
        dynamicContent.addView(label, labelParams);

        EditText emailInput = createProfileInput("joao@mail.com", true);
        dynamicContent.addView(emailInput, profileInputParams());

        Button continueButton = new Button(this);
        continueButton.setText("Continuar");
        continueButton.setAllCaps(false);
        continueButton.setTextColor(getColor(android.R.color.white));
        continueButton.setTextSize(15);
        continueButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        continueButton.setBackgroundResource(R.drawable.bg_button_primary);
        continueButton.setOnClickListener(view -> {
            String email = emailInput.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                showMessage("Informe seu e-mail para continuar.");
                return;
            }
            getPrefs().edit()
                    .putString(KEY_PROFILE_NAME, buildNameFromEmail(email))
                    .putString(KEY_PROFILE_EMAIL, email)
                    .apply();
            showProfile();
        });
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48));
        continueParams.setMargins(0, dp(2), 0, dp(20));
        dynamicContent.addView(continueButton, continueParams);

        addDividerText("ou continue com");

        ImageView google = new ImageView(this);
        google.setImageResource(R.drawable.ic_google_g);
        google.setPadding(dp(8), dp(8), dp(8), dp(8));
        google.setOnClickListener(view -> openGoogleAccountPicker());
        LinearLayout.LayoutParams googleParams = new LinearLayout.LayoutParams(dp(64), dp(64));
        googleParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        googleParams.setMargins(0, dp(12), 0, dp(22));
        dynamicContent.addView(google, googleParams);

        TextView terms = new TextView(this);
        terms.setText("Ao continuar, você concorda com os Termos de uso e Política de Privacidade do Boa Escolha.");
        terms.setTextColor(getColor(R.color.one_ui_text_secondary));
        terms.setTextSize(12);
        terms.setLineSpacing(dp(2), 1f);
        dynamicContent.addView(terms);
    }

    private void addConnectedProfileScreen(String name, String email) {
        addProfileHeader(name, email);
        addProfileMenuRow(R.drawable.ic_history, "Histórico sincronizado", true, view -> showHistory());
        addProfileMenuRow(R.drawable.ic_list, "Produtos salvos", true, view -> showSavedProducts());
        addProfileMenuRow(R.drawable.ic_settings, "Preferências do aplicativo", true,
                view -> showMessage("Preferências avançadas entram em uma próxima versão."));
        addProfileMenuRow(R.drawable.ic_doc, "Termos e privacidade", true,
                view -> showMessage("A nota é uma orientação simples e não substitui recomendação médica."));
        addProfileMenuRow(R.drawable.ic_logout, "Sair da conta", false, view -> signOutProfile());

        TextView version = new TextView(this);
        version.setText("Boa Escolha 1.0");
        version.setTextColor(getColor(R.color.one_ui_text_secondary));
        version.setTextSize(12);
        version.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(16), 0, 0);
        dynamicContent.addView(version, params);
    }

    private void addCenteredTitle(String text, int sizeSp, int topMargin, boolean bold) {
        TextView title = new TextView(this);
        title.setGravity(android.view.Gravity.CENTER);
        title.setText(text);
        title.setTextColor(getColor(R.color.one_ui_text_primary));
        title.setTextSize(sizeSp);
        title.setLineSpacing(dp(2), 1f);
        if (bold) {
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, topMargin, 0, 0);
        dynamicContent.addView(title, params);
    }

    private void addCenteredSubtitle(String text) {
        TextView subtitle = new TextView(this);
        subtitle.setGravity(android.view.Gravity.CENTER);
        subtitle.setText(text);
        subtitle.setTextColor(getColor(R.color.one_ui_text_secondary));
        subtitle.setTextSize(14);
        subtitle.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        dynamicContent.addView(subtitle, params);
    }

    private void addDividerText(String text) {
        TextView divider = new TextView(this);
        divider.setGravity(android.view.Gravity.CENTER);
        divider.setText(text);
        divider.setTextColor(getColor(R.color.one_ui_text_muted));
        divider.setTextSize(12);
        dynamicContent.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addProfileHeader(String name, String email) {
        String displayName = firstNonEmpty(name, "Criar conta");
        String displayEmail = firstNonEmpty(email, "Entre com Google ou informe seu e-mail");
        String initial = displayName.substring(0, 1).toUpperCase(Locale.ROOT);

        LinearLayout accountCard = new LinearLayout(this);
        accountCard.setOrientation(LinearLayout.HORIZONTAL);
        accountCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
        accountCard.setBackgroundResource(R.drawable.bg_card);
        accountCard.setPadding(dp(14), dp(14), dp(12), dp(14));
        accountCard.setOnClickListener(view -> showEditProfileForm(displayName, displayEmail));

        TextView avatar = new TextView(this);
        avatar.setGravity(android.view.Gravity.CENTER);
        avatar.setText(initial);
        avatar.setTextColor(getColor(android.R.color.white));
        avatar.setTextSize(24);
        avatar.setBackgroundResource(R.drawable.bg_profile_avatar);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        accountCard.addView(avatar, avatarParams);

        LinearLayout accountTexts = new LinearLayout(this);
        accountTexts.setOrientation(LinearLayout.VERTICAL);
        accountTexts.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(displayName);
        title.setTextColor(getColor(R.color.one_ui_text_primary));
        title.setTextSize(17);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setMaxLines(1);
        accountTexts.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(displayEmail);
        subtitle.setTextColor(getColor(R.color.one_ui_text_secondary));
        subtitle.setTextSize(13);
        subtitle.setMaxLines(1);
        accountTexts.addView(subtitle);

        TextView sync = new TextView(this);
        sync.setText("Conta conectada ao Firebase");
        sync.setTextColor(getColor(R.color.one_ui_text_muted));
        sync.setTextSize(12);
        sync.setMaxLines(1);
        accountTexts.addView(sync);

        accountCard.addView(accountTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageView edit = new ImageView(this);
        edit.setImageResource(R.drawable.ic_edit);
        edit.setPadding(dp(6), dp(6), dp(6), dp(6));
        accountCard.addView(edit, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout.LayoutParams accountParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        accountParams.setMargins(0, dp(2), 0, dp(14));
        dynamicContent.addView(accountCard, accountParams);
    }

    private void showEditProfileForm(String name, String email) {
        dynamicContent.removeAllViews();
        addProfileLoginForm(name, email);
    }

    private void addProfileMenuRow(int iconRes, String titleText, boolean showChevron, View.OnClickListener listener) {
        View divider = new View(this);
        divider.setBackgroundResource(R.drawable.bg_divider);
        dynamicContent.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(2), dp(12), 0, dp(12));
        row.setClickable(true);
        if (listener != null) {
            row.setOnClickListener(listener);
        }

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(getColor(R.color.one_ui_text_primary));
        title.setTextSize(15);
        title.setPadding(dp(12), 0, 0, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (showChevron) {
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_chevron_right);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        dynamicContent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addProfileLoginForm(String name, String email) {
        EditText nameInput = createProfileInput("Nome", false);
        nameInput.setText(name);
        dynamicContent.addView(nameInput, profileInputParams());

        EditText emailInput = createProfileInput("E-mail", true);
        emailInput.setText(email);
        dynamicContent.addView(emailInput, profileInputParams());

        Button saveButton = new Button(this);
        saveButton.setText(TextUtils.isEmpty(email) ? "Criar conta" : "Salvar alterações");
        saveButton.setAllCaps(false);
        saveButton.setTextColor(getColor(android.R.color.white));
        saveButton.setTextSize(15);
        saveButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        saveButton.setBackgroundResource(R.drawable.bg_button_primary);
        saveButton.setOnClickListener(view -> saveProfile(nameInput, emailInput));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48));
        saveParams.setMargins(0, dp(4), 0, dp(8));
        dynamicContent.addView(saveButton, saveParams);

        Button googleButton = new Button(this);
        googleButton.setText(TextUtils.isEmpty(email) ? "Entrar com Google" : "Trocar conta Google");
        googleButton.setAllCaps(false);
        googleButton.setTextColor(getColor(R.color.one_ui_text_primary));
        googleButton.setTextSize(15);
        googleButton.setBackgroundResource(R.drawable.bg_button_secondary);
        googleButton.setOnClickListener(view -> openGoogleAccountPicker());
        LinearLayout.LayoutParams googleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48));
        googleParams.setMargins(0, 0, 0, dp(10));
        dynamicContent.addView(googleButton, googleParams);
    }

    private EditText createProfileInput(String hint, boolean emailInput) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(getColor(R.color.one_ui_text_primary));
        input.setHintTextColor(getColor(R.color.one_ui_text_secondary));
        input.setBackgroundResource(R.drawable.bg_search_bar);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setInputType(emailInput
                ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        return input;
    }

    private LinearLayout.LayoutParams profileInputParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50));
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private void saveProfile(EditText nameInput, EditText emailInput) {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)) {
            showMessage("Informe nome e e-mail para criar a conta.");
            return;
        }
        getPrefs().edit()
                .putString(KEY_PROFILE_NAME, name)
                .putString(KEY_PROFILE_EMAIL, email)
                .apply();
        showProfile();
        showMessage("Conta salva.");
    }

    private void signOutProfile() {
        if (firebaseAuth != null) {
            firebaseAuth.signOut();
        }
        if (googleSignInClient != null) {
            googleSignInClient.signOut();
        }
        getPrefs().edit()
                .remove(KEY_PROFILE_NAME)
                .remove(KEY_PROFILE_EMAIL)
                .apply();
        showProfile();
        showMessage("Você saiu da conta.");
    }

    private void openGoogleAccountPicker() {
        if (googleSignInClient == null) {
            showMessage("Ative o provedor Google no Firebase Authentication, baixe o google-services.json atualizado e tente novamente.");
            return;
        }
        startActivityForResult(googleSignInClient.getSignInIntent(), REQUEST_GOOGLE_ACCOUNT);
    }

    private void addProfileActionRow(String titleText, String subtitleText) {
        TextView row = new TextView(this);
        row.setText(titleText + "\n" + subtitleText);
        row.setTextColor(getColor(R.color.one_ui_text_primary));
        row.setTextSize(17);
        row.setLineSpacing(dp(4), 1f);
        row.setPadding(dp(10), dp(18), dp(10), dp(18));
        dynamicContent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addProfileConnectedRow(String email) {
        TextView row = new TextView(this);
        row.setBackgroundResource(R.drawable.bg_status_card);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setText("Conta conectada\n" + email);
        row.setTextColor(getColor(R.color.one_ui_text_secondary));
        row.setTextSize(15);
        row.setLineSpacing(dp(3), 1f);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(14));
        dynamicContent.addView(row, params);
    }

    private void saveCurrentProduct() {
        if (currentProduct == null || TextUtils.isEmpty(currentProduct.code)) {
            showMessage("Consulte um produto antes de salvar.");
            return;
        }
        if (isProductSaved(currentProduct.code)) {
            removeProductFromList(KEY_SAVED_PRODUCTS, currentProduct.code, true);
            updateSaveButtonState();
            showMessage("Produto removido da lista.");
            return;
        }
        addLocalItem(KEY_SAVED_PRODUCTS, currentProduct);
        updateSaveButtonState();
        showMessage("Produto salvo em Listas.");
    }

    private void updateSaveButtonState() {
        if (btnSaveProduct == null || currentProduct == null || TextUtils.isEmpty(currentProduct.code)) {
            return;
        }
        btnSaveProduct.setText(isProductSaved(currentProduct.code) ? "Remover" : "Salvar");
    }

    private boolean isProductSaved(String code) {
        return containsItemCode(readLocalItems(KEY_SAVED_PRODUCTS), code);
    }

    private JSONArray readLocalItems(String key) {
        String raw = getPrefs().getString(key, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception exception) {
            return new JSONArray();
        }
    }

    private void addLocalItem(String key, ProductInfo product) {
        JSONArray oldItems = readLocalItems(key);
        JSONArray newItems = new JSONArray();
        newItems.put(productToJson(product));

        for (int index = 0; index < oldItems.length() && newItems.length() < MAX_LOCAL_ITEMS; index++) {
            JSONObject item = oldItems.optJSONObject(index);
            if (item != null && !product.code.equals(item.optString("code"))) {
                newItems.put(item);
            }
        }

        getPrefs().edit().putString(key, newItems.toString()).apply();
        saveCloudItem(key, product);
    }

    private void removeProductFromList(String key, String code, boolean syncCloud) {
        JSONArray oldItems = readLocalItems(key);
        JSONArray newItems = new JSONArray();
        for (int index = 0; index < oldItems.length(); index++) {
            JSONObject item = oldItems.optJSONObject(index);
            if (item != null && !code.equals(item.optString("code"))) {
                newItems.put(item);
            }
        }
        getPrefs().edit().putString(key, newItems.toString()).apply();
        if (syncCloud) {
            removeCloudItem(key, code);
        }
    }

    private JSONObject productToJson(ProductInfo product) {
        JSONObject json = new JSONObject();
        try {
            json.put("code", product.code);
            json.put("name", product.name);
            json.put("brand", product.brand);
            json.put("imageUrl", product.imageUrl);
            json.put("nutriScore", product.nutriScore);
            json.put("classification", product.score.classification);
            json.put("score", product.score.hasScore ? String.valueOf(product.score.value) : "");
            json.put("scoreSource", product.score.source);
            json.put("explanation", product.score.explanation);
        } catch (Exception ignored) {
        }
        return json;
    }

    private Map<String, Object> productToMap(ProductInfo product) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", firstNonEmpty(product.code, ""));
        map.put("name", firstNonEmpty(product.name, ""));
        map.put("brand", firstNonEmpty(product.brand, ""));
        map.put("imageUrl", firstNonEmpty(product.imageUrl, ""));
        map.put("nutriScore", firstNonEmpty(product.nutriScore, ""));
        map.put("classification", product.score != null ? product.score.classification : "Sem nota suficiente");
        map.put("score", product.score != null && product.score.hasScore ? product.score.value : -1);
        map.put("scoreSource", product.score != null ? firstNonEmpty(product.score.source, "") : "");
        map.put("explanation", product.score != null ? firstNonEmpty(product.score.explanation, "") : "");
        map.put("updatedAt", System.currentTimeMillis());
        return map;
    }

    private void saveCloudItem(String key, ProductInfo product) {
        FirebaseUser user = firebaseAuth != null ? firebaseAuth.getCurrentUser() : null;
        if (user == null || TextUtils.isEmpty(product.code)) {
            return;
        }

        firestore.collection("users")
                .document(user.getUid())
                .collection(key)
                .document(product.code)
                .set(productToMap(product))
                .addOnFailureListener(exception ->
                        showMessage("Não consegui sincronizar com o Firebase. Verifique as regras do Firestore."));
    }

    private void removeCloudItem(String key, String code) {
        FirebaseUser user = firebaseAuth != null ? firebaseAuth.getCurrentUser() : null;
        if (user == null || TextUtils.isEmpty(code)) {
            return;
        }

        firestore.collection("users")
                .document(user.getUid())
                .collection(key)
                .document(code)
                .delete()
                .addOnFailureListener(exception ->
                        showMessage("Removi do aparelho, mas não consegui remover da nuvem agora."));
    }

    private void uploadLocalItemsToCloud() {
        FirebaseUser user = firebaseAuth != null ? firebaseAuth.getCurrentUser() : null;
        if (user == null) {
            return;
        }
        uploadLocalItemsToCloud(KEY_HISTORY);
        uploadLocalItemsToCloud(KEY_SAVED_PRODUCTS);
    }

    private void uploadLocalItemsToCloud(String key) {
        JSONArray items = readLocalItems(key);
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            ProductInfo product = productFromJson(item);
            if (product != null) {
                saveCloudItem(key, product);
            }
        }
    }

    private ProductInfo productFromJson(JSONObject item) {
        if (item == null) {
            return null;
        }
        ProductInfo product = new ProductInfo();
        product.code = item.optString("code");
        product.name = item.optString("name");
        product.brand = item.optString("brand");
        product.imageUrl = item.optString("imageUrl");
        product.nutriScore = item.optString("nutriScore");
        int score = parseScore(item.optString("score"));
        product.score = score > 0
                ? ScoreInfo.withScore(
                        score,
                        firstNonEmpty(item.optString("classification"), classificationForScore(score)),
                        firstNonEmpty(item.optString("scoreSource"), "Boa Escolha"),
                        firstNonEmpty(item.optString("explanation"), "Nota salva anteriormente no Boa Escolha."))
                : ScoreInfo.withoutScore();
        return product;
    }

    private void syncCloudToLocal() {
        syncCloudCollection(KEY_HISTORY);
        syncCloudCollection(KEY_SAVED_PRODUCTS);
    }

    private void syncCloudCollection(String key) {
        FirebaseUser user = firebaseAuth != null ? firebaseAuth.getCurrentUser() : null;
        if (user == null) {
            return;
        }

        firestore.collection("users")
                .document(user.getUid())
                .collection(key)
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(MAX_LOCAL_ITEMS)
                .get()
                .addOnSuccessListener(snapshot -> {
                    JSONArray items = documentsToJson(snapshot.getDocuments());
                    mergeAndSaveLocalItems(key, items);
                })
                .addOnFailureListener(exception ->
                        showMessage("Não consegui baixar dados do Firebase. Verifique as regras do Firestore."));
    }

    private void loadCloudListForScreen(String title, String sectionTitle, String key, String emptyMessage) {
        FirebaseUser user = firebaseAuth != null ? firebaseAuth.getCurrentUser() : null;
        if (user == null) {
            return;
        }

        txtSectionMeta.setText("Sincronizando...");
        firestore.collection("users")
                .document(user.getUid())
                .collection(key)
                .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(MAX_LOCAL_ITEMS)
                .get()
                .addOnSuccessListener(snapshot -> {
                    JSONArray items = documentsToJson(snapshot.getDocuments());
                    JSONArray mergedItems = mergeAndSaveLocalItems(key, items);

                    renderLocalList(title, sectionTitle, key, emptyMessage, mergedItems);
                })
                .addOnFailureListener(exception -> {
                    txtSectionMeta.setText("Erro de sync");
                    showMessage("Não consegui ler a nuvem. No Firebase, confira se o Firestore foi criado e se as regras permitem o usuário autenticado.");
                });
    }

    private JSONArray documentsToJson(java.util.List<DocumentSnapshot> documents) {
        JSONArray items = new JSONArray();
        for (DocumentSnapshot document : documents) {
            JSONObject item = new JSONObject();
            try {
                item.put("code", firstNonEmpty(document.getString("code"), document.getId()));
                item.put("name", firstNonEmpty(document.getString("name"), "Produto sem nome"));
                item.put("brand", firstNonEmpty(document.getString("brand"), ""));
                item.put("imageUrl", firstNonEmpty(document.getString("imageUrl"), ""));
                item.put("nutriScore", firstNonEmpty(document.getString("nutriScore"), ""));
                item.put("classification", firstNonEmpty(document.getString("classification"), "Sem nota suficiente"));
                item.put("scoreSource", firstNonEmpty(document.getString("scoreSource"), ""));
                item.put("explanation", firstNonEmpty(document.getString("explanation"), ""));
                Long score = document.getLong("score");
                item.put("score", score != null && score > 0 ? String.valueOf(score) : "");
                items.put(item);
            } catch (Exception ignored) {
            }
        }
        return items;
    }

    private JSONArray mergeAndSaveLocalItems(String key, JSONArray cloudItems) {
        JSONArray localItems = readLocalItems(key);
        JSONArray mergedItems = new JSONArray();

        addUniqueItems(mergedItems, cloudItems);
        addUniqueItems(mergedItems, localItems);

        while (mergedItems.length() > MAX_LOCAL_ITEMS) {
            mergedItems.remove(mergedItems.length() - 1);
        }

        getPrefs().edit().putString(key, mergedItems.toString()).apply();
        return mergedItems;
    }

    private void addUniqueItems(JSONArray target, JSONArray source) {
        for (int index = 0; index < source.length(); index++) {
            JSONObject item = source.optJSONObject(index);
            if (item == null) {
                continue;
            }

            String code = item.optString("code");
            if (TextUtils.isEmpty(code) || containsItemCode(target, code)) {
                continue;
            }
            target.put(item);
        }
    }

    private boolean containsItemCode(JSONArray items, String code) {
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item != null && code.equals(item.optString("code"))) {
                return true;
            }
        }
        return false;
    }

    private void saveProfileFromFirebaseUser(FirebaseUser user) {
        if (user == null) {
            return;
        }
        String email = user.getEmail();
        String name = firstNonEmpty(user.getDisplayName(), buildNameFromEmail(email));
        getPrefs().edit()
                .putString(KEY_PROFILE_NAME, name)
                .putString(KEY_PROFILE_EMAIL, firstNonEmpty(email, ""))
                .apply();
    }

    private int countItems(String key) {
        return readLocalItems(key).length();
    }

    private JSONArray sortItemsByScore(JSONArray items, boolean highestFirst) {
        ArrayList<JSONObject> sorted = new ArrayList<>();
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item != null) {
                sorted.add(item);
            }
        }
        sorted.sort((left, right) -> {
            int leftScore = parseScore(left.optString("score"));
            int rightScore = parseScore(right.optString("score"));
            return highestFirst ? rightScore - leftScore : leftScore - rightScore;
        });

        JSONArray result = new JSONArray();
        for (JSONObject item : sorted) {
            result.put(item);
        }
        return result;
    }

    private int parseScore(String score) {
        try {
            return Integer.parseInt(score);
        } catch (Exception exception) {
            return -1;
        }
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateScoreColor(int score) {
        if (txtScore.getBackground() instanceof GradientDrawable) {
            GradientDrawable background = (GradientDrawable) txtScore.getBackground().mutate();
            background.setColor(getColor(scoreColorRes(score)));
        }
    }

    private int scoreColorRes(int score) {
        if (score >= 70) {
            return R.color.one_ui_good;
        } else if (score >= 50) {
            return R.color.one_ui_warning;
        } else if (score >= 1) {
            return R.color.one_ui_danger;
        }
        return R.color.one_ui_text_muted;
    }

    private String classificationForScore(int score) {
        if (score >= 90) {
            return "Ótima escolha no mercado";
        } else if (score >= 70) {
            return "Boa escolha para comparar";
        } else if (score >= 50) {
            return "Consuma com moderação";
        } else if (score >= 30) {
            return "Pouco saudável no dia a dia";
        } else {
            return "Evite no dia a dia";
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeNutriScore(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[abcde]") ? normalized : "";
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !TextUtils.isEmpty(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA_PERMISSION) {
            return;
        }

        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            openScanner();
        } else {
            showMessage("Para escanear o código de barras, permita o uso da câmera. Você também pode digitar o código manualmente.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_BARCODE_SCAN) {
            String contents = data != null ? data.getStringExtra(ScannerActivity.EXTRA_BARCODE) : null;
            if (resultCode == RESULT_OK && !TextUtils.isEmpty(contents)) {
                editBarcode.setText(contents);
                loadProduct(contents);
            } else {
                showMessage("Leitura cancelada. Você pode tentar novamente ou digitar o código.");
            }
            return;
        }
        if (requestCode == REQUEST_GOOGLE_ACCOUNT) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account);
            } catch (ApiException exception) {
                showMessage("Não consegui entrar com Google. Confira o Firebase e tente novamente.");
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        if (account == null || TextUtils.isEmpty(account.getIdToken())) {
            showMessage("A conta Google não retornou token de login.");
            return;
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        firebaseAuth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    saveProfileFromFirebaseUser(user);
                    uploadLocalItemsToCloud();
                    syncCloudToLocal();
                    showProfile();
                    showMessage("Conta Google conectada e sincronizando.");
                })
                .addOnFailureListener(exception ->
                        showMessage("Não consegui autenticar no Firebase. Verifique se Google está ativado no Firebase Auth."));
    }

    private String buildNameFromEmail(String email) {
        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            return "Conta Google";
        }
        String localPart = email.substring(0, email.indexOf("@")).replace(".", " ").replace("_", " ");
        String[] words = localPart.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (!TextUtils.isEmpty(word)) {
                if (builder.length() > 0) {
                    builder.append(" ");
                }
                builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
                if (word.length() > 1) {
                    builder.append(word.substring(1));
                }
            }
        }
        return builder.length() > 0 ? builder.toString() : "Conta Google";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private static class ProductInfo {
        String code;
        String name;
        String brand;
        String imageUrl;
        String nutriScore;
        ScoreInfo score;
    }

    private static class ScoreInfo {
        final boolean hasScore;
        final int value;
        final String classification;
        final String source;
        final String explanation;

        private ScoreInfo(boolean hasScore, int value, String classification, String source, String explanation) {
            this.hasScore = hasScore;
            this.value = value;
            this.classification = classification;
            this.source = source;
            this.explanation = explanation;
        }

        static ScoreInfo withScore(int value, String classification, String source, String explanation) {
            return new ScoreInfo(true, value, classification, source, explanation);
        }

        static ScoreInfo withoutScore() {
            return new ScoreInfo(
                    false,
                    0,
                    "Sem nota suficiente",
                    "Dados insuficientes",
                    "Não há dados suficientes para calcular uma nota.");
        }
    }

    private static class ProductResult {
        final ProductInfo product;
        final String errorMessage;

        private ProductResult(ProductInfo product, String errorMessage) {
            this.product = product;
            this.errorMessage = errorMessage;
        }

        static ProductResult success(ProductInfo product) {
            return new ProductResult(product, null);
        }

        static ProductResult notFound() {
            return new ProductResult(null, null);
        }

        static ProductResult error(String message) {
            return new ProductResult(null, message);
        }
    }
}
