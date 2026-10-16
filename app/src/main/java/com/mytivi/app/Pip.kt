package com.mytivi.app

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

/** Nút bấm trong cửa sổ nổi gửi broadcast nội bộ này về màn hình xem kênh. */
const val ACTION_PIP_CONTROL = "com.mytivi.app.PIP_CONTROL"
const val EXTRA_PIP_CONTROL = "control"
const val PIP_PREV = 1
const val PIP_NEXT = 2
const val PIP_TOGGLE = 3

val LocalPip = staticCompositionLocalOf<PipController?> { null }

/**
 * Cửa sổ nổi (Picture-in-Picture, Android 8+):
 *  - Chỉ bật khi đang xem toàn màn hình.
 *  - Android 12+: vuốt/bấm Home là tự thu nhỏ mượt (auto-enter).
 *  - Android 8–11: thu nhỏ trong onUserLeaveHint (khi người dùng bấm Home).
 *  - Trong cửa sổ nổi có 3 nút: kênh trước, phát/tạm dừng, kênh sau.
 */
class PipController(private val activity: Activity) {
    var inPip by mutableStateOf(false)
        internal set

    val supported: Boolean by lazy {
        Build.VERSION.SDK_INT >= 26 &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private var enabled = false
    private var aspect = Rational(16, 9)
    private var playing = true

    fun setEnabled(value: Boolean) {
        enabled = value
        push()
    }

    fun update(videoAspect: Float, isPlaying: Boolean) {
        aspect = rationalOf(videoAspect)
        playing = isPlaying
        push()
    }

    fun enter(): Boolean {
        if (!supported || Build.VERSION.SDK_INT < 26) return false
        return runCatching { activity.enterPictureInPictureMode(params()) }.getOrDefault(false)
    }

    fun onUserLeaveHint() {
        if (enabled && Build.VERSION.SDK_INT in 26..30) enter()
    }

    private fun push() {
        if (!supported || Build.VERSION.SDK_INT < 26) return
        runCatching { activity.setPictureInPictureParams(params()) }
    }

    @RequiresApi(26)
    private fun params(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder()
            .setAspectRatio(aspect)
            .setActions(actions())
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(enabled)
        return b.build()
    }

    @RequiresApi(26)
    private fun actions(): List<RemoteAction> {
        val all = listOf(
            action(R.drawable.ic_skip_prev, "Kênh trước", PIP_PREV),
            if (playing) action(R.drawable.ic_pause, "Tạm dừng", PIP_TOGGLE)
            else action(R.drawable.ic_play, "Phát", PIP_TOGGLE),
            action(R.drawable.ic_skip_next, "Kênh sau", PIP_NEXT),
        )
        return all.take(activity.maxNumPictureInPictureActions.coerceAtLeast(0))
    }

    @RequiresApi(26)
    private fun action(icon: Int, title: String, code: Int): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL)
            .setPackage(activity.packageName)
            .putExtra(EXTRA_PIP_CONTROL, code)
        val pending = PendingIntent.getBroadcast(
            activity,
            code,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(activity, icon), title, title, pending)
    }

    /** Android chỉ nhận tỉ lệ cửa sổ nổi trong khoảng 1:2.39 .. 2.39:1. */
    private fun rationalOf(a: Float): Rational {
        val v = if (a.isFinite() && a > 0f) a.coerceIn(0.42f, 2.39f) else 16f / 9f
        return Rational((v * 1000).roundToInt(), 1000)
    }
}
