package com.christianlatona.android.photogallery

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import java.util.concurrent.TimeUnit

private const val TAG = "PhotoGalleryFragment"
private const val POLL_WORK = "POLL_WORK"

class PhotoGalleryFragment: VisibleFragment() {

    private val photoGalleryViewModel: PhotoGalleryViewModel by lazy {
        ViewModelProvider(this).get(PhotoGalleryViewModel::class.java)
    }
    private lateinit var photoRecyclerView: RecyclerView
    private lateinit var thumbnailDownloader: ThumbnailDownloader<PhotoHolder>
    private val photoAdapter = PhotoAdapter()

    interface Callbacks {
        fun onRequestWaiting()
        fun onRequestDone()
    }
    private var callbacks: Callbacks? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        setHasOptionsMenu(true)
        val responseHandler = Handler(Looper.myLooper()!!) // need explicitly the looper associated with the thread
        thumbnailDownloader = ThumbnailDownloader(responseHandler) { photoHolder, bitmap ->
            val drawable = BitmapDrawable(resources, bitmap)
            photoHolder.bindDrawable(drawable)
        }
        thumbnailDownloader.fragmentLifecycle = lifecycle
        // lifecycle.addObserver(thumbnailDownloader.fragmentLifecycleObserver)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_photo_gallery, container, false)
        /*viewLifecycleOwnerLiveData.observe(viewLifecycleOwner, {
                it?.lifecycle?.addObserver(thumbnailDownloader.viewLifecycleObserver)
        })*/
        photoRecyclerView = view.findViewById<RecyclerView>(R.id.photo_recycler_view).apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = photoAdapter
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) { //  = PhotoAdapter(galleryItems)
        super.onViewCreated(view, savedInstanceState)
        photoGalleryViewModel.galleryItemLiveData.observe(
            viewLifecycleOwner,{ galleryItems ->
                if(galleryItems.isEmpty()){
                    Toast.makeText(context, R.string.no_photos_available, Toast.LENGTH_SHORT).show()
                }
                (photoRecyclerView.adapter as PhotoAdapter).submitList(galleryItems)
                //  we attach an adapter when we have updated gallery items
                callbacks?.onRequestDone()
            }
        )
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_photo_gallery, menu)

        val searchItem: MenuItem = menu.findItem(R.id.menu_item_search)
        val searchView = searchItem.actionView as SearchView

        searchView.apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener{
                override fun onQueryTextSubmit(query: String): Boolean {
                    photoGalleryViewModel.fetchPhotos(query)
                    photoAdapter.clearList()
                    clearFocus() // this make searchView drop the focus, making keyboard disappear
                    callbacks?.onRequestWaiting()
                    return true
                }
                override fun onQueryTextChange(newText: String): Boolean {
                    // Log.d(TAG, "QueryTextChange: $newText")
                    return false
                }
            })
            setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (!hasFocus){
                    searchView.onActionViewCollapsed() // this worked...
                }
            }
            setOnSearchClickListener {
                searchView.setQuery(photoGalleryViewModel.searchTerm, false)
            }
        }

        val toggleItem = menu.findItem(R.id.menu_item_toggle_polling)
        val isPolling = QueryPreferences.isPolling(requireContext())
        val toggleItemTitle = if (isPolling) {
            R.string.stop_polling
        }else{
            R.string.start_polling
        }
        toggleItem.setTitle(toggleItemTitle)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId){
            R.id.menu_item_clear -> {
                photoGalleryViewModel.fetchPhotos("")
                // the flickr sites returns an empty list for interestingness.getList ...
                true
            }
            R.id.menu_item_toggle_polling -> {
                val isPolling = QueryPreferences.isPolling(requireContext())
                if (isPolling) {
                    WorkManager.getInstance().cancelUniqueWork(POLL_WORK)
                    QueryPreferences.setPolling(requireContext(), false)
                } else {
                    val constraints= Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                    val periodicRequest= PeriodicWorkRequest
                        .Builder(PollWorker::class.java, 15, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .build()
                    WorkManager.getInstance()
                        .enqueueUniquePeriodicWork(POLL_WORK,
                            ExistingPeriodicWorkPolicy.KEEP,
                            periodicRequest)
                    QueryPreferences.setPolling(requireContext(), true)
                }
                activity?.invalidateOptionsMenu() // Declare that the options menu has changed,
                // so should be recreated
                true
            }
            else -> super.onOptionsItemSelected(item) // i think this is a default behavior to remember
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callbacks = context as Callbacks
    }

    override fun onDetach() {
        super.onDetach()
        callbacks = null
    }

    private inner class PhotoHolder(itemImageView: ImageView):
        RecyclerView.ViewHolder(itemImageView)
        ,View.OnClickListener{

        private lateinit var galleryItem: GalleryItem
        val bindDrawable: (Drawable) -> Unit = itemImageView::setImageDrawable // never saw this syntax, its like ES6
        fun bindGalleryItem(item: GalleryItem) {
            galleryItem = item
        }
        init {
            itemView.setOnClickListener(this)
        }
        override fun onClick(v: View?) {
            val intent = PhotoPageActivity.newIntent(requireContext(), galleryItem.photoPageUri)
            startActivity(intent)
        }
    }

    private inner class PhotoAdapter:
            androidx.recyclerview.widget.ListAdapter<GalleryItem, PhotoHolder>(PhotoDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoHolder {
            val imageView = layoutInflater.inflate(R.layout.list_item_gallery, parent, false) as ImageView
            return PhotoHolder(imageView)
        }

        override fun onBindViewHolder(holder: PhotoHolder, position: Int) {
            val galleryItem = getItem(position)
            holder.bindGalleryItem(galleryItem)
            val placeholder: Drawable = ContextCompat.getDrawable( // ContextCompat = helper for accessing context features
                    requireContext(),
                    R.drawable.bill_up_close
            ) ?: ColorDrawable() // black
            holder.bindDrawable(placeholder)
            // Log.d("ThumbnailDownloader", "ddd")
            thumbnailDownloader.queueThumbnail(holder, galleryItem.url)

            // no idea of how to implement preloading, since you cannot get next holders
            /*if(getItem(position+9) != null){
                for (i in 0..10) {
                    val preloadingItem = getItem(position+1)
                    thumbnailDownloader.queueThumbnail(holder, preloadingItem.url)
                }
            }*/
        }

        fun clearList(){
            photoGalleryViewModel.galleryItemLiveData.value?.clear()
            notifyDataSetChanged() // dunno if necessary
        }
    }

    private class PhotoDiffCallback: DiffUtil.ItemCallback<GalleryItem>(){
        override fun areItemsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        fun newInstance(): PhotoGalleryFragment {
            return PhotoGalleryFragment()
        }
    }
}