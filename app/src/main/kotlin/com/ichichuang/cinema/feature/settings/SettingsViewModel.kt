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
}
