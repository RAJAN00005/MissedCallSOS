package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.entity.ContactRuleEntity
import com.example.data.repository.DeviceContact
import com.example.data.repository.SosRepository
import com.example.domain.AddContactResult
import com.example.domain.AddContactUseCase
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassButtonVariant
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassChip
import com.example.ui.components.GlassContactRow
import com.example.ui.components.GlassStepper
import com.example.ui.components.GlassTextField
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.GlassTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    repository: SosRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val addContactUseCase = remember { AddContactUseCase(repository) }

    val allRules by repository.allRulesFlow.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedRuleIds = remember { mutableStateListOf<Long>() }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var batchAlsoDeleteHistory by remember { mutableStateOf(false) }

    // Dialog & Sheet States
    var showAddOptionsDialog by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var showPhonebookPicker by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ContactRuleEntity?>(null) }
    var showScopeRemovalDialogForRule by remember { mutableStateOf<ContactRuleEntity?>(null) }
    var removalScopeOption by remember { mutableStateOf("completely") } // "alarm", "completely"
    var removalAlsoDeleteHistory by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedRuleIds.clear()
    }

    val filteredRules = allRules.filter { rule ->
        searchQuery.isBlank() ||
                rule.displayName.contains(searchQuery, ignoreCase = true) ||
                rule.normalizedNumber.contains(searchQuery)
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Floating multi-select action bar
            AnimatedVisibility(
                visible = isSelectionMode && selectedRuleIds.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 84.dp)
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = 12.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.selected_contacts_count, selectedRuleIds.size),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassButton(
                                    onClick = {
                                        isSelectionMode = false
                                        selectedRuleIds.clear()
                                    },
                                    variant = GlassButtonVariant.SECONDARY,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text("Cancel", fontSize = 12.sp)
                                }
                                GlassButton(
                                    onClick = { showBatchDeleteConfirm = true },
                                    variant = GlassButtonVariant.DESTRUCTIVE,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.remove_selected), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.nav_rules),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${allRules.size} emergency contacts registered",
                        color = GlassTheme.tokens.textSecondary,
                        fontSize = 12.sp
                    )
                }

                // Add Contact Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DesignTokens.StatusActiveGreen.copy(alpha = 0.25f))
                        .border(1.dp, DesignTokens.StatusActiveGreen, CircleShape)
                        .clickable { showAddOptionsDialog = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .semantics { contentDescription = "Add new emergency contact" }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = DesignTokens.StatusActiveGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.add_contact),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Search Bar
            GlassTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.search_contacts),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = GlassTheme.tokens.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = GlassTheme.tokens.textMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                } else null
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Contact Rules List or Honest Empty State
            if (filteredRules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContactPhone,
                                contentDescription = null,
                                tint = DesignTokens.StatusInfoBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_contacts_yet),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.add_contacts_hint),
                            color = GlassTheme.tokens.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        GlassButton(
                            onClick = { showAddOptionsDialog = true },
                            variant = GlassButtonVariant.PRIMARY
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.add_contact), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredRules, key = { it.id }) { rule ->
                        // SwipeToDismissBox with 5-second Undo Snackbar
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                    scope.launch {
                                        // Temporarily delete
                                        repository.deleteRule(rule)
                                        val result = snackbarHostState.showSnackbar(
                                            message = "${rule.displayName} removed",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            // Undo: restore rule
                                            repository.saveRule(rule)
                                        } else {
                                            // Finalize deletion: reset counters and cancel pending SMS
                                            repository.removeContact(rule.normalizedNumber, deleteHistory = false)
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val direction = dismissState.dismissDirection
                                if (direction != SwipeToDismissBoxValue.Settled) {
                                    val isStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(DesignTokens.StatusCriticalRed.copy(alpha = 0.85f))
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Swipe to delete",
                                                tint = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.remove_contact),
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            },
                            content = {
                                GlassContactRow(
                                    rule = rule,
                                    onAlarmToggle = { enabled ->
                                        scope.launch {
                                            repository.saveRule(rule.copy(isAlarmEnabled = enabled))
                                        }
                                    },
                                    onEditThreshold = {
                                        editingRule = rule
                                    },
                                    onDeleteClick = {
                                        showScopeRemovalDialogForRule = rule
                                    },
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedRuleIds.contains(rule.id),
                                    onSelectToggle = {
                                        if (selectedRuleIds.contains(rule.id)) {
                                            selectedRuleIds.remove(rule.id)
                                            if (selectedRuleIds.isEmpty()) isSelectionMode = false
                                        } else {
                                            selectedRuleIds.add(rule.id)
                                        }
                                    },
                                    onLongClick = {
                                        isSelectionMode = true
                                        if (!selectedRuleIds.contains(rule.id)) {
                                            selectedRuleIds.add(rule.id)
                                        }
                                    }
                                )
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }

    // Add Contact Options Dialog (Phonebook vs Manual)
    if (showAddOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showAddOptionsDialog = false },
            containerColor = Color(0xFF131B33),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = { Text(stringResource(R.string.add_contact)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        onClick = {
                            showAddOptionsDialog = false
                            showPhonebookPicker = true
                        },
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.ContactPhone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.pick_from_phonebook))
                    }

                    GlassButton(
                        onClick = {
                            showAddOptionsDialog = false
                            showManualAddDialog = true
                        },
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.manual_entry))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                GlassButton(onClick = { showAddOptionsDialog = false }, variant = GlassButtonVariant.SECONDARY) {
                    Text("Close")
                }
            }
        )
    }

    // Phonebook Contact Picker Dialog
    if (showPhonebookPicker) {
        PhonebookContactPickerDialog(
            repository = repository,
            onDismiss = { showPhonebookPicker = false },
            onContactSelected = { name, number ->
                scope.launch {
                    val result = addContactUseCase(
                        displayName = name,
                        rawNumber = number,
                        groupCategory = "Family"
                    )
                    showPhonebookPicker = false
                    if (result is AddContactResult.Success) {
                        snackbarHostState.showSnackbar("Added $name to emergency list")
                    }
                }
            }
        )
    }

    // Manual Add Contact Dialog
    if (showManualAddDialog) {
        AddContactModal(
            onDismiss = { showManualAddDialog = false },
            onAddContact = { name, number, group ->
                scope.launch {
                    val result = addContactUseCase(
                        displayName = name,
                        rawNumber = number,
                        groupCategory = group
                    )
                    showManualAddDialog = false
                    when (result) {
                        is AddContactResult.Success -> {
                            snackbarHostState.showSnackbar("Added $name to emergency list")
                        }
                        is AddContactResult.InvalidNumber -> {
                            snackbarHostState.showSnackbar("Error: ${result.reason}")
                        }
                        is AddContactResult.EmptyName -> {
                            snackbarHostState.showSnackbar("Error: ${result.reason}")
                        }
                    }
                }
            }
        )
    }

    // Edit Custom Threshold & Remove Bottom Sheet
    editingRule?.let { rule ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { editingRule = null },
            sheetState = sheetState,
            containerColor = Color(0xFF131B33),
            contentColor = Color.White
        ) {
            var threshold by remember { mutableStateOf(rule.customThreshold ?: repository.preferences.getGlobalThreshold()) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Configure Rules for ${rule.displayName}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = rule.normalizedNumber,
                    fontSize = 13.sp,
                    color = GlassTheme.tokens.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Custom Missed Calls Threshold:",
                    fontSize = 14.sp,
                    color = GlassTheme.tokens.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                GlassStepper(
                    value = threshold,
                    onValueChange = { threshold = it },
                    min = 2,
                    max = 20
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassButton(
                        onClick = {
                            val target = rule
                            editingRule = null
                            showScopeRemovalDialogForRule = target
                        },
                        variant = GlassButtonVariant.DESTRUCTIVE,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.remove_contact), fontWeight = FontWeight.Bold)
                    }

                    GlassButton(
                        onClick = {
                            scope.launch {
                                repository.saveRule(rule.copy(customThreshold = threshold))
                                editingRule = null
                            }
                        },
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Removal Scope Dialog (Remove Alarm only / Completely)
    showScopeRemovalDialogForRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { showScopeRemovalDialogForRule = null },
            containerColor = Color(0xFF131B33),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.9f),
            title = { Text(stringResource(R.string.remove_scope_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select how you want to remove ${rule.displayName}:", fontSize = 13.sp, color = GlassTheme.tokens.textSecondary)

                    // Option 1: Alarm only
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { removalScopeOption = "alarm" }) {
                        RadioButton(
                            selected = removalScopeOption == "alarm",
                            onClick = { removalScopeOption = "alarm" },
                            colors = RadioButtonDefaults.colors(selectedColor = DesignTokens.StatusInfoBlue)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.remove_alarm_only), fontSize = 13.sp, color = Color.White)
                    }

                    // Option 2: Completely
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { removalScopeOption = "completely" }) {
                        RadioButton(
                            selected = removalScopeOption == "completely",
                            onClick = { removalScopeOption = "completely" },
                            colors = RadioButtonDefaults.colors(selectedColor = DesignTokens.StatusCriticalRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.remove_completely), fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    if (removalScopeOption == "completely") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { removalAlsoDeleteHistory = !removalAlsoDeleteHistory }) {
                            Checkbox(
                                checked = removalAlsoDeleteHistory,
                                onCheckedChange = { removalAlsoDeleteHistory = it },
                                colors = CheckboxDefaults.colors(checkedColor = DesignTokens.StatusCriticalRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.also_delete_history), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            },
            confirmButton = {
                GlassButton(
                    onClick = {
                        val target = rule
                        showScopeRemovalDialogForRule = null
                        scope.launch {
                            when (removalScopeOption) {
                                "alarm" -> repository.updateAlarmOnly(target.normalizedNumber, false)
                                else -> repository.removeContact(target.normalizedNumber, deleteHistory = removalAlsoDeleteHistory)
                            }
                            snackbarHostState.showSnackbar(context.getString(R.string.contact_removed))
                        }
                    },
                    variant = GlassButtonVariant.DESTRUCTIVE
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                GlassButton(onClick = { showScopeRemovalDialogForRule = null }, variant = GlassButtonVariant.SECONDARY) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch Removal Confirmation Dialog
    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            containerColor = Color(0xFF131B33),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = { Text(stringResource(R.string.remove_confirm_title, selectedRuleIds.size)) },
            text = {
                Column {
                    Text("Selected contacts will be completely removed from MissedCall SOS protection.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { batchAlsoDeleteHistory = !batchAlsoDeleteHistory }
                    ) {
                        Checkbox(
                            checked = batchAlsoDeleteHistory,
                            onCheckedChange = { batchAlsoDeleteHistory = it },
                            colors = CheckboxDefaults.colors(checkedColor = DesignTokens.StatusCriticalRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.also_delete_history), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                GlassButton(
                    onClick = {
                        val selectedIds = selectedRuleIds.toList()
                        showBatchDeleteConfirm = false
                        isSelectionMode = false
                        selectedRuleIds.clear()
                        scope.launch {
                            val targets = allRules.filter { selectedIds.contains(it.id) }
                            val targetNumbers = targets.map { it.normalizedNumber }
                            repository.removeContacts(targetNumbers, deleteHistory = batchAlsoDeleteHistory)
                            snackbarHostState.showSnackbar("Removed ${targets.size} contacts")
                        }
                    },
                    variant = GlassButtonVariant.DESTRUCTIVE
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                GlassButton(onClick = { showBatchDeleteConfirm = false }, variant = GlassButtonVariant.SECONDARY) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PhonebookContactPickerDialog(
    repository: SosRepository,
    onDismiss: () -> Unit,
    onContactSelected: (name: String, number: String) -> Unit
) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    remember(hasPermission) {
        if (hasPermission) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val list = repository.fetchDeviceContacts(context)
                contacts = list
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    val filtered = contacts.filter {
        searchQuery.isBlank() ||
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.number.contains(searchQuery)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131B33),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.9f),
        title = {
            Text(stringResource(R.string.pick_from_phonebook), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.height(380.dp)) {
                if (!hasPermission) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Contacts permission is required to browse your phonebook.", fontSize = 13.sp, color = GlassTheme.tokens.textSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        GlassButton(
                            onClick = { launcher.launch(Manifest.permission.READ_CONTACTS) },
                            variant = GlassButtonVariant.PRIMARY
                        ) {
                            Text("Grant Permission")
                        }
                    }
                } else if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DesignTokens.StatusInfoBlue)
                    }
                } else {
                    GlassTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search device contacts..."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (filtered.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No matching contacts in phonebook", color = GlassTheme.tokens.textMuted, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filtered, key = { it.id + it.number }) { contact ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable { onContactSelected(contact.name, contact.number) }
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(text = contact.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(text = contact.number, color = GlassTheme.tokens.textSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            GlassButton(onClick = onDismiss, variant = GlassButtonVariant.SECONDARY) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddContactModal(
    onDismiss: () -> Unit,
    onAddContact: (name: String, number: String, group: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("Family") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(24.dp),
            contentPadding = 20.dp
        ) {
            Column {
                Text(
                    text = "Add Trusted Contact",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Contact Name"
                )
                Spacer(modifier = Modifier.height(10.dp))

                GlassTextField(
                    value = number,
                    onValueChange = { number = it },
                    placeholder = "Phone Number"
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Category Group:",
                    color = GlassTheme.tokens.textSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Family", "Emergency", "Custom").forEach { cat ->
                        GlassChip(
                            text = cat,
                            selected = group == cat,
                            onClick = { group = cat }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassButton(
                        onClick = onDismiss,
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    GlassButton(
                        onClick = {
                            if (name.isNotBlank() && number.isNotBlank()) {
                                onAddContact(name, number, group)
                            }
                        },
                        enabled = name.isNotBlank() && number.isNotBlank(),
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
