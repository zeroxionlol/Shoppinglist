package de.zeroxion.einkaufsliste.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.zeroxion.einkaufsliste.R
import de.zeroxion.einkaufsliste.data.Category
import de.zeroxion.einkaufsliste.data.Quantity
import de.zeroxion.einkaufsliste.data.ShoppingItem

/** Rounded corners for items inside a grouped card: big outside corners, small inner ones. */
fun groupedShape(index: Int, size: Int): Shape {
    val big = 22.dp
    val small = 6.dp
    val top = if (index == 0) big else small
    val bottom = if (index == size - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

fun Category.tint(): Color = Color(color)

@Composable
fun CategoryHeader(category: Category, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(category.tint().copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(category.emoji, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        CountBadge(count, category.tint())
    }
}

@Composable
fun CountBadge(count: Int, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun CheckedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "Im Wagen",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        CountBadge(count, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (expanded) "Einklappen" else "Ausklappen",
            modifier = Modifier.rotate(rotation),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    shape: Shape,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(shape),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Löschen",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggle()
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEdit()
                        },
                    )
                    .heightIn(min = 60.dp)
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    .alpha(if (item.checked) 0.55f else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CheckCircle(checked = item.checked, color = item.category.tint())
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (item.checked) FontWeight.Normal else FontWeight.Medium,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val quantity = item.quantity
                if (!item.checked) {
                    AmountStepper(
                        amount = item.amount,
                        unit = item.unit,
                        color = item.category.tint(),
                        onIncrease = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onIncrease()
                        },
                        onDecrease = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDecrease()
                        },
                    )
                } else if (quantity != null) {
                    QuantityPill(quantity, item.category.tint())
                }
            }
        }
    }
}

/** Compact "−  2  +" control to change the amount of a product. */
@Composable
fun AmountStepper(
    amount: Double,
    unit: String?,
    color: Color,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
) {
    val canDecrease = Quantity.canDecrease(amount, unit)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.13f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            iconRes = R.drawable.ic_remove,
            description = "Weniger",
            enabled = canDecrease,
            onClick = onDecrease,
        )
        AnimatedContent(
            targetState = Quantity.format(amount, unit),
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "amount",
        ) { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 22.dp, max = 92.dp),
            )
        }
        StepButton(
            iconRes = null,
            description = "Mehr",
            enabled = true,
            onClick = onIncrease,
        )
    }
}

@Composable
private fun StepButton(iconRes: Int?, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.3f)
        if (iconRes != null) {
            Icon(painterResource(iconRes), contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
        } else {
            Icon(Icons.Rounded.Add, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun QuantityPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
fun CheckCircle(checked: Boolean, color: Color) {
    val fill by animateColorAsState(if (checked) color else Color.Transparent, label = "fill")
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "check",
    )
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = if (checked) "Erledigt" else "Offen",
            tint = Color.White,
            modifier = Modifier
                .size(18.dp)
                .scale(checkScale),
        )
    }
}
