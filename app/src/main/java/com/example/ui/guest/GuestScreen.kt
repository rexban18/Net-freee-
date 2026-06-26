package com.example.ui.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestScreen(
    onNavigateToActive: (token: String, limitMB: Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: GuestViewModel = hiltViewModel()
) {
    val tokenInput by viewModel.tokenInput.collectAsState()
    val state by viewModel.state.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successDetails by viewModel.joinSuccessDetails.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showScanSimDialog by remember { mutableStateOf(false) }
    var simTokenToScan by remember { mutableStateOf("") }

    LaunchedEffect(successDetails) {
        successDetails?.let { (_, limitMB) ->
            onNavigateToActive(tokenInput, limitMB)
            viewModel.clearState()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to Bandwidth", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 450.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Enter Session Credentials",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "Please enter the 6-digit alphanumeric token displayed on the host device, or use the scanner below:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { viewModel.onTokenChanged(it) },
                    label = { Text("6-Digit Token") },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "XK9P2M",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("token_input_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { showScanSimDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("scan_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan QR Code", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (state == GuestState.VALIDATING) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        Button(
                            onClick = { viewModel.connect() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("connect_button")
                        ) {
                            Text("Connect to Peer", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showScanSimDialog) {
            AlertDialog(
                onDismissRequest = { showScanSimDialog = false },
                title = { Text("QR Code Scanner") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Since you are on an emulator or running inside a development environment, you can simulate a QR code scan by typing the 6-digit token here:")
                        OutlinedTextField(
                            value = simTokenToScan,
                            onValueChange = { if (it.length <= 6) simTokenToScan = it.uppercase() },
                            placeholder = { Text("XK9P2M") },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            modifier = Modifier.fillMaxWidth().testTag("sim_token_input")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showScanSimDialog = false
                            viewModel.onQRScanned(simTokenToScan)
                            simTokenToScan = ""
                        },
                        modifier = Modifier.testTag("sim_scan_confirm_button")
                    ) {
                        Text("Simulate Scan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showScanSimDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
