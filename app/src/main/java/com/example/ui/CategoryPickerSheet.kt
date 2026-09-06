package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Category
import com.example.ui.theme.*

/**
 * Picking a category is the common case, so the list is right here rather than
 * behind another tap; building one is rare, so it gets its own step opened from
 * the "+ دستهٔ جدید" tile at the end of the grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    isIncomeMode: Boolean,
    onPick: (Category) -> Unit,
    onCreate: (name: String, iconKey: String, colorHex: String, isIncome: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(false) }

    // Income and spending pull from the same table but rarely from the same
    // entries — showing salary under "what did you spend on" is just noise.
    val shown = remember(categories, isIncomeMode) {
        val matching = categories.filter { it.isIncome == isIncomeMode }
        if (matching.isEmpty()) categories else matching
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HorizonSurface,
        modifier = Modifier.testTag("category_picker_sheet")
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {

            Text(
                text = if (creating) "دستهٔ جدید" else "انتخاب دسته",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, color = HorizonInk
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (creating) {
                CategoryCreator(
                    isIncomeMode = isIncomeMode,
                    onCancel = { creating = false },
                    onCreate = onCreate
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    items(shown) { category ->
                        CategoryTile(category = category, onClick = { onPick(category) })
                    }
                    item {
                        NewCategoryTile(onClick = { creating = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(category: Category, onClick: () -> Unit) {
    val tint = CategoryIcons.colorOf(category.colorHex)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .semantics { contentDescription = category.name }
            .testTag("category_${category.id}")
            .padding(vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CategoryIcons.vectorFor(category.iconKey),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp, color = HorizonInk
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun NewCategoryTile(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .semantics { contentDescription = "ساخت دستهٔ جدید" }
            .testTag("category_new")
            .padding(vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .border(1.dp, HorizonBorder, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = HorizonInkMuted,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = "دستهٔ جدید",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp, color = HorizonInkMuted
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun CategoryCreator(
    isIncomeMode: Boolean,
    onCancel: () -> Unit,
    onCreate: (name: String, iconKey: String, colorHex: String, isIncome: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf(CategoryIcons.keys.first()) }
    var colorHex by remember { mutableStateOf(CategoryIcons.palette.first()) }
    val accent = CategoryIcons.colorOf(colorHex)

    Column {
        // A live preview of the tile they are building, so the icon and colour
        // choices are judged as the thing they will actually see.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CategoryIcons.vectorFor(iconKey),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                label = { Text("نام دسته") },
                placeholder = { Text("مثلاً قبض برق") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("new_category_name")
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "آیکن",
            style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 11.sp)
        )
        Spacer(modifier = Modifier.height(9.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 108.dp)
        ) {
            items(CategoryIcons.keys) { key ->
                val on = key == iconKey
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (on) accent.copy(alpha = 0.14f) else HorizonBackground)
                        .border(
                            width = if (on) 1.5.dp else 0.dp,
                            color = if (on) accent else Color.Transparent,
                            shape = RoundedCornerShape(11.dp)
                        )
                        .clickable { iconKey = key }
                        .testTag("icon_$key"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryIcons.vectorFor(key),
                        contentDescription = null,
                        tint = if (on) accent else HorizonInkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "رنگ",
            style = MaterialTheme.typography.bodySmall.copy(color = HorizonInkMuted, fontSize = 11.sp)
        )
        Spacer(modifier = Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryIcons.palette.forEach { hex ->
                val swatch = CategoryIcons.colorOf(hex)
                val on = hex == colorHex
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (on) 2.5.dp else 0.dp,
                            color = if (on) HorizonInk else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { colorHex = hex }
                        .semantics { contentDescription = "رنگ $hex" }
                        .testTag("color_$hex")
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("انصراف", style = MaterialTheme.typography.bodyMedium.copy(color = HorizonInkMuted))
            }
            Button(
                onClick = { onCreate(name, iconKey, colorHex, isIncomeMode) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HorizonGreen),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("new_category_save")
            ) {
                Text(
                    text = "ساخت دسته",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold, color = Color.White
                    )
                )
            }
        }
    }
}
