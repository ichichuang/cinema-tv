package com.ichichuang.cinema.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichichuang.cinema.data.settings.CinemaSettings
import com.ichichuang.cinema.data.settings.ImageCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class SettingsUiState(
    /** 已经保存进 DataStore 的地址 */
    val saved: String = "",
    /** 输入框里正在编辑的内容 */
    val draft: String = "",
    /** 刚保存成功（显示 2 秒的"已保存"） */
    val justSaved: Boolean = false,

    /** 更新地址：同上三个字段，各管各的 */
    val updateSaved: String = "",
    val updateDraft: String = "",
    val justSavedUpdate: Boolean = false,

    /** 清缓存的反馈（"已清掉 32.4 MB 缓存"） */
    val cacheMessage: String? = null,
)

class SettingsViewModel(
    private val settings: CinemaSettings,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.apiBase.collect { addr ->
                // 输入框为空时才同步已保存的值，避免打断正在输入的用户
                _state.update { if (it.draft.isBlank()) it.copy(saved = addr, draft = addr) else it.copy(saved = addr) }
            }
        }
        viewModelScope.launch {
            settings.updateUrl.collect { url ->
                _state.update {
                    if (it.updateDraft.isBlank()) it.copy(updateSaved = url, updateDraft = url)
                    else it.copy(updateSaved = url)
                }
            }
        }
    }

    fun onDraftChange(value: String) = _state.update { it.copy(draft = value) }

    fun save() {
        val value = _state.value.draft
        viewModelScope.launch {
            settings.setApiBase(value)
            _state.update { it.copy(justSaved = true) }
            delay(2000)
            _state.update { it.copy(justSaved = false) }
        }
    }

    fun clear() {
        viewModelScope.launch {
            settings.setApiBase("")
            _state.update { it.copy(draft = "", saved = "") }
        }
    }

    fun onUpdateDraftChange(value: String) = _state.update { it.copy(updateDraft = value) }

    fun saveUpdate() {
        val value = _state.value.updateDraft
        viewModelScope.launch {
            settings.setUpdateUrl(value)
            _state.update { it.copy(justSavedUpdate = true) }
            delay(2000)
            _state.update { it.copy(justSavedUpdate = false) }
        }
    }

    /** 恢复默认更新地址（等于"我从没改过"，存空串） */
    fun resetUpdate() {
        viewModelScope.launch {
            settings.setUpdateUrl(CinemaSettings.DEFAULT_UPDATE_URL)
            _state.update {
                it.copy(updateDraft = CinemaSettings.DEFAULT_UPDATE_URL, updateSaved = CinemaSettings.DEFAULT_UPDATE_URL)
            }
        }
    }

    /** 清空海报缓存：真清，并如实告诉用户清掉了多少 */
    fun clearImageCache() {
        viewModelScope.launch {
            val freed = ImageCache.clear(context)
            _state.update {
                it.copy(
                    cacheMessage = when {
                        freed == null -> "这次没清掉，重启一下应用再试"
                        freed <= 0L -> "缓存本来就是空的"
                        else -> "已清掉 " + "%.1f".format(Locale.US, freed / 1024.0 / 1024.0) + " MB 海报缓存"
                    },
                )
            }
        }
    }
}
