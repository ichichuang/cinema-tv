package com.ichichuang.cinema.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichichuang.cinema.data.settings.CinemaSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
)

class SettingsViewModel(private val settings: CinemaSettings) : ViewModel() {

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
}
