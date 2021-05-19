package com.christianlatona.android.photogallery

import android.app.Application
import android.util.Log
import androidx.lifecycle.*

class PhotoGalleryViewModel(private val app: Application): AndroidViewModel(app) {

    val galleryItemLiveData: LiveData<MutableList<GalleryItem>>

    private val flickrFetchr = FlickrFetchr()
    private val mutableSearchTerm = MutableLiveData<String>()
    val searchTerm: String
        get() = mutableSearchTerm.value ?: ""

    init {
        mutableSearchTerm.value = QueryPreferences.getStoredQuery(app)
        galleryItemLiveData = Transformations.switchMap(mutableSearchTerm) { searchTerm ->
            if (searchTerm.isBlank()){
                flickrFetchr.fetchPhotos()
            }else{
                flickrFetchr.searchPhoto(searchTerm)
            }
        }

    }

    fun fetchPhotos(query: String = ""){
        QueryPreferences.setStoredQuery(app, query)
        mutableSearchTerm.value = query
    }

}