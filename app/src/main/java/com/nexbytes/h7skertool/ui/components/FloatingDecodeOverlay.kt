package com.nexbytes.h7skertool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.ui.theme.*
import com.nexbytes.h7skertool.utils.DecodeUtils

@Composable
fun FloatingDecodeOverlay(
    request: CapturedRequest,
    response: CapturedResponse?,
    onDismiss: () -> Unit,
    onSaveMod: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var tabIdx by remember { mutableIntStateOf(0) }
    val tabs = listOf("REQUEST", "RESPONSE")
    var viewMode by remember { mutableIntStateOf(0) }
    val viewModes = listOf("TEXT", "HEX", "DECODED")
    var snackMsg by remember { mutableStateOf<String?>(null) }
    var editBody by remember { mutableStateOf("") }
    var editMode by remember { mutableStateOf(false) }

    val currentBytes = if (tabIdx == 0) request.body else response?.body
    val currentText = if (tabIdx == 0) request.bodyText else response?.bodyText
    val currentHex = if (tabIdx == 0) request.bodyHex else response?.bodyHex
    val decoded: String = remember(tabIdx, currentBytes) {
        currentBytes?.let { DecodeUtils.prettyPrintJson(String(it, Charsets.UTF_8)) }
            ?: currentText ?: "(empty)"
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(18.dp))
                .background(SheetBlack)
                .border(1.dp, DividerGray, RoundedCornerShape(18.dp))
        ) {
            // Handle
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(DividerGray))
            }

            // Header
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Column {
                    Text("DECODE WINDOW", color = NeonGreen, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(request.endpoint, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = {
                        val txt = when (viewMode) {
                            0 -> currentText ?: ""; 1 -> currentHex ?: ""; else -> decoded
                        }
                        clipboard.setText(AnnotatedString(txt))
                        snackMsg = "Copied!"
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ContentCopy, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Divider(color = DividerGray, thickness = 0.5.dp)

            // Req/Res tab
            TabRow(
                selectedTabIndex = tabIdx,
                containerColor = SheetBlack,
                contentColor = NeonGreen,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[tabIdx]),
                        color = NeonGreen
                    )
                }
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = tabIdx == i,
                        onClick = { tabIdx = i; editMode = false },
                        text = { Text(t, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                    )
                }
            }

            // View mode chips
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModes.forEachIndexed { i, m ->
                    FilterChip(
                        selected = viewMode == i,
                        onClick = { viewMode = i; editMode = false },
                        label = { Text(m, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonGreen.copy(0.15f),
                            selectedLabelColor = NeonGreen,
                            containerColor = ElevatedBlack,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = viewMode == i,
                            selectedBorderColor = NeonGreen.copy(0.4f),
                            borderColor = DividerGray
                        )
                    )
                }
                Spacer(Modifier.weight(1f))
                if (tabIdx == 0) {
                    IconButton(onClick = {
                        editBody = currentText ?: ""; editMode = !editMode
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null,
                            tint = if (editMode) PurpleAccent else TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Divider(color = DividerGray, thickness = 0.5.dp)

            // Content area
            val scrollState = rememberScrollState()
            if (editMode && tabIdx == 0) {
                OutlinedTextField(
                    value = editBody, onValueChange = { editBody = it },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurpleAccent, unfocusedBorderColor = DividerGray,
                        focusedTextColor = TextBright, unfocusedTextColor = TextPrimary, cursorColor = PurpleAccent
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            } else {
                val displayText = when (viewMode) {
                    1 -> currentHex ?: "(no hex data)"
                    2 -> decoded
                    else -> currentText ?: "(empty body)"
                }
                val textColor = when (viewMode) {
                    0 -> if (tabIdx == 0) NeonGreen.copy(0.9f) else ElectricBlue.copy(0.9f)
                    1 -> Amber.copy(0.9f)
                    else -> TextPrimary
                }
                Box(
                    Modifier.weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(scrollState)
                        .padding(12.dp)
                ) {
                    Text(displayText, color = textColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
                }
            }

            Divider(color = DividerGray, thickness = 0.5.dp)

            // Action bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (editMode && tabIdx == 0) {
                    Button(
                        onClick = { onSaveMod(editBody); snackMsg = "Modification saved!" },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                    ) {
                        Icon(Icons.Default.Save, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Mod", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                OutlinedButton(
                    onClick = onDismiss, modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
                ) { Text("Close", fontSize = 13.sp) }
            }

            snackMsg?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(1500)
                    snackMsg = null
                }
                Box(
                    Modifier.fillMaxWidth().background(NeonGreen.copy(0.1f)).padding(vertical = 6.dp),
                    Alignment.Center
                ) { Text(msg, color = NeonGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}
