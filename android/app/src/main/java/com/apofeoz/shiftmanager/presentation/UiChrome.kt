package com.apofeoz.shiftmanager.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apofeoz.shiftmanager.presentation.theme.ApofeozColors

private val PanelShape = RoundedCornerShape(24.dp)
private val PillShape = RoundedCornerShape(999.dp)

@Composable
fun ApofeozPanel(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val gradient = if (accent) {
        Brush.linearGradient(
            colors = listOf(
                ApofeozColors.Primary.copy(alpha = 0.14f),
                ApofeozColors.Surface,
            ),
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                ApofeozColors.Surface,
                ApofeozColors.SurfaceVariant.copy(alpha = 0.65f),
            ),
        )
    }
    Card(
        modifier = modifier,
        shape = PanelShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, ApofeozColors.PrimaryBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun SectionHeading(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun MetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    ApofeozPanel(
        modifier = modifier,
        accent = accent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    accent: Boolean = false,
    warning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val background = when {
        warning -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
        accent -> ApofeozColors.Primary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val border = when {
        warning -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        accent -> ApofeozColors.PrimaryBorder
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    }
    val textColor = when {
        warning -> MaterialTheme.colorScheme.error
        accent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .border(1.dp, border, PillShape)
            .background(background, PillShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun IdentityBadge(
    initials: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .border(2.dp, ApofeozColors.PrimaryBorder, CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun InlineHint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
    }
}
