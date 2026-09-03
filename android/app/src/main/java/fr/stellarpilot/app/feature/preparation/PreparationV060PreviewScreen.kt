package fr.stellarpilot.app.feature.preparation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.stellarpilot.app.ui.theme.StellarBackground
import fr.stellarpilot.app.ui.theme.StellarBorder
import fr.stellarpilot.app.ui.theme.StellarGreen
import fr.stellarpilot.app.ui.theme.StellarMuted
import fr.stellarpilot.app.ui.theme.StellarOrange
import fr.stellarpilot.app.ui.theme.StellarSurface
import fr.stellarpilot.app.ui.theme.StellarSurfaceRaised
import fr.stellarpilot.app.ui.theme.StellarText

private val previewSteps = listOf(
    "Connexion",
    "Moteurs",
    "Astrométrie",
    "Étoile",
    "Bahtinov",
    "Prêt"
)

@Composable
fun PreparationV060PreviewScreen(
    onOpenSky: () -> Unit
) {
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var selectedStar by rememberSaveable { mutableStateOf("Véga") }
    var exposure by rememberSaveable { mutableStateOf(0.50) }
    var referenceCount by rememberSaveable { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StellarBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "MODE TEST INTERFACE",
                style = MaterialTheme.typography.labelLarge,
                color = StellarOrange,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Assistant StellarPilot 0.6 • aperçu sans matériel",
                style = MaterialTheme.typography.headlineMedium,
                color = StellarText,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = StellarSurfaceRaised),
                border = BorderStroke(1.dp, StellarOrange)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Aucune commande moteur, caméra, GOTO, SYNC ou capture n'est envoyée.",
                        color = StellarOrange,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ce mode sert uniquement à vérifier aujourd'hui le parcours, les boutons, les textes et la lisibilité sur la tablette.",
                        color = StellarText
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Étape ${currentStep + 1}/${previewSteps.size} • ${previewSteps[currentStep]}",
                color = StellarMuted
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = (currentStep + 1).toFloat() / previewSteps.size.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = StellarOrange,
                trackColor = StellarSurfaceRaised
            )
            Spacer(Modifier.height(20.dp))

            when (currentStep) {
                0 -> PreviewConnectionStep()
                1 -> PreviewMotorsStep()
                2 -> PreviewAstrometryStep()
                3 -> PreviewStarStep(
                    selectedStar = selectedStar,
                    onSelect = { selectedStar = it }
                )
                4 -> PreviewBahtinovStep(
                    star = selectedStar,
                    exposure = exposure,
                    referenceCount = referenceCount,
                    onExposure = { exposure = it },
                    onAddReference = { referenceCount += 1 }
                )
                else -> PreviewReadyStep(
                    referenceCount = referenceCount,
                    onOpenSky = onOpenSky
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { if (currentStep > 0) currentStep -= 1 },
                    enabled = currentStep > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Retour")
                }
                Button(
                    onClick = {
                        if (currentStep < previewSteps.lastIndex) {
                            currentStep += 1
                        }
                    },
                    enabled = currentStep < previewSteps.lastIndex,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StellarOrange,
                        contentColor = StellarBackground
                    )
                ) {
                    Text(
                        if (currentStep < previewSteps.lastIndex) "Étape suivante" else "Terminé",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewConnectionStep() {
    PreviewCard(
        title = "Connexion matériel",
        subtitle = "Aperçu de l'écran réel de préparation"
    ) {
        PreviewInfo("Serveur", "http://192.168.1.46:8000/")
        PreviewInfo("Monture", "ready")
        PreviewInfo("Caméra", "ready")
        PreviewInfo("GPS", "fix")
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Actualiser la connexion") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Tester la motorisation") }
    }
}

@Composable
private fun PreviewMotorsStep() {
    PreviewCard(
        title = "Diagnostic moteurs EQ",
        subtitle = "RA et DEC validés pendant les tests terrain"
    ) {
        PreviewInfo("Monture", "LX200 OnStep")
        PreviewInfo("État", "tracking")
        PreviewInfo("RA", "5,94639 h")
        PreviewInfo("DEC", "+85,0494°")
        PreviewInfo("Tracking", "sidereal")
        PreviewInfo("INDI", "Ok")
        PreviewInfo("Axe RA", "✓ validé")
        PreviewInfo("Axe DEC", "✓ validé")
        Spacer(Modifier.height(10.dp))
        Text(
            "✓ Test RA +0,45° validé physiquement et logiciellement",
            color = StellarGreen,
            fontWeight = FontWeight.Bold
        )
        Text(
            "✓ Test DEC −0,30° validé physiquement et logiciellement",
            color = StellarGreen,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        listOf("Tester moteur RA +", "Tester moteur RA −", "Tester moteur DEC +", "Tester moteur DEC −").forEach { label ->
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) { Text("$label • désactivé en test UI") }
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun PreviewAstrometryStep() {
    PreviewCard(
        title = "Première astrométrie",
        subtitle = "Écran testable de jour, solve réel réservé au ciel"
    ) {
        Text(
            "La caméra est actuellement capuchonnée : l'aperçu simule donc l'état de l'écran sans lancer de capture.",
            color = StellarText
        )
        Spacer(Modifier.height(12.dp))
        PreviewInfo("Pose initiale", "4,0 s")
        PreviewInfo("Qualité", "Test interface")
        PreviewInfo("Étoiles", "—")
        PreviewInfo("Solve", "simulé / non exécuté")
        PreviewInfo("SYNC OnStep", "simulé / non exécuté")
        PreviewInfo("Échelle cible", "≈ 1,2183 ″/px")
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Capturer à 4 s, résoudre et synchroniser • désactivé en test UI")
        }
    }
}

@Composable
private fun PreviewStarStep(
    selectedStar: String,
    onSelect: (String) -> Unit
) {
    val stars = listOf(
        "Véga" to "mag 0,03 • cible de démonstration",
        "Deneb" to "mag 1,25 • cible de démonstration",
        "Altaïr" to "mag 0,77 • cible de démonstration"
    )

    PreviewCard(
        title = "Étoile de mise au point",
        subtitle = "Sélection UI sans GOTO réel"
    ) {
        stars.forEach { (name, detail) ->
            if (name == selectedStar) {
                Button(
                    onClick = { onSelect(name) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StellarOrange,
                        contentColor = StellarBackground
                    )
                ) { Text("★ $name • $detail") }
            } else {
                OutlinedButton(
                    onClick = { onSelect(name) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("☆ $name • $detail") }
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        PreviewInfo("Sélection", selectedStar)
        PreviewInfo("Tracking", "simulé")
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Pointer $selectedStar et suivre • désactivé en test UI") }
    }
}

@Composable
private fun PreviewBahtinovStep(
    star: String,
    exposure: Double,
    referenceCount: Int,
    onExposure: (Double) -> Unit,
    onAddReference: () -> Unit
) {
    PreviewCard(
        title = "Calibration Bahtinov",
        subtitle = "$star • simulation interactive"
    ) {
        PreviewInfo("Monture", "tracking simulé")
        PreviewInfo("Références test", referenceCount.toString())
        Spacer(Modifier.height(10.dp))
        Text("Temps de pose", color = StellarMuted)
        Spacer(Modifier.height(6.dp))
        listOf(0.10, 0.25, 0.50, 1.0, 2.0).forEach { value ->
            val selected = kotlin.math.abs(exposure - value) < 0.001
            if (selected) {
                Button(
                    onClick = { onExposure(value) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(previewExposure(value)) }
            } else {
                OutlinedButton(
                    onClick = { onExposure(value) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(previewExposure(value)) }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Étiquettes Bahtinov — simulation locale",
            color = StellarOrange,
            fontWeight = FontWeight.Bold
        )
        listOf("Très mauvais", "Mauvais", "Moyen", "Bon", "Optimum", "Mauvais autre côté").forEach { label ->
            Button(
                onClick = onAddReference,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (label == "Optimum") StellarGreen else StellarOrange,
                    contentColor = StellarBackground
                )
            ) { Text(label, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun PreviewReadyStep(
    referenceCount: Int,
    onOpenSky: () -> Unit
) {
    PreviewCard(
        title = "Tests préparatoires terminés",
        subtitle = "Aperçu du compte rendu final"
    ) {
        Text(
            "✓ Axe RA mesuré\n" +
                "✓ Axe DEC mesuré\n" +
                "○ Astrométrie + SYNC à réaliser ce soir\n" +
                "○ GOTO étoile + tracking à réaliser ce soir\n" +
                "✓ $referenceCount interactions Bahtinov simulées",
            color = StellarGreen
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Les coches simulées de cet écran ne valident rien dans le mode réel.",
            color = StellarMuted
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenSky,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StellarOrange,
                contentColor = StellarBackground
            )
        ) { Text("Ouvrir Ciel", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PreviewCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = StellarSurface),
        border = BorderStroke(1.dp, StellarBorder)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                title,
                color = StellarText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = StellarMuted)
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

@Composable
private fun PreviewInfo(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = StellarMuted)
        Text(value, color = StellarText, fontWeight = FontWeight.SemiBold)
    }
}

private fun previewExposure(seconds: Double): String =
    if (seconds < 1.0) {
        "${(seconds * 1000).toInt()} ms"
    } else {
        String.format(java.util.Locale.FRANCE, "%.1f s", seconds)
    }
