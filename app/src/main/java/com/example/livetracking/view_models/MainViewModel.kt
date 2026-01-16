package com.example.livetracking.view_models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.livetracking.room.AppDatabase

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dao =
        AppDatabase.getInstance(application).locationDao()


    val pendingCount = dao.getPendingCount()
    val syncedCount = dao.getSyncedCount()
    val lastSyncedId = dao.getLastSyncedId()

    val isOnline = MutableLiveData<Boolean>()

    fun updateNetworkStatus(status: Boolean) {
        isOnline.postValue(status)
    }
}
