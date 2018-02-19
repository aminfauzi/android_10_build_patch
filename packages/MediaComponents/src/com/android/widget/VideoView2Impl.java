/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.widget;

import android.annotation.NonNull;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.MediaPlayerInterface;
import android.media.Cea708CaptionRenderer;
import android.media.ClosedCaptionRenderer;
import android.media.Metadata;
import android.media.PlaybackParams;
import android.media.SRTRenderer;
import android.media.SubtitleController;
import android.media.TimedText;
import android.media.TtmlRenderer;
import android.media.WebVttRenderer;
import android.media.session.MediaController;
import android.media.session.MediaController.PlaybackInfo;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.update.VideoView2Provider;
import android.media.update.ViewGroupProvider;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.accessibility.AccessibilityManager;
import android.widget.MediaControlView2;
import android.widget.VideoView2;

import com.android.media.update.ApiHelper;
import com.android.media.update.R;
import com.android.support.mediarouter.media.MediaRouter;
import com.android.support.mediarouter.media.MediaRouteSelector;
import com.android.support.mediarouter.media.RemotePlaybackClient;
import com.android.support.mediarouter.media.MediaItemStatus;
import com.android.support.mediarouter.media.MediaSessionStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class VideoView2Impl extends BaseLayout
        implements VideoView2Provider, VideoViewInterface.SurfaceListener {
    private static final String TAG = "VideoView2";
    private static final boolean DEBUG = true; // STOPSHIP: Log.isLoggable(TAG, Log.DEBUG);
    private static final long DEFAULT_SHOW_CONTROLLER_INTERVAL_MS = 2000;

    private final VideoView2 mInstance;

    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    private static final int INVALID_TRACK_INDEX = -1;

    private AccessibilityManager mAccessibilityManager;
    private AudioManager mAudioManager;
    private AudioAttributes mAudioAttributes;
    private int mAudioFocusType = AudioManager.AUDIOFOCUS_GAIN; // legacy focus gain

    private Pair<Executor, VideoView2.OnCustomActionListener> mCustomActionListenerRecord;
    private VideoView2.OnViewTypeChangedListener mViewTypeChangedListener;
    private VideoView2.OnFullScreenRequestListener mFullScreenRequestListener;

    private VideoViewInterface mCurrentView;
    private VideoTextureView mTextureView;
    private VideoSurfaceView mSurfaceView;

    private MediaPlayer mMediaPlayer;
    private MediaControlView2 mMediaControlView;
    private MediaSession mMediaSession;
    private MediaController mMediaController;
    private MediaSession.Callback mRouteSessionCallback = new RouteSessionCallback();
    private MediaRouter mMediaRouter;
    private MediaRouteSelector mRouteSelector;
    private Metadata mMetadata;
    private String mTitle;

    private PlaybackState.Builder mStateBuilder;
    private List<PlaybackState.CustomAction> mCustomActionList;
    private int mTargetState = STATE_IDLE;
    private int mCurrentState = STATE_IDLE;
    private int mCurrentBufferPercentage;
    private long mSeekWhenPrepared;  // recording the seek position while preparing

    private int mVideoWidth;
    private int mVideoHeight;

    private ArrayList<Integer> mSubtitleTrackIndices;
    private SubtitleView mSubtitleView;
    private boolean mSubtitleEnabled;
    private int mSelectedTrackIndex;  // selected subtitle track index as MediaPlayer2 returns

    private float mSpeed;
    // TODO: Remove mFallbackSpeed when integration with MediaPlayer2's new setPlaybackParams().
    // Refer: https://docs.google.com/document/d/1nzAfns6i2hJ3RkaUre3QMT6wsDedJ5ONLiA_OOBFFX8/edit
    private float mFallbackSpeed;  // keep the original speed before 'pause' is called.

    private long mShowControllerIntervalMs;

    public VideoView2Impl(VideoView2 instance,
            ViewGroupProvider superProvider, ViewGroupProvider privateProvider) {
        super(instance, superProvider, privateProvider);
        mInstance = instance;
    }

    @Override
    public void initialize(@Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mVideoWidth = 0;
        mVideoHeight = 0;
        mSpeed = 1.0f;
        mFallbackSpeed = mSpeed;
        mSelectedTrackIndex = INVALID_TRACK_INDEX;
        // TODO: add attributes to get this value.
        mShowControllerIntervalMs = DEFAULT_SHOW_CONTROLLER_INTERVAL_MS;

        mAccessibilityManager = AccessibilityManager.getInstance(mInstance.getContext());

        mAudioManager = (AudioManager) mInstance.getContext()
                .getSystemService(Context.AUDIO_SERVICE);
        mAudioAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build();
        mInstance.setFocusable(true);
        mInstance.setFocusableInTouchMode(true);
        mInstance.requestFocus();

        // TODO: try to keep a single child at a time rather than always having both.
        mTextureView = new VideoTextureView(mInstance.getContext());
        mSurfaceView = new VideoSurfaceView(mInstance.getContext());
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        mTextureView.setLayoutParams(params);
        mSurfaceView.setLayoutParams(params);
        mTextureView.setSurfaceListener(this);
        mSurfaceView.setSurfaceListener(this);

        // TODO: Choose TextureView when SurfaceView cannot be created.
        // Choose surface view by default
        mTextureView.setVisibility(View.GONE);
        mSurfaceView.setVisibility(View.VISIBLE);
        mInstance.addView(mTextureView);
        mInstance.addView(mSurfaceView);
        mCurrentView = mSurfaceView;

        LayoutParams subtitleParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        mSubtitleView = new SubtitleView(mInstance.getContext());
        mSubtitleView.setLayoutParams(subtitleParams);
        mSubtitleView.setBackgroundColor(0);
        mInstance.addView(mSubtitleView);

        // TODO: Need a common namespace for attributes those are defined in updatable library.
        boolean enableControlView = (attrs == null) || attrs.getAttributeBooleanValue(
                "http://schemas.android.com/apk/res/android",
                "enableControlView", true);
        if (enableControlView) {
            mMediaControlView = new MediaControlView2(mInstance.getContext());
        }

        mSubtitleEnabled = (attrs == null) || attrs.getAttributeBooleanValue(
                "http://schemas.android.com/apk/res/android",
                "enableSubtitle", false);

        int viewType = (attrs == null) ? VideoView2.VIEW_TYPE_SURFACEVIEW
                : attrs.getAttributeIntValue(
                "http://schemas.android.com/apk/res/android",
                "viewType", 0);
        if (viewType == 0) {
            Log.d(TAG, "viewType attribute is surfaceView.");
            // TODO: implement
        } else if (viewType == 1) {
            Log.d(TAG, "viewType attribute is textureView.");
            // TODO: implement
        }
    }

    @Override
    public void setMediaControlView2_impl(MediaControlView2 mediaControlView, long intervalMs) {
        mMediaControlView = mediaControlView;
        mShowControllerIntervalMs = intervalMs;
        if (mRouteSelector != null) {
            ((MediaControlView2Impl) mMediaControlView.getProvider())
                    .setRouteSelector(mRouteSelector);
        }

        if (mInstance.isAttachedToWindow()) {
            attachMediaControlView();
        }
    }

    @Override
    public MediaController getMediaController_impl() {
        if (mMediaSession == null) {
            throw new IllegalStateException("MediaSession instance is not available.");
        }
        return mMediaController;
    }

    @Override
    public MediaControlView2 getMediaControlView2_impl() {
        return mMediaControlView;
    }

    @Override
    public void setSubtitleEnabled_impl(boolean enable) {
        if (enable != mSubtitleEnabled) {
            selectOrDeselectSubtitle(enable);
        }
        mSubtitleEnabled = enable;
    }

    @Override
    public boolean isSubtitleEnabled_impl() {
        return mSubtitleEnabled;
    }

    // TODO: remove setSpeed_impl once MediaController2 is ready.
    @Override
    public void setSpeed_impl(float speed) {
        if (speed <= 0.0f) {
            Log.e(TAG, "Unsupported speed (" + speed + ") is ignored.");
            return;
        }
        mSpeed = speed;
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            applySpeed();
        }
        updatePlaybackState();
    }

    @Override
    public void setAudioFocusRequest_impl(int focusGain) {
        if (focusGain != AudioManager.AUDIOFOCUS_NONE
                && focusGain != AudioManager.AUDIOFOCUS_GAIN
                && focusGain != AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                && focusGain != AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                && focusGain != AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) {
            throw new IllegalArgumentException("Illegal audio focus type " + focusGain);
        }
        mAudioFocusType = focusGain;
    }

    @Override
    public void setAudioAttributes_impl(AudioAttributes attributes) {
        if (attributes == null) {
            throw new IllegalArgumentException("Illegal null AudioAttributes");
        }
        mAudioAttributes = attributes;
    }

    @Override
    public void setRouteAttributes_impl(List<String> routeCategories, MediaPlayerInterface player) {
        // TODO: implement this.
    }

    @Override
    public void setRouteAttributes_impl(@NonNull List<String> routeCategories,
            MediaSession.Callback sessionPlayer) {
        MediaRouteSelector.Builder builder = new MediaRouteSelector.Builder();
        for (String category : routeCategories) {
            builder.addControlCategory(category);
        }
        mRouteSelector = builder.build();
        if (mMediaControlView != null) {
            ((MediaControlView2Impl) mMediaControlView.getProvider())
                    .setRouteSelector(mRouteSelector);
        }
        mMediaRouter = MediaRouter.getInstance(mInstance.getContext());
        mRouteSessionCallback = sessionPlayer;
        if (mMediaSession != null) {
            mMediaRouter.setMediaSession(mMediaSession);
        }
    }

    @Override
    public void setVideoPath_impl(String path) {
        mInstance.setVideoUri(Uri.parse(path));
    }

    @Override
    public void setVideoUri_impl(Uri uri) {
        mInstance.setVideoUri(uri, null);
    }

    @Override
    public void setVideoUri_impl(Uri uri, Map<String, String> headers) {
        mSeekWhenPrepared = 0;
        openVideo(uri, headers);
    }

    @Override
    public void setViewType_impl(int viewType) {
        if (viewType == mCurrentView.getViewType()) {
            return;
        }
        VideoViewInterface targetView;
        if (viewType == VideoView2.VIEW_TYPE_TEXTUREVIEW) {
            Log.d(TAG, "switching to TextureView");
            targetView = mTextureView;
        } else if (viewType == VideoView2.VIEW_TYPE_SURFACEVIEW) {
            Log.d(TAG, "switching to SurfaceView");
            targetView = mSurfaceView;
        } else {
            throw new IllegalArgumentException("Unknown view type: " + viewType);
        }
        ((View) targetView).setVisibility(View.VISIBLE);
        targetView.takeOver(mCurrentView);
        mInstance.requestLayout();
    }

    @Override
    public int getViewType_impl() {
        return mCurrentView.getViewType();
    }

    @Override
    public void setCustomActions_impl(
            List<PlaybackState.CustomAction> actionList,
            Executor executor, VideoView2.OnCustomActionListener listener) {
        mCustomActionList = actionList;
        mCustomActionListenerRecord = new Pair<>(executor, listener);

        // Create a new playback builder in order to clear existing the custom actions.
        mStateBuilder = null;
        updatePlaybackState();
    }

    @Override
    public void setOnViewTypeChangedListener_impl(VideoView2.OnViewTypeChangedListener l) {
        mViewTypeChangedListener = l;
    }

    @Override
    public void setFullScreenRequestListener_impl(VideoView2.OnFullScreenRequestListener l) {
        mFullScreenRequestListener = l;
    }

    @Override
    public void onAttachedToWindow_impl() {
        super.onAttachedToWindow_impl();

        // Create MediaSession
        mMediaSession = new MediaSession(mInstance.getContext(), "VideoView2MediaSession");
        mMediaSession.setCallback(new MediaSessionCallback());
        mMediaController = mMediaSession.getController();
        if (mMediaRouter != null) {
            mMediaRouter.setMediaSession(mMediaSession);
        }

        attachMediaControlView();
    }

    @Override
    public void onDetachedFromWindow_impl() {
        super.onDetachedFromWindow_impl();

        mMediaSession.release();
        mMediaSession = null;
        mMediaController = null;
    }

    @Override
    public CharSequence getAccessibilityClassName_impl() {
        return VideoView2.class.getName();
    }

    @Override
    public boolean onTouchEvent_impl(MotionEvent ev) {
        if (DEBUG) {
            Log.d(TAG, "onTouchEvent(). mCurrentState=" + mCurrentState
                    + ", mTargetState=" + mTargetState);
        }
        if (ev.getAction() == MotionEvent.ACTION_UP && mMediaControlView != null) {
            toggleMediaControlViewVisibility();
        }

        return super.onTouchEvent_impl(ev);
    }

    @Override
    public boolean onTrackballEvent_impl(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_UP && mMediaControlView != null) {
            toggleMediaControlViewVisibility();
        }

        return super.onTrackballEvent_impl(ev);
    }

    @Override
    public boolean dispatchTouchEvent_impl(MotionEvent ev) {
        // TODO: Test touch event handling logic thoroughly and simplify the logic.
        return super.dispatchTouchEvent_impl(ev);
    }

    ///////////////////////////////////////////////////
    // Implements VideoViewInterface.SurfaceListener
    ///////////////////////////////////////////////////

    @Override
    public void onSurfaceCreated(View view, int width, int height) {
        if (DEBUG) {
            Log.d(TAG, "onSurfaceCreated(). mCurrentState=" + mCurrentState
                    + ", mTargetState=" + mTargetState + ", width/height: " + width + "/" + height
                    + ", " + view.toString());
        }
        if (needToStart()) {
            mMediaController.getTransportControls().play();
        }
    }

    @Override
    public void onSurfaceDestroyed(View view) {
        if (DEBUG) {
            Log.d(TAG, "onSurfaceDestroyed(). mCurrentState=" + mCurrentState
                    + ", mTargetState=" + mTargetState + ", " + view.toString());
        }
    }

    @Override
    public void onSurfaceChanged(View view, int width, int height) {
        // TODO: Do we need to call requestLayout here?
        if (DEBUG) {
            Log.d(TAG, "onSurfaceChanged(). width/height: " + width + "/" + height
                    + ", " + view.toString());
        }
    }

    @Override
    public void onSurfaceTakeOverDone(VideoViewInterface view) {
        if (DEBUG) {
            Log.d(TAG, "onSurfaceTakeOverDone(). Now current view is: " + view);
        }
        mCurrentView = view;
        if (mViewTypeChangedListener != null) {
            mViewTypeChangedListener.onViewTypeChanged(mInstance, view.getViewType());
        }
        if (needToStart()) {
            mMediaController.getTransportControls().play();
        }
    }

    ///////////////////////////////////////////////////
    // Protected or private methods
    ///////////////////////////////////////////////////

    private void attachMediaControlView() {
        // Get MediaController from MediaSession and set it inside MediaControlView
        mMediaControlView.setController(mMediaSession.getController());

        LayoutParams params =
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mInstance.addView(mMediaControlView, params);
    }

    private boolean isInPlaybackState() {
        return (mMediaPlayer != null
                && mCurrentState != STATE_ERROR
                && mCurrentState != STATE_IDLE
                && mCurrentState != STATE_PREPARING);
    }

    private boolean needToStart() {
        return (mMediaPlayer != null
                && mCurrentState != STATE_PLAYING
                && mTargetState == STATE_PLAYING);
    }

    // Creates a MediaPlayer instance and prepare playback.
    private void openVideo(Uri uri, Map<String, String> headers) {
        resetPlayer();
        if (mAudioFocusType != AudioManager.AUDIOFOCUS_NONE) {
            // TODO this should have a focus listener
            AudioFocusRequest focusRequest;
            focusRequest = new AudioFocusRequest.Builder(mAudioFocusType)
                    .setAudioAttributes(mAudioAttributes)
                    .build();
            mAudioManager.requestAudioFocus(focusRequest);
        }

        try {
            Log.d(TAG, "openVideo(): creating new MediaPlayer instance.");
            mMediaPlayer = new MediaPlayer();
            mSurfaceView.setMediaPlayer(mMediaPlayer);
            mTextureView.setMediaPlayer(mMediaPlayer);
            mCurrentView.assignSurfaceToMediaPlayer(mMediaPlayer);

            // TODO: create SubtitleController in MediaPlayer, but we need
            // a context for the subtitle renderers
            final Context context = mInstance.getContext();
            final SubtitleController controller = new SubtitleController(
                    context, mMediaPlayer.getMediaTimeProvider(), mMediaPlayer);
            controller.registerRenderer(new WebVttRenderer(context));
            controller.registerRenderer(new TtmlRenderer(context));
            controller.registerRenderer(new Cea708CaptionRenderer(context));
            controller.registerRenderer(new ClosedCaptionRenderer(context));
            controller.registerRenderer(new SRTRenderer(context));
            mMediaPlayer.setSubtitleAnchor(
                    controller, (SubtitleController.Anchor) mSubtitleView);
            // TODO: Remove timed text related code later once relevant Renderer is defined.
            // This is just for debugging purpose.
            mMediaPlayer.setOnTimedTextListener(mTimedTextListener);

            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            mMediaPlayer.setDataSource(mInstance.getContext(), uri, headers);
            mMediaPlayer.setAudioAttributes(mAudioAttributes);
            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING;
            mMediaPlayer.prepareAsync();

            // Save file name as title since the file may not have a title Metadata.
            mTitle = uri.getPath();
            String scheme = uri.getScheme();
            if (scheme != null && scheme.equals("file")) {
                mTitle = uri.getLastPathSegment();
            }

            if (DEBUG) {
                Log.d(TAG, "openVideo(). mCurrentState=" + mCurrentState
                        + ", mTargetState=" + mTargetState);
            }
            /*
            for (Pair<InputStream, MediaFormat> pending: mPendingSubtitleTracks) {
                try {
                    mMediaPlayer.addSubtitleSource(pending.first, pending.second);
                } catch (IllegalStateException e) {
                    mInfoListener.onInfo(
                            mMediaPlayer, MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE, 0);
                }
            }
            */
        } catch (IOException | IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + uri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer,
                    MediaPlayer.MEDIA_ERROR_UNKNOWN, MediaPlayer.MEDIA_ERROR_IO);
        } finally {
            //mPendingSubtitleTracks.clear();
        }
    }

    /*
     * Reset the media player in any state
     */
    private void resetPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            //mPendingSubtitleTracks.clear();
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
            if (mAudioFocusType != AudioManager.AUDIOFOCUS_NONE) {
                mAudioManager.abandonAudioFocus(null);
            }
        }
        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    private void updatePlaybackState() {
        if (mStateBuilder == null) {
            // Get the capabilities of the player for this stream
            mMetadata = mMediaPlayer.getMetadata(MediaPlayer.METADATA_ALL,
                    MediaPlayer.BYPASS_METADATA_FILTER);

            // Add Play action as default
            long playbackActions = PlaybackState.ACTION_PLAY;
            if (mMetadata != null) {
                if (!mMetadata.has(Metadata.PAUSE_AVAILABLE)
                        || mMetadata.getBoolean(Metadata.PAUSE_AVAILABLE)) {
                    playbackActions |= PlaybackState.ACTION_PAUSE;
                }
                if (!mMetadata.has(Metadata.SEEK_BACKWARD_AVAILABLE)
                        || mMetadata.getBoolean(Metadata.SEEK_BACKWARD_AVAILABLE)) {
                    playbackActions |= PlaybackState.ACTION_REWIND;
                }
                if (!mMetadata.has(Metadata.SEEK_FORWARD_AVAILABLE)
                        || mMetadata.getBoolean(Metadata.SEEK_FORWARD_AVAILABLE)) {
                    playbackActions |= PlaybackState.ACTION_FAST_FORWARD;
                }
                if (!mMetadata.has(Metadata.SEEK_AVAILABLE)
                        || mMetadata.getBoolean(Metadata.SEEK_AVAILABLE)) {
                    playbackActions |= PlaybackState.ACTION_SEEK_TO;
                }
            } else {
                playbackActions |= (PlaybackState.ACTION_PAUSE |
                        PlaybackState.ACTION_REWIND | PlaybackState.ACTION_FAST_FORWARD |
                        PlaybackState.ACTION_SEEK_TO);
            }
            mStateBuilder = new PlaybackState.Builder();
            mStateBuilder.setActions(playbackActions);

            if (mCustomActionList != null) {
                for (PlaybackState.CustomAction action : mCustomActionList) {
                    mStateBuilder.addCustomAction(action);
                }
            }
        }
        mStateBuilder.setState(getCorrespondingPlaybackState(),
                mMediaPlayer.getCurrentPosition(), mSpeed);
        if (mCurrentState != STATE_ERROR
            && mCurrentState != STATE_IDLE
            && mCurrentState != STATE_PREPARING) {
            mStateBuilder.setBufferedPosition(
                    (long) (mCurrentBufferPercentage / 100.0) * mMediaPlayer.getDuration());
        }

        // Set PlaybackState for MediaSession
        if (mMediaSession != null) {
            PlaybackState state = mStateBuilder.build();
            mMediaSession.setPlaybackState(state);
        }
    }

    private int getCorrespondingPlaybackState() {
        switch (mCurrentState) {
            case STATE_ERROR:
                return PlaybackState.STATE_ERROR;
            case STATE_IDLE:
                return PlaybackState.STATE_NONE;
            case STATE_PREPARING:
                return PlaybackState.STATE_CONNECTING;
            case STATE_PREPARED:
                return PlaybackState.STATE_PAUSED;
            case STATE_PLAYING:
                return PlaybackState.STATE_PLAYING;
            case STATE_PAUSED:
                return PlaybackState.STATE_PAUSED;
            case STATE_PLAYBACK_COMPLETED:
                return PlaybackState.STATE_STOPPED;
            default:
                return -1;
        }
    }

    private final Runnable mFadeOut = new Runnable() {
        @Override
        public void run() {
            if (mCurrentState == STATE_PLAYING) {
                mMediaControlView.setVisibility(View.GONE);
            }
        }
    };

    private void showController() {
        // TODO: Decide what to show when the state is not in playback state
        if (mMediaControlView == null || !isInPlaybackState()) {
            return;
        }
        mMediaControlView.removeCallbacks(mFadeOut);
        mMediaControlView.setVisibility(View.VISIBLE);
        if (mShowControllerIntervalMs != 0
            && !mAccessibilityManager.isTouchExplorationEnabled()) {
            mMediaControlView.postDelayed(mFadeOut, mShowControllerIntervalMs);
        }
    }

    private void toggleMediaControlViewVisibility() {
        if (mMediaControlView.getVisibility() == View.VISIBLE) {
            mMediaControlView.removeCallbacks(mFadeOut);
            mMediaControlView.setVisibility(View.GONE);
        } else {
            showController();
        }
    }

    private void applySpeed() {
        PlaybackParams params = mMediaPlayer.getPlaybackParams().allowDefaults();
        if (mSpeed != params.getSpeed()) {
            try {
                params.setSpeed(mSpeed);
                mMediaPlayer.setPlaybackParams(params);
                mFallbackSpeed = mSpeed;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "PlaybackParams has unsupported value: " + e);
                // TODO: should revise this part after integrating with MP2.
                // If mSpeed had an illegal value for speed rate, system will determine best
                // handling (see PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT).
                // Note: The pre-MP2 returns 0.0f when it is paused. In this case, VideoView2 will
                // use mFallbackSpeed instead.
                float fallbackSpeed = mMediaPlayer.getPlaybackParams().allowDefaults().getSpeed();
                if (fallbackSpeed > 0.0f) {
                    mFallbackSpeed = fallbackSpeed;
                }
                mSpeed = mFallbackSpeed;
            }
        }
    }

    private boolean isRemotePlayback() {
        if (mMediaController == null) {
            return false;
        }
        PlaybackInfo playbackInfo = mMediaController.getPlaybackInfo();
        return (playbackInfo != null)
                && (playbackInfo.getPlaybackType() == PlaybackInfo.PLAYBACK_TYPE_REMOTE);
    }

    private void selectOrDeselectSubtitle(boolean select) {
        if (!isInPlaybackState()) {
            return;
        }
        if (select) {
            if (mSubtitleTrackIndices.size() > 0) {
                // Select first subtitle track
                mSelectedTrackIndex = mSubtitleTrackIndices.get(0);
                mMediaPlayer.selectTrack(mSelectedTrackIndex);
                mSubtitleView.setVisibility(View.VISIBLE);
            }
        } else {
            if (mSelectedTrackIndex != INVALID_TRACK_INDEX) {
                mMediaPlayer.deselectTrack(mSelectedTrackIndex);
                mSelectedTrackIndex = INVALID_TRACK_INDEX;
                mSubtitleView.setVisibility(View.GONE);
            }
        }
    }

    private void extractSubtitleTracks() {
        MediaPlayer.TrackInfo[] trackInfos = mMediaPlayer.getTrackInfo();
        boolean previouslyNoTracks = mSubtitleTrackIndices == null
                || mSubtitleTrackIndices.size() == 0;
        mSubtitleTrackIndices = new ArrayList<>();
        for (int i = 0; i < trackInfos.length; ++i) {
            int trackType = trackInfos[i].getTrackType();
            if (trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE
                    || trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                  mSubtitleTrackIndices.add(i);
            }
        }
        if (mSubtitleTrackIndices.size() > 0) {
            if (previouslyNoTracks) {
                selectOrDeselectSubtitle(mSubtitleEnabled);
                // Notify MediaControlView that subtitle track exists
                // TODO: Send the subtitle track list to MediaSession for MCV2.
                Bundle data = new Bundle();
                data.putBoolean(MediaControlView2Impl.KEY_STATE_CONTAINS_SUBTITLE, true);
                mMediaSession.sendSessionEvent(
                        MediaControlView2Impl.EVENT_UPDATE_SUBTITLE_STATUS, data);
            }
        } else {
            Bundle data = new Bundle();
            data.putBoolean(MediaControlView2Impl.KEY_STATE_CONTAINS_SUBTITLE, false);
            mMediaSession.sendSessionEvent(
                    MediaControlView2Impl.EVENT_UPDATE_SUBTITLE_STATUS, data);
        }
    }

    MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    if (DEBUG) {
                        Log.d(TAG, "OnVideoSizeChanged(): size: " + width + "/" + height);
                    }
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    if (DEBUG) {
                        Log.d(TAG, "OnVideoSizeChanged(): mVideoSize:" + mVideoWidth + "/"
                                + mVideoHeight);
                    }

                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        mInstance.requestLayout();
                    }
                }
            };

    MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {
            if (DEBUG) {
                Log.d(TAG, "OnPreparedListener(). mCurrentState=" + mCurrentState
                        + ", mTargetState=" + mTargetState);
            }
            mCurrentState = STATE_PREPARED;
            // Create and set playback state for MediaControlView2
            updatePlaybackState();
            extractSubtitleTracks();

            if (mMediaControlView != null) {
                mMediaControlView.setEnabled(true);
            }
            int videoWidth = mp.getVideoWidth();
            int videoHeight = mp.getVideoHeight();

            // mSeekWhenPrepared may be changed after seekTo() call
            long seekToPosition = mSeekWhenPrepared;
            if (seekToPosition != 0) {
                mMediaController.getTransportControls().seekTo(seekToPosition);
            }

            if (videoWidth != 0 && videoHeight != 0) {
                if (videoWidth != mVideoWidth || videoHeight != mVideoHeight) {
                    if (DEBUG) {
                        Log.i(TAG, "OnPreparedListener() : ");
                        Log.i(TAG, " video size: " + videoWidth + "/" + videoHeight);
                        Log.i(TAG, " measuredSize: " + mInstance.getMeasuredWidth() + "/"
                                + mInstance.getMeasuredHeight());
                        Log.i(TAG, " viewSize: " + mInstance.getWidth() + "/"
                                + mInstance.getHeight());
                    }

                    mVideoWidth = videoWidth;
                    mVideoHeight = videoHeight;
                    mInstance.requestLayout();
                }

                if (needToStart()) {
                    mMediaController.getTransportControls().play();
                }
            } else {
                // We don't know the video size yet, but should start anyway.
                // The video size might be reported to us later.
                if (needToStart()) {
                    mMediaController.getTransportControls().play();
                }
            }

            // Get and set duration and title values as MediaMetadata for MediaControlView2
            MediaMetadata.Builder builder = new MediaMetadata.Builder();
            if (mMetadata != null && mMetadata.has(Metadata.TITLE)) {
                mTitle = mMetadata.getString(Metadata.TITLE);
            }
            builder.putString(MediaMetadata.METADATA_KEY_TITLE, mTitle);
            builder.putLong(MediaMetadata.METADATA_KEY_DURATION, mMediaPlayer.getDuration());

            if (mMediaSession != null) {
                mMediaSession.setMetadata(builder.build());
            }
        }
    };

    private MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;
                    updatePlaybackState();

                    if (mAudioFocusType != AudioManager.AUDIOFOCUS_NONE) {
                        mAudioManager.abandonAudioFocus(null);
                    }
                }
            };

    private MediaPlayer.OnInfoListener mInfoListener =
            new MediaPlayer.OnInfoListener() {
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {
                        extractSubtitleTracks();
                    }
                    return true;
                }
            };

    private MediaPlayer.OnErrorListener mErrorListener =
            new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mp, int frameworkErr, int implErr) {
                    if (DEBUG) {
                        Log.d(TAG, "Error: " + frameworkErr + "," + implErr);
                    }
                    mCurrentState = STATE_ERROR;
                    mTargetState = STATE_ERROR;
                    updatePlaybackState();

                    if (mMediaControlView != null) {
                        mMediaControlView.setVisibility(View.GONE);
                    }
                    return true;
                }
            };

    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new MediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    mCurrentBufferPercentage = percent;
                    updatePlaybackState();
                }
            };

    // TODO: Remove timed text related code later once relevant Renderer is defined.
    // This is just for debugging purpose.
    private MediaPlayer.OnTimedTextListener mTimedTextListener =
            new MediaPlayer.OnTimedTextListener() {
                public void onTimedText(MediaPlayer mp, TimedText text) {
                    Log.d(TAG, "TimedText: " + text.getText());
                }
            };

    private class MediaSessionCallback extends MediaSession.Callback {
        @Override
        public void onCommand(String command, Bundle args, ResultReceiver receiver) {
            if (isRemotePlayback()) {
                mRouteSessionCallback.onCommand(command, args, receiver);
            } else {
                switch (command) {
                    case MediaControlView2.COMMAND_SHOW_SUBTITLE:
                        mInstance.setSubtitleEnabled(true);
                        break;
                    case MediaControlView2.COMMAND_HIDE_SUBTITLE:
                        mInstance.setSubtitleEnabled(false);
                        break;
                    case MediaControlView2.COMMAND_SET_FULLSCREEN:
                        if (mFullScreenRequestListener != null) {
                            mFullScreenRequestListener.onFullScreenRequest(
                                    mInstance,
                                    args.getBoolean(MediaControlView2Impl.ARGUMENT_KEY_FULLSCREEN));
                        }
                        break;
                }
            }
            showController();
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            mCustomActionListenerRecord.first.execute(() ->
                    mCustomActionListenerRecord.second.onCustomAction(action, extras));
            showController();
        }

        @Override
        public void onPlay() {
            if (isRemotePlayback()) {
                mRouteSessionCallback.onPlay();
            } else {
                if (isInPlaybackState() && mCurrentView.hasAvailableSurface()) {
                    applySpeed();
                    mMediaPlayer.start();
                    mCurrentState = STATE_PLAYING;
                    updatePlaybackState();
                }
                mTargetState = STATE_PLAYING;
                if (DEBUG) {
                    Log.d(TAG, "onPlay(). mCurrentState=" + mCurrentState
                            + ", mTargetState=" + mTargetState);
                }
            }
            showController();
        }

        @Override
        public void onPause() {
            if (isRemotePlayback()) {
                mRouteSessionCallback.onPause();
            } else {
                if (isInPlaybackState()) {
                    if (mMediaPlayer.isPlaying()) {
                        mMediaPlayer.pause();
                        mCurrentState = STATE_PAUSED;
                        updatePlaybackState();
                    }
                }
                mTargetState = STATE_PAUSED;
                if (DEBUG) {
                    Log.d(TAG, "onPause(). mCurrentState=" + mCurrentState
                            + ", mTargetState=" + mTargetState);
                }
            }
            showController();
        }

        @Override
        public void onSeekTo(long pos) {
            if (isRemotePlayback()) {
                mRouteSessionCallback.onSeekTo(pos);
            } else {
                if (isInPlaybackState()) {
                    mMediaPlayer.seekTo(pos, MediaPlayer.SEEK_PREVIOUS_SYNC);
                    mSeekWhenPrepared = 0;
                    updatePlaybackState();
                } else {
                    mSeekWhenPrepared = pos;
                }
            }
            showController();
        }

        @Override
        public void onStop() {
            if (isRemotePlayback()) {
                mRouteSessionCallback.onStop();
            } else {
                resetPlayer();
            }
            showController();
        }
    }

    private class RouteSessionCallback extends MediaSession.Callback {
        RemotePlaybackClient mClient;

        RemotePlaybackClient.StatusCallback mStatusCallback =
                new RemotePlaybackClient.StatusCallback() {
            @Override
            public void onItemStatusChanged(Bundle data,
                    String sessionId, MediaSessionStatus sessionStatus,
                    String itemId, MediaItemStatus itemStatus) {
                // TODO: implement this
            }

            @Override
            public void onSessionStatusChanged(Bundle data,
                    String sessionId, MediaSessionStatus sessionStatus) {
                // TODO: implement this
            }

            @Override
            public void onSessionChanged(String sessionId) {
                // TODO: implement this
            }
        };

        @Override
        public void onCommand(String command, Bundle args, ResultReceiver receiver) {
            ensureClient();
            // TODO: implement this
        }

        @Override
        public void onPlay() {
            ensureClient();
            // TODO: implement this
        }

        @Override
        public void onPause() {
            ensureClient();
            // TODO: implement this
        }

        @Override
        public void onSeekTo(long pos) {
            ensureClient();
            // TODO: implement this
        }

        @Override
        public void onStop() {
            ensureClient();
            // TODO: implement this
        }

        private void ensureClient() {
            if (mClient == null) {
                mClient = new RemotePlaybackClient(
                        mInstance.getContext(), mMediaRouter.getSelectedRoute());
                mClient.setStatusCallback(mStatusCallback);
            }
        }
    }
}