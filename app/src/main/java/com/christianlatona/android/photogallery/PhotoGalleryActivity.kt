package com.christianlatona.android.photogallery

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class PhotoGalleryActivity : AppCompatActivity(), PhotoGalleryFragment.Callbacks {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_gallery)

        val isFragmentContainerEmpty = savedInstanceState == null

        if(isFragmentContainerEmpty){
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container,PhotoGalleryFragment.newInstance())
                .commit()
        }
    }

    override fun onRequestWaiting() {
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container,ProgressBarFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onRequestDone() {
        supportFragmentManager.popBackStack()
    }

    companion object {
        fun newIntent(context: Context): Intent {
            return Intent(context, PhotoGalleryActivity::class.java)
        }
    }

}