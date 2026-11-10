package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.util.AppShareHelper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassCard
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTheme
import com.example.util.DeveloperInfo

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onNavigateBack() }
    var showShareDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .semantics { contentDescription = "Navigate back to Settings" }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "About",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hero App Identity Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            contentPadding = 24.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Logo with glowing border
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF2563EB),
                                    Color(0xFF1E3A8A)
                                )
                            )
                        )
                        .border(
                            1.5.dp,
                            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent)),
                            RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "MissedCall SOS app icon",
                        modifier = Modifier.size(68.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // App Name
                Text(
                    text = stringResource(id = R.string.app_name),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Subtitle
                Text(
                    text = "Emergency Repeated Call Guard",
                    color = DesignTokens.StatusInfoBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Version Badge (from BuildConfig)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Version ${DeveloperInfo.versionName} (${DeveloperInfo.versionCode})",
                        color = GlassTheme.tokens.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Developer Credit (Single Source of Truth, Hidden if empty)
                if (DeveloperInfo.hasDeveloperName) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Developed By ${DeveloperInfo.developerName}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions & Contact Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = 8.dp
        ) {
            Column {
                // Email Contact Row (Hidden if empty)
                if (DeveloperInfo.hasContactEmail) {
                    AboutActionRow(
                        icon = Icons.Default.Email,
                        title = "Contact Developer",
                        subtitle = DeveloperInfo.contactEmail,
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:${DeveloperInfo.contactEmail}")
                                putExtra(Intent.EXTRA_SUBJECT, "MissedCall SOS Feedback & Support")
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Send Email"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                // Website Row (Hidden if empty)
                if (DeveloperInfo.hasWebsiteUrl) {
                    AboutActionRow(
                        icon = Icons.Default.Language,
                        title = "Official Website",
                        subtitle = DeveloperInfo.websiteUrl,
                        onClick = {
                            openWebUrl(context, DeveloperInfo.websiteUrl)
                        }
                    )
                }

                // Privacy Policy Row (Hidden if empty)
                if (DeveloperInfo.hasPrivacyPolicyUrl) {
                    AboutActionRow(
                        icon = Icons.Default.Policy,
                        title = "Privacy Policy",
                        subtitle = "Read data handling & privacy commitments",
                        onClick = {
                            openWebUrl(context, DeveloperInfo.privacyPolicyUrl)
                        }
                    )
                }

                // Open-Source Licenses
                AboutActionRow(
                    icon = Icons.Default.Source,
                    title = "Open-Source Licenses",
                    subtitle = "Third-party libraries & license notices",
                    onClick = onNavigateToLicenses
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Share App Button Card
        GlassButton(
            onClick = { showShareDialog = true },
            variant = GlassButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Share App",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        if (showShareDialog) {
            AlertDialog(
                onDismissRequest = { showShareDialog = false },
                containerColor = Color(0xFF131B33),
                title = {
                    Text(
                        text = "Share MissedCall SOS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Choose how you want to share with friends and family:",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )

                        // Option 1: Send APK file directly (WhatsApp, Telegram, Bluetooth)
                        GlassButton(
                            onClick = {
                                showShareDialog = false
                                AppShareHelper.shareApkFile(context)
                            },
                            variant = GlassButtonVariant.PRIMARY,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send APK File Directly", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Option 2: Share features text
                        GlassButton(
                            onClick = {
                                showShareDialog = false
                                AppShareHelper.shareAppText(context)
                            },
                            variant = GlassButtonVariant.SECONDARY,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share Features & Info", fontSize = 13.sp)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    GlassButton(
                        onClick = { showShareDialog = false },
                        variant = GlassButtonVariant.SECONDARY
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Copyright & Legal Notice
        if (DeveloperInfo.hasDeveloperName) {
            val year = if (DeveloperInfo.buildYear.isNotBlank()) DeveloperInfo.buildYear else "2026"
            Text(
                text = "\u00A9 $year ${DeveloperInfo.developerName}. All rights reserved.",
                color = GlassTheme.tokens.textMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "All rights reserved.",
                color = GlassTheme.tokens.textMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun AboutActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .semantics {
                role = Role.Button
                contentDescription = "$title: $subtitle"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DesignTokens.StatusInfoBlue,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = GlassTheme.tokens.textSecondary,
                fontSize = 12.sp
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = GlassTheme.tokens.textMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}

private fun openWebUrl(context: Context, url: String) {
    try {
        val parsedUri = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Uri.parse("https://$url")
        } else {
            Uri.parse(url)
        }
        val intent = Intent(Intent.ACTION_VIEW, parsedUri)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
    }
}
