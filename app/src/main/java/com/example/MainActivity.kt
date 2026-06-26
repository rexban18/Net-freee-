package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.data.repository.AuthRepository
import com.example.ui.navigation.AppNavigation
import com.example.ui.navigation.Screen
import com.example.ui.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject
  lateinit var authRepository: AuthRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    val startDestination = if (authRepository.isLoggedIn()) {
      Screen.Home
    } else {
      Screen.Login
    }

    setContent {
      MyApplicationTheme {
        val navController = rememberNavController()
        AppNavigation(
          navController = navController,
          startDestination = startDestination
        )
      }
    }
  }
}
