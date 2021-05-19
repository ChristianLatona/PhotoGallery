package com.christianlatona.android.photogallery

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Log
import android.util.LruCache
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ThumbnailDownloader"
private const val MESSAGE_DOWNLOAD = 0

class ThumbnailDownloader<in T: Any>(
        private val responseHandler: Handler,
        private val onThumbnailDownloaded: (T, Bitmap) -> Unit
): HandlerThread(TAG) { // dunno what "in" keyword means
    // LifecycleObserver ties this class to the lifecycle
    // now it can receive lifecycle callbacks from any lifecycle owner

    var fragmentLifecycle: Lifecycle? = null // in this challenge we take a lifecycle from the main
        set(value) {
            field = value
            field?.addObserver(this.fragmentLifecycleObserver) // that automatically add an observer, instead of
            // doing it in the main
        }

    private val fragmentLifecycleObserver: LifecycleObserver =
            object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
                fun setup() {
                    Log.i(TAG, "Starting background thread")
                    start()
                    looper
                }

                @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
                fun clearQueue() { // we deleted the viewLifecycleObserver, putting clearQueue() on the stop,
                    // because we need this in onDestroyView or before, if we do this after, like the normal
                    // onDestroy, the app will crash
                    Log.i(TAG, "Clearing all requests from queue")
                    requestHandler.removeMessages(MESSAGE_DOWNLOAD)
                    requestMap.clear()
                }

                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun tearDown() { // can't put clearQueue() code there, it would be called after onDestroyView
                    Log.i(TAG, "Destroying background thread")
                    fragmentLifecycle?.removeObserver(this)
                    quit()
                }
            }

    private var hasQuit = false
    private lateinit var requestHandler: Handler
    private val requestMap = ConcurrentHashMap<T, String>() // why concurrent? it's thread safe
    // seems like T is not only the object name, but an entire obj reference, elsewhere it wouldn't work
    private val flickrFetchr = FlickrFetchr()

    override fun quit(): Boolean {
        hasQuit = true
        return super.quit()
    }

    @Suppress("UNCHECKED_CAST")
    // @SuppressLint("HandlerLeak")
    override fun onLooperPrepared() {
        requestHandler = object : Handler(Looper.myLooper()!!) { // we are suppressing the deprecated Handler()
            override fun handleMessage(msg: Message) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    val target = msg.obj as T
                    // Log.i(TAG, "Got a request for URL: ${requestMap[target]}")
                    handleRequest(target)
                }
            }
        }
    }

    fun queueThumbnail(target: T, url: String) {
        // Log.i(TAG, "got a URL: $url")
        requestMap[target] = url
        requestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget()
        // what: MESSAGE_DOWNLOAD, obj: target
    }

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cache = object: LruCache<String, Bitmap>(maxMemory/8){
        override fun sizeOf(key: String, value: Bitmap): Int {
            Log.i(TAG, "maxMemory/8: ${maxMemory/8}")
            Log.i(TAG, "value.byteCount: ${value.byteCount/1024}")
            return value.byteCount/1024

        }
    }
    // I made a singleton to override sizeOf, but just completely bugged the cache

    private fun handleRequest(target: T) {
        val url = requestMap[target] ?: return

        var bitmap: Bitmap? = cache.get(url)
        // Log.d(TAG, "bitmap: $bitmap")
        if(bitmap == null){
            bitmap = flickrFetchr.fetchPhoto(url) ?: return
            cache.put(url,bitmap)
            // Log.d(TAG, "bitmap2: $bitmap")
            // Log.d(TAG, "url: $url")
            // Log.d(TAG, "put in the cache ${cache.get(url)}")
        }

        responseHandler.post(Runnable{ // we are skipping a passage like creating a new message and sending it back
            if (requestMap[target] != url || hasQuit){
                return@Runnable
            }

            requestMap.remove(target)
            onThumbnailDownloaded(target, bitmap)
        })
    }
}