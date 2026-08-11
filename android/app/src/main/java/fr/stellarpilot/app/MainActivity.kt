package fr.stellarpilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                StellarPilotScreen()
            }
        }
    }
}

@Composable
private fun StellarPilotScreen() {
    var status by remember { mutableStateOf("Non connecté") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("StellarPilot POC")
        Text(status, modifier = Modifier.padding(vertical = 16.dp))
        Button(onClick = {
            scope.launch {
                status = checkServer("http://10.0.2.2:8000/status")
            }
        }) {
            Text("Tester le serveur")
        }
    }
}

private suspend fun checkServer(url: String): String = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) "Serveur StellarPilot connecté" else "Erreur HTTP ${response.code}"
        }
    } catch (e: Exception) {
        "Connexion impossible : ${e.message ?: "erreur inconnue"}"
    }
}
