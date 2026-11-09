package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.StatusMedia
import com.example.util.StorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val _isBusiness = MutableStateFlow(false)
    val isBusiness: StateFlow<Boolean> = _isBusiness.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _imagesList = MutableStateFlow<List<StatusMedia>>(emptyList())
    val imagesList: StateFlow<List<StatusMedia>> = _imagesList.asStateFlow()

    private val _videosList = MutableStateFlow<List<StatusMedia>>(emptyList())
    val videosList: StateFlow<List<StatusMedia>> = _videosList.asStateFlow()

    private val _savedList = MutableStateFlow<List<StatusMedia>>(emptyList())
    val savedList: StateFlow<List<StatusMedia>> = _savedList.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<StatusMedia>>(emptySet())
    val selectedItems: StateFlow<Set<StatusMedia>> = _selectedItems.asStateFlow()

    fun setBusiness(isBusiness: Boolean) {
        if (_isBusiness.value != isBusiness) {
            _isBusiness.value = isBusiness
            clearSelection()
            loadStatuses()
        }
    }

    fun checkPermission() {
        val granted = StorageHelper.isPermissionGranted(getApplication(), _isBusiness.value)
        _isPermissionGranted.value = granted
    }

    fun loadStatuses() {
        val context = getApplication<Application>()
        checkPermission()

        viewModelScope.launch {
            _isLoading.value = true

            // 1. Load active statuses (images and videos) if permission granted
            if (_isPermissionGranted.value) {
                val images = StorageHelper.loadStatuses(context, _isBusiness.value, filterVideosOnly = false)
                val videos = StorageHelper.loadStatuses(context, _isBusiness.value, filterVideosOnly = true)
                _imagesList.value = images
                _videosList.value = videos
            } else {
                _imagesList.value = emptyList()
                _videosList.value = emptyList()
            }

            // 2. Load saved statuses from gallery
            val saved = StorageHelper.loadSavedStatuses(context)
            _savedList.value = saved

            _isLoading.value = false
        }
    }

    fun toggleSelection(item: StatusMedia) {
        val currentSet = _selectedItems.value.toMutableSet()
        val found = currentSet.firstOrNull { it.name == item.name }
        if (found != null) {
            currentSet.remove(found)
        } else {
            currentSet.add(item)
        }
        _selectedItems.value = currentSet
        _isMultiSelectMode.value = currentSet.isNotEmpty()
    }

    fun selectAll(tabIndex: Int) {
        val listToSelect = when (tabIndex) {
            0 -> _imagesList.value
            1 -> _videosList.value
            else -> _savedList.value
        }
        _selectedItems.value = listToSelect.toSet()
        _isMultiSelectMode.value = listToSelect.isNotEmpty()
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun saveSingle(media: StatusMedia, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = StorageHelper.saveStatusToGallery(getApplication(), media)
            if (success) {
                // Refresh saved list
                val saved = StorageHelper.loadSavedStatuses(getApplication())
                _savedList.value = saved
            }
            onDone(success)
        }
    }

    fun batchSave(onDone: (savedCount: Int, total: Int) -> Unit) {
        val itemsToSave = _selectedItems.value.toList()
        if (itemsToSave.isEmpty()) {
            onDone(0, 0)
            return
        }

        viewModelScope.launch {
            var count = 0
            for (item in itemsToSave) {
                val ok = StorageHelper.saveStatusToGallery(getApplication(), item)
                if (ok) count++
            }
            clearSelection()
            val saved = StorageHelper.loadSavedStatuses(getApplication())
            _savedList.value = saved
            onDone(count, itemsToSave.size)
        }
    }

    fun batchShare() {
        val itemsToShare = _selectedItems.value.toList()
        if (itemsToShare.isNotEmpty()) {
            StorageHelper.shareMultipleStatuses(getApplication(), itemsToShare)
            clearSelection()
        }
    }

    fun deleteSaved(media: StatusMedia, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = StorageHelper.deleteSavedStatus(getApplication(), media)
            if (ok) {
                val saved = StorageHelper.loadSavedStatuses(getApplication())
                _savedList.value = saved
            }
            onDone(ok)
        }
    }

    fun batchDeleteSaved(onDone: (Int) -> Unit) {
        val itemsToDelete = _selectedItems.value.toList()
        viewModelScope.launch {
            var deleted = 0
            for (item in itemsToDelete) {
                val ok = StorageHelper.deleteSavedStatus(getApplication(), item)
                if (ok) deleted++
            }
            clearSelection()
            val saved = StorageHelper.loadSavedStatuses(getApplication())
            _savedList.value = saved
            onDone(deleted)
        }
    }
}
