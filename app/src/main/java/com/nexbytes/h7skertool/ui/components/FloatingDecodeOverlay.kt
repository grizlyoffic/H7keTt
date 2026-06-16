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
    
    // ============================================================
    // REQUEST EDIT STATES
    // ============================================================
    var editRequestBody by remember { mutableStateOf(request.bodyText ?: "") }
    var editRequestMode by remember { mutableStateOf(false) }
    
    // ============================================================
    // RESPONSE EDIT STATES - NAYA ADD KIYA GAYA
    // ============================================================
    var editResponseBody by remember { mutableStateOf(response?.bodyText ?: "") }
    var editResponseMode by remember { mutableStateOf(false) }

    val currentBytes = if (tabIdx == 0) request.body else response?.body
    val currentText = if (tabIdx == 0) request.bodyText else response?.bodyText
    val currentHex = if (tabIdx == 0) request.bodyHex else response?.bodyHex
    val decoded: String = remember(tabIdx, currentBytes) {
        currentBytes?.let { 
            val str = String(it, Charsets.UTF_8)
            DecodeUtils.prettyPrintJson(str)
        } ?: currentText ?: "(empty)"
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DECODE WINDOW", color = NeonGreen, fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        // Edit mode indicator
                        if ((tabIdx == 0 && editRequestMode) || (tabIdx == 1 && editResponseMode)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(PurpleAccent)
                            )
                            Text("EDITING", color = PurpleAccent, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Text(request.endpoint, color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // ============================================================
                    // EDIT BUTTON - REQUEST AUR RESPONSE DONO KE LIYE
                    // ============================================================
                    val isEditMode = if (tabIdx == 0) editRequestMode else editResponseMode
                    val editBody = if (tabIdx == 0) editRequestBody else editResponseBody
                    
                    IconButton(
                        onClick = {
                            if (tabIdx == 0) {
                                editRequestBody = request.bodyText ?: ""
                                editRequestMode = !editRequestMode
                            } else {
                                editResponseBody = response?.bodyText ?: ""
                                editResponseMode = !editResponseMode
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isEditMode) PurpleAccent.copy(0.15f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            null,
                            tint = if (isEditMode) PurpleAccent else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            val txt = when (viewMode) {
                                0 -> currentText ?: ""
                                1 -> currentHex ?: ""
                                else -> decoded
                            }
                            clipboard.setText(AnnotatedString(txt))
                            snackMsg = "Copied!"
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
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
                        onClick = { 
                            tabIdx = i
                            // Reset edit modes when switching tabs
                            if (i != 0) editRequestMode = false
                            if (i != 1) editResponseMode = false
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(t, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                if (i == 1 && response == null) {
                                    Text("(no response)", color = TextSecondary, fontSize = 9.sp)
                                }
                                // Edit mode indicator on tab
                                if ((i == 0 && editRequestMode) || (i == 1 && editResponseMode)) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(PurpleAccent)
                                    )
                                }
                            }
                        }
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
                        onClick = { viewMode = i },
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
                
                // ============================================================
                // SAVE MOD BUTTON - REQUEST AUR RESPONSE DONO KE LIYE
                // ============================================================
                val isEditMode = if (tabIdx == 0) editRequestMode else editResponseMode
                val editBody = if (tabIdx == 0) editRequestBody else editResponseBody
                
                if (isEditMode) {
                    TextButton(
                        onClick = {
                            if (tabIdx == 0) {
                                onSaveMod(editBody)
                            } else {
                                // Response mod save - endpoint ke saath "_response" add karein
                                onSaveMod("${request.endpoint}_response", editBody)
                            }
                            snackMsg = "Mod saved!"
                            if (tabIdx == 0) editRequestMode = false else editResponseMode = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = PurpleAccent)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(14.dp))
                        Text("SAVE", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Divider(color = DividerGray, thickness = 0.5.dp)

            // ============================================================
            // CONTENT AREA - WITH EDIT SUPPORT
            // ============================================================
            val isEditMode = if (tabIdx == 0) editRequestMode else editResponseMode
            val editBody = if (tabIdx == 0) editRequestBody else editResponseBody
            val onEditChange = if (tabIdx == 0) 
                { newText: String -> editRequestBody = newText } 
            else 
                { newText: String -> editResponseBody = newText }
            
            val scrollState = rememberScrollState()
            
            if (isEditMode && (tabIdx == 0 || (tabIdx == 1 && response != null))) {
                // ============================================================
                // EDIT MODE - TextField show karein
                // ============================================================
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(12.dp)
                ) {
                    Text(
                        if (tabIdx == 0) "✏️ Editing Request Body" else "✏️ Editing Response Body",
                        color = PurpleAccent,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    OutlinedTextField(
                        value = editBody,
                        onValueChange = onEditChange,
                        modifier = Modifier.fillMaxSize(),
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurpleAccent,
                            unfocusedBorderColor = DividerGray,
                            focusedTextColor = TextBright,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PurpleAccent
                        ),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 10,
                        maxLines = 50
                    )
                }
            } else {
                // ============================================================
                // VIEW MODE - Normal display
                // ============================================================
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
                
                // Show "no response" message if response is null
                if (tabIdx == 1 && response == null) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, null, tint = WarningYellow, modifier = Modifier.size(48.dp))
                            Text("No Response", color = TextSecondary, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                            Text("Response not captured yet", color = TextSecondary.copy(0.6f), fontSize = 12.sp)
                        }
                    }
                } else {
                    Box(
                        Modifier.weight(1f)
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(scrollState)
                            .padding(12.dp)
                    ) {
                        Text(
                            displayText,
                            color = textColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Divider(color = DividerGray, thickness = 0.5.dp)

            // ============================================================
            // ACTION BAR
            // ============================================================
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ============================================================
                // SAVE MOD BUTTON - REQUEST AUR RESPONSE DONO KE LIYE
                // ============================================================
                val isEditModeBottom = if (tabIdx == 0) editRequestMode else editResponseMode
                val editBodyBottom = if (tabIdx == 0) editRequestBody else editResponseBody
                
                if (isEditModeBottom) {
                    Button(
                        onClick = {
                            if (tabIdx == 0) {
                                onSaveMod(editBodyBottom)
                            } else {
                                // Response mod save - endpoint ke saath "_response" add karein
                                onSaveMod("${request.endpoint}_response", editBodyBottom)
                            }
                            snackMsg = "Modification saved!"
                            if (tabIdx == 0) editRequestMode = false else editResponseMode = false
                        },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                    ) {
                        Icon(Icons.Default.Save, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (tabIdx == 0) "Save Request Mod" else "Save Response Mod",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(if (isEditModeBottom) 0.5f else 1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DividerGray)
                ) {
                    Text("Close", fontSize = 13.sp)
                }
            }

            // Snackbar message
            snackMsg?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(1500)
                    snackMsg = null
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(NeonGreen.copy(0.1f))
                        .padding(vertical = 6.dp),
                    Alignment.Center
                ) {
                    Text(msg, color = NeonGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
