package com.aylanetworks.aylasdk.setup.ble;

/*
 * Ayla SDK
 *
 * Copyright 2017 Ayla Networks, all rights reserved
 */

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaLog;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.aylanetworks.aylasdk.error.PreconditionError;
import com.aylanetworks.aylasdk.localdevice.ble.BLEError;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.aylanetworks.aylasdk.localdevice.ble.AylaBLEDevice.CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR;

/**
 * Base class of a structured Ayla GATT characteristic.
 */
public class AylaBaseGattCharacteristic {

    private static final String TAG = "AylaBaseGattCharacteristic";

    private WeakReference<BluetoothGattCharacteristic> _ownerCharacteristic;

    private Set<AylaBLEBaseRequest> _pendingReads  = new HashSet();
    private Set<AylaBLEBaseRequest> _pendingWrites = new HashSet();

    private Map<BluetoothGattDescriptor, Set<AylaBLEBaseRequest>> _pendingDescriptorWrites = new HashMap<>();
    private Map<BluetoothGattDescriptor, Set<AylaBLEBaseRequest>> _pendingDescriptorReads = new HashMap<>();

    private OnCharacteristicChangedListener _characteristicChangedListener;

    public interface OnCharacteristicChangedListener {
        void onCharacteristicChanged(AylaBaseGattCharacteristic characteristic);
    }

    public AylaBaseGattCharacteristic(@NonNull BluetoothGattCharacteristic owner) {
        _ownerCharacteristic = new WeakReference<>(owner);
    }

    public void setOnCharacteristicChangedListener(OnCharacteristicChangedListener listener) {
        _characteristicChangedListener = listener;
    }

    public OnCharacteristicChangedListener getCharacteristicChangedListener() {
        return _characteristicChangedListener;
    }

    /**
     * Get the owner characteristic hosted by this characteristic.
     * @return null if the owner doesn't exist.
     */
    public final BluetoothGattCharacteristic getOwner() {
        return _ownerCharacteristic.get();
    }

    public final UUID getUUID() {
        return getOwner().getUuid();
    }

    public String getName() {
        return getUUID().toString();
    }

    /**
     * Updates the locally stored value of this characteristic.
     */
    public boolean setValue(byte[] value) {
        return getOwner().setValue(value);
    }

    /**
     * Return the stored value of this characteristic.
     */
    public byte[] getValue() {
        return getOwner().getValue();
    }

    /**
     * Updates the locally stored value of this characteristic.
     */
    public boolean setIntValue(int value) {
        return getOwner().setValue(value, BluetoothGattCharacteristic.FORMAT_SINT32, 0);
    }

    /**
     * Return the stored value of this characteristic.
     */
    public int getIntValue() {
        return getOwner().getIntValue(BluetoothGattCharacteristic.FORMAT_SINT32, 0);
    }

    /**
     * Updates the locally stored value of this characteristic.
     */
    public boolean setStringValue(String value) {
        return getOwner().setValue(value);
    }

    /**
     * Return the stored value of this characteristic.
     */
    public String getStringValue() {
        return getOwner().getStringValue(0);
    }

    /**
     * Called after data for a characteristic has been read or has changed, in
     * {@link #onCharacteristicRead(BluetoothGatt, BluetoothGattCharacteristic, int)} and
     * in {@link #onCharacteristicChanged(BluetoothGatt, BluetoothGattCharacteristic)} respectively.
     *
     * @return true to notify listeners that data has been read,  or false to prevent notification.
     *
     * Subclasses should override this method to keep the structured characteristic
     * members, if any, updated. For characteristics that don't contain structured
     * members, no overriding is needed.
     */
    protected boolean shouldHandleRead() {
        return true;
    }

    /**
     * Called before data for a characteristic is about to be written.
     *
     * For subclasses that needs to assemble the structured characteristic members, if any,
     * and update the result in the owner characteristic's local value via a call to
     * {@link #setValue(byte[])}, should override this method and return true to allow the
     * writing to remote GATT server happening.
     *
     * @return true to allow performing the write, or false to skip writing to the characteristic.
     */
    protected boolean shouldHandleWrite() {
        return true;
    }

    /**
     * readCharacteristic
     */
    public AylaAPIRequest readCharacteristic(@NonNull BluetoothGatt gatt,
                                             @NonNull Response.Listener<AylaBaseGattCharacteristic> successListener,
                                             @NonNull ErrorListener errorListener) {
        if (gatt == null) {
            AylaLog.e(TAG, "gatt is null");
            errorListener.onErrorResponse(new PreconditionError("gatt is null"));
            return null;
        }

        AylaLog.i(TAG, "reading characteristic " + getName());

        if (gatt.readCharacteristic(getOwner())) {
            AylaBLEBaseRequest request = new AylaBLEBaseRequest(successListener, errorListener) {
                @Override
                public void cancel() {
                    super.cancel();
                    _pendingReads.remove(this);
                }
            };
            _pendingReads.add(request);
            return request;
        } else {
            errorListener.onErrorResponse(new PreconditionError(
                    "failed to initialize characteristic read"));
            return null;
        }
    }

    /**
     * readCharacteristic
     */
    public AylaAPIRequest writeCharacteristic(@NonNull BluetoothGatt gatt,
                                              @NonNull Response.Listener<AylaBaseGattCharacteristic> successListener,
                                              @NonNull ErrorListener errorListener) {
        if (gatt == null) {
            AylaLog.e(TAG, "gatt is null");
            errorListener.onErrorResponse(new PreconditionError("gatt is null"));
            return null;
        }

        AylaLog.i(TAG, "writing characteristic " + getName());

        for (AylaBLEBaseRequest request: _pendingWrites) {
            if (request.getSuccessListener() == successListener) {
                AylaLog.d(TAG, "write request already in progress");
                return null;
            }
        }

        if (shouldHandleWrite() && gatt.writeCharacteristic(getOwner())) {
            AylaBLEBaseRequest request = new AylaBLEBaseRequest(successListener, errorListener) {
                @Override
                public void cancel() {
                    super.cancel();
                    _pendingWrites.remove(this);
                }
            };
            _pendingWrites.add(request);
            return request;
        } else {
            errorListener.onErrorResponse(new PreconditionError("failed to write characteristic " + getUUID()));
            return null;
        }
    }

    /**
     * Enable or disable notification on a characteristic.
     * @param enable true to enable notification, false to disable notification.
     * @param successListener the listener in response to the notification set result.
     * @param errorListener errorListener
     */
    public AylaAPIRequest enableCharacteristicNotification(@NonNull BluetoothGatt gatt,
                                                           boolean enable,
                                                           @NonNull Response.Listener<AylaBaseGattCharacteristic> successListener,
                                                           @NonNull ErrorListener errorListener) {
        if (gatt == null) {
            AylaLog.e(TAG, "gatt is null");
            errorListener.onErrorResponse(new PreconditionError("gatt is null"));
            return null;
        }

        if (!gatt.setCharacteristicNotification(getOwner(), enable)) {
            errorListener.onErrorResponse(new PreconditionError("failed to set characteristic notification"));
            return null;
        }

        if (enable) {
            BluetoothGattDescriptor descriptor = getOwner().getDescriptor
                    (CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR);
            if (descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                return writeDescriptor(gatt, descriptor, successListener, errorListener);
            } else {
                errorListener.onErrorResponse(new PreconditionError("failed to write notification descriptor"));
            }
        } else {
            successListener.onResponse(this);
        }

        return null;
    }

    /**
     * readDescriptor
     */
    public AylaAPIRequest readDescriptor(@NonNull BluetoothGatt gatt,
                                         @NonNull BluetoothGattDescriptor descriptor,
                                         @NonNull Response.Listener<AylaBaseGattCharacteristic> successListener,
                                         @NonNull ErrorListener errorListener) {
        if (gatt == null) {
            AylaLog.e(TAG, "gatt is null");
            errorListener.onErrorResponse(new PreconditionError("gatt is null"));
            return null;
        }

        if (!gatt.readDescriptor(descriptor)) {
            errorListener.onErrorResponse(new PreconditionError("failed to read descriptor"));
            return null;
        }

        AylaBLEBaseRequest request = new AylaBLEBaseRequest(successListener, errorListener) {
            @Override
            public void cancel() {
                super.cancel();
                if (_pendingDescriptorReads.get(descriptor) != null) {
                    _pendingDescriptorReads.get(descriptor).remove(this);
                }
            }
        };

        if (_pendingDescriptorReads.get(descriptor) == null) {
            _pendingDescriptorReads.put(descriptor, new HashSet<>());
        }
        _pendingDescriptorReads.get(descriptor).add(request);

        return request;
    }

    /**
     * writeDescriptor
     */
    public AylaAPIRequest writeDescriptor(@NonNull BluetoothGatt gatt,
                                          @NonNull BluetoothGattDescriptor descriptor,
                                          @NonNull Response.Listener<AylaBaseGattCharacteristic> successListener,
                                          @NonNull ErrorListener errorListener) {
        if (gatt == null) {
            AylaLog.e(TAG, "gatt is null");
            errorListener.onErrorResponse(new PreconditionError("gatt is null"));
            return null;
        }

        if (!gatt.writeDescriptor(descriptor)) {
            errorListener.onErrorResponse(new PreconditionError(
                    "failed to write descriptor on characteristic "
                            + descriptor.getCharacteristic().getUuid()));
            return null;
        }

        AylaBLEBaseRequest request = new AylaBLEBaseRequest(successListener, errorListener) {
            @Override
            public void cancel() {
                super.cancel();
                if (_pendingDescriptorWrites.get(descriptor) != null) {
                    _pendingDescriptorWrites.get(descriptor).remove(this);
                }
            }
        };

        if (_pendingDescriptorWrites.get(descriptor) == null) {
            _pendingDescriptorWrites.put(descriptor, new HashSet<>());
        }
        _pendingDescriptorWrites.get(descriptor).add(request);

        return request;
    }

    /**
     * Callback method to receive the result of a characteristic read operation.
     *
     * @param gatt GATT client invoked {@link BluetoothGatt#readCharacteristic}
     * @param characteristic Characteristic that was read from the associated
     *                       remote device.
     * @param status {@link BluetoothGatt#GATT_SUCCESS} if the read operation
     *               was completed successfully.
     * @return true if the read was successful, otherwise return false.
     */
    public boolean onCharacteristicRead(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic,
                                        int status) {
        if (!characteristic.getUuid().equals(getOwner().getUuid())) {
            AylaLog.e(TAG, "mismatched characteristic UUID");
            return false;
        }

        if (status == BluetoothGatt.GATT_SUCCESS && shouldHandleRead()) {
            notifyCharacteristicOperationResult(true, null);
            return true;
        } else {
            BLEError error = new BLEError(status, "characteristic read error");
            notifyCharacteristicOperationResult(true, error);
            return false;
        }
    }

    /**
     * Callback indicating the result of a characteristic write operation.
     *
     * <p>If this callback is invoked while a reliable write transaction is
     * in progress, the value of the characteristic represents the value
     * reported by the remote device. An application should compare this
     * value to the desired value to be written. If the values don't match,
     * the application must abort the reliable write transaction.
     *
     * @param gatt GATT client invoked {@link BluetoothGatt#writeCharacteristic}
     * @param characteristic Characteristic that was written to the associated
     *                       remote device.
     * @param status The result of the write operation
     *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
     * @return true if the write was successful, otherwise return false.
     */
    public boolean onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
        if (!characteristic.getUuid().equals(getOwner().getUuid())) {
            AylaLog.e(TAG, "mismatched characteristic UUID");
            return false;
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            notifyCharacteristicOperationResult(false, null);
            return true;
        } else {
            BLEError error = new BLEError(status, "characteristic write error");
            notifyCharacteristicOperationResult(false, error);
            return false;
        }
    }

    /**
     * Callback reporting the result of a descriptor read operation.
     *
     * @param gatt GATT client invoked {@link BluetoothGatt#readDescriptor}
     * @param descriptor Descriptor that was read from the associated
     *                   remote device.
     * @param status {@link BluetoothGatt#GATT_SUCCESS} if the read operation
     *               was completed successfully
     */
    public void onDescriptorRead(BluetoothGatt gatt,
                                 BluetoothGattDescriptor descriptor,
                                 int status) {
        AylaLog.i(TAG, "onDescriptorRead"
                + ", uuid:" + descriptor.getUuid()
                + ", char:" + getName()
                + ", status:" + status);

        if (!descriptor.getCharacteristic().getUuid().equals(getOwner().getUuid())) {
            AylaLog.e(TAG, "mismatched characteristic UUID");
            return;
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            notifyDescriptorOperationResult(descriptor, true, null);
        } else {
            BLEError error = new BLEError(status, "descriptor read error");
            notifyDescriptorOperationResult(descriptor, true, error);
        }
    }

    /**
     * Callback indicating the result of a descriptor write operation.
     * Subclasses should override this method to do the specific operation.
     *
     * @param gatt GATT client invoked {@link BluetoothGatt#writeDescriptor}
     * @param descriptor Descriptor that was writte to the associated
     *                   remote device.
     * @param status The result of the write operation
     *               {@link BluetoothGatt#GATT_SUCCESS} if the operation succeeds.
     */
    public void onDescriptorWrite(BluetoothGatt gatt,
                                  BluetoothGattDescriptor descriptor,
                                  int status) {
        AylaLog.i(TAG, "onDescriptorWrite"
                + ", uuid:" + descriptor.getUuid()
                + ", char:" + getName()
                + ", status:" + status);

        if (!descriptor.getCharacteristic().getUuid().equals(getOwner().getUuid())) {
            AylaLog.e(TAG, "mismatched characteristic UUID");
            return;
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            notifyDescriptorOperationResult(descriptor, false, null);
        } else {
            BLEError error = new BLEError(status, "descriptor write error");
            notifyDescriptorOperationResult(descriptor, false, error);
        }
    }

    /**
     * Callback triggered as a result of a remote characteristic notification.
     *
     * @param gatt GATT client the characteristic is associated with
     * @param characteristic Characteristic that has been updated as a result
     *                       of a remote notification event.
     */
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        if (!characteristic.getUuid().equals(getOwner().getUuid())) {
            AylaLog.e(TAG, "mismatched characteristic UUID");
            return;
        }

        if (shouldHandleRead() && getCharacteristicChangedListener() != null) {
            getCharacteristicChangedListener().onCharacteristicChanged(this);
        }
    }

    private void notifyCharacteristicOperationResult(boolean readOperation, BLEError error) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Set<AylaBLEBaseRequest> pendingRequests = readOperation ? _pendingReads : _pendingWrites;
                for (AylaBLEBaseRequest request : pendingRequests) {
                    if (error != null) {
                        request.reportBLEError(error);
                    } else {
                        request.getSuccessListener().onResponse(AylaBaseGattCharacteristic.this);
                    }
                }
                pendingRequests.clear();
            }
        });
    }

    private void notifyDescriptorOperationResult(BluetoothGattDescriptor descriptor,
                                                 boolean readOperation,
                                                 BLEError error) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Set<AylaBLEBaseRequest> pendingRequests = readOperation ? _pendingDescriptorReads.get(descriptor)
                        : _pendingDescriptorWrites.get(descriptor);
                for (AylaBLEBaseRequest request : pendingRequests) {
                    if (error != null) {
                        request.reportBLEError(error);
                    } else {
                        request.getSuccessListener().onResponse(AylaBaseGattCharacteristic.this);
                    }
                }
                pendingRequests.clear();
            }
        });
    }
}
