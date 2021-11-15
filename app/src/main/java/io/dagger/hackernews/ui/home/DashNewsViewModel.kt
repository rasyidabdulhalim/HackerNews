package io.dagger.hackernews.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.dagger.hackernews.data.model.Item
import io.dagger.hackernews.data.remote.HNApiClient
import io.dagger.hackernews.utils.Errors
import io.dagger.hackernews.utils.getSafeResponse
import io.dagger.hackernews.utils.isConnected
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

class DashNewsViewModel(application: Application) : AndroidViewModel(application) {
    private val client = HNApiClient(application).hnApiService

    private val topItems = mutableListOf<Item>()
    private val savedItems = mutableListOf<Item>()

    var bannerIdx=0

    fun geItemsAsync(type: String, isRefresh: Boolean): Deferred<List<Item>> {
        return viewModelScope.async(Dispatchers.IO) {
            val list = getListCategory(type)
            if (list.isNotEmpty() && !isRefresh){
                return@async list
            }else{
                if (isConnected(getApplication())){
                    val itemIds = getItemIds(type)
                    val itemList = getItemList(itemIds,type == "Top")
                    list.apply {
                        clear()
                        addAll(itemList)
                    }
                    list
                }else{
                    throw Errors.OfflineException()
                }
            }
        }
    }

    private fun getListCategory(type: String) = when (type) {
        "Top" -> topItems
        "Saved" -> savedItems
        else -> throw Errors.UnknownCategory()

    }

    private suspend fun getItemIds(type: String)=when(type){
        "Top" -> getSafeResponse(client.topStories())
         else -> throw Errors.UnknownCategory()
    }


    private suspend fun getItemList(list: List<Long>, isBanner: Boolean = false): List<Item> {
        return if (list.isNotEmpty()) {
            val end = if (isBanner) 6 else 4
            val itemList = mutableListOf<Item>()
            for (i in 0 until end) {
                val item = client.getItem(list[i]).body()
                item?.let { itemList.add(item) }
            }
            itemList
        } else {
            emptyList()
        }
    }


}