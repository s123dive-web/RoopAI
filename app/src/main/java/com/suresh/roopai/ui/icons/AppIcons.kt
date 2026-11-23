/*
 * Copyright 2025 The Android Open Source Project
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

// The handful of Material icons this app uses, copied from androidx.compose.material:material-icons-extended
// (Apache 2.0) so the 40 MB icon library does not have to ship in the APK.
@file:Suppress("UnusedReceiverParameter", "ObjectPropertyName")

package com.suresh.roopai.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val Icons.AutoMirrored.Filled.Redo: ImageVector
    get() {
        if (_redo != null) {
            return _redo!!
        }
        _redo = materialIcon(name = "AutoMirrored.Filled.Redo", autoMirror = true) {
            materialPath {
                moveTo(18.4f, 10.6f)
                curveTo(16.55f, 8.99f, 14.15f, 8.0f, 11.5f, 8.0f)
                curveToRelative(-4.65f, 0.0f, -8.58f, 3.03f, -9.96f, 7.22f)
                lineTo(3.9f, 16.0f)
                curveToRelative(1.05f, -3.19f, 4.05f, -5.5f, 7.6f, -5.5f)
                curveToRelative(1.95f, 0.0f, 3.73f, 0.72f, 5.12f, 1.88f)
                lineTo(13.0f, 16.0f)
                horizontalLineToRelative(9.0f)
                verticalLineTo(7.0f)
                lineToRelative(-3.6f, 3.6f)
                close()
            }
        }
        return _redo!!
    }

private var _redo: ImageVector? = null

val Icons.AutoMirrored.Filled.RotateLeft: ImageVector
    get() {
        if (_rotateLeft != null) {
            return _rotateLeft!!
        }
        _rotateLeft = materialIcon(name = "AutoMirrored.Filled.RotateLeft", autoMirror = true) {
            materialPath {
                moveTo(7.11f, 8.53f)
                lineTo(5.7f, 7.11f)
                curveTo(4.8f, 8.27f, 4.24f, 9.61f, 4.07f, 11.0f)
                horizontalLineToRelative(2.02f)
                curveToRelative(0.14f, -0.87f, 0.49f, -1.72f, 1.02f, -2.47f)
                close()
                moveTo(6.09f, 13.0f)
                lineTo(4.07f, 13.0f)
                curveToRelative(0.17f, 1.39f, 0.72f, 2.73f, 1.62f, 3.89f)
                lineToRelative(1.41f, -1.42f)
                curveToRelative(-0.52f, -0.75f, -0.87f, -1.59f, -1.01f, -2.47f)
                close()
                moveTo(7.1f, 18.32f)
                curveToRelative(1.16f, 0.9f, 2.51f, 1.44f, 3.9f, 1.61f)
                lineTo(11.0f, 17.9f)
                curveToRelative(-0.87f, -0.15f, -1.71f, -0.49f, -2.46f, -1.03f)
                lineTo(7.1f, 18.32f)
                close()
                moveTo(13.0f, 4.07f)
                lineTo(13.0f, 1.0f)
                lineTo(8.45f, 5.55f)
                lineTo(13.0f, 10.0f)
                lineTo(13.0f, 6.09f)
                curveToRelative(2.84f, 0.48f, 5.0f, 2.94f, 5.0f, 5.91f)
                reflectiveCurveToRelative(-2.16f, 5.43f, -5.0f, 5.91f)
                verticalLineToRelative(2.02f)
                curveToRelative(3.95f, -0.49f, 7.0f, -3.85f, 7.0f, -7.93f)
                reflectiveCurveToRelative(-3.05f, -7.44f, -7.0f, -7.93f)
                close()
            }
        }
        return _rotateLeft!!
    }

private var _rotateLeft: ImageVector? = null

val Icons.AutoMirrored.Filled.RotateRight: ImageVector
    get() {
        if (_rotateRight != null) {
            return _rotateRight!!
        }
        _rotateRight = materialIcon(name = "AutoMirrored.Filled.RotateRight", autoMirror = true) {
            materialPath {
                moveTo(15.55f, 5.55f)
                lineTo(11.0f, 1.0f)
                verticalLineToRelative(3.07f)
                curveTo(7.06f, 4.56f, 4.0f, 7.92f, 4.0f, 12.0f)
                reflectiveCurveToRelative(3.05f, 7.44f, 7.0f, 7.93f)
                verticalLineToRelative(-2.02f)
                curveToRelative(-2.84f, -0.48f, -5.0f, -2.94f, -5.0f, -5.91f)
                reflectiveCurveToRelative(2.16f, -5.43f, 5.0f, -5.91f)
                lineTo(11.0f, 10.0f)
                lineToRelative(4.55f, -4.45f)
                close()
                moveTo(19.93f, 11.0f)
                curveToRelative(-0.17f, -1.39f, -0.72f, -2.73f, -1.62f, -3.89f)
                lineToRelative(-1.42f, 1.42f)
                curveToRelative(0.54f, 0.75f, 0.88f, 1.6f, 1.02f, 2.47f)
                horizontalLineToRelative(2.02f)
                close()
                moveTo(13.0f, 17.9f)
                verticalLineToRelative(2.02f)
                curveToRelative(1.39f, -0.17f, 2.74f, -0.71f, 3.9f, -1.61f)
                lineToRelative(-1.44f, -1.44f)
                curveToRelative(-0.75f, 0.54f, -1.59f, 0.89f, -2.46f, 1.03f)
                close()
                moveTo(16.89f, 15.48f)
                lineToRelative(1.42f, 1.41f)
                curveToRelative(0.9f, -1.16f, 1.45f, -2.5f, 1.62f, -3.89f)
                horizontalLineToRelative(-2.02f)
                curveToRelative(-0.14f, 0.87f, -0.48f, 1.72f, -1.02f, 2.48f)
                close()
            }
        }
        return _rotateRight!!
    }

private var _rotateRight: ImageVector? = null

val Icons.AutoMirrored.Filled.Undo: ImageVector
    get() {
        if (_undo != null) {
            return _undo!!
        }
        _undo = materialIcon(name = "AutoMirrored.Filled.Undo", autoMirror = true) {
            materialPath {
                moveTo(12.5f, 8.0f)
                curveToRelative(-2.65f, 0.0f, -5.05f, 0.99f, -6.9f, 2.6f)
                lineTo(2.0f, 7.0f)
                verticalLineToRelative(9.0f)
                horizontalLineToRelative(9.0f)
                lineToRelative(-3.62f, -3.62f)
                curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
                curveToRelative(3.54f, 0.0f, 6.55f, 2.31f, 7.6f, 5.5f)
                lineToRelative(2.37f, -0.78f)
                curveTo(21.08f, 11.03f, 17.15f, 8.0f, 12.5f, 8.0f)
                close()
            }
        }
        return _undo!!
    }

private var _undo: ImageVector? = null

val Icons.Filled.AutoAwesome: ImageVector
    get() {
        if (_autoAwesome != null) {
            return _autoAwesome!!
        }
        _autoAwesome = materialIcon(name = "Filled.AutoAwesome") {
            materialPath {
                moveTo(19.0f, 9.0f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23.0f, 5.0f)
                lineToRelative(-2.75f, -1.25f)
                lineTo(19.0f, 1.0f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15.0f, 5.0f)
                lineToRelative(2.75f, 1.25f)
                lineTo(19.0f, 9.0f)
                close()
                moveTo(11.5f, 9.5f)
                lineTo(9.0f, 4.0f)
                lineTo(6.5f, 9.5f)
                lineTo(1.0f, 12.0f)
                lineToRelative(5.5f, 2.5f)
                lineTo(9.0f, 20.0f)
                lineToRelative(2.5f, -5.5f)
                lineTo(17.0f, 12.0f)
                lineToRelative(-5.5f, -2.5f)
                close()
                moveTo(19.0f, 15.0f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15.0f, 19.0f)
                lineToRelative(2.75f, 1.25f)
                lineTo(19.0f, 23.0f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23.0f, 19.0f)
                lineToRelative(-2.75f, -1.25f)
                lineTo(19.0f, 15.0f)
                close()
            }
        }
        return _autoAwesome!!
    }

private var _autoAwesome: ImageVector? = null

val Icons.Filled.Cameraswitch: ImageVector
    get() {
        if (_cameraswitch != null) {
            return _cameraswitch!!
        }
        _cameraswitch = materialIcon(name = "Filled.Cameraswitch") {
            materialPath {
                moveTo(16.0f, 7.0f)
                horizontalLineToRelative(-1.0f)
                lineToRelative(-1.0f, -1.0f)
                horizontalLineToRelative(-4.0f)
                lineTo(9.0f, 7.0f)
                horizontalLineTo(8.0f)
                curveTo(6.9f, 7.0f, 6.0f, 7.9f, 6.0f, 9.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(9.0f)
                curveTo(18.0f, 7.9f, 17.1f, 7.0f, 16.0f, 7.0f)
                close()
                moveTo(12.0f, 14.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                curveTo(14.0f, 13.1f, 13.1f, 14.0f, 12.0f, 14.0f)
                close()
            }
            materialPath {
                moveTo(8.57f, 0.51f)
                lineToRelative(4.48f, 4.48f)
                verticalLineTo(2.04f)
                curveToRelative(4.72f, 0.47f, 8.48f, 4.23f, 8.95f, 8.95f)
                curveToRelative(0.0f, 0.0f, 2.0f, 0.0f, 2.0f, 0.0f)
                curveTo(23.34f, 3.02f, 15.49f, -1.59f, 8.57f, 0.51f)
                close()
            }
            materialPath {
                moveTo(10.95f, 21.96f)
                curveTo(6.23f, 21.49f, 2.47f, 17.73f, 2.0f, 13.01f)
                curveToRelative(0.0f, 0.0f, -2.0f, 0.0f, -2.0f, 0.0f)
                curveToRelative(0.66f, 7.97f, 8.51f, 12.58f, 15.43f, 10.48f)
                lineToRelative(-4.48f, -4.48f)
                verticalLineTo(21.96f)
                close()
            }
        }
        return _cameraswitch!!
    }

private var _cameraswitch: ImageVector? = null

val Icons.Filled.Colorize: ImageVector
    get() {
        if (_colorize != null) {
            return _colorize!!
        }
        _colorize = materialIcon(name = "Filled.Colorize") {
            materialPath {
                moveTo(20.71f, 5.63f)
                lineToRelative(-2.34f, -2.34f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-3.12f, 3.12f)
                lineToRelative(-1.93f, -1.91f)
                lineToRelative(-1.41f, 1.41f)
                lineToRelative(1.42f, 1.42f)
                lineTo(3.0f, 16.25f)
                verticalLineTo(21.0f)
                horizontalLineToRelative(4.75f)
                lineToRelative(8.92f, -8.92f)
                lineToRelative(1.42f, 1.42f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(-1.92f, -1.92f)
                lineToRelative(3.12f, -3.12f)
                curveToRelative(0.4f, -0.4f, 0.4f, -1.03f, 0.01f, -1.42f)
                close()
                moveTo(6.92f, 19.0f)
                lineTo(5.0f, 17.08f)
                lineToRelative(8.06f, -8.06f)
                lineToRelative(1.92f, 1.92f)
                lineTo(6.92f, 19.0f)
                close()
            }
        }
        return _colorize!!
    }

private var _colorize: ImageVector? = null

val Icons.Filled.Compare: ImageVector
    get() {
        if (_compare != null) {
            return _compare!!
        }
        _compare = materialIcon(name = "Filled.Compare") {
            materialPath {
                moveTo(10.0f, 3.0f)
                lineTo(5.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                lineTo(12.0f, 1.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(10.0f, 18.0f)
                lineTo(5.0f, 18.0f)
                lineToRelative(5.0f, -6.0f)
                verticalLineToRelative(6.0f)
                close()
                moveTo(19.0f, 3.0f)
                horizontalLineToRelative(-5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(13.0f)
                lineToRelative(-5.0f, -6.0f)
                verticalLineToRelative(9.0f)
                horizontalLineToRelative(5.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(21.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
            }
        }
        return _compare!!
    }

private var _compare: ImageVector? = null

val Icons.Filled.ErrorOutline: ImageVector
    get() {
        if (_errorOutline != null) {
            return _errorOutline!!
        }
        _errorOutline = materialIcon(name = "Filled.ErrorOutline") {
            materialPath {
                moveTo(11.0f, 15.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(11.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(11.99f, 2.0f)
                curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                close()
            }
        }
        return _errorOutline!!
    }

private var _errorOutline: ImageVector? = null

val Icons.Filled.Flip: ImageVector
    get() {
        if (_flip != null) {
            return _flip!!
        }
        _flip = materialIcon(name = "Filled.Flip") {
            materialPath {
                moveTo(15.0f, 21.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(19.0f, 9.0f)
                horizontalLineToRelative(2.0f)
                lineTo(21.0f, 7.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(3.0f, 5.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-2.0f)
                lineTo(5.0f, 19.0f)
                lineTo(5.0f, 5.0f)
                horizontalLineToRelative(4.0f)
                lineTo(9.0f, 3.0f)
                lineTo(5.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                close()
                moveTo(19.0f, 3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(11.0f, 23.0f)
                horizontalLineToRelative(2.0f)
                lineTo(13.0f, 1.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(22.0f)
                close()
                moveTo(19.0f, 17.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(15.0f, 5.0f)
                horizontalLineToRelative(2.0f)
                lineTo(17.0f, 3.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(19.0f, 13.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(19.0f, 21.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
        return _flip!!
    }

private var _flip: ImageVector? = null

val Icons.Filled.History: ImageVector
    get() {
        if (_history != null) {
            return _history!!
        }
        _history = materialIcon(name = "Filled.History") {
            materialPath {
                moveTo(13.0f, 3.0f)
                curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
                lineTo(1.0f, 12.0f)
                lineToRelative(3.89f, 3.89f)
                lineToRelative(0.07f, 0.14f)
                lineTo(9.0f, 12.0f)
                lineTo(6.0f, 12.0f)
                curveToRelative(0.0f, -3.87f, 3.13f, -7.0f, 7.0f, -7.0f)
                reflectiveCurveToRelative(7.0f, 3.13f, 7.0f, 7.0f)
                reflectiveCurveToRelative(-3.13f, 7.0f, -7.0f, 7.0f)
                curveToRelative(-1.93f, 0.0f, -3.68f, -0.79f, -4.94f, -2.06f)
                lineToRelative(-1.42f, 1.42f)
                curveTo(8.27f, 19.99f, 10.51f, 21.0f, 13.0f, 21.0f)
                curveToRelative(4.97f, 0.0f, 9.0f, -4.03f, 9.0f, -9.0f)
                reflectiveCurveToRelative(-4.03f, -9.0f, -9.0f, -9.0f)
                close()
                moveTo(12.0f, 8.0f)
                verticalLineToRelative(5.0f)
                lineToRelative(4.28f, 2.54f)
                lineToRelative(0.72f, -1.21f)
                lineToRelative(-3.5f, -2.08f)
                lineTo(13.5f, 8.0f)
                lineTo(12.0f, 8.0f)
                close()
            }
        }
        return _history!!
    }

private var _history: ImageVector? = null

val Icons.Filled.NoPhotography: ImageVector
    get() {
        if (_noPhotography != null) {
            return _noPhotography!!
        }
        _noPhotography = materialIcon(name = "Filled.NoPhotography") {
            materialPath {
                moveTo(10.94f, 8.12f)
                lineTo(7.48f, 4.66f)
                lineTo(9.0f, 3.0f)
                horizontalLineToRelative(6.0f)
                lineToRelative(1.83f, 2.0f)
                horizontalLineTo(20.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, 0.9f, 2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 0.05f, -0.01f, 0.1f, -0.02f, 0.16f)
                lineToRelative(-5.1f, -5.1f)
                curveTo(16.96f, 13.71f, 17.0f, 13.36f, 17.0f, 13.0f)
                curveToRelative(0.0f, -2.76f, -2.24f, -5.0f, -5.0f, -5.0f)
                curveTo(11.64f, 8.0f, 11.29f, 8.04f, 10.94f, 8.12f)
                close()
                moveTo(20.49f, 23.31f)
                lineTo(18.17f, 21.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                verticalLineTo(7.0f)
                curveToRelative(0.0f, -0.59f, 0.27f, -1.12f, 0.68f, -1.49f)
                lineToRelative(-2.0f, -2.0f)
                lineTo(2.1f, 2.1f)
                lineToRelative(19.8f, 19.8f)
                lineTo(20.49f, 23.31f)
                close()
                moveTo(14.49f, 17.32f)
                lineToRelative(-1.5f, -1.5f)
                curveTo(12.67f, 15.92f, 12.35f, 16.0f, 12.0f, 16.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, -1.34f, -3.0f, -3.0f)
                curveToRelative(0.0f, -0.35f, 0.08f, -0.67f, 0.19f, -0.98f)
                lineToRelative(-1.5f, -1.5f)
                curveTo(7.25f, 11.24f, 7.0f, 12.09f, 7.0f, 13.0f)
                curveToRelative(0.0f, 2.76f, 2.24f, 5.0f, 5.0f, 5.0f)
                curveTo(12.91f, 18.0f, 13.76f, 17.75f, 14.49f, 17.32f)
                close()
            }
        }
        return _noPhotography!!
    }

private var _noPhotography: ImageVector? = null

val Icons.Filled.PhotoCamera: ImageVector
    get() {
        if (_photoCamera != null) {
            return _photoCamera!!
        }
        _photoCamera = materialIcon(name = "Filled.PhotoCamera") {
            materialPath {
                moveTo(12.0f, 12.0f)
                moveToRelative(-3.2f, 0.0f)
                arcToRelative(3.2f, 3.2f, 0.0f, true, true, 6.4f, 0.0f)
                arcToRelative(3.2f, 3.2f, 0.0f, true, true, -6.4f, 0.0f)
            }
            materialPath {
                moveTo(9.0f, 2.0f)
                lineTo(7.17f, 4.0f)
                lineTo(4.0f, 4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(22.0f, 6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-3.17f)
                lineTo(15.0f, 2.0f)
                lineTo(9.0f, 2.0f)
                close()
                moveTo(12.0f, 17.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
                reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
                reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
                close()
            }
        }
        return _photoCamera!!
    }

private var _photoCamera: ImageVector? = null

val Icons.Filled.PhotoLibrary: ImageVector
    get() {
        if (_photoLibrary != null) {
            return _photoLibrary!!
        }
        _photoLibrary = materialIcon(name = "Filled.PhotoLibrary") {
            materialPath {
                moveTo(22.0f, 16.0f)
                lineTo(22.0f, 4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                lineTo(8.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                close()
                moveTo(11.0f, 12.0f)
                lineToRelative(2.03f, 2.71f)
                lineTo(16.0f, 11.0f)
                lineToRelative(4.0f, 5.0f)
                lineTo(8.0f, 16.0f)
                lineToRelative(3.0f, -4.0f)
                close()
                moveTo(2.0f, 6.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                lineTo(4.0f, 20.0f)
                lineTo(4.0f, 6.0f)
                lineTo(2.0f, 6.0f)
                close()
            }
        }
        return _photoLibrary!!
    }

private var _photoLibrary: ImageVector? = null

val Icons.Filled.RestartAlt: ImageVector
    get() {
        if (_restartAlt != null) {
            return _restartAlt!!
        }
        _restartAlt = materialIcon(name = "Filled.RestartAlt") {
            materialPath {
                moveTo(12.0f, 5.0f)
                verticalLineTo(2.0f)
                lineTo(8.0f, 6.0f)
                lineToRelative(4.0f, 4.0f)
                verticalLineTo(7.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                curveToRelative(0.0f, 2.97f, -2.17f, 5.43f, -5.0f, 5.91f)
                verticalLineToRelative(2.02f)
                curveToRelative(3.95f, -0.49f, 7.0f, -3.85f, 7.0f, -7.93f)
                curveTo(20.0f, 8.58f, 16.42f, 5.0f, 12.0f, 5.0f)
                close()
            }
            materialPath {
                moveTo(6.0f, 13.0f)
                curveToRelative(0.0f, -1.65f, 0.67f, -3.15f, 1.76f, -4.24f)
                lineTo(6.34f, 7.34f)
                curveTo(4.9f, 8.79f, 4.0f, 10.79f, 4.0f, 13.0f)
                curveToRelative(0.0f, 4.08f, 3.05f, 7.44f, 7.0f, 7.93f)
                verticalLineToRelative(-2.02f)
                curveTo(8.17f, 18.43f, 6.0f, 15.97f, 6.0f, 13.0f)
                close()
            }
        }
        return _restartAlt!!
    }

private var _restartAlt: ImageVector? = null

val Icons.Filled.SaveAlt: ImageVector
    get() {
        if (_saveAlt != null) {
            return _saveAlt!!
        }
        _saveAlt = materialIcon(name = "Filled.SaveAlt") {
            materialPath {
                moveTo(19.0f, 12.0f)
                verticalLineToRelative(7.0f)
                lineTo(5.0f, 19.0f)
                verticalLineToRelative(-7.0f)
                lineTo(3.0f, 12.0f)
                verticalLineToRelative(7.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(13.0f, 12.67f)
                lineToRelative(2.59f, -2.58f)
                lineTo(17.0f, 11.5f)
                lineToRelative(-5.0f, 5.0f)
                lineToRelative(-5.0f, -5.0f)
                lineToRelative(1.41f, -1.41f)
                lineTo(11.0f, 12.67f)
                lineTo(11.0f, 3.0f)
                horizontalLineToRelative(2.0f)
                close()
            }
        }
        return _saveAlt!!
    }

private var _saveAlt: ImageVector? = null
