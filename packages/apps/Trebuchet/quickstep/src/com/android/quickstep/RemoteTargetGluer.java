/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.quickstep;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.RemoteAnimationTarget;

import androidx.annotation.Nullable;

import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.views.DesktopTaskView;

import java.util.ArrayList;

/**
 * Glues together the necessary components to animate a remote target using a
 * {@link TaskViewSimulator}
 */
public class RemoteTargetGluer {
    private static final String TAG = "RemoteTargetGluer";
    private int mDisplayWidth, mDisplayHeight;
    private RemoteTargetHandle[] mRemoteTargetHandles;
    private SplitBounds mSplitBounds;

    /**
     * Use this constructor if remote targets are split-screen independent
     */
    public RemoteTargetGluer(Context context, BaseActivityInterface sizingStrategy,
            RemoteAnimationTargets targets, boolean forDesktop) {
        init(context, sizingStrategy, targets.apps.length, forDesktop);
    }

    /**
     * Use this constructor if you want the number of handles created to match the number of active
     * running tasks
     */
    public RemoteTargetGluer(Context context, BaseActivityInterface sizingStrategy) {
        if (DesktopTaskView.DESKTOP_MODE_SUPPORTED) {
            // TODO: binder call, only for prototyping. Creating the gluer should be postponed so
            //  we can create it when we have the remote animation targets ready.
            int desktopTasks = SystemUiProxy.INSTANCE.get(context).getVisibleDesktopTaskCount();
            if (desktopTasks > 0) {
                init(context, sizingStrategy, desktopTasks, true /* forDesktop */);
                return;
            }
        }

        int[] splitIds = TopTaskTracker.INSTANCE.get(context).getRunningSplitTaskIds();
        init(context, sizingStrategy, splitIds.length == 2 ? 2 : 1, false /* forDesktop */);
    }

    private void init(Context context, BaseActivityInterface sizingStrategy, int numHandles,
            boolean forDesktop) {
        // grab display size for fallback bounds
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mDisplayWidth  = dm.widthPixels;
        mDisplayHeight = dm.heightPixels;
        mRemoteTargetHandles = createHandles(context, sizingStrategy, numHandles, forDesktop);
    }

    private RemoteTargetHandle[] createHandles(Context context,
            BaseActivityInterface sizingStrategy, int numHandles, boolean forDesktop) {
        RemoteTargetHandle[] handles = new RemoteTargetHandle[numHandles];
        for (int i = 0; i < numHandles; i++) {
            TaskViewSimulator tvs = new TaskViewSimulator(context, sizingStrategy);
            tvs.setIsDesktopTask(forDesktop);
            TransformParams transformParams = new TransformParams();
            handles[i] = new RemoteTargetHandle(tvs, transformParams);
        }
        return handles;
    }

    /**
     * Pairs together {@link TaskViewSimulator}s and {@link TransformParams} into a
     * {@link RemoteTargetHandle}
     * Assigns only the apps associated with {@param targets} into their own TaskViewSimulators.
     * Length of targets.apps should match that of {@link #mRemoteTargetHandles}.
     *
     * If split screen may be active when this is called, you might want to use
     * {@link #assignTargetsForSplitScreen(RemoteAnimationTargets)}
     */
    public RemoteTargetHandle[] assignTargets(RemoteAnimationTargets targets) {
        for (int i = 0; i < mRemoteTargetHandles.length; i++) {
            RemoteAnimationTarget primaryTaskTarget = targets.apps[i];
            mRemoteTargetHandles[i].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(targets, null));
            mRemoteTargetHandles[i].mTaskViewSimulator.setPreview(primaryTaskTarget, null);
        }
        return mRemoteTargetHandles;
    }

    /**
     * Similar to {@link #assignTargets(RemoteAnimationTargets)}, except this assigns the
     * apps in {@code targets.apps} to the {@link #mRemoteTargetHandles} with index 0 will being
     * the left/top task, index 1 right/bottom.
     */
    public RemoteTargetHandle[] assignTargetsForSplitScreen(RemoteAnimationTargets targets) {
        if (mRemoteTargetHandles.length == 1) {
            // If we're not in split screen, the splitIds count doesn't really matter since we
            // should always hit this case.
            mRemoteTargetHandles[0].mTransformParams.setTargetSet(targets);
            if (targets.apps.length > 0) {
                // Unclear why/when target.apps length == 0, but it sure does happen :(
                mRemoteTargetHandles[0].mTaskViewSimulator.setPreview(targets.apps[0], null);
            }
        } else {
            // If we don’t have at least two app targets, fall back to the non-split logic
            if (targets == null || targets.apps == null || targets.apps.length < 2) {
                Log.w(TAG, "assignTargetsForSplitScreen: less than 2 targets, falling back");
                return assignTargets(targets);
            }

            RemoteAnimationTarget topLeftTarget = targets.apps[0];

            // Fetch the adjacent target for split screen.
            RemoteAnimationTarget bottomRightTarget = null;
            for (int i = 1; i < targets.apps.length; i++) {
                final RemoteAnimationTarget target = targets.apps[i];
                Rect topLeftBounds = getStartBounds(topLeftTarget);
                Rect bounds = getStartBounds(target);
                if (topLeftBounds.left > bounds.right || topLeftBounds.top > bounds.bottom) {
                    bottomRightTarget = topLeftTarget;
                    topLeftTarget = target;
                    break;
                } else if (topLeftBounds.right < bounds.left || topLeftBounds.bottom < bounds.top) {
                    bottomRightTarget = target;
                    break;
                }
            }

            // If the loop didn’t find a valid second target, fall back
            if (bottomRightTarget == null) {
                Log.w(TAG, "assignTargetsForSplitScreen: couldn't find split pair, falling back");
                return assignTargets(targets);
            }

            // remoteTargetHandle[0] denotes topLeft task, so we pass in the bottomRight to exclude,
            // vice versa
            mSplitBounds = new SplitBounds(
                    getStartBounds(topLeftTarget),
                    getStartBounds(bottomRightTarget),
                    topLeftTarget.taskId,
                    bottomRightTarget.taskId);
            mRemoteTargetHandles[0].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(targets, bottomRightTarget));
            mRemoteTargetHandles[0].mTaskViewSimulator.setPreview(topLeftTarget,
                    mSplitBounds);

            mRemoteTargetHandles[1].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTarget(targets, topLeftTarget));
            mRemoteTargetHandles[1].mTaskViewSimulator.setPreview(bottomRightTarget,
                    mSplitBounds);
        }
        return mRemoteTargetHandles;
    }

    /**
     * Similar to {@link #assignTargets(RemoteAnimationTargets)}, except this creates distinct
     * transform params per app in {@code targets.apps} list.
     */
    public RemoteTargetHandle[] assignTargetsForDesktop(RemoteAnimationTargets targets) {
        for (int i = 0; i < mRemoteTargetHandles.length; i++) {
            RemoteAnimationTarget primaryTaskTarget = targets.apps[i];
            mRemoteTargetHandles[i].mTransformParams.setTargetSet(
                    createRemoteAnimationTargetsForTaskId(targets, primaryTaskTarget.taskId));
            mRemoteTargetHandles[i].mTaskViewSimulator.setPreview(primaryTaskTarget, null);
        }
        return mRemoteTargetHandles;
    }

    private Rect getStartBounds(@Nullable RemoteAnimationTarget target) {
        // no target → full-screen fallback
        if (target == null) {
            Log.w(TAG, "getStartBounds: target is null, using full-screen fallback");
            return new Rect(0, 0, mDisplayWidth, mDisplayHeight);
        }
        // if startBounds not populated, fall back to screenSpaceBounds
        Rect sb = target.startBounds;
        if (sb == null) {
            Log.w(TAG, "getStartBounds: startBounds null for task " + target.taskId
                    + ", using screenSpaceBounds");
            return target.screenSpaceBounds;
        }
        return sb;
    }

    /**
     * Ensures that we aren't excluding ancillary targets such as home/recents
     *
     * @param targetToExclude Will be excluded from the resulting return value.
     *                        Pass in {@code null} to not exclude anything
     * @return RemoteAnimationTargets where all the app targets from the passed in
     *         {@param targets} are included except {@param targetToExclude}
     */
    private RemoteAnimationTargets createRemoteAnimationTargetsForTarget(
            RemoteAnimationTargets targets,
            RemoteAnimationTarget targetToExclude) {
        ArrayList<RemoteAnimationTarget> targetsWithoutExcluded = new ArrayList<>();

        for (RemoteAnimationTarget targetCompat : targets.unfilteredApps) {
            if (targetCompat == targetToExclude) {
                continue;
            }
            if (targetToExclude != null
                    && targetToExclude.taskInfo != null
                    && targetCompat.taskInfo != null
                    && targetToExclude.taskInfo.parentTaskId == targetCompat.taskInfo.taskId) {
                // Also exclude corresponding parent task
                continue;
            }

            targetsWithoutExcluded.add(targetCompat);
        }
        final RemoteAnimationTarget[] filteredApps = targetsWithoutExcluded.toArray(
                new RemoteAnimationTarget[targetsWithoutExcluded.size()]);
        return new RemoteAnimationTargets(
                filteredApps, targets.wallpapers, targets.nonApps, targets.targetMode);
    }

    /**
     * Ensures that we only animate one specific app target. Includes ancillary targets such as
     * home/recents
     *
     * @param targets remote animation targets to filter
     * @param taskId  id for a task that we want this remote animation to apply to
     * @return {@link RemoteAnimationTargets} where app target only includes the app that has the
     * {@code taskId} that was passed in
     */
    private RemoteAnimationTargets createRemoteAnimationTargetsForTaskId(
            RemoteAnimationTargets targets, int taskId) {
        RemoteAnimationTarget[] targetApp = null;
        for (RemoteAnimationTarget targetCompat : targets.unfilteredApps) {
            if (targetCompat.taskId == taskId) {
                targetApp = new RemoteAnimationTarget[]{targetCompat};
                break;
            }
        }

        if (targetApp == null) {
            targetApp = new RemoteAnimationTarget[0];
        }

        return new RemoteAnimationTargets(targetApp, targets.wallpapers, targets.nonApps,
                targets.targetMode);
    }

    public RemoteTargetHandle[] getRemoteTargetHandles() {
        return mRemoteTargetHandles;
    }

    public SplitBounds getSplitBounds() {
        return mSplitBounds;
    }

    /**
     * Container to keep together all the associated objects whose properties need to be updated to
     * animate a single remote app target
     */
    public static class RemoteTargetHandle {
        private final TaskViewSimulator mTaskViewSimulator;
        private final TransformParams mTransformParams;
        @Nullable
        private AnimatorControllerWithResistance mPlaybackController;

        public RemoteTargetHandle(TaskViewSimulator taskViewSimulator,
                TransformParams transformParams) {
            mTransformParams = transformParams;
            mTaskViewSimulator = taskViewSimulator;
        }

        public TaskViewSimulator getTaskViewSimulator() {
            return mTaskViewSimulator;
        }

        public TransformParams getTransformParams() {
            return mTransformParams;
        }

        @Nullable
        public AnimatorControllerWithResistance getPlaybackController() {
            return mPlaybackController;
        }

        public void setPlaybackController(
                @Nullable AnimatorControllerWithResistance playbackController) {
            mPlaybackController = playbackController;
        }
    }
}
