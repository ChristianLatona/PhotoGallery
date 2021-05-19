package com.christianlatona.android.photogallery.api

import com.christianlatona.android.photogallery.GalleryItem
import com.google.gson.annotations.SerializedName

class PhotoResponse {

    @SerializedName("photo")
    lateinit var galleryItems: MutableList<GalleryItem>
}