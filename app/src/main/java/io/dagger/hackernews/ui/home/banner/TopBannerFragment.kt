package io.dagger.hackernews.ui.home.banner


import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Picasso
import io.dagger.hackernews.R
import io.dagger.hackernews.data.model.Item
import io.dagger.hackernews.ui.ITEM_TYPE
import io.dagger.hackernews.ui.NewsItemAdapter
import io.dagger.hackernews.ui.newsDetails.NewsDetailsActivity
import io.dagger.hackernews.ui.newsType.NewsTypeFragment
import io.dagger.hackernews.ui.newsType.NewsTypeViewModel
import io.dagger.hackernews.utils.Errors
import io.dagger.hackernews.utils.isConnected
import io.dagger.hackernews.utils.isPortrait
import io.dagger.hackernews.utils.setTopDrawable
import kotlinx.android.synthetic.main.fragment_news_type.*
import kotlinx.android.synthetic.main.layout_banner_item.*
import kotlinx.android.synthetic.main.layout_error.*
import kotlinx.android.synthetic.main.layout_multi_normal_items_shimmer.*
import kotlinx.coroutines.*
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.CoroutineContext


class TopBannerFragment : Fragment(),CoroutineScope {

    val supervisor = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + supervisor

    private val newsTypeViewModel by lazy {
        ViewModelProviders
            .of(this)
            .get(NewsTypeViewModel::class.java)
    }

    private var retrySnack: Snackbar? = null

    private val mutableItemList = mutableListOf<Item?>()

    private val LOGO_URL = "https://logo.clearbit.com/"

    companion object {
        private val ITEM = "item"
        fun newInstance(item: Item): TopBannerFragment {
            return TopBannerFragment().apply {
                val bundle = Bundle().apply {
                    putSerializable(ITEM, item)
                }
                arguments = bundle
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_banner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val item = arguments?.get(ITEM) as Item

        tvTitleBanner.text = item.title
        tvAuthor.text = "By "+item.by
        tvComment.text = item.descendants.toString()
        tvScore.text = item.score.toString()
        Picasso.get().load("$LOGO_URL${item.domain}").placeholder(R.drawable.hn).into(ivLogoBanner)

        view.setOnClickListener {
            val options = ActivityOptions.makeSceneTransitionAnimation(
                requireActivity(),
                ivLogoBanner, "logoTransition"
            )
            requireContext().startActivity(
                Intent(context, NewsDetailsActivity::class.java).apply {
                    putExtra("LogoUrl", "$LOGO_URL${item.domain}")
                    putExtra("url", item.url)
                    putExtra("author", item.by)
                    putExtra("itemObj",item)
                },
                options.toBundle()
            )
        }
    }


    private fun showNoSavedItemsView() {
        tvError.apply {
            isVisible = true
            text = "No Saved items"
            val drawable = resources.getDrawable(R.drawable.ic_empty_bin)
            setTopDrawable(drawable)
        }
    }

    private fun showSavedItems() {
        newsTypeViewModel.savedStories.observe(viewLifecycleOwner, Observer {
            if (it.isEmpty())
                showNoSavedItemsView()

            mutableItemList.apply {
                clear()
                addAll(it)
            }
            rvItems.adapter?.notifyDataSetChanged()
        })
        shimmerNewsType.apply {
            stopShimmer()
            isVisible = false
        }
        setUpRecyclerView(mutableItemList)
    }

    private fun retryAction() {
        if (isConnected(requireContext())) {
            val newsType = arguments?.get(TopBannerFragment.ITEM) as String
            loadStories(newsType, true)
        } else {
            launch {
                delay(300)
                retrySnack?.setText("You are offline")?.show()
            }
        }
    }

    private fun loadStories(newsType: String, refresh: Boolean) {
        launch {
            try {
                showStoryItems()

                setLoadView(true)

                val itemList = newsTypeViewModel.getStoriesAsync(newsType, refresh).await()
                mutableItemList.apply {
                    clear()
                    addAll(itemList)
                }

                setLoadView(false)

                rvItems.adapter?.notifyDataSetChanged() ?: setUpRecyclerView(mutableItemList)
                newsTypeContainer.isRefreshing = false

            } catch (u: UnknownHostException) {
                showOfflineView()
            } catch (ce: ConnectException) {
                showOfflineView()
            } catch (se: SocketTimeoutException) {
                showErrorView()
            } catch (e: Errors) {
                when (e) {
                    is Errors.OfflineException -> showOfflineView()
                    is Errors.FetchException -> showErrorView()
                }
            } finally {
                shimmerNewsType?.apply {
                    stopShimmer()
                    isVisible = false
                }
            }
        }
    }

    private fun showErrorView() {
        rvItems.isVisible = false
        tvError.apply {
            isVisible = true
            text = "Error Occured"
            val drawable = resources.getDrawable(R.drawable.ic_interupt)
            setTopDrawable(drawable)
        }
        newsTypeContainer.isRefreshing = false
        retrySnack?.setText("Error Occured")?.show()

    }

    private fun showOfflineView() {
        rvItems.isVisible = false
        tvError.apply {
            isVisible = true
            text = resources.getString(R.string.no_internet)
            val drawable = resources.getDrawable(R.drawable.ic_no_wifi)
            setTopDrawable(drawable)
        }
        newsTypeContainer.isRefreshing = false
        retrySnack?.setText("You are offline")?.show()

    }

    private fun showStoryItems() {
        tvError.isVisible = false
        rvItems.isVisible = true
        retrySnack?.dismiss()
    }

    private fun setLoadView(isLoading: Boolean) {
        shimmerNewsType.apply {
            if (isLoading) startShimmer() else stopShimmer()

            isVisible = isLoading
        }
        rvItems.isVisible = !isLoading
    }

    private fun setUpRecyclerView(mutableItemList: MutableList<Item?>) {

        rvItems.apply {
            isVisible = true
            adapter = NewsItemAdapter(
                mutableItemList,
                ITEM_TYPE.NORMAL,
                requireActivity()
            )
            layoutManager =
                if (isPortrait(requireContext())) {
                    LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false).apply {
                        scrollToPosition(newsTypeViewModel.scrollPosition)
                    }
                } else {
                    GridLayoutManager(requireContext(), 2).apply {
                        scrollToPosition(newsTypeViewModel.scrollPosition)
                    }
                }

            if (!isPortrait(requireContext())) {
                addItemDecoration(SpacesItemDecoration((10)))
            }

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = rvItems.layoutManager as LinearLayoutManager

                    newsTypeViewModel.scrollPosition = layoutManager.findFirstCompletelyVisibleItemPosition()

                    if (layoutManager.findLastCompletelyVisibleItemPosition() == mutableItemList.size - 1) {
                        if (newsTypeViewModel.isNotFullLoaded() &&
                            newsTypeViewModel.isLoading.value == false
                        ) {
                            if (isConnected(requireContext())) {
                                retrySnack?.dismiss()
                                newsTypeViewModel.isLoading.value = true
                            } else {
                                retrySnack?.setText("You are offline")?.show()
                            }
                        }
                    }
                }
            })
        }
    }

    private fun loadMoreStories(mutableItemList: MutableList<Item?>) {
        launch {
            try {

                addDummyLoadItem(true)
                val newsItemList = newsTypeViewModel.loadMoreItemsAsync().await()
                addDummyLoadItem(false)

                if (newsItemList.isNotEmpty()) {
                    mutableItemList.addAll(newsItemList)
                    rvItems.adapter?.notifyDataSetChanged()
                }
            } catch (u: UnknownHostException) {
                addDummyLoadItem(false)
                retrySnack?.setText("You are offline")?.show()
            } catch (ce: ConnectException) {
                addDummyLoadItem(false)
                retrySnack?.setText("You are offline")?.show()
            } catch (se: SocketTimeoutException) {
                addDummyLoadItem(false)
                retrySnack?.setText("Error Occured")?.show()
            } finally {
                newsTypeViewModel.isLoading.value = false
            }
        }

    }

    private fun addDummyLoadItem(toAdd: Boolean) {
        if (toAdd) {
            mutableItemList.add(null)
            rvItems.adapter?.notifyItemInserted(mutableItemList.size - 1)
        } else {
            mutableItemList.removeAt(mutableItemList.size - 1)
            rvItems.adapter?.notifyItemRemoved(mutableItemList.size - 1)
        }
    }

    override fun onDetach() {
        coroutineContext.cancelChildren()
        retrySnack?.dismiss()
        super.onDetach()
    }

    inner class SpacesItemDecoration(private val space: Int) : RecyclerView.ItemDecoration() {


        override fun getItemOffsets(
            outRect: Rect, view: View,
            parent: RecyclerView, state: RecyclerView.State
        ) {
            outRect.left = space
            outRect.right = space
            outRect.bottom = space

            if (parent.getChildLayoutPosition(view) == 0) {
                outRect.top = space
            } else {
                outRect.top = 0
            }
        }
    }

}
