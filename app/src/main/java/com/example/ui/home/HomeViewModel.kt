package com.example.ui.home

import androidx.lifecycle.ViewModel
import com.example.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val userEmail: String
        get() = authRepository.getCurrentUserEmail()

    fun logout() {
        authRepository.logout()
    }
}
