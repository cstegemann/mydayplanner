package com.example.mydayplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.mydayplanner.ui.history.HistoryScreen
import com.example.mydayplanner.ui.home.HomeScreen
import com.example.mydayplanner.ui.theme.MydayplannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MydayplannerTheme {
                var showHistory by remember { mutableStateOf(false) }
                if (showHistory) {
                    HistoryScreen(onBack = { showHistory = false })
                } else {
                    HomeScreen(onOpenHistory = { showHistory = true })
                }
            }
        }
    }
    /*
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MydayplannerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }*/
}


/*
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MydayplannerTheme {
        Greeting("Android")
    }
}
 */
