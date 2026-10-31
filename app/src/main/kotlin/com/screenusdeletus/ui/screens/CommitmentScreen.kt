package com.screenusdeletus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.screenusdeletus.ScreenusDeletusApp
import com.screenusdeletus.core.CommitmentDevice
import com.screenusdeletus.core.CommitmentLedger
import com.screenusdeletus.security.AuthManager
import com.screenusdeletus.ui.components.PinField
import com.screenusdeletus.ui.components.SectionCard
import com.screenusdeletus.util.Permissions

@Composable
fun CommitmentScreen() {
    val context = LocalContext.current
    val c = ScreenusDeletusApp.container(context)
    var commitment by remember { mutableStateOf(c.commitment.commitment) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Commitment pledge", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pledge an amount to a charity. Every slip (going over a limit, pushing past a pause, an emergency unlock) " +
                "adds a penalty that grows within the week. Screenus Deletus never moves money: it keeps the ledger and " +
                "reminds you — and your guardian — until you've donated what you owe.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val current = commitment
        if (current == null || !current.active) {
            SetupForm { commitment = it; c.commitment.commitment = it }
        } else {
            Ledger(current, onSettle = { amt -> c.commitment.settle(amt); commitment = c.commitment.commitment },
                onOpenCharity = { Permissions.url(context, current.charityUrl) },
                onEnd = { commitment = current.copy(active = false); c.commitment.commitment = commitment },
                guardianPinRequired = c.auth.hasGuardianPin,
                verifyGuardian = { c.auth.verifyGuardianPin(it) == AuthManager.PinResult.Ok })
        }
    }
}

@Composable
private fun SetupForm(onCreate: (CommitmentDevice) -> Unit) {
    var pledge by remember { mutableStateOf("50") }
    var base by remember { mutableStateOf("2") }
    var mult by remember { mutableStateOf("1.5") }
    var currency by remember { mutableStateOf("USD") }
    var charity by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    val number = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    SectionCard("Set up") {
        OutlinedTextField(pledge, { pledge = it }, Modifier.fillMaxWidth(), label = { Text("Total pledge") }, keyboardOptions = number, singleLine = true)
        OutlinedTextField(currency, { currency = it.uppercase().take(3) }, Modifier.fillMaxWidth(), label = { Text("Currency") }, singleLine = true)
        OutlinedTextField(base, { base = it }, Modifier.fillMaxWidth(), label = { Text("First penalty each week") }, keyboardOptions = number, singleLine = true)
        OutlinedTextField(mult, { mult = it }, Modifier.fillMaxWidth(), label = { Text("Multiplier per further slip") }, keyboardOptions = number, singleLine = true)
        OutlinedTextField(charity, { charity = it }, Modifier.fillMaxWidth(), label = { Text("Charity") }, singleLine = true)
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Donation link") }, singleLine = true)
        val valid = pledge.toDoubleOrNull()?.let { it > 0 } == true && base.toDoubleOrNull()?.let { it > 0 } == true &&
            mult.toDoubleOrNull()?.let { it >= 1 } == true && charity.isNotBlank()
        Button(enabled = valid, onClick = {
            onCreate(CommitmentDevice(pledge.toDouble(), currency, base.toDouble(), mult.toDouble(), charity.trim(), url.trim()))
        }) { Text("Make the pledge") }
    }
}

@Composable
private fun Ledger(
    c: CommitmentDevice,
    onSettle: (Double) -> Unit,
    onOpenCharity: () -> Unit,
    onEnd: () -> Unit,
    guardianPinRequired: Boolean,
    verifyGuardian: (String) -> Boolean,
) {
    var amount by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    SectionCard("Ledger · ${c.charityName}") {
        Text("Outstanding: %.2f %s".format(c.outstanding, c.currency), style = MaterialTheme.typography.headlineSmall)
        Text("Owed in total %.2f · donated %.2f · pledge left %.2f".format(c.totalOwed, c.totalSettled, c.remainingPledge))
        Text("Slips this week: ${c.violationsThisWeek} · next penalty %.2f".format(CommitmentLedger.nextPenalty(c)))
        Text("Slips in total: ${c.totalViolations}", style = MaterialTheme.typography.bodySmall)
    }
    if (c.outstanding > 0) {
        SectionCard("Settle up") {
            Text("Donate first, then record it here.")
            OutlinedButton(onClick = onOpenCharity) { Text("Open donation page") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, Modifier.weight(1f), label = { Text("Amount donated") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Button(enabled = (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = { onSettle(amount.toDouble()); amount = "" }) { Text("Record") }
            }
        }
    }
    SectionCard("End the pledge") {
        when {
            c.outstanding > 0 -> Text("Settle what you owe before ending the pledge.")
            guardianPinRequired -> {
                PinField(pin, { pin = it; error = null }, "Guardian PIN", isError = error != null)
                OutlinedButton(onClick = { if (verifyGuardian(pin)) onEnd() else error = "Wrong PIN" }) { Text("End pledge") }
            }
            else -> OutlinedButton(onClick = onEnd) { Text("End pledge") }
        }
    }
}
