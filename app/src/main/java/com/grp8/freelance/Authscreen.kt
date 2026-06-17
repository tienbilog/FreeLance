package com.grp8.freelance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grp8.freelance.ui.theme.*

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onContinueAsGuest: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    // Toggle between Sign In and Create Account
    var isCreating by remember { mutableStateOf(false) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Clear fields and errors when toggling mode
    LaunchedEffect(isCreating) {
        username = ""
        password = ""
        authViewModel.clearError()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App name / hero
            Text(
                text = "FreeLance",
                fontFamily = InterFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                color = Ink
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Smart scheduling for freelancers",
                fontFamily = InterFamily,
                fontSize = 14.sp,
                color = SlateDeep
            )

            Spacer(Modifier.height(40.dp))

            // Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(Modifier.padding(24.dp)) {

                    Text(
                        text = if (isCreating) "Create Account" else "Sign In",
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Ink
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isCreating)
                            "Pick a username — no email needed."
                        else
                            "Welcome back.",
                        fontFamily = InterFamily,
                        fontSize = 13.sp,
                        color = SlateDeep
                    )

                    Spacer(Modifier.height(20.dp))

                    // Username field
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = SlateMid,
                            focusedLabelColor = AccentBlue
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (passwordVisible)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = SlateDeep
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = SlateMid,
                            focusedLabelColor = AccentBlue
                        )
                    )

                    // Error message
                    AnimatedVisibility(visible = uiState.errorMessage != null) {
                        uiState.errorMessage?.let { msg ->
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFFE5E5))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = msg,
                                    fontFamily = InterFamily,
                                    fontSize = 13.sp,
                                    color = Color(0xFF8B0000)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Primary action button
                    Button(
                        onClick = {
                            if (isCreating) {
                                authViewModel.signUp(username, password, onSuccess = {})
                            } else {
                                authViewModel.signIn(username, password, onSuccess = {})
                            }
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isCreating) "Create Account" else "Sign In",
                                fontFamily = InterFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = White
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Toggle between sign-in / create account
                    TextButton(
                        onClick = { isCreating = !isCreating },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isCreating)
                                "Already have an account? Sign in"
                            else
                                "No account? Create one",
                            fontFamily = InterFamily,
                            fontSize = 13.sp,
                            color = AccentBlue,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Guest mode divider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = SlateMid)
                Text(
                    "  or  ",
                    fontFamily = InterFamily,
                    fontSize = 13.sp,
                    color = SlateDeep
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = SlateMid)
            }

            Spacer(Modifier.height(16.dp))

            // Guest mode button
            OutlinedButton(
                onClick = onContinueAsGuest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    // use SlateMid colour
                )
            ) {
                Text(
                    text = "Continue as Guest",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = Ink
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Guest data is saved on this device only.",
                fontFamily = InterFamily,
                fontSize = 12.sp,
                color = SlateDeep,
                textAlign = TextAlign.Center
            )
        }
    }
}