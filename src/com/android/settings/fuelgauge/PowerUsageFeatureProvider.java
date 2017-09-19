/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.settings.fuelgauge;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.SparseIntArray;

import com.android.internal.os.BatterySipper;

/**
 * Feature Provider used in power usage
 */
public interface PowerUsageFeatureProvider {
    /**
     * Check whether location setting is enabled
     */
    boolean isLocationSettingEnabled(String[] packages);

    /**
     * Check whether additional battery info feature is enabled.
     */
    boolean isAdditionalBatteryInfoEnabled();

    /**
     * Gets an {@link Intent} to show additional battery info.
     */
    Intent getAdditionalBatteryInfoIntent();

    /**
     * Check whether advanced ui is enabled
     */
    boolean isAdvancedUiEnabled();

    /**
     * Check whether it is type service
     */
    boolean isTypeService(BatterySipper sipper);

    /**
     * Check whether it is type system
     */
    boolean isTypeSystem(BatterySipper sipper);

    /**
     * Check whether the toggle for power accounting is enabled
     */
    boolean isPowerAccountingToggleEnabled();

    /**
     * Returns an improved prediction for battery time remaining.
     */
    long getEnhancedBatteryPrediction(Context context);

    /**
     * Returns an improved projection curve for future battery level.
     * @param zeroTime timestamps (array keys) are shifted by this amount
     */
    SparseIntArray getEnhancedBatteryPredictionCurve(Context context, long zeroTime);

    /**
     * Checks whether the toggle for enhanced battery predictions is enabled.
     */
    boolean isEnhancedBatteryPredictionEnabled(Context context);

    /**
     * Returns the Uri used to query for an enhanced battery prediction from a cursor loader.
     */
    Uri getEnhancedBatteryPredictionUri();

    /**
     * Returns the the estimate in the cursor as a long or -1 if the cursor is null
     */
    long getTimeRemainingEstimate(Cursor cursor);

    /**
     * Checks whether debugging should be enabled for battery estimates.
     * @return
     */
    boolean isEstimateDebugEnabled();

    /**
     * Converts the provided string containing the remaining time into a debug string for enhanced
     * estimates.
     * @param timeRemaining
     * @return A string containing the estimate and a label indicating it is an enhanced estimate
     */
    String getEnhancedEstimateDebugString(String timeRemaining);

    /**
     * Converts the provided string containing the remaining time into a debug string.
     * @param timeRemaining
     * @return A string containing the estimate and a label indicating it is a normal estimate
     */
    String getOldEstimateDebugString(String timeRemaining);

    /**
     * Returns the string to show in the advanced usage battery page when enhanced estimates are
     * enabled. This string notifies users that the estimate is using enhanced prediction.
     */
    String getAdvancedUsageScreenInfoString();
}