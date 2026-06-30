package br.com.boaescolha;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

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

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private static final String OPEN_FOOD_FACTS_API_URL = "https://world.openfoodfacts.org/api/v2/product/";
    private static final String OPEN_PRODUCTS_FACTS_API_URL = "https://world.openproductsfacts.org/api/v2/product/";
    private static final String ANVISA_NEWS_URL = "https://www.gov.br/anvisa/pt-br/assuntos/noticias-anvisa/";
    private static final String SOURCE_OPEN_FOOD_FACTS = "Open Food Facts";
    private static final String SOURCE_OPEN_PRODUCTS_FACTS = "Open Products Facts";
    private static final String SOURCE_ANVISA = "Anvisa";
    private static final String USER_AGENT = "BoaEscolhaAndroid/1.0 (Android; contato-local)";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int REQUEST_BARCODE_SCAN = 1002;
    private static final int REQUEST_GOOGLE_ACCOUNT = 1003;
    private static final String PREFS_NAME = "boa_escolha";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_SAVED_PRODUCTS = "saved_products";
    private static final String KEY_PROFILE_NAME = "profile_name";
    private static final String KEY_PROFILE_EMAIL = "profile_email";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_RESTORE_SCREEN = "restore_screen";
    private static final String THEME_SYSTEM = "system";
    private static final String THEME_LIGHT = "light";
    private static final String THEME_DARK = "dark";
    private static final String SCREEN_SETTINGS = "settings";
    private static final int MAX_LOCAL_ITEMS = 20;
    private static final int RECALL_PAGE_SIZE = 20;
    private static final int FIRST_RECALL_YEAR = 2020;
    private static final long PRODUCT_CACHE_TTL_MS = 6L * 60L * 60L * 1000L;
    private static final String PRODUCT_IMAGE_CACHE_DIR = "product_images";
    private static final Pattern ANVISA_ARTICLE_PATTERN = Pattern.compile("(?is)<article class=\"entry\">(.*?)</article>");
    private static final Pattern ANVISA_TITLE_PATTERN = Pattern.compile("(?is)<a href=\"([^\"]+)\"[^>]*>(.*?)</a>");
    private static final Pattern ANVISA_DATE_PATTERN = Pattern.compile("(?is)última modificação\\s*([0-9]{2}/[0-9]{2}/[0-9]{4})\\s*([0-9]{2}h[0-9]{2})?");
    private static final Pattern ANVISA_SUMMARY_PATTERN = Pattern.compile("(?is)<p class=\"summary[^>]*>(.*?)</p>");
    private static final Pattern ANVISA_IMAGE_PATTERN = Pattern.compile("(?is)<meta[^>]+(?:property|name)=\"(?:og:image|twitter:image|image)\"[^>]+content=\"([^\"]+)\"[^>]*>");
    private static final Pattern ANVISA_PAGE_IMAGE_PATTERN = Pattern.compile("(?is)<img\\b[^>]+(?:src|data-src|data-lazy-src)=[\"']([^\"']+)[\"'][^>]*>");
    private static final int TAB_SEARCH = 2;
    private static final int TAB_LISTS = 3;
    private static final int TAB_RECALLS = 4;
    private static final int TAB_PROFILE = 5;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private GoogleSignInClient googleSignInClient;

    private View rootScroll;
    private View mainContent;
    private View topHeader;
    private View btnBack;
    private View sectionHeader;
    private TextView txtTitle;
    private View searchContainer;
    private View filtersScroll;
    private View btnSort;
    private LinearLayout dynamicContent;
    private TextView txtSectionTitle;
    private TextView txtSectionMeta;
    private EditText editSearch;
    private ProgressBar progress;
    private View cardStatus;
    private TextView txtStatus;
    private View cardResult;
    private View txtFooter;
    private ImageView imgProduct;
    private TextView txtProductName;
    private TextView txtBrand;
    private TextView txtScore;
    private TextView txtClassification;
    private TextView txtNutriScore;
    private TextView txtExplanation;
    private ImageButton btnSearch;
    private ImageButton btnProfile;
    private Button btnSaveProduct;
    private View indicatorSearch;
    private View indicatorLists;
    private View indicatorRecalls;
    private ImageView iconSearch;
    private ImageView iconLists;
    private ImageView iconRecalls;
    private TextView textSearch;
    private TextView textLists;
    private TextView textRecalls;
    private View bottomNav;
    private ProductInfo currentProduct;
    private String currentCode = "";
    private boolean sortHighestFirst = true;
    private String currentListTitle = "";
    private String currentListSectionTitle = "";
    private String currentListKey = "";
    private String currentListEmptyMessage = "";
    private boolean showingAppSettings;
    private boolean showingProductDetails;
    private int productReturnTab = TAB_SEARCH;
    private int profileReturnTab = TAB_SEARCH;
    private int activeTab = TAB_SEARCH;
    private int navigationInsetBottom;
    private String searchQuery = "";
    private String listSearchQuery = "";
    private String recallQuery = "";
    private JSONArray recallItems = new JSONArray();
    private final Map<String, String> anvisaImageUrlCache = new HashMap<>();
    private int selectedRecallYear = Calendar.getInstance().get(Calendar.YEAR);
    private int loadedRecallYear = -1;
    private int recallOffset;
    private int recallTotal = -1;
    private boolean loadingRecalls;
    private boolean suppressSearchUpdates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyPreferredTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootScroll = findViewById(R.id.rootScroll);
        mainContent = findViewById(R.id.mainContent);
        topHeader = findViewById(R.id.topHeader);
        btnBack = findViewById(R.id.btnBack);
        sectionHeader = findViewById(R.id.sectionHeader);
        txtTitle = findViewById(R.id.txtTitle);
        searchContainer = findViewById(R.id.searchContainer);
        filtersScroll = findViewById(R.id.filtersScroll);
        btnSort = findViewById(R.id.btnSort);
        dynamicContent = findViewById(R.id.dynamicContent);
        txtSectionTitle = findViewById(R.id.txtSectionTitle);
        txtSectionMeta = findViewById(R.id.txtSectionMeta);
        editSearch = findViewById(R.id.editSearch);
        progress = findViewById(R.id.progress);
        cardStatus = findViewById(R.id.cardStatus);
        txtStatus = findViewById(R.id.txtStatus);
        cardResult = findViewById(R.id.cardResult);
        txtFooter = findViewById(R.id.txtFooter);
        imgProduct = findViewById(R.id.imgProduct);
        txtProductName = findViewById(R.id.txtProductName);
        txtBrand = findViewById(R.id.txtBrand);
        txtScore = findViewById(R.id.txtScore);
        txtClassification = findViewById(R.id.txtClassification);
        txtNutriScore = findViewById(R.id.txtNutriScore);
        txtExplanation = findViewById(R.id.txtExplanation);

        btnSearch = findViewById(R.id.btnSearch);
        btnProfile = findViewById(R.id.btnProfile);
        btnSaveProduct = findViewById(R.id.btnSaveProduct);
        bottomNav = findViewById(R.id.bottomNav);
        indicatorSearch = findViewById(R.id.indicatorSearch);
        indicatorLists = findViewById(R.id.indicatorLists);
        indicatorRecalls = findViewById(R.id.indicatorRecalls);
        iconSearch = findViewById(R.id.iconSearch);
        iconLists = findViewById(R.id.iconLists);
        iconRecalls = findViewById(R.id.iconRecalls);
        textSearch = findViewById(R.id.textSearch);
        textLists = findViewById(R.id.textLists);
        textRecalls = findViewById(R.id.textRecalls);

        applySystemNavigationInsets(bottomNav);

        configureProductSearchAction();
        btnSaveProduct.setOnClickListener(view -> saveCurrentProduct());
        btnSort.setOnClickListener(view -> {
            if (activeTab == TAB_RECALLS) {
                showRecallYearMenu();
            } else {
                showSortMenu();
            }
        });
        btnBack.setOnClickListener(view -> handleBackNavigation());
        btnProfile.setOnClickListener(view -> openProfileFromCurrentScreen());
        findViewById(R.id.navSearch).setOnClickListener(view -> showSearch());
        findViewById(R.id.navLists).setOnClickListener(view -> showSavedProducts());
        findViewById(R.id.navRecalls).setOnClickListener(view -> showRecalls());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!handleBackNavigation()) {
                    finish();
                }
            }
        });
        editSearch.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                hideKeyboard();
                return true;
            }
            return false;
        });
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                if (suppressSearchUpdates || showingProductDetails) {
                    return;
                }
                if (activeTab == TAB_SEARCH && !showingProductDetails) {
                    searchQuery = text.toString();
                    renderSearchRecentItems();
                } else if (activeTab == TAB_LISTS && !TextUtils.isEmpty(currentListKey)) {
                    listSearchQuery = text.toString();
                    renderLocalList(
                            currentListTitle,
                            currentListSectionTitle,
                            currentListKey,
                            currentListEmptyMessage,
                            readLocalItems(currentListKey));
                } else if (activeTab == TAB_RECALLS) {
                    recallQuery = text.toString();
                    renderRecallItems();
                }
            }
        });
        String restoreScreen = getPrefs().getString(KEY_RESTORE_SCREEN, "");
        if (SCREEN_SETTINGS.equals(restoreScreen)) {
            getPrefs().edit().remove(KEY_RESTORE_SCREEN).apply();
            showAppSettings();
        } else {
            showSearch();
        }
        setupFirebase();
    }

    private void updateNavigationState(int activeTab) {
        this.activeTab = activeTab;
        setNavIndicator(indicatorSearch, activeTab == TAB_SEARCH);
        setNavIndicator(indicatorLists, activeTab == TAB_LISTS);
        setNavIndicator(indicatorRecalls, activeTab == TAB_RECALLS);
        setNavItemState(iconSearch, textSearch, activeTab == TAB_SEARCH);
        setNavItemState(iconLists, textLists, activeTab == TAB_LISTS);
        setNavItemState(iconRecalls, textRecalls, activeTab == TAB_RECALLS);
    }

    private void setNavIndicator(View indicator, boolean active) {
        indicator.setVisibility(active ? View.VISIBLE : View.INVISIBLE);
    }

    private void setNavItemState(ImageView icon, TextView text, boolean active) {
        int color = getColor(active ? R.color.one_ui_accent : R.color.one_ui_text_secondary);
        icon.setColorFilter(color);
        text.setTextColor(color);
    }

    private void openProfileFromCurrentScreen() {
        if (showingProductDetails || activeTab == TAB_PROFILE) {
            return;
        }
        profileReturnTab = activeTab;
        showProfile();
    }

    private void setProfileButtonVisible(boolean visible) {
        if (btnProfile != null) {
            btnProfile.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void setBottomNavigationVisible(boolean visible) {
        bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateBottomNavigationSpacing();
    }

    private void updateBottomNavigationSpacing() {
        boolean navVisible = bottomNav != null && bottomNav.getVisibility() == View.VISIBLE;
        rootScroll.setPadding(
                rootScroll.getPaddingLeft(),
                rootScroll.getPaddingTop(),
                rootScroll.getPaddingRight(),
                navigationInsetBottom + (navVisible ? dp(74) : 0));
        mainContent.setPadding(
                mainContent.getPaddingLeft(),
                mainContent.getPaddingTop(),
                mainContent.getPaddingRight(),
                navVisible ? dp(88) : dp(24));
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
        int baseNavBottom = bottomNav.getPaddingBottom();
        int baseNavHeight = bottomNav.getLayoutParams().height;

        bottomNav.setOnApplyWindowInsetsListener((view, insets) -> {
            navigationInsetBottom = insets.getSystemWindowInsetBottom();

            ViewGroup.LayoutParams params = view.getLayoutParams();
            params.height = baseNavHeight + navigationInsetBottom;
            view.setLayoutParams(params);

            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    baseNavBottom + navigationInsetBottom);

            updateBottomNavigationSpacing();

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

    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
        }
        editSearch.clearFocus();
    }

    private void configureSearchInput(String hint, String value) {
        suppressSearchUpdates = true;
        editSearch.setHint(hint);
        if (!TextUtils.equals(editSearch.getText().toString(), value)) {
            editSearch.setText(value);
            editSearch.setSelection(editSearch.length());
        }
        suppressSearchUpdates = false;
    }

    private void configureProductSearchAction() {
        if (btnSearch == null) {
            return;
        }
        btnSearch.setVisibility(View.VISIBLE);
        btnSearch.setImageResource(R.drawable.ic_scan);
        btnSearch.setColorFilter(getColor(R.color.one_ui_accent));
        btnSearch.setContentDescription("Escanear produto");
        btnSearch.setOnClickListener(view -> startScanner());
    }

    private void configureTextSearchAction(String description) {
        if (btnSearch == null) {
            return;
        }
        btnSearch.setVisibility(View.VISIBLE);
        btnSearch.setImageResource(R.drawable.ic_search);
        btnSearch.setColorFilter(getColor(R.color.one_ui_text_secondary));
        btnSearch.setContentDescription(description);
        btnSearch.setOnClickListener(view -> {
            hideKeyboard();
            if (activeTab == TAB_RECALLS) {
                renderRecallItems();
            } else if (activeTab == TAB_LISTS && !TextUtils.isEmpty(currentListKey)) {
                renderLocalList(
                        currentListTitle,
                        currentListSectionTitle,
                        currentListKey,
                        currentListEmptyMessage,
                        readLocalItems(currentListKey));
            } else if (activeTab == TAB_SEARCH) {
                renderSearchRecentItems();
            }
        });
    }

    private void openProductDetails(String code, int returnTab, ProductInfo savedProduct) {
        if (TextUtils.isEmpty(code)) {
            return;
        }
        productReturnTab = returnTab;
        sanitizeProductRecallAlert(savedProduct);
        if (isFreshSavedProduct(savedProduct, code)) {
            currentCode = code;
            showProduct(savedProduct);
            return;
        }
        showProductLoading(code);
        loadProduct(code, savedProduct);
    }

    private boolean isFreshSavedProduct(ProductInfo savedProduct, String code) {
        if (savedProduct == null || TextUtils.isEmpty(savedProduct.code) || !savedProduct.code.equals(code)) {
            return false;
        }
        return savedProduct.cachedAt > 0
                && System.currentTimeMillis() - savedProduct.cachedAt < PRODUCT_CACHE_TTL_MS;
    }

    private void loadProduct(String code, ProductInfo savedProduct) {
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
                    if (savedProduct != null) {
                        showProduct(savedProduct);
                        addDetailNotice("Não foi possível atualizar os dados agora. Exibindo as informações salvas.");
                    } else {
                        showProductLoadError(result.errorMessage);
                    }
                } else if (result.product == null) {
                    if (savedProduct != null) {
                        showProduct(savedProduct);
                        addDetailNotice("Este produto não foi encontrado na consulta atual. Exibindo as informações salvas.");
                    } else {
                        showProductLoadError("Produto não encontrado. Confira o código ou tente escanear outro item.");
                    }
                } else {
                    addLocalItem(KEY_HISTORY, result.product);
                    if (isProductSaved(result.product.code)) {
                        addLocalItem(KEY_SAVED_PRODUCTS, result.product);
                    }
                    showProduct(result.product);
                    enrichProductRecallAlertInBackground(result.product);
                }
            });
        });
    }

    private ProductResult fetchProduct(String code) {
        ProductSourceResult primary = fetchProductJson(OPEN_FOOD_FACTS_API_URL, SOURCE_OPEN_FOOD_FACTS, code);
        ProductSourceResult supplemental = null;

        if (primary.product != null) {
            if (shouldTrySupplementalSource(primary.product)) {
                supplemental = fetchProductJson(OPEN_PRODUCTS_FACTS_API_URL, SOURCE_OPEN_PRODUCTS_FACTS, code);
            }
            JSONObject mergedProduct = mergeProductData(primary.product, supplemental != null ? supplemental.product : null);
            ProductInfo product = buildProductInfo(code, mergedProduct, sourceSummary(primary, supplemental));
            return ProductResult.success(product);
        }

        supplemental = fetchProductJson(OPEN_PRODUCTS_FACTS_API_URL, SOURCE_OPEN_PRODUCTS_FACTS, code);
        if (supplemental.product != null) {
            ProductInfo product = buildProductInfo(code, supplemental.product, sourceSummary(null, supplemental));
            return ProductResult.success(product);
        }

        if (!TextUtils.isEmpty(primary.errorMessage)) {
            return ProductResult.error(primary.errorMessage);
        }
        return ProductResult.notFound();
    }

    private ProductSourceResult fetchProductJson(String baseUrl, String sourceName, String code) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + code);
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
            JSONObject root = new JSONObject(body);
            JSONObject jsonProduct = root.optJSONObject("product");
            if (jsonProduct != null) {
                return ProductSourceResult.success(sourceName, jsonProduct);
            }

            if (responseCode == 404) {
                return ProductSourceResult.notFound(sourceName);
            }

            if (responseCode < 200 || responseCode >= 300) {
                return ProductSourceResult.error(sourceName, "Não foi possível consultar o produto agora. Tente novamente em instantes.");
            }

            if (root.optInt("status", 0) != 1) {
                return ProductSourceResult.notFound(sourceName);
            }

            return ProductSourceResult.notFound(sourceName);
        } catch (Exception exception) {
            return ProductSourceResult.error(sourceName, "Não consegui buscar esse produto. Verifique a conexão e tente novamente.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ProductInfo buildProductInfo(String code, JSONObject jsonProduct, String dataSources) {
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
        product.score = calculateScore(product.nutriScore, jsonProduct, dataSources);
        product.code = code;
        product.cachedAt = System.currentTimeMillis();
        product.dataSources = firstNonEmpty(dataSources, SOURCE_OPEN_FOOD_FACTS);
        product.quantity = firstNonEmpty(jsonProduct.optString("quantity"), "");
        product.servingSize = firstNonEmpty(jsonProduct.optString("serving_size"), "");
        product.ingredients = firstNonEmpty(
                jsonProduct.optString("ingredients_text_pt"),
                jsonProduct.optString("ingredients_text"),
                "");
        product.allergens = firstNonEmpty(
                cleanDisplayList(jsonProduct.optString("allergens")),
                cleanDisplayList(jsonProduct.optString("allergens_from_ingredients")),
                readTagList(jsonProduct, "allergens_tags"));
        product.traces = firstNonEmpty(
                cleanDisplayList(jsonProduct.optString("traces")),
                readTagList(jsonProduct, "traces_tags"));
        product.categories = firstNonEmpty(
                cleanDisplayList(jsonProduct.optString("categories")),
                readTagList(jsonProduct, "categories_tags"));
        product.labels = firstNonEmpty(
                cleanDisplayList(jsonProduct.optString("labels")),
                readTagList(jsonProduct, "labels_tags"));
        product.origins = firstNonEmpty(
                cleanDisplayList(jsonProduct.optString("origins")),
                readTagList(jsonProduct, "origins_tags"));
        product.manufacturingPlaces = cleanDisplayList(jsonProduct.optString("manufacturing_places"));
        product.packaging = firstNonEmpty(
                cleanDisplayList(jsonProduct.optString("packaging")),
                readTagList(jsonProduct, "packaging_tags"));
        product.countries = firstNonEmpty(
                cleanDisplayList(jsonProduct.optString("countries")),
                readTagList(jsonProduct, "countries_tags"));
        product.additives = readTagList(jsonProduct, "additives_tags");
        product.mineralWater = isMineralWaterProduct(product);

        JSONObject nutriments = jsonProduct.optJSONObject("nutriments");
        if (nutriments == null) {
            nutriments = new JSONObject();
        }
        product.energyKcal = readFirstNutriment(
                nutriments,
                "energy-kcal_100g",
                "energy-kcal_value",
                "energy-kcal");
        if (product.energyKcal < 0) {
            double energyKj = readFirstNutriment(
                    nutriments,
                    "energy-kj_100g",
                    "energy_100g",
                    "energy-kj_value",
                    "energy-kj");
            product.energyKcal = energyKj >= 0 ? energyKj / 4.184 : -1;
        }
        product.fat = readFirstNutriment(nutriments, "fat_100g", "fat_value", "fat");
        product.saturatedFat = readFirstNutriment(
                nutriments,
                "saturated-fat_100g",
                "saturated-fat_value",
                "saturated-fat");
        product.carbohydrates = readFirstNutriment(
                nutriments,
                "carbohydrates_100g",
                "carbohydrates_value",
                "carbohydrates");
        product.sugars = readFirstNutriment(nutriments, "sugars_100g", "sugars_value", "sugars");
        product.fiber = readFirstNutriment(nutriments, "fiber_100g", "fiber_value", "fiber");
        product.proteins = readFirstNutriment(nutriments, "proteins_100g", "proteins_value", "proteins");
        product.salt = readFirstNutriment(nutriments, "salt_100g", "salt_value", "salt");
        product.sodium = readFirstNutriment(nutriments, "sodium_100g", "sodium_value", "sodium");
        product.calcium = readFirstNutriment(nutriments, "calcium_100g", "calcium_value", "calcium");
        product.magnesium = readFirstNutriment(nutriments, "magnesium_100g", "magnesium_value", "magnesium");
        product.potassium = readFirstNutriment(nutriments, "potassium_100g", "potassium_value", "potassium");
        product.bicarbonate = readFirstNutriment(nutriments, "bicarbonate_100g", "bicarbonate_value", "bicarbonate");
        product.chloride = readFirstNutriment(nutriments, "chloride_100g", "chloride_value", "chloride");
        product.sulfate = readFirstNutriment(nutriments, "sulfate_100g", "sulfate_value", "sulfate");
        product.novaGroup = readNovaGroup(jsonProduct, nutriments);
        return product;
    }

    private boolean isMineralWaterProduct(ProductInfo product) {
        if (product == null) {
            return false;
        }
        String text = normalizeSearchText(
                product.name
                        + " " + product.categories
                        + " " + product.labels
                        + " " + product.ingredients);
        return text.contains("agua mineral")
                || text.contains("aguas minerais")
                || text.contains("mineral water")
                || text.contains("spring water")
                || (text.contains("agua") && text.contains("mineral"))
                || text.contains("water, bottled");
    }

    private boolean shouldTrySupplementalSource(JSONObject product) {
        return TextUtils.isEmpty(firstNonEmpty(
                product.optString("product_name_pt"),
                product.optString("product_name"),
                product.optString("generic_name_pt"),
                product.optString("generic_name")))
                || TextUtils.isEmpty(firstNonEmpty(
                product.optString("image_front_url"),
                product.optString("image_url")))
                || TextUtils.isEmpty(product.optString("brands"))
                || (TextUtils.isEmpty(readNutriScore(product)) && countScoreSignals(product) < 3);
    }

    private int countScoreSignals(JSONObject product) {
        JSONObject nutriments = product.optJSONObject("nutriments");
        if (nutriments == null) {
            nutriments = new JSONObject();
        }
        JSONObject nutrientLevels = product.optJSONObject("nutrient_levels");

        int signals = 0;
        if (readFirstNutriment(nutriments, "sugars_100g", "sugars_value", "sugars") >= 0
                || nutrientLevelPenalty(nutrientLevels, product, "sugars", 1, 1, 1) >= 0) {
            signals++;
        }
        if (readFirstNutriment(nutriments, "saturated-fat_100g", "saturated-fat_value", "saturated-fat") >= 0
                || nutrientLevelPenalty(nutrientLevels, product, "saturated-fat", 1, 1, 1) >= 0) {
            signals++;
        }
        if (readFirstNutriment(nutriments, "salt_100g", "salt_value", "salt") >= 0
                || readFirstNutriment(nutriments, "sodium_100g", "sodium_value", "sodium") >= 0
                || nutrientLevelPenalty(nutrientLevels, product, "salt", 1, 1, 1) >= 0) {
            signals++;
        }
        if (readFirstNutriment(nutriments, "energy-kcal_100g", "energy-kcal_value", "energy-kcal") >= 0
                || readFirstNutriment(nutriments, "energy-kj_100g", "energy_100g", "energy-kj_value", "energy-kj") >= 0) {
            signals++;
        }
        if (readFirstNutriment(nutriments, "fiber_100g", "fiber_value", "fiber") >= 0) {
            signals++;
        }
        if (readFirstNutriment(nutriments, "proteins_100g", "proteins_value", "proteins") >= 0) {
            signals++;
        }
        if (readNovaGroup(product, nutriments) > 0) {
            signals++;
        }
        return signals;
    }

    private JSONObject mergeProductData(JSONObject primary, JSONObject supplemental) {
        JSONObject merged;
        try {
            merged = primary != null ? new JSONObject(primary.toString()) : new JSONObject();
            if (supplemental == null) {
                return merged;
            }

            JSONArray keys = supplemental.names();
            if (keys == null) {
                return merged;
            }

            for (int index = 0; index < keys.length(); index++) {
                String key = keys.optString(index);
                Object supplementalValue = supplemental.opt(key);
                if (!isUsefulJsonValue(supplementalValue)) {
                    continue;
                }

                if ("nutriments".equals(key) || "nutrient_levels".equals(key)) {
                    JSONObject targetNested = merged.optJSONObject(key);
                    JSONObject supplementalNested = supplemental.optJSONObject(key);
                    if (supplementalNested != null) {
                        merged.put(key, mergeNestedJson(targetNested, supplementalNested));
                    }
                    continue;
                }

                if (!isUsefulJsonValue(merged.opt(key))) {
                    merged.put(key, supplementalValue);
                }
            }
        } catch (Exception exception) {
            return primary != null ? primary : new JSONObject();
        }
        return merged;
    }

    private JSONObject mergeNestedJson(JSONObject primary, JSONObject supplemental) throws Exception {
        JSONObject merged = primary != null ? new JSONObject(primary.toString()) : new JSONObject();
        JSONArray keys = supplemental.names();
        if (keys == null) {
            return merged;
        }
        for (int index = 0; index < keys.length(); index++) {
            String key = keys.optString(index);
            if (!isUsefulJsonValue(merged.opt(key)) && isUsefulJsonValue(supplemental.opt(key))) {
                merged.put(key, supplemental.opt(key));
            }
        }
        return merged;
    }

    private boolean isUsefulJsonValue(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return false;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            return !TextUtils.isEmpty(text) && !"unknown".equalsIgnoreCase(text);
        }
        if (value instanceof JSONArray) {
            return ((JSONArray) value).length() > 0;
        }
        if (value instanceof JSONObject) {
            return ((JSONObject) value).length() > 0;
        }
        return true;
    }

    private String sourceSummary(ProductSourceResult first, ProductSourceResult second) {
        ArrayList<String> sources = new ArrayList<>();
        addSourceName(sources, first);
        addSourceName(sources, second);
        return joinSignals(sources);
    }

    private void addSourceName(ArrayList<String> sources, ProductSourceResult source) {
        if (source == null || source.product == null || TextUtils.isEmpty(source.sourceName)) {
            return;
        }
        if (!sources.contains(source.sourceName)) {
            sources.add(source.sourceName);
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

    private String readTagList(JSONObject product, String key) {
        JSONArray tags = product.optJSONArray(key);
        if (tags == null || tags.length() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < tags.length(); index++) {
            String value = cleanDisplayList(tags.optString(index));
            if (TextUtils.isEmpty(value)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private String cleanDisplayList(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String[] entries = value.split(",");
        StringBuilder builder = new StringBuilder();
        for (String entry : entries) {
            String cleaned = entry.trim()
                    .replaceFirst("^[a-z]{2}:", "")
                    .replace('_', ' ');
            if (TextUtils.isEmpty(cleaned)) {
                continue;
            }
            if (cleaned.matches("(?i)e[0-9]+.*")) {
                cleaned = cleaned.toUpperCase(Locale.ROOT);
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(cleaned);
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

    private ScoreInfo calculateScore(String nutriScore, JSONObject product, String dataSources) {
        switch (nutriScore) {
            case "a":
                return ScoreInfo.withScore(
                        100,
                        classificationForScore(100),
                        "Nutri-Score",
                        nutriScoreExplanation("A", 100, dataSources));
            case "b":
                return ScoreInfo.withScore(
                        80,
                        classificationForScore(80),
                        "Nutri-Score",
                        nutriScoreExplanation("B", 80, dataSources));
            case "c":
                return ScoreInfo.withScore(
                        60,
                        classificationForScore(60),
                        "Nutri-Score",
                        nutriScoreExplanation("C", 60, dataSources));
            case "d":
                return ScoreInfo.withScore(
                        40,
                        classificationForScore(40),
                        "Nutri-Score",
                        nutriScoreExplanation("D", 40, dataSources));
            case "e":
                return ScoreInfo.withScore(
                        20,
                        classificationForScore(20),
                        "Nutri-Score",
                        nutriScoreExplanation("E", 20, dataSources));
            default:
                return estimateScoreFromNutrition(product, dataSources);
        }
    }

    private ScoreInfo estimateScoreFromNutrition(JSONObject product, String dataSources) {
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
                        "Este produto não trouxe Nutri-Score. A nota foi %s com os dados estruturados disponíveis nas fontes consultadas (%s): %s. Use como triagem rápida, não como avaliação oficial.",
                        confidence,
                        firstNonEmpty(dataSources, SOURCE_OPEN_FOOD_FACTS),
                        joinSignals(usedSignals)));
    }

    private String nutriScoreExplanation(String grade, int score, String dataSources) {
        return String.format(
                Locale.getDefault(),
                "Nota baseada no Nutri-Score %s informado nas fontes consultadas (%s): %d de 100. O Boa Escolha usa essa escala para destacar rapidamente opções mais favoráveis no mercado. É uma orientação simples, não uma recomendação médica.",
                grade,
                firstNonEmpty(dataSources, SOURCE_OPEN_FOOD_FACTS),
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

    private void showProductLoading(String code) {
        configureProductDetailScreen();
        currentProduct = null;

        TextView loading = new TextView(this);
        loading.setText("Carregando informações do produto…");
        loading.setTextColor(getColor(R.color.one_ui_text_secondary));
        loading.setTextSize(15);
        loading.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(56), 0, 0);
        dynamicContent.addView(loading, params);
        currentCode = code;
    }

    private void configureProductDetailScreen() {
        showingAppSettings = false;
        showingProductDetails = true;
        updateNavigationState(productReturnTab);
        setBottomNavigationVisible(false);
        topHeader.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.VISIBLE);
        setProfileButtonVisible(false);
        txtTitle.setText("Detalhes do produto");
        txtTitle.setTextSize(24);
        searchContainer.setVisibility(View.GONE);
        filtersScroll.setVisibility(View.GONE);
        sectionHeader.setVisibility(View.GONE);
        btnSort.setVisibility(View.GONE);
        cardStatus.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
        txtFooter.setVisibility(View.GONE);
        dynamicContent.removeAllViews();
        dynamicContent.setVisibility(View.VISIBLE);
        rootScroll.scrollTo(0, 0);
    }

    private void showProductLoadError(String message) {
        configureProductDetailScreen();
        setLoading(false);
        addInfoCard("Não foi possível abrir o produto", message);

        Button backButton = new Button(this);
        backButton.setText("Voltar");
        backButton.setAllCaps(false);
        backButton.setTextColor(getColor(R.color.one_ui_text_primary));
        backButton.setTextSize(14);
        backButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        backButton.setBackgroundResource(R.drawable.bg_button_secondary);
        backButton.setOnClickListener(view -> returnFromProductDetails());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46));
        params.setMargins(0, dp(8), 0, 0);
        dynamicContent.addView(backButton, params);
    }

    private void addDetailNotice(String message) {
        TextView notice = new TextView(this);
        notice.setText(message);
        notice.setTextColor(getColor(R.color.one_ui_text_secondary));
        notice.setTextSize(12);
        notice.setLineSpacing(dp(2), 1f);
        notice.setBackgroundResource(R.drawable.bg_status_card);
        notice.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(12), 0, 0);
        dynamicContent.addView(notice, params);
    }

    private void showProduct(ProductInfo product) {
        sanitizeProductRecallAlert(product);
        configureProductDetailScreen();
        currentProduct = product;

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(android.view.Gravity.TOP);
        hero.setPadding(0, dp(4), 0, dp(6));

        FrameLayout imageStack = new FrameLayout(this);

        imgProduct = new ImageView(this);
        imgProduct.setBackgroundResource(R.drawable.bg_product_image);
        imgProduct.setContentDescription(getString(R.string.product_image));
        imgProduct.setImageResource(R.drawable.ic_scan);
        imgProduct.setColorFilter(getColor(R.color.one_ui_text_muted));
        imgProduct.setPadding(dp(24), dp(24), dp(24), dp(24));
        imgProduct.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(dp(88), dp(88));
        imageParams.gravity = android.view.Gravity.START | android.view.Gravity.TOP;
        imageStack.addView(imgProduct, imageParams);

        txtScore = new TextView(this);
        txtScore.setGravity(android.view.Gravity.CENTER);
        txtScore.setTextColor(getColor(android.R.color.white));
        txtScore.setTextSize(13);
        txtScore.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        txtScore.setBackgroundResource(R.drawable.bg_score_circle);
        FrameLayout.LayoutParams scoreParams = new FrameLayout.LayoutParams(dp(42), dp(42));
        scoreParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        imageStack.addView(txtScore, scoreParams);
        hero.addView(imageStack, new LinearLayout.LayoutParams(dp(102), dp(94)));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(12), 0, 0, 0);

        txtProductName = new TextView(this);
        txtProductName.setText(firstNonEmpty(product.name, "Produto sem nome informado"));
        txtProductName.setTextColor(getColor(R.color.one_ui_text_primary));
        txtProductName.setTextSize(18);
        txtProductName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        txtProductName.setMaxLines(3);
        identity.addView(txtProductName);

        txtBrand = new TextView(this);
        txtBrand.setText(firstNonEmpty(product.brand, "Marca não informada"));
        txtBrand.setTextColor(getColor(R.color.one_ui_text_secondary));
        txtBrand.setTextSize(14);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        brandParams.setMargins(0, dp(4), 0, 0);
        identity.addView(txtBrand, brandParams);

        txtNutriScore = new TextView(this);
        txtNutriScore.setText(!TextUtils.isEmpty(product.nutriScore)
                ? "Nutri-Score " + product.nutriScore.toUpperCase(Locale.ROOT)
                : product.score.source);
        txtNutriScore.setTextColor(getColor(R.color.one_ui_text_secondary));
        txtNutriScore.setTextSize(12);
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sourceParams.setMargins(0, dp(3), 0, 0);
        identity.addView(txtNutriScore, sourceParams);

        hero.addView(identity, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));
        dynamicContent.addView(hero, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        txtClassification = new TextView(this);
        boolean hasRecallAlert = !TextUtils.isEmpty(product.recallAlertTitle);
        txtClassification.setText(hasRecallAlert
                ? "Evite: alerta da Anvisa"
                : product.score.hasScore
                ? product.score.classification
                : "Sem nota suficiente");
        txtClassification.setTextColor(getColor(hasRecallAlert
                ? R.color.one_ui_danger
                : product.score.hasScore
                ? scoreColorRes(product.score.value)
                : R.color.one_ui_text_secondary));
        txtClassification.setTextSize(15);
        txtClassification.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams classificationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        classificationParams.setMargins(0, dp(6), 0, 0);
        dynamicContent.addView(txtClassification, classificationParams);

        if (product.score.hasScore) {
            txtScore.setText(String.valueOf(product.score.value));
            updateScoreColor(product.score.value);
        } else {
            txtScore.setText("–");
            updateScoreColor(0);
        }

        btnSaveProduct = new Button(this);
        btnSaveProduct.setAllCaps(false);
        btnSaveProduct.setTextSize(14);
        btnSaveProduct.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btnSaveProduct.setBackgroundResource(R.drawable.bg_button_secondary);
        btnSaveProduct.setCompoundDrawablePadding(dp(8));
        btnSaveProduct.setOnClickListener(view -> saveCurrentProduct());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48));
        actionParams.setMargins(0, dp(10), 0, dp(6));
        dynamicContent.addView(btnSaveProduct, actionParams);
        updateSaveButtonState();

        addProductRecallAlertSection(product);

        LinearLayout assessment = createDetailSection("Avaliação");
        txtExplanation = addDetailParagraph(
                assessment,
                "Como chegamos à nota",
                product.score.hasScore
                        ? product.score.explanation
                        : product.mineralWater
                        ? "Água mineral nem sempre traz Nutri-Score ou tabela nutricional completa nas bases consultadas. Neste caso, confira os dados da água e da garrafa abaixo, como volume, origem, embalagem e minerais disponíveis."
                        : "O Open Food Facts não trouxe Nutri-Score nem dados nutricionais suficientes para estimar uma nota com segurança.",
                R.color.one_ui_text_secondary);
        addDetailSection(assessment);

        addMineralWaterSection(product);

        if (hasNutritionData(product)) {
            LinearLayout nutrition = createDetailSection("Informação nutricional");
            TextView basis = new TextView(this);
            basis.setText("Valores por 100 g ou 100 ml");
            basis.setTextColor(getColor(R.color.one_ui_text_muted));
            basis.setTextSize(12);
            LinearLayout.LayoutParams basisParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            basisParams.setMargins(0, 0, 0, dp(6));
            nutrition.addView(basis, basisParams);
            addNutrientRow(nutrition, "Energia", product.energyKcal, "kcal");
            addNutrientRow(nutrition, "Gorduras", product.fat, "g");
            addNutrientRow(nutrition, "Gorduras saturadas", product.saturatedFat, "g");
            addNutrientRow(nutrition, "Carboidratos", product.carbohydrates, "g");
            addNutrientRow(nutrition, "Açúcares", product.sugars, "g");
            addNutrientRow(nutrition, "Fibras", product.fiber, "g");
            addNutrientRow(nutrition, "Proteínas", product.proteins, "g");
            addNutrientRow(nutrition, "Sal", product.salt, "g");
            addNutrientRow(nutrition, "Sódio", product.sodium, "g");
            addDetailSection(nutrition);
        }

        if (!TextUtils.isEmpty(product.ingredients)
                || !TextUtils.isEmpty(product.allergens)
                || !TextUtils.isEmpty(product.traces)) {
            LinearLayout ingredients = createDetailSection("Ingredientes e alertas");
            addOptionalDetailParagraph(ingredients, "Ingredientes", product.ingredients, R.color.one_ui_text_secondary);
            addOptionalDetailParagraph(ingredients, "Alérgenos", product.allergens, R.color.one_ui_danger);
            addOptionalDetailParagraph(ingredients, "Pode conter", product.traces, R.color.one_ui_warning);
            addDetailSection(ingredients);
        }

        LinearLayout productData = createDetailSection("Sobre o produto");
        addOptionalDetailRow(productData, "Código de barras", product.code);
        addOptionalDetailRow(productData, "Quantidade", product.quantity);
        addOptionalDetailRow(productData, "Porção", product.servingSize);
        if (product.novaGroup > 0) {
            addOptionalDetailRow(productData, "Processamento NOVA", novaDescription(product.novaGroup));
        }
        addOptionalDetailRow(productData, "Categorias", product.categories);
        addOptionalDetailRow(productData, "Selos e características", product.labels);
        addOptionalDetailRow(productData, "Aditivos", product.additives);
        addOptionalDetailRow(productData, "Origem", product.origins);
        addOptionalDetailRow(productData, "Fabricado em", product.manufacturingPlaces);
        addOptionalDetailRow(productData, "Embalagem", product.packaging);
        addOptionalDetailRow(productData, "Países de venda", product.countries);
        addDetailSection(productData);

        TextView footer = new TextView(this);
        footer.setText(String.format(
                Locale.getDefault(),
                "Dados fornecidos por %s. A nota é uma orientação simples e não substitui recomendação médica.",
                firstNonEmpty(product.dataSources, SOURCE_OPEN_FOOD_FACTS)));
        footer.setTextColor(getColor(R.color.one_ui_text_muted));
        footer.setTextSize(12);
        footer.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        footerParams.setMargins(0, dp(14), 0, dp(6));
        dynamicContent.addView(footer, footerParams);

        if (!TextUtils.isEmpty(product.imageUrl)) {
            loadImage(product.imageUrl, currentCode);
        }
    }

    private void addProductRecallAlertSection(ProductInfo product) {
        if (product == null || TextUtils.isEmpty(product.recallAlertTitle)) {
            return;
        }

        LinearLayout alert = createDetailSection("Possível alerta da Anvisa");
        addDetailParagraph(
                alert,
                firstNonEmpty(product.recallAlertProductName, "Produto relacionado"),
                "Este produto pode estar relacionado a uma notificação oficial da Anvisa.",
                R.color.one_ui_warning);
        addOptionalDetailRow(alert, "Tipo", product.recallAlertType);
        addOptionalDetailRow(alert, "Data", product.recallAlertDate);
        addOptionalDetailRow(alert, "Fonte", firstNonEmpty(product.recallAlertSource, SOURCE_ANVISA));
        addRecallNoticeLink(alert, product.recallAlertTitle, product.recallAlertUrl);
        addDetailSection(alert);
    }

    private void addMineralWaterSection(ProductInfo product) {
        if (product == null || !product.mineralWater || !hasMineralWaterDetails(product)) {
            return;
        }

        LinearLayout water = createDetailSection("Dados da água");
        addOptionalDetailRow(water, "Volume", product.quantity);
        addOptionalDetailRow(water, "Origem", product.origins);
        addOptionalDetailRow(water, "Fabricado em", product.manufacturingPlaces);
        addOptionalDetailRow(water, "Embalagem", product.packaging);
        addOptionalDetailRow(water, "Países de venda", product.countries);
        addOptionalDetailRow(water, "Selos e características", product.labels);
        addOptionalWaterMineralRow(water, "Sódio", product.sodium);
        addOptionalWaterMineralRow(water, "Cálcio", product.calcium);
        addOptionalWaterMineralRow(water, "Magnésio", product.magnesium);
        addOptionalWaterMineralRow(water, "Potássio", product.potassium);
        addOptionalWaterMineralRow(water, "Bicarbonato", product.bicarbonate);
        addOptionalWaterMineralRow(water, "Cloreto", product.chloride);
        addOptionalWaterMineralRow(water, "Sulfato", product.sulfate);
        addDetailSection(water);
    }

    private boolean hasMineralWaterDetails(ProductInfo product) {
        return !TextUtils.isEmpty(product.quantity)
                || !TextUtils.isEmpty(product.origins)
                || !TextUtils.isEmpty(product.manufacturingPlaces)
                || !TextUtils.isEmpty(product.packaging)
                || !TextUtils.isEmpty(product.countries)
                || !TextUtils.isEmpty(product.labels)
                || product.sodium >= 0
                || product.calcium >= 0
                || product.magnesium >= 0
                || product.potassium >= 0
                || product.bicarbonate >= 0
                || product.chloride >= 0
                || product.sulfate >= 0;
    }

    private void addOptionalWaterMineralRow(LinearLayout section, String label, double value) {
        if (value < 0) {
            return;
        }
        addOptionalDetailRow(section, label, formatWaterMineralValue(value));
    }

    private String formatWaterMineralValue(double gramsPer100Ml) {
        double mgPerLiter = gramsPer100Ml * 10000;
        if (Math.abs(mgPerLiter - Math.rint(mgPerLiter)) < 0.01) {
            return String.format(Locale.getDefault(), "%.0f mg/L", mgPerLiter);
        }
        if (mgPerLiter < 10) {
            return String.format(Locale.getDefault(), "%.2f mg/L", mgPerLiter);
        }
        return String.format(Locale.getDefault(), "%.1f mg/L", mgPerLiter);
    }

    private LinearLayout createDetailSection(String titleText) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundResource(R.drawable.bg_card);
        section.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(getColor(R.color.one_ui_text_primary));
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        section.addView(title, params);
        return section;
    }

    private void addDetailSection(LinearLayout section) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        dynamicContent.addView(section, params);
    }

    private TextView addDetailParagraph(LinearLayout section, String labelText, String bodyText, int bodyColor) {
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(getColor(R.color.one_ui_text_primary));
        label.setTextSize(13);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        section.addView(label);

        TextView body = new TextView(this);
        body.setText(bodyText);
        body.setTextColor(getColor(bodyColor));
        body.setTextSize(13);
        body.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(3), 0, dp(8));
        section.addView(body, params);
        return body;
    }

    private void addOptionalDetailParagraph(LinearLayout section, String label, String value, int bodyColor) {
        if (!TextUtils.isEmpty(value)) {
            addDetailParagraph(section, label, value, bodyColor);
        }
    }

    private void addNutrientRow(LinearLayout section, String labelText, double value, String unit) {
        if (value < 0) {
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(getColor(R.color.one_ui_text_secondary));
        label.setTextSize(13);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView amount = new TextView(this);
        amount.setText(formatNutritionValue(value, unit));
        amount.setTextColor(getColor(R.color.one_ui_text_primary));
        amount.setTextSize(13);
        amount.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(amount);
        section.addView(row);
    }

    private void addOptionalDetailRow(LinearLayout section, String labelText, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        addDetailParagraph(section, labelText, value, R.color.one_ui_text_secondary);
    }

    private boolean hasNutritionData(ProductInfo product) {
        return product.energyKcal >= 0
                || product.fat >= 0
                || product.saturatedFat >= 0
                || product.carbohydrates >= 0
                || product.sugars >= 0
                || product.fiber >= 0
                || product.proteins >= 0
                || product.salt >= 0
                || product.sodium >= 0;
    }

    private String formatNutritionValue(double value, String unit) {
        String formatted;
        if (Math.abs(value - Math.rint(value)) < 0.01) {
            formatted = String.format(Locale.getDefault(), "%.0f", value);
        } else if (value < 1) {
            formatted = String.format(Locale.getDefault(), "%.2f", value);
        } else {
            formatted = String.format(Locale.getDefault(), "%.1f", value);
        }
        return formatted + " " + unit;
    }

    private String novaDescription(int group) {
        switch (group) {
            case 1:
                return "1 · Não processado ou minimamente processado";
            case 2:
                return "2 · Ingrediente culinário processado";
            case 3:
                return "3 · Alimento processado";
            case 4:
                return "4 · Ultraprocessado";
            default:
                return String.valueOf(group);
        }
    }

    private void loadImage(String imageUrl, String code) {
        executor.execute(() -> {
            Bitmap finalBitmap = loadCachedImage(imageUrl);
            mainHandler.post(() -> {
                if (!code.equals(currentCode)) {
                    return;
                }
                if (finalBitmap != null) {
                    imgProduct.clearColorFilter();
                    imgProduct.setPadding(0, 0, 0, 0);
                    imgProduct.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imgProduct.setImageBitmap(finalBitmap);
                    imgProduct.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private Bitmap loadCachedImage(String imageUrl) {
        File cacheFile = getImageCacheFile(imageUrl);
        Bitmap cachedBitmap = decodeCachedImage(cacheFile);
        if (cachedBitmap != null) {
            return cachedBitmap;
        }

        Bitmap downloadedBitmap = downloadImageToCache(imageUrl, cacheFile);
        if (downloadedBitmap != null) {
            return downloadedBitmap;
        }

        return decodeCachedImage(cacheFile);
    }

    private Bitmap decodeCachedImage(File cacheFile) {
        if (cacheFile == null || !cacheFile.exists() || cacheFile.length() <= 0) {
            return null;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
        if (bitmap == null) {
            cacheFile.delete();
        }
        return bitmap;
    }

    private Bitmap downloadImageToCache(String imageUrl, File cacheFile) {
        if (TextUtils.isEmpty(imageUrl)) {
            return null;
        }

        HttpURLConnection connection = null;
        File tempFile = cacheFile != null
                ? new File(cacheFile.getAbsolutePath() + "." + System.nanoTime() + ".tmp")
                : null;
        try {
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int responseCode = connection.getResponseCode();
            if (responseCode >= 400) {
                return null;
            }

            try (InputStream stream = new BufferedInputStream(connection.getInputStream())) {
                if (tempFile == null) {
                    return BitmapFactory.decodeStream(stream);
                }

                try (FileOutputStream output = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = stream.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
            }

            Bitmap bitmap = BitmapFactory.decodeFile(tempFile.getAbsolutePath());
            if (bitmap != null) {
                if ((!cacheFile.exists() || cacheFile.delete()) && tempFile.renameTo(cacheFile)) {
                    tempFile = null;
                }
                return bitmap;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
        return null;
    }

    private File getImageCacheFile(String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) {
            return null;
        }

        File cacheDir = new File(getFilesDir(), PRODUCT_IMAGE_CACHE_DIR);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            return null;
        }
        return new File(cacheDir, sha256(imageUrl) + ".img");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return builder.toString();
        } catch (Exception exception) {
            return String.valueOf(value.hashCode());
        }
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
        showingAppSettings = false;
        showingProductDetails = false;
        currentCode = "";
        setLoading(false);
        setBottomNavigationVisible(true);
        updateNavigationState(TAB_SEARCH);
        topHeader.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.GONE);
        setProfileButtonVisible(true);
        sectionHeader.setVisibility(View.VISIBLE);
        txtTitle.setText("Busca");
        txtTitle.setTextSize(30);
        searchContainer.setVisibility(View.VISIBLE);
        configureSearchInput("Buscar nos recentes", searchQuery);
        configureProductSearchAction();
        filtersScroll.setVisibility(View.GONE);
        btnSort.setVisibility(View.INVISIBLE);
        dynamicContent.setVisibility(View.VISIBLE);
        cardStatus.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
        txtFooter.setVisibility(View.VISIBLE);
        renderSearchRecentItems();
        rootScroll.scrollTo(0, 0);
    }

    private void renderSearchRecentItems() {
        String query = normalizeSearchText(searchQuery);
        JSONArray localItems = readLocalItems(KEY_HISTORY);
        JSONArray visibleItems = filterItemsByNameOrBrand(localItems, query);

        boolean filtering = !TextUtils.isEmpty(query);
        txtSectionTitle.setText(filtering ? "Resultados" : "Recentes");
        int displayCount = filtering ? visibleItems.length() : Math.min(visibleItems.length(), 6);
        txtSectionMeta.setText(displayCount + (displayCount == 1 ? " item" : " itens"));
        dynamicContent.removeAllViews();
        if (visibleItems.length() == 0) {
            if (filtering) {
                addInfoCard("Nenhum resultado", "Nenhum produto recente corresponde ao nome ou marca digitados.");
            } else {
                addInfoCard("Recentes", "Os produtos consultados aparecem aqui.");
            }
            return;
        }
        int limit = filtering ? visibleItems.length() : Math.min(visibleItems.length(), 6);
        for (int index = 0; index < limit; index++) {
            JSONObject item = visibleItems.optJSONObject(index);
            if (item != null) {
                addHistoryRow(item);
            }
        }
    }

    private String normalizeSearchText(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }

    private JSONArray filterItemsByNameOrBrand(JSONArray items, String normalizedQuery) {
        JSONArray filtered = new JSONArray();
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) {
                continue;
            }
            String searchableText = normalizeSearchText(
                    item.optString("name")
                            + " " + item.optString("brand")
                            + " " + item.optString("recallAlertTitle")
                            + " " + item.optString("recallAlertProductName"));
            if (TextUtils.isEmpty(normalizedQuery) || searchableText.contains(normalizedQuery)) {
                filtered.put(item);
            }
        }
        return filtered;
    }

    private void showSavedProducts() {
        updateNavigationState(TAB_LISTS);
        showLocalList("Listas", "Minha lista", KEY_SAVED_PRODUCTS, "Nenhum produto salvo ainda. Consulte um produto e toque em Salvar.");
    }

    private void showRecalls() {
        showingAppSettings = false;
        showingProductDetails = false;
        currentCode = "";
        currentListKey = "";
        setBottomNavigationVisible(true);
        updateNavigationState(TAB_RECALLS);
        topHeader.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.GONE);
        setProfileButtonVisible(true);
        sectionHeader.setVisibility(View.VISIBLE);
        txtTitle.setText("Alertas no Brasil");
        txtTitle.setTextSize(30);
        searchContainer.setVisibility(View.VISIBLE);
        configureSearchInput("Buscar nos alertas", recallQuery);
        configureTextSearchAction("Buscar alertas");
        filtersScroll.setVisibility(View.GONE);
        btnSort.setVisibility(View.VISIBLE);
        btnSort.setContentDescription("Filtrar ano do recall");
        cardStatus.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
        txtFooter.setVisibility(View.VISIBLE);
        dynamicContent.setVisibility(View.VISIBLE);
        rootScroll.scrollTo(0, 0);

        if (loadedRecallYear != selectedRecallYear && !loadingRecalls) {
            loadRecalls(true);
        } else {
            renderRecallItems();
        }
    }

    private void loadRecalls(boolean reset) {
        if (loadingRecalls) {
            return;
        }
        if (reset) {
            recallItems = new JSONArray();
            recallOffset = 0;
            recallTotal = -1;
            loadedRecallYear = selectedRecallYear;
        }

        loadingRecalls = true;
        setLoading(true);
        if (reset) {
            renderRecallItems();
        }

        int year = selectedRecallYear;
        int offset = recallOffset;
        executor.execute(() -> {
            RecallResult result = fetchRecallItems(year, offset);
            mainHandler.post(() -> {
                if (activeTab != TAB_RECALLS || year != selectedRecallYear) {
                    loadingRecalls = false;
                    setLoading(false);
                    return;
                }
                loadingRecalls = false;
                setLoading(false);
                if (!TextUtils.isEmpty(result.errorMessage)) {
                    renderRecallItems();
                    showMessage(result.errorMessage);
                    return;
                }
                appendRecallItems(result.items);
                recallTotal = result.total < 0 ? -1 : recallItems.length();
                recallOffset += RECALL_PAGE_SIZE;
                renderRecallItems();
            });
        });
    }

    private RecallResult fetchRecallItems(int year, int offset) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(ANVISA_NEWS_URL + year + "?b_start:int=" + offset);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", USER_AGENT);

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                return RecallResult.error("Não foi possível carregar recolhimentos da Anvisa agora. Tente novamente em instantes.");
            }

            String body = readText(connection.getInputStream());
            JSONArray results = parseAnvisaRecallItems(body);
            boolean hasMore = body.contains("b_start:int=" + (offset + RECALL_PAGE_SIZE));
            return RecallResult.success(results, hasMore ? -1 : recallItems.length() + results.length());
        } catch (Exception exception) {
            return RecallResult.error("Não consegui buscar recolhimentos da Anvisa. Verifique a conexão e tente novamente.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONArray parseAnvisaRecallItems(String html) {
        JSONArray items = new JSONArray();
        if (TextUtils.isEmpty(html)) {
            return items;
        }

        Matcher articleMatcher = ANVISA_ARTICLE_PATTERN.matcher(html);
        while (articleMatcher.find()) {
            String article = articleMatcher.group(1);
            Matcher titleMatcher = ANVISA_TITLE_PATTERN.matcher(article);
            if (!titleMatcher.find()) {
                continue;
            }

            String title = cleanHtmlText(titleMatcher.group(2));
            String url = titleMatcher.group(1);
            String summary = readAnvisaSummary(article);
            String searchable = normalizeSearchText(title + " " + summary + " " + url);
            if (!isBrazilianProductRecall(searchable)) {
                continue;
            }

            try {
                JSONObject item = new JSONObject();
                item.put("title", title);
                item.put("productName", extractAnvisaRecallProductName(title));
                item.put("type", classifyAnvisaRecallType(title + " " + summary));
                item.put("url", url);
                item.put("date", readAnvisaDate(article));
                item.put("summary", summary);
                item.put("source", SOURCE_ANVISA);
                items.put(item);
            } catch (Exception ignored) {
            }
        }
        return items;
    }

    private void enrichProductRecallAlert(ProductInfo product) {
        if (product == null || (TextUtils.isEmpty(product.name) && TextUtils.isEmpty(product.brand))) {
            return;
        }

        ArrayList<String> terms = productRecallTerms(product);
        if (terms.isEmpty()) {
            return;
        }

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int year = currentYear; year >= Math.max(FIRST_RECALL_YEAR, currentYear - 1); year--) {
            for (int offset = 0; offset < RECALL_PAGE_SIZE * 4; offset += RECALL_PAGE_SIZE) {
                String html = fetchAnvisaNewsPage(year, offset);
                if (TextUtils.isEmpty(html)) {
                    continue;
                }

                JSONArray alerts = parseAnvisaRecallItems(html);
                for (int index = 0; index < alerts.length(); index++) {
                    JSONObject alert = alerts.optJSONObject(index);
                    if (alert == null) {
                        continue;
                    }
                    if (productMatchesRecallAlert(product, terms, alert, false)) {
                        applyProductRecallAlert(product, alert);
                        return;
                    }
                }

                if (!html.contains("b_start:int=" + (offset + RECALL_PAGE_SIZE))) {
                    break;
                }
            }
        }
    }

    private void enrichProductRecallAlertInBackground(ProductInfo product) {
        if (product == null || TextUtils.isEmpty(product.code) || !TextUtils.isEmpty(product.recallAlertTitle)) {
            return;
        }

        String code = product.code;
        executor.execute(() -> {
            enrichProductRecallAlert(product);
            mainHandler.post(() -> {
                if (TextUtils.isEmpty(product.recallAlertTitle)) {
                    return;
                }
                addLocalItem(KEY_HISTORY, product);
                if (isProductSaved(product.code)) {
                    addLocalItem(KEY_SAVED_PRODUCTS, product);
                }
                if (showingProductDetails && code.equals(currentCode)) {
                    showProduct(product);
                    addDetailNotice("Alerta da Anvisa encontrado e adicionado ao produto.");
                }
            });
        });
    }

    private String fetchAnvisaNewsPage(int year, int offset) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(ANVISA_NEWS_URL + year + "?b_start:int=" + offset);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return "";
            }
            return readText(connection.getInputStream());
        } catch (Exception exception) {
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String fetchAnvisaRecallDetailText(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        HttpURLConnection connection = null;
        try {
            URL pageUrl = new URL(url);
            connection = (HttpURLConnection) pageUrl.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return "";
            }
            return normalizeSearchText(cleanHtmlText(readText(connection.getInputStream())));
        } catch (Exception exception) {
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private ArrayList<String> productRecallTerms(ProductInfo product) {
        ArrayList<String> terms = new ArrayList<>();
        String[] entries = normalizeSearchText(product.name).split("\\s+");
        for (String entry : entries) {
            if (entry.length() >= 4 && !isIgnoredRecallTerm(entry)) {
                addRecallTerm(terms, entry, 4);
            }
        }
        addRecallTerm(terms, product.categories, 5);
        for (String brandTerm : productBrandTerms(product)) {
            terms.remove(brandTerm);
        }
        return terms;
    }

    private void addRecallTerm(ArrayList<String> terms, String value, int minLength) {
        String normalized = normalizeSearchText(value);
        if (TextUtils.isEmpty(normalized)) {
            return;
        }
        String[] entries = normalized.split("[,\\s]+");
        for (String entry : entries) {
            if (entry.length() >= minLength && !terms.contains(entry) && !isIgnoredRecallTerm(entry)) {
                terms.add(entry);
            }
        }
    }

    private boolean isIgnoredRecallTerm(String term) {
        return "produto".equals(term)
                || "marca".equals(term)
                || "sabor".equals(term)
                || "tipo".equals(term)
                || "sem".equals(term)
                || "com".equals(term)
                || "para".equals(term)
                || "lote".equals(term)
                || "lotes".equals(term)
                || "unidade".equals(term);
    }

    private boolean productMatchesRecallAlert(ProductInfo product, ArrayList<String> terms, JSONObject alert, boolean detailChecked) {
        return productMatchesRecallAlert(product, terms, alert, detailChecked, "");
    }

    private boolean productMatchesRecallAlert(ProductInfo product, ArrayList<String> terms, JSONObject alert, boolean detailChecked, String detailText) {
        String alertText = normalizedRecallAlertText(alert);
        if (TextUtils.isEmpty(alertText)) {
            return false;
        }

        ArrayList<String> productTerms = productRecallTerms(product);
        ArrayList<String> brandTerms = productBrandTerms(product);
        if (!brandTerms.isEmpty()) {
            if (!containsAnyRecallTerm(alertText, brandTerms)) {
                return false;
            }
            return containsAnyRecallTerm(alertText, productTerms);
        }

        int matches = 0;
        for (String term : productTerms) {
            if (containsRecallTerm(alertText, term)) {
                matches++;
            }
        }
        return matches >= 3;
    }

    private String normalizedRecallAlertText(JSONObject alert) {
        if (alert == null) {
            return "";
        }
        return normalizeSearchText(
                alert.optString("productName")
                        + " " + alert.optString("title")
                        + " " + alert.optString("summary"));
    }

    private ArrayList<String> productBrandTerms(ProductInfo product) {
        ArrayList<String> terms = new ArrayList<>();
        if (product == null) {
            return terms;
        }
        String[] entries = normalizeSearchText(product.brand).split("[,\\s]+");
        for (String entry : entries) {
            if (entry.length() >= 3 && !isIgnoredRecallTerm(entry) && !terms.contains(entry)) {
                terms.add(entry);
            }
        }
        return terms;
    }

    private boolean containsAnyRecallTerm(String text, ArrayList<String> terms) {
        for (String term : terms) {
            if (containsRecallTerm(text, term)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsRecallTerm(String text, String term) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(term)) {
            return false;
        }
        return Pattern.compile("(^|[^a-z0-9])" + Pattern.quote(term) + "([^a-z0-9]|$)")
                .matcher(text)
                .find();
    }

    private void applyProductRecallAlert(ProductInfo product, JSONObject alert) {
        product.recallAlertTitle = alert.optString("title");
        product.recallAlertProductName = firstNonEmpty(alert.optString("productName"), alert.optString("title"));
        product.recallAlertType = alert.optString("type");
        product.recallAlertDate = alert.optString("date");
        product.recallAlertUrl = alert.optString("url");
        product.recallAlertSource = firstNonEmpty(alert.optString("source"), SOURCE_ANVISA);
    }

    private void sanitizeProductRecallAlert(ProductInfo product) {
        if (product == null || TextUtils.isEmpty(product.recallAlertTitle)) {
            return;
        }
        try {
            JSONObject alert = new JSONObject();
            alert.put("title", product.recallAlertTitle);
            alert.put("productName", product.recallAlertProductName);
            if (!productMatchesRecallAlert(product, productRecallTerms(product), alert, false)) {
                clearProductRecallAlert(product);
            }
        } catch (Exception exception) {
            clearProductRecallAlert(product);
        }
    }

    private void clearProductRecallAlert(ProductInfo product) {
        product.recallAlertTitle = "";
        product.recallAlertProductName = "";
        product.recallAlertType = "";
        product.recallAlertDate = "";
        product.recallAlertUrl = "";
        product.recallAlertSource = "";
    }

    private boolean isBrazilianProductRecall(String normalizedText) {
        if (TextUtils.isEmpty(normalizedText)) {
            return false;
        }
        return normalizedText.contains("recolh")
                || normalizedText.contains("proibid")
                || normalizedText.contains("suspens")
                || normalizedText.contains("apreens")
                || normalizedText.contains("interdit")
                || normalizedText.contains("contaminad")
                || normalizedText.contains("irregular");
    }

    private String classifyAnvisaRecallType(String value) {
        String text = normalizeSearchText(value);
        boolean prohibited = text.contains("proibid") || text.contains("proibe");
        boolean recalled = text.contains("recolh");
        boolean suspended = text.contains("suspens");
        boolean seized = text.contains("apreens");
        boolean interdicted = text.contains("interdit");

        if (prohibited && recalled) {
            return "Proibição e recolhimento";
        }
        if (suspended && recalled) {
            return "Suspensão e recolhimento";
        }
        if (prohibited) {
            return "Proibição";
        }
        if (recalled) {
            return "Recolhimento";
        }
        if (suspended) {
            return "Suspensão";
        }
        if (seized) {
            return "Apreensão";
        }
        if (interdicted) {
            return "Interdição";
        }
        if (text.contains("contaminad")) {
            return "Contaminação";
        }
        if (text.contains("irregular")) {
            return "Irregularidade";
        }
        return "Alerta";
    }

    private String extractAnvisaRecallProductName(String title) {
        if (TextUtils.isEmpty(title)) {
            return "";
        }
        String product = title.trim()
                .replaceAll("(?i)^anvisa\\s+", "")
                .replaceAll("(?i)^determina(?:do)?\\s+recolhimento\\s+de\\s+", "")
                .replaceAll("(?i)^determina\\s+recolhimento\\s+de\\s+", "")
                .replaceAll("(?i)^recolhimento\\s+de\\s+", "")
                .replaceAll("(?i)^lotes?\\s+de\\s+", "")
                .replaceAll("(?i)^proibida\\s+venda\\s+de\\s+", "")
                .replaceAll("(?i)^proibido\\s+(?:comércio|comercio|uso|consumo)\\s+de\\s+", "")
                .replaceAll("(?i)^proíbe\\s+", "")
                .replaceAll("(?i)^proibe\\s+", "")
                .replaceAll("(?i)\\s+(?:é|e|são|sao|foi|foram|está|esta|estão|estao)\\s+(?:recolhid[oa]s?|proibid[oa]s?|apreendid[oa]s?|suspens[oa]s?).*$", "")
                .replaceAll("(?i)^de\\s+", "")
                .trim();
        if (TextUtils.isEmpty(product)) {
            return title.trim();
        }
        return product.substring(0, 1).toUpperCase(Locale.ROOT) + product.substring(1);
    }

    private String readAnvisaDate(String article) {
        Matcher matcher = ANVISA_DATE_PATTERN.matcher(article);
        if (!matcher.find()) {
            return "";
        }
        String time = firstNonEmpty(matcher.group(2), "");
        return matcher.group(1) + (TextUtils.isEmpty(time) ? "" : " " + time);
    }

    private String readAnvisaSummary(String article) {
        Matcher matcher = ANVISA_SUMMARY_PATTERN.matcher(article);
        if (!matcher.find()) {
            return "";
        }
        return cleanHtmlText(matcher.group(1));
    }

    private String cleanHtmlText(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String text = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString();
        return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private void appendRecallItems(JSONArray newItems) {
        if (newItems == null) {
            return;
        }
        for (int index = 0; index < newItems.length(); index++) {
            JSONObject item = newItems.optJSONObject(index);
            if (item != null && !hasRecallItem(item.optString("url"))) {
                recallItems.put(item);
            }
        }
    }

    private boolean hasRecallItem(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        for (int index = 0; index < recallItems.length(); index++) {
            JSONObject item = recallItems.optJSONObject(index);
            if (item != null && url.equals(item.optString("url"))) {
                return true;
            }
        }
        return false;
    }

    private void renderRecallItems() {
        boolean filtering = !TextUtils.isEmpty(normalizeSearchText(recallQuery));
        txtSectionTitle.setText(filtering ? "Resultados" : "Notificações");
        txtSectionMeta.setText(recallMetaText());
        dynamicContent.removeAllViews();

        if (loadingRecalls && recallItems.length() == 0) {
            addInfoCard("Carregando", "Buscando recolhimentos de " + selectedRecallYear + "...");
            return;
        }

        JSONArray visibleItems = filterRecallItems(recallItems, normalizeSearchText(recallQuery));
        if (visibleItems.length() == 0) {
            if (recallItems.length() == 0) {
                addInfoCard("Nenhum alerta encontrado", "Não encontrei notícias de recolhimento, proibição ou suspensão de produtos para " + selectedRecallYear + " nessa fonte.");
            } else {
                addInfoCard("Nenhum resultado", "Nenhum recolhimento carregado corresponde ao texto digitado.");
            }
            addLoadMoreRecallButtonIfNeeded();
            addRecallSourceFootnote();
            return;
        }

        for (int index = 0; index < visibleItems.length(); index++) {
            JSONObject item = visibleItems.optJSONObject(index);
            if (item != null) {
                addRecallRow(item);
            }
        }
        addLoadMoreRecallButtonIfNeeded();
        addRecallSourceFootnote();
    }

    private JSONArray filterRecallItems(JSONArray items, String normalizedQuery) {
        JSONArray filtered = new JSONArray();
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) {
                continue;
            }
            String searchableText = normalizeSearchText(
                    item.optString("productName")
                            + " " + item.optString("type")
                            + " " + item.optString("title")
                            + " " + item.optString("summary")
                            + " " + item.optString("source"));
            if (TextUtils.isEmpty(normalizedQuery) || searchableText.contains(normalizedQuery)) {
                filtered.put(item);
            }
        }
        return filtered;
    }

    private void addRecallRow(JSONObject item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.TOP);
        row.setBackgroundResource(R.drawable.bg_result_row);
        row.setPadding(dp(8), dp(8), dp(10), dp(8));

        LinearLayout mediaColumn = new LinearLayout(this);
        mediaColumn.setOrientation(LinearLayout.VERTICAL);
        mediaColumn.setGravity(android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);

        ImageView image = new ImageView(this);
        image.setBackgroundResource(R.drawable.bg_product_image);
        image.setContentDescription("Alerta de " + firstNonEmpty(item.optString("type"), "produto").toLowerCase(Locale.ROOT));
        image.setImageResource(R.drawable.ic_recall);
        image.setColorFilter(getColor(R.color.one_ui_warning));
        image.setPadding(dp(16), dp(16), dp(16), dp(16));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        mediaColumn.addView(image, imageParams);

        TextView typeChip = createRecallTypeChip(item.optString("type"));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chipParams.setMargins(0, dp(6), 0, 0);
        mediaColumn.addView(typeChip, chipParams);

        LinearLayout.LayoutParams mediaParams = new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT);
        mediaParams.setMargins(0, 0, dp(10), 0);
        row.addView(mediaColumn, mediaParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView productName = new TextView(this);
        productName.setText(firstNonEmpty(item.optString("productName"), item.optString("title"), "Produto em alerta"));
        productName.setTextColor(getColor(R.color.one_ui_text_primary));
        productName.setTextSize(15);
        productName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        productName.setMaxLines(2);
        texts.addView(productName);

        TextView title = new TextView(this);
        String titleText = firstNonEmpty(item.optString("title"), "Notificação sem título");
        String url = item.optString("url");
        title.setText(titleText);
        title.setTextColor(getColor(TextUtils.isEmpty(url) ? R.color.one_ui_text_secondary : R.color.one_ui_accent));
        title.setTextSize(12);
        title.setMaxLines(3);
        title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        if (!TextUtils.isEmpty(url)) {
            title.setClickable(true);
            title.setFocusable(true);
            title.setContentDescription("Abrir notificação completa: " + titleText);
            title.setOnClickListener(view -> openExternalUrl(url));
        }
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(3), 0, 0);
        texts.addView(title, titleParams);

        TextView meta = new TextView(this);
        meta.setText(firstNonEmpty(item.optString("source"), SOURCE_ANVISA)
                + " · " + firstNonEmpty(item.optString("date"), "data não informada"));
        meta.setTextColor(getColor(R.color.one_ui_text_secondary));
        meta.setTextSize(12);
        meta.setMaxLines(2);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.setMargins(0, dp(4), 0, 0);
        texts.addView(meta, metaParams);

        addOptionalRecallText(texts, "Resumo", item.optString("summary"), R.color.one_ui_text_secondary);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        dynamicContent.addView(row, params);
        loadAnvisaRecallImage(image, item);
    }

    private TextView createRecallTypeChip(String type) {
        TextView chip = new TextView(this);
        String label = compactRecallType(type);
        chip.setText(label);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setTextColor(getColor(recallTypeColorRes(type)));
        chip.setTextSize(recallTypeTextSize(label));
        chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        chip.setMaxLines(2);
        chip.setIncludeFontPadding(false);
        chip.setPadding(dp(4), dp(4), dp(4), dp(4));
        chip.setBackground(roundedDrawable(getColor(R.color.one_ui_surface_alt), dp(14)));
        return chip;
    }

    private String compactRecallType(String type) {
        String normalized = normalizeSearchText(type);
        if (normalized.contains("proib") && normalized.contains("recolh")) {
            return "Proibido\nRecolhido";
        }
        if (normalized.contains("suspens") && normalized.contains("recolh")) {
            return "Suspenso\nRecolhido";
        }
        if (normalized.contains("proib")) {
            return "Proibido";
        }
        if (normalized.contains("recolh")) {
            return "Recolhido";
        }
        if (normalized.contains("suspens")) {
            return "Suspenso";
        }
        if (normalized.contains("apreens")) {
            return "Apreendido";
        }
        if (normalized.contains("interdit")) {
            return "Interditado";
        }
        if (normalized.contains("contamin")) {
            return "Contaminado";
        }
        if (normalized.contains("irregular")) {
            return "Irregular";
        }
        return firstNonEmpty(type, "Alerta");
    }

    private int recallTypeTextSize(String label) {
        String longestLine = "";
        String[] lines = firstNonEmpty(label, "").split("\\n");
        for (String line : lines) {
            if (line.length() > longestLine.length()) {
                longestLine = line;
            }
        }
        if (longestLine.length() <= 8) {
            return 10;
        }
        if (longestLine.length() <= 10) {
            return 9;
        }
        return 8;
    }

    private int recallTypeColorRes(String type) {
        String normalized = normalizeSearchText(type);
        if (normalized.contains("proib") || normalized.contains("apreens") || normalized.contains("interdit")) {
            return R.color.one_ui_danger;
        }
        if (normalized.contains("recolh") || normalized.contains("suspens") || normalized.contains("contamin")) {
            return R.color.one_ui_warning;
        }
        return R.color.one_ui_accent;
    }

    private void addOptionalRecallText(LinearLayout row, String label, String value, int colorRes) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        TextView text = new TextView(this);
        text.setText(label + ": " + value);
        text.setTextColor(getColor(colorRes));
        text.setTextSize(12);
        text.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, 0);
        row.addView(text, params);
    }

    private void addRecallNoticeLink(LinearLayout row, String titleText, String url) {
        if (TextUtils.isEmpty(titleText)) {
            return;
        }

        TextView link = new TextView(this);
        link.setText(titleText);
        link.setTextColor(getColor(TextUtils.isEmpty(url) ? R.color.one_ui_text_secondary : R.color.one_ui_accent));
        link.setTextSize(13);
        link.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        link.setLineSpacing(dp(2), 1f);
        link.setPaintFlags(link.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        if (!TextUtils.isEmpty(url)) {
            link.setClickable(true);
            link.setFocusable(true);
            link.setContentDescription("Abrir notificação completa: " + titleText);
            link.setOnClickListener(view -> openExternalUrl(url));
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        row.addView(link, params);
    }

    private void openExternalUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception exception) {
            showMessage("Não consegui abrir a notificação completa agora.");
        }
    }

    private void loadAnvisaRecallImage(ImageView image, JSONObject item) {
        String pageUrl = item.optString("url");
        if (TextUtils.isEmpty(pageUrl)) {
            return;
        }

        String cachedUrl = anvisaImageUrlCache.get(pageUrl);
        if (cachedUrl != null) {
            if (!TextUtils.isEmpty(cachedUrl)) {
                loadImageIntoView(image, pageUrl, cachedUrl);
            }
            return;
        }

        image.setTag(pageUrl);
        executor.execute(() -> {
            String imageUrl = fetchAnvisaNewsImageUrl(pageUrl);
            anvisaImageUrlCache.put(pageUrl, firstNonEmpty(imageUrl, ""));
            if (!TextUtils.isEmpty(imageUrl)) {
                mainHandler.post(() -> loadImageIntoView(image, pageUrl, imageUrl));
            }
        });
    }

    private String fetchAnvisaNewsImageUrl(String pageUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(pageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                return "";
            }
            String body = readText(connection.getInputStream());
            Matcher pageImageMatcher = ANVISA_PAGE_IMAGE_PATTERN.matcher(body);
            while (pageImageMatcher.find()) {
                String imageUrl = resolveAnvisaImageUrl(pageUrl, cleanHtmlText(pageImageMatcher.group(1)));
                if (isUsefulAnvisaImageUrl(imageUrl)) {
                    return imageUrl;
                }
            }

            Matcher imageMatcher = ANVISA_IMAGE_PATTERN.matcher(body);
            while (imageMatcher.find()) {
                String imageUrl = resolveAnvisaImageUrl(pageUrl, cleanHtmlText(imageMatcher.group(1)));
                if (isUsefulAnvisaImageUrl(imageUrl)) {
                    return imageUrl;
                }
            }
            return "";
        } catch (Exception exception) {
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String resolveAnvisaImageUrl(String pageUrl, String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) {
            return "";
        }
        try {
            return new URL(new URL(pageUrl), imageUrl).toString();
        } catch (Exception exception) {
            return imageUrl;
        }
    }

    private boolean isUsefulAnvisaImageUrl(String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) {
            return false;
        }
        String normalized = imageUrl.toLowerCase(Locale.ROOT);
        return (normalized.startsWith("http://") || normalized.startsWith("https://"))
                && !normalized.endsWith("/logo.png")
                && !normalized.contains("/logo.png")
                && !normalized.contains("favicon")
                && !normalized.contains("govbr")
                && !normalized.contains("barra-brasil")
                && !normalized.contains("avatar")
                && !normalized.contains("placeholder")
                && !normalized.contains("sprite")
                && !normalized.endsWith(".svg");
    }

    private void loadImageIntoView(ImageView image, String key, String imageUrl) {
        image.setTag(key);
        executor.execute(() -> {
            Bitmap finalBitmap = loadCachedImage(imageUrl);
            mainHandler.post(() -> {
                if (!key.equals(image.getTag()) || finalBitmap == null) {
                    return;
                }
                image.clearColorFilter();
                image.setPadding(0, 0, 0, 0);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageBitmap(finalBitmap);
            });
        });
    }

    private void addLoadMoreRecallButtonIfNeeded() {
        if (loadingRecalls || (recallTotal >= 0 && recallTotal <= recallItems.length())) {
            return;
        }
        Button loadMore = new Button(this);
        loadMore.setText("Carregar mais");
        loadMore.setAllCaps(false);
        loadMore.setTextColor(getColor(R.color.one_ui_text_primary));
        loadMore.setTextSize(14);
        loadMore.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        loadMore.setBackgroundResource(R.drawable.bg_button_secondary);
        loadMore.setOnClickListener(view -> loadRecalls(false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46));
        params.setMargins(0, dp(2), 0, dp(8));
        dynamicContent.addView(loadMore, params);
    }

    private void addRecallSourceFootnote() {
        TextView footnote = new TextView(this);
        footnote.setText("Fonte: notícias públicas da Anvisa filtradas para recolhimentos, proibições, suspensões e alertas de produtos no Brasil.");
        footnote.setTextColor(getColor(R.color.one_ui_text_muted));
        footnote.setTextSize(12);
        footnote.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(10));
        dynamicContent.addView(footnote, params);
    }

    private String recallMetaText() {
        String loadedText = recallItems.length() + (recallItems.length() == 1 ? " carregado" : " carregados");
        if (recallTotal >= 0) {
            loadedText += " de " + recallTotal;
        }
        return selectedRecallYear + " · " + loadedText;
    }

    private String formatRecallDate(String value) {
        if (TextUtils.isEmpty(value) || value.length() != 8) {
            return "data não informada";
        }
        return value.substring(6, 8) + "/" + value.substring(4, 6) + "/" + value.substring(0, 4);
    }

    private void showRecallYearMenu() {
        PopupMenu menu = new PopupMenu(this, btnSort);
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int year = currentYear; year >= FIRST_RECALL_YEAR; year--) {
            menu.getMenu().add(0, year, currentYear - year, year == selectedRecallYear ? year + " ✓" : String.valueOf(year));
        }
        menu.setOnMenuItemClickListener(item -> {
            selectedRecallYear = item.getItemId();
            loadRecalls(true);
            return true;
        });
        menu.show();
    }

    private void showProfile() {
        showingAppSettings = false;
        showingProductDetails = false;
        currentCode = "";
        setLoading(false);
        setBottomNavigationVisible(true);
        updateNavigationState(TAB_PROFILE);
        topHeader.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.VISIBLE);
        setProfileButtonVisible(false);
        txtTitle.setText("Perfil");
        txtTitle.setTextSize(30);
        sectionHeader.setVisibility(View.GONE);
        searchContainer.setVisibility(View.GONE);
        filtersScroll.setVisibility(View.GONE);
        btnSort.setVisibility(View.GONE);
        cardStatus.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
        txtFooter.setVisibility(View.GONE);
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

    private void showAppSettings() {
        showingAppSettings = true;
        showingProductDetails = false;
        currentCode = "";
        setLoading(false);
        setBottomNavigationVisible(true);
        updateNavigationState(TAB_PROFILE);
        topHeader.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.VISIBLE);
        setProfileButtonVisible(false);
        sectionHeader.setVisibility(View.GONE);
        txtTitle.setText("Configurações");
        txtTitle.setTextSize(30);
        searchContainer.setVisibility(View.GONE);
        filtersScroll.setVisibility(View.GONE);
        btnSort.setVisibility(View.GONE);
        cardStatus.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
        txtFooter.setVisibility(View.GONE);
        dynamicContent.removeAllViews();
        dynamicContent.setVisibility(View.VISIBLE);

        addProfileMenuRow(R.drawable.ic_settings, "Tema", currentThemeLabel(), true,
                view -> showThemeDialog());
    }

    private void showLocalList(String title, String sectionTitle, String key, String emptyMessage) {
        showingAppSettings = false;
        showingProductDetails = false;
        currentCode = "";
        setLoading(false);
        setBottomNavigationVisible(true);
        currentListTitle = title;
        currentListSectionTitle = sectionTitle;
        currentListKey = key;
        currentListEmptyMessage = emptyMessage;
        topHeader.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.GONE);
        setProfileButtonVisible(true);
        sectionHeader.setVisibility(View.VISIBLE);
        txtTitle.setText(title);
        txtTitle.setTextSize(30);
        searchContainer.setVisibility(View.VISIBLE);
        configureSearchInput("Buscar na minha lista", listSearchQuery);
        configureTextSearchAction("Buscar na minha lista");
        filtersScroll.setVisibility(View.GONE);
        btnSort.setVisibility(View.VISIBLE);
        cardStatus.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
        txtFooter.setVisibility(View.VISIBLE);
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
        String query = normalizeSearchText(listSearchQuery);
        boolean filtering = !TextUtils.isEmpty(query);
        txtSectionTitle.setText(filtering ? "Resultados" : sectionTitle);
        JSONArray filteredItems = filterItemsByNameOrBrand(rawItems, query);
        JSONArray items = sortItemsByScore(filteredItems, sortHighestFirst);
        txtSectionMeta.setText(items.length() + " itens" + (KEY_HISTORY.equals(key) ? "" : " · " + (sortHighestFirst ? "maior nota" : "menor nota")));
        dynamicContent.removeAllViews();
        if (items.length() == 0) {
            if (filtering) {
                addInfoCard("Nenhum resultado", "Nenhum produto salvo corresponde ao nome ou marca digitados.");
            } else {
                addInfoCard(sectionTitle, emptyMessage);
            }
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
        addCompactProductRow(item, TAB_SEARCH, false);
    }

    private void addProductRow(JSONObject item) {
        addCompactProductRow(item, TAB_LISTS, true);
    }

    private void addCompactProductRow(JSONObject item, int returnTab, boolean showRemoveButton) {
        String code = item.optString("code");
        ProductInfo savedProduct = productFromJson(item);
        String name = firstNonEmpty(item.optString("name"), "Produto sem nome");
        String brand = firstNonEmpty(item.optString("brand"), "Marca não informada");
        String classification = !TextUtils.isEmpty(savedProduct.recallAlertTitle)
                ? "Evite: alerta da Anvisa"
                : savedProduct.score != null && savedProduct.score.hasScore
                ? savedProduct.score.classification
                : "Sem nota suficiente";
        String score = item.optString("score");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_result_row);
        row.setPadding(dp(6), dp(6), dp(6), dp(6));
        row.setOnClickListener(view -> openProductDetails(code, returnTab, savedProduct));

        FrameLayout productPreview = new FrameLayout(this);

        ImageView productImage = new ImageView(this);
        productImage.setBackgroundResource(R.drawable.bg_product_image);
        productImage.setContentDescription("Imagem do produto");
        productImage.setImageResource(R.drawable.ic_scan);
        productImage.setColorFilter(getColor(R.color.one_ui_text_muted));
        productImage.setPadding(dp(12), dp(12), dp(12), dp(12));
        productImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        productPreview.addView(productImage, new FrameLayout.LayoutParams(dp(52), dp(52)));
        if (!TextUtils.isEmpty(savedProduct.imageUrl)) {
            productImage.setTag(code);
            loadImageIntoView(productImage, code, savedProduct.imageUrl);
        }

        TextView scoreView = new TextView(this);
        scoreView.setGravity(android.view.Gravity.CENTER);
        scoreView.setText(TextUtils.isEmpty(score) ? "-" : score);
        scoreView.setTextColor(getColor(android.R.color.white));
        scoreView.setTextSize(10);
        scoreView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        scoreView.setBackgroundResource(R.drawable.bg_score_circle);
        if (scoreView.getBackground() instanceof GradientDrawable) {
            GradientDrawable background = (GradientDrawable) scoreView.getBackground().mutate();
            background.setColor(getColor(scoreColorRes(parseScore(score))));
        }
        FrameLayout.LayoutParams scoreParams = new FrameLayout.LayoutParams(dp(28), dp(28));
        scoreParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        scoreParams.setMargins(0, dp(-2), dp(-2), 0);
        productPreview.addView(scoreView, scoreParams);

        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        row.addView(productPreview, previewParams);

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

        if (!TextUtils.isEmpty(savedProduct.recallAlertTitle)) {
            TextView recall = new TextView(this);
            recall.setText(firstNonEmpty(savedProduct.recallAlertType, "Alerta") + " da Anvisa");
            recall.setTextColor(getColor(R.color.one_ui_warning));
            recall.setTextSize(12);
            recall.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            recall.setMaxLines(1);
            texts.addView(recall);
        }

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (showRemoveButton) {
            ImageButton removeButton = new ImageButton(this);
            removeButton.setImageResource(R.drawable.ic_delete);
            removeButton.setColorFilter(getColor(R.color.one_ui_danger));
            removeButton.setBackgroundResource(R.drawable.bg_icon_action);
            removeButton.setContentDescription("Remover da lista");
            removeButton.setPadding(dp(7), dp(7), dp(7), dp(7));
            removeButton.setOnClickListener(view -> showRemoveSavedProductConfirmation(code, name, () -> {
                renderLocalList(
                        currentListTitle,
                        currentListSectionTitle,
                        currentListKey,
                        currentListEmptyMessage,
                        readLocalItems(currentListKey));
                if (currentProduct != null && code.equals(currentProduct.code)) {
                    updateSaveButtonState();
                }
            }));
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(36), dp(36));
            removeParams.setMargins(dp(4), 0, 0, 0);
            row.addView(removeButton, removeParams);
        }

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(1));
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
        addProfileMenuRow(R.drawable.ic_settings, "Configurações do aplicativo", true,
                view -> showAppSettings());
        addProfileMenuRow(R.drawable.ic_doc, "Termos e privacidade", true,
                view -> showTermsAndPrivacyDialog());
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

    private void applyPreferredTheme() {
        String mode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_THEME_MODE, THEME_SYSTEM);
        switch (mode) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private String currentThemeLabel() {
        String mode = getPrefs().getString(KEY_THEME_MODE, THEME_SYSTEM);
        switch (mode) {
            case THEME_LIGHT:
                return "Claro";
            case THEME_DARK:
                return "Escuro";
            default:
                return "Conforme configurado no sistema";
        }
    }

    private void showThemeDialog() {
        AlertDialog dialog = createRoundedDialog();
        LinearLayout content = createDialogContent();

        addDialogTitle(content, "Tema");
        addDialogMessage(content, "Escolha como o Boa Escolha deve aparecer neste aparelho.");

        String current = getPrefs().getString(KEY_THEME_MODE, THEME_SYSTEM);
        addThemeOption(content, dialog, "Conforme o sistema", "Usa o modo claro ou escuro configurado no celular.", THEME_SYSTEM, current);
        addThemeOption(content, dialog, "Claro", "Mantém o app sempre claro.", THEME_LIGHT, current);
        addThemeOption(content, dialog, "Escuro", "Mantém o app sempre escuro.", THEME_DARK, current);

        addDialogCloseButton(content, dialog, "Cancelar");
        showRoundedDialog(dialog, content);
    }

    private void addThemeOption(LinearLayout content, AlertDialog dialog, String titleText, String summaryText, String value, String current) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setClickable(true);
        row.setOnClickListener(view -> {
            getPrefs().edit().putString(KEY_THEME_MODE, value).apply();
            getPrefs().edit().putString(KEY_RESTORE_SCREEN, SCREEN_SETTINGS).apply();
            applyPreferredTheme();
            dialog.dismiss();
            recreate();
        });

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(getColor(R.color.one_ui_text_primary));
        title.setTextSize(15);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        texts.addView(title);

        TextView summary = new TextView(this);
        summary.setText(summaryText);
        summary.setTextColor(getColor(R.color.one_ui_text_secondary));
        summary.setTextSize(12);
        summary.setLineSpacing(dp(2), 1f);
        texts.addView(summary);

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (value.equals(current)) {
            TextView selected = new TextView(this);
            selected.setText("Selecionado");
            selected.setTextColor(getColor(R.color.one_ui_accent));
            selected.setTextSize(12);
            selected.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            row.addView(selected);
        }

        content.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void showTermsAndPrivacyDialog() {
        AlertDialog dialog = createRoundedDialog();
        LinearLayout content = createDialogContent();

        addDialogTitle(content, "Termos e privacidade");
        addDialogMessage(content,
                "O Boa Escolha consulta dados públicos do Open Food Facts e mostra uma nota simples para ajudar na comparação de alimentos.\n\n" +
                        "A nota é apenas uma orientação alimentar geral. Ela não substitui avaliação médica, nutricional ou recomendação profissional.\n\n" +
                        "Quando você entra com sua conta, o app pode sincronizar histórico e listas no Firebase para uso em outro aparelho. Nome, e-mail e produtos salvos ficam associados à sua conta.\n\n" +
                        "Você pode sair da conta pelo Perfil. Recursos e textos podem mudar enquanto o app estiver em desenvolvimento.");
        addDialogCloseButton(content, dialog, "Fechar");
        showRoundedDialog(dialog, content);
    }

    private AlertDialog createRoundedDialog() {
        return new AlertDialog.Builder(this).create();
    }

    private LinearLayout createDialogContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(18));
        content.setBackground(roundedDrawable(getColor(R.color.one_ui_surface), dp(28)));
        return content;
    }

    private void addDialogTitle(LinearLayout content, String titleText) {
        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(getColor(R.color.one_ui_text_primary));
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addDialogMessage(LinearLayout content, String messageText) {
        TextView message = new TextView(this);
        message.setText(messageText);
        message.setTextColor(getColor(R.color.one_ui_text_secondary));
        message.setTextSize(14);
        message.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, dp(10), 0, dp(14));
        content.addView(message, messageParams);
    }

    private void addDialogCloseButton(LinearLayout content, AlertDialog dialog, String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(getColor(R.color.one_ui_text_primary));
        button.setTextSize(14);
        button.setBackgroundResource(R.drawable.bg_button_secondary);
        button.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(120), dp(44));
        params.gravity = android.view.Gravity.END;
        params.setMargins(0, dp(8), 0, 0);
        content.addView(button, params);
    }

    private void showRoundedDialog(AlertDialog dialog, LinearLayout content) {
        dialog.setView(content);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
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
        addProfileMenuRow(iconRes, titleText, "", showChevron, listener);
    }

    private void addProfileMenuRow(int iconRes, String titleText, String summaryText, boolean showChevron, View.OnClickListener listener) {
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

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(getColor(R.color.one_ui_text_primary));
        title.setTextSize(15);
        texts.addView(title);

        if (!TextUtils.isEmpty(summaryText)) {
            TextView summary = new TextView(this);
            summary.setText(summaryText);
            summary.setTextColor(getColor(R.color.one_ui_text_secondary));
            summary.setTextSize(12);
            summary.setMaxLines(1);
            texts.addView(summary);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

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
            showRemoveSavedProductConfirmation(currentProduct.code, currentProduct.name, this::updateSaveButtonState);
            return;
        }
        addLocalItem(KEY_SAVED_PRODUCTS, currentProduct);
        updateSaveButtonState();
        showMessage("Produto salvo em Listas.");
    }

    private void showRemoveSavedProductConfirmation(String code, String productName, Runnable afterRemove) {
        if (TextUtils.isEmpty(code)) {
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this).create();

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(18));
        content.setBackground(roundedDrawable(getColor(R.color.one_ui_surface), dp(28)));

        TextView title = new TextView(this);
        title.setText("Remover da lista?");
        title.setTextColor(getColor(R.color.one_ui_text_primary));
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(this);
        String displayName = firstNonEmpty(productName, "este produto");
        message.setText("Você quer remover \"" + displayName + "\" da sua lista?");
        message.setTextColor(getColor(R.color.one_ui_text_secondary));
        message.setTextSize(14);
        message.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, dp(10), 0, dp(18));
        content.addView(message, messageParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(android.view.Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button cancelButton = new Button(this);
        cancelButton.setText("Cancelar");
        cancelButton.setAllCaps(false);
        cancelButton.setTextColor(getColor(R.color.one_ui_text_primary));
        cancelButton.setTextSize(14);
        cancelButton.setBackgroundResource(R.drawable.bg_button_secondary);
        cancelButton.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(112), dp(44));
        cancelParams.setMargins(0, 0, dp(8), 0);
        actions.addView(cancelButton, cancelParams);

        Button removeButton = new Button(this);
        removeButton.setText("Remover");
        removeButton.setAllCaps(false);
        removeButton.setTextColor(Color.WHITE);
        removeButton.setTextSize(14);
        removeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        removeButton.setBackground(roundedDrawable(getColor(R.color.one_ui_danger), dp(22)));
        removeButton.setOnClickListener(view -> {
            dialog.dismiss();
            removeProductFromList(KEY_SAVED_PRODUCTS, code, true);
            if (afterRemove != null) {
                afterRemove.run();
            }
            showMessage("Produto removido da lista.");
        });
        actions.addView(removeButton, new LinearLayout.LayoutParams(dp(112), dp(44)));

        content.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setView(content);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void updateSaveButtonState() {
        if (btnSaveProduct == null || currentProduct == null || TextUtils.isEmpty(currentProduct.code)) {
            return;
        }
        boolean saved = isProductSaved(currentProduct.code);
        btnSaveProduct.setText(saved ? "Remover da lista" : "Salvar na lista");
        btnSaveProduct.setTextColor(getColor(saved ? R.color.one_ui_danger : R.color.one_ui_accent));
        btnSaveProduct.setCompoundDrawablesWithIntrinsicBounds(
                saved ? R.drawable.ic_delete : R.drawable.ic_star,
                0,
                0,
                0);
        btnSaveProduct.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                getColor(saved ? R.color.one_ui_danger : R.color.one_ui_accent)));
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
        sanitizeProductRecallAlert(product);
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
            json.put("cachedAt", product.cachedAt > 0 ? product.cachedAt : System.currentTimeMillis());
            json.put("name", product.name);
            json.put("brand", product.brand);
            json.put("imageUrl", product.imageUrl);
            json.put("nutriScore", product.nutriScore);
            json.put("dataSources", firstNonEmpty(product.dataSources, SOURCE_OPEN_FOOD_FACTS));
            json.put("classification", product.score.classification);
            json.put("score", product.score.hasScore ? String.valueOf(product.score.value) : "");
            json.put("scoreSource", product.score.source);
            json.put("explanation", product.score.explanation);
            json.put("quantity", product.quantity);
            json.put("servingSize", product.servingSize);
            json.put("ingredients", product.ingredients);
            json.put("allergens", product.allergens);
            json.put("traces", product.traces);
            json.put("categories", product.categories);
            json.put("labels", product.labels);
            json.put("origins", product.origins);
            json.put("manufacturingPlaces", product.manufacturingPlaces);
            json.put("packaging", product.packaging);
            json.put("countries", product.countries);
            json.put("additives", product.additives);
            json.put("mineralWater", product.mineralWater);
            json.put("novaGroup", product.novaGroup);
            json.put("energyKcal", product.energyKcal);
            json.put("fat", product.fat);
            json.put("saturatedFat", product.saturatedFat);
            json.put("carbohydrates", product.carbohydrates);
            json.put("sugars", product.sugars);
            json.put("fiber", product.fiber);
            json.put("proteins", product.proteins);
            json.put("salt", product.salt);
            json.put("sodium", product.sodium);
            json.put("calcium", product.calcium);
            json.put("magnesium", product.magnesium);
            json.put("potassium", product.potassium);
            json.put("bicarbonate", product.bicarbonate);
            json.put("chloride", product.chloride);
            json.put("sulfate", product.sulfate);
            json.put("recallAlertTitle", firstNonEmpty(product.recallAlertTitle, ""));
            json.put("recallAlertProductName", firstNonEmpty(product.recallAlertProductName, ""));
            json.put("recallAlertType", firstNonEmpty(product.recallAlertType, ""));
            json.put("recallAlertDate", firstNonEmpty(product.recallAlertDate, ""));
            json.put("recallAlertUrl", firstNonEmpty(product.recallAlertUrl, ""));
            json.put("recallAlertSource", firstNonEmpty(product.recallAlertSource, ""));
        } catch (Exception ignored) {
        }
        return json;
    }

    private Map<String, Object> productToMap(ProductInfo product) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", firstNonEmpty(product.code, ""));
        map.put("cachedAt", product.cachedAt > 0 ? product.cachedAt : System.currentTimeMillis());
        map.put("name", firstNonEmpty(product.name, ""));
        map.put("brand", firstNonEmpty(product.brand, ""));
        map.put("imageUrl", firstNonEmpty(product.imageUrl, ""));
        map.put("nutriScore", firstNonEmpty(product.nutriScore, ""));
        map.put("dataSources", firstNonEmpty(product.dataSources, SOURCE_OPEN_FOOD_FACTS));
        map.put("classification", product.score != null ? product.score.classification : "Sem nota suficiente");
        map.put("score", product.score != null && product.score.hasScore ? product.score.value : -1);
        map.put("scoreSource", product.score != null ? firstNonEmpty(product.score.source, "") : "");
        map.put("explanation", product.score != null ? firstNonEmpty(product.score.explanation, "") : "");
        map.put("quantity", firstNonEmpty(product.quantity, ""));
        map.put("servingSize", firstNonEmpty(product.servingSize, ""));
        map.put("ingredients", firstNonEmpty(product.ingredients, ""));
        map.put("allergens", firstNonEmpty(product.allergens, ""));
        map.put("traces", firstNonEmpty(product.traces, ""));
        map.put("categories", firstNonEmpty(product.categories, ""));
        map.put("labels", firstNonEmpty(product.labels, ""));
        map.put("origins", firstNonEmpty(product.origins, ""));
        map.put("manufacturingPlaces", firstNonEmpty(product.manufacturingPlaces, ""));
        map.put("packaging", firstNonEmpty(product.packaging, ""));
        map.put("countries", firstNonEmpty(product.countries, ""));
        map.put("additives", firstNonEmpty(product.additives, ""));
        map.put("mineralWater", product.mineralWater);
        map.put("novaGroup", product.novaGroup);
        map.put("energyKcal", product.energyKcal);
        map.put("fat", product.fat);
        map.put("saturatedFat", product.saturatedFat);
        map.put("carbohydrates", product.carbohydrates);
        map.put("sugars", product.sugars);
        map.put("fiber", product.fiber);
        map.put("proteins", product.proteins);
        map.put("salt", product.salt);
        map.put("sodium", product.sodium);
        map.put("calcium", product.calcium);
        map.put("magnesium", product.magnesium);
        map.put("potassium", product.potassium);
        map.put("bicarbonate", product.bicarbonate);
        map.put("chloride", product.chloride);
        map.put("sulfate", product.sulfate);
        map.put("recallAlertTitle", firstNonEmpty(product.recallAlertTitle, ""));
        map.put("recallAlertProductName", firstNonEmpty(product.recallAlertProductName, ""));
        map.put("recallAlertType", firstNonEmpty(product.recallAlertType, ""));
        map.put("recallAlertDate", firstNonEmpty(product.recallAlertDate, ""));
        map.put("recallAlertUrl", firstNonEmpty(product.recallAlertUrl, ""));
        map.put("recallAlertSource", firstNonEmpty(product.recallAlertSource, ""));
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
        product.cachedAt = item.optLong("cachedAt", 0);
        product.name = item.optString("name");
        product.brand = item.optString("brand");
        product.imageUrl = item.optString("imageUrl");
        product.nutriScore = item.optString("nutriScore");
        product.dataSources = firstNonEmpty(item.optString("dataSources"), SOURCE_OPEN_FOOD_FACTS);
        product.quantity = item.optString("quantity");
        product.servingSize = item.optString("servingSize");
        product.ingredients = item.optString("ingredients");
        product.allergens = item.optString("allergens");
        product.traces = item.optString("traces");
        product.categories = item.optString("categories");
        product.labels = item.optString("labels");
        product.origins = item.optString("origins");
        product.manufacturingPlaces = item.optString("manufacturingPlaces");
        product.packaging = item.optString("packaging");
        product.countries = item.optString("countries");
        product.additives = item.optString("additives");
        product.mineralWater = item.optBoolean("mineralWater", isMineralWaterProduct(product));
        product.novaGroup = item.optInt("novaGroup", 0);
        product.energyKcal = item.optDouble("energyKcal", -1);
        product.fat = item.optDouble("fat", -1);
        product.saturatedFat = item.optDouble("saturatedFat", -1);
        product.carbohydrates = item.optDouble("carbohydrates", -1);
        product.sugars = item.optDouble("sugars", -1);
        product.fiber = item.optDouble("fiber", -1);
        product.proteins = item.optDouble("proteins", -1);
        product.salt = item.optDouble("salt", -1);
        product.sodium = item.optDouble("sodium", -1);
        product.calcium = item.optDouble("calcium", -1);
        product.magnesium = item.optDouble("magnesium", -1);
        product.potassium = item.optDouble("potassium", -1);
        product.bicarbonate = item.optDouble("bicarbonate", -1);
        product.chloride = item.optDouble("chloride", -1);
        product.sulfate = item.optDouble("sulfate", -1);
        product.recallAlertTitle = item.optString("recallAlertTitle");
        product.recallAlertProductName = item.optString("recallAlertProductName");
        product.recallAlertType = item.optString("recallAlertType");
        product.recallAlertDate = item.optString("recallAlertDate");
        product.recallAlertUrl = item.optString("recallAlertUrl");
        product.recallAlertSource = item.optString("recallAlertSource");
        String savedScore = item.optString("score");
        int score = parseScore(savedScore);
        product.score = !TextUtils.isEmpty(savedScore) && score >= 0
                ? ScoreInfo.withScore(
                        score,
                        firstNonEmpty(item.optString("classification"), classificationForScore(score)),
                        firstNonEmpty(item.optString("scoreSource"), "Boa Escolha"),
                        firstNonEmpty(item.optString("explanation"), "Nota salva anteriormente no Boa Escolha."))
                : ScoreInfo.withoutScore();
        sanitizeProductRecallAlert(product);
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

                    if (!showingProductDetails
                            && activeTab == TAB_LISTS
                            && key.equals(currentListKey)) {
                        renderLocalList(title, sectionTitle, key, emptyMessage, mergedItems);
                    }
                })
                .addOnFailureListener(exception -> {
                    if (!showingProductDetails
                            && activeTab == TAB_LISTS
                            && key.equals(currentListKey)) {
                        txtSectionMeta.setText("Erro de sync");
                        showMessage("Não consegui ler a nuvem. No Firebase, confira se o Firestore foi criado e se as regras permitem o usuário autenticado.");
                    }
                });
    }

    private JSONArray documentsToJson(java.util.List<DocumentSnapshot> documents) {
        JSONArray items = new JSONArray();
        for (DocumentSnapshot document : documents) {
            JSONObject item = new JSONObject();
            try {
                item.put("code", firstNonEmpty(document.getString("code"), document.getId()));
                Long cachedAt = document.getLong("cachedAt");
                Long updatedAt = document.getLong("updatedAt");
                item.put("cachedAt", cachedAt != null ? cachedAt : (updatedAt != null ? updatedAt : 0));
                item.put("name", firstNonEmpty(document.getString("name"), "Produto sem nome"));
                item.put("brand", firstNonEmpty(document.getString("brand"), ""));
                item.put("imageUrl", firstNonEmpty(document.getString("imageUrl"), ""));
                item.put("nutriScore", firstNonEmpty(document.getString("nutriScore"), ""));
                item.put("dataSources", firstNonEmpty(document.getString("dataSources"), SOURCE_OPEN_FOOD_FACTS));
                item.put("classification", firstNonEmpty(document.getString("classification"), "Sem nota suficiente"));
                item.put("scoreSource", firstNonEmpty(document.getString("scoreSource"), ""));
                item.put("explanation", firstNonEmpty(document.getString("explanation"), ""));
                item.put("quantity", firstNonEmpty(document.getString("quantity"), ""));
                item.put("servingSize", firstNonEmpty(document.getString("servingSize"), ""));
                item.put("ingredients", firstNonEmpty(document.getString("ingredients"), ""));
                item.put("allergens", firstNonEmpty(document.getString("allergens"), ""));
                item.put("traces", firstNonEmpty(document.getString("traces"), ""));
                item.put("categories", firstNonEmpty(document.getString("categories"), ""));
                item.put("labels", firstNonEmpty(document.getString("labels"), ""));
                item.put("origins", firstNonEmpty(document.getString("origins"), ""));
                item.put("manufacturingPlaces", firstNonEmpty(document.getString("manufacturingPlaces"), ""));
                item.put("packaging", firstNonEmpty(document.getString("packaging"), ""));
                item.put("countries", firstNonEmpty(document.getString("countries"), ""));
                item.put("additives", firstNonEmpty(document.getString("additives"), ""));
                Boolean mineralWater = document.getBoolean("mineralWater");
                item.put("mineralWater", mineralWater != null && mineralWater);
                Long novaGroup = document.getLong("novaGroup");
                item.put("novaGroup", novaGroup != null ? novaGroup : 0);
                putDocumentNumber(item, document, "energyKcal");
                putDocumentNumber(item, document, "fat");
                putDocumentNumber(item, document, "saturatedFat");
                putDocumentNumber(item, document, "carbohydrates");
                putDocumentNumber(item, document, "sugars");
                putDocumentNumber(item, document, "fiber");
                putDocumentNumber(item, document, "proteins");
                putDocumentNumber(item, document, "salt");
                putDocumentNumber(item, document, "sodium");
                putDocumentNumber(item, document, "calcium");
                putDocumentNumber(item, document, "magnesium");
                putDocumentNumber(item, document, "potassium");
                putDocumentNumber(item, document, "bicarbonate");
                putDocumentNumber(item, document, "chloride");
                putDocumentNumber(item, document, "sulfate");
                item.put("recallAlertTitle", firstNonEmpty(document.getString("recallAlertTitle"), ""));
                item.put("recallAlertProductName", firstNonEmpty(document.getString("recallAlertProductName"), ""));
                item.put("recallAlertType", firstNonEmpty(document.getString("recallAlertType"), ""));
                item.put("recallAlertDate", firstNonEmpty(document.getString("recallAlertDate"), ""));
                item.put("recallAlertUrl", firstNonEmpty(document.getString("recallAlertUrl"), ""));
                item.put("recallAlertSource", firstNonEmpty(document.getString("recallAlertSource"), ""));
                Long score = document.getLong("score");
                item.put("score", score != null && score >= 0 ? String.valueOf(score) : "");
                items.put(item);
            } catch (Exception ignored) {
            }
        }
        return items;
    }

    private void putDocumentNumber(JSONObject item, DocumentSnapshot document, String key) throws Exception {
        Double value = document.getDouble(key);
        item.put(key, value != null ? value : -1);
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

    private GradientDrawable roundedDrawable(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
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
                openProductDetails(contents, activeTab, null);
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

    private boolean handleBackNavigation() {
        if (showingProductDetails) {
            returnFromProductDetails();
            return true;
        }
        if (showingAppSettings) {
            showProfile();
            return true;
        }
        if (activeTab == TAB_PROFILE) {
            returnFromProfile();
            return true;
        }
        return false;
    }

    private void returnFromProfile() {
        if (profileReturnTab == TAB_LISTS) {
            showSavedProducts();
        } else if (profileReturnTab == TAB_RECALLS) {
            showRecalls();
        } else {
            showSearch();
        }
    }

    private void returnFromProductDetails() {
        showingProductDetails = false;
        currentCode = "";
        currentProduct = null;
        setLoading(false);
        if (productReturnTab == TAB_LISTS) {
            showSavedProducts();
        } else if (productReturnTab == TAB_PROFILE) {
            showProfile();
        } else {
            showSearch();
        }
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
        long cachedAt;
        String name;
        String brand;
        String imageUrl;
        String nutriScore;
        String dataSources;
        String quantity;
        String servingSize;
        String ingredients;
        String allergens;
        String traces;
        String categories;
        String labels;
        String origins;
        String manufacturingPlaces;
        String packaging;
        String countries;
        String additives;
        boolean mineralWater;
        int novaGroup;
        double energyKcal = -1;
        double fat = -1;
        double saturatedFat = -1;
        double carbohydrates = -1;
        double sugars = -1;
        double fiber = -1;
        double proteins = -1;
        double salt = -1;
        double sodium = -1;
        double calcium = -1;
        double magnesium = -1;
        double potassium = -1;
        double bicarbonate = -1;
        double chloride = -1;
        double sulfate = -1;
        String recallAlertTitle;
        String recallAlertProductName;
        String recallAlertType;
        String recallAlertDate;
        String recallAlertUrl;
        String recallAlertSource;
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

    private static class RecallResult {
        final JSONArray items;
        final int total;
        final String errorMessage;

        private RecallResult(JSONArray items, int total, String errorMessage) {
            this.items = items;
            this.total = total;
            this.errorMessage = errorMessage;
        }

        static RecallResult success(JSONArray items, int total) {
            return new RecallResult(items, total, null);
        }

        static RecallResult error(String message) {
            return new RecallResult(new JSONArray(), 0, message);
        }
    }

    private static class ProductSourceResult {
        final String sourceName;
        final JSONObject product;
        final String errorMessage;

        private ProductSourceResult(String sourceName, JSONObject product, String errorMessage) {
            this.sourceName = sourceName;
            this.product = product;
            this.errorMessage = errorMessage;
        }

        static ProductSourceResult success(String sourceName, JSONObject product) {
            return new ProductSourceResult(sourceName, product, null);
        }

        static ProductSourceResult notFound(String sourceName) {
            return new ProductSourceResult(sourceName, null, null);
        }

        static ProductSourceResult error(String sourceName, String message) {
            return new ProductSourceResult(sourceName, null, message);
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
