package com.example.mydayplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mydayplanner.ui.history.HistoryScreen
import com.example.mydayplanner.ui.home.HomeScreen
import com.example.mydayplanner.ui.garmin.GarminScreen
import com.example.mydayplanner.ui.weather.WeatherScreen
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.mydayplanner.ui.theme.MydayplannerTheme
import com.example.mydayplanner.di.AppGraph
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private enum class Page(val label: String) {
    HOME("Home"), HISTORY("History"), GARMIN("Garmin"), WEATHER("Weather")
}

class MainActivity : ComponentActivity() {
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) lifecycleScope.launch { AppGraph.weatherRepository.refresh(force = true) }
    }
    override fun onResume() {
        super.onResume()
        AppGraph.garminWatchSync.onAppResumed()
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else lifecycleScope.launch { AppGraph.weatherRepository.refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MydayplannerTheme {
                var page by remember { mutableStateOf(Page.HOME) }
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val openMenu = { scope.launch { drawerState.open() }; Unit }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text("My Day Planner", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(12.dp))
                            Page.entries.forEach { destination ->
                                NavigationDrawerItem(
                                    label = { Text(destination.label) },
                                    selected = page == destination,
                                    onClick = {
                                        page = destination
                                        scope.launch { drawerState.close() }
                                    }
                                )
                            }
                        }
                    }
                ) {
                    when (page) {
                        Page.HOME -> HomeScreen(onOpenMenu = openMenu)
                        Page.HISTORY -> HistoryScreen(onOpenMenu = openMenu)
                        Page.GARMIN -> GarminScreen(onOpenMenu = openMenu)
                        Page.WEATHER -> WeatherScreen(onOpenMenu = openMenu)
                    }
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
