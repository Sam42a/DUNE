package org.jellyfin.androidtv.ui;

import static org.koin.java.KoinJavaComponent.inject;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.auth.repository.UserRepository;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.sdk.api.client.ApiClient;
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.MediaStream;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import kotlin.Lazy;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Call;
import okhttp3.Callback;
import timber.log.Timber;

public class SubtitleManagementPopup {
    private static final String JELLYFIN_SEARCH_ENDPOINT_TEMPLATE = "/Items/%s/RemoteSearch/Subtitles/%s";
    private static final String JELLYFIN_DOWNLOAD_ENDPOINT_TEMPLATE = "/Items/%s/RemoteSearch/Subtitles/%s";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private PopupWindow mPopup;
    private View mAnchorView;
    private Lifecycle mLifecycle;
    private BaseItemDto mBaseItem;
    private ListView mCurrentSubtitlesList;
    private ListView mAvailableSubtitlesList;
    private Spinner mLanguageSpinner;
    private Button mSearchButton;
    private Button mCancelButton;
    private ProgressBar mProgressBar;
    private TextView mAvailableSubtitlesLabel;
    private List<MediaStream> mCurrentSubtitles;
    private List<SubtitleSearchResult> mAvailableSubtitles;
    private Lazy<ApiClient> api = inject(ApiClient.class);
    private Lazy<UserRepository> userRepository = inject(UserRepository.class);
    private OkHttpClient httpClient;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isRequestActive = false;

    public SubtitleManagementPopup(Context context, Lifecycle lifecycle, View anchorView, BaseItemDto baseItem) {
        mLifecycle = lifecycle;
        mAnchorView = anchorView;
        mBaseItem = baseItem;

        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(R.layout.subtitle_management_popup, null);
        int popupWidth = Utils.convertDpToPixel(context, 900);
        int popupHeight = Utils.convertDpToPixel(context, 470);

        mPopup = new PopupWindow(layout, popupWidth, popupHeight);
        mPopup.setFocusable(true);
        mPopup.setOutsideTouchable(true);
        mPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        initializeViews(layout);
        setupLanguageSpinner();
        loadCurrentSubtitles();
        setupClickListeners();
    }

    private void initializeViews(View layout) {
        mCurrentSubtitlesList = layout.findViewById(R.id.current_subtitles_list);
        mAvailableSubtitlesList = layout.findViewById(R.id.available_subtitles_list);
        mLanguageSpinner = layout.findViewById(R.id.language_spinner);
        mSearchButton = layout.findViewById(R.id.search_button);
        mCancelButton = layout.findViewById(R.id.cancel_button);
        mProgressBar = layout.findViewById(R.id.progress_bar);
        mAvailableSubtitlesLabel = layout.findViewById(R.id.available_subtitles_label);
    }

    private void setupLanguageSpinner() {
        List<String> languages = Arrays.asList(
                "English", "Spanish", "French", "German", "Italian",
                "Portuguese", "Russian", "Chinese", "Japanese", "Korean",
                "Arabic", "Hindi", "Dutch", "Swedish", "Norwegian",
                "Danish", "Finnish", "Polish", "Czech", "Hungarian",
                "Greek", "Turkish", "Hebrew", "Thai", "Vietnamese",
                "Indonesian", "Malay", "Filipino", "Bengali", "Punjabi",
                "Urdu", "Persian", "Ukrainian", "Romanian", "Bulgarian",
                "Croatian", "Serbian", "Slovak", "Slovenian", "Lithuanian",
                "Latvian", "Estonian", "Icelandic", "Maltese", "Albanian",
                "Macedonian", "Bosnian", "Montenegrin", "Georgian", "Armenian",
                "Azerbaijani", "Kazakh", "Uzbek", "Mongolian", "Nepali",
                "Sinhala", "Tamil", "Telugu", "Marathi", "Gujarati",
                "Kannada", "Malayalam", "Burmese", "Khmer", "Lao",
                "Amharic", "Swahili", "Zulu", "Afrikaans", "Somali"
        );

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                mAnchorView.getContext(),
                android.R.layout.simple_spinner_item,
                languages
        );
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        mLanguageSpinner.setAdapter(adapter);
    }

    private void loadCurrentSubtitles() {
        if (mBaseItem.getMediaStreams() != null) {
            mCurrentSubtitles = new ArrayList<>();
            for (MediaStream stream : mBaseItem.getMediaStreams()) {
                if (stream.getType() == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE) {
                    mCurrentSubtitles.add(stream);
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    mAnchorView.getContext(),
                    android.R.layout.simple_list_item_1,
                    getSubtitleDisplayNames(mCurrentSubtitles)
            );
            mCurrentSubtitlesList.setAdapter(adapter);
        }
    }

    private List<String> getSubtitleDisplayNames(List<MediaStream> subtitles) {
        List<String> names = new ArrayList<>();
        for (MediaStream subtitle : subtitles) {
            String name;

            if (subtitle.getDisplayTitle() != null && !subtitle.getDisplayTitle().isEmpty()) {
                name = subtitle.getDisplayTitle();
            } else if (subtitle.getTitle() != null && !subtitle.getTitle().isEmpty()) {
                name = subtitle.getTitle();
            } else if (subtitle.getCodec() != null && subtitle.getLanguage() != null) {
                name = subtitle.getLanguage() + " (" + subtitle.getCodec().toUpperCase() + ")";
            } else if (subtitle.getLanguage() != null) {
                name = subtitle.getLanguage();
            } else {
                name = "Unknown";
            }

            if (subtitle.isDefault()) name += " (Default)";
            if (subtitle.isForced()) name += " (Forced)";

            names.add(name);
        }
        return names;
    }

    private void setupClickListeners() {
        mSearchButton.setOnClickListener(v -> searchSubtitles());
        mCancelButton.setOnClickListener(v -> dismiss());

        mAvailableSubtitlesList.setOnItemClickListener((parent, view, position, id) -> {
            if (mAvailableSubtitles != null && position < mAvailableSubtitles.size()) {
                downloadSubtitle(mAvailableSubtitles.get(position));
            }
        });
    }

    private void searchSubtitles() {
        if (isRequestActive) return;

        String selectedLanguage = (String) mLanguageSpinner.getSelectedItem();
        mProgressBar.setVisibility(View.VISIBLE);
        mSearchButton.setEnabled(false);
        isRequestActive = true;

        try {
            String userId = userRepository.getValue().getCurrentUser().getValue().getId().toString();
            String itemId = mBaseItem.getId().toString();
            String languageCode = getLanguageCode(selectedLanguage);

            // JSON payload
            JSONObject jsonParams = new JSONObject();
            jsonParams.put("ItemId", itemId);
            jsonParams.put("UserId", userId);
            jsonParams.put("Language", languageCode);
            jsonParams.put("MediaName", mBaseItem.getName());
            if (mBaseItem.getProductionYear() != null) {
                jsonParams.put("Year", mBaseItem.getProductionYear());
            }
            if ("Episode".equals(mBaseItem.getType())) {
                if (mBaseItem.getParentIndexNumber() != null) {
                    jsonParams.put("Season", mBaseItem.getParentIndexNumber());
                }
                if (mBaseItem.getIndexNumber() != null) {
                    jsonParams.put("Episode", mBaseItem.getIndexNumber());
                }
            }
            if (mBaseItem.getProviderIds() != null && mBaseItem.getProviderIds().containsKey("Imdb")) {
                jsonParams.put("ImdbId", mBaseItem.getProviderIds().get("Imdb"));
            }

            Timber.tag("SubtitleManagement").i("Searching subtitles: %s", jsonParams.toString());

            String endpoint = String.format(JELLYFIN_SEARCH_ENDPOINT_TEMPLATE, itemId, languageCode);
            ApiClient apiClient = api.getValue();
            String fullUrl = apiClient.getBaseUrl() + endpoint;

            Timber.tag("SubtitleManagement").i("Requesting: %s", fullUrl);

            String authHeader = "MediaBrowser Client=\"" + apiClient.getClientInfo().getName() + "\", Device=\"" + apiClient.getDeviceInfo().getName() + "\", DeviceId=\"" + apiClient.getDeviceInfo().getId() + "\", Version=\"" + apiClient.getClientInfo().getVersion() + "\", Token=\"" + apiClient.getAccessToken() + "\"";

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .addHeader("Authorization", authHeader)
                    .addHeader("X-Emby-Authorization", authHeader)
                    .get()
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    Timber.tag("SubtitleManagement").e(e, "Search failed: %s", e.getMessage());
                    mainHandler.post(() -> {
                        mProgressBar.setVisibility(View.GONE);
                        mSearchButton.setEnabled(true);
                        isRequestActive = false;
                        Utils.showToast(mAnchorView.getContext(), "Search failed: " + e.getMessage());
                    });
                }

                @Override
                public void onResponse(Call call, okhttp3.Response response) throws java.io.IOException {
                    handleSearchResponse(response);
                }
            });
        } catch (Exception e) {
            handleRequestFailure("Error preparing search: " + e.getMessage());
        }
    }


    private void handleSearchResponse(okhttp3.Response response) throws java.io.IOException {
        String responseBody = null;
        try {
            if (response.isSuccessful()) {
                responseBody = response.body().string();
                Timber.tag("SubtitleManagement").d("Search response: %s", responseBody);
                mAvailableSubtitles = parseSearchResults(responseBody);
                mainHandler.post(this::updateAvailableSubtitlesList);
            } else {
                responseBody = response.body().string();
                Timber.tag("SubtitleManagement").e("Search failed: HTTP %d %s, body: %s",
                        response.code(), response.message(), responseBody);
                handleRequestFailure("Search failed: HTTP " + response.code());
            }
        } catch (Exception e) {
            Timber.tag("SubtitleManagement").e(e, "Error processing response: %s", responseBody);
            handleRequestFailure("Error processing response: " + e.getMessage());
        } finally {
            response.close();
            mainHandler.post(() -> {
                mProgressBar.setVisibility(View.GONE);
                mSearchButton.setEnabled(true);
                isRequestActive = false;
            });
        }
    }

    private void downloadSubtitle(SubtitleSearchResult subtitle) {
        if (isRequestActive) return;

        mProgressBar.setVisibility(View.VISIBLE);
        mAvailableSubtitlesList.setEnabled(false);
        isRequestActive = true;

        try {
            String userId = userRepository.getValue().getCurrentUser().getValue().getId().toString();
            String itemId = mBaseItem.getId().toString();

            JSONObject jsonParams = new JSONObject();
            jsonParams.put("ItemId", itemId);
            jsonParams.put("UserId", userId);
            jsonParams.put("SubtitleId", subtitle.getId());
            jsonParams.put("Language", subtitle.getLanguage());

            Timber.tag("SubtitleManagement").i("Downloading subtitle: %s", jsonParams.toString());

            String subtitleId = jsonParams.optString("SubtitleId");
            String endpoint = String.format(JELLYFIN_DOWNLOAD_ENDPOINT_TEMPLATE, itemId, subtitleId);
            ApiClient apiClient = api.getValue();
            String fullUrl = apiClient.getBaseUrl() + endpoint;

            Timber.tag("SubtitleManagement").i("Requesting: %s with body: %s", fullUrl, jsonParams.toString());

            String authHeader = "MediaBrowser Client=\"" + apiClient.getClientInfo().getName() + "\", Device=\"" + apiClient.getDeviceInfo().getName() + "\", DeviceId=\"" + apiClient.getDeviceInfo().getId() + "\", Version=\"" + apiClient.getClientInfo().getVersion() + "\", Token=\"" + apiClient.getAccessToken() + "\"";

            RequestBody body = RequestBody.create(jsonParams.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(fullUrl)
                    .addHeader("Authorization", authHeader)
                    .addHeader("X-Emby-Authorization", authHeader)
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    Timber.tag("SubtitleManagement").e(e, "Download failed: %s", e.getMessage());
                    mainHandler.post(() -> {
                        mProgressBar.setVisibility(View.GONE);
                        mAvailableSubtitlesList.setEnabled(true);
                        isRequestActive = false;
                        Utils.showToast(mAnchorView.getContext(), "Download failed: " + e.getMessage());
                    });
                }

                @Override
                public void onResponse(Call call, okhttp3.Response response) throws java.io.IOException {
                    String responseBody = null;
                    try {
                        if (response.isSuccessful()) {
                            responseBody = response.body().string();
                            Timber.tag("SubtitleManagement").i("Download success: %s", responseBody);
                            mainHandler.post(() -> {
                                Utils.showToast(mAnchorView.getContext(), "Subtitle downloaded successfully");
                                loadCurrentSubtitles();
                            });
                        } else {
                            responseBody = response.body().string();
                            Timber.tag("SubtitleManagement").e("Download failed: HTTP %d %s, body: %s",
                                    response.code(), response.message(), responseBody);
                            mainHandler.post(() -> {
                                mProgressBar.setVisibility(View.GONE);
                                mAvailableSubtitlesList.setEnabled(true);
                                isRequestActive = false;
                                Utils.showToast(mAnchorView.getContext(), "Download failed: HTTP " + response.code());
                            });
                        }
                    } catch (Exception e) {
                        Timber.tag("SubtitleManagement").e(e, "Error processing download: %s", responseBody);
                        mainHandler.post(() -> {
                            mProgressBar.setVisibility(View.GONE);
                            mAvailableSubtitlesList.setEnabled(true);
                            isRequestActive = false;
                            Utils.showToast(mAnchorView.getContext(), "Error processing download: " + e.getMessage());
                        });
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Exception e) {
            handleRequestFailure("Error preparing download: " + e.getMessage());
        }
    }

    private void handleRequestFailure(String message) {
        Timber.tag("SubtitleManagement").e(message);
        mainHandler.post(() -> {
            mProgressBar.setVisibility(View.GONE);
            mSearchButton.setEnabled(true);
            Utils.showToast(mAnchorView.getContext(), message);
            isRequestActive = false;
        });
    }

    private String getLanguageCode(String language) {
        switch (language.toLowerCase()) {
            case "spanish": return "spa";
            case "french": return "fre";
            case "german": return "ger";
            case "italian": return "ita";
            case "portuguese": return "por";
            case "russian": return "rus";
            case "chinese": return "chi";
            case "japanese": return "jpn";
            case "korean": return "kor";
            case "arabic": return "ara";
            case "hindi": return "hin";
            case "dutch": return "dut";
            case "swedish": return "swe";
            case "norwegian": return "nor";
            default: return "eng";
        }
    }

    private void updateAvailableSubtitlesList() {
        if (mAvailableSubtitles != null && !mAvailableSubtitles.isEmpty()) {
            List<String> displayNames = new ArrayList<>();
            for (SubtitleSearchResult result : mAvailableSubtitles) {
                String displayName = result.getName();
                if (result.getLanguage() != null && !result.getLanguage().isEmpty()) {
                    displayName += " [" + getLanguageName(result.getLanguage()) + "]";
                }
                if (result.getDescription() != null && !result.getDescription().isEmpty()) {
                    displayName += " - " + result.getDescription();
                }
                displayNames.add(displayName);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    mAnchorView.getContext(),
                    android.R.layout.simple_list_item_1,
                    displayNames
            );
            mAvailableSubtitlesList.setAdapter(adapter);
            mAvailableSubtitlesLabel.setText("Available Subtitles (" + mAvailableSubtitles.size() + "):");
            mAvailableSubtitlesLabel.setVisibility(View.VISIBLE);
            mAvailableSubtitlesList.setVisibility(View.VISIBLE);
        } else {
            mAvailableSubtitlesLabel.setText("No subtitles found");
            mAvailableSubtitlesLabel.setVisibility(View.VISIBLE);
            mAvailableSubtitlesList.setVisibility(View.GONE);
        }
    }

    private String getLanguageName(String languageCode) {
        switch (languageCode.toLowerCase()) {
            case "spa": return "Spanish";
            case "fre": return "French";
            case "ger": return "German";
            case "ita": return "Italian";
            case "por": return "Portuguese";
            case "rus": return "Russian";
            case "chi": return "Chinese";
            case "jpn": return "Japanese";
            case "kor": return "Korean";
            case "ara": return "Arabic";
            case "hin": return "Hindi";
            case "dut": return "Dutch";
            case "swe": return "Swedish";
            case "nor": return "Norwegian";
            default: return "English";
        }
    }


    private List<SubtitleSearchResult> parseSearchResults(String jsonResponse) {
        List<SubtitleSearchResult> results = new ArrayList<>();
        try {
            if (jsonResponse != null && !jsonResponse.trim().isEmpty()) {
                JSONArray jsonArray = new JSONArray(jsonResponse);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject item = jsonArray.getJSONObject(i);
                    String id = item.optString("Id");
                    String name = item.optString("Name", "Unknown");
                    String language = item.optString("Language");
                    String description = item.optString("Description");
                    if (!id.isEmpty() && !name.isEmpty()) {
                        results.add(new SubtitleSearchResult(id, name, language, description));
                    }
                }
                Timber.tag("SubtitleManagement").i("Parsed %d subtitles", results.size());
            } else {
                Timber.tag("SubtitleManagement").i("Empty response");
            }
        } catch (Exception e) {
            Timber.tag("SubtitleManagement").e(e, "Parse error: %s", jsonResponse);
            try {
                // Fallback for Jellyfin native format
                JSONObject wrapper = new JSONObject(jsonResponse);
                if (wrapper.has("SearchResults")) {
                    JSONArray searchResults = wrapper.getJSONArray("SearchResults");
                    for (int i = 0; i < searchResults.length(); i++) {
                        JSONObject item = searchResults.getJSONObject(i);
                        String id = item.optString("Id");
                        String name = item.optString("Name", "Unknown");
                        String language = item.optString("Language");
                        String description = item.optString("Overview", item.optString("SearchProviderName", ""));
                        if (!id.isEmpty() && !name.isEmpty()) {
                            results.add(new SubtitleSearchResult(id, name, language, description));
                        }
                    }
                    Timber.tag("SubtitleManagement").i("Parsed %d native subtitles", results.size());
                }
            } catch (Exception e2) {
                Timber.tag("SubtitleManagement").e(e2, "Fallback parse failed");
            }
        }
        return results;
    }

    public void show() {
        if (mAnchorView != null) {
            mPopup.showAtLocation(mAnchorView, android.view.Gravity.CENTER, 0, 0);
        }
    }

    public void dismiss() {
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
        }
        if (isRequestActive) {
            httpClient.dispatcher().cancelAll();
        }
    }

    public boolean isShowing() {
        return mPopup != null && mPopup.isShowing();
    }

    public static class SubtitleSearchResult {
        private String id;
        private String name;
        private String language;
        private String description;

        public SubtitleSearchResult(String id, String name, String language, String description) {
            this.id = id;
            this.name = name;
            this.language = language;
            this.description = description;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getLanguage() { return language; }
        public String getDescription() { return description; }
    }
}
