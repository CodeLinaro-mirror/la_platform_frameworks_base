/**
 * Copyright (c) 2021 The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *
 *    * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.net.wifi;

import android.annotation.NonNull;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class for thermal info structure
 * @hide
 */
public final class ThermalData implements Parcelable {
    private int temperature;
    private int thermal_level;

    public static final int THERMAL_INFO_LEVEL_FULL_PERF = 0;
    public static final int THERMAL_INFO_LEVEL_REDUCED_PERF = 1;
    public static final int THERMAL_INFO_LEVEL_TX_OFF = 2;
    public static final int THERMAL_INFO_LEVEL_SHUT_DOWN = 3;
    public static final int THERMAL_INFO_LEVEL_UNKNOWN = -1;

    public int getTemperature() {
        return temperature;
    }
    public void setTemperature(int temp) {
        temperature = temp;
    }
    public int getThermalLevel() {
        return thermal_level;
    }
    public void setThermalLevel(int thermal) {
        thermal_level = thermal;
    }

    public static final @NonNull Parcelable.Creator<ThermalData> CREATOR
         = new Parcelable.Creator<ThermalData>() {
        public ThermalData createFromParcel(@NonNull Parcel in) {
            return new ThermalData(in);
        }

        public ThermalData[] newArray(int size) {
            return new ThermalData[size];
        }
    };

    public ThermalData() {
    }

    private ThermalData(Parcel in) {
        readFromParcel(in);
    }


    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeInt(temperature);
        out.writeInt(thermal_level);
    }

    public void readFromParcel(@NonNull Parcel in) {
        temperature = in.readInt();
        thermal_level = in.readInt();
    }

    public int describeContents() {
        return 0;
    }
}
