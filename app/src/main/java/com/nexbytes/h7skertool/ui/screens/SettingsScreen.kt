package com.nexbytes.h7skertool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexbytes.h7skertool.ui.theme.*
import com.nexbytes.h7skertool.utils.DecodeUtils
import com.nexbytes.h7skertool.viewmodel.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppUiState,
    onChangeClientUrl: () -> Unit,
    onClearCaptures: () -> Unit,
    onClearMods: () -> Unit,
    onLogout: () -> Unit,
    onResetAll: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    // ============================================================
    // PLUGIN STATES - YAHAN DECLARE KIYE GAYE HAIN
    // ============================================================
    var selectedPluginTab by remember { mutableIntStateOf(0) }
    val pluginTabs = listOf("🔄 CONVERT", "📦 EXTRACTOR")
    
    // Conversion states
    var conversionInput by remember { mutableStateOf("") }
    var conversionOutput by remember { mutableStateOf("") }
    var selectedConversion by remember { mutableIntStateOf(0) }
    val conversions = listOf(
        "Hex → Base64",
        "Base64 → Hex",
        "Hex → Text",
        "Text → Hex",
        "Base64 → Text",
        "Text → Base64"
    )
    
    // Payload extractor states
    var hexDumpInput by remember { mutableStateOf("") }
    var extractedPayload by remember { mutableStateOf("") }
    
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        Modifier.fillMaxSize().background(DeepBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ============================================================
        // PLUGINS SECTION
        // ============================================================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBlack),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricBlue.copy(0.2f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🔌", fontSize = 16.sp)
                            Text(
                                "PLUGINS",
                                color = ElectricBlue,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Badge(containerColor = ElectricBlue.copy(0.1f)) {
                                Text("2", color = ElectricBlue, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Text(
                            "v1.0",
                            color = TextDim,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Plugin Tabs
                    ScrollableTabRow(
                        selectedTabIndex = selectedPluginTab,
                        containerColor = ElevatedBlack,
                        contentColor = ElectricBlue,
                        edgePadding = 0.dp,
                        indicator = { tp ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tp[selectedPluginTab]),
                                color = ElectricBlue,
                                height = 2.dp
                            )
                        },
                        divider = {}
                    ) {
                        pluginTabs.forEachIndexed { i, title ->
                            Tab(
                                selected = selectedPluginTab == i,
                                onClick = { selectedPluginTab = i },
                                text = {
                                    Text(
                                        title,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (selectedPluginTab == i) ElectricBlue else TextSecondary
                                    )
                                }
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Plugin Content
                    when (selectedPluginTab) {
                        0 -> ConversionPluginContent(
                            inputText = conversionInput,
                            onInputChange = { conversionInput = it },
                            outputText = conversionOutput,
                            selectedConversion = selectedConversion,
                            onConversionChange = { selectedConversion = it },
                            conversions = conversions,
                            onConvert = {
                                conversionOutput = when (selectedConversion) {
                                    0 -> DecodeUtils.hexToBase64(conversionInput)
                                    1 -> DecodeUtils.base64ToHex(conversionInput)
                                    2 -> DecodeUtils.hexToText(conversionInput)
                                    3 -> DecodeUtils.textToHex(conversionInput)
                                    4 -> DecodeUtils.base64ToText(conversionInput)
                                    5 -> DecodeUtils.textToBase64(conversionInput)
                                    else -> "Invalid"
                                }
                            },
                            onClear = { conversionInput = ""; conversionOutput = "" },
                            onCopy = { clipboard.setText(AnnotatedString(conversionOutput)) }
                        )
                        1 -> PayloadExtractorPluginContent(
                            hexDumpInput = hexDumpInput,
                            onHexDumpChange = { hexDumpInput = it },
                            extractedPayload = extractedPayload,
                            onExtract = {
                                extractedPayload = DecodeUtils.extractPayloadFromHexDump(hexDumpInput)
                            },
                            onClear = { hexDumpInput = ""; extractedPayload = "" },
                            onCopy = { clipboard.setText(AnnotatedString(extractedPayload)) }
                        )
                    }
                }
            }
        }

        // ============================================================
        // SETTINGS SECTION
        // ============================================================
        item {
            Text("⚙️ SETTINGS", color = NeonGreen, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }

        // Account
        item {
            SettingsSection("ACCOUNT") {
                SettingsInfoRow(Icons.Default.Person, "User", state.username.ifEmpty { "unknown" }, NeonGreen)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsInfoRow(Icons.Default.VerifiedUser, "Status",
                    if (state.isVerified) "Verified ✓" else "Not verified", if (state.isVerified) SuccessGreen else AlertRed)
            }
        }

        // Proxy config
        item {
            SettingsSection("PROXY CONFIGURATION") {
                SettingsInfoRow(Icons.Default.Http, "Client URL", state.clientUrl.ifEmpty { "(not set)" }, ElectricBlue)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsActionRow(Icons.Default.Edit, "Change Client URL", ElectricBlue, onChangeClientUrl)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsInfoRow(Icons.Default.Router, "Proxy Address", "127.0.0.1:8080", TextSecondary)
            }
        }

        // Capture stats
        item {
            SettingsSection("CAPTURE") {
                SettingsInfoRow(Icons.Default.Api, "Captured Requests", "${state.requests.size}", NeonGreen)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsInfoRow(Icons.Default.PlayArrow, "Status",
                    if (state.isCapturing) "Active" else "Idle",
                    if (state.isCapturing) SuccessGreen else TextSecondary)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsActionRow(Icons.Default.DeleteSweep, "Clear All Captures", Amber, onClearCaptures)
            }
        }

        // Saved modifications
        if (state.savedMods.isNotEmpty()) {
            item {
                SettingsSection("SAVED MODIFICATIONS (${state.savedMods.size})") {
                    state.savedMods.entries.forEachIndexed { idx, (ep, body) ->
                        if (idx > 0) Divider(color = DividerGray, thickness = 0.5.dp)
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            Arrangement.SpaceBetween, Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ep, color = PurpleAccent, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium)
                                Text(body.take(60) + if (body.length > 60) "…" else "",
                                    color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Divider(color = DividerGray, thickness = 0.5.dp)
                    SettingsActionRow(Icons.Default.DeleteForever, "Clear All Modifications", AlertRed, onClearMods)
                }
            }
        }

        // Danger zone
        item {
            SettingsSection("DANGER ZONE") {
                SettingsActionRow(Icons.Default.Logout, "Log Out", Amber) { showLogoutDialog = true }
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsActionRow(Icons.Default.RestartAlt, "Factory Reset", AlertRed) { showResetDialog = true }
            }
        }

        // App info
        item {
            SettingsSection("ABOUT") {
                SettingsInfoRow(Icons.Default.Apps, "App Name", "H7skER TOOL", NeonGreen)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsInfoRow(Icons.Default.Info, "Version", "2.0 (build 200)", TextSecondary)
                Divider(color = DividerGray, thickness = 0.5.dp)
                SettingsInfoRow(Icons.Default.Domain, "Package", "com.nexbytes.h7skertool", TextDim)
            }
        }
    }

    // Logout dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = ElevatedBlack,
            title = { Text("Log Out?", color = TextBright, fontWeight = FontWeight.Bold) },
            text = { Text("Your session will end. You'll need to verify your password again.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("Log Out", color = Amber, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    // Reset dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = ElevatedBlack,
            title = { Text("Factory Reset?", color = AlertRed, fontWeight = FontWeight.Bold) },
            text = { Text("This will clear ALL data including your session, client URL, and all captures. This cannot be undone.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; onResetAll() }) {
                    Text("RESET EVERYTHING", color = AlertRed, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

// ============================================================
// SETTINGS SECTION COMPONENTS
// ============================================================

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = TextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(CardBlack).border(1.dp, DividerGray, RoundedCornerShape(14.dp)),
            content = content
        )
    }
}

@Composable
private fun SettingsInfoRow(icon: ImageVector, label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Text(label, color = TextPrimary, fontSize = 13.sp)
        }
        Text(value, color = valueColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
    }
}

@Composable
private fun SettingsActionRow(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.Start, Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ============================================================
// CONVERSION PLUGIN CONTENT
// ============================================================

@Composable
private fun ConversionPluginContent(
    inputText: String,
    onInputChange: (String) -> Unit,
    outputText: String,
    selectedConversion: Int,
    onConversionChange: (Int) -> Unit,
    conversions: List<String>,
    onConvert: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Conversion type dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = conversions[selectedConversion],
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricBlue,
                    unfocusedBorderColor = DividerGray,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                conversions.forEachIndexed { index, item ->
                    DropdownMenuItem(
                        text = { 
                            Text(
                                item, 
                                fontFamily = FontFamily.Monospace, 
                                fontSize = 11.sp,
                                color = if (index == selectedConversion) ElectricBlue else TextPrimary
                            ) 
                        },
                        onClick = {
                            onConversionChange(index)
                            expanded = false
                        }
                    )
                }
            }
        }
        
        // Input
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            label = { Text("Input", color = TextSecondary, fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = DividerGray,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        )
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onConvert,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.CompareArrows, null, modifier = Modifier.size(14.dp), tint = Color.Black)
                Spacer(Modifier.width(4.dp))
                Text("CONVERT", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Black)
            }
            
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(36.dp)
                    .background(CardBlack, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Clear, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
        
        // Output
        OutlinedTextField(
            value = outputText,
            onValueChange = {},
            label = { 
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Output", color = TextSecondary, fontSize = 10.sp)
                    if (outputText.isNotEmpty() && !outputText.startsWith("Invalid")) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.CopyAll, null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (outputText.startsWith("Invalid")) AlertRed else NeonGreen,
                unfocusedBorderColor = DividerGray,
                focusedTextColor = if (outputText.startsWith("Invalid")) AlertRed else NeonGreen,
                unfocusedTextColor = if (outputText.startsWith("Invalid")) AlertRed else TextPrimary
            ),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            readOnly = true
        )
    }
}

// ============================================================
// PAYLOAD EXTRACTOR PLUGIN CONTENT
// ============================================================

@Composable
private fun PayloadExtractorPluginContent(
    hexDumpInput: String,
    onHexDumpChange: (String) -> Unit,
    extractedPayload: String,
    onExtract: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Paste Hex Dump (from Wireshark/PCAPdroid)",
            color = TextSecondary.copy(0.6f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
        
        OutlinedTextField(
            value = hexDumpInput,
            onValueChange = onHexDumpChange,
            label = { Text("Hex Dump", color = TextSecondary, fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = DividerGray,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onExtract,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(14.dp), tint = Color.Black)
                Spacer(Modifier.width(4.dp))
                Text("EXTRACT", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Black)
            }
            
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(36.dp)
                    .background(CardBlack, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Clear, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
        
        if (extractedPayload.isNotEmpty()) {
            OutlinedTextField(
                value = extractedPayload,
                onValueChange = {},
                label = { 
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Extracted Payload", color = TextSecondary, fontSize = 10.sp)
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.CopyAll, null, tint = NeonGreen, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonGreen,
                    unfocusedBorderColor = DividerGray,
                    focusedTextColor = NeonGreen,
                    unfocusedTextColor = NeonGreen
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                ),
                readOnly = true
            )
        }
    }
}
