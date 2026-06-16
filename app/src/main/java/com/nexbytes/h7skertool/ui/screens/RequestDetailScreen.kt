package com.nexbytes.h7skertool.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.ui.components.FloatingDecodeOverlay
import com.nexbytes.h7skertool.ui.components.MethodBadge
import com.nexbytes.h7skertool.ui.theme.*
import com.nexbytes.h7skertool.utils.ExportUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailScreen(
    request: CapturedRequest,
    response: CapturedResponse?,
    onBack: () -> Unit,
    onSaveMod: (String, String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var mainTab by remember { mutableIntStateOf(0) }
    var subTab by remember { mutableIntStateOf(0) }
    val mainTabs = listOf("REQUEST", "RESPONSE")
    val subTabs = listOf("TEXT", "HEADERS", "HEX")
    var showDecode by remember { mutableStateOf(false) }
    var snack by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MethodBadge(request.method)
                            Text(request.endpoint, color = TextBright, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                        }
                        Text(request.url, color = TextSecondary, fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextBright)
                    }
                },
                actions = {
                    IconButton(onClick = { showDecode = true }) {
                        Icon(Icons.Default.Code, null, tint = Amber)
                    }
                    IconButton(onClick = {
                        val text = ExportUtils.buildUnifiedLog(request, response)
                        val file = ExportUtils.exportAll(ctx, listOf(request), response?.let { mapOf(request.id to it) } ?: emptyMap())
                        ExportUtils.shareFile(ctx, file)
                    }) {
                        Icon(Icons.Default.Share, null, tint = ElectricBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBlack)
            )
        },
        containerColor = DeepBlack
    ) { pv ->
        Column(Modifier.fillMaxSize().padding(pv)) {
            // Main tabs
            TabRow(
                selectedTabIndex = mainTab,
                containerColor = CardBlack,
                contentColor = NeonGreen,
                indicator = { tp -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tp[mainTab]), color = NeonGreen) }
            ) {
                mainTabs.forEachIndexed { i, t ->
                    Tab(selected = mainTab == i, onClick = { mainTab = i; subTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(t, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                if (i == 1 && response != null) {
                                    val statusColor = when {
                                        response.statusCode in 200..299 -> NeonGreen
                                        response.statusCode in 400..499 -> Amber
                                        else -> AlertRed
                                    }
                                    Badge(containerColor = statusColor.copy(0.15f)) {
                                        Text("${response.statusCode}", color = statusColor, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Sub-tabs
            ScrollableTabRow(
                selectedTabIndex = subTab,
                containerColor = ElevatedBlack,
                contentColor = ElectricBlue,
                edgePadding = 0.dp,
                indicator = { tp -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tp[subTab]), color = ElectricBlue, height = 1.5.dp) },
                divider = {}
            ) {
                subTabs.forEachIndexed { i, t ->
                    Tab(selected = subTab == i, onClick = { subTab = i },
                        text = { Text(t, fontSize = 11.sp, fontFamily = FontFamily.Monospace) })
                }
            }

            Divider(color = DividerGray, thickness = 0.5.dp)

            // Action row
            val curReq = mainTab == 0
            Row(
                Modifier.fillMaxWidth().background(ElevatedBlack).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        val txt = if (curReq) {
                            when (subTab) { 1 -> request.headersAsString(); 2 -> request.bodyHex ?: ""; else -> request.bodyText ?: "" }
                        } else {
                            when (subTab) { 1 -> response?.headersAsString() ?: ""; 2 -> response?.bodyHex ?: ""; else -> response?.bodyText ?: "" }
                        }
                        clipboard.setText(AnnotatedString(txt)); snack = "Copied!"
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonGreen)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", fontSize = 12.sp)
                }
                if (curReq && request.bodyText?.isNotEmpty() == true) {
                    TextButton(
                        onClick = { onSaveMod(request.endpoint, request.bodyText ?: ""); snack = "Saved as mod!" },
                        colors = ButtonDefaults.textButtonColors(contentColor = PurpleAccent)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Mod", fontSize = 12.sp)
                    }
                }
            }

            Divider(color = DividerGray, thickness = 0.5.dp)

            // Content
            val content = if (curReq) {
                when (subTab) {
                    1 -> request.headersAsString()
                    2 -> request.bodyHex ?: "(no hex)"
                    else -> request.bodyText ?: "(empty body)"
                }
            } else {
                when (subTab) {
                    1 -> response?.headersAsString() ?: "(no response)"
                    2 -> response?.bodyHex ?: "(no hex)"
                    else -> response?.bodyText ?: "(waiting for response…)"
                }
            }
            val textColor = when {
                curReq && subTab == 2 -> Amber.copy(0.85f)
                curReq -> NeonGreen.copy(0.9f)
                !curReq && subTab == 2 -> Amber.copy(0.85f)
                else -> ElectricBlue.copy(0.9f)
            }

            Box(
                Modifier.fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(content, color = textColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
            }
        }
    }

    if (showDecode) {
        FloatingDecodeOverlay(
            request = request,
            response = response,
            onDismiss = { showDecode = false },
            onSaveMod = { body -> onSaveMod(request.endpoint, body); showDecode = false }
        )
    }

    snack?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(1500); snack = null }
        Snackbar(Modifier.padding(16.dp), containerColor = ElevatedBlack) {
            Text(msg, color = NeonGreen, fontSize = 12.sp)
        }
    }
}
