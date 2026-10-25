package com.wordecho.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordecho.app.data.Word

@Composable
fun WrongBookScreen(
    words: List<Word>,
    onBack: () -> Unit,
    onStartWrongQuiz: () -> Unit,
    onExport: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    ScreenScaffold(
        title = "错题本",
        subtitle = "${words.size} 个词待攻克",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (words.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "还没有错题。背一轮试试，答错的会自动收进来。",
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onStartWrongQuiz,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("专项刷题", fontSize = 14.sp) }

                    OutlinedButton(
                        onClick = onExport,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("导出文本", fontSize = 14.sp) }
                }
                Spacer(Modifier.height(12.dp))
            }

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(words, key = { it.id }) { w ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    w.text,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(w.meaning, fontSize = 12.sp, color = cs.onSurfaceVariant)
                            }
                            Text(
                                "错 ${w.wrongCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = ErrorRed
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
