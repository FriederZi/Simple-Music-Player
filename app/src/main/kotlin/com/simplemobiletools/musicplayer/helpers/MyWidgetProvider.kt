package com.simplemobiletools.musicplayer.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import com.simplemobiletools.musicplayer.Constants
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.Utils
import com.simplemobiletools.musicplayer.activities.MainActivity
import com.simplemobiletools.musicplayer.extensions.getSharedPrefs
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Song
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe

class MyWidgetProvider : AppWidgetProvider() {
    companion object {
        private var mBus: Bus? = null
        private var mCurrSong: Song? = null
        private var mPlayBitmap: Bitmap? = null
        private var mPauseBitmap: Bitmap? = null
        private var mIsPlaying = false
        private var mWidgetIds: IntArray? = null

        lateinit var mRemoteViews: RemoteViews
        lateinit var mWidgetManager: AppWidgetManager
        lateinit var mContext: Context
        lateinit var mIntent: Intent

        private fun getCellsForSize(size: Int): Int {
            var n = 2
            while (70 * n - 30 < size) {
                ++n
            }
            return n - 1
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        initVariables(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    private fun initVariables(context: Context) {
        mContext = context
        mIntent = Intent(mContext, MyWidgetProvider::class.java)
        mWidgetManager = AppWidgetManager.getInstance(mContext)
        updateWidgetIds()
        for (widgetId in mWidgetIds!!) {
            mRemoteViews = getRemoteViews(mWidgetManager, mContext, widgetId)
        }

        setupViews(mContext)

        if (mBus == null) {
            mBus = BusProvider.instance
        }
        registerBus()
    }

    private fun setupIntent(action: String, id: Int) {
        mIntent.action = action
        val pendingIntent = PendingIntent.getBroadcast(mContext, 0, mIntent, 0)
        mRemoteViews.setOnClickPendingIntent(id, pendingIntent)
    }

    private fun setupAppOpenIntent(id: Int) {
        val intent = Intent(mContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0)
        mRemoteViews.setOnClickPendingIntent(id, pendingIntent)
    }

    private fun updateWidgetIds() {
        val component = ComponentName(mContext, MyWidgetProvider::class.java)
        mWidgetIds = mWidgetManager.getAppWidgetIds(component)
    }

    @Subscribe
    fun songChangedEvent(event: Events.SongChanged) {
        mCurrSong = event.song
        updateSongInfo()
        updateWidgets()
    }

    private fun updateSongInfo() {
        var title = ""
        var artist = ""
        if (mCurrSong != null) {
            title = mCurrSong!!.title
            artist = mCurrSong!!.artist
        }

        mRemoteViews.setTextViewText(R.id.songTitle, title)
        mRemoteViews.setTextViewText(R.id.songArtist, artist)
    }

    @Subscribe
    fun songStateChanged(event: Events.SongStateChanged) {
        if (mIsPlaying == event.isPlaying)
            return

        mIsPlaying = event.isPlaying
        updatePlayPauseButton()
        updateWidgets()
    }

    private fun updatePlayPauseButton() {
        val bmp = if (mIsPlaying) mPauseBitmap else mPlayBitmap
        mRemoteViews.setImageViewBitmap(R.id.playPauseBtn, bmp)
    }

    private fun updateWidgets() {
        for (widgetId in mWidgetIds!!) {
            mWidgetManager.updateAppWidget(widgetId, mRemoteViews)
        }
    }

    private fun updateColors() {
        val prefs = mContext.getSharedPrefs()
        val res = mContext.resources
        val defaultColor = res.getColor(R.color.dark_grey_transparent)
        val newBgColor = prefs.getInt(Constants.WIDGET_BG_COLOR, defaultColor)
        val newTextColor = prefs.getInt(Constants.WIDGET_TEXT_COLOR, Color.WHITE)
        var bmp = Utils.getColoredIcon(res, newTextColor, R.mipmap.previous)

        mRemoteViews.apply {
            setInt(R.id.widget_holder, "setBackgroundColor", newBgColor)
            setInt(R.id.songTitle, "setTextColor", newTextColor)
            setInt(R.id.songArtist, "setTextColor", newTextColor)

            setImageViewBitmap(R.id.previousBtn, bmp)

            mPlayBitmap = Utils.getColoredIcon(res, newTextColor, R.mipmap.play)
            mPauseBitmap = Utils.getColoredIcon(res, newTextColor, R.mipmap.pause)

            bmp = Utils.getColoredIcon(res, newTextColor, R.mipmap.next)
            setImageViewBitmap(R.id.nextBtn, bmp)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when (action) {
            Constants.PREVIOUS, Constants.PLAYPAUSE, Constants.NEXT -> Utils.sendIntent(context, action)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        unregisterBus()
        mContext = context
        updateWidgetIds()
    }

    private fun setupButtons() {
        setupIntent(Constants.PREVIOUS, R.id.previousBtn)
        setupIntent(Constants.PLAYPAUSE, R.id.playPauseBtn)
        setupIntent(Constants.NEXT, R.id.nextBtn)

        setupAppOpenIntent(R.id.songTitle)
        setupAppOpenIntent(R.id.songArtist)
    }

    private fun setupViews(context: Context) {
        mContext = context
        updateWidgetIds()
        updateColors()
        setupButtons()
        updateSongInfo()
        updatePlayPauseButton()
        updateWidgets()
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int, newOptions: Bundle) {
        mRemoteViews = getRemoteViews(appWidgetManager, context, widgetId)
        mWidgetManager = appWidgetManager
        setupViews(context)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, widgetId, newOptions)
    }

    private fun getRemoteViews(appWidgetManager: AppWidgetManager, context: Context, widgetId: Int): RemoteViews {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val rows = getCellsForSize(minHeight)

        var layoutId = R.layout.widget
        if (rows == 1)
            layoutId = R.layout.small_widget

        return RemoteViews(context.packageName, layoutId)
    }

    private fun registerBus() {
        try {
            mBus!!.register(this)
        } catch (e: Exception) {
        }

    }

    private fun unregisterBus() {
        try {
            mBus!!.unregister(this)
        } catch (e: Exception) {
        }

    }
}