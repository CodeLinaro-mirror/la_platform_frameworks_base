/*
 * Copyright (C) 2019 The Android Open Source Project
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
 *
 * Changes from Qualcomm Innovation Center are provided under the following license:
 *
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear.
 */
package android.os;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.annotation.SystemApi;
import android.annotation.SystemApi.Client;
import android.os.BluetoothServiceManager;

/**
 * Provides a way to register and obtain the system service binder objects managed by the bluetooth
 * service.
 *
 * @hide
 */
@SystemApi(client = Client.MODULE_LIBRARIES)
public class BluetoothServiceManager {

    /** @hide */
    public static final String BLUETOOTH_MANAGER_SERVICE = "bluetooth_manager";

    /** @hide */
    public static final String BLUETOOTH_MANAGER_EXT_SERVICE = "bluetooth_manager_ext";

    /**
     * @hide
     */
    public BluetoothServiceManager() {
    }

    /**
     * A class that exposes the methods to register and obtain each system service.
     */
    public static final class ServiceRegisterer {
        private final String mServiceName;
        private final String mExtServiceName;

        /**
         * @hide
         */
        public ServiceRegisterer(String serviceName) {
            mServiceName = serviceName;
            mExtServiceName = BLUETOOTH_MANAGER_EXT_SERVICE;
        }

        /**
         * @hide
         */
        public ServiceRegisterer(String serviceName, String extServiceName) {
            mServiceName = serviceName;
            mExtServiceName = extServiceName;
        }

        /**
         * Register a system server binding object for a service.
         */
        public void register(@NonNull IBinder service) {
            ServiceManager.addService(mServiceName, service);
        }

        /**
         * Register a system server binding object for a service.
         */
        public void register(@NonNull IBinder service, @Nullable IBinder extService) {
            register(service);
            if (extService != null) {
                ServiceManager.addService(mExtServiceName, extService);
            }
        }

        @Nullable
        private IBinder get(String name) {
            return ServiceManager.getService(name);
        }

        /**
         * Get the system server binding object for a service.
         *
         * <p>This blocks until the service instance is ready,
         * or a timeout happens, in which case it returns null.
         */
        @Nullable
        public IBinder get() {
            return get(mServiceName);
        }

        /**
         * Get the system server binding object for a service.
         *
         * <p>This blocks until the service instance is ready,
         * or a timeout happens, in which case it returns null.
         */
        @Nullable
        public IBinder getExt() {
            return get(mExtServiceName);
        }

        /**
         * Get the system server binding object for a service.
         *
         * <p>This blocks until the service instance is ready,
         * or a timeout happens, in which case it throws {@link ServiceNotFoundException}.
         */
        @NonNull
        public IBinder getOrThrow() throws ServiceNotFoundException {
            try {
                return ServiceManager.getServiceOrThrow(mServiceName);
            } catch (ServiceManager.ServiceNotFoundException e) {
                throw new ServiceNotFoundException(mServiceName);
            }
        }

        /**
         * Get the system server binding object for a service.
         *
         * <p>This blocks until the service instance is ready,
         * or a timeout happens, in which case it throws {@link ServiceNotFoundException}.
         */
        @NonNull
        public IBinder getOrThrowExt() throws ServiceNotFoundException {
            try {
                return ServiceManager.getServiceOrThrow(mExtServiceName);
            } catch (ServiceManager.ServiceNotFoundException e) {
                throw new ServiceNotFoundException(mExtServiceName);
            }
        }

        @Nullable
        private IBinder tryGet(String name) {
            return ServiceManager.checkService(mServiceName);
        }

        /**
         * Get the system server binding object for a service. If the specified service is
         * not available, it returns null.
         */
        @Nullable
        public IBinder tryGet() {
            return tryGet(mServiceName);
        }

        /**
         * Get the system server binding object for a service. If the specified service is
         * not available, it returns null.
         */
        @Nullable
        public IBinder tryGetExt() {
            return tryGet(mExtServiceName);
        }
    }

    /**
     * See {@link ServiceRegisterer#getOrThrow}.
     *
     */
    public static class ServiceNotFoundException extends ServiceManager.ServiceNotFoundException {
        /**
         * Constructor.
         *
         * @param name the name of the binder service that cannot be found.
         *
         */
        public ServiceNotFoundException(@NonNull String name) {
            super(name);
        }
    }

    /**
     * Returns {@link ServiceRegisterer} for the "bluetooth" service.
     */
    @NonNull
    public ServiceRegisterer getBluetoothManagerServiceRegisterer() {
        return new ServiceRegisterer(BLUETOOTH_MANAGER_SERVICE);
    }
}
