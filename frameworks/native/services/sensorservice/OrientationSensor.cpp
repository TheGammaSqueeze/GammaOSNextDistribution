/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <stdint.h>
#include <math.h>
#include <sys/types.h>
#include <cutils/properties.h>    // Needed for property_get

#include <utils/Errors.h>
#include <hardware/sensors.h>

#include "OrientationSensor.h"
#include "SensorDevice.h"
#include "SensorFusion.h"

namespace android {
// ---------------------------------------------------------------------------

// Helper function to construct a 3x3 rotation matrix corresponding to a rotation
// about the Z–axis by sensorRot degrees.
static mat33_t getRotationMatrixForSensorOrientation(int sensorRot) {
    mat33_t rot;
    float rad = sensorRot * M_PI / 180.0f;
    rot[0][0] = cosf(rad);  rot[0][1] = -sinf(rad);  rot[0][2] = 0;
    rot[1][0] = sinf(rad);  rot[1][1] = cosf(rad);   rot[1][2] = 0;
    rot[2][0] = 0;          rot[2][1] = 0;           rot[2][2] = 1;
    return rot;
}

OrientationSensor::OrientationSensor() {
    const sensor_t sensor = {
        .name       = "Orientation Sensor",
        .vendor     = "AOSP",
        .version    = 1,
        .handle     = '_ypr',
        .type       = SENSOR_TYPE_ORIENTATION,
        .maxRange   = 360.0f,
        .resolution = 1.0f/256.0f, // FIXME: real value here
        .power      = mSensorFusion.getPowerUsage(),
        .minDelay   = mSensorFusion.getMinDelay(),
    };
    mSensor = Sensor(&sensor);
}

bool OrientationSensor::process(sensors_event_t* outEvent,
        const sensors_event_t& event)
{
    if (event.type == SENSOR_TYPE_ACCELEROMETER) {
        if (mSensorFusion.hasEstimate()) {
            // Read the sensor–mount orientation property.
            char prop[PROPERTY_VALUE_MAX];
            property_get("ro.sensors.accelerometer_orientation", prop, "0");
            int sensorRot = atoi(prop);

            const float rad2deg = 180 / M_PI;
            // Get the rotation matrix from the sensor fusion.
            const mat33_t fusionR(mSensorFusion.getRotationMatrix());
            // Compute the fixed rotation from the property.
            const mat33_t sensorRotMatrix = getRotationMatrixForSensorOrientation(sensorRot);
            // Combine them so that the sensor fusion output is rotated by sensorRot degrees.
            mat33_t R = sensorRotMatrix * fusionR; // assumes operator* is defined for mat33_t

            vec3_t g;
            g[0] = atan2f(-R[1][0], R[0][0])    * rad2deg;
            g[1] = atan2f(-R[2][1], R[2][2])    * rad2deg;
            g[2] = asinf ( R[2][0])             * rad2deg;
            if (g[0] < 0)
                g[0] += 360;

            *outEvent = event;
            outEvent->orientation.azimuth = g.x;
            outEvent->orientation.pitch   = g.y;
            outEvent->orientation.roll    = g.z;
            outEvent->orientation.status  = SENSOR_STATUS_ACCURACY_HIGH;
            outEvent->sensor = '_ypr';
            outEvent->type = SENSOR_TYPE_ORIENTATION;
            return true;
        }
    }
    return false;
}

status_t OrientationSensor::activate(void* ident, bool enabled) {
    return mSensorFusion.activate(FUSION_9AXIS, ident, enabled);
}

status_t OrientationSensor::setDelay(void* ident, int /*handle*/, int64_t ns) {
    return mSensorFusion.setDelay(FUSION_9AXIS, ident, ns);
}

// ---------------------------------------------------------------------------
}; // namespace android
