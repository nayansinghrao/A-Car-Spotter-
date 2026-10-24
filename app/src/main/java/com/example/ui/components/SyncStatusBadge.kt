package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NearBlack
import com.example.ui.theme.SpotrGreen
import com.example.ui.theme.WarningAmber

@Composable
fun SyncStatusBadge(
    syncStatus: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val (icon, text, color) = when (syncStatus) {
        "syncing" -> Triple(Icons.Default.CloudUpload, "Syncing...", WarningAmber)
        "failed" -> Triple(Icons.Default.CloudOff, "Sync Failed", DangerRed)
        else -> Triple(Icons.Default.CloudDone, "Synced", SpotrGreen)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(NearBlack.copy(alpha = 0.85f))
            .border(0.5.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(if (compact) 11.dp else 13.dp)
        )
        if (!compact) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
