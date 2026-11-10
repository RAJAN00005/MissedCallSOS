package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ContactRuleEntity
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassChip
import com.example.ui.components.GlassContactRow
import com.example.ui.components.GlassStepper
import com.example.ui.components.GlassSwitch
import com.example.ui.components.GlassTextField
import com.example.ui.components.SlideToDismissTrack
import com.example.ui.components.StatusOrb
import com.example.ui.theme.GlassTheme
import com.example.ui.theme.GlassTier
import com.example.ui.theme.ProtectionVisualStatus

@Composable
fun DesignGalleryScreen(
    modifier: Modifier = Modifier
) {
    var previewTier by remember { mutableStateOf(GlassTier.TIER_A) }
    var switchVal by remember { mutableStateOf(true) }
    var stepperVal by remember { mutableStateOf(5) }
    var textVal by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Liquid Glass Design Gallery (Debug)",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Test matrix across Tiers A, B, and C with live interaction",
            color = GlassTheme.tokens.textSecondary,
            fontSize = 12.sp
        )

        // Tier Selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassTier.values().forEach { tier ->
                GlassChip(
                    text = tier.name,
                    selected = previewTier == tier,
                    onClick = { previewTier = tier }
                )
            }
        }

        // Section 1: Status Orb
        Text(
            text = "Status Orb (Active, Attention, Critical)",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatusOrb(status = ProtectionVisualStatus.ACTIVE, modifier = Modifier.size(100.dp))
            StatusOrb(status = ProtectionVisualStatus.ATTENTION, modifier = Modifier.size(100.dp))
            StatusOrb(status = ProtectionVisualStatus.CRITICAL, modifier = Modifier.size(100.dp))
        }

        // Section 2: Glass Buttons
        Text(
            text = "Glass Buttons",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(onClick = {}, variant = GlassButtonVariant.PRIMARY, modifier = Modifier.weight(1f)) {
                Text("Primary")
            }
            GlassButton(onClick = {}, variant = GlassButtonVariant.SECONDARY, modifier = Modifier.weight(1f)) {
                Text("Secondary")
            }
            GlassButton(onClick = {}, variant = GlassButtonVariant.DESTRUCTIVE, modifier = Modifier.weight(1f)) {
                Text("Alert")
            }
        }

        // Section 3: Controls & Stepper
        Text(
            text = "Controls & Stepper",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            contentPadding = 14.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Liquid Switch:", color = Color.White, fontSize = 14.sp)
                    GlassSwitch(checked = switchVal, onCheckedChange = { switchVal = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Stepper ($stepperVal calls):", color = Color.White, fontSize = 14.sp)
                    GlassStepper(value = stepperVal, onValueChange = { stepperVal = it }, min = 1, max = 20)
                }
            }
        }

        // Section 4: Text Field
        Text(
            text = "Glass Text Field",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        GlassTextField(
            value = textVal,
            onValueChange = { textVal = it },
            placeholder = "Enter emergency contact name..."
        )

        // Section 5: Slide to Dismiss
        Text(
            text = "Slide to Dismiss Track (Safety Critical)",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        SlideToDismissTrack(
            onDismissed = {},
            text = "TEST SLIDE TO STOP"
        )

        Spacer(modifier = Modifier.height(90.dp))
    }
}
