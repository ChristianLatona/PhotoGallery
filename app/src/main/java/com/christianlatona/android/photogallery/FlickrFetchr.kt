package com.christianlatona.android.photogallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.christianlatona.android.photogallery.api.*
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val TAG = "FlickrFetchr"
class FlickrFetchr {

    private val flickrApi: FlickrApi

    init {
        val client = OkHttpClient.Builder()
            .addInterceptor(PhotoInterceptor())
            .build()

        val gson = GsonBuilder()
                .registerTypeAdapter(PhotoResponse::class.java, PhotoDeserializer())
                .create()

        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://api.flickr.com/")
            .addConverterFactory(GsonConverterFactory.create(gson)) // ScalarsConverterFactory converts
            // strings and primitive types (also wrapped ones) to text/plain bodies
            .client(client)
            .build()

        flickrApi = retrofit.create(FlickrApi::class.java)
    }

    @WorkerThread
    fun fetchPhoto(url: String): Bitmap? {
        val response: Response<ResponseBody> = flickrApi.fetchUrlBytes(url).execute()
        // this execute seems very handy, but it's a synchronous request. It will normally cause a crash
        // because network call cannot be done on main thread. @WorkerThread ensures this is done in a background thread
        // It will not do the work, it just lint if it is in the main thread, you have to do this in a background thread
        val bitmap = response.body()?.byteStream()?.use(BitmapFactory::decodeStream)
        // takes the InputStream from the response and decode it into a bitmap
        // use { } handles the closing of the InputStream automatically
        Log.i(TAG, "Decoded bitmap=$bitmap from Response $response")
        return bitmap
    }

    fun fetchPhotosRequest(): Call<FlickrResponse>{
        return flickrApi.fetchPhotos()
    }
    fun fetchPhotos(): LiveData<MutableList<GalleryItem>>{
        return fetchPhotoMetadata(fetchPhotosRequest())
    }

    fun searchPhotosRequest(query: String): Call<FlickrResponse>{
        return flickrApi.searchPhotos(query)
    }
    fun searchPhoto(query: String): LiveData<MutableList<GalleryItem>>{
        return fetchPhotoMetadata(searchPhotosRequest(query))
    }

    private fun fetchPhotoMetadata(flickrRequest: Call<FlickrResponse>): LiveData<MutableList<GalleryItem>>{
        val responseLiveData: MutableLiveData<MutableList<GalleryItem>> = MutableLiveData()

        flickrRequest.enqueue(object : Callback<FlickrResponse>{
            override fun onFailure(call: Call<FlickrResponse>, t: Throwable) {
                Log.e("PhotoGalleryFragment", "failed to fetch photos", t)
            }
            override fun onResponse(call: Call<FlickrResponse>, response: Response<FlickrResponse>) {
                val flickrResponse: FlickrResponse? = response.body()
                val photoResponse: PhotoResponse? = flickrResponse?.photos
                var galleryItems: MutableList<GalleryItem> = photoResponse?.galleryItems ?: mutableListOf()
                galleryItems = galleryItems.filterNot {
                    it.url.isBlank()
                } as MutableList<GalleryItem> // this is because images may have invalid urls
                responseLiveData.value = galleryItems
            }

        })
        return responseLiveData
    }
}