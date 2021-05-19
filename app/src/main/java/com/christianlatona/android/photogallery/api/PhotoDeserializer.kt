package com.christianlatona.android.photogallery.api

import com.christianlatona.android.photogallery.GalleryItem
import com.google.gson.*
import java.lang.reflect.Type

class PhotoDeserializer: JsonDeserializer<PhotoResponse> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): PhotoResponse {
        // Pull photos object out of JsonElement
        // And convert to PhotoResponse object

        val jsonObject = json?.asJsonObject!! // these functions are convenience methods
        val jsonArray = jsonObject.get("photo").asJsonArray // we get an array of photos
        val photos = mutableListOf<GalleryItem>()
        jsonArray.forEach{ photoElement ->
            val photoObject = photoElement.asJsonObject
            if(photoObject.get("url_s") != null){
                val galleryItem = GalleryItem(
                    photoObject.get("title").asString,
                    photoObject.get("id").asString,
                    photoObject.get("url_s").asString,
                    photoObject.get("owner").asString
                )
                photos.add(galleryItem)
            }
        }
        val photoResponse = PhotoResponse()
        photoResponse.galleryItems = photos
        return photoResponse
    }
}