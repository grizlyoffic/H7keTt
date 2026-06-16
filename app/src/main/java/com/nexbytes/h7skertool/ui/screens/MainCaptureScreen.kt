package com.nexbytes.h7skertool.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.ui.components.FloatingDecodeOverlay
import com.nexbytes.h7skertool.ui.components.LogViewer
import com.nexbytes.h7skertool.ui.components.UnifiedCaptureItem
import com.nexbytes.h7skertool.ui.theme.*
import com.nexbytes.h7skertool.utils.ExportUtils
import com.nexbytes.h7skertool.viewmodel.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCaptureScreen(
    state: AppUiState,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onSearch: (String) -> Unit,
    onFilterEndpoint: (String?) -> Unit,
    onClearCaptures: () -> Unit,
    onSaveMod: (String, String) -> Unit,
    onClearLogs: () -> Unit,
    onNavigateToDetail: (CapturedRequest) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("CAPTURE", "LOGS")
    var decodeTarget by remember { mutableStateOf<CapturedRequest?>(null) }
    var snack by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.size(8.dp).clip(CircleShape)
                                    .background(if (state.isCapturing) NeonGreen else TextDim)
                            )
                            Text("H7skER TOOL", color = NeonGreen, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 1.sp)
                            Text("v2.0", color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    },
                    actions = {
                        // Request count
                        if (state.requests.isNotEmpty()) {
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(NeonGreen.copy(0.1f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("${state.filteredRequests.size}", color = NeonGreen, fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        IconButton(onClick = onClearCaptures) {
                            Icon(Icons.Default.DeleteSweep, null, tint = Amber)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBlack)
                )

                // Search bar
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(46.dp),
                    placeholder = { Text("Search endpoints, bodies…", color = TextDim, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearch("") }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Clear, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen, unfocusedBorderColor = DividerGray,
                        focusedTextColor = TextBright, unfocusedTextColor = TextPrimary, cursorColor = NeonGreen
                    ),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    shape = RoundedCornerShape(10.dp)
                )

                // Endpoint filter chips
                if (state.allEndpoints.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().background(CardBlack),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = state.endpointFilter == null,
                                onClick = { onFilterEndpoint(null) },
                                label = { Text("ALL", fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonGreen.copy(0.15f),
                                    selectedLabelColor = NeonGreen,
                                    containerColor = ElevatedBlack, labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(enabled = true,
                                    selected = state.endpointFilter == null,
                                    selectedBorderColor = NeonGreen.copy(0.4f), borderColor = DividerGray)
                            )
                        }
                        items(state.allEndpoints) { ep ->
                            FilterChip(
                                selected = state.endpointFilter == ep,
                                onClick = { onFilterEndpoint(if (state.endpointFilter == ep) null else ep) },
                                label = { Text(ep.trimStart('/'), fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricBlue.copy(0.15f),
                                    selectedLabelColor = ElectricBlue,
                                    containerColor = ElevatedBlack, labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(enabled = true,
                                    selected = state.endpointFilter == ep,
                                    selectedBorderColor = ElectricBlue.copy(0.4f), borderColor = DividerGray)
                            )
                        }
                    }
                }

                // Capture toggle strip
                CaptureStrip(state.isCapturing, onStartCapture, onStopCapture, state.clientUrl)

                // Tabs
                TabRow(
                    selectedTabIndex = tab, containerColor = CardBlack, contentColor = NeonGreen,
                    indicator = { tp -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tp[tab]), color = NeonGreen) }
                ) {
                    tabs.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(t, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                    if (i == 0 && state.filteredRequests.isNotEmpty())
                                        Badge(containerColor = NeonGreen.copy(0.1f)) {
                                            Text("${state.filteredRequests.size}", color = NeonGreen, fontSize = 9.sp)
                                        }
                                    if (i == 1 && state.logs.isNotEmpty())
                                        Badge(containerColor = ElectricBlue.copy(0.1f)) {
                                            Text("${state.logs.size}", color = ElectricBlue, fontSize = 9.sp)
                                        }
                                }
                            })
                    }
                }
            }
        },
        containerColor = DeepBlack
    ) { pv ->
        Box(Modifier.fillMaxSize().padding(pv)) {
            when (tab) {
                0 -> CaptureList(
                    requests = state.filteredRequests,
                    responses = state.responses,
                    onTap = onNavigateToDetail,
                    onCopyRequest = { req ->
                        clipboard.setText(AnnotatedString(ExportUtils.buildRequestText(req)))
                        snack = "Request copied!"
                    },
                    onCopyResponse = { req ->
                        val res = state.responses[req.id]
                        if (res != null) {
                            clipboard.setText(AnnotatedString(ExportUtils.buildResponseText(req, res)))
                            snack = "Response copied!"
                        }
                    },
                    onOpenDecode = { decodeTarget = it },
                    onSaveMod = { req -> onSaveMod(req.endpoint, req.bodyText ?: ""); snack = "Mod saved!" }
                )
                1 -> LogViewer(logs = state.logs, onClear = onClearLogs)
            }

            // File write status overlay
            if (state.fileWriteStatus.isNotEmpty() && state.isCapturing) {
                FileWriteStatus(state.fileWriteStatus, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }

    // Floating decode overlay
    decodeTarget?.let { req ->
        FloatingDecodeOverlay(
            request = req,
            response = state.responses[req.id],
            onDismiss = { decodeTarget = null },
            onSaveMod = { body -> onSaveMod(req.endpoint, body); decodeTarget = null }
        )
    }

    snack?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(1500); snack = null }
    }
}

@Composable
private fun CaptureList(
    requests: List<CapturedRequest>,
    responses: Map<String, CapturedResponse>,
    onTap: (CapturedRequest) -> Unit,
    onCopyRequest: (CapturedRequest) -> Unit,
    onCopyResponse: (CapturedRequest) -> Unit,
    onOpenDecode: (CapturedRequest) -> Unit,
    onSaveMod: (CapturedRequest) -> Unit
) {
    if (requests.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.SignalWifiOff, null, tint = TextDim, modifier = Modifier.size(48.dp))
                Text("No requests captured", color = TextDim, fontSize = 14.sp)
                Text("Start capture to intercept FreeFire API traffic", color = TextDim.copy(0.6f), fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(requests, key = { it.id }) { req ->
                UnifiedCaptureItem(
                    request = req,
                    response = responses[req.id],
                    onTap = { onTap(req) },
                    onCopyRequest = { onCopyRequest(req) },
                    onCopyResponse = { onCopyResponse(req) },
                    onOpenDecode = { onOpenDecode(req) },
                    onSaveMod = { onSaveMod(req) }
                )
            }
        }
    }
}

@Composable
private fun CaptureStrip(capturing: Boolean, onStart: () -> Unit, onStop: () -> Unit, clientUrl: String) {
    Row(
        Modifier.fillMaxWidth().background(ElevatedBlack).padding(horizontal = 12.dp, vertical = 8.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically
    ) {
        Column {
            Text(if (capturing) "● CAPTURING" else "○ IDLE", color = if (capturing) NeonGreen else TextSecondary,
                fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(clientUrl.take(40), color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        if (capturing) {
            OutlinedButton(
                onClick = onStop, modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(0.5f))
            ) {
                Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onStart, modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Start", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FileWriteStatus(lines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(ElevatedBlack.copy(0.9f))
            .border(1.dp, DividerGray, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("FILE STATUS", color = TextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
        lines.forEach { line ->
            val color = if (line.startsWith("✓")) NeonGreen else AlertRed
            Text(line, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
