package com.miguelrodriguez19.stayleafy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.miguelrodriguez19.stayleafy.R
import com.miguelrodriguez19.stayleafy.model.CareType

@Composable
fun LeafyCareIcon(
    careType: CareType,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        painter = painterResource(
            when (careType) {
                CareType.WATERING -> R.drawable.ic_water
                CareType.PRUNING -> R.drawable.ic_prune
                CareType.FERTILIZING -> R.drawable.ic_fertilize
            },
        ),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun LeafyStateContent(
    message: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        supporting?.let {
            Text(
                it,
                Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action?.let {
            Button(onClick = onAction, Modifier.padding(top = 16.dp)) { Text(it) }
        }
    }
}
